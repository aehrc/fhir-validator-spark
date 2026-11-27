"""
Integration tests that run the installed fhir-validator command with Spark and the validator JAR
given in the FHIR_VALIDATOR_JAR environment variable (e.g. target/fhir-validator-spark-*.jar,
built with `mvn package`). They are skipped when it is not set.

Author: John Grimes
"""

import os
import subprocess
import sys
from pathlib import Path

import pyarrow.parquet as pq
import pytest

REPO = Path(__file__).resolve().parents[2]
# Resolved here, as the command runs from the root of the repository.
JAR = os.environ.get("FHIR_VALIDATOR_JAR") and str(
    Path(os.environ["FHIR_VALIDATOR_JAR"]).resolve()
)
COMMAND = str(Path(sys.executable).parent / "fhir-validator")
MEDICATION_REQUEST = "src/test/resources/ndjson/MedicationRequest_R4.ndjson"
MIMIC_PATIENT = "data/mimic-iv-demo-10/MimicPatient.ndjson"
MIMIC_IG = "data/packages/kindlab.fhir.mimic/package.tgz"

pytestmark = pytest.mark.skipif(not JAR, reason="FHIR_VALIDATOR_JAR is not set")


def run(*args):
    """Runs the command from the root of the repository, where the test data is."""
    return subprocess.run(
        [COMMAND, *args],
        cwd=REPO,
        capture_output=True,
        text=True,
        timeout=900,
        check=False,
    )


def validate(input_path, output, *args):
    result = run(
        "validate", input_path, str(output), "--jar", JAR, "--master", "local[2]", *args
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return pq.read_table(output)


def issue_levels(results):
    return [
        issue["level"]
        for issues in results.column("issues").to_pylist()
        for issue in issues
    ]


@pytest.fixture(scope="module")
def default_results(tmp_path_factory):
    """Validation results for MedicationRequest resources, with the default validator settings."""
    output = tmp_path_factory.mktemp("default") / "validation"
    return output, validate(MEDICATION_REQUEST, output)


def test_validate_reports_warnings_by_default(default_results):
    # Control for the -level errors test below.
    _, results = default_results
    assert "warning" in issue_levels(results)


def test_arguments_after_double_dash_reach_the_validator(tmp_path):
    results = validate(
        MEDICATION_REQUEST, tmp_path / "validation", "--", "-level", "errors"
    )
    # Without -level errors the validator reports warnings for these resources (see above).
    assert set(issue_levels(results)) <= {"error", "fatal"}


def test_validate_with_an_ig_names_results_after_the_input(tmp_path):
    # The MIMIC profiles are only known to the validator when the IG is loaded.
    results = validate(
        MIMIC_PATIENT,
        tmp_path / "validation",
        "--ig",
        MIMIC_IG,
        "--",
        "-profile",
        "http://mimic.mit.edu/fhir/mimic/StructureDefinition/mimic-patient",
    )
    assert results.column_names == ["filename", "issues", "resource"]
    assert results.num_rows > 0
    assert set(results.column("filename").to_pylist()) == {MIMIC_PATIENT}


def test_unsupported_validator_argument_fails_before_validation(tmp_path):
    output = tmp_path / "validation"
    result = run(
        "validate", MIMIC_PATIENT, str(output), "--jar", JAR, "--", "-output", "x"
    )
    assert result.returncode == 1
    assert (
        "Error: Validator argument(s) not supported when validating in Spark: -output"
        in (result.stderr)
    )
    assert "Traceback" not in result.stderr
    assert not output.exists()


def test_report_on_validation_results(default_results, tmp_path):
    output, _ = default_results
    report = tmp_path / "report.html"
    result = run("report", str(output), str(report), "--min-level", "0")
    assert result.returncode == 0, result.stdout + result.stderr
    html = report.read_text()
    assert f"Validation Report for: {output}" in html
    assert "<td>warning</td>" in html
    assert f"<td>{MEDICATION_REQUEST}</td>" in html
