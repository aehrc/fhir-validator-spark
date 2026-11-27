package au.csiro.fhir.validation.cli;

import au.csiro.fhir.utils.Streams;
import au.csiro.fhir.validation.hl7.HL7ValidationConfig;
import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.hl7.HL7ValidationService;
import lombok.*;
import org.apache.spark.sql.*;
import org.hl7.fhir.validation.cli.utils.Params;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command line application to validate large number of FHIR resources in ndjson format using
 * Apache Spark for parallel processing and HL7 FHIR Validator for fhir validation.
 *
 * <p>
 * The input is a text file in ndjson format with each line containing a FHIR resource or a directory containing such ndjson files.
 * Additionally, the intput directory can be partitioned using the hive style partitioning with the `filename` column, e.g.:
 * <pre>
 *  dataset/
 *      filename=MimicPatient/
 *          part-00000.ndjson
 *          part-00001.ndjson
 *          ...
 *      filename=MimicObservation/
 *          part-00000.ndjson
 *          part-00001.ndjson
 *          ...
 *      ...
 * </pre>
 * For non-partitioned data the `filename` column is added to the dataset with the value of the input file.
 *
 * <p>
 * The output is a parquet dataset with the following schema:
 * <pre>
 *     root
 *     |-- resource: string (nullable = false)  // FHIR resource
 *     |-- filename: string (nullable = fase)  // filename of the resource
 *     |-- issues: array (nullable = true)
 *     |    |-- element: struct (containsNull = false)
 *     |    |    |-- level: string (nullable = false) // Issue severity (information, warning, error, fatal)
 *     |    |    |-- type: string (nullable = false) // Issue type (according to the validator classification)
 *     |    |    |-- message: string (nullable = false) // Issue message
 *     |    |    |-- messageId: string (nullable = true) // Issue message id
 *     |    |    |-- location: string (nullable = true) // Issue location, e.g. the fhirpath expression
 *     |    |    |-- line: integer (nullable = true) // Issue line number
 *     |    |    |-- col: integer(nullable = true) // Issue column number
 *  </pre>
 *
 * <p>
 * Arguments after {@code --} are passed to the argument parser of the HL7 FHIR validator, after the
 * validator arguments that express the options of this application (see {@link HL7ValidationConfig}).
 *
 * @see <a href="https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator">HL7 FHIR Validator</a>
 * @see <a href="https://github.com/hapifhir/org.hl7.fhir.core">HL7 FHIR Core tools</a>
 * @author John Grimes
 */
@CommandLine.Command(name = "validate-fhir", mixinStandardHelpOptions = true, version = "validate 1.0",
        description = "Validate FHIR resources")
@ToString
public class ValidateApp implements Runnable {

    public static final String FILENAME_COLUMN = "filename";
    @CommandLine.Parameters(index = "0", description = "Input file.")
    String inputFile;

    @CommandLine.Parameters(index = "1", description = "Output file.")
    String outputFile;

    @CommandLine.Option(names = {"-v", "--fhir-version"}, description = "FHIR version (validator -version). Defaults to " + HL7ValidationConfig.DEFAULT_VERSION + ".")
    String fhirVersion = null;

    @CommandLine.Option(names = {"-i", "--ig"}, description = "Implementation guide(s) (validator -ig).", arity = "0..*")
    List<String> igs = List.of();

    @CommandLine.Option(names = {"-p", "--log-progress"}, description = "Log progress.", defaultValue = "false")
    boolean logProgress = false;

    @CommandLine.Option(names = {"-d", "--log-level"}, description = "Spark log level", defaultValue = "WARN")
    String debugLevel = "WARN";

    @CommandLine.Option(names = {"-l", "--language"}, description = "Language to use (validator -language).")
    String language = null;

    @CommandLine.Option(names = {"-tx", "--tx-server"}, description = "Tx server to use (validator -tx). Defaults to none.")
    String txServer = null;

    @CommandLine.Parameters(index = "2..*", paramLabel = "VALIDATOR_ARG",
            description = "Arguments for the HL7 FHIR validator, given after --, e.g. -- -level errors -extension any.")
    List<String> validatorArgs = List.of();

    @CommandLine.Spec
    @ToString.Exclude
    CommandLine.Model.CommandSpec spec;

