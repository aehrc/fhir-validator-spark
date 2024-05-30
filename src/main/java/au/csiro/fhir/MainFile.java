package au.csiro.fhir;

import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.formats.FormatUtilities;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.formats.ParserBase;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.hl7.fhir.utilities.tests.TestConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.cli.utils.ValidationLevel;
import org.hl7.fhir.validation.instance.InstanceValidator;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainFile {
    static OperationOutcome validate(ValidationEngine validationEngine, String data){
        try {
            return validationEngine.validate(Manager.FhirFormat.JSON, new ByteArrayInputStream(data.getBytes()), List.of());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (EOperationOutcome e) {
            throw new RuntimeException(e);
        }
    }

    static  String toJson(OperationOutcome outcome, IParser jsonParser ){
        try {
            return jsonParser.composeString(outcome);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException, URISyntaxException, EOperationOutcome {
        final ValidationEngine validationEngine = new ValidationEngine.ValidationEngineBuilder()
                .withCanRunWithoutTerminologyServer(true)
                .withVersion("4.0")
                .withUserAgent(TestConstants.USER_AGENT)
                .fromSource("hl7.fhir.r4.core#4.0.1")
                .setBestPracticeLevel(BestPracticeWarningLevel.Error)
                .setLevel(ValidationLevel.ERRORS);
        validationEngine.loadPackage("kindlab.fhir.mimic", "dev");

        final String fileName = "../mimic/physionet.org/files/mimic-iv-fhir-demo/2.0/mimic-fhir/Patient.ndjson";

        final IParser jsonParser = FormatUtilities.makeParser(Manager.FhirFormat.JSON).setOutputStyle(IParser.OutputStyle.PRETTY);

        //read file into stream, try-with-resources
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.map(l -> l.replace("http://fhir.mimic.mit.edu/StructureDefinition/", "http://mimic.mit.edu/fhir/mimic/StructureDefinition/"))
                    .map(line -> validate(validationEngine, line)).map(oo -> toJson(oo,jsonParser)).forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
//
//
//        OperationOutcome result = validationEngine.validate(Manager.FhirFormat.JSON, new FileInputStream("data/Patient_mimic.json"), List.of());
//        ParserBase jsonParser = FormatUtilities.makeParser(Manager.FhirFormat.JSON);
//        jsonParser.setOutputStyle(IParser.OutputStyle.PRETTY);
//        jsonParser.setAllowUnknownContent(true);
//        System.out.println(jsonParser.composeString(result));
//
//        InstanceValidator validator = validationEngine.getValidator(Manager.FhirFormat.JSON);
//        final List<ValidationMessage> errors = new ArrayList<>();
//        Resource res = jsonParser.parseAndClose(new FileInputStream("data/Patient_01.json"));
//
//        validator.validate(null, errors, res);
//        System.out.println(errors);

    }
}