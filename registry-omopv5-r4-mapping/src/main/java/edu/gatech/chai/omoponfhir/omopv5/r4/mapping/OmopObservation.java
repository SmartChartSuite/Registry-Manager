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

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationReferenceRangeComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Quantity.QuantityComparator;
import org.hl7.fhir.r4.model.codesystems.QuantityComparatorEnumFactory;
import org.apache.commons.lang.StringEscapeUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.TokenParamModifier;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.ConditionResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.DocumentReferenceResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.EncounterResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.MedicationRequestResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.MedicationStatementResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.ObservationResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.PatientResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.provider.PractitionerResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.CodeableConceptUtil;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.ConfigValues;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.DateUtil;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.ExtensionUtil;
import edu.gatech.chai.omopv5.dba.service.FObservationViewService;
import edu.gatech.chai.omopv5.dba.service.FactRelationshipService;
import edu.gatech.chai.omopv5.dba.service.MeasurementService;
import edu.gatech.chai.omopv5.dba.service.NoteService;
import edu.gatech.chai.omopv5.dba.service.ObservationService;
import edu.gatech.chai.omopv5.dba.service.ParameterWrapper;
import edu.gatech.chai.omopv5.dba.service.VisitOccurrenceService;
import edu.gatech.chai.omopv5.model.entity.BaseEntity;
import edu.gatech.chai.omopv5.model.entity.Concept;
import edu.gatech.chai.omopv5.model.entity.FObservationView;
import edu.gatech.chai.omopv5.model.entity.FPerson;
import edu.gatech.chai.omopv5.model.entity.FactRelationship;
import edu.gatech.chai.omopv5.model.entity.Measurement;
import edu.gatech.chai.omopv5.model.entity.Note;
import edu.gatech.chai.omopv5.model.entity.VisitOccurrence;

public class OmopObservation extends BaseOmopResource<Observation, FObservationView, FObservationViewService> {

	static final Logger logger = LoggerFactory.getLogger(OmopObservation.class);
	private static OmopObservation omopObservation = new OmopObservation();

	public static final long SYSTOLIC_CONCEPT_ID = 3004249L;
	public static final long DIASTOLIC_CONCEPT_ID = 3012888L;
	public static final String SYSTOLIC_LOINC_CODE = "8480-6";
	public static final String DIASTOLIC_LOINC_CODE = "8462-4";
	public static final String BP_SYSTOLIC_DIASTOLIC_CODE = "55284-4";
	public static final String BP_SYSTOLIC_DIASTOLIC_DISPLAY = "Blood pressure systolic & diastolic";

	private MeasurementService measurementService;
	private ObservationService observationService;
	private VisitOccurrenceService visitOccurrenceService;
	private NoteService noteService;
	private FactRelationshipService factRelationshipService;

	private ConfigValues schemaConfig;

	public OmopObservation(WebApplicationContext context) {
		super(context, FObservationView.class, FObservationViewService.class, ObservationResourceProvider.getType());
		initialize(context);

		// getSize(true);
	}

	public OmopObservation() {
		super(ContextLoaderListener.getCurrentWebApplicationContext(), FObservationView.class,
				FObservationViewService.class, ObservationResourceProvider.getType());
		initialize(ContextLoaderListener.getCurrentWebApplicationContext());
	}

	private void initialize(WebApplicationContext context) {
		// Get bean for other services that we need for mapping.
		measurementService = context.getBean(MeasurementService.class);
		observationService = context.getBean(ObservationService.class);
		visitOccurrenceService = context.getBean(VisitOccurrenceService.class);
		noteService = context.getBean(NoteService.class);
		factRelationshipService = context.getBean(FactRelationshipService.class);
		schemaConfig = context.getBean(ConfigValues.class);
	}

	public Long getDiastolicConcept() {
		return OmopObservation.DIASTOLIC_CONCEPT_ID;
	}

	public static OmopObservation getInstance() {
		return OmopObservation.omopObservation;
	}

	@Override
	public Observation constructFHIR(Long fhirId, FObservationView fObservationView) throws Exception {
		Observation observation = new Observation();
		observation.setId(new IdType(fhirId));

		String omopVocabulary = fObservationView.getObservationConcept().getVocabularyId();
		String systemUriString = CodeableConceptUtil.getFhirSystemNameFromOmopVocabulary(conceptService,
				omopVocabulary);
		if ("None".equals(systemUriString)) {
			// If we can't find FHIR Uri or system name, just use Omop Vocabulary Id.
			systemUriString = omopVocabulary;
		}

		// See if we have comparator.
		QuantityComparator comparator = null;
		Concept operatorConcept = fObservationView.getObservationOperatorConcept();
		if (operatorConcept != null) {
			String opName = operatorConcept.getConceptName();
			if (opName != null && !opName.isEmpty()) {
				comparator = QuantityComparator.fromCode(opName);
			}
		}

		// If we have unit, this should be used across all the value.
		String unitSystemUri = null;
		String unitCode = null;
		String unitUnit = null;
		String unitSource = null;
		Concept unitConcept = fObservationView.getUnitConcept();
		if (unitConcept == null || unitConcept.getId() == 0L) {
			// see if we can get the unit from source column.
			unitSource = fObservationView.getUnitSourceValue();
			if (unitSource != null && !unitSource.isEmpty()) {
				unitUnit = unitSource;
				unitConcept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService,
						OmopCodeableConceptMapping.UCUM.getOmopVocabulary(), unitSource);
			}
		}

		if (unitConcept != null && unitConcept.getId() != 0L) {
			String omopUnitVocabularyId = unitConcept.getVocabularyId();
			unitSystemUri = CodeableConceptUtil.getFhirSystemNameFromOmopVocabulary(conceptService,
					omopUnitVocabularyId);
			if ("None".equals(unitSystemUri)) {
				unitSystemUri = omopUnitVocabularyId;
			}

			unitUnit = unitConcept.getConceptName();
			unitCode = unitConcept.getConceptCode();
		}

		String codeString = fObservationView.getObservationConcept().getConceptCode();
		String displayString;
		if (fObservationView.getObservationConcept().getId() == 0L) {
			displayString = fObservationView.getObservationSourceValue();
		} else {
			displayString = fObservationView.getObservationConcept().getConceptName();
		}

		// OMOP database maintains Systolic and Diastolic Blood Pressures
		// separately.
		// FHIR however keeps them together. Observation DAO filters out
		// Diastolic values.
		// Here, when we are reading systolic, we search for matching diastolic
		// and put them
		// together. The Observation ID will be systolic's OMOP ID.
		// public static final Long SYSTOLIC_CONCEPT_ID = new Long(3004249);
		// public static final Long DIASTOLIC_CONCEPT_ID = new Long(3012888);
		if (OmopObservation.SYSTOLIC_CONCEPT_ID == fObservationView.getObservationConcept().getId()) {
			// Set coding for systolic and diastolic observation
			systemUriString = OmopCodeableConceptMapping.LOINC.getFhirUri();
			codeString = BP_SYSTOLIC_DIASTOLIC_CODE;
			displayString = BP_SYSTOLIC_DIASTOLIC_DISPLAY;

			List<ObservationComponentComponent> components = new ArrayList<ObservationComponentComponent>();
			// First we add systolic component.
			ObservationComponentComponent comp = new ObservationComponentComponent();
			Coding coding = new Coding(systemUriString, fObservationView.getObservationConcept().getConceptCode(),
					fObservationView.getObservationConcept().getConceptName());
			CodeableConcept componentCode = new CodeableConcept();
			componentCode.addCoding(coding);
			comp.setCode(componentCode);

			if (fObservationView.getValueAsNumber() != null) {
				Quantity quantity = new Quantity(fObservationView.getValueAsNumber().doubleValue());

				// Comparator if exists
				if (comparator != null) {
					quantity.setComparator(comparator);
				}

				// Unit is defined as a concept code in omop v4, then unit and
				// code are the same in this case
				if (unitSystemUri != null || unitCode != null || unitUnit != null) {
					quantity.setUnit(unitUnit);
					quantity.setCode(unitCode);
					quantity.setSystem(unitSystemUri);
					comp.setValue(quantity);
				} else {
					if (unitSource != null) {
						quantity.setUnit(unitSource);
					}
				}
			}
			components.add(comp);

			// Now search for diastolic component.
			WebApplicationContext myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();
			FObservationViewService myService = myAppCtx.getBean(FObservationViewService.class);
			FObservationView diastolicDb = myService.findDiastolic(DIASTOLIC_CONCEPT_ID,
					fObservationView.getFPerson().getId(), fObservationView.getObservationDate(),
					fObservationView.getObservationDateTime());
			if (diastolicDb != null) {
				comp = new ObservationComponentComponent();
				coding = new Coding(systemUriString, diastolicDb.getObservationConcept().getConceptCode(),
						diastolicDb.getObservationConcept().getConceptName());
				componentCode = new CodeableConcept();
				componentCode.addCoding(coding);
				comp.setCode(componentCode);

				if (diastolicDb.getValueAsNumber() != null) {
					Quantity quantity = new Quantity(diastolicDb.getValueAsNumber().doubleValue());

					// Comparator if exists
					if (comparator != null) {
						quantity.setComparator(comparator);
					}

					// Unit is defined as a concept code in omop v4, then unit
					// and code are the same in this case
					if (diastolicDb.getUnitConcept() != null && diastolicDb.getUnitConcept().getId() != 0L) {
						quantity.setUnit(diastolicDb.getUnitConcept().getConceptName());
						quantity.setCode(diastolicDb.getUnitConcept().getConceptCode());
						String unitSystem = CodeableConceptUtil.getFhirSystemNameFromOmopVocabulary(
								conceptService, diastolicDb.getUnitConcept().getVocabularyId());
						if ("None".equals(unitSystem))
							unitSystem = diastolicDb.getUnitConcept().getVocabularyId();
						quantity.setSystem(unitSystem);
						comp.setValue(quantity);
					} else {
						String diastolicUnitSource = diastolicDb.getUnitSourceValue();
						if (diastolicUnitSource != null && !diastolicUnitSource.isEmpty()) {
							Concept diastolicUnitConcept = CodeableConceptUtil
									.getOmopConceptWithOmopVacabIdAndCode(conceptService, "UCUM", unitSource);
							if (diastolicUnitConcept != null && diastolicUnitConcept.getId() != 0L) {
								quantity.setUnit(diastolicUnitConcept.getConceptName());
								quantity.setCode(diastolicUnitConcept.getConceptCode());
								String unitSystem = CodeableConceptUtil.getFhirSystemNameFromOmopVocabulary(
										conceptService, diastolicUnitConcept.getVocabularyId());
								if ("None".equals(unitSystem))
									unitSystem = diastolicUnitConcept.getVocabularyId();
								quantity.setSystem(unitSystem);
							} else {
								quantity.setUnit(diastolicUnitSource);
							}
							comp.setValue(quantity);
						}
					}
				}
				components.add(comp);
			}

			if (!components.isEmpty()) {
				observation.setComponent(components);
			}
		} else {
			if (fObservationView.getValueAsNumber() != null
					&& (fObservationView.getValueAsString() == null
							&& fObservationView.getValueAsConcept() == null)) {
				Quantity quantity = new Quantity(fObservationView.getValueAsNumber().doubleValue());

				// Comparator if exists
				if (comparator != null) {
					quantity.setComparator(comparator);
				}

				if (unitSystemUri != null || unitCode != null || unitUnit != null) {
					quantity.setUnit(unitUnit);
					quantity.setCode(unitCode);
					quantity.setSystem(unitSystemUri);
				} else {
					if (unitSource != null) {
						quantity.setUnit(unitSource);
					}
				}

				// if (fObservationView.getUnitConcept() != null) {
				// // Unit is defined as a concept code in omop v4, then unit
				// // and code are the same in this case
				// quantity.setUnit(unitUnit);
				// quantity.setCode(unitCode);
				// quantity.setSystem(unitSystemUri);
				// }
				observation.setValue(quantity);
			} else if (fObservationView.getValueAsString() != null) {
				// Check if this is ratio.
				String valueString = fObservationView.getValueAsString();
				String[] valueStrings = valueString.split(":");
				if (valueStrings.length == 2) {
					try {
						double numerator = Double.parseDouble(valueStrings[0]);
						double denominator = Double.parseDouble(valueStrings[1]);

						Ratio ratio = new Ratio();
						ratio.setNumerator(new Quantity(numerator));
						ratio.setDenominator(new Quantity(denominator));

						observation.setValue(ratio);
					} catch (NumberFormatException nfe) {
						observation.setValue(new StringType(valueString));
					}
				} else {
					observation.setValue(new StringType(valueString));
				}
			} else if (fObservationView.getValueAsConcept() != null
					&& fObservationView.getValueAsConcept().getId() != 0L) {
				// vocabulary is a required attribute for concept, then it's
				// expected to not be null
				String valueSystem = CodeableConceptUtil.getFhirSystemNameFromOmopVocabulary(conceptService,
						fObservationView.getValueAsConcept().getVocabularyId());
				if ("None".equals(valueSystem))
					valueSystem = fObservationView.getValueAsConcept().getVocabularyId();
				Coding coding = new Coding(valueSystem, fObservationView.getValueAsConcept().getConceptCode(),
						fObservationView.getValueAsConcept().getConceptName());
				CodeableConcept valueAsConcept = new CodeableConcept();
				valueAsConcept.addCoding(coding);
				observation.setValue(valueAsConcept);
			} else {
				observation.setValue(new StringType(fObservationView.getValueSourceValue()));
			}
		}

