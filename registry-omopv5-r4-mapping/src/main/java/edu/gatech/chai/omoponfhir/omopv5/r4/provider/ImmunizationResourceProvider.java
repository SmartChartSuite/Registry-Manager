package edu.gatech.chai.omoponfhir.omopv5.r4.provider;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Sort;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import edu.gatech.chai.omoponfhir.omopv5.r4.mapping.OmopImmunization;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.StaticValues;
import edu.gatech.chai.omopv5.dba.service.ParameterWrapper;

public class ImmunizationResourceProvider implements IResourceProvider {
	private static final Logger logger = LoggerFactory.getLogger(ImmunizationResourceProvider.class);

	private WebApplicationContext myAppCtx;
	private OmopImmunization myMapper;
	private int preferredPageSize = 30;

	public ImmunizationResourceProvider() {
		myAppCtx = ContextLoader.getCurrentWebApplicationContext();
		myMapper = new OmopImmunization(myAppCtx);
	}
	
	public static String getType() {
		return "Immunization";
	}

    public OmopImmunization getMyMapper() {
    	return myMapper;
    }

	private Integer getTotalSize(List<ParameterWrapper> paramList) throws Exception {
		final Long totalSize;
		if (paramList.isEmpty()) {
			totalSize = getMyMapper().getSize();
		} else {
			totalSize = getMyMapper().getSize(paramList);
		}

		return totalSize.intValue();
	}

	private Integer getTotalSize(String queryString, List<String> parameterList, List<String> valueList) throws Exception {
		final Long totalSize = getMyMapper().getSize(queryString, parameterList, valueList);
			
		return totalSize.intValue();
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Immunization.class;
	}

	/**
	 * The "@Create" annotation indicates that this method implements "create=type", which adds a 
	 * new instance of a resource to the server.
	 * @throws Exception 
	 */
	@Create()
	public MethodOutcome createImmunization(@ResourceParam Immunization theImmunization) throws Exception {
		validateResource(theImmunization);
		
		Long id=null;
		try {
			id = myMapper.toDbase(theImmunization, null);
		} catch (FHIRException e) {
			e.printStackTrace();
		}
		
		if (id == null) {
			OperationOutcome outcome = new OperationOutcome();
			CodeableConcept detailCode = new CodeableConcept();
			detailCode.setText("Failed to create entity.");
			outcome.addIssue().setSeverity(IssueSeverity.FATAL).setDetails(detailCode);
			throw new UnprocessableEntityException(StaticValues.myFhirContext, outcome);
		}

		return new MethodOutcome(new IdDt(id));
	}

	@Delete()
	public void deleteMedicationRequest(@IdParam IdType theId) throws Exception {
		if (myMapper.removeByFhirId(theId) <= 0) {
			throw new ResourceNotFoundException(theId);
		}
	}


	@Update()
	public MethodOutcome updateUmmunization(@IdParam IdType theId, @ResourceParam Immunization theImmunization) throws Exception {
		validateResource(theImmunization);
		
		Long fhirId=null;
		try {
			fhirId = myMapper.toDbase(theImmunization, theId);
		} catch (FHIRException e) {
			e.printStackTrace();
		}

		if (fhirId == null) {
			throw new ResourceNotFoundException(theId);
		}

		return new MethodOutcome();
	}

	@Read()
	public Immunization readImmunization(@IdParam IdType theId) throws Exception {
		Immunization retval = getMyMapper().toFHIR(theId);
		if (retval == null) {
			throw new ResourceNotFoundException(theId);
		}
			
		return retval;
	}
	
	@Search()
	public IBundleProvider findImmunizationById(
			@RequiredParam(name = Immunization.SP_RES_ID) TokenParam theImmunizationId
			) throws Exception {

		List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();

		if (theImmunizationId != null) {
			paramList.addAll(myMapper.mapParameter(Immunization.SP_RES_ID, theImmunizationId, false));
		}

		MyBundleProvider myBundleProvider = new MyBundleProvider(paramList);
		myBundleProvider.setTotalSize(getTotalSize(paramList));
		myBundleProvider.setPreferredPageSize(preferredPageSize);
		return myBundleProvider;
	}

