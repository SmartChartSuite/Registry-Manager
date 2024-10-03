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
package edu.gatech.chai.omoponfhir.omopv5.r4.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.exceptions.FHIRException;

import edu.gatech.chai.omoponfhir.omopv5.r4.mapping.OmopCodeableConceptMapping;
import edu.gatech.chai.omopv5.dba.service.ConceptRelationshipService;
import edu.gatech.chai.omopv5.dba.service.ConceptService;
import edu.gatech.chai.omopv5.dba.service.ParameterWrapper;
import edu.gatech.chai.omopv5.model.entity.Concept;
import edu.gatech.chai.omopv5.model.entity.ConceptRelationship;

public class CodeableConceptUtil {
	public static void addCodingFromOmopConcept(CodeableConcept codeableConcept, Concept concept) throws FHIRException {
		String fhirUri = OmopCodeableConceptMapping.fhirUriforOmopVocabulary(concept.getVocabularyId());
		
		Coding coding = new Coding();
		coding.setSystem(fhirUri);
		coding.setCode(concept.getConceptCode());
		coding.setDisplay(concept.getConceptName());
		
		codeableConcept.addCoding(coding);
	}
	
	public static Coding getCodingFromOmopConcept(ConceptService conceptService, Concept concept) throws Exception {
		String fhirUri = CodeableConceptUtil.getFhirSystemNameFromOmopVocabulary(conceptService, concept.getVocabularyId());
		Coding coding = new Coding();
		coding.setSystem(fhirUri);
		coding.setCode(concept.getConceptCode());
		coding.setDisplay(concept.getConceptName());

		return coding;
	}
	
	public static CodeableConcept getCodeableConceptFromOmopConcept(ConceptService conceptService, Concept concept) throws Exception {
		CodeableConcept codeableConcept = new CodeableConcept();
		Coding coding = CodeableConceptUtil.getCodingFromOmopConcept(conceptService, concept);
		codeableConcept.addCoding(coding);

		return codeableConcept;
	}

	public static CodeableConcept getCodeableConceptFromOmopConcept(Concept concept) throws FHIRException {
		CodeableConcept codeableConcept = new CodeableConcept();
		addCodingFromOmopConcept (codeableConcept, concept);		
		return codeableConcept;
	}

	public static Concept getOmopConceptWithOmopCode(ConceptService conceptService, String code) throws Exception {		
		ParameterWrapper param = new ParameterWrapper(
				"String",
				Arrays.asList("conceptCode"),
				Arrays.asList("="),
				Arrays.asList(code),
				"and"
				);
		
		List<ParameterWrapper> params = new ArrayList<ParameterWrapper>();
		params.add(param);

		List<Concept> conceptIds = conceptService.searchWithParams(0, 0, params, null);
		if (conceptIds.isEmpty()) {
			return null;
		}
		
		// We should have only one entry... so... 
		return conceptIds.get(0);
	}

	public static Concept getOmopConceptWithOmopVacabIdAndCode(ConceptService conceptService, String omopVocabularyId, String code) throws Exception {
		if (omopVocabularyId == null) return null;
		
		ParameterWrapper param = new ParameterWrapper(
				"String",
				Arrays.asList("vocabularyId", "conceptCode"),
				Arrays.asList("=", "="),
				Arrays.asList(omopVocabularyId, code),
				"and"
				);
		
		List<ParameterWrapper> params = new ArrayList<ParameterWrapper>();
		params.add(param);

		List<Concept> conceptIds = conceptService.searchWithParams(0, 0, params, null);
		if (conceptIds.isEmpty()) {
			return null;
		}
		
		// We should have only one entry... so... 
		return conceptIds.get(0);
	}
	
	public static Concept getOmopConceptWithFhirConcept(ConceptService conceptService, Coding fhirCoding) throws Exception {
		String system = fhirCoding.getSystem();
		String code = fhirCoding.getCode();
		
		String omopVocabularyId = OmopCodeableConceptMapping.omopVocabularyforFhirUri(system);
		return getOmopConceptWithOmopVacabIdAndCode(conceptService, omopVocabularyId, code);
	}
	
	public static String getOmopVocabularyFromFhirSystemName(ConceptService conceptService, String fhirSystemUri) throws Exception {
		List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();

		ParameterWrapper paramWrapper = new ParameterWrapper();
		paramWrapper.setParameterType("String");
		paramWrapper.setParameters(Arrays.asList("conceptName", "conceptClassId"));
		paramWrapper.setOperators(Arrays.asList("=", "="));
		paramWrapper.setValues(Arrays.asList(fhirSystemUri, "FHIR Concept Mapping"));
		paramWrapper.setRelationship("and");
		paramList.add(paramWrapper);

		List<Concept> concepts = conceptService.searchWithParams(0, 0, paramList, null);
		for (Concept concept : concepts) {
			return concept.getVocabularyId();
		}

		return "None";
	}