		if (fObservationView.getRangeLow() != null) {
			SimpleQuantity low = new SimpleQuantity();
			low.setValue(fObservationView.getRangeLow().doubleValue());
			low.setSystem(unitSystemUri);
			low.setCode(unitCode);
			low.setUnit(unitUnit);
			observation.getReferenceRangeFirstRep().setLow(low);
		}
		if (fObservationView.getRangeHigh() != null) {
			SimpleQuantity high = new SimpleQuantity();
			high.setValue(fObservationView.getRangeHigh().doubleValue());
			high.setSystem(unitSystemUri);
			high.setCode(unitCode);
			high.setUnit(unitUnit);
			observation.getReferenceRangeFirstRep().setHigh(high);
		}

		Coding resourceCoding = new Coding(systemUriString, codeString, displayString);
		CodeableConcept code = new CodeableConcept();
		code.addCoding(resourceCoding);
		observation.setCode(code);

		observation.setStatus(ObservationStatus.FINAL);

		if (fObservationView.getObservationDate() != null) {
			Date myDate = createDateTime(fObservationView);
			if (myDate != null) {
				DateTimeType appliesDate = new DateTimeType(myDate);
				observation.setEffective(appliesDate);
			}
		}

		if (fObservationView.getFPerson() != null) {
			Reference personRef = new Reference(
					new IdType(PatientResourceProvider.getType(), fObservationView.getFPerson().getId()));
			personRef.setDisplay(fObservationView.getFPerson().getNameAsSingleString());
			observation.setSubject(personRef);
		}

		if (fObservationView.getVisitOccurrence() != null)
			observation.getEncounter().setReferenceElement(
					new IdType(EncounterResourceProvider.getType(), fObservationView.getVisitOccurrence().getId()));

		if (fObservationView.getObservationTypeConcept() != null) {
			if (fObservationView.getObservationTypeConcept().getId() == 44818701L
					|| fObservationView.getObservationTypeConcept().getId() == 38000280L
					|| fObservationView.getObservationTypeConcept().getId() == 38000281L) {
				// This is From physical examination.

				CodeableConcept typeConcept = new CodeableConcept();
				Coding typeCoding = new Coding("http://hl7.org/fhir/observation-category", "exam", "");
				typeConcept.addCoding(typeCoding);
				observation.addCategory(typeConcept);
			} else if (fObservationView.getObservationTypeConcept().getId() == 44818702L
					|| fObservationView.getObservationTypeConcept().getId() == 44791245L
					|| fObservationView.getObservationTypeConcept().getId() == 38000277L
					|| fObservationView.getObservationTypeConcept().getId() == 38000278L) {
				CodeableConcept typeConcept = new CodeableConcept();
				// This is Lab result
				Coding typeCoding = new Coding("http://hl7.org/fhir/observation-category", "laboratory", "");
				typeConcept.addCoding(typeCoding);
				observation.addCategory(typeConcept);
			} else if (fObservationView.getObservationTypeConcept().getId() == 45905771L) {
				CodeableConcept typeConcept = new CodeableConcept();
				// This is Lab result
				Coding typeCoding = new Coding("http://hl7.org/fhir/observation-category", "survey", "");
				typeConcept.addCoding(typeCoding);
				observation.addCategory(typeConcept);
			}
		}

		if (fObservationView.getProvider() != null) {
			Reference performerRef = new Reference(
					new IdType(PractitionerResourceProvider.getType(), fObservationView.getProvider().getId()));
			String providerName = fObservationView.getProvider().getProviderName();
			if (providerName != null && !providerName.isEmpty())
				performerRef.setDisplay(providerName);
			observation.addPerformer(performerRef);
		}

		String identifierString = fObservationView.getObservationSourceValue();
		if (identifierString != null && !identifierString.isEmpty()) {
			Identifier identifier = new Identifier();
			identifier.setValue(identifierString);
			observation.addIdentifier(identifier);
		}

		if (fObservationView.getId() > 0) {
			List<BaseEntity> methods = factRelationshipService.searchMeasurementUsingMethod(fObservationView.getId());
			if (methods != null && !methods.isEmpty()) {
				for (BaseEntity method : methods) {
					if (method instanceof Note) {
						Note note = (Note) method;
						String methodString = noteService.findById(note.getId()).getNoteText();
						CodeableConcept methodCodeable = new CodeableConcept();
						methodCodeable.setText(methodString);
						observation.setMethod(methodCodeable);
					} else if (method instanceof Concept) {
						Concept concept = (Concept) method;
						CodeableConcept methodCodeable = CodeableConceptUtil
								.getCodeableConceptFromOmopConcept(conceptService.findById(concept.getId()));
						observation.setMethod(methodCodeable);
					} else {
						logger.error("Method couldn't be retrieved. Method class type undefined");
					}
				}
			}

			List<Note> notes = factRelationshipService.searchMeasurementContainsComments(fObservationView.getId());
			String comments = "";
			for (Note note : notes) {
				comments = comments.concat(noteService.findById(note.getId()).getNoteText());
			}
			if (!comments.isEmpty()) {
				Annotation tempAnnotation = new Annotation();
				tempAnnotation.setText(comments);
				observation.addNote(tempAnnotation);
			}
		}

