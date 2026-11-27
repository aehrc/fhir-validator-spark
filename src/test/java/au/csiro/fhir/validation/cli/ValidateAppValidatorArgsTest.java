package au.csiro.fhir.validation.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static au.csiro.fhir.validation.cli.ValidateAppTest.MEDICATION_REQUEST_R4;
import static au.csiro.fhir.validation.cli.ValidateAppTest.issueLevels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that validator arguments given after {@code --} reach the validator.
 *
 * <p>
 * This is a separate class from {@link ValidateAppTest} so that it runs in its own JVM: the
 * validation engine is cached per thread, and Spark executor threads that have already validated
 * with the default configuration would otherwise keep using it.
 *
 * @author John Grimes
 */
class ValidateAppValidatorArgsTest {

    @Test
    void levelArgumentRestrictsReportedIssues() {
        // With -level errors the validator drops the warnings it reports by default (see
        // ValidateAppTest.defaultReportsWarnings).
        System.setProperty("spark.master", "local[*]");
        final String output = "target/MedicationRequest-errors-validation.parquet";
        final int exitCode = ValidateApp.execute(new String[]{MEDICATION_REQUEST_R4, output, "--", "-level", "errors"});
        assertEquals(0, exitCode);

        final List<String> levels = issueLevels(output);
        assertTrue(levels.stream().allMatch(l -> l.equals("error") || l.equals("fatal")),
                "Expected only errors with -level errors, got: " + levels);
    }
}
