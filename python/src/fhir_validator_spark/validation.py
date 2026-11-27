"""
Validation of FHIR resources with the validator JAR in a Spark session.

The validation runs in the JVM, through the public entry point of the JAR
(`au.csiro.fhir.validation.spark.ValidationApi.validate`), and the results are written as Parquet.

Author: John Grimes
"""

import re
import sys
import time
from collections.abc import Iterable, Sequence

MAVEN_ARTIFACT = "au.csiro.fhir:fhir-validator-spark"
DEFAULT_MASTER = "local[*]"
# Each executor thread builds its own validation engine, which holds the FHIR packages it has
# loaded. In local mode the threads run in the driver, which runs out of memory with the Spark
# default of 1g.
DEFAULT_DRIVER_MEMORY = "8g"
FILENAME_COLUMN = "filename"


def maven_version(package_version: str) -> str:
    """
    Returns the Maven version of the validator JAR that matches a version of this package.

    :param package_version: the version of this package, e.g. ``2.0.0`` or ``2.0.0.dev0``
    :returns: the same version for a release, or the SNAPSHOT of a development version, e.g.
        ``2.0.0-SNAPSHOT`` for ``2.0.0.dev0``
    """
    return re.sub(r"\.dev\d+$", "-SNAPSHOT", package_version)


def validator_args(
    fhir_version: str | None = None,
    igs: Iterable[str] = (),
    language: str | None = None,
    tx_server: str | None = None,
    extra: Iterable[str] = (),
) -> list[str]:
    """
    Expresses the validation options as HL7 FHIR validator arguments, followed by the arguments
    given after ``--``. Where a validator option takes a single value the last one wins, so the
    latter override the options.

    :param fhir_version: the FHIR version (``-version``)
    :param igs: implementation guides to load (``-ig``)
    :param language: the language of the messages (``-language``)
    :param tx_server: the terminology server (``-tx``)
    :param extra: validator arguments to pass through
    :returns: the validator arguments, e.g. ``["-ig", "a.tgz", "-level", "errors"]``
    """
    args = []
    if fhir_version is not None:
        args += ["-version", fhir_version]
    for ig in igs:
        args += ["-ig", ig]
    if language is not None:
        args += ["-language", language]
    if tx_server is not None:
        args += ["-tx", tx_server]
    return args + list(extra)


def spark_conf(
    package_version: str,
    master: str = DEFAULT_MASTER,
    driver_memory: str = DEFAULT_DRIVER_MEMORY,
    jar: str | None = None,
    conf: Sequence[tuple[str, str]] = (),
) -> dict[str, str]:
    """
    Builds the configuration of the Spark session that runs the validation.

    :param package_version: the version of this package, which selects the JAR from Maven Central
    :param master: the Spark master
    :param driver_memory: the memory of the Spark driver, which runs the validation in local mode
    :param jar: a local validator JAR to use instead of the one from Maven Central
    :param conf: further Spark configuration, applied last so that it overrides the above
    :returns: the Spark configuration
    """
    result = {
        "spark.app.name": "fhir-validator",
        "spark.master": master,
        "spark.driver.memory": driver_memory,
    }
    if jar is None:
        result["spark.jars.packages"] = (
            f"{MAVEN_ARTIFACT}:{maven_version(package_version)}"
        )
    else:
        result["spark.jars"] = jar
    result.update(conf)
    return result


def run_validation(
    input_path: str,
    output_path: str,
    *,
    spark_conf: dict[str, str],
    validator_args: list[str],
    log_progress: bool,
    log_level: str,
) -> None:
    """
    Validates the FHIR resources in NDJSON files and writes the resources with issues to a
    Parquet dataset, replacing any existing one.

    :param input_path: an NDJSON file, or a directory of them, optionally partitioned by
        ``filename``; without a ``filename`` partition the input path is used as the filename
    :param output_path: the directory of the Parquet dataset to write
    :param spark_conf: the configuration of the Spark session
    :param validator_args: the HL7 FHIR validator arguments
    :param log_progress: whether the validator logs its progress through each resource
    :param log_level: the Spark log level
    :raises ValueError: if the validator arguments are invalid, or the input cannot be read
    """
    from pyspark.errors import AnalysisException
    from pyspark.sql import DataFrame, SparkSession
    from pyspark.sql.functions import lit

    start = time.monotonic()
    status(f"Starting Spark ({spark_conf['spark.master']})")
    builder = SparkSession.builder
    for key, value in spark_conf.items():
        builder = builder.config(key, value)
    spark = builder.getOrCreate()
    try:
        spark.sparkContext.setLogLevel(log_level)
        jvm = spark.sparkContext._jvm
        config = (
            jvm.au.csiro.fhir.validation.hl7.HL7ValidationConfig.builder()
            .validatorArgs(validator_args)
            .showProgress(log_progress)
            .build()
        )
        # Check the validator arguments before Spark reads the input.
        java_call(config.toCliContext)
        try:
            ndjson = spark.read.text(input_path)
        except AnalysisException as e:
            raise ValueError(str(e)) from e
        if FILENAME_COLUMN not in ndjson.columns:
            ndjson = ndjson.withColumn(FILENAME_COLUMN, lit(input_path))
        status(f"Validating {input_path} into {output_path}")
        results = java_call(
            jvm.au.csiro.fhir.validation.spark.ValidationApi.validate,
            ndjson._jdf,
            config,
        )
        DataFrame(results, spark).write.mode("overwrite").parquet(output_path)
    finally:
        spark.stop()
    status(f"Validated {input_path} in {time.monotonic() - start:.1f} s")


def java_call(method, *args):
    """
    Calls a JVM method, and raises an ``IllegalArgumentException`` that it throws as a
    ``ValueError`` with the same message. PySpark converts the Java exception into its own.
    """
    from pyspark.errors import IllegalArgumentException

    try:
        return method(*args)
    except IllegalArgumentException as e:
        raise ValueError(str(e)) from e


def status(message: str) -> None:
    """Reports the progress of the validation on standard error, alongside the Spark logs."""
    print(message, file=sys.stderr, flush=True)
