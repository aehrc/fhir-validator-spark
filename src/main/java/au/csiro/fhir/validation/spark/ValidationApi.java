package au.csiro.fhir.validation.spark;

import au.csiro.fhir.utils.Streams;
import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.hl7.HL7ValidationConfig;
import au.csiro.fhir.validation.hl7.HL7ValidationService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.apache.spark.api.java.function.MapPartitionsFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.apache.spark.sql.functions.lit;

/**
 * Validates FHIR resources in a Spark dataset with the HL7 FHIR validator. This is the entry point
 * for using the library from Spark code, and the implementation behind {@link
 * au.csiro.fhir.validation.cli.ValidateApp}.
 *
 * <p>
 * The input is a dataset with a string column {@value #VALUE_COLUMN} holding one FHIR resource
 * as JSON per row, which is what reading NDJSON files as text gives, and optionally a string
 * column {@value #FILENAME_COLUMN} naming the source of each resource. Other columns are ignored.
 *
 * <p>
 * The result has one row for each input resource for which the validator reports at least one
 * issue. Resources without issues are left out. The schema is the one {@code ValidateApp} writes:
 * <pre>
 * root
 *  |-- filename: string (nullable = false)          // source of the resource
 *  |-- issues: array (nullable = true)
 *  |    |-- element: struct (containsNull = true)
 *  |    |    |-- col: integer (nullable = true)      // column of the issue in the resource JSON
 *  |    |    |-- level: string (nullable = false)    // fatal, error, warning or information
 *  |    |    |-- line: integer (nullable = true)     // line of the issue in the resource JSON
 *  |    |    |-- location: string (nullable = true)  // FHIRPath of the element with the issue
 *  |    |    |-- message: string (nullable = false)  // issue message
 *  |    |    |-- messageId: string (nullable = true) // validator message identifier
 *  |    |    |-- type: string (nullable = false)     // issue type, as the validator classifies it
 *  |-- resource: string (nullable = false)          // the resource JSON
 * </pre>
 * If the input has no {@value #FILENAME_COLUMN} column, the {@code filename} column of the result
 * is null and nullable. A null value in the input {@value #VALUE_COLUMN} or {@value
 * #FILENAME_COLUMN} column fails the job.
 *
 * <p>
 * The result is computed lazily, like any Spark transformation, but the configuration is checked
 * when this method is called. Each executor thread keeps one validation engine and re-uses it for
 * every partition with the same configuration. A thread given a partition with a different
 * configuration builds a new engine, so a single job that combines results validated with
 * different configurations (for example a union) can rebuild engines many times.
 *
 * <p>
 * Example in Java:
 * <pre>{@code
 * Dataset<Row> ndjson = spark.read().text("data/mimic-iv-demo-10/MimicPatient.ndjson");
 * HL7ValidationConfig config = HL7ValidationConfig.builder()
 *         .validatorArgs(List.of("-ig", "data/packages/kindlab.fhir.mimic/package.tgz"))
 *         .build();
 * Dataset<Row> results = ValidationApi.validate(ndjson, config);
 * }</pre>
 *
 * <p>
 * Example in PySpark:
 * <pre>{@code
 * jvm = spark.sparkContext._jvm
 * config = (jvm.au.csiro.fhir.validation.hl7.HL7ValidationConfig.builder()
 *           .validatorArgs(["-ig", "data/packages/kindlab.fhir.mimic/package.tgz"])
 *           .build())
 * ndjson = spark.read.text("data/mimic-iv-demo-10/MimicPatient.ndjson")
 * results = DataFrame(jvm.au.csiro.fhir.validation.spark.ValidationApi.validate(ndjson._jdf, config), spark)
 * }</pre>
 *
 * @author John Grimes
 * @see HL7ValidationConfig for the validator arguments and their defaults
 */
public final class ValidationApi {

    /**
     * The name of the input column holding the FHIR resource JSON.
     */
    public static final String VALUE_COLUMN = "value";

    /**
     * The name of the input and output column naming the source of a resource.
     */
    public static final String FILENAME_COLUMN = "filename";

    private ValidationApi() {
    }

    /**
     * Validates the FHIR resources in a dataset.
     *
     * @param ndjson a dataset with a string column {@value #VALUE_COLUMN} holding one FHIR
     * resource as JSON per row, and optionally a string column {@value #FILENAME_COLUMN}
     * @param config the validation configuration
     * @return the resources with at least one issue, with their filename and issues
     * @throws IllegalArgumentException if the dataset has no {@value #VALUE_COLUMN} column, or the
     * validator arguments of the configuration are invalid
     */
    @Nonnull
    public static Dataset<Row> validate(@Nonnull final Dataset<Row> ndjson, @Nonnull final HL7ValidationConfig config) {
        final List<String> columns = Arrays.asList(ndjson.columns());
        if (!columns.contains(VALUE_COLUMN)) {
            throw new IllegalArgumentException("The input has no `" + VALUE_COLUMN + "` column, found: " + columns);
        }
        // Fail here rather than in the first task.
        config.toCliContext();

        final boolean hasFilename = columns.contains(FILENAME_COLUMN);
        // The bean encoder needs a non-null filename, which is replaced with null in the result.
        final Dataset<Row> withFilename = hasFilename ? ndjson : ndjson.withColumn(FILENAME_COLUMN, lit(""));
        final Dataset<Row> result = withFilename.as(Encoders.bean(ValueWithFile.class))
                .mapPartitions(new PartitionValidator(config), Encoders.bean(ResourceWithIssues.class))
                .toDF();
        return hasFilename ? result : result.withColumn(FILENAME_COLUMN, lit(null).cast(DataTypes.StringType));
    }

    /**
     * An input row: a resource and the name of its source.
     */
    @Data
    @NoArgsConstructor
    public static class ValueWithFile implements Serializable {
        @Nonnull
        String value;
        @Nonnull
        String filename;
    }

    /**
     * An output row: a resource, the name of its source and the issues the validator reported.
     */
    @Value
    public static class ResourceWithIssues implements Serializable {
        @Nonnull
        String resource;

        @Nonnull
        String filename;

        @Nullable
        List<ValidationResult.Issue> issues;

        boolean hasIssues() {
            return issues != null;
        }

        @Nonnull
        static ResourceWithIssues of(@Nonnull final String resource, @Nonnull final String filename,
                                     @Nonnull final ValidationResult validationResult) {
            return new ResourceWithIssues(resource, filename,
                    validationResult.getIssues().isEmpty() ? null : validationResult.getIssues());
        }
    }

    /**
     * Validates a partition of resources with the validation engine of the executor thread.
     */
    @AllArgsConstructor
    private static final class PartitionValidator implements MapPartitionsFunction<ValueWithFile, ResourceWithIssues> {

        @Nonnull
        private final HL7ValidationConfig config;

        @Override
        @Nonnull
        public Iterator<ResourceWithIssues> call(@Nonnull final Iterator<ValueWithFile> input) {
            final HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
            return Streams.streamOf(input)
                    .map(s -> ResourceWithIssues.of(s.getValue(), s.getFilename(),
                            validationService.validateJson(s.getValue().getBytes(StandardCharsets.UTF_8))))
                    .filter(ResourceWithIssues::hasIssues)
                    .iterator();
        }
    }
}
