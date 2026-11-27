"""
Tests for the command line parsing of the validate command, with the validation itself replaced.

Author: John Grimes
"""

from importlib.metadata import version

import pytest
from click.testing import CliRunner

from fhir_validator_spark import cli
from fhir_validator_spark.validation import spark_conf

PACKAGE_VERSION = version("fhir-validator-spark")


@pytest.fixture
def runs(monkeypatch):
    """Replaces the validation with a recorder of the arguments it is called with."""
    calls = []
    monkeypatch.setattr(cli, "run_validation", lambda *args, **kwargs: calls.append((args, kwargs)))
    return calls


def invoke(*args):
    return CliRunner().invoke(cli.main, list(args))


# --- Validation options ---


def test_validate_uses_defaults_without_options(runs):
    result = invoke("validate", "in.ndjson", "out")
    assert result.exit_code == 0, result.output
    assert runs == [
        (
            ("in.ndjson", "out"),
            {
                "spark_conf": spark_conf(package_version=PACKAGE_VERSION),
                "validator_args": [],
                "log_progress": False,
                "log_level": "WARN",
            },
        )
    ]


def test_validate_accepts_the_options_of_validate_fhir(runs):
    # The short and long option names of the previous validate-fhir command still work.
    result = invoke(
        "validate",
        "in.ndjson",
        "out",
        "-v",
        "5.0.0",
        "-i",
        "a.tgz",
        "--ig",
        "b.tgz",
        "-l",
        "de",
        "-tx",
        "http://tx.example.org",
        "-p",
        "-d",
        "INFO",
    )
    assert result.exit_code == 0, result.output
    kwargs = runs[0][1]
    assert kwargs["validator_args"] == [
        "-version",
        "5.0.0",
        "-ig",
        "a.tgz",
        "-ig",
        "b.tgz",
        "-language",
        "de",
        "-tx",
        "http://tx.example.org",
    ]
    assert kwargs["log_progress"] is True
    assert kwargs["log_level"] == "INFO"


def test_validate_forwards_arguments_after_double_dash(runs):
    result = invoke(
        "validate", "in.ndjson", "out", "--ig", "a.tgz", "--", "-level", "errors", "-extension", "any"
    )
    assert result.exit_code == 0, result.output
    assert runs[0][1]["validator_args"] == [
        "-ig",
        "a.tgz",
        "-level",
        "errors",
        "-extension",
        "any",
    ]


@pytest.mark.parametrize("argument", ["-level", "-ig", "-display-issues-are-warnings"])
def test_validator_argument_before_double_dash_is_rejected(runs, argument):
    # Without this check, "-level" would be read as "-l evel" and "-ig" as "-i g".
    result = invoke("validate", "in.ndjson", "out", argument, "errors")
    assert result.exit_code == 2
    assert f"No such option: {argument}" in result.output
    assert "after --" in result.output
    assert runs == []


# --- Spark options ---


def test_validate_passes_spark_options(runs, tmp_path):
    jar = tmp_path / "validator.jar"
    jar.touch()
    result = invoke(
        "validate",
        "in.ndjson",
        "out",
        "--master",
        "spark://host:7077",
        "--driver-memory",
        "2g",
        "--conf",
        "spark.executor.memory=4g",
        "--conf",
        "spark.sql.shuffle.partitions=8",
        "--jar",
        str(jar),
    )
    assert result.exit_code == 0, result.output
    assert runs[0][1]["spark_conf"] == spark_conf(
        package_version=PACKAGE_VERSION,
        master="spark://host:7077",
        driver_memory="2g",
        jar=str(jar),
        conf=[("spark.executor.memory", "4g"), ("spark.sql.shuffle.partitions", "8")],
    )


def test_relative_jar_path_is_made_absolute(runs, tmp_path, monkeypatch):
    # Spark executors on other hosts cannot resolve a path relative to the working directory.
    monkeypatch.chdir(tmp_path)
    (tmp_path / "validator.jar").touch()
    result = invoke("validate", "in.ndjson", "out", "--jar", "validator.jar")
    assert result.exit_code == 0, result.output
    assert runs[0][1]["spark_conf"]["spark.jars"] == str(tmp_path / "validator.jar")


def test_conf_without_value_is_rejected(runs):
    result = invoke("validate", "in.ndjson", "out", "--conf", "spark.executor.memory")
    assert result.exit_code == 2
    assert "KEY=VALUE" in result.output
    assert runs == []


def test_missing_jar_is_rejected(runs, tmp_path):
    result = invoke("validate", "in.ndjson", "out", "--jar", str(tmp_path / "missing.jar"))
    assert result.exit_code == 2
    assert "does not exist" in result.output
    assert runs == []


# --- Errors and version ---


def test_validation_error_is_reported_without_a_traceback(monkeypatch):
    def fail(*args, **kwargs):
        raise ValueError("Validator argument(s) not supported when validating in Spark: -output")

    monkeypatch.setattr(cli, "run_validation", fail)
    result = invoke("validate", "in.ndjson", "out", "--", "-output", "x")
    assert result.exit_code == 1
    assert "Error: Validator argument(s) not supported when validating in Spark: -output" in (
        result.output
    )
    assert "Traceback" not in result.output


def test_version_names_the_package_and_validator_versions():
    result = invoke("--version")
    assert result.exit_code == 0
    assert PACKAGE_VERSION in result.output
    assert "au.csiro.fhir:fhir-validator-spark:" in result.output
