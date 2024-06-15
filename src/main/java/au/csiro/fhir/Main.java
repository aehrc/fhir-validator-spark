package au.csiro.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r5.formats.FormatUtilities;
import org.hl7.fhir.r5.formats.ParserBase;
import org.hl7.fhir.r5.elementmodel.JsonParser;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.tests.TestConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.instance.InstanceValidator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException, URISyntaxException, EOperationOutcome {
        final ValidationEngine validationEngine = new ValidationEngine.ValidationEngineBuilder()
                .withCanRunWithoutTerminologyServer(true)
                .withVersion("4.0")
                .withUserAgent(TestConstants.USER_AGENT)
                .fromSource("hl7.fhir.r4.core#4.0.1");

        validationEngine.loadPackage("kindlab.fhir.mimic", "dev");

        OperationOutcome result = validationEngine.validate(Manager.FhirFormat.JSON, new FileInputStream("data/Patient_01.json"), List.of());
        ParserBase jsonParser = FormatUtilities.makeParser(Manager.FhirFormat.JSON);
        jsonParser.setOutputStyle(IParser.OutputStyle.PRETTY);
        jsonParser.setAllowUnknownContent(true);
        System.out.println(jsonParser.composeString(result));

        InstanceValidator validator = validationEngine.getValidator(Manager.FhirFormat.JSON);
        final List<ValidationMessage> errors = new ArrayList<>();
        Resource res = jsonParser.parseAndClose(new FileInputStream("data/Patient_01.json"));

        validator.validate(null, errors, res);
        System.out.println(errors);
    }
}