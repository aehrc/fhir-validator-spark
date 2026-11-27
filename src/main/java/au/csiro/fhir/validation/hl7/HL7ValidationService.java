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
import javax.annotation.Nullable;
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

    /**
     * The engine of each thread, with the validator arguments it was built for. The HL7 validation
     * engine is not thread safe, so each thread has its own. A thread keeps one engine at a time, as
     * an engine holds all the packages it has loaded.
     */
    private static final ThreadLocal<CachedEngine> ENGINE = new ThreadLocal<>();
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
     * Returns a validation service for the given configuration. The validation engine of the
     * current thread is re-used if it was built for the same validator arguments; otherwise a new
     * engine is built and replaces it.
     *
     * @param config the validation configuration
     * @return the validation service
     * @throws IllegalArgumentException if the validator arguments of the configuration are invalid,
     * or name a profile that the loaded packages do not contain
     */
    @Nonnull
    public static HL7ValidationService getOrCreate(@Nonnull final HL7ValidationConfig config) {
        final CliContext context = config.toCliContext();
        ValidationEngine engine = reusableEngine(config.getValidatorArgs());
        if (engine != null) {
            log.debug("Re-using thread-local ValidationEngine for config: {}", config);
        } else {
            log.debug("Creating thread-local ValidationEngine for config: {}", config);
            engine = createEngine(context);
            ENGINE.set(new CachedEngine(config.getValidatorArgs(), engine));
        }
        return new HL7ValidationService(engine, config.isShowProgress(),
                resolveProfiles(engine, context.getProfiles()));
    }

    /**
     * Returns the engine of the current thread if it was built for the given validator arguments.
     * Otherwise the engine is released, so that it can be collected while the next one is built.
     *
     * @param validatorArgs the validator arguments of the configuration
     * @return the engine, or null if the thread has none for these arguments
     */
    @Nullable
    private static ValidationEngine reusableEngine(@Nonnull final List<String> validatorArgs) {
        final CachedEngine cached = ENGINE.get();
        if (cached != null && cached.validatorArgs().equals(validatorArgs)) {
            return cached.engine();
        }
        ENGINE.remove();
        return null;
    }

    private record CachedEngine(@Nonnull List<String> validatorArgs, @Nonnull ValidationEngine engine) {
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
