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
package edu.gatech.chai.omoponfhir.omopv5.r4.mapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.web.context.WebApplicationContext;

import ca.uhn.fhir.rest.api.SortSpec;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.EncounterResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.CodeableConceptUtil;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.ExtensionUtil;
import edu.gatech.chai.omopv5.dba.service.ConceptRelationshipService;
import edu.gatech.chai.omopv5.dba.service.ConceptService;
import edu.gatech.chai.omopv5.dba.service.FResourceDeduplicateService;
import edu.gatech.chai.omopv5.dba.service.IService;
import edu.gatech.chai.omopv5.dba.service.ParameterWrapper;
import edu.gatech.chai.omopv5.dba.service.VisitOccurrenceService;
import edu.gatech.chai.omopv5.model.entity.BaseEntity;
import edu.gatech.chai.omopv5.model.entity.Concept;
import edu.gatech.chai.omopv5.model.entity.FResourceDeduplicate;
import edu.gatech.chai.omopv5.model.entity.VisitOccurrence;

public abstract class BaseOmopResource<v extends Resource, t extends BaseEntity, p extends IService<t>>
		implements IResourceMapping<v, t> {
			
	static final Logger logger = LoggerFactory.getLogger(BaseOmopResource.class);

	private p myOmopService;
	private Class<t> myEntityClass;
	private Class<p> myServiceClass;
	private String myFhirResourceType;
	private FResourceDeduplicateService fResourceDeduplicateService;
	private String returnStringValue;

	protected ConceptService conceptService;
	protected ConceptRelationshipService conceptRelationshipService;

	public static String MAP_EXCEPTION_FILTER = "FILTER";
	public static String MAP_EXCEPTION_EXCLUDE = "EXCLUDE";

	public BaseOmopResource(WebApplicationContext context, Class<t> entityClass, Class<p> serviceClass,
			String fhirResourceType) {
		myOmopService = context.getBean(serviceClass);
		fResourceDeduplicateService = context.getBean(FResourceDeduplicateService.class);

		myEntityClass = entityClass;
		myFhirResourceType = fhirResourceType;

		conceptService = context.getBean(ConceptService.class);
		conceptRelationshipService = context.getBean(ConceptRelationshipService.class);
	}

	public String getMyFhirResourceType() {
		return this.myFhirResourceType;
	}

	public p getMyOmopService() {
		return this.myOmopService;
	}

	public void setMyOmopService(WebApplicationContext context) {
		this.myOmopService = context.getBean(myServiceClass);
	}

	public String getReturnStringValue() {
		return this.returnStringValue;
	}

	public Class<t> getMyEntityClass() {
		return this.myEntityClass;
	}

	public void removeDbase(Long id) throws Exception {
		myOmopService.removeById(id);
	}

	public Long removeByFhirId(IdType fhirId) throws Exception {
		Long idLongPart = fhirId.getIdPartAsLong();
		Long myId = IdMapping.getOMOPfromFHIR(idLongPart, getMyFhirResourceType());

		return myOmopService.removeById(myId);
	}

	// public Long getSize(boolean cacheOnly) {
	// 	return myOmopService.getSize(true);
	// }

	public Long getSize() throws Exception {
		Long size = myOmopService.getSize();
		
		// update the counts map.
		if (size != null)
			ExtensionUtil.addResourceCount(myFhirResourceType, size);

		return size;
	}

	// public Long getSize(List<ParameterWrapper> mapList, boolean cacheOnly) {
	// 	return myOmopService.getSize(mapList, cacheOnly);
	// }

	public Long getSize(List<ParameterWrapper> mapList) throws Exception {
		return myOmopService.getSize(mapList);
	}

	// public Long getSize(String queryString, List<String> parameterList, List<String> valueList, boolean cacheOnly) {
	// 	return myOmopService.getSize(queryString, parameterList, valueList, cacheOnly);
	// }

	public Long getSize(String queryString, List<String> parameterList, List<String> valueList) throws Exception {
		Long size = myOmopService.getSize(queryString, parameterList, valueList);
		if (size != null && (parameterList == null || parameterList.isEmpty())) {
			ExtensionUtil.addResourceCount(myFhirResourceType, size);
		}

		return size;
	}

	/***
	 * search for duplicates.
	 * @throws Exception 
	 */
	protected Long findOMOPEntity(List<Identifier> identifiers, String domainId) throws Exception {

		for (Identifier identifier : identifiers) {
			List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();

			// FHIR Resource Type parameter
			ParameterWrapper resourceParam = new ParameterWrapper();
			resourceParam.setParameterType("String");
			resourceParam.setParameters(Arrays.asList("domainId", "fhirResourceType", "fhirIdentifierSystem", "fhirIdentifierValue"));
			resourceParam.setOperators(Arrays.asList("=", "=", "=", "="));
			resourceParam.setValues(Arrays.asList(domainId, getMyFhirResourceType(), identifier.getSystem(), identifier.getValue()));
			resourceParam.setRelationship("and");

			paramList.add(resourceParam);

			List<FResourceDeduplicate> omopEntities = fResourceDeduplicateService.searchWithParams(0, 0, paramList, null);
			if (omopEntities != null && !omopEntities.isEmpty()) {
				FResourceDeduplicate fResourceDeduplicate = omopEntities.get(0);
				return fResourceDeduplicate.getOmopId();
			}

			if (omopEntities == null) return null;
		} 
			
		return 0L;
	}

	protected void createDuplicateEntry(List<Identifier> identifiers, String domainId, Long omopId) throws Exception {
		for (Identifier identifier : identifiers) {
			if (identifier.getSystem() != null && !identifier.getSystem().isEmpty()
				&& identifier.getValue() != null && !identifier.getValue().isEmpty()) {
				FResourceDeduplicate fResourceDeduplicate = new FResourceDeduplicate();
				fResourceDeduplicate.setDomainId(domainId);
				fResourceDeduplicate.setOmopId(omopId);
				fResourceDeduplicate.setFhirResourceType(getMyFhirResourceType());
				fResourceDeduplicate.setFhirIdentifierSystem(identifier.getSystem());
				fResourceDeduplicate.setFhirIdentifierValue(identifier.getValue());
				
				FResourceDeduplicate ret = fResourceDeduplicateService.create(fResourceDeduplicate);
				if (ret == null) throw new Exception("Duplicate Entry Create Failed");
			}
		}
	}

	/***
	 * constructResource: Overwrite this if you want to implement includes.
	 * @throws Exception 
	 */
	public v constructResource(Long fhirId, t entity, List<String> includes) throws Exception {
		return constructFHIR(fhirId, entity);
	}

	/***
	 * toFHIR this is called from FHIR provider for read operation.
	 * @throws Exception 
	 */
	public v toFHIR(IdType id) throws Exception {
		Long id_long_part = id.getIdPartAsLong();
		Long myId = IdMapping.getOMOPfromFHIR(id_long_part, getMyFhirResourceType());

		t entityClass = (t) getMyOmopService().findById(myId);
		if (entityClass == null)
			return null;

		Long fhirId = IdMapping.getFHIRfromOMOP(myId, getMyFhirResourceType());

		return constructFHIR(fhirId, entityClass);
	}

	public void searchWithoutParams(int fromIndex, int toIndex, List<IBaseResource> listResources,
			List<String> includes, String sort) throws Exception {
		List<t> entities = getMyOmopService().searchWithoutParams(fromIndex, toIndex, sort);

		// We got the results back from OMOP database. Now, we need to construct
		// the list of
		// FHIR Patient resources to be included in the bundle.
		for (t entity : entities) {
			Long omopId = entity.getIdAsLong();
			Long fhirId = IdMapping.getFHIRfromOMOP(omopId, getMyFhirResourceType());
			v fhirResource = constructResource(fhirId, entity, includes);
			if (fhirResource != null) {
				listResources.add(fhirResource);
				addRevIncludes(omopId, includes, listResources);
			}
		}
	}

	public void searchWithParams(int fromIndex, int toIndex, List<ParameterWrapper> mapList,
			List<IBaseResource> listResources, List<String> includes, String sort) throws Exception {
		List<t> entities = getMyOmopService().searchWithParams(fromIndex, toIndex, mapList, sort);

		for (t entity : entities) {
			Long omopId = entity.getIdAsLong();
			Long fhirId = IdMapping.getFHIRfromOMOP(omopId, getMyFhirResourceType());
			v fhirResource = constructResource(fhirId, entity, includes);
			if (fhirResource != null) {
				listResources.add(fhirResource);
				// Do the rev_include and add the resource to the list.
				addRevIncludes(omopId, includes, listResources);
			}
		}
	}

	public void searchWithSql(String sql, List<String> parameterList, List<String> valueList, int fromIndex, int toIndex, String sort, List<IBaseResource> listResources) throws Exception {
		List<t> entities = getMyOmopService().searchBySql(fromIndex, toIndex, sql, parameterList, valueList, sort);

		for (t entity : entities) {
			Long omopId = entity.getIdAsLong();
			Long fhirId = IdMapping.getFHIRfromOMOP(omopId, getMyFhirResourceType());
			v fhirResource = constructResource(fhirId, entity, null);
			if (fhirResource != null) {
				listResources.add(fhirResource);
			}		
		}
	}

	// Override the this method to provide rev_includes.
	public void addRevIncludes(Long omopId, List<String> includes, List<IBaseResource> listResources) throws Exception {

	}

	// Some common functions that are repetitively used.
	protected void addParamlistForPatientIDName(String parameter, String value, ParameterWrapper paramWrapper,
			List<ParameterWrapper> mapList) throws Exception {
		switch (parameter) {
		case "Patient:" + Patient.SP_RES_ID:
			String pId = value;
			paramWrapper.setParameterType("Long");
			paramWrapper.setParameters(Arrays.asList("fPerson.id"));
			paramWrapper.setOperators(Arrays.asList("="));
			paramWrapper.setValues(Arrays.asList(pId));
			paramWrapper.setRelationship("or");
			mapList.add(paramWrapper);
			break;
		case "Patient:" + Patient.SP_NAME:
			String patientName = value.replace("\"", "");
			// patientName = patientName.replace("'", "");
			
			paramWrapper.setParameterType("String");
			paramWrapper.setParameters(Arrays.asList("fPerson.familyName", "fPerson.givenName1", "fPerson.givenName2",
					"fPerson.prefixName", "fPerson.suffixName"));
			paramWrapper.setOperators(Arrays.asList("like", "like", "like", "like", "like"));
			paramWrapper.setValues(Arrays.asList("%" + patientName + "%"));
			paramWrapper.setRelationship("or");
			mapList.add(paramWrapper);
			break;
		case "Patient:" + Patient.SP_IDENTIFIER:
			String identifier = value.replace("\"", "");
			// identifier = identifier.replace("'", "");

			// Patient identifier should be token variable separated with |
			String[] ids = identifier.split("\\|");
			if (ids.length == 1) {
				identifier = ids[0].trim();
			} else {
				String system = ids[0].trim();
				String system_value = ids[1].trim();
				if (system == null || system.isEmpty()) {
					identifier = system_value;
				} else {
					String omopVocabId = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService, system);
					if (!"None".equals(omopVocabId)) {
						identifier = omopVocabId + "^" + system_value;
					} else {
						identifier = system + "^" + system_value;
					}
				}
			}
			
			
			paramWrapper.setParameterType("String");
			paramWrapper.setParameters(Arrays.asList("fPerson.personSourceValue"));
//			paramWrapper.setOperators(Arrays.asList("like"));
//			paramWrapper.setValues(Arrays.asList("%" + identifier + "%"));
			paramWrapper.setOperators(Arrays.asList("="));
			paramWrapper.setValues(Arrays.asList(identifier));
			paramWrapper.setRelationship("or");
			mapList.add(paramWrapper);
			break;
		default:
			return;
		}
	}
	
	public String constructOrderParams(SortSpec theSort) {
		String direction;
		
		if (theSort == null) return "id ASC";
		
		if (theSort.getOrder() != null) direction = theSort.getOrder().toString();
		else direction = "ASC";

		String orderParam = "id " + direction;
		
		return orderParam;
	}

	public String codingInString(Coding coding, int limit) {
		String system = coding.getSystem();
		String code = coding.getCode();
		String display = coding.getDisplay();

		if (system.startsWith("http:")) {
			int lastPartOfUrlIndex = system.lastIndexOf("/");
			system = system.substring(lastPartOfUrlIndex+1);
		}

		String retv = system + " " + code + " " + display;
		if (retv.length() > limit) {
			retv = retv.substring(0, limit-1);
		}

		return retv.trim();
	}	
	
	public Concept fhirCode2OmopConcept(ConceptService conceptService, CodeableConcept code, String obsSourceString) throws Exception {
		List<Coding> codings = code.getCoding();
		Coding codingFound = null;
		Coding codingSecondChoice = null;
		String omopSystem = null;
		for (Coding coding : codings) {
			String fhirSystemUri = coding.getSystem();
			// We prefer LOINC code. So, if we found one, we break out from
			// this loop
			if (code.getText() != null && !code.getText().isEmpty()) {
				this.returnStringValue = code.getText();
			} else {
				this.returnStringValue = (coding.getSystem() + "^" + coding.getCode() + "^" + coding.getDisplay()).trim();
			}

			if (fhirSystemUri != null && fhirSystemUri.equals(OmopCodeableConceptMapping.LOINC.getFhirUri())) {
				// Found the code we want.
				codingFound = coding;
				break;
			} else {
				// See if we can handle this coding.
				try {
					if (fhirSystemUri != null && !fhirSystemUri.isEmpty()) {
						omopSystem = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService, fhirSystemUri);

						if (!"None".equals(omopSystem)) {
							// We can at least handle this. Save it
							// We may find another one we can handle. Let it replace.
							// 2nd choice is just 2nd choice.
							codingSecondChoice = coding;
						}
					}
				} catch (FHIRException e) {
					e.printStackTrace();
				}
			}
		}

		Concept concept = null;
		if (codingFound != null) {
			// Find the concept id for this coding.
			concept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService,
					OmopCodeableConceptMapping.LOINC.getOmopVocabulary(), codingFound.getCode());
		} else if (codingSecondChoice != null) {
			// This is not our first choice. But, found one that we can
			// map.
			concept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService, omopSystem,
					codingSecondChoice.getCode());
		} else {
			concept = null;
		}

		if (concept == null) {
			concept = conceptService.findById(0L);
		}
		
		return concept;
	}
	
	public VisitOccurrence fhirContext2OmopVisitOccurrence(VisitOccurrenceService visitOccurrenceService, Reference contextReference) throws Exception {
		VisitOccurrence visitOccurrence = null;
		if (contextReference != null && !contextReference.isEmpty()) {
			if (contextReference.getReferenceElement().getResourceType().equals(EncounterResourceProvider.getType())) {
				// Encounter context.
				Long fhirEncounterId = contextReference.getReferenceElement().getIdPartAsLong();
				Long omopVisitOccurrenceId = IdMapping.getOMOPfromFHIR(fhirEncounterId,
						EncounterResourceProvider.getType());
				if (omopVisitOccurrenceId != null) {
					visitOccurrence = visitOccurrenceService.findById(omopVisitOccurrenceId);
				}
				if (visitOccurrence == null) {
					logger.warn ("The Encounter (" + contextReference.getReference() + ") context couldn't be found.");
				} else {
					return visitOccurrence;
				}
			} else {
				// Episode of Care context.
				// TODO: Do we have a mapping for the Episode of Care??
			}
		}
		
		return visitOccurrence;
	}
}
