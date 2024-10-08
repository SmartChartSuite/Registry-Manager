/*******************************************************************************
 * Copyright (c) 2019 Georgia Tech Research Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package edu.gatech.chai.omoponfhir.omopv5.r4.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.MessageHeader.MessageHeaderResponseComponent;
import org.hl7.fhir.r4.model.MessageHeader.ResponseType;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.exceptions.FHIRException;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import edu.gatech.chai.omoponfhir.omopv5.r4.mapping.OmopServerOperations;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.CodeableConceptUtil;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.ConfigValues;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.QueryRequest;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.StaticValues;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.ThrowFHIRExceptions;
import edu.gatech.chai.omopv5.dba.service.CaseInfoService;
import edu.gatech.chai.omopv5.dba.service.ParameterWrapper;
import edu.gatech.chai.omopv5.model.entity.CaseInfo;
import edu.gatech.chai.omopv5.model.entity.FPerson;

public class ServerOperations {
	private static final Logger logger = LoggerFactory.getLogger(ServerOperations.class);
	private OmopServerOperations myMapper;
	private CaseInfoService caseInfoService;
	private ConfigValues configValues;
	private String rcApiHost;

	public ServerOperations() {
		WebApplicationContext myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();
		myMapper = new OmopServerOperations(myAppCtx);
		caseInfoService = myAppCtx.getBean(CaseInfoService.class);
		configValues = myAppCtx.getBean(ConfigValues.class);

		// rcApiHost = System.getenv("RCAPI_HOST");
		rcApiHost = configValues.getRcApiHostUrl();
		if (rcApiHost == null || rcApiHost.isEmpty()) {
			logger.error("RC API Host is not set. Please check application.properties.");
			rcApiHost = "Check Application.Properties. RC API URL is missing";
		}

		logger.debug("RC API HOST is " + rcApiHost);
	}

	@Operation(name = "$process-message")
	public Bundle processMessageOperation(
			@OperationParam(name = "content") Bundle theContent,
			@OperationParam(name = "async") BooleanType theAsync,
			@OperationParam(name = "response-url") UriType theUri) {
		Bundle retVal = new Bundle();
		MessageHeader messageHeader = null;
		// List<BundleEntryComponent> resources = new ArrayList<BundleEntryComponent>();
		List<BundleEntryComponent> entries = theContent.getEntry();

		if (theContent.getType() == BundleType.MESSAGE) {
			// Evaluate the first entry, which must be MessageHeader
			// BundleEntryComponent entry1 = theContent.getEntryFirstRep();
			// Resource resource = entry1.getResource();
			if (entries != null && !entries.isEmpty() &&
					entries.get(0).getResource() != null &&
					entries.get(0).getResource().getResourceType() == ResourceType.MessageHeader) {
				messageHeader = (MessageHeader) entries.get(0).getResource();
				// We handle observation-type.
				// TODO: Add other types later.
				Coding event = messageHeader.getEventCoding();
				// Coding obsprovided = new Coding("http://hl7.org/fhir/message-events",
				// "observation-provide", "Provide a simple observation or update a previously
				// provided simple observation.");
				Coding obsprovided = new Coding("http://terminology.hl7.org/CodeSystem/observation-category",
						"laboratory", "Laboratory");
				if (CodeableConceptUtil.compareCodings(event, obsprovided) != 0) {
					ThrowFHIRExceptions.unprocessableEntityException(
							"We currently support only observation-provided Message event");
				}
			}
		} else {
			ThrowFHIRExceptions.unprocessableEntityException(
					"The bundle must be a MESSAGE type");
		}
		MessageHeaderResponseComponent messageHeaderResponse = new MessageHeaderResponseComponent();
		messageHeaderResponse.setId(messageHeader.getId());

		List<BundleEntryComponent> resultEntries = null;
		try {
			resultEntries = myMapper.createEntries(entries);
			messageHeaderResponse.setCode(ResponseType.OK);
		} catch (Exception e) {
			e.printStackTrace();
			messageHeaderResponse.setCode(ResponseType.OK);
			OperationOutcome outcome = new OperationOutcome();
			CodeableConcept detailCode = new CodeableConcept();
			detailCode.setText(e.getMessage());
			outcome.addIssue().setSeverity(IssueSeverity.ERROR).setDetails(detailCode);
			messageHeaderResponse.setDetailsTarget(outcome);
		}

		messageHeader.setResponse(messageHeaderResponse);
		BundleEntryComponent responseMessageEntry = new BundleEntryComponent();
		UUID uuid = UUID.randomUUID();
		responseMessageEntry.setFullUrl("urn:uuid:" + uuid.toString());
		responseMessageEntry.setResource(messageHeader);

		if (resultEntries == null)
			resultEntries = new ArrayList<BundleEntryComponent>();

		resultEntries.add(0, responseMessageEntry);
		retVal.setEntry(resultEntries);

		return retVal;
	}

	@Operation(name = "$registry-control", manualResponse = true)
	public void registryControlOperation(RequestDetails theRequestDetails,
			@OperationParam(name = "case-id") StringParam theCaseId,
			@OperationParam(name = "special-action") StringParam theSpecialAction,
			@OperationParam(name = "patient-identifier") TokenParam thePatientIdentifier,
			@OperationParam(name = "set-status") StringParam theSetStatus,
			@OperationParam(name = "set-tries-left") NumberParam theTriesLeft,
			@OperationParam(name = "lab-results") Bundle theLabResults) throws Exception {

		Integer triesLeft = StaticValues.MAX_TRY;
		if (theTriesLeft != null) {
			triesLeft = theTriesLeft.getValue().intValue();
		}

		// Set parameterwrapper for the caseId if available
		List<ParameterWrapper> caseIdParamList = new ArrayList<ParameterWrapper>();
		ParameterWrapper caseIdParameterWrapper = new ParameterWrapper();
		if (theCaseId != null) {
			caseIdParameterWrapper.setParameterType("Integer");
			caseIdParameterWrapper.setParameters(Arrays.asList("id"));
			caseIdParameterWrapper.setOperators(Arrays.asList("="));
			caseIdParameterWrapper.setValues(Arrays.asList(theCaseId.getValue()));
			caseIdParameterWrapper.setRelationship("or");
			caseIdParamList.add(caseIdParameterWrapper);
		}

		List<ParameterWrapper> patientIdParamList = new ArrayList<ParameterWrapper>();
		ParameterWrapper patientIdParameterWrapper = new ParameterWrapper();
		String patientIdentifier = "";
		if ((thePatientIdentifier == null || thePatientIdentifier.isEmpty()) && theCaseId == null) {
			ThrowFHIRExceptions.unprocessableEntityException(
					"Either Patient Identifier or case Id is required to trigger the query");
		} else if (thePatientIdentifier != null && !thePatientIdentifier.isEmpty()) {
			patientIdentifier = thePatientIdentifier.getValue();
			patientIdParameterWrapper.setParameterType("String");
			patientIdParameterWrapper.setParameters(Arrays.asList("patientIdentifier"));
			patientIdParameterWrapper.setOperators(Arrays.asList("="));
			patientIdParameterWrapper.setValues(Arrays.asList(patientIdentifier));
			patientIdParameterWrapper.setRelationship("or");
			patientIdParamList.add(patientIdParameterWrapper);
		}

		Date currentTime = new Date();

		// get the value of set-status parameter.
		if (theSetStatus == null || theSetStatus.isEmpty()) {
			if (theCaseId != null) {
				List<CaseInfo> caseInfos = caseInfoService.searchWithParams(0, 0, caseIdParamList, "id ASC");
				for (CaseInfo caseInfo : caseInfos) {
					caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
					caseInfo.setTriggerAtDateTime(currentTime);
					caseInfo.setLastUpdatedDateTime(currentTime);
					caseInfo.setTriesLeft(triesLeft);
					caseInfoService.update(caseInfo);
				}
			} else {
				// This is a new REQUEST.
				if (theLabResults == null || theLabResults.isEmpty()) {
					ThrowFHIRExceptions.unprocessableEntityException(
							"Lab Results with a patient are required to create a new REQUEST");
				}

				// Sanity check. The lab results have a patient resource. The patient identifier
				// must match the
				// patient identifier in the parameter.
				boolean patientIdSystemOk = false;
				boolean patientIdValueOk = false;
				for (BundleEntryComponent entry : theLabResults.getEntry()) {
					Resource resource = entry.getResource();
					if (resource instanceof Patient) {
						for (Identifier identifier : ((Patient) resource).getIdentifier()) {
							String patientIdParamSystem = thePatientIdentifier.getSystem();
							String patientIdParamValue = thePatientIdentifier.getValue();

							String patientIdSystem = identifier.getSystem();
							String patientIdValue = identifier.getValue();

							if (patientIdParamSystem == null || patientIdParamSystem.isBlank()
									|| patientIdParamSystem.equalsIgnoreCase(patientIdSystem)) {
								patientIdSystemOk = true;
							}

							if (patientIdParamValue.equalsIgnoreCase(patientIdValue)) {
								patientIdValueOk = true;
							}

							if (patientIdSystemOk && patientIdValueOk) {
								break;
							}
						}

						if (patientIdSystemOk && patientIdValueOk) {
							break;
						}
					}
				}

				if (!patientIdSystemOk || !patientIdValueOk) {
					// Error the patient identifier in the parameter is not same as the patient
					// identifier
					// in the Patient resource.
					ThrowFHIRExceptions.unprocessableEntityException(
							"Parameters.patient-identifier must match with Parameters.lab-results.entry.resource.Patient.identifier");
				}

				// Even if this is a new request, we may already have a case for this patient.
				// Check if we have a case
				List<CaseInfo> caseInfos = caseInfoService.searchWithParams(0, 0, patientIdParamList, "id ASC");
				CaseInfo caseInfo = null;
				if (caseInfos != null && !caseInfos.isEmpty()) {
					caseInfo = caseInfos.get(0);
					caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
					caseInfo.setLastUpdatedDateTime(currentTime);
					caseInfo.setActivatedDateTime(currentTime);
					caseInfo.setTriesLeft(triesLeft);
					caseInfoService.update(caseInfo);

					if (caseInfos.size() > 1) {
						logger.warn("More than one case_info found. Duplicate cases must be removed");
					}
				}

				// We have a lab. Create these results in the
				// OMOP database.
				List<BundleEntryComponent> responseEntries = myMapper.createEntries(theLabResults.getEntry(), caseInfo);
				int errorFlag = 0;
				String errMessage = "";
				FPerson fPerson = null;
				for (BundleEntryComponent responseEntry : responseEntries) {
					Resource resource = responseEntry.getResource();
					if (resource instanceof Patient) {
						fPerson = new FPerson();
						System.out
								.println("NEW PATIENT IS:::::" + ((Patient) resource).getIdElement().getIdPartAsLong());
						fPerson.setId(((Patient) resource).getIdElement().getIdPartAsLong());
					}

					if (!responseEntry.getResponse().getStatus().startsWith("201")
							&& !responseEntry.getResponse().getStatus().startsWith("200")) {
						String jsonResource = StaticValues.serializeIt(resource);
						errMessage += "Failed to create/add " + jsonResource;
						logger.error(errMessage);
						errorFlag = 1;
					}
				}

				if (errorFlag == 1 || fPerson == null) {
					// Error occurred on one of resources.
					if (fPerson == null) {
						errMessage += " Patient resource is REQUIRED";
					}
					ThrowFHIRExceptions
							.unprocessableEntityException("Failed to create entiry resources: " + errMessage);
				}

				if (caseInfo == null) {
					caseInfo = new CaseInfo();
					caseInfo.setPatientIdentifier(patientIdentifier);
					caseInfo.setFPerson(fPerson);
					caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
					caseInfo.setServerHost(this.rcApiHost);
					caseInfo.setServerUrl("/forms/start?asyncFlag=true");
					caseInfo.setCreatedDateTime(currentTime);
					caseInfo.setLastUpdatedDateTime(currentTime);
					caseInfo.setActivatedDateTime(currentTime);
					caseInfo.setTriggerAtDateTime(currentTime);
					caseInfo.setTriesLeft(triesLeft);
					caseInfoService.create(caseInfo);
				}
			}
		} else {
			String newStatus = theSetStatus.getValue();

			List<ParameterWrapper> IdParamList = null;
			if (theCaseId != null) {
				IdParamList = caseIdParamList;
			} else if (thePatientIdentifier != null) {
				IdParamList = patientIdParamList;
			} else {
				ThrowFHIRExceptions.unprocessableEntityException(
						"Either case ID or patient identifer must be provided to refresh the case");
			}

			List<CaseInfo> caseInfos = caseInfoService.searchWithParams(0, 0, IdParamList, "id ASC");
			for (CaseInfo caseInfo : caseInfos) {
				if ("RESET_TO_RESTART".equals(newStatus)) {
					caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
					caseInfo.setActivatedDateTime(currentTime);
				} else {
					caseInfo.setStatus(newStatus);
				}
				caseInfo.setTriggerAtDateTime(currentTime);
				caseInfo.setTriesLeft(triesLeft);
				caseInfoService.update(caseInfo);
			}
		}
	}

	@Operation(name = "$registry-test", manualResponse = true)
	public Bundle rcApiResponseTest(RequestDetails theRequestDetails,
			@OperationParam(name = "name") StringParam theName,
			@OperationParam(name = "caseId") NumberParam theCaseId,
			@OperationParam(name = "resource") Bundle theResource) throws Exception {

		CaseInfo myCase = caseInfoService.findById(theCaseId.getValue().longValue());
		if (myCase == null) {
			throw new FHIRException("case, " + theCaseId.getValue().longValue() + ", not found");
		}

		if ("rc-api".equalsIgnoreCase(theName.getValue())) {
			if (theResource != null && !theResource.isEmpty()) {
				List<BundleEntryComponent> entries = theResource.getEntry();

				myMapper.createEntries(entries, myCase);
			}
		}

		return null;
	}
}
