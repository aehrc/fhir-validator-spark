package au.csiro.fhir.validation.spark;

import au.csiro.fhir.validation.hl7.HL7ValidationConfig;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.apache.spark.sql.functions.lit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ValidationApi} run against a local Spark master.
 *
 * @author John Grimes
 */
public class ValidationApiTest {

    /**
     * A resource for which the validator reports a dom-6 warning (no narrative), and nothing else.
     */
    private static final String PATIENT = "{\"resourceType\":\"Patient\"}";

    /**
     * A resource for which the validator reports a fatal issue (no resource type).
     */
    private static final String EMPTY = "{}";

    private static final HL7ValidationConfig ERRORS_ONLY_CONFIG = HL7ValidationConfig.builder()
            .validatorArgs(List.of("-level", "errors"))
            .build();

    /**
     * The schema that ValidateApp writes, read from its Parquet output on the base branch.
     */
    public static final StructType RESULT_SCHEMA = (StructType) DataType.fromJson("{\"type\":\"struct\",\"fields\":["
            + "{\"name\":\"filename\",\"type\":\"string\",\"nullable\":false,\"metadata\":{}},"
            + "{\"name\":\"issues\",\"type\":{\"type\":\"array\",\"elementType\":{\"type\":\"struct\",\"fields\":["
            + "{\"name\":\"col\",\"type\":\"integer\",\"nullable\":true,\"metadata\":{}},"
            + "{\"name\":\"level\",\"type\":\"string\",\"nullable\":false,\"metadata\":{}},"
            + "{\"name\":\"line\",\"type\":\"integer\",\"nullable\":true,\"metadata\":{}},"
            + "{\"name\":\"location\",\"type\":\"string\",\"nullable\":true,\"metadata\":{}},"
            + "{\"name\":\"message\",\"type\":\"string\",\"nullable\":false,\"metadata\":{}},"
            + "{\"name\":\"messageId\",\"type\":\"string\",\"nullable\":true,\"metadata\":{}},"
            + "{\"name\":\"type\",\"type\":\"string\",\"nullable\":false,\"metadata\":{}}]},"
            + "\"containsNull\":true},\"nullable\":true,\"metadata\":{}},"
            + "{\"name\":\"resource\",\"type\":\"string\",\"nullable\":false,\"metadata\":{}}]}");

    private static SparkSession spark;

    @BeforeAll
    static void startSpark() {
        spark = SparkSession.builder().master("local[2]").appName("ValidationApiTest").getOrCreate();
        spark.sparkContext().setLogLevel("WARN");
    }

    @AfterAll
    static void stopSpark() {
        spark.stop();
    }

    /**
     * Creates the dataset that reading NDJSON as text gives: one resource per row in {@code value}.
     */
    private static Dataset<Row> ndjson(final String... resources) {
        return spark.createDataset(List.of(resources), Encoders.STRING()).toDF(ValidationApi.VALUE_COLUMN);
    }

    private static Dataset<Row> ndjsonWithFilename(final String filename, final String... resources) {
        return ndjson(resources).withColumn(ValidationApi.FILENAME_COLUMN, lit(filename));
    }

    // --- Results ---

    @Test
    void returnsOnlyResourcesWithIssues() {
        // With -level errors the Patient has no issues and is left out; the empty resource is kept.
        final List<Row> rows = ValidationApi.validate(ndjsonWithFilename("input.ndjson", PATIENT, EMPTY),
                ERRORS_ONLY_CONFIG).collectAsList();

        assertEquals(1, rows.size());
        final Row row = rows.get(0);
        assertEquals(EMPTY, row.getAs("resource"));
        assertEquals("input.ndjson", row.getAs("filename"));
        final List<Row> issues = row.getList(row.fieldIndex("issues"));
        assertEquals("fatal", issues.get(0).getAs("level"));
        assertEquals("UNABLE_TO_FIND_RESOURCETYPE_PROPERTY", issues.get(0).getAs("messageId"));
    }

    @Test
    void resultHasTheSchemaThatValidateAppWrites() {
        assertEquals(RESULT_SCHEMA, ValidationApi.validate(ndjsonWithFilename("input.ndjson", PATIENT),
                HL7ValidationConfig.defaultConfig()).schema());
    }

    @Test
    void filenameIsNullWhenTheInputHasNoFilenameColumn() {
        final Dataset<Row> result = ValidationApi.validate(ndjson(PATIENT), HL7ValidationConfig.defaultConfig());

        // Same columns as with a filename, but the filename can now be null.
        assertEquals(RESULT_SCHEMA.fieldNames().length, result.schema().fieldNames().length);
        assertTrue(result.schema().apply("filename").nullable());
        assertEquals(DataTypes.StringType, result.schema().apply("filename").dataType());
        final Row row = result.collectAsList().get(0);
        assertNull(row.getAs("filename"));
        assertEquals(PATIENT, row.getAs("resource"));
    }

    @Test
    void otherInputColumnsAreDropped() {
        final Dataset<Row> input = ndjsonWithFilename("input.ndjson", PATIENT)
                .withColumn("other", lit(1));
        assertEquals(RESULT_SCHEMA, ValidationApi.validate(input, HL7ValidationConfig.defaultConfig()).schema());
    }

    @Test
    void differentConfigurationsInOneSessionEachGetTheirOwnResult() {
        // The default level reports the Patient's warning, -level errors does not. Both run in the
        // same JVM, on executor threads that may already hold an engine for the other configuration.
        final Dataset<Row> input = ndjsonWithFilename("input.ndjson", PATIENT);
        assertEquals(1, ValidationApi.validate(input, HL7ValidationConfig.defaultConfig()).count());
        assertEquals(0, ValidationApi.validate(input, ERRORS_ONLY_CONFIG).count());
        assertEquals(1, ValidationApi.validate(input, HL7ValidationConfig.defaultConfig()).count());
    }

    // --- Errors ---

    @Test
    void rejectsInputWithoutValueColumn() {
        final Dataset<Row> input = ndjson(PATIENT).withColumnRenamed(ValidationApi.VALUE_COLUMN, "json");
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ValidationApi.validate(input, HL7ValidationConfig.defaultConfig()));
        assertTrue(e.getMessage().contains("value"), e.getMessage());
    }

    @Test
    void rejectsInvalidConfigurationBeforeValidating() {
        // The configuration is checked when validate is called, not when the result is computed.
        final HL7ValidationConfig invalid = HL7ValidationConfig.builder()
                .validatorArgs(List.of("-output", "out.json"))
                .build();
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ValidationApi.validate(ndjson(PATIENT), invalid));
        assertTrue(e.getMessage().contains("-output"), e.getMessage());
    }
}
