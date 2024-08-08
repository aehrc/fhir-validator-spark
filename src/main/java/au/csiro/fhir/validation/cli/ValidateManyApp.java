package au.csiro.fhir.validation.cli;

import au.csiro.fhir.utils.Streams;
import au.csiro.fhir.validation.hl7.HL7ValidationConfig;
import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.hl7.HL7ValidationService;
import lombok.*;
import org.apache.spark.sql.*;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

@CommandLine.Command(name = "validate-fhir", mixinStandardHelpOptions = true, version = "validate 1.0",
        description = "Validate FHIR resources")
@ToString
public class ValidateManyApp implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Input file.")
    String inputFile;

    @CommandLine.Parameters(index = "1", description = "Output file.")
    String outputFile;

    @CommandLine.Option(names = {"-v", "--fhir-version"}, description = "FHIR version.", defaultValue = HL7ValidationConfig.DEFAULT_VERSION)
    String fhirVersion = HL7ValidationConfig.DEFAULT_VERSION;

    @CommandLine.Option(names = {"-i", "--ig"}, description = "Implementation guide(s).", arity = "0..*")
    List<String> igs = List.of();

    @CommandLine.Option(names = {"-p", "--log-progress"}, description = "Log progress.", defaultValue = "false")
    boolean logProgress = false;

    @CommandLine.Option(names = {"-d", "--log-level"}, description = "Spark log level", defaultValue = "WARN")
    String debugLevel = "WARN";

    @CommandLine.Option(names = {"-l", "--language"}, description = "Language to use")
    String language = null;

    @CommandLine.Option(names = {"-tx", "--tx-server"}, description = "Tx server to use")
    String txServer = null;


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
        final SparkSession sparkSession = SparkSession.builder()
                .appName("FhirValidator")
                .getOrCreate();
        if (!debugLevel.isEmpty()) {
            sparkSession.sparkContext().setLogLevel(debugLevel);
        }
        final HL7ValidationConfig config = HL7ValidationConfig.builder()
                .txSever(txServer)
                .language(language)
                .igs(igs)
                .showProgress(logProgress)
                // hardcoded for no
                .bestPracticeLevel(BestPracticeWarningLevel.Warning)
                .displayMismatchAsWarning(true)
                .build();
        System.out.println("Validation config: " + config);
        final Validator validator = new Validator(config);
        System.out.println("Validating: " + inputFile + " and writing to: " + outputFile);
        Dataset<ValueWithFile> ndjsonDf = sparkSession.read().text(inputFile).as(Encoders.bean(ValueWithFile.class));
        Dataset<ResourceWithIssues> result = ndjsonDf.mapPartitions(validator::validatePartition, Encoders.bean(ResourceWithIssues.class));
        result.toDF().write().mode(SaveMode.Overwrite).parquet(outputFile);
        long endTime = System.currentTimeMillis();
        System.out.printf("Elapsed time: %.3f s", (endTime - startTime) / 1000.0);
    }

    public static void main(String[] args) {
        //Dataset<Row> ndjsonDf = sparkSession.read().text("/Users/szu004/dev/mimic-vi-fhir/target/mimic4-demo-export-db").;
        new CommandLine(new ValidateManyApp()).execute(args);
    }
}
