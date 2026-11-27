# FHIR Validation on Apache Spark

[![Test](https://github.com/aehrc/fhir-validator-spark/workflows/Verify/badge.svg)](https://github.com/aehrc/fhir-validator-spark/actions?query=workflow%3AVerify)

This project provides tools for validating large sets of FHIR resources using Apache Spark parallel processing.
It supports validation of resources in NDJSON format
with [HL7 FHIR Validator](https://github.com/hapifhir/org.hl7.fhir.core).

## Prerequisites

- Java 21
- Spark 4.1.x, built for Scala 2.13
- Maven
- Python 3.10 to 3.12
- Pip



## Installing the Project from the distribution

The binary distribution of the project is available as a tar.gz  file in the GitHub releases.

To install the project from the distribution tar.gz follow these steps:

1. **Download the distribution tar.gz file**:
   - Navigate to the [Releases](https://github.com/aehrc/fhir-validator-spark/releases) page of the project on GitHub.
   - Download the latest release tar.gz file (e.g., `fhir-validator-spark-VERSION-dist.tar.gz`).

2. **Extract the Tar.gz File**:
   - Use the following command to extract the tar.gz file:
     ```sh
     tar -xzf fhir-validator-spark-VERSION-dist.tar.gz
     ```

3. **Navigate to the Extracted Directory**:
   - Change to the directory created by extracting the tar.gz file:
     ```sh
     cd fhir-validator-spark-VERSION/
     ```

## Building the Project

To build the project from sources, run the following command:

```sh
mvn clean install
```

## Validating FHIR Resources

The validation of FHIR resources is a two-step process:

1. Validate the FHIR resources using the command line application (e.g.: `validate-fhir`) to produce a parquet dataset
   with the validation results.
2. Generate a validation report that from the parquet validation results dataset using one of the provided python
   scripts (e.g.: `validation-report-issues.py`).

### Setting up the environment

To set up the environment install the required python packages defined in the `env/requirements.txt` file into your
python environment (conda, virtualenv, etc).

```sh
pip install -r env/requirements.txt
``` 

### Validating a single FHIR resource

`data/mimic-iv-demo-10` contains a small samples of mimic-fhir-demo resources in NDJSON format.

To validate a single FHIR resource (MimicPatient) against the MIMIC-FHIR IG, run the following command:

```sh
  bin/validate-fhir data/mimic-iv-demo-10/MimicPatient.ndjson target/validation-patient --ig data/packages/kindlab.fhir.mimic/package.tgz
```  

The validation results in parquest are stored in the `target/validation-patient` directory.

To generate a validation report that includes all levels of issues, run the following command:

```sh
  bin/validation-report-issues.py target/validation-patient target/report-patient.html --min-level 0
```

Then open the `target/report-patient.html` file in a web browser to see the results.

Please, note that the value of the "File name" column in the report corresponds to the input file name.

### Validating  multiple FHIR resources

`data/mimic-iv-demo-10_partitioned` contains a small samples of mimic-fhir-demo dataset partitioned
by `filename` using the hive partitioning scheme.

To validate the all resources in the `data/mimic-iv-demo-10_partitioned` directory, run the following command:

```sh
  bin/validate-fhir data/mimic-iv-demo-10_partitioned target/validation-result-partitioned
```

The validation results in parquest are stored in the `target/validation-result-partitioned` directory.

To generate a validation report that includes all levels of issues, run the following command:

```sh
  bin/validation-report-issues.py target/validation-result-partitioned target/report-partitioned.html --min-level 0
```

Then open the `target/report-partitioned.html` file in a web browser to see the results.

Please, note that the value of the "File name" column in the report corresponds to the `filename` partition in the
dataset.

## Command line applications

### validate-fhir

Command line application to validate large number of FHIR resources in ndjson format using
Apache Spark for parallel processing
and [HL7 FHIR Validator](https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator) for fhir validation.

The input is a text file in ndjson format with each line containing a FHIR resource or a directory containing such
ndjson files.
Additionally, the intput directory can be partitioned using the hive style partitioning with the `filename` column,
e.g.:

```
dataset/
     filename=MimicPatient/
          part-00000.ndjson
          part-00001.ndjson
          ...
      filename=MimicObservation/
          part-00000.ndjson
          part-00001.ndjson
          ...
      ...
```

For non-partitioned data the `filename` column is added to the dataset with the value of the input file.

The output is a parquet dataset with the following schema:

```
      root
     |-- resource: string (nullable = false)  // FHIR resource
     |-- filename: string (nullable = fase)  // filename of the resource
     |-- issues: array (nullable = true)
     |    |-- element: struct (containsNull = false)
     |    |    |-- level: string (nullable = false) // Issue severity (information, warning, error, fatal)
     |    |    |-- type: string (nullable = false) // Issue type (according to the validator classification)
     |    |    |-- message: string (nullable = false) // Issue message
     |    |    |-- messageId: string (nullable = true) // Issue message id
     |    |    |-- location: string (nullable = true) // Issue location, e.g. the fhirpath expression
     |    |    |-- line: integer (nullable = true) // Issue line number
     |    |    |-- col: integer(nullable = true) // Issue column number
```

To see the available options, run the following command:

```sh
  bin/validate-fhir --help
```

#### Validator arguments

Arguments given after `--` are passed to the argument parser of the HL7 FHIR validator, so `validate-fhir` accepts the
same validation options as the validator version it is built with (6.3.10). See
[Using the FHIR Validator](https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator) for the list of
parameters. For example, to report only errors, allow any extension and report display mismatches as warnings:

```sh
  bin/validate-fhir data/mimic-iv-demo-10/MimicPatient.ndjson target/validation-patient \
    -- -ig data/packages/kindlab.fhir.mimic/package.tgz -level errors -extension any -display-issues-are-warnings
```

To validate every resource against a profile, and to treat best practice recommendations as errors:

```sh
  bin/validate-fhir data/mimic-iv-demo-10/MimicPatient.ndjson target/validation-patient \
    -- -ig data/packages/kindlab.fhir.mimic/package.tgz \
    -profile http://mimic.mit.edu/fhir/mimic/StructureDefinition/mimic-patient -best-practice error
```

The options of `validate-fhir` itself are given to the validator as the equivalent validator arguments, ahead of the
arguments after `--`. Where a validator option takes a single value, the last one wins, so an argument after `--`
overrides the `validate-fhir` option.

| `validate-fhir` option | Validator argument |
|------------------------|--------------------|
| `--fhir-version`       | `-version`         |
| `--ig`                 | `-ig`              |
| `--language`           | `-language`        |
| `--tx-server`          | `-tx`              |

The validator defaults apply, except for two settings that would make the validator send network requests while it
validates resources:

- No terminology server is used (`-tx n/a`), where the validator would use `tx.fhir.org`. Use `--tx-server <url>` or
  `-- -tx <url>` to use one.
- The default resource fetcher is turned off (`-disable-default-resource-fetcher`), so canonical URLs that the loaded
  packages do not define, such as a profile in `meta.profile`, are not fetched from the network. Load the packages
  that define them with `-ig`. The validator has no argument to turn the fetcher back on, so this cannot be
  overridden.

The FHIR version defaults to R4 (`4.0.1`).

Validator arguments that have no effect when validating individual resources in Spark are rejected with an error
before validation starts. These are input files (the input is read by Spark), other validator modes such as
`-convert`, `-compare` or `-snapshot`, output and log files such as `-output`, `-html-output` or `-txLog`, process
settings such as `-proxy`, `-fhir-settings` or `-watch-mode`, and options that the validator only applies when it
reads files itself (`-native`, `-do-native`, `-no-native` and `-show-times`). Arguments that the validator does not
recognise are rejected in the same way. Option values are interpreted by the validator: for example, an unknown
`-level` value is read as `hints`.

Before version 2.0.0, `validate-fhir` always reported display mismatches in codings as warnings. They are now
errors, which is the validator default; use `-- -display-issues-are-warnings` to report them as warnings. A
terminology server given with `--tx-server` that cannot be reached now fails the validation, where it was previously
ignored. The validator also loads the `hl7.terminology` and `hl7.fhir.uv.extensions` packages, as the validator
command line does, so these must be in the FHIR package cache or downloadable when validation starts.

The application is implemented in the `au.csiro.fhir.validation.cli.ValidateApp` class.

See also:

- <a href="https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator">HL7 FHIR Validator</a>
- <a href="https://github.com/hapifhir/org.hl7.fhir.core">HL7 FHIR Core tools</a>

## Report generation


### validation-report-issues.py

Generates a single report file with  the issues are aggregated by: 
- level
- type
- message_id
- filename

For each group an example is provided (including the actual message, the location
of the issue and the json representation of the resource) as well as the count of the issues.

Command line options can be used to:
- filter the issues by level (`--min-level`)
- limit the total number of issues in the report  (`--limit`)
- exclude messages matching the given SQL `LIKE` pattern(s) (`--exclude-message`)

To see the available options, run the following command:
```sh
  bin/validation-report-issues.py --help
```

## Important note

This software is currently in alpha. It is not yet ready for production use.

Copyright © 2024, Commonwealth Scientific and Industrial Research Organisation (CSIRO) ABN 41 687 119 230. Licensed
under the [Apache License, version 2.0](./LICENSE).
