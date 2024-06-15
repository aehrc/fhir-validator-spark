package au.csiro.fhir.validation.cli;

import org.junit.jupiter.api.Test;

class ValidateHapiAppTest {

    @Test
    void testMain() {
        System.setProperty("spark.master", "local[*]");
        ValidateHapiApp.main(new String[]{"data/mimic-iv-10/MimicPatient.ndjson", "target/MimicPatent-validation.parquet", "-d", "WARN"});
    }
}