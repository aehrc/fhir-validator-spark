package au.csiro.fhir.validation;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

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
            if (codeString == null || codeString.isEmpty())
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
    }

    @Nonnull
    List<Issue> issues;

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
