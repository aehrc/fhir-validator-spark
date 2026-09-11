package au.csiro.fhir.validation.hl7;

import lombok.SneakyThrows;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Resolves the message id of a validator message, by matching the message against the templates
 * the validator generates its messages from.
 *
 * @author John Grimes
 */
public class HL7MessageResolver {

    /**
     * Loads the message templates the validator can be told apart by.
     * <p>
     * Many templates are shared by more than one id, most often the singular and plural forms of
     * the same message, and those ids cannot be told apart from the message alone. One
     * representative of each group of identical templates is kept, chosen by id so that the choice
     * is stable across releases.
     *
     * @return message ids by their template
     */
    @SneakyThrows
    @Nonnull
    static Map<String, String> loadMessages() {
        final Properties p = new Properties();
        p.load(HL7MessageResolver.class.getResourceAsStream("/Messages.properties"));
        return p.entrySet().stream()
                .map(e -> Map.entry(e.getKey().toString(), e.getValue().toString()))
                // Templates that are nothing but parameters match everything.
                .filter(kv -> !kv.getValue().matches("([\\s]*\\{\\d+\\})*[\\s]*"))
                .filter(kv -> !"Bad_file_path_error".equals(kv.getKey()))
                .filter(kv -> !kv.getKey().startsWith("_"))
                // Named groups in the resolver's pattern cannot contain underscores.
                .map(kv -> Map.entry(kv.getKey().replace("_", "0").toUpperCase(),
                        kv.getValue().trim()))
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.groupingBy(kv -> MessageResolver.messageToRegex(kv.getValue()),
                        LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(group -> group.stream().min(Comparator.comparing(Map.Entry::getKey)).orElseThrow())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static final MessageResolver INSTANCE = MessageResolver.ofMessages(loadMessages());

    /**
     * Resolves the id of the message, if the message matches exactly one template.
     *
     * @param message a validator message
     * @return the message id, if it can be resolved
     */
    @Nonnull
    public static Optional<String> getMessageId(@Nonnull final String message) {
        return INSTANCE.getMessageId(message).map(s -> s.replace("0", "_"));
    }
}
