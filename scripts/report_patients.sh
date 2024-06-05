#!/bin/sh

# Validate the patient data
ROOT_DIR="$(dirname $0)/.."

$ROOT_DIR/bin/validation-report-one.py \
    $ROOT_DIR/target/MimicPatient_validation.parquet/*.parquet \
    $ROOT_DIR/target/MimicPatient_validation_report.html
