package au.csiro.fhir.validation;


import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.hl7.fhir.utilities.tests.TestConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.cli.utils.ValidationLevel;
import org.hl7.fhir.validation.instance.InstanceValidator;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

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

        synchronized (INSTANCES) {
            log.debug("Setting thread-local clone of ValidationEngine for config: {}", config);
            ENGINE.set(new ValidationEngine(INSTANCES.computeIfAbsent(config, ValidationService::create).validationEngine));
            return new ValidationService(ENGINE.get(), config.isShowProgress());
        }
    }

    @Nonnull
    @SneakyThrows
    private static ValidationService create(@Nonnull final ValidationConfig config) {
        log.debug("Creating new ValidationEngine prototype for config: {}", config);
        final ValidationEngine validationEngine = new ValidationEngine.ValidationEngineBuilder()
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
        System.out.println("Package Summary: " + validationEngine.getContext().loadedPackageSummary());
        return new ValidationService(validationEngine, config.isShowProgress());
    }
}
