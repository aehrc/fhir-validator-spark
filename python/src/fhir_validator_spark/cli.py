"""
The fhir-validator command, with the subcommands validate and report.

Author: John Grimes
"""

from importlib.metadata import version
from itertools import takewhile

import click

from fhir_validator_spark.report import generate_report
from fhir_validator_spark.validation import (
    DEFAULT_DRIVER_MEMORY,
    DEFAULT_MASTER,
    MAVEN_ARTIFACT,
    maven_version,
    run_validation,
    spark_conf,
    validator_args,
)

PACKAGE_VERSION = version("fhir-validator-spark")
LOG_LEVELS = ["ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF"]


@click.group(context_settings={"help_option_names": ["-h", "--help"]})
@click.version_option(
    PACKAGE_VERSION,
    message=f"%(prog)s %(version)s (validator JAR {MAVEN_ARTIFACT}:{maven_version(PACKAGE_VERSION)})",
)
def main():
    """Validates FHIR resources in NDJSON format with the HL7 FHIR Validator on Apache Spark, and
    reports the issues found."""


class ValidateCommand(click.Command):
    """
    A command that rejects arguments before ``--`` that start with a single dash and are not its
    own options. Click would otherwise read a validator argument such as ``-level`` as the option
    ``-l`` with the value ``evel``.
    """

    def parse_args(self, ctx: click.Context, args: list[str]) -> list[str]:
        names = {
            name
            for param in self.get_params(ctx)
            if isinstance(param, click.Option)
            for name in param.opts + param.secondary_opts
        }
        for arg in takewhile(lambda a: a != "--", args):
            if (
                arg.startswith("-")
                and not arg.startswith("--")
                and arg != "-"
                and arg not in names
            ):
                raise click.NoSuchOption(
                    arg,
                    message=f"No such option: {arg}. Give validator arguments after --, "
                    f"e.g. -- {arg}",
                    ctx=ctx,
                )
        return super().parse_args(ctx, args)


def parse_conf(
    ctx: click.Context, param: click.Parameter, values: tuple[str, ...]
) -> list[tuple[str, str]]:
    """Splits Spark configuration given as KEY=VALUE into pairs."""
    pairs = []
    for value in values:
        key, separator, setting = value.partition("=")
        if not separator or not key:
            raise click.BadParameter(f"expected KEY=VALUE, got {value!r}", ctx, param)
        pairs.append((key, setting))
    return pairs


@main.command(cls=ValidateCommand)
@click.argument("input_path", metavar="INPUT")
@click.argument("output_path", metavar="OUTPUT")
@click.argument("extra_args", nargs=-1, metavar="[-- VALIDATOR_ARG...]")
@click.option(
    "-v", "--fhir-version", help="FHIR version (validator -version). Defaults to 4.0.1."
)
@click.option(
    "-i",
    "--ig",
    "igs",
    multiple=True,
    help="Implementation guide (validator -ig). Repeatable.",
)
@click.option(
    "-l", "--language", help="Language of the messages (validator -language)."
)
@click.option(
    "-tx", "--tx-server", help="Terminology server (validator -tx). Defaults to none."
)
@click.option(
    "-p",
    "--log-progress",
    is_flag=True,
    help="Log the progress of the validator in each resource.",
)
@click.option(
    "-d",
    "--log-level",
    type=click.Choice(LOG_LEVELS, case_sensitive=False),
    default="WARN",
    show_default=True,
    help="Spark log level.",
)
@click.option(
    "--master", default=DEFAULT_MASTER, show_default=True, help="Spark master URL."
)
@click.option(
    "--driver-memory",
    default=DEFAULT_DRIVER_MEMORY,
    show_default=True,
    help="Memory of the Spark driver, which runs the validation in local mode.",
)
@click.option(
    "--conf",
    multiple=True,
    metavar="KEY=VALUE",
    callback=parse_conf,
    help="Spark configuration property. Repeatable. Overrides the options above.",
)
@click.option(
    "--jar",
    type=click.Path(exists=True, dir_okay=False, resolve_path=True),
    help="Validator JAR to use instead of the one from Maven Central.",
)
def validate(
    input_path,
    output_path,
    extra_args,
    fhir_version,
    igs,
    language,
    tx_server,
    log_progress,
    log_level,
    master,
    driver_memory,
    conf,
    jar,
):
    """Validates the FHIR resources in INPUT, and writes those with issues to OUTPUT.

    INPUT is an NDJSON file, or a directory of them that can be partitioned by filename (e.g.
    INPUT/filename=MimicPatient/part-00000.ndjson). OUTPUT is a Parquet dataset, which is
    replaced if it exists. Each row has the filename, which is INPUT for data that is not
    partitioned, the resource and its issues.

    Arguments after -- are passed to the HL7 FHIR validator, after the validator arguments that
    express the options, e.g. -- -level errors -extension any.

    The validator JAR is resolved from Maven Central, at the version of this command, unless
    --jar is given.
    """
    try:
        run_validation(
            input_path,
            output_path,
            spark_conf=spark_conf(
                package_version=PACKAGE_VERSION,
                master=master,
                driver_memory=driver_memory,
                jar=jar,
                conf=conf,
            ),
            validator_args=validator_args(
                fhir_version=fhir_version,
                igs=igs,
                language=language,
                tx_server=tx_server,
                extra=extra_args,
            ),
            log_progress=log_progress,
            log_level=log_level,
        )
    except ValueError as e:
        raise click.ClickException(str(e)) from e


@main.command()
@click.argument("input_dir", metavar="INPUT", type=click.Path(exists=True))
@click.argument("output_file", metavar="OUTPUT", type=click.Path(dir_okay=False))
@click.option(
    "--partition-by-dir",
    is_flag=True,
    help="INPUT has a subdirectory for each filename, rather than a filename column.",
)
@click.option(
    "--min-level",
    type=int,
    default=3,
    show_default=True,
    help="Minimum level of the issues to include: information=1, warning=2, error=3, fatal=4.",
)
@click.option(
    "--exclude-message",
    multiple=True,
    help="Leave out the messages that match this SQL LIKE pattern. Repeatable.",
)
@click.option(
    "--limit", type=int, help="Maximum number of rows in the report. Defaults to all."
)
def report(input_dir, output_file, partition_by_dir, min_level, exclude_message, limit):
    """Writes an HTML report of the issues in the validation results in INPUT to OUTPUT.

    The issues are grouped by level, type, message identifier and filename, with the number of
    issues and an example resource for each group.
    """
    click.echo(
        f"Reporting the issues in {input_dir} with a minimum level of {min_level}"
    )
    rows = generate_report(
        input_dir,
        output_file,
        min_level=min_level,
        exclude_messages=exclude_message,
        partition_by_dir=partition_by_dir,
        limit=limit,
    )
    click.echo(f"Wrote {rows} rows to {output_file}")
