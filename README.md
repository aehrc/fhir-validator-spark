# FHIR Validation on Apache Spark

[![Test](https://github.com/aehrc/fhir-validator-spark/workflows/Verify/badge.svg)](https://github.com/aehrc/fhir-validator-spark/actions?query=workflow%3AVerify)

This project provides tools for validating large sets of FHIR resources using Apache Spark parallel processing.
It supports validation of resources in NDJSON format
with [HL7 FHIR Validator](https://github.com/hapifhir/org.hl7.fhir.core).

## Prerequisites

- Java 17 or later (built for Java 17 bytecode, tested on Java 21)
- Spark 4.0.x, built for Scala 2.13
- Maven
- Python 3.9 or later
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

This writes the shaded jar to `target/fhir-validator-spark-VERSION-all.jar`. That jar is what
`bin/validate-fhir` submits, and what to put on the classpath of an application that calls the
Java API.

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


The application is implemented in the `au.csiro.fhir.validation.cli.ValidateApp` class.

See also:

- <a href="https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator">HL7 FHIR Validator</a>
- <a href="https://github.com/hapifhir/org.hl7.fhir.core">HL7 FHIR Core tools</a>

## Validating from an existing Spark session

`au.csiro.fhir.validation.spark.ValidationApi` validates resources in a session that something
else owns, for example the session of an application that has just produced the resources. It is
the same validator the command line application uses, and it adds the same `issues` column, so
either producer's output can be read by the report scripts.

```java
final ValidationConfig config = ValidationConfig.builder()
        .ig("data/packages/kindlab.fhir.mimic/package.tgz")
        .terminologyServerUrl("https://tx.fhir.org/r4")
        .build();
final ValidationApi api = ValidationApi.create(existingSession, config);

// Either validate a dataset of resource JSON, keeping its other columns,
final Dataset<Row> results = api.validate(resources);

// or validate every NDJSON resource under a path.
final Dataset<Row> fromPath = api.validatePath("data/mimic-iv-demo-10");
```

`ValidationConfig` carries the implementation guides, the terminology server, the FHIR version,
and the names of the resource and filename columns. Setting `resolveTerminology(false)` validates
without a terminology server, against only the code systems the loaded packages supply.

The session is used as it is found: the API never creates, stops or reconfigures it. The
validator is expensive to build, so it is built once per executor thread and reused for every
partition that thread validates, never per row.

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
