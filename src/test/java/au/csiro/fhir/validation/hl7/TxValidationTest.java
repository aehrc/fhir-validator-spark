package au.csiro.fhir.validation.hl7;

import au.csiro.fhir.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

public class TxValidationTest {
    private static final HL7ValidationConfig NO_TX_CONFIG = HL7ValidationConfig.builder()
            .ig("data/packages/kindlab.fhir.mimic/package.tgz")
            .showProgress(true).build();

    private static final HL7ValidationConfig WITH_TX_CONFIG = HL7ValidationConfig.builder()
            .ig("data/packages/kindlab.fhir.mimic/package.tgz")
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
        HL7ValidationService validationService = HL7ValidationService.getOrCreate(NO_TX_CONFIG);
        ValidationResult result = validationService.validateJson(
                Files.readAllBytes(Paths.get("src/test/resources/fhir/Condition_ICD9_OK.json")));
        result.getIssues().forEach(System.out::println);
    }


    @Test
    void testInvalidICD9withoutTx() throws Exception {

        HL7ValidationService validationService = HL7ValidationService.getOrCreate(NO_TX_CONFIG);
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
        HL7ValidationService validationService = HL7ValidationService.getOrCreate(NO_TX_CONFIG);
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
        HL7ValidationService validationService = HL7ValidationService.getOrCreate(WITH_TX_CONFIG);
        ValidationResult result = validationService.validateJson(
                Files.readAllBytes(Paths.get("src/test/resources/fhir/Observation_Loinc_OK.json")));
        result.getIssues().forEach(System.out::println);
    }
}
