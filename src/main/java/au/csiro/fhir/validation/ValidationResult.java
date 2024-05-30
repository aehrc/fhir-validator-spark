package au.csiro.fhir.validation;


import lombok.Builder;
import lombok.SneakyThrows;
import lombok.Value;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.Configuration;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.utilities.validation.ValidationMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

@Value
public class ValidationResult implements Serializable {

    enum IssueLevel {
        SUCCESS,
        INFORMATION,
        WARNING,
        ERROR,
        FATAL;


        public IssueLevel fromCode(String codeString) {
            if (codeString == null || "".equals(codeString))
                return null;
            if ("fatal".equals(codeString))
                return FATAL;
            if ("error".equals(codeString))
                return ERROR;
            if ("warning".equals(codeString))
                return WARNING;
            if ("information".equals(codeString))
                return INFORMATION;
            if ("success".equals(codeString))
                return SUCCESS;
            else
                throw new IllegalArgumentException("Unknown IssueLevel code '" + codeString + "'");
        }

        public String toCode() {
            switch (this) {
                case FATAL:
                    return "fatal";
                case ERROR:
                    return "error";
                case WARNING:
                    return "warning";
                case INFORMATION:
                    return "information";
                case SUCCESS:
                    return "success";
                default:
                    return "?";
            }
        }
    }

    @Value
    @Builder
    public static class Issue implements Serializable {
        @Nonnull
        String level;
        @Nonnull
        String type;

        @Nonnull
        String message;

        @Nullable
        @Builder.Default
        String messageId = null;

        @Nullable
        @Builder.Default
        String location = null;

        @Nullable
        @Builder.Default
        Integer line = null;

        @Nullable
        @Builder.Default
        Integer col = null;

        @Nonnull
        static Issue fromComponent(@Nonnull final OperationOutcome.OperationOutcomeIssueComponent component) {
            final IssueBuilder builder = Issue.builder()
                    .level(component.getSeverity().toCode())
                    .type(component.getCode().toCode())
                    .message(component.getDetails().getText());
            if (component.hasExpression()) {
                builder.location(component.getExpression().get(0).getValue());
            }
            component.getExtension().forEach(extension -> {
                if (extension.getUrl().equals("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id")) {
                    builder.messageId(extension.getValueCodeType().primitiveValue());
                } else if (extension.getUrl().equals("http://hl7.org/fhir/StructureDefinition/operationoutcome-issue-line")) {
                    builder.line(extension.getValueIntegerType().getValue());
                } else if (extension.getUrl().equals("http://hl7.org/fhir/StructureDefinition/operationoutcome-issue-col")) {
                    builder.col(extension.getValueIntegerType().getValue());
                }
            });

            return builder.build();
        }


        @Nonnull
        static Issue fromValidationMessage(@Nonnull final ValidationMessage message) {
            final IssueBuilder builder = Issue.builder()
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
            if (message.getMessageId() != null) {
                builder.messageId(message.getMessageId());
            }

            return builder.build();
        }
    }

    @Nonnull
    List<Issue> issues;

    @Nonnull
    @SneakyThrows
    public static ValidationResult fromOperationOutcome(@Nonnull final OperationOutcome validationOutcome) {
        return new ValidationResult(validationOutcome.getIssue().stream()
                .map(Issue::fromComponent)
                .collect(Collectors.toUnmodifiableList()));

    }

    @Nonnull
    @SneakyThrows
    public static ValidationResult fromValidationMessages(@Nonnull final List<ValidationMessage> validationMessages) {
        return new ValidationResult(validationMessages.stream()
                .map(Issue::fromValidationMessage)
                .collect(Collectors.toUnmodifiableList()));
    }

    public static ValidationResult fromException(Exception e) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
