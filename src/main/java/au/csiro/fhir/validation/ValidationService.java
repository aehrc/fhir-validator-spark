package au.csiro.fhir.validation;


import lombok.SneakyThrows;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.hl7.fhir.utilities.tests.TestConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.ValidatorUtils;
import org.hl7.fhir.validation.cli.utils.ValidationLevel;
import org.hl7.fhir.validation.instance.InstanceValidator;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class ValidationService {

    private static final Map<ValidationConfig, ValidationService> INSTANCES = new HashMap<>();

    private static final ThreadLocal<ValidationEngine> ENGINE = new ThreadLocal<>();

    private final ValidationEngine validationEngine;
    private final boolean showProgress;

    public ValidationService(@Nonnull final ValidationEngine validationEngine, boolean showProgress) {
        this.validationEngine = validationEngine;
        this.showProgress = showProgress;
    }

    // HACK: this is a hack to access the protected method messagesToOutcome
    static class MyValidatorUtils extends ValidatorUtils {
        static public OperationOutcome messagesToOutcome(List<ValidationMessage> messages, SimpleWorkerContext context, FHIRPathEngine fpe) throws IOException, FHIRException, EOperationOutcome {
            return ValidatorUtils.messagesToOutcome(messages, context, fpe);
        }
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
            return new ValidationService(ENGINE.get(), config.isShowProgress());
        }

        synchronized (INSTANCES) {
            ENGINE.set(new ValidationEngine(INSTANCES.computeIfAbsent(config, ValidationService::create).validationEngine));
            return new ValidationService(ENGINE.get(), config.isShowProgress());
        }
    }

    @Nonnull
    @SneakyThrows
    private static ValidationService create(@Nonnull final ValidationConfig config) {
        final ValidationEngine validationEngine = new ValidationEngine.ValidationEngineBuilder()
                .withCanRunWithoutTerminologyServer(true)
                .withVersion(config.getVersion())
                .withUserAgent(TestConstants.USER_AGENT)
                .fromSource("hl7.fhir.r4.core#4.0.1")
                .setBestPracticeLevel(BestPracticeWarningLevel.Error)
                .setLevel(ValidationLevel.ERRORS)
                .setShowTimes(false)
                .setDebug(false);
        for (final String ig : config.getIgs()) {
            validationEngine.loadPackage(ig, null);
        }
        System.out.println("  Package Summary: "+ validationEngine.getContext().loadedPackageSummary());
        return new ValidationService(validationEngine, config.isShowProgress());
    }
}
