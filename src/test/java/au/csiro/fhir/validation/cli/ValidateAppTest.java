package au.csiro.fhir.validation.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidateAppTest {

    @Test
    void testSingleFile() {
        System.setProperty("spark.master", "local[*]");
        int exitCode = ValidateApp.execute(new String[]{"data/mimic-iv-demo-10/MimicPatient.ndjson", "target/MimicPatient-validation.parquet","-i", "data/packages/kindlab.fhir.mimic/package.tgz", "-p", "-d", "WARN"});
        assertEquals(0, exitCode);
    }


    @Test
    void testPartitionedDataset() {
        System.setProperty("spark.master", "local[*]");
        int exitCode = ValidateApp.execute(new String[]{"data/mimic-iv-demo-10_partitioned", "target/MimicDemo-validation.parquet","-i", "data/packages/kindlab.fhir.mimic/package.tgz", "-p", "-d", "WARN"});
        assertEquals(0, exitCode);
    }
}