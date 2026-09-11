package au.csiro.fhir.validation.spark;

import au.csiro.fhir.validation.cli.ValidateApp;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hl7.fhir.r5.utils.validation.constants.ReferenceValidationPolicy;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the session callable API, which is the contract the Pathling based consumers use.
 *
 * @author John Grimes
 */
class ValidationApiTest {

    private static final String CONFORMANT_PATIENT = "{\"resourceType\":\"Patient\",\"id\":\"conformant\","
            + "\"text\":{\"status\":\"generated\",\"div\":\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">A patient</div>\"},"
            + "\"gender\":\"female\",\"birthDate\":\"1980-01-01\"}";

    private static final String NON_CONFORMANT_PATIENT = "{\"resourceType\":\"Patient\",\"id\":\"non-conformant\","
            + "\"text\":{\"status\":\"generated\",\"div\":\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">A patient</div>\"},"
            + "\"gender\":\"banana\",\"birthDate\":\"1980-01-01\"}";

    /**
     * A resource whose reference names a resource that is not in the input. Every resource the
     * Dataset API validates is in that position, since a row carries one resource and nothing else.
     */
    private static final String REFERRING_OBSERVATION = "{\"resourceType\":\"Observation\",\"id\":\"referring\","
            + "\"text\":{\"status\":\"generated\",\"div\":\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">An observation</div>\"},"
            + "\"status\":\"final\",\"code\":{\"text\":\"A code\"},"
            + "\"subject\":{\"reference\":\"Patient/not-in-the-input\"}}";

    private static final StructType INPUT_SCHEMA = new StructType()
            .add("resource", DataTypes.StringType, false)
            .add("filename", DataTypes.StringType, false);

    /**
     * A session created by something other than the API, standing in for a session created by
     * Pathling.
     */
    private static SparkSession externalSession;

    private static Map<String, String> configurationBefore;

    @BeforeAll
    static void setUpSession() {
        externalSession = SparkSession.builder()
                .master("local[2]")
                .appName("ExternalSession")
                .config("spark.sql.shuffle.partitions", "1")
                .config("spark.ui.enabled", "false")
                .getOrCreate();
        externalSession.sparkContext().setLogLevel("WARN");
        configurationBefore = configurationOf(externalSession);
    }

    @AfterAll
    static void tearDownSession() {
        externalSession.stop();
    }

    @Nonnull
    private static Map<String, String> configurationOf(@Nonnull final SparkSession session) {
        return new HashMap<>(scala.jdk.javaapi.CollectionConverters.asJava(session.conf().getAll()));
    }

    @Nonnull
    private static ValidationConfig config() {
        return ValidationConfig.builder()
                .resolveTerminology(false)
                .build();
    }

    @Nonnull
    private static Dataset<Row> inputDataset() {
        return externalSession.createDataFrame(List.of(
                RowFactory.create(CONFORMANT_PATIENT, "conformant.ndjson"),
                RowFactory.create(NON_CONFORMANT_PATIENT, "non-conformant.ndjson")
        ), INPUT_SCHEMA);
    }

    @Nonnull
    private static Dataset<Row> referringDataset() {
        return externalSession.createDataFrame(List.of(
                RowFactory.create(REFERRING_OBSERVATION, "referring.ndjson")
        ), INPUT_SCHEMA);
    }

