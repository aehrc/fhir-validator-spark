package au.csiro.fhir.validation.spark;

import au.csiro.fhir.validation.hl7.HL7ValidationConfig;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

/**
 * Stub.
 *
 * @author John Grimes
 */
public final class ValidationApi {

    public static final String VALUE_COLUMN = "value";
    public static final String FILENAME_COLUMN = "filename";

    private ValidationApi() {
    }

    public static Dataset<Row> validate(final Dataset<Row> ndjson, final HL7ValidationConfig config) {
        throw new UnsupportedOperationException("not implemented");
    }
}
