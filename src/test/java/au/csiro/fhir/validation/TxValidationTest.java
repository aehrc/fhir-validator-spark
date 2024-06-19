package au.csiro.fhir.validation;

import org.hl7.fhir.validation.ValidatorCli;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

public class TxValidationTest {
    private static final ValidationConfig NO_TX_CONFIG = ValidationConfig.builder()
            .ig("fhir/packages/kindlab.fhir.mimic/package.tgz")
            .showProgress(true).build();

    private static final ValidationConfig WITH_TX_CONFIG = ValidationConfig.builder()
            .ig("fhir/packages/kindlab.fhir.mimic/package.tgz")
            .txSever("http://tx.fhir.org")
            .showProgress(true).build();

    @Test
    void testValidICD9withoutTx() throws Exception {
//        ValidatorCli.main(new String[]{
//                "src/test/resources/fhir/Condition_ICD9_OK.json",
//                "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz",
//                "-tx", "n/a",
//                "-language", "en",
//                "-version", "4.0"
//        });
        ValidationService validationService = ValidationService.getOrCreate(NO_TX_CONFIG);
        ValidationResult result = validationService.validateJson(
                Files.readAllBytes(Paths.get("src/test/resources/fhir/Condition_ICD9_OK.json")));
        result.getIssues().forEach(System.out::println);
    }


    @Test
    void testInvalidICD9withoutTx() throws Exception {

        ValidationService validationService = ValidationService.getOrCreate(NO_TX_CONFIG);
        ValidationResult result = validationService.validateJson(
                Files.readAllBytes(Paths.get("src/test/resources/fhir/Condition_ICD9_WrongDisplay.json")));
        result.getIssues().forEach(System.out::println);
    }

    @Test
    void testValidLoincWithoutTx() throws Exception {
//        ValidatorCli.main(new String[]{
//                "src/test/resources/fhir/Observation_Loinc_OK.json",
//                "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz",
//                "-tx", "n/a",
//                "-language", "en",
//                "-version", "4.0"
//        });
        ValidationService validationService = ValidationService.getOrCreate(NO_TX_CONFIG);
        ValidationResult result = validationService.validateJson(
                Files.readAllBytes(Paths.get("src/test/resources/fhir/Observation_Loinc_OK.json")));
        result.getIssues().forEach(System.out::println);
    }

    @Test
    void testValidLoincWithTx() throws Exception {
//        ValidatorCli.main(new String[]{
//                "src/test/resources/fhir/Observation_Loinc_OK.json",
//                "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz",
//                "-tx", "n/a",
//                "-language", "en",
//                "-version", "4.0"
//        });
        ValidationService validationService = ValidationService.getOrCreate(WITH_TX_CONFIG);
        ValidationResult result = validationService.validateJson(
                Files.readAllBytes(Paths.get("src/test/resources/fhir/Observation_Loinc_OK.json")));
        result.getIssues().forEach(System.out::println);
    }
}
