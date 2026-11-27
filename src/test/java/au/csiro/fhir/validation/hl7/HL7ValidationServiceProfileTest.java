package au.csiro.fhir.validation.hl7;

import au.csiro.fhir.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the {@code -profile} validator argument validates each resource against the given
 * profile.
 *
 * @author John Grimes
 */
class HL7ValidationServiceProfileTest {

    private static final String MIMIC_IG = "data/packages/kindlab.fhir.mimic/package.tgz";
    private static final String MIMIC_PATIENT = "http://mimic.mit.edu/fhir/mimic/StructureDefinition/mimic-patient";

    private static HL7ValidationConfig configWithProfile(final String profile) {
        return HL7ValidationConfig.builder()
                .validatorArgs(List.of("-ig", MIMIC_IG, "-profile", profile))
                .build();
    }

    @Test
    void resourceIsValidatedAgainstProfileArgument() {
        // The MIMIC patient profile requires an identifier, which the base Patient does not.
        final HL7ValidationService service = HL7ValidationService.getOrCreate(configWithProfile(MIMIC_PATIENT));
        final ValidationResult result = service.validateJson(
                "{\"resourceType\":\"Patient\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(result.getIssues().stream().anyMatch(i -> "error".equals(i.getLevel())
                        && i.getMessage().contains("Patient.identifier")
                        && i.getMessage().contains(MIMIC_PATIENT)),
                "Expected a missing identifier error from the MIMIC profile, got: " + result);
    }

    @Test
    void unknownProfileArgumentFails() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HL7ValidationService.getOrCreate(configWithProfile("http://example.org/StructureDefinition/none")));
        assertTrue(e.getMessage().contains("http://example.org/StructureDefinition/none"), e.getMessage());
    }
}
