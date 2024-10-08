package edu.gatech.chai.omoponfhir.local.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownHttpStatusCodeException;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVParser;

import edu.gatech.chai.omoponfhir.omopv5.r4.mapping.OmopServerOperations;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.CodeableConceptUtil;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.ConfigValues;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.QueryRequest;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.StaticValues;
import edu.gatech.chai.omopv5.dba.service.ConceptRelationshipService;
import edu.gatech.chai.omopv5.dba.service.ConceptService;
import edu.gatech.chai.omopv5.dba.service.ParameterWrapper;
import edu.gatech.chai.omopv5.dba.service.RelationshipService;
import edu.gatech.chai.omopv5.dba.service.CaseLogService;
import edu.gatech.chai.omopv5.dba.service.CaseInfoService;
import edu.gatech.chai.omopv5.dba.service.VocabularyService;
import edu.gatech.chai.omopv5.model.entity.Concept;
import edu.gatech.chai.omopv5.model.entity.ConceptRelationship;
import edu.gatech.chai.omopv5.model.entity.Relationship;
import edu.gatech.chai.omopv5.model.entity.CaseInfo;
import edu.gatech.chai.omopv5.model.entity.CaseLog;
import edu.gatech.chai.omopv5.model.entity.Vocabulary;

@Component
public class ScheduledTask {
	private static final Logger logger = LoggerFactory.getLogger(ScheduledTask.class);
	ObjectMapper mapper = new ObjectMapper();

	private OmopServerOperations myMapper;

	@Autowired
	private ConfigValues configValues;
	@Autowired
	private ConceptService conceptService;
	@Autowired
	private ConceptRelationshipService conceptRelationshipService;
	@Autowired
	private CaseInfoService caseInfoService;
	@Autowired
	private CaseLogService caseLogService;
	@Autowired
	private RelationshipService relationshipService;
	@Autowired
	private VocabularyService vocabularyService;

	@Value("${rcapi.numoutstandingreq}")
	private int numOfOutstandingRequests;

	private Long conceptIdStart;

	private Long thresholdDuration1;
	private Long thresholdDuration2;
	private Long thresholdDuration3;

	private Long queryPeriod1;
	private Long queryPeriod2;
	private Long queryPeriod3;

	public ScheduledTask() {
		conceptIdStart = StaticValues.CONCEPT_MY_SPACE;
		// setSmartPacerBasicAuth(System.getenv("RCAPI_BASIC_AUTH"));

		// We are using the server operations implementation.
		WebApplicationContext myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();

		// configValues = myAppCtx.getBean(ConfigValues.class);

		myMapper = new OmopServerOperations(myAppCtx);

		// Get PACER query logic variables.
		thresholdDuration1 = System.getenv("thresholdDuration1") == null ? StaticValues.TWO_WEEKS_IN_SEC
				: Long.getLong(System.getenv("thresholdDuration1"));
		thresholdDuration2 = System.getenv("thresholdDuration2") == null ? StaticValues.FOUR_WEEKS_IN_SEC
				: Long.getLong(System.getenv("thresholdDuration2"));
		thresholdDuration3 = System.getenv("thresholdDuration3") == null ? StaticValues.EIGHT_WEEKS_IN_SEC
				: Long.getLong(System.getenv("thresholdDuration3"));

		thresholdDuration1 *= 1000L;
		thresholdDuration2 *= 1000L;
		thresholdDuration3 *= 1000L;

		queryPeriod1 = System.getenv("queryPeriod1") == null ? StaticValues.ONE_DAY_IN_SEC
				: Long.getLong(System.getenv("queryPeriod1"));
		queryPeriod2 = System.getenv("queryPeriod2") == null ? StaticValues.SEVEN_DAYS_IN_SEC
				: Long.getLong(System.getenv("queryPeriod2"));
		queryPeriod3 = System.getenv("queryPeriod3") == null ? StaticValues.FOURTEEN_DAYS_IN_SEC
				: Long.getLong(System.getenv("queryPeriod3"));

		queryPeriod1 *= 1000L;
		queryPeriod2 *= 1000L;
		queryPeriod3 *= 1000L;
	}

	@PostConstruct
	private void debugValueDisplay() {
		logger.debug("RC API Basic Auth is " + configValues.getRcApiBasicAuth());
	}

	// public String getSmartPacerBasicAuth() {
	// return this.smartPacerBasicAuth;
	// }

	// public void setSmartPacerBasicAuth(String smartPacerBasicAuth) {
	// if (smartPacerBasicAuth != null && !smartPacerBasicAuth.isEmpty()) {
	// this.smartPacerBasicAuth = smartPacerBasicAuth;
	// }
	// }

	protected void writeToLog(CaseInfo caseInfo, String message) throws Exception {
		CaseLog caseLog = new CaseLog();

		caseLog.setCaseInfo(caseInfo);
		caseLog.setText(message);
		caseLog.setLogDateTime(new Date());
		caseLogService.create(caseLog);
	}

	protected String getEndPoint(String serverHost, String apiPoint) {
		String endPoint;

		if ((serverHost.endsWith("/") && apiPoint.startsWith("/"))
				|| (!serverHost.endsWith("/") && !apiPoint.startsWith("/"))) {
			endPoint = serverHost + "/" + apiPoint.substring(1);
		} else if ((serverHost.endsWith("/") && !apiPoint.startsWith("/"))
				|| (!serverHost.endsWith("/") && apiPoint.startsWith("/"))) {
			endPoint = serverHost + apiPoint;
		} else {
			endPoint = serverHost + apiPoint;
		}

		return endPoint;
	}

