package au.csiro.fhir.validation;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

@Value
@Builder
public class ValidationConfig implements Serializable {

    @Nullable
    String version;

    @Nonnull
    @Singular("ig")
    List<String> igs;


    public static ValidationConfig defaultConfig() {
        return ValidationConfig.builder()
                .build();
    }
}
