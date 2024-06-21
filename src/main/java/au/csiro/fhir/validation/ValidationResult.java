package au.csiro.fhir.validation;


import lombok.*;
import org.hl7.fhir.utilities.validation.ValidationMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
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
            // Try to resolve message id
            HL7MessageResolver.getMessageId(message.getMessage()).ifPresent(builder::messageId);
            return builder.build();
        }
    }

    @Nonnull
    List<Issue> issues;


    @Nonnull
    @SneakyThrows
    public static ValidationResult fromValidationMessages(@Nonnull final List<ValidationMessage> validationMessages) {
        return new ValidationResult(validationMessages.stream()
                .map(Issue::fromValidationMessage)
                .collect(Collectors.toUnmodifiableList()));
    }

    @Nonnull
    public static ValidationResult fromException(@Nonnull final Exception ex) {
        return new ValidationResult(List.of(Issue.builder()
                .level(IssueLevel.FATAL.toCode())
                .type(ex.getClass().getSimpleName())
                .message(ex.getMessage())
                .build()));
    }


    @Nonnull
    public static ValidationResult of(@Nonnull final Issue... issues) {
        return new ValidationResult(List.of(issues));
    }
}
