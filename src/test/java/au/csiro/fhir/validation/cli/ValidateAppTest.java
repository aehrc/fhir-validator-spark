package au.csiro.fhir.validation.cli;

import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static au.csiro.fhir.validation.spark.ValidationApiTest.RESULT_SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ValidateApp} run against a local Spark master.
 *
 * @author John Grimes
 */
class ValidateAppTest {

    static final String MEDICATION_REQUEST_R4 = "src/test/resources/ndjson/MedicationRequest_R4.ndjson";

    /**
     * Reads the messages of all error-level issues from a validation output dataset.
     */
    static List<String> errorMessages(final String output) {
        return SparkSession.active().read().parquet(output)
                .selectExpr("explode(issues) AS issue")
                .where("issue.level = 'error'")
                .selectExpr("issue.message")
                .as(Encoders.STRING())
                .collectAsList();
    }

    /**
     * Reads the levels of all issues from a validation output dataset.
     */
    static List<String> issueLevels(final String output) {
        return SparkSession.active().read().parquet(output)
                .selectExpr("explode(issues) AS issue")
                .selectExpr("issue.level")
                .as(Encoders.STRING())
                .collectAsList();
    }

    /**
     * Reads the Spark schema that Spark stores in the footer of a Parquet file of an output
     * dataset. Reading the dataset back with Spark would make every column nullable.
     */
    static StructType writtenSchema(final String output) throws IOException {
        final Path file;
        try (Stream<Path> files = Files.list(Paths.get(output))) {
            file = files.filter(p -> p.toString().endsWith(".parquet")).findFirst().orElseThrow();
        }
        try (ParquetFileReader reader = ParquetFileReader.open(HadoopInputFile.fromPath(
                new org.apache.hadoop.fs.Path(file.toUri()), new Configuration()))) {
            return (StructType) DataType.fromJson(reader.getFooter().getFileMetaData().getKeyValueMetaData()
                    .get("org.apache.spark.sql.parquet.row.metadata"));
        }
    }

    @Test
    void defaultReportsWarnings() {
        // Control for ValidateAppValidatorArgsTest: with the default validation level the
        // validator reports warnings for these resources.
        System.setProperty("spark.master", "local[*]");
        final String output = "target/MedicationRequest-default-validation.parquet";
        final int exitCode = ValidateApp.execute(new String[]{MEDICATION_REQUEST_R4, output});
        assertEquals(0, exitCode);

        final List<String> levels = issueLevels(output);
        assertTrue(levels.contains("warning"), "Expected warnings at the default level, got: " + levels);
    }

    @Test
    void testSingleFile() throws IOException {
        System.setProperty("spark.master", "local[*]");
        final String output = "target/MimicPatient-validation.parquet";
        int exitCode = ValidateApp.execute(new String[]{"data/mimic-iv-demo-10/MimicPatient.ndjson", output, "-i", "data/packages/kindlab.fhir.mimic/package.tgz", "-p", "-d", "WARN"});
        assertEquals(0, exitCode);
        // The output schema is part of the contract with the report scripts.
        assertEquals(RESULT_SCHEMA, writtenSchema(output));
    }


    @Test
    void testPartitionedDataset() throws IOException {
        System.setProperty("spark.master", "local[*]");
        final String output = "target/MimicDemo-validation.parquet";
        int exitCode = ValidateApp.execute(new String[]{"data/mimic-iv-demo-10_partitioned", output, "-i", "data/packages/kindlab.fhir.mimic/package.tgz", "-p", "-d", "WARN"});
        assertEquals(0, exitCode);
        assertEquals(RESULT_SCHEMA, writtenSchema(output));
    }

    @Test
    void defaultFhirVersionIsR4() {
        // Control for ValidateAppFhirVersionTest: without --fhir-version the validator uses R4, in
        // which medicationCodeableConcept is a valid element.
        System.setProperty("spark.master", "local[*]");
        final String output = "target/MedicationRequest-R4-validation.parquet";
        final int exitCode = ValidateApp.execute(new String[]{MEDICATION_REQUEST_R4, output});
        assertEquals(0, exitCode);

        final List<String> errors = errorMessages(output);
        assertTrue(errors.stream().noneMatch(m -> m.contains("medicationCodeableConcept")),
                "Expected no error about medicationCodeableConcept under R4, got: " + errors);
    }
}