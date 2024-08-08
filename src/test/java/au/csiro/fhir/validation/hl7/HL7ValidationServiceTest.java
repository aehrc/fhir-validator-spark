package au.csiro.fhir.validation.hl7;

import au.csiro.fhir.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

class HL7ValidationServiceTest {

    private final HL7ValidationConfig config = HL7ValidationConfig.builder()
            //.ig("kindlab.fhir.mimic#dev")
            .ig("fhir/packages/kindlab.fhir.mimic/package.tgz")
            .showProgress(true).build();

    @Test
    void testInvalidJson() {
        HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
        ValidationResult result = validationService.validateJson("invalid json".getBytes(StandardCharsets.UTF_8));
        System.out.println(result);
    }

    @Test
    void testInvalidResource() {
        HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
        ValidationResult result = validationService.validateJson("{}".getBytes(StandardCharsets.UTF_8));
        System.out.println(result);
    }

    @Test
    void testEmptyPatient() {
        HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
        ValidationResult result = validationService.validateJson("{\"resourceType\":\"Patient\"}".getBytes(StandardCharsets.UTF_8));
        System.out.println(result);
    }

    @Test
    void testDuckDBPatient() throws Exception {
        HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
        ValidationResult result = validationService.validateJson(Files.readAllBytes(Paths.get("data/Patient_01.json")));
        System.out.println(result);
    }

}