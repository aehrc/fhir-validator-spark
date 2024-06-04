#!/bin/bash
#SBATCH --time=2:00:00 -A OD-221245
#SBATCH --cpus-per-task=32 --mem 128G
#SBATCH --array=1-30%1

module load amazon-corretto/11.0.14.10.1

eval "$(conda shell.bash hook)"
conda activate /scratch3/szu004/conda/mimic 

PROJECT_ROOT="$HOME/dev/fhir-validator-spark"

INPUT_DIR="/scratch3/szu004/mimic-iv/fhir"
OUTPUT_DIR="/scratch3/szu004/validation"


if [ -z "${SLURM_ARRAY_TASK_ID}" ]; then 
        echo "Not an array job"
        exit 1
fi

echo "Array ID: $SLURM_ARRAY_TASK_ID"

INPUT_FILE=$(ls $INPUT_DIR/*.ndjson | sed -n "${SLURM_ARRAY_TASK_ID}p")

if [ -z "${INPUT_FILE}" ];then
        echo "No file at index: ${SLURM_ARRAY_TASK_ID}"
        exit 0
fi


OUTPUT_FILE=${OUTPUT_DIR}/$(basename $INPUT_FILE .ndjson)-validation.parquet

echo $INPUT_FILE
echo $OUTPUT_FILE

mkdir -p "$OUTPUT_DIR"
$PROJECT_ROOT/bin/validate-fhir $INPUT_FILE $OUTPUT_FILE -i $PROJECT_ROOT/fhir/packages/kindlab.fhir.mimic/package.tgz 
