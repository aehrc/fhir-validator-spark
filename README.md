# FHIR Validation on Apache Spark

This project provides tools for validating large sets of FHIR resources using Apache Spark parallel processing.
It supports validation of resources in NDJSON format
with [HL7 FHIR Validator](https://github.com/hapifhir/org.hl7.fhir.core).

## Prerequisites

- Java 11 or higher
- Spark 3.4.x
- Maven
- Python 3.x
- Pip

## Building the Project

To build the project, run the following command:

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

The application is implemented in the `au.csiro.fhir.validation.cli.ValidateApp` class.

See also:

- <a href="https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator">HL7 FHIR Validator</a>
- <a href="https://github.com/hapifhir/org.hl7.fhir.core">HL7 FHIR Core tools</a>

## Important note

This software is currently in alpha. It is not yet ready for production use.

Copyright © 2024, Commonwealth Scientific and Industrial Research Organisation (CSIRO) ABN 41 687 119 230. Licensed
under the [Apache License, version 2.0](./LICENSE).
