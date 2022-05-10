package edu.gatech.chai.omoponfhir.omopv5.r4.utilities;

import org.hl7.fhir.r4.model.Resource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

public class StaticValues {
	public static final FhirContext myFhirContext = FhirContext.forR4();
	public static final long CONCEPT_MY_SPACE = 2000000000L;
	public static final String ACTIVE            = "ACTIVE";
	public static final String INACTIVE          = "INACTIVE";
	public static final String ERROR             = "ERROR";
	public static final String REQUEST           = "REQUEST";
	public static final String REQUEST_IN_ACTIVE = "REQUEST_IN_ACTIVE";

	public static final long TWO_WEEKS_IN_SEC = 1209600L;
	public static final long FOUR_WEEKS_IN_SEC = 2419200L;
	public static final long EIGHT_WEEKS_IN_SEC = 4838400L;

	public static final long ONE_DAY_IN_SEC = 86400L;
	public static final long SEVEN_DAYS_IN_SEC = 604800L;
	public static final long FOURTEEN_DAYS_IN_SEC = 1209600L;

	private StaticValues() {}

	public static String serializeIt (Resource resource) {
		IParser parser = StaticValues.myFhirContext.newJsonParser();
		return parser.encodeResourceToString(resource);
	}
}