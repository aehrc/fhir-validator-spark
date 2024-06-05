#!/bin/sh

# Validate the patient data
ROOT_DIR="$(dirname $0)/.."

mkdir -p "$ROOT_DIR/target"

spark-submit --driver-memory 4g --master local[*] \
    $ROOT_DIR/target/fhir-validator-spark-1.0-SNAPSHOT-all.jar \
    --ig "$ROOT_DIR/fhir/packages/kindlab.fhir.mimic/package.tgz" \
    --log-progress \
    $ROOT_DIR/data/mimic-iv-10/MimicPatient.ndjson \
    $ROOT_DIR/target/MimicPatient_validation.parquet
