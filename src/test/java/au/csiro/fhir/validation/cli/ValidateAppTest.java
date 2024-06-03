package au.csiro.fhir.validation.cli;

import org.junit.jupiter.api.Test;

class ValidateAppTest {

    @Test
    void testMain() {
        System.setProperty("spark.master", "local[*]");
        ValidateApp.main(new String[]{"data/mimic-iv-10/MimicPatient.ndjson", "target/MimicPatent-validation.parquet","-i", "fhir/packages/kindlab.fhir.mimic/package.tgz", "-l", "-d", "WARN"});
    }
}