package edu.gatech.chai.omoponfhir.omopv5.r4.utilities;

import org.hl7.fhir.r4.model.Resource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

public class StaticValues {
	public static final FhirContext myFhirContext = FhirContext.forR4();
	public static final long CONCEPT_MY_SPACE = 2000000000L;

	public static final String ACTIVE              = "ACTIVE";
	public static final String INACTIVE            = "INACTIVE";
	public static final String ERROR_IN_CLIENT     = "ERROR IN CLIENT";
	public static final String ERROR_IN_SERVER     = "ERROR IN SERVER";
	public static final String ERROR_UNKNOWN       = "ERROR - UNKNOWN";
	public static final String REQUEST             = "REQUEST";
	public static final String REQUEST_IN_ACTIVE   = "REQUEST IN ACTIVE";
	public static final String STOPPED_NO_RESPONSE = "STOPPED FOR NO RESPONSE";
	public static final String TIMED_OUT           = "TIMED OUT";

	public static final long TWO_WEEKS_IN_SEC = 1209600L;
	public static final long FOUR_WEEKS_IN_SEC = 2419200L;
	public static final long EIGHT_WEEKS_IN_SEC = 4838400L;

	public static final long ONE_DAY_IN_SEC = 86400L;
	public static final long SEVEN_DAYS_IN_SEC = 604800L;
	public static final long FOURTEEN_DAYS_IN_SEC = 1209600L;

	public static final Integer MAX_TRY = 5;
	
	private StaticValues() {}

	public static String serializeIt (Resource resource) {
		IParser parser = StaticValues.myFhirContext.newJsonParser();
		return parser.encodeResourceToString(resource);
	}
}