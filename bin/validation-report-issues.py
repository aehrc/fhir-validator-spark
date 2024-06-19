#!/usr/bin/env python3
from collections import namedtuple
import os
import click
import duckdb
import pyarrow.dataset as ds
import pyarrow as pa
import jinja2
from markupsafe import Markup, escape

REPORT_QUERY = """
SELECT
    issue.level AS level,
    issue.type AS type,
    issue.message AS message,
    count(*) AS issue_count,
    ANY_VALUE(resource) AS example,
    ANY_VALUE(issue.location) AS location,
    filename AS filename,
    ANY_VALUE(issue.col) AS col,
    CASE WHEN level='information' THEN 1 WHEN level='warning' then 2 WHEN level='error' then 3 WHEN level='fatal' THEN 4 ELSE 0 END AS level_order
FROM (SELECT filename, resource, unnest(issues) AS issue FROM report_tbl)
WHERE  level_order >= {min_level_order} {filter_clause}
GROUP BY level, type, message, filename
ORDER BY 
    level_order DESC, type, message, filename, issue_count DESC
"""


def higlight(text, column):
    if (column is None) or (column < 0) or (column >= len(text)):
        return Markup(escape(text))
    pre = text[:column]
    focus = text[column]
    post = text[column + 1:]
    return Markup(f'{escape(pre)}<mark>{escape(focus)}</mark>{escape(post)}')


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
                <td>{{issue.message}}</td>
                <td>{{higlight(issue.example, issue.col)}}</td>
                <td>{{issue.location}}</td>
                <td>{{issue.filename}}</td>
                <td>{{issue.issue_count}}</td>
            </tr>
        {% endfor %}       
    </table>
</body>
</html>"""

SUMMARY_QUERY = """
PIVOT (SELECT filename, issue.level as level
    FROM (SELECT filename, unnest(issues) AS issue FROM report_tbl))
    ON level in ('fatal', 'error', 'warning', 'information')
    ORDER BY filename
"""


def zero_as_dash(value):
    return "-" if value == 0 else value


@click.command()
@click.argument('input_dir', type=str)
@click.argument('output_file', type=str)
@click.option('--partition-by-dir', help='Partition the input directory by directory. Default=False', is_flag=True)
@click.option('--min-level', default=3,
              help='Minimum level to include in the report. Information=1, Warning=2, Error=3, Fatal=4. Default=3')
@click.option('--exclude-message', multiple=True, help='Exclude messages containing the provided strings')
@click.option('--limit', help='The max number of issues to include in the report. Default=-1', default=None, type=int)
def validation_report(input_dir, output_file, min_level, exclude_message, partition_by_dir, limit):
    click.echo(f"Generating issue validation report from: {input_dir} to: {output_file}, with min level: {min_level}")
    partitioning = ds.DirectoryPartitioning(pa.schema([('filename',
                                                        pa.string())])) if partition_by_dir else None  # ds.FilenamePartitioning(pa.schema([('_partname', pa.string())]))
    report_tbl = ds.dataset(input_dir, format='parquet', partitioning=partitioning)

    message_filters = ' AND '.join([f"message NOT LIKE '{m}'" for m in exclude_message])
    filter_clause = f" AND {message_filters}" if message_filters else ""
    click.echo(f"Filtering out messages: {filter_clause}")

    issues_tbl = duckdb.sql(REPORT_QUERY.format(min_level_order=min_level, filter_clause=filter_clause))
    IssueRow = namedtuple('IssuRow', issues_tbl.columns)
    issue_rows = list(map(lambda r: IssueRow(*r), issues_tbl.fetchmany(size=limit) if limit else issues_tbl.fetchall()))
    template = jinja2.Template(REPORT_TEMPLATE, autoescape=True, trim_blocks=True, lstrip_blocks=True)
    click.echo(f"Writing report to: {output_file}")
    with open(output_file, 'w') as f:
        f.write(template.render(dict(issues=issue_rows, higlight=higlight)))


if __name__ == '__main__':
    validation_report()
