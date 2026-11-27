# fhir-validator-spark

The `fhir-validator` command validates FHIR resources in NDJSON format with the
[HL7 FHIR Validator](https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator) on Apache Spark, and
generates an HTML report of the issues found.

```sh
uv tool install fhir-validator-spark
fhir-validator validate data/mimic-iv-demo-10 target/validation --ig package.tgz
fhir-validator report target/validation target/report.html --min-level 0
```

It needs Java 21. See the [project README](https://github.com/aehrc/fhir-validator-spark#readme) for the options and
examples.
