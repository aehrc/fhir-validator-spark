package au.csiro.fhir.validation.hl7;

import au.csiro.fhir.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the per-thread cache of validation engines in {@link HL7ValidationService}.
 *
 * <p>
 * Each thread keeps one engine, built for the last configuration it was given. All tests run on
 * the test thread, so they exercise the cache of a single thread.
 *
 * @author John Grimes
 */
class HL7ValidationServiceCacheTest {

    /**
     * A resource for which the validator reports a dom-6 warning (no narrative), and nothing else.
     */
    private static final byte[] PATIENT = "{\"resourceType\":\"Patient\"}".getBytes(StandardCharsets.UTF_8);

    private static final HL7ValidationConfig DEFAULT_CONFIG = HL7ValidationConfig.defaultConfig();

    private static final HL7ValidationConfig ERRORS_ONLY_CONFIG = HL7ValidationConfig.builder()
            .validatorArgs(List.of("-level", "errors"))
            .build();

    @Test
    void differentConfigurationsOnOneThreadEachGetTheirOwnResult() {
        // The default level reports the warning, -level errors does not. Going back to the default
        // configuration shows that the second configuration did not stick to the thread either.
        assertEquals(List.of("warning"), levels(HL7ValidationService.getOrCreate(DEFAULT_CONFIG).validateJson(PATIENT)));
        assertEquals(List.of(), levels(HL7ValidationService.getOrCreate(ERRORS_ONLY_CONFIG).validateJson(PATIENT)));
        assertEquals(List.of("warning"), levels(HL7ValidationService.getOrCreate(DEFAULT_CONFIG).validateJson(PATIENT)));
    }

    @Test
    void sameConfigurationReusesTheEngine() {
        // An equal configuration, built separately, must not rebuild the engine.
        final HL7ValidationConfig equalConfig = HL7ValidationConfig.builder().build();
        assertSame(HL7ValidationService.getOrCreate(DEFAULT_CONFIG).getValidationEngine(),
                HL7ValidationService.getOrCreate(equalConfig).getValidationEngine());
    }

    @Test
    void showProgressDoesNotRebuildTheEngine() {
        // Progress logging is a setting of the service, not of the engine.
        final HL7ValidationConfig withProgress = HL7ValidationConfig.builder().showProgress(true).build();
        assertSame(HL7ValidationService.getOrCreate(DEFAULT_CONFIG).getValidationEngine(),
                HL7ValidationService.getOrCreate(withProgress).getValidationEngine());
    }

    @Test
    void differentConfigurationRebuildsTheEngine() {
        assertNotSame(HL7ValidationService.getOrCreate(DEFAULT_CONFIG).getValidationEngine(),
                HL7ValidationService.getOrCreate(ERRORS_ONLY_CONFIG).getValidationEngine());
    }

    private static List<String> levels(final ValidationResult result) {
        return result.getIssues().stream().map(ValidationResult.Issue::getLevel).toList();
    }
}
