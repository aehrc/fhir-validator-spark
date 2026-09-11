package au.csiro.fhir.validation.spark;

import au.csiro.fhir.validation.hl7.HL7ValidationConfig;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

/**
 * Configuration for {@link ValidationApi}.
 * <p>
 * Carries the implementation guides to validate against, the terminology server to resolve codes
 * with, the FHIR version, and the names of the columns the API reads.
 *
 * @author John Grimes
 */
@Value
@Builder
public class ValidationConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The default name of the column holding the FHIR resource JSON.
     */
    public static final String DEFAULT_RESOURCE_COLUMN = "resource";

    /**
     * The default name of the column holding the name of the file a resource came from.
     */
    public static final String DEFAULT_FILENAME_COLUMN = "filename";

    /**
     * Implementation guides to load, each either a package identifier ({@code id#version}) or the
     * path of a package tarball.
     */
    @Nonnull
    @Singular("ig")
    List<String> igs;

    /**
     * The FHIR version to validate against.
     */
    @Nonnull
    @Builder.Default
    String fhirVersion = HL7ValidationConfig.DEFAULT_VERSION;

    /**
     * Whether to resolve terminology at all. When false, no terminology server is used and codes
     * are only checked against the code systems the loaded packages supply.
     */
    @Builder.Default
    boolean resolveTerminology = true;

    /**
     * The terminology server to resolve codes with. Ignored when {@link #resolveTerminology} is
     * false.
     */
    @Nullable
    @Builder.Default
    String terminologyServerUrl = null;

    /**
     * The name of the column holding the FHIR resource JSON.
     */
    @Nonnull
    @Builder.Default
    String resourceColumn = DEFAULT_RESOURCE_COLUMN;

    /**
     * The name of the column holding the name of the file a resource came from.
     */
    @Nonnull
    @Builder.Default
    String filenameColumn = DEFAULT_FILENAME_COLUMN;

    /**
     * The language to report issues in, or null for the validator default.
     */
    @Nullable
    @Builder.Default
    String language = null;

    /**
     * Whether display mismatches in codings are reported as warnings rather than errors.
     */
    @Builder.Default
    boolean displayMismatchAsWarning = true;

    /**
     * Whether to log validation progress to standard output.
     */
    @Builder.Default
    boolean logProgress = false;

    /**
     * Translates this configuration into the configuration of the underlying HL7 validation
     * engine. This is the same configuration the command line application builds, so the two
     * producers report the same issues.
     *
     * @return the engine configuration
     */
    @Nonnull
    public HL7ValidationConfig toEngineConfig() {
        return HL7ValidationConfig.builder()
                .version(fhirVersion)
                .igs(igs)
                .txSever(resolveTerminology
                        ? terminologyServerUrl
                        : null)
                .language(language)
                .showProgress(logProgress)
                .bestPracticeLevel(BestPracticeWarningLevel.Warning)
                .displayMismatchAsWarning(displayMismatchAsWarning)
                .build();
    }
}
