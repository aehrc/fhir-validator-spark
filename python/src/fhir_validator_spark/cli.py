"""The fhir-validator command."""

import click

from fhir_validator_spark.validation import run_validation  # noqa: F401


@click.group()
def main():
    """Validates FHIR resources on Spark."""
