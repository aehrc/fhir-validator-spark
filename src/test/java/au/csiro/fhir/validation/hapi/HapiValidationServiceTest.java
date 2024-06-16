package au.csiro.fhir.validation.hapi;

import au.csiro.fhir.validation.ValidationResult;
import ca.uhn.fhir.context.FhirVersionEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HapiValidationServiceTest {

    @Test
    void testJsonParsingError() {

        final String invalidJson = "{ deer }";
        final HapiValidationService service = HapiValidationService.getOrCreate(FhirVersionEnum.R4);
        final ValidationResult result = service.validateJson(invalidJson.getBytes());
        final ValidationResult.Issue parsingErrrorIssue = ValidationResult.Issue.builder().level("fatal")
                .type("DataFormatException")
                .message("HAPI-1861: Failed to parse JSON encoded FHIR content: Unexpected character ('d' (code 100)): was expecting double-quote to start field name\n" +
                        " at [Source: UNKNOWN; line: 1, column: 4]")
                .build();
        System.out.println(result);
        assertEquals(ValidationResult.of(parsingErrrorIssue), result);
    }

    @Test
    void testValidationErrors() {

        final String invalidJson = "{ \"resourceType\": \"Patient\", \"id\": \"example\", \"active\": true, \"name\": {}, \"xxx\":\"yy\"}";
        final HapiValidationService service = HapiValidationService.getOrCreate(FhirVersionEnum.R4);
        ValidationResult result = service.validateJson(invalidJson.getBytes());
        System.out.println(result);
    }

}