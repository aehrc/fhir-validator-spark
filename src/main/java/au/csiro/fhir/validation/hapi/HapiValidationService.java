package au.csiro.fhir.validation.hapi;

import au.csiro.fhir.validation.ValidationResult;
import au.csiro.fhir.validation.ValidationService;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.JsonParser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HapiValidationService implements ValidationService {


    private static final ThreadLocal<Map<FhirVersionEnum, FhirContext>> LOCAL = ThreadLocal.withInitial(HashMap::new);

    private final FhirContext fhirContext;

    @Override
    @Nonnull
    public ValidationResult validateJson(@Nonnull final byte[] data) {
        try {
            final CollectorErrorHandler errorHandler = new CollectorErrorHandler();
            new JsonParser(fhirContext, errorHandler).parseResource(new ByteArrayInputStream(data));
            return errorHandler.toValidationResult();
        } catch (DataFormatException e) {
            return ValidationResult.fromException(e);
        }
    }

    @Nonnull
    public static HapiValidationService getOrCreate(@Nonnull final FhirVersionEnum version) {
        return new HapiValidationService(LOCAL.get().computeIfAbsent(version, FhirContext::new));
    }

}
