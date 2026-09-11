package au.csiro.fhir.validation;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.util.VersionUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the HAPI FHIR library versions this build resolves.
 * <p>
 * {@code org.hl7.fhir.validation} depends on {@code hapi-fhir-base} 6.4.1 while the
 * {@code hapi-fhir-structures-*} modules depend on the version this build pins. Maven's
 * nearest-wins rule prefers the shallower 6.4.1 unless {@code dependencyManagement} says
 * otherwise, and the shaded jar then ships a core library older than the structures
 * modules built against it. Nothing here fails at build time when that happens: the
 * breakage surfaces only in a consumer, as {@code NoSuchMethodError} on the methods HAPI
 * 8.x added, and only once that consumer talks to a FHIR server.
 * <p>
 * Both tests below therefore exist. The first states the invariant directly, so the
 * failure names the cause; the second calls the method the 6.4.1 regression actually
 * removed, so the guard holds even if the version numbers move on.
 *
 * @author John Grimes
 */
class HapiLibraryVersionTest {

    /**
     * The version this build pins the structures modules to, passed in by Surefire from
     * the {@code hapi.structuresVersion} POM property.
     */
    private static final String EXPECTED_VERSION = System.getProperty("hapi.structuresVersion");

    @Test
    void testBaseLibraryMatchesStructuresVersion() {

        assertNotNull(EXPECTED_VERSION,
                "hapi.structuresVersion was not passed to the test JVM; check the Surefire configuration");
        assertEquals(EXPECTED_VERSION, VersionUtil.getVersion(),
                "The resolved hapi-fhir-base version differs from the version the structures modules "
                        + "are pinned to. Add or restore the ca.uhn.hapi.fhir:hapi-fhir-base entry in "
                        + "dependencyManagement, otherwise Maven takes the older version that "
                        + "org.hl7.fhir.validation asks for.");
    }

    @Test
    void testMethodOutcomeCarriesResponseStatusCode() {

        // Added in HAPI FHIR 8.x and absent from 6.4.1. Pathling's terminology client calls
        // it on every request, so a downgraded hapi-fhir-base on this class path stops any
        // $lookup, $translate or $subsumes in the same JVM with NoSuchMethodError.
        final MethodOutcome outcome = new MethodOutcome();
        outcome.setResponseStatusCode(201);

        assertEquals(201, outcome.getResponseStatusCode());
    }

}
