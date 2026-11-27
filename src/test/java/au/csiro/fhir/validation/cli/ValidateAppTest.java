package au.csiro.fhir.validation.cli;

import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void testSingleFile() {
        System.setProperty("spark.master", "local[*]");
        int exitCode = ValidateApp.execute(new String[]{"data/mimic-iv-demo-10/MimicPatient.ndjson", "target/MimicPatient-validation.parquet","-i", "data/packages/kindlab.fhir.mimic/package.tgz", "-p", "-d", "WARN"});
        assertEquals(0, exitCode);
    }


    @Test
    void testPartitionedDataset() {
        System.setProperty("spark.master", "local[*]");
        int exitCode = ValidateApp.execute(new String[]{"data/mimic-iv-demo-10_partitioned", "target/MimicDemo-validation.parquet","-i", "data/packages/kindlab.fhir.mimic/package.tgz", "-p", "-d", "WARN"});
        assertEquals(0, exitCode);
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