	public static String getFhirSystemNameFromOmopVocabulary(ConceptService conceptService, String omopVocabulary) throws Exception {
		List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();

		ParameterWrapper paramWrapper = new ParameterWrapper();
		paramWrapper.setParameterType("String");
		paramWrapper.setParameters(Arrays.asList("conceptClassId", "vocabularyId"));
		paramWrapper.setOperators(Arrays.asList("=", "="));
		paramWrapper.setValues(Arrays.asList("FHIR Concept Mapping", omopVocabulary));
		paramWrapper.setRelationship("and");
		paramList.add(paramWrapper);

		List<Concept> concepts = conceptService.searchWithParams(0, 0, paramList, null);
		String fhirConceptName = "None";
		for (Concept concept : concepts) {
			fhirConceptName = concept.getConceptName();
			if (fhirConceptName.startsWith("http://") || fhirConceptName.startsWith("https://")) {
				return fhirConceptName;
			}
		}

		return fhirConceptName;
	}

	public static Concept searchConcept(ConceptService conceptService, CodeableConcept codeableConcept) throws Exception {
		List<Coding> codings = codeableConcept.getCoding();
		for (Coding coding : codings) {
			// get OMOP Vocabulary from mapping.
			Concept ret = getOmopConceptWithFhirConcept(conceptService, coding);
			if (ret != null) return ret;
		}
		return null;
	}

	/**
	 * Creates a {@link CodeableConcept} from a {@link Concept}
	 * @param concept the {@link Concept} to use to generate the {@link CodeableConcept}
	 * @return a {@link CodeableConcept} generated from the passed in {@link Concept}
	 * @throws FHIRException if the {@link Concept} vocabulary cannot be mapped by the {@link OmopCodeableConceptMapping} fhirUriforOmopVocabularyi method.
     */
	public static CodeableConcept createFromConcept(Concept concept) throws FHIRException{
		String conceptVocab = concept.getVocabularyId();
		String conceptFhirUri = OmopCodeableConceptMapping.fhirUriforOmopVocabulary(conceptVocab);
		String conceptCode = concept.getConceptCode();
		String conceptName = concept.getConceptName();

		Coding conceptCoding = new Coding();
		conceptCoding.setSystem(conceptFhirUri);
		conceptCoding.setCode(conceptCode);
		conceptCoding.setDisplay(conceptName);

		CodeableConcept codeableConcept = new CodeableConcept();
		codeableConcept.addCoding(conceptCoding);
		return codeableConcept;
	}
	
	public static String convert2String(Coding coding) {
		String retVal = "";
		if (coding.getSystem() != null && !coding.getSystem().isBlank()) {
			retVal += coding.getSystem() + " ";
		}

		if (coding.getCode() != null && !coding.getCode().isBlank()) {
			retVal += coding.getCode() + " ";
		}

		if (coding.getDisplay() != null && !coding.getDisplay().isBlank()) {
			retVal += coding.getDisplay();
		}

		return retVal;
	}

	/**
	 * 
	 * @param coding1
	 * @param coding2
	 * @return 
	 *   1 if only code matches,
	 *   0 if both system and code match,
	 *   -1 if none matches.
	 */
	public static int compareCodings(Coding coding1, Coding coding2) {
		boolean isSystemMatch = false;
		boolean isCodeMatch = false;
		
		if (coding1.hasSystem() && coding1.hasSystem()) {
			if (coding1.getSystem().equals(coding2.getSystem())) {
				isSystemMatch = true;
			}
		}
		
		if (coding1.hasCode() && coding2.hasCode()) {
			if (coding1.getCode().equals(coding2.getCode())) {
				isCodeMatch = true;
			}
		}
		
		if (isSystemMatch && isCodeMatch) return 0;
		if (isCodeMatch) return 1;
		return -1;
	}

