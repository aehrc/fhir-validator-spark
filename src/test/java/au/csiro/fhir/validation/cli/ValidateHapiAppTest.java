package au.csiro.fhir.validation.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidateHapiAppTest {

    @Test
    void testMain() {
        System.setProperty("spark.master", "local[*]");
        int exitCode = ValidateHapiApp.execute(new String[]{"data/mimic-iv-demo-10/MimicPatient.ndjson", "target/MimicPatient-hapi-validation.parquet", "-d", "WARN"});
        assertEquals(0, exitCode);
    }
}