    @Nonnull
    private static Row rowFor(@Nonnull final List<Row> rows, @Nonnull final String filename) {
        return rows.stream()
                .filter(r -> filename.equals(r.getAs("filename")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row for: " + filename));
    }

    @Nonnull
    private static List<Row> issuesOf(@Nonnull final Row row) {
        final List<Row> issues = row.getList(row.fieldIndex("issues"));
        return issues != null
                ? issues
                : List.of();
    }

    @Nonnull
    private static List<Row> issuesAtLevel(@Nonnull final Row row, @Nonnull final String level) {
        return issuesOf(row).stream()
                .filter(i -> level.equals(i.getAs("level")))
                .collect(Collectors.toUnmodifiableList());
    }

    @Nonnull
    private static String locationOf(@Nonnull final Row issue) {
        return String.valueOf(issue.<String>getAs("location"));
    }

    @Test
    void validatesResourcesInASessionItDidNotCreate() {
        final ValidationApi api = ValidationApi.create(externalSession, config());
        final List<Row> rows = api.validate(inputDataset()).collectAsList();

        // Every input row comes back, with its input columns intact.
        assertEquals(2, rows.size());
        assertEquals(List.of("resource", "filename", "issues"),
                List.of(rows.get(0).schema().fieldNames()));
        assertEquals(CONFORMANT_PATIENT, rowFor(rows, "conformant.ndjson").getAs("resource"));

        // The conformant resource has nothing at error level or above.
        final Row conformant = rowFor(rows, "conformant.ndjson");
        assertEquals(List.of(), issuesAtLevel(conformant, "error"));
        assertEquals(List.of(), issuesAtLevel(conformant, "fatal"));

        // The non-conformant resource has an error that names the offending element.
        final List<Row> errors = issuesAtLevel(rowFor(rows, "non-conformant.ndjson"), "error");
        assertFalse(errors.isEmpty(), "Expected an error issue for an invalid gender code");
        final Row error = errors.stream()
                .filter(i -> locationOf(i).contains("Patient.gender"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected an error at Patient.gender, got: " + errors));
        assertNotNull(error.getAs("type"));
        assertNotNull(error.getAs("message"));
    }

    @Test
    void leavesTheSessionAlone() {
        final ValidationApi api = ValidationApi.create(externalSession, config());
        api.validate(inputDataset()).collect();

        assertFalse(externalSession.sparkContext().isStopped(), "The session was stopped");
        assertEquals("ExternalSession", externalSession.sparkContext().appName());
        assertEquals(configurationBefore, configurationOf(externalSession));
        assertSame(externalSession, SparkSession.active(), "The active session was replaced");
    }

    @Test
    void producesTheSameIssueStructAsTheCommandLine() {
        final ValidationApi api = ValidationApi.create(externalSession, config());
        final StructType schema = api.validate(inputDataset()).schema();
        final DataType issuesType = schema.apply("issues").dataType();

        assertEquals(Encoders.bean(ValidateApp.ResourceWithIssues.class).schema().apply("issues").dataType(),
                issuesType);
        assertEquals(Set.of("level", "type", "message", "messageId", "location", "line", "col"),
                Set.of(((StructType) ((ArrayType) issuesType).elementType()).fieldNames()));
    }

    @Test
    void producesTheSameIssuesOnRepeatedCalls() {
        final ValidationApi api = ValidationApi.create(externalSession, config());
        final List<Row> first = api.validate(inputDataset()).collectAsList();
        final List<Row> second = api.validate(inputDataset()).collectAsList();

        assertEquals(issuesOf(rowFor(first, "non-conformant.ndjson")).toString(),
                issuesOf(rowFor(second, "non-conformant.ndjson")).toString());
    }

    @Test
    void validatesEveryResourceUnderAPath() {
        final ValidationApi api = ValidationApi.create(externalSession, config());
        final Dataset<Row> result = api.validatePath("data/mimic-iv-demo-10/MimicPatient.ndjson");

        assertEquals(List.of("resource", "filename", "issues"), List.of(result.schema().fieldNames()));
        final List<Row> rows = result.collectAsList();
        assertEquals(10, rows.size());
        assertTrue(rows.stream().allMatch(r -> "data/mimic-iv-demo-10/MimicPatient.ndjson".equals(r.getAs("filename"))));
    }

    @Test
    void doesNotReportAReferenceItCannotResolve() {
        final ValidationApi api = ValidationApi.create(externalSession, config());
        final Row row = api.validate(referringDataset()).collectAsList().get(0);

        // A row carries one resource, so no reference in it can ever be resolved. Reporting each
        // one would make every reference in a dataset a false positive.
        assertEquals(List.of(), issuesAtLevel(row, "error"), "Expected no error for an unresolvable reference");
        assertEquals(List.of(), issuesAtLevel(row, "fatal"));
    }

    @Test
    void reportsAReferenceItCannotResolveWhenAskedTo() {
        final ValidationConfig checking = ValidationConfig.builder()
                .resolveTerminology(false)
                .referenceValidationPolicy(ReferenceValidationPolicy.CHECK_VALID)
                .build();
        final ValidationApi api = ValidationApi.create(externalSession, checking);
        final Row row = api.validate(referringDataset()).collectAsList().get(0);

        // The command line's behaviour, which validates a whole file at a time, is still available.
        final List<Row> errors = issuesAtLevel(row, "error");
        assertTrue(errors.stream().anyMatch(i -> locationOf(i).contains("Observation.subject")),
                "Expected an error at Observation.subject, got: " + errors);
    }
}
