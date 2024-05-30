SELECT
    issue.level AS level,
    issue.type AS type,
    issue.message AS message,
    count(*) AS issue_count,
    ANY_VALUE(resource) AS example,
    ANY_VALUE(issue.location) AS location
FROM (SELECT resource, unnest(issues) AS issue FROM read_parquet('../_tmp/MC_fast.parquet/*.parquet'))
GROUP BY level, type, message;