	protected Bundle retrieveQueryResult(CaseInfo caseInfo) throws Exception {
		RestTemplate restTemplate = new RestTemplate();
		IParser parser = StaticValues.myFhirContext.newJsonParser();

		String serverHost = caseInfo.getServerHost();
		ResponseEntity<String> response = null;
		Bundle resultBundle = null;

		// call status URL to get FHIR syphilis registry data.
		String statusUrl = caseInfo.getStatusUrl();
		HttpEntity<String> reqAuth = new HttpEntity<String>(createHeaders());

		Date currentTime = new Date();

		try {
			String statusEndPoint;
			if (statusUrl.startsWith("http")) {
				statusEndPoint = statusUrl;
			} else {
				statusEndPoint = getEndPoint(serverHost, statusUrl);
			}

			// Get the status
			response = restTemplate.exchange(statusEndPoint, HttpMethod.GET, reqAuth, String.class);
		} catch (HttpClientErrorException e) {
			String rBody = e.getResponseBodyAsString();
			OperationOutcome oo = parser.parseResource(OperationOutcome.class, rBody);

			Boolean isJobIdValid = true;
			if (oo != null && !oo.isEmpty()) {
				for (OperationOutcomeIssueComponent issue : oo.getIssue()) {
					IssueSeverity severity = issue.getSeverity();
					IssueType issueCode = issue.getCode();
					if (IssueSeverity.ERROR == severity && IssueType.CODEINVALID == issueCode) {
						isJobIdValid = false;
						break;
					}
				}
			}

			if ((404 == e.getStatusCode().value() && !isJobIdValid)
					|| rBody.contains("jobPackage again with a new job id")) {
				// job ID may be timed out. Make a new request.
				caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
			} else {
				caseInfo.setStatus(QueryRequest.ERROR_IN_CLIENT.getCodeString());
			}
			writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") STATUS GET FAILED: " + e.getStatusCode() + "\n"
					+ rBody + "\n Next State (" + caseInfo.getStatus() + ")");

			retryCountUpdate(caseInfo);
			caseInfoService.update(caseInfo);
			return null;
		} catch (HttpServerErrorException e) {
			String rBody = e.getResponseBodyAsString();
			caseInfo.setStatus(QueryRequest.ERROR_IN_SERVER.getCodeString());
			caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

			writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") SERVER ERROR: " + e.getStatusCode() + "\n"
					+ rBody + "\n Next State (" + caseInfo.getStatus() + ")");
			retryCountUpdate(caseInfo);

			caseInfoService.update(caseInfo);

