"""
Tests for the translation of the options of the validate command into validator arguments and Spark
configuration.

Author: John Grimes
"""

import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest

from fhir_validator_spark.validation import maven_version, spark_conf, validator_args

REPO = Path(__file__).resolve().parents[2]


# --- Maven version of the validator JAR ---


@pytest.mark.parametrize(
    ("package_version", "expected"),
    [
        ("2.0.0", "2.0.0"),
        ("2.0.0.dev0", "2.0.0-SNAPSHOT"),
        ("2.1.3.dev12", "2.1.3-SNAPSHOT"),
    ],
)
def test_maven_version_maps_development_versions_to_snapshots(package_version, expected):
    # A release uses the JAR of the same version; a development version uses the SNAPSHOT.
    assert maven_version(package_version) == expected


def test_package_version_matches_pom_version():
    # The JAR that the package uses by default must be the one built from the same source.
    pyproject = tomllib.loads((REPO / "python" / "pyproject.toml").read_text())
    pom = ET.parse(REPO / "pom.xml").getroot()
    pom_version = pom.find("{http://maven.apache.org/POM/4.0.0}version").text
    assert maven_version(pyproject["project"]["version"]) == pom_version


# --- Validator arguments ---


def test_validator_args_are_empty_without_options():
    assert validator_args() == []


def test_validator_args_express_options_ahead_of_pass_through_arguments():
    # The options come first, so that a pass-through argument overrides an option.
    args = validator_args(
        fhir_version="5.0.0",
        igs=["a.tgz", "b.tgz"],
        language="de",
        tx_server="http://tx.example.org",
        extra=["-level", "errors", "-tx", "n/a"],
    )
    assert args == [
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
        "-level",
        "errors",
        "-tx",
        "n/a",
    ]


# --- Spark configuration ---


def test_spark_conf_defaults_to_local_mode_and_the_jar_from_maven_central():
    assert spark_conf(package_version="2.0.0") == {
        "spark.app.name": "fhir-validator",
        "spark.master": "local[*]",
        "spark.driver.memory": "8g",
        "spark.jars.packages": "au.csiro.fhir:fhir-validator-spark:2.0.0",
    }


def test_spark_conf_resolves_the_snapshot_jar_for_a_development_version():
    conf = spark_conf(package_version="2.0.0.dev0")
    assert conf["spark.jars.packages"] == "au.csiro.fhir:fhir-validator-spark:2.0.0-SNAPSHOT"


def test_spark_conf_uses_a_local_jar_instead_of_the_package():
    conf = spark_conf(package_version="2.0.0", jar="/opt/fhir-validator-spark.jar")
    assert conf["spark.jars"] == "/opt/fhir-validator-spark.jar"
    assert "spark.jars.packages" not in conf


def test_spark_conf_sets_master_and_driver_memory():
    conf = spark_conf(package_version="2.0.0", master="spark://host:7077", driver_memory="2g")
    assert conf["spark.master"] == "spark://host:7077"
    assert conf["spark.driver.memory"] == "2g"


def test_spark_conf_entries_are_added_and_override_options():
    # Arbitrary configuration is applied last, so it wins over the dedicated options.
    conf = spark_conf(
        package_version="2.0.0",
        driver_memory="2g",
        conf=[("spark.executor.memory", "4g"), ("spark.driver.memory", "16g")],
    )
    assert conf["spark.executor.memory"] == "4g"
    assert conf["spark.driver.memory"] == "16g"
