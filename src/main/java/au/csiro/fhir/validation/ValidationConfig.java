package au.csiro.fhir.validation;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.hl7.fhir.validation.cli.utils.ValidationLevel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

@Value
@Builder
public class ValidationConfig implements Serializable {

    // default version is R4
    public static final String DEFAULT_VERSION = "4.0.1";

    @Nonnull
    @Builder.Default
    String version = DEFAULT_VERSION;

    @Nonnull
    @Singular("ig")
    List<String> igs;

    @Builder.Default
    boolean showProgress = false;

    @Nullable
    @Builder.Default
    String language = null;

    @Nullable
    @Builder.Default
    String txSever = null;

    /**
     * If true, display mismatches in codings are reported as warnings instead of errors
     */
    @Builder.Default
    boolean displayMismatchAsWarning = false;


    @Nonnull
    @Builder.Default
    BestPracticeWarningLevel bestPracticeLevel = BestPracticeWarningLevel.Warning;

    @Nonnull
    @Builder.Default
    ValidationLevel validationLevel = ValidationLevel.HINTS;


    public static ValidationConfig defaultConfig() {
        return ValidationConfig.builder()
                .build();
    }

    public static ValidationConfig fromIGs(String... igs) {
        return ValidationConfig.builder()
                .igs(List.of(igs))
                .build();
    }

}