			return null;
			// We do not change the status as this is a server error.
		} catch (UnknownHttpStatusCodeException e) {
			String rBody = e.getResponseBodyAsString();
			caseInfo.setStatus(QueryRequest.ERROR_UNKNOWN.getCodeString());
			writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") STATUS GET FAILED with Unknown code\n" + rBody
					+ "\n Next State (" + caseInfo.getStatus() + ")");
			caseInfoService.update(caseInfo);
			return null;
		}

		HttpStatusCode statusCode = response.getStatusCode();
		if (!statusCode.is2xxSuccessful()) {
			if (statusCode.is4xxClientError()) {
				if (404 == statusCode.value()) {
					// job ID may be timed out. Make a new request.
					caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
				} else {
					caseInfo.setStatus(QueryRequest.ERROR_IN_CLIENT.getCodeString());
				}
			} else if (statusCode.is5xxServerError()) {
				caseInfo.setStatus(QueryRequest.ERROR_IN_SERVER.getCodeString());
				caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

				retryCountUpdate(caseInfo);
			} else {
				caseInfo.setStatus(QueryRequest.ERROR_UNKNOWN.getCodeString());
			}

			logger.debug("Status Query Failed and Responded with statusCode:" + statusCode.toString());
			OperationOutcome oo = parser.parseResource(OperationOutcome.class, response.getBody());
			if (oo != null && !oo.isEmpty()) {
				String errorBody = "";
				for (OperationOutcomeIssueComponent issue : oo.getIssue()) {
					errorBody += issue.getCode() + ", ";
				}
				writeToLog(caseInfo, "Status Query Failed and Responded with issue(s):" + errorBody + "\n Next State ("
						+ caseInfo.getStatus() + ")");
			} else {
				writeToLog(caseInfo, "Status Query Failed and Responded with statusCode:" + statusCode.toString()
						+ "\n Next State (" + caseInfo.getStatus() + ")");
			}
		} else {
			// Get response body
			String responseBody = response.getBody();

			Parameters parameters = null;
			if (responseBody != null && !responseBody.isEmpty()) {
				try {
					parameters = parser.parseResource(Parameters.class, responseBody);
				} catch (DataFormatException e) {
					caseInfo.setStatus(QueryRequest.RESULT_PARSE_ERROR.getCodeString());
					writeToLog(caseInfo,
							"case info (" + caseInfo.getId() + ") Response Parameter Parse Error\n Next State ("
									+ caseInfo.getStatus() + "). " + e.getMessage());
					caseInfoService.update(caseInfo);
					return null;
				}
				
				if (parameters == null || parameters.isEmpty()) {
					caseInfo.setStatus(QueryRequest.RESULT_PARSE_ERROR.getCodeString());

					if (responseBody.length() > 65535) {
						responseBody = responseBody.substring(0, 65535);
					}

					writeToLog(caseInfo, "Failed to parse the Parameters. Indepth Evaluation on response is needed: " + responseBody);
					return null;
				}

				// StringType jobStatus = (StringType) parameters.getParameter("jobStatus");
				ParametersParameterComponent parameter = parameters.getParameter("jobStatus");
				StringType jobStatus = null;
				if (parameter == null || parameter.isEmpty()) {
					caseInfo.setStatus(QueryRequest.ERROR_IN_SERVER.getCodeString());
					caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

					retryCountUpdate(caseInfo);
					caseInfoService.update(caseInfo);
					return null;
				}

				jobStatus = (StringType) parameter.getValue();
				if (jobStatus == null || jobStatus.isEmpty()) {
					logger.debug("RC-API jobStatus is null or empty ...");
					writeToLog(caseInfo, "RC-API jobStatus is null or empty ...");
					caseInfo.setStatus(QueryRequest.ERROR_IN_SERVER.getCodeString());
					caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

					retryCountUpdate(caseInfo);
					caseInfoService.update(caseInfo);
					return null;
				}

				if (jobStatus != null && !jobStatus.isEmpty()
						&& "inProgress".equalsIgnoreCase(jobStatus.asStringValue())) {
					long expirationTime = caseInfo.getCaseStartedRunningDateTime().getTime() + 1800000L;
					if (expirationTime <= currentTime.getTime()) {
						caseInfo.setStatus(QueryRequest.PAUSED.getCodeString());
						logger.debug("RC-API jobStatus: " + jobStatus.asStringValue() + " Will pause. Took too long ... Nest State: " + caseInfo.getStatus());
						writeToLog(caseInfo, "RC-API jobStatus: " + jobStatus.asStringValue() + " Will pause. Took too lone ... Nest State: " + caseInfo.getStatus());
						caseInfoService.update(caseInfo);
					} else {
						logger.debug("RC-API jobStatus: " + jobStatus.asStringValue() + " Will try again ... Nest State: " + caseInfo.getStatus());
						writeToLog(caseInfo, "RC-API jobStatus: " + jobStatus.asStringValue() + " Will try again ... Nest State: " + caseInfo.getStatus());
						// caseInfo.setStatus(QueryRequest.RUNNING.getCodeString());
						// caseInfoService.update(caseInfo);
					}
					return null;
				}

				if (jobStatus != null && !jobStatus.isEmpty()
						&& !"inProgress".equalsIgnoreCase(jobStatus.asStringValue())
						&& !"complete".equalsIgnoreCase(jobStatus.asStringValue())) {
					logger.debug("RC-API jobStatus: " + jobStatus.asStringValue() + " is not recognized ... ");
					writeToLog(caseInfo, "RC-API jobStatus: " + jobStatus.asStringValue() + " is not recognized ... ");
					caseInfo.setStatus(QueryRequest.ERROR_IN_SERVER.getCodeString());
					caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

					retryCountUpdate(caseInfo);
					caseInfoService.update(caseInfo);
					return null;
				}

				List<ParametersParameterComponent> parameterComponents = parameters.getParameter();
				for (ParametersParameterComponent parameterComponent : parameterComponents) {
					if ("result".equals(parameterComponent.getName())) {
						resultBundle = (Bundle) parameterComponent.getResource();
						break;
					}
				}
			}
		}

		return resultBundle;
	}

	protected void createEntries(Bundle resultBundle, CaseInfo caseInfo) throws Exception {
		Date currentTime = new Date();

		List<BundleEntryComponent> entries = resultBundle.getEntry();
		List<BundleEntryComponent> responseEntries = null;
		try {
			responseEntries = myMapper.createEntries(entries, caseInfo);
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);

			logger.error(
					"Error occured while creating resources in the Output FHIR Bundle entries. \n" + sw.toString());
			writeToLog(caseInfo,
					"Error occured while creating resources in the Output FHIR Bundle entries.\n" + sw.toString());

			if (e instanceof SQLTimeoutException) {
				Long next = (2 + (int) Math.random()*10) * 3600000L;
				caseInfo.setTriggerAtDateTime(new Date(currentTime.getTime()+next));
				caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
			} else {
				caseInfo.setStatus(QueryRequest.ERROR_IN_CLIENT.getCodeString());
			}

			caseInfoService.update(caseInfo);
			return;
		}

		int errorFlag = 0;
		String errMessage = "";
		for (BundleEntryComponent responseEntry : responseEntries) {
			if (!responseEntry.getResponse().getStatus().startsWith("201")
					&& !responseEntry.getResponse().getStatus().startsWith("200")) {
				String jsonResource = StaticValues.serializeIt(responseEntry.getResource());
				errMessage += "Failed to create/add " + jsonResource;
				errorFlag = 1;
			}
		}

		if (errorFlag == 1) {
			// Error occurred on one of resources.
			logger.error(errMessage);
			writeToLog(caseInfo, errMessage);
			caseInfo.setStatus(QueryRequest.ERROR_UNKNOWN.getCodeString());
		} else {
			// case query was successful.
			caseInfo.setLastSuccessfulDateTime(currentTime);

			// We will request for query again if trigger algorithm allows
			caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());

			logger.debug("TRIGGER: current_time=" + currentTime.getTime() + ", activated_time = "
					+ caseInfo.getActivatedDateTime().getTime() + ", thresholdDuration1 = " + thresholdDuration1
					+ ", threshold_at = "
					+ (new Date(caseInfo.getActivatedDateTime().getTime() + thresholdDuration1)).getTime()
					+ ", trigger_at=" + (new Date(currentTime.getTime() + queryPeriod1).getTime()));
			if (currentTime.before(new Date(caseInfo.getActivatedDateTime().getTime() + thresholdDuration1))) {
				caseInfo.setTriggerAtDateTime(new Date(currentTime.getTime() + queryPeriod1));
			} else if (currentTime.before(new Date(caseInfo.getActivatedDateTime().getTime() + thresholdDuration2))) {
				caseInfo.setTriggerAtDateTime(new Date(currentTime.getTime() + queryPeriod2));
			} else if (currentTime.before(new Date(caseInfo.getActivatedDateTime().getTime() + thresholdDuration3))) {
				caseInfo.setTriggerAtDateTime(new Date(currentTime.getTime() + queryPeriod3));
			} else {
				caseInfo.setStatus(QueryRequest.END.getCodeString());
				writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") changed status to " + caseInfo.getStatus());
			}

			if (QueryRequest.END.getCodeString().equals(caseInfo.getStatus())) {
				writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") query successful. And case becomes "
						+ QueryRequest.END.getCodeString());
			} else {
				writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") query successful. Next trigger at "
						+ caseInfo.getTriggerAtDateTime().toString());
			}

			caseInfo.setTriesLeft(StaticValues.MAX_TRY);

			// Query was successful and the entries are written to data bases.
			caseInfoService.runAlgorithms(caseInfo);
		}

		caseInfoService.update(caseInfo);
	}

	/**
	 * 
	 * @param caseInfo
	 *                 - call this function at the end of process and be careful not
	 *                 to override other conditions that will
	 *                 change the state status.
	 * @return
	 * @throws Exception 
	 */
	protected Integer retryCountUpdate(CaseInfo caseInfo) throws Exception {
		Integer retrytLeft = caseInfo.getTriesLeft();

		// decrement the counter
		int next_retryLeft = retrytLeft - 1;
		caseInfo.setTriesLeft(next_retryLeft);

		if (retrytLeft <= 1) {
			writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") Request Timed Out");
			caseInfo.setStatus(QueryRequest.TIMED_OUT.getCodeString());
		}

		return next_retryLeft;
	}

	protected void requestForQuery(CaseInfo caseInfo) throws Exception {
		Date currentTime = new Date();

		RestTemplate restTemplate = new RestTemplate();
		IParser parser = StaticValues.myFhirContext.newJsonParser();

		String serverHost = caseInfo.getServerHost();
		ResponseEntity<String> response = null;

		// Send a request. This is triggered by a new ELR or NoSuchRequest from PACER
		// server
		String patientIdentifier = caseInfo.getPatientIdentifier();
		if (patientIdentifier != null) {
			// Create Parameters for the REQUEST.
			Parameters parameters = new Parameters()
					.addParameter(
							new ParametersParameterComponent(new StringType("patientIdentifier"))
									.setValue(new StringType(patientIdentifier)))
					.addParameter(
							new ParametersParameterComponent(new StringType("jobPackage"))
									.setValue(new StringType(configValues.getJobPackage())));

			String parameterJson = parser.encodeResourceToString(parameters);

			JsonNode requestJson = null;
			response = null;
			try {
				requestJson = mapper.readTree(parameterJson);
				HttpHeaders headers = createHeaders();
				headers.setContentType(MediaType.APPLICATION_JSON);
				HttpEntity<JsonNode> entity = new HttpEntity<JsonNode>(requestJson, headers);

				String serverUrl = caseInfo.getServerUrl();
				String serverEndPoint;
				if (serverUrl.startsWith("http")) {
					serverEndPoint = serverUrl;
				} else {
					if (serverHost == null || serverHost.isEmpty()) {
						writeToLog(caseInfo, "server endpoint error: " + serverHost + serverUrl);
						logger.error("server endpoint error: " + serverHost + serverUrl);
						caseInfo.setStatus(QueryRequest.ERROR_UNKNOWN.getCodeString());
						caseInfoService.update(caseInfo);
						return;
					}

					serverEndPoint = getEndPoint(serverHost, serverUrl);
				}
				response = restTemplate.postForEntity(serverEndPoint, entity, String.class);

			} catch (Exception e) {
				e.printStackTrace();
				caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
				caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

				retryCountUpdate(caseInfo);
				writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") REQUEST FAILED: " + e.getMessage()
						+ "\n Next State(" + caseInfo.getStatus() + ")");
				caseInfoService.update(caseInfo);
				return;
			}

			if (response.getStatusCode().equals(HttpStatus.CREATED) || response.getStatusCode().equals(HttpStatus.OK)) {
				// Get Location
				StringType jobId = null;
				HttpHeaders responseHeaders = response.getHeaders();
				URI statusUri = responseHeaders.getLocation();
				String responseBody = response.getBody();

				if (responseBody != null && !responseBody.isEmpty()) {
					logger.debug("response body for REQUEST status: " + responseBody);
					Parameters returnedParameters = parser.parseResource(Parameters.class, responseBody);
					// jobId = (StringType) returnedParameters.getParameter("jobId");
					ParametersParameterComponent parameter = returnedParameters.getParameter("jobId");
					if (parameter == null || parameter.isEmpty()) {
						caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
						caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

						retryCountUpdate(caseInfo);
						writeToLog(caseInfo,
								"case info (" + caseInfo.getId()
										+ ") failed to get jobId. jobId parameter is null. \n Next State ("
										+ caseInfo.getStatus() + ")");
						caseInfoService.update(caseInfo);
						return;
					}

					jobId = (StringType) parameter.getValue();
				}

				if (jobId == null || jobId.isEmpty()) {
					// We failed to get a JobID.
					caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
					caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

					writeToLog(caseInfo, "case info (" + caseInfo.getId()
							+ ") failed to get jobId (null). \n Next State (" + caseInfo.getStatus() + ")");
					retryCountUpdate(caseInfo);
				} else {
					if (statusUri != null) {
						/////////////////////////////////////////////////////
						// Done. We got all we need. Now we are running this case. Set it to RUNNING
						//
						caseInfo.setStatusUrl(statusUri.toString());
						caseInfo.setJobId(jobId.asStringValue());
						caseInfo.setCaseStartedRunningDateTime(currentTime);
						caseInfo.setStatus(QueryRequest.RUNNING.getCodeString());

						// set the triggered_at. Since this is a REQUEST, we set it to now.
						caseInfo.setTriggerAtDateTime(currentTime);

						// log this session
						writeToLog(caseInfo, "caes info (" + caseInfo.getId() + ") is updated to "
								+ QueryRequest.RUNNING.getCodeString());
					} else {
						caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
						caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));

						writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") failed to get status URL.");
						retryCountUpdate(caseInfo);
					}
				}
			} else {
				// non 2xx received. Check the OperationOutcome.
				String errorBody = response.getBody();
				if (errorBody != null && !errorBody.isEmpty()) {
					OperationOutcome oo = parser.parseResource(OperationOutcome.class, errorBody);
					String issueDesc = "";
					for (OperationOutcomeIssueComponent issue : oo.getIssue()) {
						issueDesc += issue.getCode().toString() + ", ";
					}
					writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") error response (" + issueDesc + ")");
				} else {
					writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") error response ("
							+ response.getStatusCode().toString() + ")");
				}

				if (response.getStatusCode().is4xxClientError()) {
					caseInfo.setStatus(QueryRequest.ERROR_IN_CLIENT.getCodeString());
				} else if (response.getStatusCode().is5xxServerError()) {
					caseInfo.setStatus(QueryRequest.REQUEST_PENDING.getCodeString());
					caseInfo.setTriggerAtDateTime(org.apache.commons.lang3.time.DateUtils.addDays(currentTime, 1));
				} else {
					caseInfo.setStatus(QueryRequest.ERROR_UNKNOWN.getCodeString());
				}
				
				retryCountUpdate(caseInfo);
			}
		} else {
			// This cannot happen as patient identifier is a required field.
			// BUt, if this ever happens, we write this in session log and return to error
			// in client, which will
			// stop querying.
			caseInfo.setStatus(QueryRequest.ERROR_IN_CLIENT.getCodeString());
			writeToLog(caseInfo, "case info (" + caseInfo.getId() + ") without patient identifier. \n Next State ("
					+ caseInfo.getStatus() + ")");
		}

		caseInfoService.update(caseInfo);
	}

	/**
	 * Query State Machine that maintains a session for each case.
	 */
	@Scheduled(initialDelay = 30000, fixedDelay = 60000)
	public void runPeriodicQuery() {
		Date currentTime = new Date();

		Long currentTimeEpoch = currentTime.getTime();
		List<ParameterWrapper> params = new ArrayList<ParameterWrapper>();

		// Add "triggerAt parameter"
		if (numOfOutstandingRequests <= 0) {
			numOfOutstandingRequests = 3;
		}

		ParameterWrapper paramDate = new ParameterWrapper("Date", Arrays.asList("triggerAtDateTime"),
				Arrays.asList("<="), Arrays.asList(String.valueOf(currentTimeEpoch)), "and");
		params.add(paramDate);			
		ParameterWrapper param = new ParameterWrapper("String", 
			Arrays.asList("status", "status", "status", "status", "status", "status", "status"),
			Arrays.asList("!=", "!=", "!=", "!=", "!=", "!=", "!="), 
			Arrays.asList(
				QueryRequest.END.getCodeString(),
				QueryRequest.TIMED_OUT.getCodeString(),
				QueryRequest.PAUSED.getCodeString(),
				QueryRequest.ERROR_IN_CLIENT.getCodeString(), 
				QueryRequest.RESULT_PARSE_ERROR.getCodeString(),
				QueryRequest.ERROR_UNKNOWN.getCodeString(),
				QueryRequest.INVALID.getCodeString()),
			"and");

		params.add(param);
		
		List<CaseInfo> caseInfos;
		try {
			caseInfos = caseInfoService.searchWithParams(0, numOfOutstandingRequests, params, "status DESC,triggerAtDateTime ASC");
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		for (CaseInfo caseInfo : caseInfos) {
			switch (QueryRequest.codeEnumOf(caseInfo.getStatus())) {
				case RUNNING: // case is awating for next scheduled time.
					logger.debug("Case (" + caseInfo.getId() + ") retrieving from RC-API");
					try {
						Bundle queryResult = retrieveQueryResult(caseInfo);
						if (queryResult != null && !queryResult.isEmpty()) {
							createEntries(queryResult, caseInfo);
						}
					} catch (Exception e) {
						e.printStackTrace();
						return;
					}

					break;
				case REQUEST_PENDING:
				case ERROR_IN_SERVER:
					logger.debug("Case (" + caseInfo.getId() + ") requesting to RC-API");
					try {
						requestForQuery(caseInfo);
					} catch (Exception e) {
						e.printStackTrace();
						return;
					}
					break;

				default:
					// state that we do not need to do anything.
					break;
			}

			// caseInfoService.update(caseInfo);
		}
	}

	@Scheduled(fixedDelay = 120000)
	public void localCodeMappingTask() throws Exception {
		// We may need to load local mapping data. Get a path where the mapping CSV
		// file(s) are located and load them if files exist. The files will then be
		// deleted.
		CSVParser parser = new CSVParser();
		String localMappingFilePath = System.getenv("LOCAL_CODEMAPPING_FILE_PATH");

		if (localMappingFilePath != null && !localMappingFilePath.trim().isEmpty()
				&& !"none".equalsIgnoreCase(localMappingFilePath)) {
			logger.debug("LocalMappingFilePath is set to " + localMappingFilePath);

			// create if folder does not exist.
			Path path = Paths.get(localMappingFilePath);

			if (!Files.exists(path)) {
				try {
					Files.createDirectory(path);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
			}

			// get the list of files in this path.
			BufferedReader reader = null;
			try (Stream<Path> walk = Files.walk(path)) {
				List<String> result = walk.filter(Files::isRegularFile).map(x -> x.toString())
						.collect(Collectors.toList());

				for (String aFile : result) {
					reader = new BufferedReader(new FileReader(aFile));
					String line = reader.readLine();
					int i = 0;

					String omopSourceVocab = null;
					String omopTargetVocab = null;
					String fhirSourceCodeSystem = null;
					String fhirTargetCodeSystem = null;

					int sourceCodeSystemIndex = -1;
					int sourceCodeIndex = -1;
					int sourceCodeDescIndex = -1;
					int targetCodeIndex = -1;

					while (line != null) {
						String line_ = line.trim();
						if (line_.startsWith("#") || line_.charAt(1) == '#' || line_.isEmpty()) {
							// This is comment line skip...
							line = reader.readLine();
							continue;
						}

						i++;
						if (i <= 2) {
							// First line. Must identify coding translation.
							String[] mappingCodes = parser.parseLine(line_);
							if (mappingCodes.length != 2) {
								// Incorrectly formed file. delete this file and move to next one.
								logger.error("Line #" + i + " must be two values. But, there are " + mappingCodes.length
										+ " values. values=" + line_ + ". File, " + aFile
										+ ", is skipped and deleted.");
								reader.close();
								Files.deleteIfExists(Paths.get(aFile));
								break;
							}

							if (i == 1) {
								omopSourceVocab = mappingCodes[0];
								omopTargetVocab = mappingCodes[1];

								if (vocabularyService.findById(omopTargetVocab) == null) {
									logger.error("Line #" + i + " must have standard coding for target. See if "
											+ omopTargetVocab + " exists in OMOP Concept table. File, " + aFile
											+ "is skipped and deleted.");
									reader.close();
									Files.deleteIfExists(Paths.get(aFile));
									break;
								}

								// Done for line 1.
							} else {
								fhirSourceCodeSystem = mappingCodes[0];
								fhirTargetCodeSystem = mappingCodes[1];

								// Done for line 2.
							}
							line = reader.readLine();
							continue;
						}

						if (omopSourceVocab == null || omopTargetVocab == null || fhirSourceCodeSystem == null
								|| fhirTargetCodeSystem == null) {
							// Incorrectly formed file.
							logger.error(
									"OMOP Vocabulary, OMOP Concept Type and FHIR Code System must be defined in the first 2 lines. File, "
											+ aFile + ", is skipped and deleted.");
							reader.close();
							Files.deleteIfExists(Paths.get(aFile));
							break;
						}

						if (i == 3) {
							// This is a header. Get right index for our needs
							String[] mappingCodes = parser.parseLine(line_);
							for (int index = 0; index < mappingCodes.length; index++) {
								if ("SOURCE_CODESYSTEM".equals(mappingCodes[index])) {
									sourceCodeSystemIndex = index;
									continue;
								}
								if ("SOURCE_CODE".equals(mappingCodes[index])) {
									sourceCodeIndex = index;
									continue;
								}
								if ("SOURCE_DESC".equals(mappingCodes[index])) {
									sourceCodeDescIndex = index;
									continue;
								}
								if ("TARGET_CODE".equals(mappingCodes[index])) {
									targetCodeIndex = index;
									continue;
								}
							}

							if (sourceCodeSystemIndex == -1 || sourceCodeIndex == -1 || targetCodeIndex == -1) {
								// These MUST be set.
								logger.error("localCodeMapping failed to set index(es). sourceCodeSystemIndex="
										+ sourceCodeSystemIndex + ", sourceCodeIndex=" + sourceCodeIndex
										+ ", and targetCodeIndex=" + targetCodeIndex + ". This file, " + aFile
										+ ", is skipped and deleted");
								reader.close();
								Files.deleteIfExists(Paths.get(aFile));
								break;
							}

							// We got indexes. Done for line 3.
							line = reader.readLine();
							continue;
						}

						// Now, we are at the actual code translation rows.
						String[] omopSrc = omopSourceVocab.split("\\^");
						logger.debug(omopSrc[0] + "\n" + omopSrc[1] + "\n\n\n\n\n\n\n\n\n\n\n");
						Vocabulary myVocab = vocabularyService.findById(omopSrc[0]);
						if (myVocab == null) {
							// We need to add this to our local code mapping database.
							myVocab = createNewEntry(omopSrc, fhirSourceCodeSystem);
							if (myVocab == null) {
								logger.error("localCodeMapping failed to create a new entry for " + omopSrc[0]
										+ ". This file, " + aFile + ", is skipped and deleted");
								reader.close();
								Files.deleteIfExists(Paths.get(aFile));
								break;
							}
						}

						// Now CSV lines. parse one line at a time.
						String[] fields = parser.parseLine(line);

						List<ParameterWrapper> paramList;
						ParameterWrapper param = new ParameterWrapper();

						// From target code, collect necessary information such as domain id and concept
						// class id.
						String targetCode = fields[targetCodeIndex];
						param.setParameterType("String");
						param.setParameters(Arrays.asList("vocabulary.id", "conceptCode"));
						param.setOperators(Arrays.asList("=", "="));
						param.setValues(Arrays.asList(omopTargetVocab, targetCode));
						param.setRelationship("and");
						paramList = Arrays.asList(param);
						List<Concept> retParam = conceptService.searchWithParams(0, 0, paramList, null);
						if (retParam.size() == 0) {
							// We should have this target code in the concept table.
							logger.error("localCodeMapping task failed to locate the target code system, "
									+ omopTargetVocab + "/" + targetCode + ". Skipping line #" + i);
							line = reader.readLine();
							continue;
						}
						Concept targetConcept = retParam.get(0);

						// Create concept and concept relationship
						String sourceCodeName = fields[sourceCodeSystemIndex];
						if (!fhirSourceCodeSystem.equals(sourceCodeName) && !omopSrc[0].equals(sourceCodeName)) {
							logger.error("The Source Code System, " + sourceCodeName + ", name should be either "
									+ fhirSourceCodeSystem + " or " + omopSrc[0] + ". Skipping line #" + i);
							line = reader.readLine();
							continue;
						}

						// Check the source code. If we don't have this, add it to concept table.
						String sourceCode = fields[sourceCodeIndex];

						param.setParameterType("String");
						param.setParameters(Arrays.asList("vocabulary.id", "conceptCode"));
						param.setOperators(Arrays.asList("=", "="));
						param.setValues(Arrays.asList(omopSrc[0], sourceCode));
						param.setRelationship("and");

						retParam = conceptService.searchWithParams(0, 0, paramList, null);

						Concept sourceConcept;
						if (retParam.size() <= 0) {
							sourceConcept = new Concept();
							sourceConcept.setId(getTheLargestConceptId());

							String conceptName;
							if (sourceCodeDescIndex >= 0 && fields[sourceCodeDescIndex] != null
									&& !fields[sourceCodeDescIndex].trim().isEmpty()) {
								conceptName = fields[sourceCodeDescIndex];
							} else {
								conceptName = omopSrc[0];
							}

							sourceConcept.setConceptName(conceptName);
							sourceConcept.setDomainId(targetConcept.getDomainId());
							sourceConcept.setVocabularyId(myVocab.getId());
							sourceConcept.setConceptClassId(targetConcept.getConceptClassId());
							sourceConcept.setConceptCode(sourceCode);
							sourceConcept.setValidStartDate(targetConcept.getValidStartDate());
							sourceConcept.setValidEndDate(targetConcept.getValidEndDate());

							sourceConcept = conceptService.create(sourceConcept);
							if (sourceConcept == null) {
								logger.error("The Source Code, " + sourceCodeName + "|" + sourceCode
										+ ", could not be created. Skipping line #" + i);
								line = reader.readLine();
								continue;
							}
						} else {
							sourceConcept = retParam.get(0);
						}

						// Now create relationship if this relationship does not exist.
						String relationshipId = omopSrc[0] + " - " + omopTargetVocab + " eq";
						String relationshipName = omopSrc[0] + " to " + omopTargetVocab + " equivalent";
						String revRelationshipId = omopTargetVocab + " - " + omopSrc[0] + " eq";

						Relationship relationship = relationshipService.findById(relationshipId);
						if (relationship == null) {
							relationship = createOmopRelationshipConcept(relationshipId, relationshipName,
									revRelationshipId);
						}

						// see if this relationship exists. If not create one.
						ConceptRelationship conceptRelationship = conceptRelationshipService
								.find(sourceConcept, targetConcept, relationshipId);
						if (conceptRelationship != null) {
							line = reader.readLine();
							continue;
						}

						// Create concept_relationship entry
						conceptRelationship = new ConceptRelationship(sourceConcept, targetConcept, relationshipId);
						conceptRelationship.setValidStartDate(new Date(0L));
						SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
						try {
							Date date = format.parse("2099-12-31");
							conceptRelationship.setValidEndDate(date);
						} catch (ParseException e) {
							e.printStackTrace();
						}

						conceptRelationshipService.create(conceptRelationship);

						// read next line
						line = reader.readLine();

					} // while
					reader.close();
					Files.deleteIfExists(Paths.get(aFile));
				}

			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	private HttpHeaders createHeaders() {
		HttpHeaders httpHeaders = new HttpHeaders();
		byte[] encodedAuth = Base64.encodeBase64(configValues.getRcApiBasicAuth().getBytes());
		String authHeader = "Basic " + new String(encodedAuth);
		httpHeaders.set("Authorization", authHeader);
		httpHeaders.setAccept(Arrays.asList(new MediaType("application", "fhir+json")));

		return httpHeaders;
	}

	private Long getTheLargestConceptId() {
		Long largestId = conceptService.getLargestId();
		if (largestId != null && largestId >= conceptIdStart) {
			conceptIdStart = largestId + 1L;
		}

		return conceptIdStart;
	}

	private Vocabulary createNewEntry(String[] omopVacab, String fhirCoding) throws Exception {
		Vocabulary vocab = createOmopVocabularyConcept(omopVacab);

		// Create FHIR representation of the vocabulary.
		String fhirCodeSystem = CodeableConceptUtil.getFhirSystemNameFromOmopVocabulary(conceptService, vocab.getId());
		if ("none".equalsIgnoreCase(fhirCodeSystem)) {
			// add this to local omopvocab2fhir code map db
			vocabularyService.create(vocab);
		}

		return vocab;
	}

	private Vocabulary createOmopVocabularyConcept(String[] values) throws Exception {
		Vocabulary newVocab = new Vocabulary();
		String vocName = null;
		newVocab.setId(values[0]);
		if (values.length > 1) {
			vocName = values[1];
			newVocab.setVocabularyName(values[1]);
		}

		if (values.length > 2) {
			newVocab.setVocabularyReference(values[2]);
		} else {
			newVocab.setVocabularyReference("OMOPonFHIR generated");
		}

		if (values.length > 3) {
			newVocab.setVocabularyVersion(values[3]);
		}

		// If we created a new vocabulary, we also need to add this to Concept table.
		// Add to concept table and put the new concept id to vocabulary table.

		// create concept
		String name;
		if (vocName != null)
			name = vocName;
		else
			name = values[0];

		// See if we have this vocabulary in concept.
		List<ParameterWrapper> paramList;
		ParameterWrapper param = new ParameterWrapper();
		param.setParameterType("String");
		param.setParameters(Arrays.asList("name", "vocabulary.id", "conceptCode"));
		param.setOperators(Arrays.asList("=", "="));
		param.setValues(Arrays.asList(name, "Vocabulary", "OMOPonFHIR generated"));
		param.setRelationship("and");
		paramList = Arrays.asList(param);
		List<Concept> concepts = conceptService.searchWithParams(0, 0, paramList, null);

		Concept vocConcept;
		if (!concepts.isEmpty()) {
			vocConcept = concepts.get(0);
		} else {
			vocConcept = createVocabularyConcept(name, "Vocabulary");
		}

		if (vocConcept == null)
			return null;

		newVocab.setVocabularyConcept(vocConcept);

		// create vocabulary
		return vocabularyService.create(newVocab);
	}

	private Relationship createOmopRelationshipConcept(String id, String name, String revId) throws Exception {
		Relationship newRelationship = new Relationship();
		newRelationship.setId(id);
		newRelationship.setRelationshipName(name);
		newRelationship.setIsHierarchical('0');
		newRelationship.setDefinesAncestry('0');
		newRelationship.setReverseRelationshipId(revId);

		// See if we have this vocabulary in concept.
		List<ParameterWrapper> paramList;
		ParameterWrapper param = new ParameterWrapper();
		param.setParameterType("String");
		param.setParameters(Arrays.asList("name", "vocabulary.id", "conceptCode"));
		param.setOperators(Arrays.asList("=", "="));
		param.setValues(Arrays.asList(name, "Relationship", "OMOPonFHIR generated"));
		param.setRelationship("and");
		paramList = Arrays.asList(param);
		List<Concept> concepts = conceptService.searchWithParams(0, 0, paramList, null);

		Concept relationshipConcept;
		if (!concepts.isEmpty()) {
			relationshipConcept = concepts.get(0);
		} else {
			relationshipConcept = createVocabularyConcept(name, "Relationship");
		}

		if (relationshipConcept == null)
			return null;
		newRelationship.setRelationshipConcept(relationshipConcept);

		// create vocabulary
		return relationshipService.create(newRelationship);
	}

	private Concept createVocabularyConcept(String name, String vocabId) throws Exception {
		Concept conceptVoc = new Concept();
		conceptVoc.setId(getTheLargestConceptId());
		conceptVoc.setConceptName(name);
		conceptVoc.setDomainId("Metadata");

		conceptVoc.setVocabularyId(vocabId);
		conceptVoc.setConceptClassId(vocabId);
		conceptVoc.setConceptCode("OMOPonFHIR generated");
		conceptVoc.setValidStartDate(new Date(0L));

		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		try {
			Date date = format.parse("2099-12-31");
			conceptVoc.setValidEndDate(date);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		// Create concept
		logger.debug("Trying to create a concept:\n" + conceptVoc.toString());
		Concept newConcept = conceptService.create(conceptVoc);
		if (newConcept != null) {
			logger.debug("Scheduled Task: new concept created for " + name);
		} else {
			logger.debug("Scheduled Task: creating a new concept for " + name + "failed. Vocabulary not created");
			return null;
		}

		return newConcept;
	}
}
