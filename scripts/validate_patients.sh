#!/bin/sh

# Validate the patient data
ROOT_DIR="$(dirname $0)/.."

mkdir -p "$ROOT_DIR/target"

spark-submit --driver-memory 4g --master local[*] \
    $ROOT_DIR/target/spark-validator-1.0-SNAPSHOT-all.jar \
    $ROOT_DIR/data/mimic-iv-10/MimicPatient.ndjson \
    $ROOT_DIR/target/MimicPatient_validation.parquet
