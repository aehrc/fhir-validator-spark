package au.csiro.fhir.validation;

import javax.annotation.Nonnull;

public interface ValidationService {

    @Nonnull
    ValidationResult validateJson(@Nonnull final byte[] data);
}