	@Search()
	public IBundleProvider findImmunizationsssByParams(
			@OptionalParam(name = Immunization.SP_VACCINE_CODE) TokenOrListParam theVaccineOrVCodes,
			@OptionalParam(name = Immunization.SP_DATE) DateRangeParam theDateRangeParam,
			@OptionalParam(name = Immunization.SP_PATIENT) ReferenceParam thePatient,
			@Sort SortSpec theSort
			) throws Exception {
		List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();

		if (theVaccineOrVCodes != null) {
			List<TokenParam> codes = theVaccineOrVCodes.getValuesAsQueryTokens();
			boolean orValue = true;
			if (codes.size() <= 1)
				orValue = false;
			for (TokenParam code : codes) {
				paramList.addAll(getMyMapper().mapParameter(Immunization.SP_VACCINE_CODE, code, orValue));
			}
		}
		
		if (thePatient != null) {
			String patientChain = thePatient.getChain();
			if (patientChain != null) {
				if (Patient.SP_NAME.equals(patientChain)) {
					String thePatientName = thePatient.getValue();
					paramList.addAll(getMyMapper().mapParameter ("Patient:"+Patient.SP_NAME, thePatientName, false));
				} else if (Patient.SP_IDENTIFIER.equals(patientChain)) {
					paramList.addAll(getMyMapper().mapParameter ("Patient:"+Patient.SP_IDENTIFIER, thePatient.getValue(), false));
				} else if ("".equals(patientChain)) {
					paramList.addAll(getMyMapper().mapParameter ("Patient:"+Patient.SP_RES_ID, thePatient.getValue(), false));
				}
			} else {
				paramList.addAll(getMyMapper().mapParameter ("Patient:"+Patient.SP_RES_ID, thePatient.getIdPart(), false));
			}

			// String newWhere = getMyMapper().mapParameter(Immunization.SP_PATIENT, thePatient, parameterList, valueList);
			// if (newWhere != null && !newWhere.isEmpty()) {
			// 	whereStatement = whereStatement.isEmpty() ? newWhere : whereStatement + " and " + newWhere;
			// }
		}

		if (theDateRangeParam != null) {
			paramList.addAll(getMyMapper().mapParameter(Immunization.SP_DATE, theDateRangeParam, false));


			// String newWhere = getMyMapper().mapParameter(Immunization.SP_DATE, theDateRangeParam, parameterList, valueList);
			// if (newWhere != null && !newWhere.isEmpty()) {
			// 	whereStatement = whereStatement.isEmpty() ? newWhere : whereStatement + " and " + newWhere; 
			// }
		}
		
		String orderParams = getMyMapper().constructOrderParams(theSort);

		MyBundleProvider myBundleProvider = new MyBundleProvider(paramList);
		myBundleProvider.setTotalSize(getTotalSize(paramList));
		myBundleProvider.setPreferredPageSize(preferredPageSize);
		myBundleProvider.setOrderParams(orderParams);
		
		return myBundleProvider;
	}
	
	private void validateResource(Immunization theMedication) {
		// TODO: implement validation method
	}

	class MyBundleProvider extends OmopFhirBundleProvider {

		public MyBundleProvider(List<ParameterWrapper> paramList) {
			super(paramList);
		}

		@Override
		public List<IBaseResource> getResources(int fromIndex, int toIndex) {
			List<IBaseResource> retv = new ArrayList<IBaseResource>();

			// _Include
			List<String> includes = new ArrayList<String>();

			try {
				if (paramList.isEmpty()) {
					myMapper.searchWithoutParams(fromIndex, toIndex, retv, includes, null);
				} else {
					myMapper.searchWithParams(fromIndex, toIndex, paramList, retv, includes, null);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			return retv;
		}
	}
}
