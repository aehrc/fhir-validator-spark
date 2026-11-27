package au.csiro.fhir.validation.hl7;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.validation.cli.model.CliContext;
import org.hl7.fhir.validation.cli.utils.Params;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration of the HL7 FHIR validation engine.
 *
 * <p>
 * The engine is configured with the command line arguments of the HL7 FHIR validator (see <a
 * href="https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator">Using the FHIR
 * Validator</a>), which are parsed by the validator's own argument parser. The validator defaults
 * apply, except that no terminology server and no default resource fetcher are used unless the
 * arguments ask for them, so that validating a resource makes no network requests. Arguments that
 * have no meaning when validating individual resources, such as input files, output files and
 * other validator modes, are rejected.
 *
 * @author John Grimes
 */
@Value
@Builder
public class HL7ValidationConfig implements Serializable {

    /**
     * The FHIR version used when the arguments do not include {@code -version} (R4).
     */
    public static final String DEFAULT_VERSION = "4.0.1";

    /**
     * Arguments that precede the configured arguments. The validator would otherwise use
     * tx.fhir.org as its terminology server, and resolve unknown canonical URLs over the network.
     * {@code -disable-default-resource-fetcher} cannot be reversed by a later argument.
     */
    private static final List<String> DEFAULT_ARGS = List.of(
            "-tx", "n/a",
            "-disable-default-resource-fetcher");

    /**
     * Arguments that the validator accepts but that have no meaning when validating individual
     * resources within Spark: other modes, inputs and outputs, and process-wide settings.
     */
    private static final Set<String> UNSUPPORTED_ARGS = Set.of(
            // other modes
            "-convert", "-fhirpath", "-narrative", "-snapshot", "-spreadsheet", "-install", "-scan",
            "-transform", "-lang-transform", "-compile", "-to-version", "-run-tests", "-map",
            // inputs and outputs
            "-input", "-output", "-outputSuffix", "-html-output", "-txLog", "-txCache", "-log",
            "-mode", "-src-lang", "-tgt-lang", "-output-style",
            // process settings
            "-proxy", "-https-proxy", "-auth", "-fhir-settings", "-watch-mode", "-watch-scan-delay",
            "-watch-settle-time");

    /**
     * Prefix of the arguments that the validator skips together with the argument that follows.
     */
    private static final String IGNORED_ARG_PREFIX = "-x";

    /**
     * HL7 FHIR validator command line arguments, e.g. {@code ["-ig", "package.tgz", "-level",
     * "errors"]}. Where an option that takes a single value is repeated, the last one wins.
     */
    @Nonnull
    @Singular("validatorArg")
    List<String> validatorArgs;

    /**
     * Whether the validator logs its progress through each resource.
     */
    @Builder.Default
    boolean showProgress = false;

    /**
     * Returns the default configuration.
     *
     * @return a configuration with no validator arguments
     */
    @Nonnull
    public static HL7ValidationConfig defaultConfig() {
        return HL7ValidationConfig.builder()
                .build();
    }

    /**
     * Parses the validator arguments, preceded by the defaults of this library, with the parser of
     * the HL7 FHIR validator command line.
     *
     * @return the parsed validator settings
     * @throws IllegalArgumentException if an argument is not recognised by the validator, has an
     * invalid value, or has no meaning when validating individual resources
     */
    @Nonnull
    public CliContext toCliContext() {
        final List<String> unsupported = validatorArgs.stream()
                .filter(arg -> UNSUPPORTED_ARGS.contains(arg) || arg.startsWith(IGNORED_ARG_PREFIX))
                .collect(Collectors.toList());
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("Validator argument(s) not supported when validating in Spark: "
                    + String.join(" ", unsupported));
        }

        final List<String> args = new ArrayList<>(DEFAULT_ARGS);
        args.addAll(validatorArgs);
        final CliContext context;
        try {
            context = Params.loadCliContext(args.toArray(String[]::new));
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Validator argument "
                    + validatorArgs.get(validatorArgs.size() - 1) + " requires a value", e);
        } catch (final Exception | Error e) {
            throw new IllegalArgumentException("Invalid validator arguments: " + e.getMessage(), e);
        }

        // The validator parser treats any argument that it does not recognise as an input source.
        if (!context.getSources().isEmpty()) {
            throw new IllegalArgumentException("Validator argument(s) not recognised: "
                    + String.join(" ", context.getSources())
                    + ". Input files cannot be given as validator arguments, as Spark reads the input.");
        }

        if (context.getSv() == null) {
            context.setSv(VersionUtilities.getCurrentPackageVersion(DEFAULT_VERSION));
        }
        if (!"dev".equals(context.getSv()) && VersionUtilities.packageForVersion(context.getSv()) == null) {
            throw new IllegalArgumentException("Unsupported FHIR version: " + context.getSv());
        }
        return context;
    }
}