		return observation;
	}

	private List<Measurement> handleBloodPressure(Long omopId, Observation fhirResource) throws Exception {
		List<Measurement> retVal = new ArrayList<Measurement>();

		// This is measurement. And, fhirId is for systolic.
		// And, for update, we need to find diastolic and update that as well.
		Measurement systolicMeasurement = null;
		Measurement diastolicMeasurement = null;

		if (omopId != null) {
			Measurement measurement = measurementService.findById(omopId);
			if (measurement == null) {
				throw new FHIRException(
						"Couldn't find the matching resource, " + fhirResource.getIdElement().asStringValue());
			}

			if (measurement.getMeasurementConcept().getId() == SYSTOLIC_CONCEPT_ID) {
				systolicMeasurement = measurement;
			}

			if (measurement.getMeasurementConcept().getId() == DIASTOLIC_CONCEPT_ID) {
				diastolicMeasurement = measurement;
			}
		}

		Type systolicValue = null;
		Type diastolicValue = null;
		List<ObservationComponentComponent> components = fhirResource.getComponent();
		for (ObservationComponentComponent component : components) {
			List<Coding> codings = component.getCode().getCoding();
			for (Coding coding : codings) {
				String fhirSystem = coding.getSystem();
				String fhirCode = coding.getCode();

				if (OmopCodeableConceptMapping.LOINC.getFhirUri().equals(fhirSystem)
						&& SYSTOLIC_LOINC_CODE.equals(fhirCode)) {
					Type value = component.getValue();
					if (value != null && !value.isEmpty()) {
						systolicValue = value;
					}
				} else if (OmopCodeableConceptMapping.LOINC.getFhirUri().equals(fhirSystem)
						&& DIASTOLIC_LOINC_CODE.equals(fhirCode)) {
					Type value = component.getValue();
					if (value != null && !value.isEmpty()) {
						diastolicValue = value;
					}
				}
			}
		}

		if (systolicValue == null && diastolicValue == null) {
			throw new FHIRException("Either systolic or diastolic needs to be available in component");
		}

		Long fhirSubjectId = fhirResource.getSubject().getReferenceElement().getIdPartAsLong();
		Long omopPersonId = IdMapping.getOMOPfromFHIR(fhirSubjectId, PatientResourceProvider.getType());
		FPerson tPerson = new FPerson();
		tPerson.setId(omopPersonId);

		if (omopId == null) {
			// Create.
			if (systolicMeasurement == null && systolicValue != null) {
				systolicMeasurement = new Measurement();
				systolicMeasurement.setFPerson(tPerson);

			}
			if (diastolicMeasurement == null && diastolicValue != null) {
				diastolicMeasurement = new Measurement();
				diastolicMeasurement.setFPerson(tPerson);
			}

		} else {
			// Update
			// Sanity check. The entry found from identifier should have
			// matching id.
			if (systolicMeasurement != null && systolicMeasurement.getId() != omopId) {
				throw new FHIRException("The systolic measurement has incorrect id or identifier.");
			} else {
				// Now check if we have disastoic measurement.
				if (diastolicMeasurement != null && diastolicMeasurement.getId() != omopId) {
					// OK, originally, we had no systolic. Do the sanity
					// check
					// with diastolic measurement.
					throw new FHIRException("The diastolic measurement has incorrect id or identifier.");
				}
			}

			// Update. We use systolic measurement id as our prime id. However,
			// sometimes, there is a chance that only one is available.
			// If systolic is not available, diastolic will use the id.
			// Thus, we first need to check if
			if (systolicMeasurement == null && systolicValue != null) {
				systolicMeasurement = measurementService.findById(omopId);
				systolicMeasurement.setFPerson(tPerson);
			}
			if (diastolicMeasurement == null && diastolicValue != null) {
				// We have diastolic value. But, we cannot use omopId here.
				//
				diastolicMeasurement = measurementService.findById(omopId);
				diastolicMeasurement.setFPerson(tPerson);
			}

			if (systolicMeasurement == null && diastolicMeasurement == null) {
				throw new FHIRException("Failed to get either systolic or diastolic measurement for update.");
			}
		}

		// We look at component coding.
		if (systolicMeasurement != null) {
			Concept codeConcept = new Concept();
			codeConcept.setId(SYSTOLIC_CONCEPT_ID);
			systolicMeasurement.setMeasurementConcept(codeConcept);

			if (systolicValue instanceof Quantity) {
				systolicMeasurement.setValueAsNumber(((Quantity) systolicValue).getValue().doubleValue());

				// Save the unit in the unit source column to save the
				// source
				// value.
				String unitString = ((Quantity) systolicValue).getUnit();
				systolicMeasurement.setUnitSourceValue(unitString);

				String unitSystem = ((Quantity) systolicValue).getSystem();
				String unitCode = ((Quantity) systolicValue).getCode();
				String omopVocabularyId = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
						unitSystem);
				if (omopVocabularyId != null) {
					Concept unitConcept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService,
							omopVocabularyId, unitCode);
					systolicMeasurement.setUnitConcept(unitConcept);
				}
				systolicMeasurement.setValueSourceValue(((Quantity) systolicValue).getValue().toString());
			} else if (systolicValue instanceof CodeableConcept) {
				Concept systolicValueConcept = CodeableConceptUtil.searchConcept(conceptService,
						(CodeableConcept) systolicValue);
				systolicMeasurement.setValueAsConcept(systolicValueConcept);
				systolicMeasurement.setValueSourceValue(((CodeableConcept) systolicValue).toString());
			} else
				throw new FHIRException("Systolic measurement should be either Quantity or CodeableConcept");
		}

		if (diastolicMeasurement != null) {
			Concept codeConcept = new Concept();
			codeConcept.setId(DIASTOLIC_CONCEPT_ID);
			diastolicMeasurement.setMeasurementConcept(codeConcept);

			if (diastolicValue instanceof Quantity) {
				diastolicMeasurement.setValueAsNumber(((Quantity) diastolicValue).getValue().doubleValue());

				// Save the unit in the unit source column to save the
				// source
				// value.
				String unitString = ((Quantity) diastolicValue).getUnit();
				diastolicMeasurement.setUnitSourceValue(unitString);

				String unitSystem = ((Quantity) diastolicValue).getSystem();
				String unitCode = ((Quantity) diastolicValue).getCode();
				String omopVocabularyId = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
						unitSystem);
				if (omopVocabularyId != null) {
					Concept unitConcept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService,
							omopVocabularyId, unitCode);
					diastolicMeasurement.setUnitConcept(unitConcept);
				}
				diastolicMeasurement.setValueSourceValue(((Quantity) diastolicValue).getValue().toString());
			} else if (diastolicValue instanceof CodeableConcept) {
				Concept diastolicValueConcept = CodeableConceptUtil.searchConcept(conceptService,
						(CodeableConcept) diastolicValue);
				diastolicMeasurement.setValueAsConcept(diastolicValueConcept);
				diastolicMeasurement.setValueSourceValue(((CodeableConcept) diastolicValue).toString());
			} else
				throw new FHIRException("Diastolic measurement should be either Quantity or CodeableConcept");
		}

		// Get low and high range if available.
		// Components have two value. From the range list, we should
		// find the matching range. If exists, we can update measurement
		// entity class.
		List<ObservationReferenceRangeComponent> ranges = fhirResource.getReferenceRange();
		List<Coding> codings;

		// For BP, we should walk through these range references and
		// find a right matching one to put our measurement entries.
		for (ObservationReferenceRangeComponent range : ranges) {
			if (range.isEmpty())
				continue;

			// Get high and low values.
			Quantity highQtyValue = range.getHigh();
			Quantity lowQtyValue = range.getLow();
			if (highQtyValue.isEmpty() && lowQtyValue.isEmpty()) {
				// We need these values. If these are empty.
				// We have no reason to look at the appliesTo data.
				// Skip to next reference.
				continue;
			}

			// Check the all the included FHIR concept codes.
			List<CodeableConcept> rangeConceptCodes = range.getAppliesTo();
			for (CodeableConcept rangeConceptCode : rangeConceptCodes) {
				codings = rangeConceptCode.getCoding();
				for (Coding coding : codings) {
					try {
						if (OmopCodeableConceptMapping.LOINC.fhirUri.equals(coding.getSystem())) {
							if (SYSTOLIC_LOINC_CODE.equals(coding.getCode())) {
								// This applies to Systolic blood pressure.
								if (systolicMeasurement != null) {
									if (!highQtyValue.isEmpty()) {
										systolicMeasurement.setRangeHigh(highQtyValue.getValue().doubleValue());
									}
									if (!lowQtyValue.isEmpty()) {
										systolicMeasurement.setRangeLow(lowQtyValue.getValue().doubleValue());
									}
									break;
								} else {
									throw new FHIRException(
											"Systolic value is not available. But, range for systolic is provided. BP data inconsistent");
								}
							} else if (DIASTOLIC_LOINC_CODE.equals(coding.getCode())) {
								// This applies to Diastolic blood pressure.
								if (diastolicMeasurement != null) {
									if (!highQtyValue.isEmpty()) {
										diastolicMeasurement.setRangeHigh(highQtyValue.getValue().doubleValue());
									}
									if (!lowQtyValue.isEmpty()) {
										diastolicMeasurement.setRangeLow(lowQtyValue.getValue().doubleValue());
									}
									break;
								} else {
									throw new FHIRException(
											"Diastolic value is not available. But, range for diastolic is provided. BP data inconsistent");
								}
							}
						}
					} catch (FHIRException e) {
						e.printStackTrace();
					}
				}
			}
		}

		if (fhirResource.getEffective() instanceof DateTimeType) {
			Date date = ((DateTimeType) fhirResource.getEffective()).getValue();
			if (systolicMeasurement != null) {
				systolicMeasurement.setMeasurementDate(date);
				systolicMeasurement.setMeasurementDateTime(date);
			}
			if (diastolicMeasurement != null) {
				diastolicMeasurement.setMeasurementDate(date);
				diastolicMeasurement.setMeasurementDateTime(date);
			}
		} else if (fhirResource.getEffective() instanceof Period) {
			Date startDate = ((Period) fhirResource.getEffective()).getStart();
			if (startDate != null) {
				if (systolicMeasurement != null) {
					systolicMeasurement.setMeasurementDate(startDate);
					systolicMeasurement.setMeasurementDateTime(startDate);
				}
			}
			if (startDate != null) {
				if (diastolicMeasurement != null) {
					diastolicMeasurement.setMeasurementDate(startDate);
					diastolicMeasurement.setMeasurementDateTime(startDate);
				}
			}
		}

		/* Set visit occurrence */
		Reference contextReference = fhirResource.getEncounter();
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
					logger.warn("The Encounter (" + contextReference.getReference() + ") context couldn't be found.");
				} else {
					if (systolicMeasurement != null) {
						systolicMeasurement.setVisitOccurrence(visitOccurrence);
					}
					if (diastolicMeasurement != null) {
						diastolicMeasurement.setVisitOccurrence(visitOccurrence);
					}
				}
			} else {
				// Episode of Care context.
				// TODO: Do we have a mapping for the Episode of Care??
			}
		}

		if (systolicMeasurement != null) {
			retVal.add(systolicMeasurement);
		}
		if (diastolicMeasurement != null) {
			retVal.add(diastolicMeasurement);
		}

		return retVal;
	}

	@Override
	public Long removeByFhirId(IdType fhirId) throws Exception {
		Long id_long_part = fhirId.getIdPartAsLong();
		Long myId = IdMapping.getOMOPfromFHIR(id_long_part, getMyFhirResourceType());
		if (myId < 0) {
			// This is observation table.
			return observationService.removeById(myId);
		} else {
			return measurementService.removeById(myId);
		}
	}

	@Override
	public String constructOrderParams(SortSpec theSort) {
		if (theSort == null)
			return "id ASC";

		String direction;

		if (theSort.getOrder() != null)
			direction = theSort.getOrder().toString();
		else
			direction = "ASC";

		String orderParam = new String();

		if (theSort.getParamName().equals(Observation.SP_CODE)) {
			orderParam = "observationConcept.concept_code " + direction;
		} else if (theSort.getParamName().equals(Observation.SP_DATE)) {
			orderParam = "observationDate " + direction;
		} else if (theSort.getParamName().equals(Observation.SP_PATIENT)
				|| theSort.getParamName().equals(Observation.SP_SUBJECT)) {
			orderParam = "fPerson " + direction;
		} else {
			orderParam = "id " + direction;
		}

		String orderParams = orderParam;

		if (theSort.getChain() != null) {
			orderParams = orderParams.concat("," + constructOrderParams(theSort.getChain()));
		}

		return orderParams;
	}

	public List<Measurement> constructOmopMeasurement(Long omopId, Observation fhirResource, String system,
			String codeString) throws Exception {
		List<Measurement> retVal = new ArrayList<Measurement>();

		// If we have BP information, we handle this separately.
		// OMOP cannot handle multiple entries. So, we do not have
		// this code in our concept table.
		if (system != null && system.equals(OmopCodeableConceptMapping.LOINC.getFhirUri())
				&& codeString.equals(BP_SYSTOLIC_DIASTOLIC_CODE)) {
			// OK, we have BP systolic & diastolic. Handle this separately.
			// If successful, we will end and return.

			return handleBloodPressure(omopId, fhirResource);
		}

		Measurement measurement = null;
		if (omopId == null) {
			// This is CREATE.
			measurement = new Measurement();
		} else {
			// This is UPDATE.
			measurement = measurementService.findById(omopId);
			if (measurement == null) {
				// We have a request to add with a non-existing id.
				// In this case, we create this with this ID.
				// throw new FHIRException("We have no matching FHIR Observation (Observation)
				// to update.");
				measurement = new Measurement();
				measurement.setId(omopId);
			}
		}

		String idString = fhirResource.getSubject().getReferenceElement().getIdPart();

		try {
			Long fhirSubjectId = Long.parseLong(idString);
			Long omopPersonId = IdMapping.getOMOPfromFHIR(fhirSubjectId, PatientResourceProvider.getType());

			FPerson tPerson = new FPerson();
			tPerson.setId(omopPersonId);
			measurement.setFPerson(tPerson);
		} catch (Exception e) {
			// We have non-numeric id for the person. This should be handled later by
			// caller.
			e.printStackTrace();
		}

		// Get code system information.
		CodeableConcept code = fhirResource.getCode();
		String valueSourceString = "";
		Concept concept = fhirCode2OmopConcept(conceptService, code, valueSourceString);
		valueSourceString = getReturnStringValue();
		measurement.setMeasurementConcept(concept);
		measurement.setMeasurementSourceConcept(concept);

		// Set this in the source column
		if (concept == null || concept.getIdAsLong() == 0L) {
			// measurement.setValueSourceValue(valueSourceString.substring(0,
			// valueSourceString.length()>50?49:valueSourceString.length()));
			measurement.setMeasurementSourceValue(valueSourceString);
		}

		/* Set the value of the observation */
		Type valueType = fhirResource.getValue();
		List<Coding> codings;
		if (valueType instanceof Quantity) {
			BigDecimal qtyValue = ((Quantity) valueType).getValue();
			if (qtyValue != null) {
				measurement.setValueAsNumber(qtyValue.doubleValue());
				measurement.setValueSourceValue(String.valueOf(qtyValue));
			}

			// For operator, check comparator.
			QuantityComparator comparator = ((Quantity) valueType).getComparator();
			if (comparator != null) {
				if (comparator == QuantityComparator.LESS_THAN) {
					Concept operatorConcept = CodeableConceptUtil.getOmopConceptWithOmopNameAndDomain(conceptService, "<", "Meas Value Operator");
					if (operatorConcept != null) {
						measurement.setOperationConcept(operatorConcept);
					}
				} else if (comparator == QuantityComparator.LESS_OR_EQUAL) {
					Concept operatorConcept = CodeableConceptUtil.getOmopConceptWithOmopNameAndDomain(conceptService, "<=", "Meas Value Operator");
					if (operatorConcept != null) {
						measurement.setOperationConcept(operatorConcept);
					}
				} else if (comparator == QuantityComparator.GREATER_THAN) {
					Concept operatorConcept = CodeableConceptUtil.getOmopConceptWithOmopNameAndDomain(conceptService, ">", "Meas Value Operator");
					if (operatorConcept != null) {
						measurement.setOperationConcept(operatorConcept);
					}
				} else if (comparator == QuantityComparator.GREATER_OR_EQUAL) {
					Concept operatorConcept = CodeableConceptUtil.getOmopConceptWithOmopNameAndDomain(conceptService, ">=", "Meas Value Operator");
					if (operatorConcept != null) {
						measurement.setOperationConcept(operatorConcept);
					}
				}
			}

			// For unit, OMOP need unit concept
			String unitCode = ((Quantity) valueType).getCode();
			String unitSystem = ((Quantity) valueType).getSystem();

			String omopVocabulary = null;
			concept = null;
			if (unitCode != null && !unitCode.isEmpty()) {
				if (unitSystem == null || unitSystem.isEmpty()) {
					// If system is empty, then we check UCUM for the unit.
					omopVocabulary = OmopCodeableConceptMapping.UCUM.getOmopVocabulary();
				} else {
					omopVocabulary = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
							unitSystem);
				}
				concept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService, omopVocabulary,
						unitCode);
			}

			// Save the unit in the unit source column to save the source
			// value.
			String unitString = ((Quantity) valueType).getUnit();
			if (unitString != null && !unitString.isEmpty()) {
				measurement.setUnitSourceValue(unitString);
			}

			if (concept != null) {
				// If we found the concept for unit, use it. Otherwise,
				// leave it empty.
				// We still have this in the unit source column.
				measurement.setUnitConcept(concept);
			}

		} else if (valueType instanceof CodeableConcept) {
			// We have coeable concept value. Get System and Value.
			// FHIR allows one value[x].
			codings = ((CodeableConcept) valueType).getCoding();
			concept = null;
			for (Coding coding : codings) {
				String fhirSystem = coding.getSystem();
				String fhirCode = coding.getCode();

				if (fhirSystem == null || fhirSystem.isEmpty() || fhirCode == null || fhirCode.isEmpty()) {
					continue;
				}

				String omopVocabulary = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
						fhirSystem);
				concept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService, omopVocabulary,
						fhirCode);

				if (concept == null) {
					throw new FHIRException(
							"We couldn't map the codeable concept value - " + fhirSystem + ":" + fhirCode);
				}
				break;
			}
			if (concept == null) {
				throw new FHIRException("We couldn't find a concept to map the codeable concept value.");
			}

			measurement.setValueAsConcept(concept);
		} else if (valueType instanceof StringType) {
			String valueString = ((StringType) valueType).getValue();
			// Measurement table in OMOPv5 does not have a column for string value.
			// If the value is what we can recognize as a concept code, we will use it.
			if ("none detected".equalsIgnoreCase(valueString)) {
				measurement.setValueAsConcept(conceptService.findById(45878003L));
			} else if ("not detected".equalsIgnoreCase(valueString)) {
				measurement.setValueAsConcept(conceptService.findById(45880296L));
			} else if ("detected".equalsIgnoreCase(valueString)) {
				measurement.setValueAsConcept(conceptService.findById(45877985L));
			}

			measurement.setValueSourceValue(valueString);
		}

		// Get low and high range if available. This is only applicable to
		// measurement.
		if (!fhirResource.getReferenceRangeFirstRep().isEmpty()) {
			Quantity high = fhirResource.getReferenceRangeFirstRep().getHigh();
			if (!high.isEmpty()) {
				measurement.setRangeHigh(high.getValue().doubleValue());

				if (measurement.getUnitConcept() == null) {
					Concept rangeUnitConcept = null;
					if (high.getCode() != null && !high.getCode().isEmpty()) {
						String omopVocabulary;
						if (high.getSystem() == null || high.getSystem().isEmpty()) {
							// If system is empty, then we check UCUM for the unit.
							omopVocabulary = OmopCodeableConceptMapping.UCUM.getOmopVocabulary();
						} else {
							omopVocabulary = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
									high.getSystem());
						}
						rangeUnitConcept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService,
								omopVocabulary, high.getCode());
					}

					if (rangeUnitConcept != null) {
						measurement.setUnitConcept(rangeUnitConcept);
					}
				}
			}
			Quantity low = fhirResource.getReferenceRangeFirstRep().getLow();
			if (!low.isEmpty()) {
				measurement.setRangeLow(low.getValue().doubleValue());

				if (measurement.getUnitConcept() == null) {
					Concept rangeUnitConcept = null;
					if (low.getCode() != null && !low.getCode().isEmpty()) {
						String omopVocabulary;
						if (low.getSystem() == null || low.getSystem().isEmpty()) {
							// If system is empty, then we check UCUM for the unit.
							omopVocabulary = OmopCodeableConceptMapping.UCUM.getOmopVocabulary();
						} else {
							omopVocabulary = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
									low.getSystem());
						}
						rangeUnitConcept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService,
								omopVocabulary, low.getCode());
					}

					if (rangeUnitConcept != null) {
						measurement.setUnitConcept(rangeUnitConcept);
					}
				}
			}
		}

		if (fhirResource.getEffective() instanceof DateTimeType) {
			Date date = ((DateTimeType) fhirResource.getEffective()).getValue();
			measurement.setMeasurementDate(date);
			measurement.setMeasurementDateTime(date);
		} else if (fhirResource.getEffective() instanceof Period) {
			Date startDate = ((Period) fhirResource.getEffective()).getStart();
			if (startDate != null) {
				measurement.setMeasurementDate(startDate);
				measurement.setMeasurementDateTime(startDate);
			}
		} else {
			// datetime is required field.
			measurement.setMeasurementDateTime(new Date(0));
		}

		/* Set visit occurrence */
		Reference contextReference = fhirResource.getEncounter();
		VisitOccurrence visitOccurrence = fhirContext2OmopVisitOccurrence(visitOccurrenceService, contextReference);
		if (visitOccurrence != null) {
			measurement.setVisitOccurrence(visitOccurrence);
		}

		retVal.add(measurement);

		return retVal;

	}

	public edu.gatech.chai.omopv5.model.entity.Observation constructOmopObservation(Long omopId,
			Observation fhirResource) throws Exception {
		edu.gatech.chai.omopv5.model.entity.Observation observation = null;
		if (omopId == null) {
			// This is CREATE.
			observation = new edu.gatech.chai.omopv5.model.entity.Observation();
		} else {
			observation = observationService.findById(omopId);
			if (observation == null) {
				// We have no observation to update.
				throw new FHIRException("We have no matching FHIR Observation (Observation) to update.");
			}
		}

		Long fhirSubjectId = fhirResource.getSubject().getReferenceElement().getIdPartAsLong();
		Long omopPersonId = IdMapping.getOMOPfromFHIR(fhirSubjectId, PatientResourceProvider.getType());

		FPerson tPerson = new FPerson();
		tPerson.setId(omopPersonId);
		observation.setFPerson(tPerson);

		CodeableConcept code = fhirResource.getCode();

		// code should NOT be null as this is required field.
		// And, validation should check this.
		List<Coding> codings = code.getCoding();
		Coding codingFound = null;
		Coding codingSecondChoice = null;
		String OmopSystem = null;
		String obsSourceString = "";
		for (Coding coding : codings) {
			String fhirSystemUri = coding.getSystem();

			if (code.getText() != null && !code.getText().isEmpty()) {
				obsSourceString = code.getText();
			} else {
				obsSourceString = coding.getSystem() + "^" + coding.getCode() + "^" + coding.getDisplay();
				obsSourceString = obsSourceString.trim();
			}

			if (OmopCodeableConceptMapping.LOINC.getFhirUri().equals(fhirSystemUri)) {
				// Found the code we want, which is LOINC
				codingFound = coding;
				break;
			} else {
				// See if we can handle this coding.
				try {
					if (fhirSystemUri != null && !fhirSystemUri.isEmpty()) {
						OmopSystem = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
								fhirSystemUri);
						if (!"None".equals(OmopSystem)) {
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
			concept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService, OmopSystem,
					codingSecondChoice.getCode());
			if (concept == null) {
				// TODO: try with only a code. Need to fix this
				concept = CodeableConceptUtil.getOmopConceptWithOmopCode(conceptService, codingSecondChoice.getCode());
			}
		}

		if (concept == null) {
			concept = conceptService.findById(0L);
		}

		observation.setObservationConcept(concept);
		observation.setObservationSourceConcept(concept);

		// observation.setObservationSourceValue(valueSourceString.substring(0,
		// valueSourceString.length()>50?49:valueSourceString.length()));
		observation.setObservationSourceValue(obsSourceString);

		if (concept != null)
			observation.setObservationSourceConcept(concept);

		/* Set the value of the observation */
		Type valueType = fhirResource.getValue();
		if (valueType instanceof Quantity) {
			observation.setValueAsNumber(((Quantity) valueType).getValue().doubleValue());

			// For unit, OMOP need unit concept
			String unitCode = ((Quantity) valueType).getCode();
			String unitSystem = ((Quantity) valueType).getSystem();

			String omopVocabulary = null;
			concept = null;
			if (unitCode != null && !unitCode.isEmpty()) {
				if (unitSystem == null || unitSystem.isEmpty()) {
					// If system is empty, then we check UCUM for the unit.
					omopVocabulary = OmopCodeableConceptMapping.UCUM.getOmopVocabulary();
				} else {
					try {
						omopVocabulary = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
								unitSystem);
					} catch (FHIRException e) {
						e.printStackTrace();
					}
				}
				concept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService, omopVocabulary,
						unitCode);
			}

			// Save the unit in the unit source column to save the source value.
			String unitString = ((Quantity) valueType).getUnit();
			observation.setUnitSourceValue(unitString);

			if (concept != null) {
				// If we found the concept for unit, use it. Otherwise, leave it
				// empty.
				// We still have this in the unit source column.
				observation.setUnitConcept(concept);
			}

		} else if (valueType instanceof CodeableConcept) {
			// We have coeable concept value. Get System and Value.
			// FHIR allows one value[x].
			codings = ((CodeableConcept) valueType).getCoding();
			concept = null;
			String stringValue = "No Value(s)";
			for (Coding coding : codings) {
				String fhirSystem = coding.getSystem();
				String fhirCode = coding.getCode();

				if (fhirSystem == null || fhirSystem.isEmpty() || fhirCode == null || fhirCode.isEmpty()) {
					continue;
				}

				stringValue = fhirSystem + "^" + fhirCode + "^" + coding.getDisplay();
				String omopVocabulary = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
						fhirSystem);
				concept = CodeableConceptUtil.getOmopConceptWithOmopVacabIdAndCode(conceptService, omopVocabulary,
						fhirCode);

				if (concept == null) {
					logger.debug("We couldn't map the codeable concept value - " + fhirSystem + ":" + fhirCode);
				} else {
					break;
				}
			}
			if (concept == null) {
				observation.setValueAsString(stringValue);
				logger.warn("We couldn't find a concept to map the codeable concept value.");
			} else {
				observation.setValueAsConcept(concept);
				observation.setValueSourceValue(stringValue);
			}
		} else if (valueType instanceof StringType) {
			String valueString = ((StringType) valueType).asStringValue();
			if (valueString != null && !valueString.isEmpty()) {
				String[] valueStringData = valueString.split("\\^");
				if (valueStringData.length > 0) {
					for (int i = 0; i < valueStringData.length; i++) {
						String omopVocId = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
								valueStringData[i]);
						if (!"None".equals(omopVocId)) {
							valueString = valueString.replace(valueStringData[i], omopVocId);
						}
					}
				}

				// // We need to escape single quote - moved to sql layer
				// valueString = StringEscapeUtils.escapeSql(valueString);
				observation.setValueAsString(valueString);
			}
		} else if (valueType instanceof Ratio) {
			Ratio ratio = (Ratio) valueType;
			Quantity numerator = ratio.getNumerator();
			Quantity denominator = ratio.getDenominator();
			if (numerator == null || denominator == null) {
				throw new FHIRException("For Ratio, both numerator and denominator must not be null.");
			}
			BigDecimal numeratorValue = ((Ratio) valueType).getNumerator().getValue();
			BigDecimal denominatorValue = ((Ratio) valueType).getDenominator().getValue();

			String valueNumeratorString;
			if ((numeratorValue.doubleValue() / numeratorValue.intValue()) == 1) {
				valueNumeratorString = String.valueOf(numeratorValue.intValue());
			} else {
				valueNumeratorString = String.valueOf(numeratorValue);
			}

			String valueDenominatorString;
			if ((denominatorValue.doubleValue() / denominatorValue.intValue()) == 1) {
				valueDenominatorString = String.valueOf(denominatorValue.intValue());
			} else {
				valueDenominatorString = String.valueOf(denominatorValue);
			}

			String valueString = valueNumeratorString + ":" + valueDenominatorString;
			observation.setValueAsString(valueString);
		}

		if (fhirResource.getEffective() instanceof DateTimeType) {
			Date date = ((DateTimeType) fhirResource.getEffective()).getValue();
			observation.setObservationDate(date);
			observation.setObservationDateTime(date);
		} else if (fhirResource.getEffective() instanceof Period) {
			Date startDate = ((Period) fhirResource.getEffective()).getStart();
			if (startDate != null) {
				observation.setObservationDate(startDate);
				observation.setObservationDateTime(startDate);
			}
		} else {
			observation.setObservationDate(new Date(0));
		}

		/* Set visit occurrence */
		Reference contextReference = fhirResource.getEncounter();
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
					logger.warn("The Encounter (" + contextReference.getReference() + ") context couldn't be found.");
				} else {
					observation.setVisitOccurrence(visitOccurrence);
				}
			} else {
				// Episode of Care context.
				// TODO: Do we have a mapping for the Episode of Care??
			}
		}

		// TODO: Write 0 here for now. This is a new data element in OMOP in v6.0
		observation.setObsEventFieldConcept(new Concept(0L));

		return observation;
	}

	private boolean isMeasurementByValuetype(Observation fhirResource) {
		Type value = fhirResource.getValue();
		return (value instanceof Quantity);
	}

	private boolean isObservationByValueType(Observation fhirResource) {
		Type value = fhirResource.getValue();
		return (value instanceof Ratio);
	}

	public Map<String, Object> constructOmopMeasurementObservation(Long omopId, Observation fhirResource,
			boolean isSurvey) throws Exception {
		/*
		 * returns a map that contains either OMOP measurement entity classes or
		 * OMOP observation entity. The return map consists as follows,
		 * "type": "Observation" or "Measurement"
		 * "entity": omopObservation or List<Measurement>
		 */
		Map<String, Object> retVal = new HashMap<String, Object>();

		List<Measurement> measurements = null;
		edu.gatech.chai.omopv5.model.entity.Observation observation = null;

		// Observation.coding is required in mapping to OMOP.
		// However, if coding is not available, then mark it as 0L instead of
		// throwing error.
		if (fhirResource.getCode().getCoding().isEmpty()) {
			fhirResource.getCode().addCoding(new Coding("urn:gtri:error-code", "code-missing", "code is missing"));
		}
		for (Coding coding : fhirResource.getCode().getCoding()) {
			String code = coding.getCode();
			if (code == null) {
				code = "";
			}
			String system = coding.getSystem();
			if (system == null) {
				system = "";
			}

			List<Concept> conceptForCodes = conceptService.searchByColumnString("conceptCode", code);
			if (conceptForCodes.isEmpty()) {
				// we have no matching code. Put no matching code.
				conceptForCodes.add(conceptService.findById(0L));
			}

			for (Concept conceptForCode : conceptForCodes) {
				String domain = conceptForCode.getDomainId();
				String systemName = conceptForCode.getVocabularyId();
				if (!isSurvey && !isObservationByValueType(fhirResource) && ((domain.equalsIgnoreCase("measurement")
						&& systemName.equalsIgnoreCase(
								CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService, system)))
						|| isMeasurementByValuetype(fhirResource))) {
					/*
					 * check if we already have this entry by looking up the
					 * fhir_resource_deduplicate table.
					 * We use identifier to check.
					 */
					Long omopIdFound = findOMOPEntity(fhirResource.getIdentifier(), "Measurement");
					if (omopIdFound != 0L) {
						omopId = omopIdFound;
					}

					measurements = constructOmopMeasurement(omopId, fhirResource, system, code);
					if (measurements != null && !measurements.isEmpty()) {
						retVal.put("type", "Measurement");
						retVal.put("entity", measurements);
						return retVal;
					}
				} else {
					/*
					 * check if we already have this entry by looking up the
					 * fhir_resource_deduplicate table.
					 * We use identifier to check.
					 */

					// If this is a survey, we handle differently for a match finding.
					if (isSurvey) {
						// We may have a different Identifier.
					} else {
						Long omopIdFound = findOMOPEntity(fhirResource.getIdentifier(), "Observation");
						if (omopIdFound != 0L) {
							omopId = omopIdFound;
						}
					}

					observation = constructOmopObservation(omopId, fhirResource);
					if (observation != null) {
						retVal.put("type", "Observation");
						retVal.put("entity", observation);
						return retVal;
					}
				}
			}
		}

		// Error... we don't know how to handle this coding...
		// TODO: add some exception or notification of the error here.
		String errMsg = "";
		for (Coding coding : fhirResource.getCode().getCoding()) {
			errMsg += "[" + coding.getSystem() + ", " + coding.getCode() + ", " + coding.getDisplay() + "] ";
		}
		logger.error("we don't know how to handle this coding for this observation with code = : " + errMsg);
		return null;
	}

	public void validation(Observation fhirResource, IdType fhirId) throws FHIRException {
		Reference subjectReference = fhirResource.getSubject();
		if (subjectReference == null || subjectReference.isEmpty()) {
			throw new FHIRException("We requres subject to contain a Patient");
		}
		if (subjectReference.hasReferenceElement()) {
			if (!PatientResourceProvider.getType()
					.equalsIgnoreCase(subjectReference.getReferenceElement().getResourceType())) {
				throw new FHIRException("We only support " + PatientResourceProvider.getType()
						+ " for subject. But provided [" + subjectReference.getReferenceElement().getResourceType()
						+ "]");
			}

			Long fhirSubjectId = subjectReference.getReferenceElement().getIdPartAsLong();
			Long omopPersonId = IdMapping.getOMOPfromFHIR(fhirSubjectId, PatientResourceProvider.getType());
			if (omopPersonId == null) {
				throw new FHIRException("We couldn't find the patient in the Subject");
			}
		}
	}

	@Override
	public Long toDbase(Observation fhirResource, IdType fhirId) throws Exception {
		Long fhirIdLong = null;
		Long omopId = null;
		if (fhirId != null) {
			fhirIdLong = fhirId.getIdPartAsLong();
			omopId = IdMapping.getOMOPfromFHIR(fhirIdLong, ObservationResourceProvider.getType());
			if (omopId < 0) {
				// This is observation table data in OMOP.
				omopId = -omopId; // convert to positive number;
			}
		}

		validation(fhirResource, fhirId);

		List<Measurement> measurements = null;
		edu.gatech.chai.omopv5.model.entity.Observation observation = null;

		// We may need an additional mapping to fact_relationship table for some FHIR
		// Observation
		// data elements such as method. Since this implementation is customized for
		// syphilis registry, we need to check if this observation is for survey. If so,
		// we need to
		// get focus and note text properly handled.
		boolean isSurvey = false;
		Long typeConceptId = 0L;

		List<CodeableConcept> categories = fhirResource.getCategory();
		for (CodeableConcept category : categories) {
			List<Coding> codings = category.getCoding();
			for (Coding coding : codings) {
				// check if this is a survey
				if ("survey".equals(coding.getCode())) {
					isSurvey = true;
				}

				// check if we can get type from the category
				String fhirSystem = coding.getSystem();
				String fhirCode = coding.getCode();
				if (fhirSystem != null && !fhirSystem.isEmpty() && fhirCode != null && !fhirCode.isEmpty()) {
					try {
						typeConceptId = OmopConceptMapping.omopForObservationCategoryCode(fhirCode);
					} catch (FHIRException e) {
						e.printStackTrace();
					}
				}
			}
		}

		Concept typeConcept = new Concept();
		typeConcept.setId(typeConceptId);

		// For deduplication, we need to have identifer. Check if we have it.
		// If not, we create the one that will be unique and consitent.
		if (fhirResource.getIdentifier().isEmpty()) {
			String identifierString = fhirResource.getSubject().getReferenceElement().getIdPart();
			if (fhirResource.getEffective() instanceof DateTimeType) {
				Date date = ((DateTimeType) fhirResource.getEffective()).getValue();
				identifierString += "-" + date.getTime();
			} else if (fhirResource.getEffective() instanceof Period) {
				Date startDate = ((Period) fhirResource.getEffective()).getStart();
				Date endDate = ((Period) fhirResource.getEffective()).getEnd();
				identifierString += "-" + startDate.getTime() + "-" + endDate.getTime();
			} else {
				// datetime is required field.
				identifierString += "-" + (new Date(0)).getTime();
			}

			CodeableConcept cc = fhirResource.getCode();
			for (Coding coding : cc.getCoding()) {
				identifierString += "-" + coding.getSystem() + "-" + coding.getCode();
			}

			Identifier newId = new Identifier();
			newId.setSystem("urn:registry:system");
			newId.setValue(identifierString);
			fhirResource.addIdentifier(newId);
		}

		Map<String, Object> entityMap = constructOmopMeasurementObservation(omopId, fhirResource, isSurvey);
		if (entityMap == null) {
			return null;
		}

		Long retId = null;

		Date date = null;
		FPerson fPerson = null;
		Long domainConceptId = null;

		String commentText = "";
		List<Annotation> notes = fhirResource.getNote();
		for (Annotation note : notes) {
			String text2use = note.getText();
			if (isSurvey) {
				String[] structuredSourceData = text2use.split("\\^");
				if (structuredSourceData.length > 0) {
					for (int i = 0; i < structuredSourceData.length; i++) {
						String omopVocId = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
								structuredSourceData[i]);
						if (!"None".equals(omopVocId)) {
							text2use = text2use.replace(structuredSourceData[i], omopVocId);
						}
					}
				}
			}
			commentText += text2use;
		}

		boolean isOMOPObservation = false;
		if (entityMap != null && ((String) entityMap.get("type")).equalsIgnoreCase("measurement")) {
			measurements = (List<Measurement>) entityMap.get("entity");

			Long retvalSystolic = null;
			Long retvalDiastolic = null;

			domainConceptId = 21L;

			for (Measurement m : measurements) {
				if (m != null) {
					m.setMeasurementTypeConcept(typeConcept);
					if (m.getId() != null) {
						retId = measurementService.update(m).getId();
					} else {
						retId = measurementService.create(m).getId();

						// Create a deduplicate entry
						createDuplicateEntry(fhirResource.getIdentifier(), "Measurement", retId);
					}
					if (m.getMeasurementConcept().getId() == OmopObservation.SYSTOLIC_CONCEPT_ID) {
						retvalSystolic = retId;
					} else if (m.getMeasurementConcept().getId() == OmopObservation.DIASTOLIC_CONCEPT_ID) {
						retvalDiastolic = retId;
					}

					date = m.getMeasurementDate();
					fPerson = m.getFPerson();

					// we may have note in the FHIR observation. Put them in the note and create 
					// this in the fact relationship table. 
					if (commentText != null && !commentText.isEmpty() && !isSurvey && !ExtensionUtil.isInUserSpace(m.getMeasurementConcept().getId())) {
						createFactRelationship(date, fPerson, commentText, domainConceptId, 26L, 44818721L, retId, null);
					}
				}
			}

			// Ok, done. now we return.
			if (retvalSystolic != null)
				retId = retvalSystolic;
			else if (retvalDiastolic != null)
				retId = retvalDiastolic;
		} else {
			isOMOPObservation = true;
			observation = (edu.gatech.chai.omopv5.model.entity.Observation) entityMap.get("entity");
			if (isSurvey || ExtensionUtil.isInUserSpace(observation.getObservationConcept().getId())) {
				observation.setObservationSourceValue(commentText);

				// We may already have a question for this. Check if we have this question
				String sql = "SELECT observation.observation_id AS observation_observation_id "
						+ "FROM " + schemaConfig.getDataSchema() + ".observation observation "
						+ "WHERE observation.observation_concept_id = @obs_concept AND "
						+ "observation.value_as_string = @value AND "
						+ "observation.observation_source_value = @valueSource AND "
						+ "observation.person_id = @person";
				// + "observation.person_id = @person AND "
				// + "observation.observation_date = @obs_date";

				List<String> parameterList = new ArrayList<String>();
				parameterList.add("obs_concept");
				parameterList.add("value");
				parameterList.add("valueSource");
				parameterList.add("person");
				// parameterList.add("obs_date");

				List<String> valueList = new ArrayList<String>();

				// obs_concept
				valueList.add(String.valueOf(observation.getObservationConcept().getId()));

				// value
				String escapedFieldValue = StringEscapeUtils.escapeSql(((String) observation.getValueAsString()));
				valueList.add("'" + escapedFieldValue + "'");

				// valueSource
				escapedFieldValue = StringEscapeUtils.escapeSql(((String) observation.getObservationSourceValue()));
				valueList.add("'" + escapedFieldValue + "'");

				// person
				valueList.add(String.valueOf(observation.getFPerson().getId()));

				// obs_date is removed for a quick solution for the repeated values from rc-api.
				// Date myDate = observation.getObservationDate();
				// DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
				// String valueName = "'" + dateFormat.format(myDate) + "'";
				// valueList.add(valueName);

				List<edu.gatech.chai.omopv5.model.entity.Observation> existingObservations = observationService
						.searchBySql(0, 0, sql, parameterList, valueList, null);
				if (!existingObservations.isEmpty()) {
					observation.setId(existingObservations.get(0).getId());
				}
			}

			observation.setObservationTypeConcept(typeConcept);

			if (observation.getId() != null) {
				retId = observationService.update(observation).getId();
			} else {
				retId = observationService.create(observation).getId();

				// Create a deduplicate entry
				if (retId != null && retId != 0L) {
					createDuplicateEntry(fhirResource.getIdentifier(), "Observation", retId);
				}
			}

			date = observation.getObservationDate();
			fPerson = observation.getFPerson();

			domainConceptId = 27L;
		}

		if (retId == null) {
			return null;
		}

		// Check method in FHIR. If we have method, check the concept ID if it's
		// codeable concept and put
		// entry in the relationship table. If text, use Note table and put the
		// relationship in relationship table.
		CodeableConcept methodCodeable = fhirResource.getMethod();
		List<Coding> methodCodings = methodCodeable.getCoding();
		String methodString = methodCodeable.getText();
		if (methodCodings != null && !methodCodings.isEmpty()) {
			for (Coding methodCoding : methodCodings) {
				Concept methodConcept = CodeableConceptUtil.getOmopConceptWithFhirConcept(conceptService, methodCoding);
				if (methodConcept == null) {
					String methodCodingDisplay = methodCoding.getDisplay();
					if (methodCodingDisplay != null && !methodCodingDisplay.isEmpty()) {
						createFactRelationship(date, fPerson, methodCodingDisplay, domainConceptId, 26L, 44818800L,
								retId, null);
					}
				} else {
					// Create relationship.
					createFactRelationship(null, null, null, domainConceptId, 58L, 44818800L, retId,
							methodConcept.getId());
				}
			}
		} else {
			if (methodString != null && !methodString.isEmpty()) {
				createFactRelationship(date, fPerson, methodString, domainConceptId, 26L, 44818800L, retId, null);
			}
		}

		// Check focus element. If we have the focus resources, we use factRelationship
		// to store the relationship.
		List<Reference> focusReferences = fhirResource.getFocus();
		for (Reference focusReference : focusReferences) {
			IIdType referenceElement = focusReference.getReferenceElement();
			logger.debug("Target Focus Reference (" + focusReference.getReference() + "): "
					+ referenceElement.getIdPart() + " " + referenceElement.getIdPartAsLong());
			createFactRelationship(domainConceptId, retId, focusReference, 44818759L);
		}

		// Check comments. If exists, put them in note table. And create relationship
		// entry. This applies to omop observation only. omop measurements are handled above
		// in the measurement handler section.
		if (commentText != null && !commentText.isEmpty() && !isSurvey && 
			observation != null && !ExtensionUtil.isInUserSpace(observation.getObservationConcept().getId())) {
			createFactRelationship(date, fPerson, commentText, domainConceptId, 26L, 44818721L, retId, null);
		}

		if (isOMOPObservation)
			retId = -retId;
		return retId;
	}

	private void createFactRelationship(Date noteDate, FPerson noteFPerson, String noteText, Long domainConceptId1,
			Long domainConceptId2, Long relationshipId, Long factId1, Long factId2) throws Exception {
		// Create relationship.
		FactRelationship factRelationship = new FactRelationship();

		if (noteDate != null && noteFPerson != null && noteText != null) {
			// Check if this note exists.
			List<Note> existingNotes = noteService.searchByColumnString("noteText", noteText);
			Note note;
			boolean found = false;
			if (existingNotes != null && !existingNotes.isEmpty()) {
				// check other fields for all notes.
				for (Note existingNote : existingNotes) {
					Calendar cal = Calendar.getInstance();
					cal.setTime(noteDate);
					int noteYear = cal.get(Calendar.YEAR);
					int noteMonth = cal.get(Calendar.MONTH);
					int noteDay = cal.get(Calendar.DAY_OF_MONTH);

					cal.setTime(existingNote.getNoteDate());
					int exNoteYear = cal.get(Calendar.YEAR);
					int exNoteMonth = cal.get(Calendar.MONTH);
					int exNoteDay = cal.get(Calendar.DAY_OF_MONTH);

					if (noteYear == exNoteYear && noteMonth == exNoteMonth && noteDay == exNoteDay
							&& noteFPerson.getId() == existingNote.getFPerson().getId()
							&& 44814645L == existingNote.getNoteTypeConcept().getId()) {

						// check if we have this in the fact relationship table. If so,
						// there is no further action required.
						List<FactRelationship> factRelationships = factRelationshipService.searchFactRelationship(
								domainConceptId1, factId1, domainConceptId2, factId2, relationshipId);
						if (!factRelationships.isEmpty()) {
							found = true;
						}

						note = existingNote;
						break;
					}
				}
			}
			if (!found) {
				Note methodNote = new Note();
				methodNote.setNoteDate(noteDate);
				methodNote.setFPerson(noteFPerson);
				methodNote.setNoteText(noteText);
				methodNote.setNoteTypeConcept(new Concept(44814645L));
				methodNote.setNoteClassConcept(new Concept(32721L)); // LOINC Method
				methodNote.setEncodingConcept(new Concept(0L));
				methodNote.setLanguageConcept(new Concept(0L));

				note = noteService.create(methodNote);
			} else {
				return;
			}

			if (note != null) {
				factRelationship.setFactId2(note.getId());
			} else {
				return;
			}
		} else {
			// This is relationship to concept. Thus, we don't need to create entry for
			// concept.
			// But, check fact relationship table for its existence.
			List<FactRelationship> factRelationships = factRelationshipService.searchFactRelationship(domainConceptId1,
					factId1, domainConceptId2, factId2, relationshipId);

			if (!factRelationships.isEmpty()) {
				return;
			}

			factRelationship.setFactId2(factId2);
		}

		factRelationship.setDomainConceptId1(domainConceptId1);
		factRelationship.setFactId1(factId1);
		factRelationship.setDomainConceptId2(domainConceptId2);
		factRelationship.setRelationshipConcept(new Concept(relationshipId));
		factRelationshipService.create(factRelationship);
	}

	private void createFactRelationship(Long domainConceptId1, Long factId1, Reference targetReference,
			Long relationshipConceptId) throws Exception {
		// Check if targetReference is not null.
		if (targetReference == null || targetReference.isEmpty()) {
			logger.error("Observariont.focus has a null or empty reference");
			return;
		}

		IIdType referenceIdType = targetReference.getReferenceElement();
		// Fine target domain and id
		if (referenceIdType == null) {
			logger.error(
					"Observation.focus.reference has no reference Id type. Reference Id type is required for linking.");
			return;
		}

		String targetResourceType = referenceIdType.getResourceType();
		Long factId2 = referenceIdType.getIdPartAsLong();

		Long domainConceptId2;
		if (MedicationStatementResourceProvider.getType().equals(targetResourceType)
				|| MedicationRequestResourceProvider.getType().equals(targetResourceType)) {
			domainConceptId2 = 13L;
		} else if (ConditionResourceProvider.getType().equals(targetResourceType)) {
			domainConceptId2 = 19L;
		} else if (ObservationResourceProvider.getType().equals(targetResourceType)) {
			if (factId2 < 0) {
				domainConceptId2 = 27L;
				factId2 = -factId2;
			} else {
				domainConceptId2 = 21L;
			}
		} else if (DocumentReferenceResourceProvider.getType().equals(targetResourceType)) {
			domainConceptId2 = 5085L;
		} else {
			logger.error("Not supported focus link resource. Please contact developer to add " + targetResourceType
					+ " resource");
			return;
		}

		// Create relationship.
		FactRelationship factRelationship = new FactRelationship();

		factRelationship.setDomainConceptId1(domainConceptId1);
		factRelationship.setDomainConceptId2(domainConceptId2);
		factRelationship.setFactId1(factId1);
		factRelationship.setFactId2(factId2);
		factRelationship.setRelationshipConcept(new Concept(relationshipConceptId));

		// see if this relationship exists.
		ParameterWrapper factParam = new ParameterWrapper("Long",
				Arrays.asList("domainConceptId1", "domainConceptId2", "factId1", "factId2", "relationshipConcept.id"),
				Arrays.asList("=", "=", "=", "=", "="),
				Arrays.asList(String.valueOf(domainConceptId1),
						String.valueOf(domainConceptId2),
						String.valueOf(factId1),
						String.valueOf(factId2),
						String.valueOf(relationshipConceptId)),
				"and");

		List<ParameterWrapper> mapList = new ArrayList<ParameterWrapper>();
		mapList.add(factParam);

		List<FactRelationship> factSearchOut = factRelationshipService.searchWithParams(0, 0, mapList, null);
		if (factSearchOut.isEmpty()) {
			factRelationshipService.create(factRelationship);
		}
	}

	// Blood Pressure is stored in the component. So, we store two values in
	// the component section. We do this by selecting diastolic when systolic
	// is selected. Since we are selecting this already, we need to skip
	// diastolic.
	final ParameterWrapper exceptionParam = new ParameterWrapper("Long", Arrays.asList("measurementConcept.id"),
			Arrays.asList("!="), Arrays.asList(String.valueOf(OmopObservation.DIASTOLIC_CONCEPT_ID)), "or");

	final ParameterWrapper exceptionParam4Search = new ParameterWrapper("Long", Arrays.asList("observationConcept.id"),
			Arrays.asList("!="), Arrays.asList(String.valueOf(OmopObservation.DIASTOLIC_CONCEPT_ID)), "or");

	@Override
	public Long getSize() throws Exception {
		List<ParameterWrapper> mapList = new ArrayList<ParameterWrapper>();

		Long size = getSize(mapList);
		ExtensionUtil.addResourceCount(ObservationResourceProvider.getType(), size);

		return size;
	}

	@Override
	public Long getSize(List<ParameterWrapper> mapList) throws Exception {
		mapList.add(exceptionParam4Search);
		return getMyOmopService().getSize(mapList);
	}

	@Override
	public void searchWithoutParams(int fromIndex, int toIndex, List<IBaseResource> listResources,
			List<String> includes, String sort) throws Exception {

		List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();
		searchWithParams(fromIndex, toIndex, paramList, listResources, includes, sort);
	}

	@Override
	public void searchWithParams(int fromIndex, int toIndex, List<ParameterWrapper> paramList,
			List<IBaseResource> listResources, List<String> includes, String sort) throws Exception {
		paramList.add(exceptionParam4Search);

		// long start = System.currentTimeMillis();

		List<FObservationView> fObservationViews = getMyOmopService().searchWithParams(fromIndex, toIndex, paramList,
				sort);

		// long gettingObses = System.currentTimeMillis()-start;
		// logger.debug("gettingObses: at "+Long.toString(gettingObses)+" duration:
		// "+Long.toString(gettingObses));

		for (FObservationView fObservationView : fObservationViews) {
			Long omopId = fObservationView.getId();
			Long fhirId = IdMapping.getFHIRfromOMOP(omopId, ObservationResourceProvider.getType());
			Observation fhirResource = constructResource(fhirId, fObservationView, includes);
			if (fhirResource != null) {
				listResources.add(fhirResource);
				// Do the rev_include and add the resource to the list.
				addRevIncludes(omopId, includes, listResources);
			}
		}
	}

	private static Date createDateTime(FObservationView fObservationView) {
		Date myDate = null;
		if (fObservationView.getObservationDate() != null) {
			try {
				if (fObservationView.getObservationDateTime() != null) {
					SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					String dateTimeString = fmt.format(fObservationView.getObservationDateTime());
					myDate = fmt.parse(dateTimeString);
				} else {
					SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
					String dateString = fmt.format(fObservationView.getObservationDate());
					myDate = fmt.parse(dateString);
				}
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

		return myDate;
	}

	public List<ParameterWrapper> mapParameter(String parameter, Object value, boolean or) throws Exception {
		List<ParameterWrapper> mapList = new ArrayList<ParameterWrapper>();
		ParameterWrapper paramWrapper = new ParameterWrapper();
		if (or)
			paramWrapper.setUpperRelationship("or");
		else
			paramWrapper.setUpperRelationship("and");

		switch (parameter) {
			case Observation.SP_RES_ID:
				String organizationId = ((TokenParam) value).getValue();
				paramWrapper.setParameterType("Long");
				paramWrapper.setParameters(Arrays.asList("id"));
				paramWrapper.setOperators(Arrays.asList("="));
				paramWrapper.setValues(Arrays.asList(organizationId));
				paramWrapper.setRelationship("or");
				mapList.add(paramWrapper);
				break;
			case Observation.SP_DATE:
				DateRangeParam dateRangeParam = ((DateRangeParam) value);
				DateUtil.constructParameterWrapper(dateRangeParam, "observationDate", paramWrapper, mapList);
				break;
			case Observation.SP_CODE:
				String system = ((TokenParam) value).getSystem();
				String code = ((TokenParam) value).getValue();
				TokenParamModifier modifier = ((TokenParam) value).getModifier();

				String modifierString = null;
				if (modifier != null) {
					modifierString = modifier.getValue();
				}

				String omopVocabulary = null;
				if (system != null && !system.isEmpty()) {
					try {
						omopVocabulary = CodeableConceptUtil.getOmopVocabularyFromFhirSystemName(conceptService,
								system);
					} catch (FHIRException e) {
						e.printStackTrace();
						break;
					}
				} else {
					omopVocabulary = "None";
				}

				if (omopVocabulary.equals(OmopCodeableConceptMapping.LOINC.getOmopVocabulary())) {
					// This is LOINC code.
					// Check if this is for BP.
					if (code != null && !code.isEmpty()) {
						if (BP_SYSTOLIC_DIASTOLIC_CODE.equals(code)) {
							// In OMOP, we have systolic and diastolic as separate
							// entries.
							// We search for systolic. When constructing FHIR<,
							// constructFHIR
							// will search matching diastolic value.
							paramWrapper.setParameterType("String");
							if (modifierString != null && modifierString.equalsIgnoreCase("text")) {
								paramWrapper.setParameters(
										Arrays.asList("observationConcept.conceptName"));
								paramWrapper.setOperators(Arrays.asList("like"));
								paramWrapper.setValues(Arrays.asList("%" + code + "%"));
							} else {
								paramWrapper.setParameters(
										Arrays.asList("observationConcept.vocabularyId",
												"observationConcept.conceptCode"));
								paramWrapper.setOperators(Arrays.asList("like", "like"));
								paramWrapper.setValues(Arrays.asList(omopVocabulary, SYSTOLIC_LOINC_CODE));
							}
							paramWrapper.setRelationship("and");
							mapList.add(paramWrapper);
						} else {
							paramWrapper.setParameterType("String");
							if (modifierString != null && modifierString.equalsIgnoreCase("text")) {
								paramWrapper.setParameters(
										Arrays.asList("observationConcept.conceptName"));
								paramWrapper.setOperators(Arrays.asList("like"));
								paramWrapper.setValues(Arrays.asList("%" + code + "%"));
							} else {
								paramWrapper.setParameters(
										Arrays.asList("observationConcept.vocabularyId",
												"observationConcept.conceptCode"));
								paramWrapper.setOperators(Arrays.asList("like", "like"));
								paramWrapper.setValues(Arrays.asList(omopVocabulary, code));
							}
							paramWrapper.setRelationship("and");
							mapList.add(paramWrapper);
						}
					} else {
						// We have no code specified. Search by system.
						paramWrapper.setParameterType("String");
						if (modifierString != null && modifierString.equalsIgnoreCase("text")) {
							paramWrapper.setParameters(
									Arrays.asList("observationConcept.conceptName"));
							paramWrapper.setOperators(Arrays.asList("like"));
							paramWrapper.setValues(Arrays.asList("%" + code + "%"));
						} else {
							paramWrapper.setParameters(Arrays.asList("observationConcept.vocabularyId"));
							paramWrapper.setOperators(Arrays.asList("like"));
							paramWrapper.setValues(Arrays.asList(omopVocabulary));
						}
						paramWrapper.setRelationship("or");
						mapList.add(paramWrapper);
					}
				} else {
					if (system == null || system.isEmpty()) {
						if (code == null || code.isEmpty()) {
							// nothing to do
							break;
						} else {
							// no system but code.
							paramWrapper.setParameterType("String");
							if (modifierString != null && modifierString.equalsIgnoreCase("text")) {
								paramWrapper.setParameters(
										Arrays.asList("observationConcept.conceptName"));
								paramWrapper.setOperators(Arrays.asList("like"));
								paramWrapper.setValues(Arrays.asList("%" + code + "%"));
							} else {
								paramWrapper.setParameters(Arrays.asList("observationConcept.conceptCode"));
								paramWrapper.setOperators(Arrays.asList("like"));
								if (BP_SYSTOLIC_DIASTOLIC_CODE.equals(code))
									paramWrapper.setValues(Arrays.asList(SYSTOLIC_LOINC_CODE));
								else
									paramWrapper.setValues(Arrays.asList(code));
							}
							paramWrapper.setRelationship("or");
							mapList.add(paramWrapper);
						}
					} else {
						if (code == null || code.isEmpty()) {
							// yes system but no code.
							paramWrapper.setParameterType("String");
							if (modifierString != null && modifierString.equalsIgnoreCase("text")) {
								paramWrapper.setParameters(
										Arrays.asList("observationConcept.conceptName"));
								paramWrapper.setOperators(Arrays.asList("like"));
								paramWrapper.setValues(Arrays.asList("%" + code + "%"));
							} else {
								paramWrapper.setParameters(Arrays.asList("observationConcept.vocabularyId"));
								paramWrapper.setOperators(Arrays.asList("like"));
								paramWrapper.setValues(Arrays.asList(omopVocabulary));
							}
							paramWrapper.setRelationship("or");
							mapList.add(paramWrapper);
						} else {
							// We have both system and code.
							paramWrapper.setParameterType("String");
							if (modifierString != null && modifierString.equalsIgnoreCase("text")) {
								paramWrapper.setParameters(
										Arrays.asList("observationConcept.conceptName"));
								paramWrapper.setOperators(Arrays.asList("like"));
								paramWrapper.setValues(Arrays.asList("%" + code + "%"));
							} else {
								paramWrapper.setParameters(
										Arrays.asList("observationConcept.vocabularyId",
												"observationConcept.conceptCode"));
								paramWrapper.setOperators(Arrays.asList("like", "like"));
								paramWrapper.setValues(Arrays.asList(omopVocabulary, code));
							}
							paramWrapper.setRelationship("and");
							mapList.add(paramWrapper);
						}
					}
				}
				break;
			case "Patient:" + Patient.SP_RES_ID:
				addParamlistForPatientIDName(parameter, (String) value, paramWrapper, mapList);
				// String pId = (String) value;
				// paramWrapper.setParameterType("Long");
				// paramWrapper.setParameters(Arrays.asList("fPerson.id"));
				// paramWrapper.setOperators(Arrays.asList("="));
				// paramWrapper.setValues(Arrays.asList(pId));
				// paramWrapper.setRelationship("or");
				// mapList.add(paramWrapper);
				break;
			case "Patient:" + Patient.SP_NAME:
				addParamlistForPatientIDName(parameter, (String) value, paramWrapper, mapList);
				// String patientName = ((String) value).replace("\"", "");
				// paramWrapper.setParameterType("String");
				// paramWrapper.setParameters(Arrays.asList("fPerson.familyName",
				// "fPerson.givenName1", "fPerson.givenName2",
				// "fPerson.prefixName", "fPerson.suffixName"));
				// paramWrapper.setOperators(Arrays.asList("like", "like", "like", "like",
				// "like"));
				// paramWrapper.setValues(Arrays.asList("%" + patientName + "%"));
				// paramWrapper.setRelationship("or");
				// mapList.add(paramWrapper);
				break;
			case "Patient:" + Patient.SP_IDENTIFIER:
				addParamlistForPatientIDName(parameter, (String) value, paramWrapper, mapList);
				break;
			default:
				mapList = null;
		}

		return mapList;
	}

	@Override
	public FObservationView constructOmop(Long omopId, Observation fhirResource) {
		// This is view. So, we can't update or create.
		// See the contructOmop for the actual tables such as
		// constructOmopMeasurement
		// or consturctOmopObservation.
		return null;
	}

}
