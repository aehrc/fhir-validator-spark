package au.csiro.fhir;

import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.tests.TestConstants;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.ValidatorCli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class MainCli {
    public static void main(String[] args) throws Exception {
        ValidatorCli.main(new String[]{"data/Patient_*.json", "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz", "-tx", "n/a", "-version", "4.0"});
        ValidatorCli.main(new String[]{"data/Patient_*.json", "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz", "-tx", "n/a", "-version", "4.0"});
    }
}