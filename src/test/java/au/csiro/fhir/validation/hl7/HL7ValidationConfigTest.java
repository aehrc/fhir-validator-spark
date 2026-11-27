package au.csiro.fhir.validation.hl7;

import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.hl7.fhir.validation.cli.model.CliContext;
import org.hl7.fhir.validation.cli.utils.ValidationLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the parsing of HL7 validator arguments in {@link HL7ValidationConfig}. These tests do
 * not build a validation engine.
 *
 * @author John Grimes
 */
class HL7ValidationConfigTest {

    private static CliContext parse(final String... args) {
        return HL7ValidationConfig.builder().validatorArgs(List.of(args)).build().toCliContext();
    }

    private static IllegalArgumentException rejected(final String... args) {
        return assertThrows(IllegalArgumentException.class, () -> parse(args));
    }

    // ---- Defaults ----

    @Test
    void defaultsUseNoTerminologyServerAndNoResourceFetcher() {
        // These are the two deliberate differences from the upstream validator defaults.
        final CliContext context = HL7ValidationConfig.defaultConfig().toCliContext();
        assertNull(context.getTxServer());
        assertTrue(context.isDisableDefaultResourceFetcher());
    }

    @Test
    void defaultVersionIsR4() {
        assertEquals("4.0", HL7ValidationConfig.defaultConfig().toCliContext().getSv());
    }

    @Test
    void otherDefaultsFollowUpstream() {
        // The values ValidateApp used to hardcode are now the upstream defaults.
        final CliContext context = HL7ValidationConfig.defaultConfig().toCliContext();
        final CliContext upstream = new CliContext();
        assertEquals(upstream.getLevel(), context.getLevel());
        assertEquals(upstream.getBestPracticeLevel(), context.getBestPracticeLevel());
        assertEquals(upstream.isDisplayWarnings(), context.isDisplayWarnings());
        assertEquals(upstream.isAllowExampleUrls(), context.isAllowExampleUrls());
        assertTrue(context.getExtensions().isEmpty());
    }

    // ---- Arguments override the defaults ----

    @Test
    void terminologyServerArgumentOverridesDefault() {
        assertEquals("https://tx.example.org/fhir", parse("-tx", "https://tx.example.org/fhir").getTxServer());
    }

    @Test
    void versionArgumentOverridesDefault() {
        assertEquals("5.0", parse("-version", "5.0.0").getSv());
    }

    @Test
    void laterArgumentWinsForSingleValuedOptions() {
        // ValidateApp puts its own options first, so pass-through arguments override them.
        assertEquals(ValidationLevel.ERRORS, parse("-level", "warnings", "-level", "errors").getLevel());
    }

    // ---- Upstream arguments are parsed by the upstream parser ----

    @Test
    void validationOptionsAreParsed() {
        final CliContext context = parse(
                "-level", "errors",
                "-best-practice", "ignore",
                "-display-issues-are-warnings",
                "-extension", "any",
                "-allow-example-urls", "true",
                "-ig", "data/packages/kindlab.fhir.mimic/package.tgz",
                "-profile", "http://example.org/StructureDefinition/p");
        assertEquals(ValidationLevel.ERRORS, context.getLevel());
        assertEquals(BestPracticeWarningLevel.Ignore, context.getBestPracticeLevel());
        assertTrue(context.isDisplayWarnings());
        assertEquals(List.of("any"), context.getExtensions());
        assertTrue(context.isAllowExampleUrls());
        assertEquals(List.of("data/packages/kindlab.fhir.mimic/package.tgz"), context.getIgs());
        assertEquals(List.of("http://example.org/StructureDefinition/p"), context.getProfiles());
    }

    // ---- Rejected arguments ----

    @Test
    void unrecognisedArgumentIsRejected() {
        // The upstream parser treats anything it does not recognise as an input source.
        final IllegalArgumentException e = rejected("-level", "errors", "-no-such-option");
        assertTrue(e.getMessage().contains("-no-such-option"), e.getMessage());
        assertFalse(e.getMessage().contains("errors"), e.getMessage());
    }

    @Test
    void inputFileIsRejected() {
        final IllegalArgumentException e = rejected("patient.json");
        assertTrue(e.getMessage().contains("patient.json"), e.getMessage());
    }

    @Test
    void otherValidatorTasksAreRejected() {
        // Tasks such as -compare are not parsed by Params at all, so they arrive as sources.
        final IllegalArgumentException e = rejected("-compare", "-left", "a", "-right", "b");
        assertTrue(e.getMessage().contains("-compare"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // other modes
            "-convert", "-fhirpath", "-narrative", "-snapshot", "-spreadsheet", "-install", "-scan",
            "-transform", "-lang-transform", "-compile", "-to-version", "-run-tests", "-map",
            // inputs and outputs
            "-input", "-output", "-outputSuffix", "-html-output", "-txLog", "-txCache", "-log",
            "-mode", "-src-lang", "-tgt-lang", "-output-style",
            // process settings that have no effect inside Spark
            "-proxy", "-https-proxy", "-auth", "-fhir-settings", "-watch-mode", "-watch-scan-delay",
            "-watch-settle-time", "-x"})
    void unsupportedOptionIsRejected(final String option) {
        final IllegalArgumentException e = rejected(option, "value");
        assertTrue(e.getMessage().contains(option), e.getMessage());
    }

    @Test
    void invalidOptionValueIsRejected() {
        final IllegalArgumentException e = rejected("-best-practice", "sometimes");
        assertTrue(e.getMessage().contains("sometimes"), e.getMessage());
    }

    @Test
    void missingOptionValueIsRejected() {
        // Params indexes past the end of the array for some options that take a value.
        final IllegalArgumentException e = rejected("-extension");
        assertTrue(e.getMessage().contains("-extension"), e.getMessage());
    }

    @Test
    void unsupportedFhirVersionIsRejected() {
        final IllegalArgumentException e = rejected("-version", "9.9");
        assertTrue(e.getMessage().contains("9.9"), e.getMessage());
    }

    // ---- Serialisation ----

    @Test
    void configSurvivesJavaSerialisation() throws Exception {
        // Spark ships the configuration to executors with Java serialisation.
        final HL7ValidationConfig config = HL7ValidationConfig.builder()
                .validatorArgs(List.of("-level", "errors"))
                .showProgress(true)
                .build();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(config);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(config, in.readObject());
        }
    }
}
