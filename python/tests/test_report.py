"""
Tests for the report command, run on small validation results written with pyarrow.

Author: John Grimes
"""

import re

import pyarrow as pa
import pyarrow.parquet as pq
import pytest
from click.testing import CliRunner

from fhir_validator_spark import cli
from fhir_validator_spark.report import highlight

ISSUE = pa.struct(
    [
        ("col", pa.int32()),
        ("level", pa.string()),
        ("line", pa.int32()),
        ("location", pa.string()),
        ("message", pa.string()),
        ("messageId", pa.string()),
        ("type", pa.string()),
    ]
)
RESULTS = pa.schema(
    [("filename", pa.string()), ("issues", pa.list_(ISSUE)), ("resource", pa.string())]
)


def issue(level, message, message_id, col=None):
    return {
        "col": col,
        "level": level,
        "line": 1,
        "location": "Patient",
        "message": message,
        "messageId": message_id,
        "type": "structure",
    }


NAME_MISSING = issue("error", "Patient's name is missing", "NAME", col=1)
ID_INVALID = issue("error", "Invalid id", "ID")
NO_NARRATIVE = issue("warning", "Consider adding a narrative", "NARRATIVE")
NO_PROFILE = issue("information", "No profile declared", "PROFILE")
STATUS_FATAL = issue("fatal", "Unknown status", "STATUS")


@pytest.fixture
def results(tmp_path):
    """A validation output directory as Spark writes it, with issues at every level."""
    output = tmp_path / "validation"
    output.mkdir()
    rows = [
        {
            "filename": "Patient.ndjson",
            "resource": '{"resourceType":"Patient","id":"p1"}',
            "issues": [NAME_MISSING, NO_NARRATIVE, NO_PROFILE],
        },
        {
            "filename": "Patient.ndjson",
            "resource": '{"resourceType":"Patient","id":"p2"}',
            "issues": [NAME_MISSING, ID_INVALID],
        },
        {
            "filename": "Encounter.ndjson",
            "resource": '{"resourceType":"Encounter"}',
            "issues": [STATUS_FATAL],
        },
    ]
    pq.write_table(pa.Table.from_pylist(rows, schema=RESULTS), output / "part-00000.parquet")
    # Spark also writes these, which are not Parquet files.
    (output / "_SUCCESS").touch()
    (output / ".part-00000.parquet.crc").write_bytes(b"crc")
    return output


def report(input_dir, tmp_path, *options):
    """Runs the report command and returns the rows of the issues table, as lists of cells."""
    output = tmp_path / "report.html"
    result = CliRunner().invoke(cli.main, ["report", str(input_dir), str(output), *options])
    assert result.exit_code == 0, result.output
    html = output.read_text()
    rows = re.findall(r"<tr>(.*?)</tr>", html, re.DOTALL)[1:]
    return html, [re.findall(r"<td>(.*?)</td>", row, re.DOTALL) for row in rows]


def column(rows, index):
    return [row[index] for row in rows]


LEVEL, MESSAGE_ID, MESSAGE, EXAMPLE, FILENAME, COUNT = 0, 2, 3, 4, 6, 7


# --- Content of the report ---


def test_title_names_the_input(results, tmp_path):
    # The title was blank before, as the template was not given the input.
    html, _ = report(results, tmp_path)
    assert f"<h1>Validation Report for: {results}</h1>" in html


def test_issues_are_grouped_by_message_and_counted(results, tmp_path):
    _, rows = report(results, tmp_path)
    name_rows = [row for row in rows if row[MESSAGE_ID] == "NAME"]
    assert len(name_rows) == 1
    assert name_rows[0][COUNT] == "2"
    assert name_rows[0][FILENAME] == "Patient.ndjson"
    assert name_rows[0][MESSAGE] == "Patient&#39;s name is missing"


def test_issues_are_ordered_from_most_to_least_severe(results, tmp_path):
    _, rows = report(results, tmp_path, "--min-level", "0")
    assert column(rows, LEVEL) == ["fatal", "error", "error", "warning", "information"]


def test_example_highlights_the_column_of_the_issue(results, tmp_path):
    _, rows = report(results, tmp_path)
    name_row = next(row for row in rows if row[MESSAGE_ID] == "NAME")
    assert "<mark>&#34;</mark>resourceType" in name_row[EXAMPLE]


# --- Options ---


def test_default_min_level_includes_errors_and_fatal_issues(results, tmp_path):
    _, rows = report(results, tmp_path)
    assert sorted(column(rows, MESSAGE_ID)) == ["ID", "NAME", "STATUS"]


@pytest.mark.parametrize(
    ("min_level", "levels"),
    [
        ("0", {"fatal", "error", "warning", "information"}),
        ("2", {"fatal", "error", "warning"}),
        ("4", {"fatal"}),
    ],
)
def test_min_level_filters_issues(results, tmp_path, min_level, levels):
    _, rows = report(results, tmp_path, "--min-level", min_level)
    assert set(column(rows, LEVEL)) == levels


def test_exclude_message_pattern_with_an_apostrophe(results, tmp_path):
    # A pattern with a quote broke the query before, as it was spliced into the SQL.
    _, rows = report(results, tmp_path, "--exclude-message", "Patient's%")
    assert sorted(column(rows, MESSAGE_ID)) == ["ID", "STATUS"]


def test_exclude_message_can_be_repeated(results, tmp_path):
    _, rows = report(
        results, tmp_path, "--exclude-message", "%name%", "--exclude-message", "Unknown%"
    )
    assert column(rows, MESSAGE_ID) == ["ID"]


def test_limit_caps_the_number_of_rows(results, tmp_path):
    _, rows = report(results, tmp_path, "--min-level", "0", "--limit", "2")
    assert column(rows, LEVEL) == ["fatal", "error"]


def test_partition_by_dir_takes_the_filename_from_the_directory(tmp_path):
    # Each subdirectory holds the results of one input, without a filename column.
    schema = pa.schema([field for field in RESULTS if field.name != "filename"])
    for name in ["MimicPatient", "MimicEncounter"]:
        directory = tmp_path / "validation" / name
        directory.mkdir(parents=True)
        rows = [{"resource": "{}", "issues": [ID_INVALID]}]
        pq.write_table(pa.Table.from_pylist(rows, schema=schema), directory / "part.parquet")
    _, rows = report(tmp_path / "validation", tmp_path, "--partition-by-dir")
    assert sorted(column(rows, FILENAME)) == ["MimicEncounter", "MimicPatient"]


def test_empty_results_give_a_report_without_issues(tmp_path):
    output = tmp_path / "validation"
    output.mkdir()
    pq.write_table(RESULTS.empty_table(), output / "part-00000.parquet")
    html, rows = report(output, tmp_path)
    assert rows == []
    assert "<table" in html


# --- Highlighting ---


def test_highlight_marks_the_character_at_the_column_and_escapes_the_text():
    assert str(highlight("<a>", 1)) == "&lt;<mark>a</mark>&gt;"


@pytest.mark.parametrize("col", [None, -1, 3])
def test_highlight_without_a_column_in_the_text_only_escapes(col):
    assert str(highlight("<a>", col)) == "&lt;a&gt;"
