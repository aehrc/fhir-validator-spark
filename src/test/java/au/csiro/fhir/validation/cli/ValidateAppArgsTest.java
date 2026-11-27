package au.csiro.fhir.validation.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link ValidateApp} turns its options and the arguments after {@code --} into HL7
 * validator arguments. These tests do not start Spark.
 *
 * @author John Grimes
 */
class ValidateAppArgsTest {

    private static ValidateApp parse(final String... args) {
        final CommandLine commandLine = ValidateApp.commandLine();
        commandLine.parseArgs(args);
        return commandLine.getCommand();
    }

    @Test
    void noOptionsGiveNoValidatorArguments() {
        // The defaults are applied by HL7ValidationConfig, not by ValidateApp.
        assertEquals(List.of(), parse("in", "out").toConfig().getValidatorArgs());
    }

    @Test
    void existingOptionsAreExpressedAsValidatorArguments() {
        final ValidateApp app = parse("in", "out",
                "--fhir-version", "5.0.0",
                "--ig", "a.tgz", "--ig", "b.tgz",
                "--language", "de",
                "--tx-server", "https://tx.example.org/fhir");
        assertEquals(List.of(
                        "-version", "5.0.0",
                        "-ig", "a.tgz",
                        "-ig", "b.tgz",
                        "-language", "de",
                        "-tx", "https://tx.example.org/fhir"),
                app.toConfig().getValidatorArgs());
    }

    @Test
    void argumentsAfterDoubleDashArePassedThroughAfterOptions() {
        // Pass-through arguments come last so that they override the ValidateApp options.
        final ValidateApp app = parse("in", "out", "--ig", "a.tgz", "--",
                "-level", "errors", "-extension", "any", "-ig", "b.tgz");
        assertEquals(List.of("-ig", "a.tgz", "-level", "errors", "-extension", "any", "-ig", "b.tgz"),
                app.toConfig().getValidatorArgs());
    }

    @Test
    void logProgressSetsShowProgress() {
        assertTrue(parse("in", "out", "--log-progress").toConfig().isShowProgress());
        assertFalse(parse("in", "out").toConfig().isShowProgress());
    }

    @Test
    void validatorOptionBeforeDoubleDashIsAnUnknownOption() {
        // Validator arguments must follow --, otherwise picocli rejects them. Without clustered
        // short options, -level is not read as -l with the value "evel".
        assertThrows(CommandLine.UnmatchedArgumentException.class, () -> parse("in", "out", "-level", "errors"));
    }

    @Test
    void invalidValidatorArgumentFailsBeforeSparkStarts() {
        // An argument the validator does not recognise is a usage error, and no output is written.
        final Path output = Path.of("target/ValidateAppArgsTest-invalid.parquet");
        final int exitCode = ValidateApp.execute(new String[]{
                ValidateAppTest.MEDICATION_REQUEST_R4, output.toString(), "--", "-no-such-option"});
        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        assertFalse(Files.exists(output));
    }
}
