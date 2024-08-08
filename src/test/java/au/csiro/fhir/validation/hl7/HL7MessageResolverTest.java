package au.csiro.fhir.validation.hl7;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HL7MessageResolverTest {

    // NOTICE: Watch out for trailing whitespaces in the messages!
    private static Stream<Arguments> provideMessagesWithIds() {
        return Stream.of(
                Arguments.of("URL value 'http://mimic.mit.edu/fhir/mimic/identifier/encounter-hosp' does not resolve", "TYPE_SPECIFIC_CHECKS_DT_URL_RESOLVE"),
                Arguments.of("Best Practice Recommendation: In general, all observations should have a performer", "ALL_OBSERVATIONS_SHOULD_HAVE_A_PERFORMER"),
                Arguments.of("value should not start or finish with whitespace '2 '", "TYPE_SPECIFIC_CHECKS_DT_STRING_WS"),
                Arguments.of("The value '0.005008015591556614' is outside the range of commonly/reasonably supported decimals", "TYPE_SPECIFIC_CHECKS_DT_DECIMAL_RANGE"),
                Arguments.of("Wrong Display Name 'Iatrogenic cerebrovascular infarction or hemorrhage' for http://mimic.mit.edu/fhir/mimic/CodeSystem/mimic-diagnosis-icd9#99702." +
                        " Valid display is 'POST OP CVA' (en) (for the language(s) 'en')", "DISPLAY_NAME_FOR__SHOULD_BE_ONE_OF__INSTEAD_OF_ONE")
        );
    }


    @ParameterizedTest
    @MethodSource("provideMessagesWithIds")
    void testResolveMessage(String message, String id) {
        // Example usage
        String resolvedId = HL7MessageResolver.getMessageId(message).orElseThrow();
        assertEquals(id, resolvedId);
    }

    private static Stream<Arguments> provideAllHL7TMessages() {
        return HL7MessageResolver.loadMessages().entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .map(e -> Arguments.of(e.getValue().replace("''", "'"), e.getKey()));
    }


    @ParameterizedTest
    @MethodSource("provideAllHL7TMessages")
    void testResolveMessageTemplates(String message, String id) {
        // Example usage
        String resolvedId = HL7MessageResolver.getMessageId(message).orElseThrow();
        assertEquals(id.replace("0", "_"), resolvedId);
    }

}
