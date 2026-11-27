# FHIR Validation on Apache Spark

[![Test](https://github.com/aehrc/fhir-validator-spark/workflows/Verify/badge.svg)](https://github.com/aehrc/fhir-validator-spark/actions?query=workflow%3AVerify)

This project provides tools for validating large sets of FHIR resources using Apache Spark parallel processing.
It supports validation of resources in NDJSON format
with [HL7 FHIR Validator](https://github.com/hapifhir/org.hl7.fhir.core).

It has two parts:

- The `fhir-validator` command, which validates NDJSON files and reports the issues found. It is a Python package
  that runs Spark through PySpark, so it needs no separate Spark installation.
- A library for Spark, published to Maven Central, which the command uses and which can be called from Spark code.

## Prerequisites

- Java 21
- [uv](https://docs.astral.sh/uv/), to install the command
- Maven, to build from source

## Installation

Install the command with uv:

```sh
uv tool install fhir-validator-spark
```

The package is published to PyPI from version 2.0.0. Until then, or to use unreleased changes, install it from GitHub:

```sh
uv tool install "git+https://github.com/aehrc/fhir-validator-spark#subdirectory=python"
```

or from a checkout of this repository:

```sh
uv tool install ./python
```

`fhir-validator validate` resolves the validator JAR from Maven Central the first time it runs, at the version that
matches the package, and caches it. `fhir-validator --version` shows both versions. A package installed from GitHub or
a checkout has a development version (e.g. `2.0.0.dev0`), which uses the SNAPSHOT JAR (e.g. `2.0.0-SNAPSHOT`). That
JAR is not on Maven Central. Either install it into the local Maven repository, which Spark also searches, with
`mvn install`, or give the JAR built with `mvn package` with `--jar target/fhir-validator-spark-VERSION.jar`.

## Validating FHIR resources

The validation of FHIR resources is a two-step process:

1. `fhir-validator validate` validates the resources and writes the results as a Parquet dataset.
2. `fhir-validator report` generates an HTML report of the issues from the Parquet dataset.

The examples are run from the root of this repository, which has sample data and its implementation guide.

### Validating a single file

`data/mimic-iv-demo-10` contains small samples of the MIMIC-IV demo resources in NDJSON format. To validate the
patients against the MIMIC-IV FHIR IG:

```sh
fhir-validator validate data/mimic-iv-demo-10/MimicPatient.ndjson target/validation-patient \
  --ig data/packages/kindlab.fhir.mimic/package.tgz
```

The results are written to the `target/validation-patient` directory. To generate a report that includes issues of
all levels:

```sh
fhir-validator report target/validation-patient target/report-patient.html --min-level 0
```

Then open `target/report-patient.html` in a web browser. The "File Name" column of the report is the input file.

### Validating a partitioned dataset

`data/mimic-iv-demo-10_partitioned` contains the same samples, partitioned by `filename` in the Hive partitioning
scheme. To validate all of them:

```sh
fhir-validator validate data/mimic-iv-demo-10_partitioned target/validation-partitioned \
  --ig data/packages/kindlab.fhir.mimic/package.tgz
fhir-validator report target/validation-partitioned target/report-partitioned.html --min-level 0
```

The "File Name" column of the report is the `filename` partition of the dataset.

## Command reference

`fhir-validator --help` lists the commands, and `fhir-validator COMMAND --help` lists the options of each.

### fhir-validator validate

```sh
fhir-validator validate [OPTIONS] INPUT OUTPUT [-- VALIDATOR_ARG...]
```

Validates FHIR resources in NDJSON format with Apache Spark, using
the [HL7 FHIR Validator](https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator).

`INPUT` is an NDJSON file with a FHIR resource on each line, or a directory of such files. The directory can be
partitioned with the Hive partitioning scheme on a `filename` column, e.g.:

```
dataset/
     filename=MimicPatient/
          part-00000.ndjson
          part-00001.ndjson
          ...
      filename=MimicObservation/
          part-00000.ndjson
          part-00001.ndjson
          ...
      ...
```

For data that is not partitioned, the `filename` of each resource is `INPUT`.

`OUTPUT` is a Parquet dataset of the resources for which the validator reports at least one issue, in the schema
described in [Using the library with Spark](#using-the-library-with-spark). An existing dataset is replaced.

The validation options are:

| Option                 | Description                                                                      |
|------------------------|----------------------------------------------------------------------------------|
| `-v`, `--fhir-version` | FHIR version, defaults to `4.0.1`                                                |
| `-i`, `--ig`           | Implementation guide to load (a package file, directory or `id#version`), repeatable |
| `-l`, `--language`     | Language of the messages                                                         |
| `-tx`, `--tx-server`   | Terminology server, defaults to none                                             |
| `-p`, `--log-progress` | Log the progress of the validator in each resource                               |
| `-d`, `--log-level`    | Spark log level, defaults to `WARN`                                              |

Before version 2.0.0, `--ig` took several values (`--ig a.tgz b.tgz`). Repeat the option instead
(`--ig a.tgz --ig b.tgz`).

The Spark options are:

| Option            | Description                                                                                   |
|-------------------|-----------------------------------------------------------------------------------------------|
| `--master`        | Spark master URL, defaults to `local[*]`, which runs Spark in this process with a thread per core |
| `--driver-memory` | Memory of the Spark driver, defaults to `8g`                                                  |
| `--conf`          | Spark configuration property as `KEY=VALUE`, repeatable; overrides the options above          |
| `--jar`           | Validator JAR to use, instead of the one from Maven Central                                   |

Each thread that runs validation builds its own validation engine, which holds the FHIR packages it has loaded. In
local mode the threads run in the driver, which needs more memory than the Spark default of 1 GB. The default of
8 GB is enough for 14 threads with the MIMIC IG loaded. With less memory, use fewer threads
(e.g. `--master local[4]`). On a cluster, set the executor memory with `--conf spark.executor.memory=8g`.

To run on a cluster, give its master URL, e.g.:

```sh
fhir-validator validate --master spark://spark-master:7077 --conf spark.executor.memory=8g \
  hdfs:///data/fhir hdfs:///data/validation --ig hl7.fhir.au.core#1.0.0
```

The input, the output and any implementation guide files must be readable by the executors.

#### Validator arguments

Arguments given after `--` are passed to the argument parser of the HL7 FHIR validator, so `fhir-validator validate`
accepts the same validation options as the validator version it is built with (6.3.10). See
[Using the FHIR Validator](https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator) for the list of
parameters. For example, to report only errors, allow any extension and report display mismatches as warnings:

```sh
fhir-validator validate data/mimic-iv-demo-10/MimicPatient.ndjson target/validation-patient \
  -- -ig data/packages/kindlab.fhir.mimic/package.tgz -level errors -extension any -display-issues-are-warnings
```

To validate every resource against a profile, and to treat best practice recommendations as errors:

```sh
fhir-validator validate data/mimic-iv-demo-10/MimicPatient.ndjson target/validation-patient \
  -- -ig data/packages/kindlab.fhir.mimic/package.tgz \
  -profile http://mimic.mit.edu/fhir/mimic/StructureDefinition/mimic-patient -best-practice error
```

The validation options of `fhir-validator validate` are given to the validator as the equivalent validator
arguments, ahead of the arguments after `--`. Where a validator option takes a single value, the last one wins, so an
argument after `--` overrides the option.

| Option           | Validator argument |
|------------------|--------------------|
| `--fhir-version` | `-version`         |
| `--ig`           | `-ig`              |
| `--language`     | `-language`        |
| `--tx-server`    | `-tx`              |

The validator defaults apply, except for two settings that would make the validator send network requests while it
validates resources:

- No terminology server is used (`-tx n/a`), where the validator would use `tx.fhir.org`. Use `--tx-server <url>` or
  `-- -tx <url>` to use one.
- The default resource fetcher is turned off (`-disable-default-resource-fetcher`), so canonical URLs that the loaded
  packages do not define, such as a profile in `meta.profile`, are not fetched from the network. Load the packages
  that define them with `-ig`. The validator has no argument to turn the fetcher back on, so this cannot be
  overridden.

The FHIR version defaults to R4 (`4.0.1`).

Validator arguments that have no effect when validating individual resources in Spark are rejected with an error
before validation starts. These are input files (the input is read by Spark), other validator modes such as
`-convert`, `-compare` or `-snapshot`, output and log files such as `-output`, `-html-output` or `-txLog`, process
settings such as `-proxy`, `-fhir-settings` or `-watch-mode`, and options that the validator only applies when it
reads files itself (`-native`, `-do-native`, `-no-native` and `-show-times`). Arguments that the validator does not
recognise are rejected in the same way. Option values are interpreted by the validator: for example, an unknown
`-level` value is read as `hints`.

Before version 2.0.0, the `validate-fhir` script always reported display mismatches in codings as warnings. They are
now errors, which is the validator default; use `-- -display-issues-are-warnings` to report them as warnings. A
terminology server given with `--tx-server` that cannot be reached now fails the validation, where it was previously
ignored. The validator also loads the `hl7.terminology` and `hl7.fhir.uv.extensions` packages, as the validator
command line does, so these must be in the FHIR package cache or downloadable when validation starts.

See also:

- <a href="https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator">HL7 FHIR Validator</a>
- <a href="https://github.com/hapifhir/org.hl7.fhir.core">HL7 FHIR Core tools</a>

### fhir-validator report

```sh
fhir-validator report [OPTIONS] INPUT OUTPUT
```

Writes an HTML report of the issues in the validation results in `INPUT` to `OUTPUT`. The issues are grouped by:

- level
- type
- message identifier
- filename

Each group has an example (the message, the location of the issue and the JSON of a resource, with the column of the
issue highlighted) and the number of issues.

| Option              | Description                                                                                        |
|---------------------|----------------------------------------------------------------------------------------------------|
| `--min-level`       | Minimum level of the issues to include: information=1, warning=2, error=3, fatal=4; defaults to 3 |
| `--exclude-message` | Leave out the messages that match this SQL `LIKE` pattern, e.g. `%narrative%`; repeatable          |
| `--limit`           | Maximum number of rows in the report, defaults to all                                              |
| `--partition-by-dir` | `INPUT` has a subdirectory for each filename (e.g. `INPUT/MimicPatient/`), rather than a `filename` column |

## Using the library with Spark

The library is published to Maven Central as `au.csiro.fhir:fhir-validator-spark`, from version 2.0.0. The artifact is
a single JAR that contains the HL7 FHIR Validator and its dependencies, with the libraries that clash with those of
Spark relocated, so Spark only needs to resolve this one JAR.

The entry point for Spark code is `au.csiro.fhir.validation.spark.ValidationApi.validate`, which takes a dataset of
FHIR resources and an `HL7ValidationConfig`, and returns the validation results as a dataset:

- The input has a string column `value` with one FHIR resource as JSON per row, which is what `spark.read.text` gives
  for NDJSON files. An optional string column `filename` names the source of each resource. Other columns are ignored.
- The configuration holds the [validator arguments](#validator-arguments), with the defaults described there.
  Invalid arguments are reported when `validate` is called.
- The result has one row for each resource for which the validator reports at least one issue. Resources without
  issues are left out. If the input has no `filename` column, `filename` is null in the result.

The result has this schema, which is also the schema of the output of
[fhir-validator validate](#fhir-validator-validate):

```
root
 |-- filename: string (nullable = false)          // source of the resource
 |-- issues: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- col: integer (nullable = true)      // column of the issue in the resource JSON
 |    |    |-- level: string (nullable = false)    // fatal, error, warning or information
 |    |    |-- line: integer (nullable = true)     // line of the issue in the resource JSON
 |    |    |-- location: string (nullable = true)  // FHIRPath of the element with the issue
 |    |    |-- message: string (nullable = false)  // issue message
 |    |    |-- messageId: string (nullable = true) // validator message identifier
 |    |    |-- type: string (nullable = false)     // issue type, as the validator classifies it
 |-- resource: string (nullable = false)          // the resource JSON
```

Each executor thread builds its own validation engine, which holds the FHIR packages it has loaded. The examples below
run Spark in local mode with 8 GB of driver memory; with less memory, use fewer threads (e.g. `--master local[2]`).
They are run from the root of this repository, which has the MIMIC demo data and its implementation guide.

### spark-submit

To run the validation application in the JAR, `au.csiro.fhir.validation.cli.ValidateApp`, which takes the same
validation options and [validator arguments](#validator-arguments) as `fhir-validator validate`:

```sh
spark-submit --packages au.csiro.fhir:fhir-validator-spark:VERSION --driver-memory 8g \
  --class au.csiro.fhir.validation.cli.ValidateApp spark-internal \
  data/mimic-iv-demo-10/MimicPatient.ndjson target/validation-patient \
  --ig data/packages/kindlab.fhir.mimic/package.tgz
```

To run a PySpark script that calls the entry point, save the following as `validate.py`:

```python
from pyspark.sql import DataFrame, SparkSession

spark = SparkSession.builder.getOrCreate()
jvm = spark.sparkContext._jvm

config = (
    jvm.au.csiro.fhir.validation.hl7.HL7ValidationConfig.builder()
    .validatorArgs(["-ig", "data/packages/kindlab.fhir.mimic/package.tgz"])
    .build()
)
ndjson = spark.read.text("data/mimic-iv-demo-10/MimicPatient.ndjson")
results = DataFrame(
    jvm.au.csiro.fhir.validation.spark.ValidationApi.validate(ndjson._jdf, config), spark
)
results.selectExpr("filename", "explode(issues) AS issue").groupBy("issue.level").count().show()
```

Then run:

```sh
spark-submit --packages au.csiro.fhir:fhir-validator-spark:VERSION --driver-memory 8g validate.py
```

### pyspark and spark-shell

Start `pyspark` with the package, and run the code of `validate.py` above, without creating the session:

```sh
pyspark --packages au.csiro.fhir:fhir-validator-spark:VERSION --driver-memory 8g
```

Start `spark-shell` with the package:

```sh
spark-shell --packages au.csiro.fhir:fhir-validator-spark:VERSION --driver-memory 8g
```

Then run:

```scala
import au.csiro.fhir.validation.hl7.HL7ValidationConfig
import au.csiro.fhir.validation.spark.ValidationApi
import scala.jdk.CollectionConverters._

val args = List("-ig", "data/packages/kindlab.fhir.mimic/package.tgz")
val config = HL7ValidationConfig.builder().validatorArgs(args.asJava).build()
val ndjson = spark.read.text("data/mimic-iv-demo-10/MimicPatient.ndjson")
val results = ValidationApi.validate(ndjson, config)
results.selectExpr("filename", "explode(issues) AS issue").groupBy("issue.level").count().show()
```

### SparkSession with spark.jars.packages

In a Python program, set `spark.jars.packages` when the session is created, then call the entry point as in
`validate.py` above:

```python
from pyspark.sql import SparkSession

spark = (
    SparkSession.builder.master("local[*]")
    .config("spark.jars.packages", "au.csiro.fhir:fhir-validator-spark:VERSION")
    .config("spark.driver.memory", "8g")
    .getOrCreate()
)
```

## Building from source

To build the library and run its tests:

```sh
mvn clean install
```

The Python package is in the `python` directory. To lint it and run its tests against the JAR built from source:

```sh
cd python
uv run ruff check
uv run ruff format --check
FHIR_VALIDATOR_JAR=$(ls ../target/fhir-validator-spark-*.jar) uv run pytest
```

Without `FHIR_VALIDATOR_JAR`, the tests that run Spark are skipped.

## Releasing

1. Set the release version in `pom.xml` (e.g. `2.0.0`) and in `python/pyproject.toml`, commit it and push a tag named
   after it (e.g. `v2.0.0`). The test suite checks that the two versions match.
2. The [Draft Release](.github/workflows/draft-release.yml) workflow checks that the version is not a SNAPSHOT,
   creates a draft GitHub release, and uploads the signed artifacts to the
   [Central Portal](https://central.sonatype.com/publishing/deployments), where it waits until they are validated.
3. Check the deployment in the Central Portal and publish it. Publishing to Maven Central cannot be undone: a
   published version can never be changed or removed.
4. The workflow then waits for approval to publish the Python package to PyPI. Approve it only once the JAR is
   available on Maven Central, as the package uses the JAR of the same version. The workflow fails if the package
   version does not match the tag. A version published to PyPI cannot be published again.
5. Publish the draft GitHub release.
6. Set the next development version in `pom.xml` (e.g. `2.1.0-SNAPSHOT`) and `python/pyproject.toml`
   (e.g. `2.1.0.dev0`).

The workflow needs these repository secrets: `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central Portal
user token), `GPG_PRIVATE_KEY` (an ASCII-armoured private key) and `GPG_PASSPHRASE`. To build the release artifacts
locally without uploading them, run `mvn -Prelease verify` with the key and passphrase in the `MAVEN_GPG_KEY` and
`MAVEN_GPG_PASSPHRASE` environment variables.

Publishing to PyPI uses [trusted publishing](https://docs.pypi.org/trusted-publishers/), with no secret. It needs a
GitHub environment named `pypi` with required reviewers, and a trusted publisher for the `fhir-validator-spark`
project on PyPI for this repository, the `draft-release.yml` workflow and the `pypi` environment.

## Important note

This software is currently in alpha. It is not yet ready for production use.

Copyright © 2024, Commonwealth Scientific and Industrial Research Organisation (CSIRO) ABN 41 687 119 230. Licensed
under the [Apache License, version 2.0](./LICENSE).
