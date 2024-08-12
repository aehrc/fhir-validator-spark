package au.csiro.fhir.validation.hl7;

import au.csiro.fhir.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HL7ValidationServiceTest {

    private final HL7ValidationConfig config = HL7ValidationConfig.builder()
            .ig("data/packages/kindlab.fhir.mimic/package.tgz")
            .showProgress(true).build();

    @Test
    void testInvalidJson() {
        final HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
        final ValidationResult result = validationService.validateJson("invalid json".getBytes(StandardCharsets.UTF_8));
        final ValidationResult expectedResult = ValidationResult.of(
                ValidationResult.Issue.builder().level("fatal").type("invalid")
                        .message("Error parsing JSON: Error parsing JSON source: Unexpected content at start of JSON: String at Line 1 (path=[invalid])")
                        .messageId("ERROR_PARSING_JSON_")
                        .location("(document)")
                        .build()
        );
        assertEquals(expectedResult, result);
    }

    @Test
    void testInvalidResource() {
        final HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
        final ValidationResult result = validationService.validateJson("{}".getBytes(StandardCharsets.UTF_8));
        final ValidationResult expectedResult = ValidationResult.of(
                ValidationResult.Issue.builder().level("fatal").type("invalid")
                        .message("Unable to find resourceType property")
                        .messageId("UNABLE_TO_FIND_RESOURCETYPE_PROPERTY")
                        .location("$")
                        .line(1)
                        .col(3)
                        .build()
        );
        assertEquals(expectedResult, result);
    }

    @Test
    void testEmptyPatient() {
        final HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
        final ValidationResult result = validationService.validateJson("{\"resourceType\":\"Patient\"}".getBytes(StandardCharsets.UTF_8));
        System.out.println(result);

        final ValidationResult expectedResult = ValidationResult.of(
                ValidationResult.Issue.builder().level("warning").type("invariant")
                        .message("Constraint failed: dom-6: 'A resource should have narrative for robust management' (defined in http://hl7.org/fhir/StructureDefinition/DomainResource) (Best Practice Recommendation)")
                        .messageId("INV_FAILED_SOURCE")
                        .location("Patient")
                        .line(1)
                        .col(27)
                        .build()
        );
        assertEquals(expectedResult, result);
    }
}