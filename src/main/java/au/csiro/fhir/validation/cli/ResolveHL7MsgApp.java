package au.csiro.fhir.validation.cli;

import au.csiro.fhir.utils.Streams;
import au.csiro.fhir.validation.hl7.HL7MessageResolver;
import au.csiro.fhir.validation.ValidationResult;
import lombok.*;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * This class is a command line interface for validating FHIR resources.
 */
@CommandLine.Command(name = "validate-fhir", mixinStandardHelpOptions = true, version = "validate 1.0",
        description = "Validate FHIR resources")
@ToString
public class ResolveHL7MsgApp implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Input file.")
    String inputFile;

    @CommandLine.Parameters(index = "1", description = "Output file.")
    String outputFile;

    @CommandLine.Option(names = {"-d", "--log-level"}, description = "Spark log level", defaultValue = "WARN")
    String debugLevel = "WARN";

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
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
    static class MessageResolver implements Serializable {

        @Nonnull
        private Iterator<ResourceWithIssues> validatePartition(@Nonnull final Iterator<ResourceWithIssues> input) {
            return Streams.streamOf(input)
                    .map(s -> new ResourceWithIssues(s.resource, s.filename,
                            s.hasIssues()
                                    ? requireNonNull(s.getIssues()).stream()
                                    .map(i -> Optional.of(i.getMessage())
                                            .flatMap(HL7MessageResolver::getMessageId)
                                            .map(m -> i.toBuilder().messageId(m).build())
                                            .orElse(i)
                                    ).collect(Collectors.toUnmodifiableList())
                                    : null
                    ))
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
        final MessageResolver validator = new MessageResolver();
        System.out.println("Resolving HL7 Messages in : " + inputFile + " and writing to: " + outputFile);
        Dataset<ResourceWithIssues> ndjsonDf = sparkSession.read().parquet(inputFile).as(Encoders.bean(ResourceWithIssues.class));
        Dataset<ResourceWithIssues> result = ndjsonDf.mapPartitions(validator::validatePartition, Encoders.bean(ResourceWithIssues.class));
        result.toDF().write().mode(SaveMode.Overwrite).parquet(outputFile);
        long endTime = System.currentTimeMillis();
        System.out.printf("Elapsed time: %.3f s", (endTime - startTime) / 1000.0);
    }

    public static void main(String[] args) {
        //Dataset<Row> ndjsonDf = sparkSession.read().text("/Users/szu004/dev/mimic-vi-fhir/target/mimic4-demo-export-db").;
        new CommandLine(new ResolveHL7MsgApp()).execute(args);
    }
}
