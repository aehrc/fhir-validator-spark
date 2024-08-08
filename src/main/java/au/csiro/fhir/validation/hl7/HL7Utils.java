package au.csiro.fhir.validation.hl7;

import au.csiro.fhir.validation.ValidationResult;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.validation.ValidationMessage;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class HL7Utils {

    @Nonnull
    public static String getFhirDefinitionFromVersion(@Nonnull final String version) {
        return "dev".equals(version)
                ? "hl7.fhir.r5.core#current"
                : VersionUtilities.packageForVersion(version) + "#" + VersionUtilities.getCurrentVersion(version);
    }


    @Nonnull
    static ValidationResult.Issue validationMessageToIssue(@Nonnull final ValidationMessage message) {
        final ValidationResult.Issue.IssueBuilder builder = ValidationResult.Issue.builder()
                .level(message.getLevel().toCode())
                .type(message.getType().toCode())
                .message(message.getMessage())
                .location(message.getLocation())
                .messageId(message.getMessageId());

        // pass through line/col if they're present
        if (message.getLine() >= 0) {
            builder.line(message.getLine());
        }
        if (message.getCol() >= 0) {
            builder.col(message.getCol());
        }
        if (message.getLocation() != null) {
            builder.location(message.getLocation());
        }
        // Try to resolve message id
        HL7MessageResolver.getMessageId(message.getMessage()).ifPresent(builder::messageId);
        return builder.build();
    }



    @Nonnull
    @SneakyThrows
    public static ValidationResult validationMessagesToResult(@Nonnull final List<ValidationMessage> validationMessages) {
        return new ValidationResult(validationMessages.stream()
                .map(HL7Utils::validationMessageToIssue)
                .collect(Collectors.toUnmodifiableList()));
    }


}
