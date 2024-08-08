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
    ANY_VALUE(issue.col) AS col,
    CASE WHEN level='information' THEN 1 WHEN level='warning' then 2 WHEN level='error' then 3 WHEN level='fatal' THEN 4 ELSE 0 END AS level_order
FROM (SELECT resource, unnest(issues) AS issue FROM report_tbl WHERE filename = '{filename}')
WHERE  level_order >= {min_level_order}
GROUP BY level, type, message
ORDER BY 
    level_order DESC, issue_count DESC, type
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
            {% for heading in issues_headings %}
                <th>{{heading}}</th>
            {% endfor %}
        </tr>
        {% for issue in issues %}
            <tr>
                <td>{{issue[0]}}</td>
                <td>{{issue[1]}}</td>
                <td>{{issue[2]}}</td>
                <td>{{issue[3]}}</td>
                <td>{{higlight(issue[4], issue[6])}}</td>
                <td>{{issue[5]}}</td>
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


SUMMARY_TEMPLATE = """<!DOCTYPE html>
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
    
        table#files {
            width: fit-content;
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
    <h2>Validation Report for: {{input_dir}}</h2>
    <table id="files">
        <tr>
            <th>Filename</th>
            <th>#Fatal</th>
            <th>#Error</th>
            <th>#Warning</th>
            <th>#Info</th>
        </tr>
        {% for row in rows %}
            <tr>
                <td><a href="{{row.filename}}.html">{{row.filename}}</a></td>
                <td>{{zero_as_dash(row.fatal)}}</td>
                <td>{{zero_as_dash(row.error)}}</td>
                <td>{{zero_as_dash(row.warning)}}</td>
                <td>{{zero_as_dash(row.information)}}</td>
            </tr>
        {% endfor %}       
    </table>
</body>
</html>"""


@click.command()
@click.argument('input_dir', type=str)
@click.argument('output_dir', type=str)
@click.option('--min-level', default=3,
              help='Minimum level to include in the report. Information=1, Warning=2, Error=3, Fatal=4. Default=3')
@click.option('--partition-by-dir', help='Partition the input directory by directory. Default=False', is_flag=True)
def validation_report(input_dir, output_dir, min_level, partition_by_dir):
    click.echo(f"Generating validation report from: {input_dir} to: {output_dir}, with min level: {min_level}")
    partitioning = ds.DirectoryPartitioning(pa.schema([('filename',
                                                        pa.string())])) if partition_by_dir else None
    report_tbl = ds.dataset(input_dir, format='parquet', partitioning=partitioning)
    summary_df = duckdb.sql(SUMMARY_QUERY)
    SummaryRow = namedtuple('SummaryRow', summary_df.columns)
    summary_rows = list(map(lambda r: SummaryRow(*r), summary_df.fetchall()))
    summary_template = jinja2.Template(SUMMARY_TEMPLATE, autoescape=True, trim_blocks=True, lstrip_blocks=True)
    os.makedirs(output_dir, exist_ok=True)
    index_file = os.path.join(output_dir, 'index.html')
    click.echo(f"Writing index to: {index_file}")
    with open(index_file, 'w') as f:
        f.write(summary_template.render(dict(input_dir=input_dir, rows=summary_rows, zero_as_dash=zero_as_dash)))
    filenames = [fr for fr, in duckdb.sql("SELECT DISTINCT filename FROM report_tbl ORDER BY  filename").fetchall()]
    for filename in filenames:
        click.echo(f"Generating report for: {filename}")
        issues_tbl = duckdb.sql(REPORT_QUERY.format(filename=filename, min_level_order=min_level))
        issues_headings = issues_tbl.columns[0:6]
        issues = issues_tbl.fetchall()
        template = jinja2.Template(REPORT_TEMPLATE, autoescape=True, trim_blocks=True, lstrip_blocks=True)
        file_report = os.path.join(output_dir, f'{filename}.html')
        click.echo(f"Writing report to: {file_report}")
        with open(file_report, 'w') as f:
            f.write(template.render(
                dict(issues=issues, issues_headings=issues_headings, higlight=higlight, input_file=filename)))


if __name__ == '__main__':
    validation_report()
