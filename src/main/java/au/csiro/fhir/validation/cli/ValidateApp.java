package au.csiro.fhir.validation.cli;

import au.csiro.fhir.utils.Streams;
import au.csiro.fhir.validation.ValidationConfig;
import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.ValidationService;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.Value;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

@CommandLine.Command(name = "validate", mixinStandardHelpOptions = true, version = "validate 1.0",
        description = "Validate FHIR resources")
@ToString
public class ValidateApp implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Input file.")
    String inputFile;

    @CommandLine.Parameters(index = "1", description = "Output file.")
    String outputFile;

    @CommandLine.Option(names = {"-v", "--fhir-version"}, description = "FHIR version.", defaultValue = ValidationConfig.DEFAULT_VERSION)
    String fhirVersion = ValidationConfig.DEFAULT_VERSION;

    @CommandLine.Option(names = {"-i", "--ig"}, description = "Implementation guide(s).", arity = "0..*")
    List<String> igs = List.of();

    @CommandLine.Option(names = {"-l", "--log-progress"}, description = "Log progress.", defaultValue = "false")
    boolean logProgress = false;

    @CommandLine.Option(names = {"-d", "--log-level"}, description = "Spark log level", defaultValue = "")
    String debugLevel = "";

    @Value
    public static class ResourceWithIssues implements Serializable {
        @Nonnull
        String resource;
        @Nullable
        List<ValidationResult.Issue> issues;

        @Nonnull
        static ResourceWithIssues of(@Nonnull final String resource, @Nonnull final ValidationResult validationResult) {
            return new ResourceWithIssues(resource, validationResult.getIssues().isEmpty() ? null : validationResult.getIssues());
        }
    }

    @AllArgsConstructor
    static class Validator implements Serializable {

        @Nonnull
        private final ValidationConfig config;

        @Nonnull
        private Iterator<ResourceWithIssues> validatePartition(@Nonnull final Iterator<String> input) {
            final ValidationService validationService = ValidationService.getOrCreate(config);
            return Streams.streamOf(input)
                    // TODO: this is a hack to make the validation work
                    .map(l -> l.replace("http://fhir.mimic.mit.edu/", "http://mimic.mit.edu/fhir/mimic/"))
                    .map(s -> ResourceWithIssues.of(s, validationService.validateJson(s.getBytes(StandardCharsets.UTF_8))))
                    .iterator();
        }
    }

    public void run() {
        System.out.println("FHIR Validator: " + this);
        final SparkSession sparkSession = SparkSession.builder()
                .appName("FhirValidator")
                .getOrCreate();
        if (!debugLevel.isEmpty()) {
            sparkSession.sparkContext().setLogLevel(debugLevel);
        }
        final ValidationConfig config = ValidationConfig.builder()
                .igs(igs)
                .showProgress(logProgress).build();
        System.out.println("Validation config: " + config);
        final Validator validator = new Validator(config);
        System.out.println("Validating: " + inputFile + " and writing to: " + outputFile);
        Dataset<String> ndjsonDf = sparkSession.read().textFile(inputFile);
        Dataset<ResourceWithIssues> result = ndjsonDf.mapPartitions(validator::validatePartition, Encoders.bean(ResourceWithIssues.class));
        result.toDF().write().mode(SaveMode.Overwrite).parquet(outputFile);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new ValidateApp()).execute(args));
    }
}