    /**
     * Expresses the options of this application as HL7 validator arguments, followed by the
     * arguments given after {@code --}, so that the latter take precedence.
     *
     * @return the validation configuration
     */
    @Nonnull
    HL7ValidationConfig toConfig() {
        final List<String> args = new ArrayList<>();
        if (fhirVersion != null) {
            args.addAll(List.of(Params.VERSION, fhirVersion));
        }
        for (final String ig : igs) {
            args.addAll(List.of(Params.IMPLEMENTATION_GUIDE, ig));
        }
        if (language != null) {
            args.addAll(List.of(Params.LANGUAGE, language));
        }
        if (txServer != null) {
            args.addAll(List.of(Params.TERMINOLOGY, txServer));
        }
        args.addAll(validatorArgs);
        return HL7ValidationConfig.builder()
                .validatorArgs(args)
                .showProgress(logProgress)
                .build();
    }


    @Data
    @NoArgsConstructor
    public static class ValueWithFile implements Serializable {
        @Nonnull
        String value;
        @Nonnull
        String filename;
    }

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
        static ResourceWithIssues of(@Nonnull final String resource, @Nonnull final String filename, @Nonnull final ValidationResult validationResult) {
            return new ResourceWithIssues(resource, filename, validationResult.getIssues().isEmpty() ? null : validationResult.getIssues());
        }
    }

    @AllArgsConstructor
    static class Validator implements Serializable {

        @Nonnull
        private final HL7ValidationConfig config;

        @Nonnull
        private Iterator<ResourceWithIssues> validatePartition(@Nonnull final Iterator<ValueWithFile> input) {
            final HL7ValidationService validationService = HL7ValidationService.getOrCreate(config);
            return Streams.streamOf(input)
                    .map(s -> ResourceWithIssues.of(s.getValue(), s.getFilename(),
                            validationService.validateJson(s.getValue().getBytes(StandardCharsets.UTF_8))))
                    .filter(ResourceWithIssues::hasIssues)
                    .iterator();
        }
    }

    public void run() {
        long startTime = System.currentTimeMillis();
        System.out.println("FHIR Validator: " + this);
        final HL7ValidationConfig config = toConfig();
        try {
            // Check the validator arguments here, before any Spark job starts.
            config.toCliContext();
        } catch (final IllegalArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage(), e);
        }
        System.out.println("Validation config: " + config);
        final SparkSession sparkSession = SparkSession.builder()
                .appName("FhirValidator")
                .getOrCreate();
        if (!debugLevel.isEmpty()) {
            sparkSession.sparkContext().setLogLevel(debugLevel);
        }
        final Validator validator = new Validator(config);
        System.out.println("Validating: " + inputFile + " and writing to: " + outputFile);
        final Dataset<Row> inputDF = sparkSession.read().text(inputFile);
        final Dataset<ValueWithFile> ndjsonDatset;
        if (Stream.of(inputDF.columns()).noneMatch(FILENAME_COLUMN::equals)) {
            System.out.println("Setting `filename` column to:" + inputFile);
            ndjsonDatset = inputDF.withColumn(FILENAME_COLUMN, functions.lit(inputFile)).as(Encoders.bean(ValueWithFile.class));
        } else {
            System.out.println("Using `filename` column present in the dataset.");
            ndjsonDatset = inputDF.as(Encoders.bean(ValueWithFile.class));
        }
        final Dataset<ResourceWithIssues> result = ndjsonDatset.mapPartitions(validator::validatePartition, Encoders.bean(ResourceWithIssues.class));
        result.toDF().write().mode(SaveMode.Overwrite).parquet(outputFile);
        long endTime = System.currentTimeMillis();
        System.out.printf("Elapsed time: %.3f s\n", (endTime - startTime) / 1000.0);
    }

    /**
     * Creates the command line of this application. Clustered short options are turned off, so
     * that a validator argument such as {@code -level} given before {@code --} is reported as an
     * unknown option rather than read as {@code -l evel}.
     *
     * @return the command line
     */
    @Nonnull
    static CommandLine commandLine() {
        return new CommandLine(new ValidateApp()).setPosixClusteredShortOptionsAllowed(false);
    }

    static int execute(String[] args) {
        return commandLine().execute(args);
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }
}
