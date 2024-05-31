#!/usr/bin/env python3
import click
import duckdb
import jinja2

REPORT_QUERY = """
SELECT
    issue.level AS level,
    issue.type AS type,
    issue.message AS message,
    count(*) AS issue_count,
    ANY_VALUE(resource) AS example,
    ANY_VALUE(issue.location) AS location
FROM (SELECT resource, unnest(issues) AS issue FROM report_tbl)
GROUP BY level, type, message
ORDER BY 
    CASE WHEN level='information' THEN 1 WHEN level='warning' then 2 WHEN level='error' then 3 WHEN level='fatal' THEN 4 ELSE 0 END DESC, 
    issue_count DESC, type
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
    </style>
</head>
<body>
    <h1>Validation Report</h1>
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
                <td>{{issue[4]}}</td>
                <td>{{issue[5]}}</td>
            </tr>
        {% endfor %}       
    </table>
</body>
</html>"""


@click.command()
@click.argument('input_file_glob', type=str)
@click.argument('output_file', type=str)
def validation_report(input_file_glob, output_file):
    click.echo(f"Generating validation report from: {input_file_glob}")
    report_tbl = duckdb.read_parquet(input_file_glob)
    issues_tbl = duckdb.sql(REPORT_QUERY)
    issues_headings = issues_tbl.columns
    issues = issues_tbl.fetchall()
    template = jinja2.Template(REPORT_TEMPLATE, autoescape=True, trim_blocks=True, lstrip_blocks=True)
    with open(output_file, 'w') as f:
        f.write(template.render(dict(issues=issues, issues_headings=issues_headings)))
    click.echo(f"Validation report with {len(issues)} issues written to: {output_file}")

if __name__ == '__main__':
    validation_report()
