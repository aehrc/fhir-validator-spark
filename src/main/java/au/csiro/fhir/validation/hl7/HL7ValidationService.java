package au.csiro.fhir.validation.hl7;


import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.ValidationService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.tests.TestConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.instance.InstanceValidator;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static java.util.Objects.nonNull;

@Slf4j
public class HL7ValidationService implements ValidationService {

    /**
     * Validation engines, one per configuration, held per thread because the underlying instance
     * validator is not thread safe. An engine is built at most once per thread and configuration,
     * and reused for every resource that thread validates.
     */
    private static final ThreadLocal<Map<HL7ValidationConfig, ValidationEngine>> ENGINES =
            ThreadLocal.withInitial(HashMap::new);
    private final ValidationEngine validationEngine;
    private final boolean showProgress;

    public HL7ValidationService(@Nonnull final ValidationEngine validationEngine, boolean showProgress) {
        this.validationEngine = validationEngine;
        this.showProgress = showProgress;
    }

    @Override
    @Nonnull
    public ValidationResult validateJson(@Nonnull final byte[] data) {
        try {
            // this replicates the functionality of ValidationEngine.validate()
            // but is needed to customize the InstanceValidator
            final Manager.FhirFormat format = Manager.FhirFormat.JSON;
            List<ValidationMessage> messages = new ArrayList<>();
            InstanceValidator validator = validationEngine.getValidator(format);
            // customize the instance validator
            validator.setLogProgress(showProgress);
            validator.validate(null, messages, new ByteArrayInputStream(data), format, Collections.emptyList());
            return HL7Utils.validationMessagesToResult(messages);
        } catch (final IOException e) {
            return ValidationResult.fromException(e);
        }
    }

    /**
     * Returns a validation service for the given configuration, building its engine only if this
     * thread has not already built one for that configuration.
     *
     * @param config the validation configuration
     * @return a validation service backed by a cached engine
     */
    @Nonnull
    @SneakyThrows
    public static HL7ValidationService getOrCreate(@Nonnull final HL7ValidationConfig config) {
        final ValidationEngine engine = ENGINES.get().computeIfAbsent(config, c -> {
            log.debug("Setting thread-local ValidationEngine for config: {}", c);
            return createEngine(c);
        });
        return new HL7ValidationService(engine, config.isShowProgress());
    }

    @Nonnull
    @SneakyThrows
    // synchronize here - this increases the init time but seems the only way
    // for not to get around all the synchronization issues
    public static synchronized ValidationEngine createEngine(@Nonnull final HL7ValidationConfig config) {

        // based on:
        // org.hl7.fhir.validation.ValidatorCli.getValidationEngine
        // org.hl7.fhir.validation.cli.services.ValidationService.buildValidationEngine

        log.debug("Creating new ValidationEngine for config: {}", config);
        ValidationEngine.ValidationEngineBuilder builder = nonNull(config.getTxSever())
                ? new ValidationEngine.ValidationEngineBuilder().withTxServer(config.getTxSever(),
                null, FhirPublication.fromCode(config.getVersion()), true)
                : new ValidationEngine.ValidationEngineBuilder().withNoTerminologyServer();

        final String fhirDefinition = HL7Utils.getFhirDefinitionFromVersion(config.getVersion());
        log.info("Load FHIR v{} from {}", config.getVersion(), fhirDefinition);
        final ValidationEngine validationEngine = builder
                // The terminology package is only loaded when a version is set, so leaving the
                // version unset is the replacement for the withTHO(false) of earlier releases.
                .withThoVersion(null)
                .withCanRunWithoutTerminologyServer(true)
                .withVersion(config.getVersion())
                .withUserAgent(TestConstants.USER_AGENT)
                .fromSource(fhirDefinition)
                .setLanguage(config.getLanguage())
                .setBestPracticeLevel(config.getBestPracticeLevel())
                .setLevel(config.getValidationLevel())
                .setDisplayWarnings(config.isDisplayMismatchAsWarning())
                .setShowTimes(false)
                .setDebug(false);
        for (final String ig : config.getIgs()) {
            validationEngine.loadPackage(ig, null);
        }
        log.info("Package Summary: {}", validationEngine.getContext().loadedPackageSummary());
        return validationEngine;
    }

}
