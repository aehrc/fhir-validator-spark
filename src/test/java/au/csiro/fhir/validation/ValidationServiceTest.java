package au.csiro.fhir.validation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ValidationServiceTest {

    private final ValidationConfig config = ValidationConfig.builder()
            .ig("kindlab.fhir.mimic#dev").showProgress(true).build();

    @Test
    void testInvalidJson() {
        ValidationService validationService = ValidationService.getOrCreate(config);
        ValidationResult result = validationService.validateJson("invalid json".getBytes(StandardCharsets.UTF_8));
        System.out.println(result);
    }

    @Test
    void testInvalidResource() {
        ValidationService validationService = ValidationService.getOrCreate(config);
        ValidationResult result = validationService.validateJson("{}".getBytes(StandardCharsets.UTF_8));
        System.out.println(result);
    }

    @Test
    void testEmptyPatient() {
        ValidationService validationService = ValidationService.getOrCreate(config);
        ValidationResult result = validationService.validateJson("{\"resourceType\":\"Patient\"}".getBytes(StandardCharsets.UTF_8));
        System.out.println(result);
    }

    @Test
    void testDuckDBPatient() throws Exception {
        ValidationService validationService = ValidationService.getOrCreate(config);
        ValidationResult result = validationService.validateJson(Files.readAllBytes(Paths.get("data/Patient_01.json")));
        System.out.println(result);
    }


}