	public static Coding getFhirCodingFromOmopSourceString(ConceptService conceptService, String sourceString) throws Exception {
		Coding retv = null;

		List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();
		ParameterWrapper paramWrapper = new ParameterWrapper();
		paramWrapper.setParameterType("String");
		paramWrapper.setParameters(Arrays.asList("conceptName", "conceptClassId"));
		paramWrapper.setOperators(Arrays.asList("=", "="));
		paramWrapper.setValues(Arrays.asList(sourceString, "FHIR Concept"));
		paramWrapper.setRelationship("and");
		paramList.add(paramWrapper);

		List<Concept> concepts = conceptService.searchWithParams(0, 0, paramList, null);
		String vocabularyId = "None";
		String fhirCode = "None";
		String fhirDisplay = sourceString;
		String fhirSystem = "None";
		for (Concept concept : concepts) {
			vocabularyId = concept.getVocabularyId();
			fhirCode = concept.getConceptCode();
		}

		if ("None".equals(vocabularyId)) {
			return retv;
		}

		paramList.clear();
		paramWrapper.setParameterType("String");
		paramWrapper.setParameters(Arrays.asList("vocabularyId", "conceptClassId"));
		paramWrapper.setOperators(Arrays.asList("=", "="));
		paramWrapper.setValues(Arrays.asList(vocabularyId, "FHIR Concept Mapping"));
		paramWrapper.setRelationship("and");
		paramList.add(paramWrapper);

		concepts = conceptService.searchWithParams(0, 0, paramList, null);
		for (Concept concept : concepts) {
			fhirSystem = concept.getConceptName();
		}

		retv = new Coding();
		retv.setSystem(fhirSystem);
		retv.setCode(fhirCode);
		retv.setDisplay(fhirDisplay);

		return retv;
	}

	public static Long getOmopCodeFromFhirCoding(ConceptService conceptService, Coding fhirCoding) throws Exception {
		Long retv = 0L;

		String fhirSystem = fhirCoding.getSystem();
		String fhirCode = fhirCoding.getCode();
		if (fhirSystem == null || fhirSystem.isEmpty() || fhirCode == null || fhirCode.isEmpty()) {
			// We need to know system and code.
			return retv;
		}

		List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();
		ParameterWrapper paramWrapper = new ParameterWrapper();
		paramWrapper.setParameterType("String");
		paramWrapper.setParameters(Arrays.asList("conceptName", "conceptClassId"));
		paramWrapper.setOperators(Arrays.asList("=", "="));
		paramWrapper.setValues(Arrays.asList(fhirSystem, "FHIR Concept Mapping"));
		paramWrapper.setRelationship("and");
		paramList.add(paramWrapper);

		String vocabularyId = null;
		List<Concept> concepts = conceptService.searchWithParams(0, 0, paramList, null);
		for (Concept concept : concepts) {
			vocabularyId = concept.getConceptName();
		}

		if (vocabularyId == null) {
			return retv;
		}

		paramList.clear();

		paramWrapper = new ParameterWrapper();
		paramWrapper.setParameterType("String");
		paramWrapper.setParameters(Arrays.asList("conceptCode", "vocabularyId"));
		paramWrapper.setOperators(Arrays.asList("=", "="));
		paramWrapper.setValues(Arrays.asList(fhirCode, vocabularyId));
		paramWrapper.setRelationship("and");
		paramList.add(paramWrapper);

		concepts = conceptService.searchWithParams(0, 0, paramList, null);
		for (Concept concept : concepts) {
			return concept.getId();
		}

		return retv;
	}

	public static Coding getFhirCodingFromOmopConcept(ConceptService conceptService, Long conceptId) throws Exception {
		Coding retv = null;

		List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();
		ParameterWrapper paramWrapper = new ParameterWrapper();
		paramWrapper.setParameterType("Long");
		paramWrapper.setParameters(Arrays.asList("id"));
		paramWrapper.setOperators(Arrays.asList("="));
		paramWrapper.setValues(Arrays.asList(String.valueOf(conceptId)));
		paramWrapper.setRelationship("and");
		paramList.add(paramWrapper);

		List<Concept> concepts = conceptService.searchWithParams(0, 0, paramList, null);

		String fhirSystem = "None";
		String fhirCode = "None";
		String fhirDisplay = "None";
		String vocabularyId = "None";
		for (Concept concept : concepts) {
			fhirCode = concept.getConceptCode();
			fhirDisplay = concept.getConceptName();
			vocabularyId = concept.getVocabularyId();
		}

		if ("None".equals(vocabularyId)) {
			return retv;
		}

		paramList.clear();
		paramWrapper.setParameterType("String");
		paramWrapper.setParameters(Arrays.asList("vocabularyId", "conceptClassId"));
		paramWrapper.setOperators(Arrays.asList("=", "="));
		paramWrapper.setValues(Arrays.asList(vocabularyId, "FHIR Concept Mapping"));
		paramWrapper.setRelationship("and");
		paramList.add(paramWrapper);

		concepts = conceptService.searchWithParams(0, 0, paramList, null);
		for (Concept concept : concepts) {
			fhirSystem = concept.getConceptName();
		}

		retv = new Coding();
		retv.setSystem(fhirSystem);
		retv.setCode(fhirCode);
		retv.setDisplay(fhirDisplay);

		return retv;
	}
}
