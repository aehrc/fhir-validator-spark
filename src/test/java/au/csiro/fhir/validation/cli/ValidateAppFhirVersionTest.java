package au.csiro.fhir.validation.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static au.csiro.fhir.validation.cli.ValidateAppTest.MEDICATION_REQUEST_R4;
import static au.csiro.fhir.validation.cli.ValidateAppTest.errorMessages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the {@code --fhir-version} option selects the FHIR version used by the validator.
 *
 * @author John Grimes
 */
class ValidateAppFhirVersionTest {

    @Test
    void fhirVersionOptionValidatesAgainstRequestedVersion() {
        // medicationCodeableConcept is valid in R4 but was replaced by medication in R5, so an R5
        // validator must report it as an error.
        System.setProperty("spark.master", "local[*]");
        final String output = "target/MedicationRequest-R5-validation.parquet";
        final int exitCode = ValidateApp.execute(new String[]{MEDICATION_REQUEST_R4, output, "--fhir-version", "5.0.0"});
        assertEquals(0, exitCode);

        final List<String> errors = errorMessages(output);
        assertTrue(errors.stream().anyMatch(m -> m.contains("medicationCodeableConcept")),
                "Expected an R5 error about medicationCodeableConcept, got: " + errors);
    }
}
