package au.csiro.fhir.validation.cli;

import au.csiro.fhir.utils.Streams;
import au.csiro.fhir.validation.hl7.HL7ValidationConfig;
import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.hapi.HapiValidationService;
import ca.uhn.fhir.context.FhirVersionEnum;
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

@CommandLine.Command(name = "validate-fhir", mixinStandardHelpOptions = true, version = "validate 1.0",
        description = "Validate FHIR resources")
@ToString
public class ValidateHapiApp implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Input file.")
    String inputFile;

    @CommandLine.Parameters(index = "1", description = "Output file.")
    String outputFile;

    @CommandLine.Option(names = {"-v", "--fhir-version"}, description = "FHIR version.", defaultValue = HL7ValidationConfig.DEFAULT_VERSION)
    String fhirVersion = HL7ValidationConfig.DEFAULT_VERSION;

    @CommandLine.Option(names = {"-d", "--log-level"}, description = "Spark log level", defaultValue = "WARN")
    String debugLevel = "WARN";

    @Value
    public static class ResourceWithIssues implements Serializable {
        @Nonnull
        String resource;
        @Nullable
        List<ValidationResult.Issue> issues;


        boolean hasIssues() {
            return issues != null;
        }

        @Nonnull
        static ResourceWithIssues of(@Nonnull final String resource, @Nonnull final ValidationResult validationResult) {
            return new ResourceWithIssues(resource, validationResult.getIssues().isEmpty() ? null : validationResult.getIssues());
        }
    }

    @AllArgsConstructor
    static class Validator implements Serializable {

        @Nonnull
        private final FhirVersionEnum fhirVersionEnum;

        @Nonnull
        private Iterator<ResourceWithIssues> validatePartition(@Nonnull final Iterator<String> input) {
            final HapiValidationService validationService = HapiValidationService.getOrCreate(fhirVersionEnum);
            return Streams.streamOf(input)
                    .map(s -> ResourceWithIssues.of(s, validationService.validateJson(s.getBytes(StandardCharsets.UTF_8))))
                    .filter(ResourceWithIssues::hasIssues)
                    .iterator();
        }
    }

    public void run() {
        long startTime = System.currentTimeMillis();
        System.out.println("FHIR Happi Validator: " + this);
        final SparkSession sparkSession = SparkSession.builder()
                .appName("FhirValidator")
                .getOrCreate();
        if (!debugLevel.isEmpty()) {
            sparkSession.sparkContext().setLogLevel(debugLevel);
        }
        final FhirVersionEnum fhirVersionEnum = FhirVersionEnum.forVersionString(fhirVersion);
        System.out.println("Validation config: " + fhirVersionEnum);
        final Validator validator = new Validator(fhirVersionEnum);
        System.out.println("Validating: " + inputFile + " and writing to: " + outputFile);
        Dataset<String> ndjsonDf = sparkSession.read().textFile(inputFile);
        Dataset<ResourceWithIssues> result = ndjsonDf.mapPartitions(validator::validatePartition, Encoders.bean(ResourceWithIssues.class));
        result.toDF().write().mode(SaveMode.Overwrite).parquet(outputFile);
        long endTime = System.currentTimeMillis();
        System.out.printf("Elapsed time: %.3f s", (endTime - startTime) / 1000.0);
    }

    static int execute(String[] args) {
        return new CommandLine(new ValidateHapiApp()).execute(args);
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }
}
