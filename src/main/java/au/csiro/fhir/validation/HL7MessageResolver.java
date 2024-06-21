package au.csiro.fhir.validation;

import lombok.SneakyThrows;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class HL7MessageResolver {

    // THESE have exact duplicate messages
    private static Set<String> EXCLUDE = Set.of(
            "BUNDLE_BUNDLE_ENTRY_NOTFOUND_FRAGMENT",
            "VALIDATION_VAL_PROFILE_MAXIMUM_OTHER",
            "VALIDATION_VAL_PROFILE_MINIMUM_OTHER");

    @SneakyThrows
    @Nonnull
    static Map<String, String> loadMessages() {
        Properties p = new Properties();
        p.load(HL7MessageResolver.class.getResourceAsStream("/Messages.properties"));
        final Map<String, String> messages = p.entrySet().stream().collect(Collectors.toUnmodifiableMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
        return messages.entrySet().stream()
                .filter(kv -> !kv.getValue().matches("([\\s]*\\{\\d+\\})*[\\s]*"))
                .filter(kv -> !"Bad_file_path_error".equals(kv.getKey()))
                .filter(kv -> !kv.getKey().startsWith("_") && !EXCLUDE.contains(kv.getKey().toUpperCase()))
                .collect(Collectors.toUnmodifiableMap(kv -> kv.getKey().replace("_", "0").toUpperCase(), kv -> kv.getValue().trim()));
    }

    private static final MessageResolver INSTANCE = MessageResolver.ofMessages(loadMessages());

    @Nonnull
    public static Optional<String> getMessageId(@Nonnull final String message) {
        return INSTANCE.getMessageId(message).map(s -> s.replace("0", "_"));
    }
}
