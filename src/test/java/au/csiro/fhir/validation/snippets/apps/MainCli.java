package au.csiro.fhir.validation.snippets.apps;

import org.hl7.fhir.validation.ValidatorCli;

public class MainCli {
    public static void main(String[] args) throws Exception {
        ValidatorCli.main(new String[]{"data/Patient_*.json", "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz", "-tx", "n/a", "-version", "4.0"});
        ValidatorCli.main(new String[]{"data/Patient_*.json", "-ig", "fhir/packages/kindlab.fhir.mimic/package.tgz", "-tx", "n/a", "-version", "4.0"});
    }
}