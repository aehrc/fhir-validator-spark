"""
HTML report of the issues in validation results, aggregated by level, type, message identifier
and filename, with an example resource and the number of issues in each group.

Author: Piotr Szul
Author: John Grimes
"""

from collections import namedtuple
from collections.abc import Sequence

import duckdb
import jinja2
import pyarrow as pa
import pyarrow.dataset as ds
from markupsafe import Markup, escape

LEVEL_ORDER = """
    CASE issue.level
        WHEN 'information' THEN 1 WHEN 'warning' THEN 2 WHEN 'error' THEN 3 WHEN 'fatal' THEN 4
        ELSE 0
    END"""

REPORT_QUERY = f"""
SELECT
    issue.level AS level,
    issue.type AS type,
    ANY_VALUE(issue.message) AS message,
    count(*) AS issue_count,
    ANY_VALUE(resource) AS example,
    ANY_VALUE(issue.location) AS location,
    filename AS filename,
    ANY_VALUE(issue.col) AS col,
    {LEVEL_ORDER} AS level_order,
    issue.messageId AS message_id
FROM (SELECT filename, resource, unnest(issues) AS issue FROM report_tbl)
WHERE {LEVEL_ORDER} >= ? {{filter_clause}}
GROUP BY level, type, message_id, filename
ORDER BY level_order DESC, type, message_id, message, filename, issue_count DESC
"""

REPORT_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
    <title>Validation Report</title>
        <style>
        body {
            font-family: Verdana, sans-serif;
            font-size: small;
        }
        table {
            border-collapse: collapse;
            width: 100%;
        }

        th, td {
            border: 1px solid black;
            padding: 8px;
            text-align: left;
        }

        th {
            background-color: #f2f2f2;
        }

        tr:nth-child(even) {
            background-color: #f2f2f2;
        }
        mark {
            background-color: red;
            color: black;
        }
    </style>
</head>
<body>
    <h1>Validation Report for: {{input_file}}</h1>
    <table id="issues">
        <tr>
            <th>Level</th>
            <th>Type</th>
            <th>MessageId</th>
            <th>Message</th>
            <th>Example</th>
            <th>Location</th>
            <th>File Name</th>
            <th>Count</th>
        </tr>
        {% for issue in issues %}
            <tr>
                <td>{{issue.level}}</td>
                <td>{{issue.type}}</td>
                <td>{{issue.message_id}}</td>
                <td>{{issue.message}}</td>
                <td>{{highlight(issue.example, issue.col)}}</td>
                <td>{{issue.location}}</td>
                <td>{{issue.filename}}</td>
                <td>{{issue.issue_count}}</td>
            </tr>
        {% endfor %}
    </table>
</body>
</html>"""


def highlight(text: str, column: int | None) -> Markup:
    """
    Escapes text for HTML, and marks the character at a column.

    :param text: the text, e.g. the JSON of a resource
    :param column: the index of the character to mark, or None
    :returns: the escaped text, with the character at the column in a ``mark`` element if the
        column is within the text
    """
    if column is None or column < 0 or column >= len(text):
        return escape(text)
    return Markup(
        f"{escape(text[:column])}<mark>{escape(text[column])}</mark>{escape(text[column + 1 :])}"
    )


def generate_report(
    input_dir: str,
    output_file: str,
    min_level: int = 3,
    exclude_messages: Sequence[str] = (),
    partition_by_dir: bool = False,
    limit: int | None = None,
) -> int:
    """
    Writes an HTML report of the issues in a Parquet dataset of validation results.

    :param input_dir: the directory of the validation results
    :param output_file: the HTML file to write
    :param min_level: the minimum level of the issues to include: information=1, warning=2,
        error=3, fatal=4
    :param exclude_messages: SQL ``LIKE`` patterns of the messages to leave out
    :param partition_by_dir: whether the input has a subdirectory for each filename, rather than
        a filename column
    :param limit: the maximum number of rows of the report, or None for no limit
    :returns: the number of rows of the report
    """
    partitioning = (
        ds.DirectoryPartitioning(pa.schema([("filename", pa.string())]))
        if partition_by_dir
        else None
    )
    connection = duckdb.connect()
    connection.register(
        "report_tbl", ds.dataset(input_dir, format="parquet", partitioning=partitioning)
    )
    filter_clause = "".join(" AND issue.message NOT LIKE ?" for _ in exclude_messages)
    issues = connection.execute(
        REPORT_QUERY.format(filter_clause=filter_clause), [min_level, *exclude_messages]
    )
    issue_row = namedtuple("IssueRow", [column[0] for column in issues.description])
    rows = issues.fetchmany(limit) if limit else issues.fetchall()
    template = jinja2.Template(
        REPORT_TEMPLATE, autoescape=True, trim_blocks=True, lstrip_blocks=True
    )
    with open(output_file, "w") as f:
        f.write(
            template.render(
                input_file=input_dir,
                issues=[issue_row(*row) for row in rows],
                highlight=highlight,
            )
        )
    return len(rows)
