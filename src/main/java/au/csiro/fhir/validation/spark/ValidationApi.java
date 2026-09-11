package au.csiro.fhir.validation.spark;

import au.csiro.fhir.utils.Streams;
import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.hl7.HL7ValidationConfig;
import au.csiro.fhir.validation.hl7.HL7ValidationService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.spark.api.java.function.MapPartitionsFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import scala.jdk.javaapi.CollectionConverters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validates FHIR resources inside a Spark session that something else owns, so that validation can
 * run in the same session as the code that produced the resources.
 * <p>
 * The session is used as it is found: this class never creates, stops or reconfigures it.
 * <p>
 * The validator itself is expensive to build, so it is built once per executor thread and reused
 * for every partition that thread processes. It is never built per row. The HL7 validation engine
 * is not thread safe, which is why the cache is per thread rather than one instance for the whole
 * JVM.
 *
 * @author John Grimes
 * @see au.csiro.fhir.validation.cli.ValidateApp the equivalent command line application
 */
public final class ValidationApi {

    /**
     * The name of the column of validation issues added to the input.
     */
    public static final String ISSUES_COLUMN = "issues";

    /**
     * The column of validation issues, taken from the bean encoder the command line application
     * writes its Parquet output with, so that both producers write the same type.
     */
    private static final StructField ISSUES_FIELD = new StructField(ISSUES_COLUMN,
            Encoders.bean(Issues.class).schema().apply(ISSUES_COLUMN).dataType(), true, Metadata.empty());

    private static final String TEXT_COLUMN = "value";

    @Nonnull
    private final SparkSession spark;

    @Nonnull
    private final ValidationConfig config;

    private ValidationApi(@Nonnull final SparkSession spark, @Nonnull final ValidationConfig config) {
        this.spark = spark;
        this.config = config;
    }

    /**
     * Creates an API instance bound to an existing session.
     *
     * @param spark an existing session, which is neither created, stopped nor reconfigured here
     * @param config the validation configuration
     * @return the API instance
     */
    @Nonnull
    public static ValidationApi create(@Nonnull final SparkSession spark,
            @Nonnull final ValidationConfig config) {
        return new ValidationApi(spark, config);
    }

    /**
     * Validates a dataset of FHIR resource JSON.
     *
     * @param resources a dataset with at least the column named by the configuration's resource
     * column
     * @return the input columns plus {@value #ISSUES_COLUMN}, an array of the issue struct the
     * command line application writes
     */
    @Nonnull
    public Dataset<Row> validate(@Nonnull final Dataset<Row> resources) {
        final String resourceColumn = config.getResourceColumn();
        if (Arrays.stream(resources.columns()).noneMatch(resourceColumn::equals)) {
            throw new IllegalArgumentException(
                    "The input has no `" + resourceColumn + "` column, found: " + Arrays.toString(
                            resources.columns()));
        }
        final StructType outputSchema = resources.schema().add(ISSUES_FIELD);
        return resources.mapPartitions(
                new PartitionValidator(config.toEngineConfig(), resourceColumn, outputSchema),
                Encoders.row(outputSchema));
    }

    /**
     * Validates every NDJSON resource under a path, applying the same hive style {@code filename}
     * partition handling as the command line application: a partitioned input keeps its partition
     * values, and an unpartitioned input gets the path itself.
     *
     * @param path a file or directory of NDJSON
     * @return the resource and filename columns plus {@value #ISSUES_COLUMN}
     */
    @Nonnull
    public Dataset<Row> validatePath(@Nonnull final String path) {
        final String filenameColumn = config.getFilenameColumn();
        final Dataset<Row> text = spark.read().text(path);
        final Dataset<Row> withFilename =
                Stream.of(text.columns()).noneMatch(filenameColumn::equals)
                        ? text.withColumn(filenameColumn, functions.lit(path))
                        : text;
        return validate(withFilename.select(
                functions.col(TEXT_COLUMN).alias(config.getResourceColumn()),
                functions.col(filenameColumn)));
    }

    /**
     * Carries the type of the issues column, which is whatever the bean encoder makes of the issue
     * list the command line application writes.
     */
    @Data
    @NoArgsConstructor
    public static class Issues implements Serializable {

        private static final long serialVersionUID = 1L;

        List<ValidationResult.Issue> issues;
    }

    /**
     * Validates a partition of rows, building the validator once for the partition.
     */
    @AllArgsConstructor
    static class PartitionValidator implements MapPartitionsFunction<Row, Row>, Serializable {

        private static final long serialVersionUID = 1L;

        @Nonnull
        private final HL7ValidationConfig config;

        @Nonnull
        private final String resourceColumn;

        @Nonnull
        private final StructType outputSchema;

        @Override
        @Nonnull
        public Iterator<Row> call(@Nonnull final Iterator<Row> input) {
            final HL7ValidationService service = HL7ValidationService.getOrCreate(config);
            final StructType issueSchema = issueSchemaOf(outputSchema);
            return Streams.streamOf(input)
                    .map(row -> withIssues(row, service, issueSchema))
                    .iterator();
        }

        @Nonnull
        private Row withIssues(@Nonnull final Row row, @Nonnull final HL7ValidationService service,
                @Nonnull final StructType issueSchema) {
            final Object[] values = new Object[row.size() + 1];
            for (int i = 0; i < row.size(); i++) {
                values[i] = row.get(i);
            }
            final String resource = row.getAs(resourceColumn);
            // The row encoder expects a Scala sequence for an array typed value.
            values[row.size()] = resource != null
                    ? CollectionConverters.asScala(
                            service.validateJson(resource.getBytes(StandardCharsets.UTF_8))
                                    .getIssues().stream()
                                    .map(issue -> issueRow(issue, issueSchema))
                                    .collect(Collectors.toList()))
                    .toSeq()
                    : null;
            return new GenericRowWithSchema(values, outputSchema);
        }
    }

    @Nonnull
    private static StructType issueSchemaOf(@Nonnull final StructType outputSchema) {
        final DataType issuesType = outputSchema.apply(ISSUES_COLUMN).dataType();
        return (StructType) ((ArrayType) issuesType).elementType();
    }

    /**
     * Renders an issue as a row of the issue struct, in whatever field order the struct has.
     */
    @Nonnull
    private static Row issueRow(@Nonnull final ValidationResult.Issue issue,
            @Nonnull final StructType issueSchema) {
        final String[] fieldNames = issueSchema.fieldNames();
        final Object[] values = new Object[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            values[i] = issueValue(issue, fieldNames[i]);
        }
        return new GenericRowWithSchema(values, issueSchema);
    }

    @Nullable
    private static Object issueValue(@Nonnull final ValidationResult.Issue issue,
            @Nonnull final String field) {
        switch (field) {
            case "level":
                return issue.getLevel();
            case "type":
                return issue.getType();
            case "message":
                return issue.getMessage();
            case "messageId":
                return issue.getMessageId();
            case "location":
                return issue.getLocation();
            case "line":
                return issue.getLine();
            case "col":
                return issue.getCol();
            default:
                throw new IllegalStateException("Unexpected issue field: " + field);
        }
    }
}
