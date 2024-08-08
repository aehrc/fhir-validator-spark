package au.csiro.fhir.validation.snippets;

import org.hl7.fhir.utilities.SystemExitManager;
import org.hl7.fhir.validation.ValidatorCli;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


/**
 * Test the CLI with terminology validation to check for different outcomes.
 */
@Disabled
public class TestCliTerminology {

    @BeforeAll
    static void setupAll() {
        SystemExitManager.setNoExit(true);
    }


    @Test
    void testValidICD9withoutTx() throws Exception {
        ValidatorCli.main(new String[]{
                "src/test/resources/fhir/Condition_ICD9_OK.json",
                "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz",
                "-tx", "n/a",
                "-language", "en",
                "-version", "4.0"
        });
    }

    @Test
    void testInvalidICD9withoutTx() throws Exception {
        ValidatorCli.main(new String[]{
                "src/test/resources/fhir/Condition_ICD9_WrongDisplay.json",
                "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz",
                "-tx", "n/a",
                "-language", "en",
                "-version", "4.0"
        });
    }

    @Test
    void testValidLoincwithoutTx() throws Exception {
        ValidatorCli.main(new String[]{
                "src/test/resources/fhir/Observation_Loinc_OK.json",
                "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz",
                "-tx", "n/a",
                "-language", "en",
                "-version", "4.0"
        });
    }

    @Test
    void testValidLoincWithTx() throws Exception {
        ValidatorCli.main(new String[]{
                "src/test/resources/fhir/Observation_Loinc_OK.json",
                "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz",
                "-tx", "http://tx.fhir.org",
                "-language", "en",
                "-version", "4.0"
        });
    }
}
