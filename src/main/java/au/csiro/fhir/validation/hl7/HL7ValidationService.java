package au.csiro.fhir.validation.hl7;


import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.ValidationService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.utilities.TimeTracker;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.cli.model.CliContext;
import org.hl7.fhir.validation.instance.InstanceValidator;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Validates FHIR resources with the HL7 FHIR validator.
 *
 * @author John Grimes
 */
@Slf4j
public class HL7ValidationService implements ValidationService {

    private static final ThreadLocal<ValidationEngine> ENGINE = new ThreadLocal<>();
    private final ValidationEngine validationEngine;
    private final boolean showProgress;
    private final List<StructureDefinition> profiles;

    /**
     * Creates a validation service.
     *
     * @param validationEngine the engine to validate with
     * @param showProgress whether the validator logs its progress through each resource
     * @param profiles the profiles to validate each resource against, in addition to those it
     * declares
     */
    public HL7ValidationService(@Nonnull final ValidationEngine validationEngine, boolean showProgress,
                                @Nonnull final List<StructureDefinition> profiles) {
        this.validationEngine = validationEngine;
        this.showProgress = showProgress;
        this.profiles = profiles;
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
            validator.validate(null, messages, new ByteArrayInputStream(data), format, profiles);
            return HL7Utils.validationMessagesToResult(messages);
        } catch (final IOException e) {
            return ValidationResult.fromException(e);
        }
    }

    /**
     * Returns the engine this service validates with.
     *
     * @return the validation engine
     */
    @Nonnull
    ValidationEngine getValidationEngine() {
        return validationEngine;
    }

    /**
     * Returns a validation service for the given configuration, re-using the validation engine of
     * the current thread if there is one.
     *
     * @param config the validation configuration
     * @return the validation service
     * @throws IllegalArgumentException if the validator arguments of the configuration are invalid,
     * or name a profile that the loaded packages do not contain
     */
    @Nonnull
    public static HL7ValidationService getOrCreate(@Nonnull final HL7ValidationConfig config) {
        final CliContext context = config.toCliContext();
        // NOTE: This is currently assuming that the config does not change for a thread
        // and always returns the exising engine with the initial config regardless of the input config
        if (ENGINE.get() != null) {
            log.debug("Re-using thread-local ValidationEngine for config: {}", config);
        } else {
            log.debug("Setting thread-local clone of ValidationEngine for config: {}", config);
            ENGINE.set(createEngine(context));
        }
        return new HL7ValidationService(ENGINE.get(), config.isShowProgress(),
                resolveProfiles(ENGINE.get(), context.getProfiles()));
    }

    /**
     * Creates a validation engine for the given configuration.
     *
     * @param config the validation configuration
     * @return a new validation engine
     * @throws IllegalArgumentException if the validator arguments of the configuration are invalid
     */
    @Nonnull
    public static ValidationEngine createEngine(@Nonnull final HL7ValidationConfig config) {
        return createEngine(config.toCliContext());
    }

    @Nonnull
    @SneakyThrows
    // synchronize here - this increases the init time but seems the only way
    // for not to get around all the synchronization issues
    private static synchronized ValidationEngine createEngine(@Nonnull final CliContext context) {
        log.debug("Creating new ValidationEngine for validator context: {}", context);
        return new EngineBuilder().build(context);
    }

    @Nonnull
    private static List<StructureDefinition> resolveProfiles(@Nonnull final ValidationEngine engine,
                                                             @Nonnull final List<String> urls) {
        final List<StructureDefinition> profiles = new ArrayList<>();
        for (final String url : urls) {
            final StructureDefinition profile = engine.getContext().fetchResource(StructureDefinition.class, url);
            if (profile == null) {
                throw new IllegalArgumentException("Unable to resolve profile " + url
                        + ": load the package that defines it with -ig");
            }
            profiles.add(profile);
        }
        return profiles;
    }

    /**
     * Builds validation engines the way the HL7 validator command line does, so that every
     * validator argument has the same effect here as it has there.
     */
    private static final class EngineBuilder extends org.hl7.fhir.validation.cli.services.ValidationService {

        @Nonnull
        ValidationEngine build(@Nonnull final CliContext context) throws Exception {
            return buildValidationEngine(context, HL7Utils.getFhirDefinitionFromVersion(context.getSv()),
                    new TimeTracker());
        }
    }
}
