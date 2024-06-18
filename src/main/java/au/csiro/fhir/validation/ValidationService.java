package au.csiro.fhir.validation;


import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.tests.TestConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.cli.utils.ValidationLevel;
import org.hl7.fhir.validation.instance.InstanceValidator;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static java.util.Objects.nonNull;

@Slf4j
public class ValidationService {

    private static final Map<ValidationConfig, ValidationService> INSTANCES = new HashMap<>();

    private static final ThreadLocal<ValidationEngine> ENGINE = new ThreadLocal<>();

    private final ValidationEngine validationEngine;
    private final boolean showProgress;

    public ValidationService(@Nonnull final ValidationEngine validationEngine, boolean showProgress) {
        this.validationEngine = validationEngine;
        this.showProgress = showProgress;
    }

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
            return ValidationResult.fromValidationMessages(messages);
        } catch (final IOException e) {
            return ValidationResult.fromException(e);
        }
    }

    @Nonnull
    @SneakyThrows
    public static ValidationService getOrCreate(@Nonnull final ValidationConfig config) {
        if (ENGINE.get() != null) {
            log.debug("Re-using thread-local ValidationEngine for config: {}", config);
            return new ValidationService(ENGINE.get(), config.isShowProgress());
        }

        log.debug("Setting thread-local clone of ValidationEngine for config: {}", config);
        ENGINE.set(createEngine(config));
        return new ValidationService(ENGINE.get(), config.isShowProgress());
    }

    @Nonnull
    @SneakyThrows
    // synchronize here - this increases the init time but seems the only way
    // for not to get around all the synchronization issues
    public static synchronized ValidationEngine createEngine(@Nonnull final ValidationConfig config) {
        log.debug("Creating new ValidationEngine for config: {}", config);
        ValidationEngine.ValidationEngineBuilder builder = nonNull(config.getTxSever())
                ? new ValidationEngine.ValidationEngineBuilder().withTxServer(config.getTxSever(),
                null, FhirPublication.fromCode(config.getVersion()), true)
                : new ValidationEngine.ValidationEngineBuilder().withNoTerminologyServer();

        final ValidationEngine validationEngine = builder
                .withCanRunWithoutTerminologyServer(true)
                .withVersion(config.getVersion())
                .withUserAgent(TestConstants.USER_AGENT)
                // TODO: this need to be either a parameter or somehow inferred
                .fromSource("hl7.fhir.r4.core#4.0.1")
                .setBestPracticeLevel(BestPracticeWarningLevel.Error)
                .setLevel(ValidationLevel.ERRORS)
                .setShowTimes(false)
                .setDebug(false);
        for (final String ig : config.getIgs()) {
            validationEngine.loadPackage(ig, null);
        }
        // TODO: change this to logger info
        log.info("Package Summary: {}", validationEngine.getContext().loadedPackageSummary());
        return validationEngine;
    }

}
