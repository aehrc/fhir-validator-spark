package au.csiro.fhir.validation;

import lombok.AllArgsConstructor;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
public class MessageResolver {

    @Nonnull
    private final Pattern pattern;

    @Nonnull
    private final List<String> messages;


    @Nonnull
    public Optional<String> getMessageId(@Nonnull final String message) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return messages.stream()
                    .filter(s -> matcher.group(s) != null)
                    .findFirst();
        }
        return Optional.empty();
    }

    @Nonnull
    private static String messageToRegex(@Nonnull final String message) {
        // unescape '' and split message using regex matching {0} and {1} and {2} etc
        String[] parts = message.replace("''", "'").split("\\{\\d+\\}", -1);
        // now Pattern.quote() the parts and join them with the regex matching any character
        return Stream.of(parts).map(Pattern::quote).collect(Collectors.joining(".*"));
    }

    @Nonnull
    public static MessageResolver ofMessages(@Nonnull final Map<String, String> messages) {
        final String pattern = messages.entrySet().stream()
                .sorted(Comparator.comparingInt(kv -> -kv.getValue().length()))
                .map(kv -> String.format("(?<%s>^%s$)", kv.getKey(),
                        messageToRegex(kv.getValue())))
                .collect(Collectors.joining("|"));
        return new MessageResolver(Pattern.compile(pattern), new ArrayList<>(messages.keySet()));
    }
}


