package au.csiro.fhir.validation;

import au.csiro.fhir.validation.hapi.CollectorErrorHandler;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.JsonParser;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.parser.StrictErrorHandler;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.elementmodel.Manager;
import org.hl7.fhir.r4.formats.FormatUtilities;
import org.hl7.fhir.r4.formats.ParserBase;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class FhirParserTest {
    
    
    
    @Test
    void testParserJson() throws IOException {
        String json = "{ \"resourceType\": \"Patient\", \"id\": \"example\", \"active\": true, \"name\": {}, \"xxx\":\"yy\"  deer }";
//        ParserBase jsonParser = FormatUtilities.makeParser(Manager.FhirFormat.JSON);
//        Resource result = jsonParser.parse(json);
//        System.out.println(result);
//        System.out.println(jsonParser.composeString(result));

        final FhirContext ctx = FhirContext.forR5();
        final ValidationResult validationResult;
        ValidationResult validationResult1;
        try {
        final CollectorErrorHandler errorHandler = new CollectorErrorHandler();
        IBaseResource result2 = new JsonParser(ctx, errorHandler).parseResource(json);
        validationResult1 = errorHandler.toValidationResult();
        } catch (DataFormatException e) {
            validationResult1 = ValidationResult.fromException(e);
        }
        validationResult = validationResult1;
        System.out.println(validationResult);
    }
}
