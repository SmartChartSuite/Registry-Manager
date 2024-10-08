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
 *
 *******************************************************************************/
package edu.gatech.chai.omopv5.dba.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.TableResult;

import edu.gatech.chai.omopv5.model.entity.Concept;

@Service
public class ConceptServiceImp extends BaseEntityServiceImp<Concept> implements ConceptService {

	@Value("${schema.vocabularies}")
	private String vocabSchema;

	/**
	 * Instantiates a new concept service imp.
	 */
	public ConceptServiceImp() {
		super(Concept.class);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edu.gatech.chai.omopv5.dba.service.ConceptService#getIngredient(edu.gatech.
	 * chai.omopv5.model.entity.Concept)
	 */
	public List<Concept> getIngredient(Concept concept) {

		List<Concept> concepts = new ArrayList<Concept>();

		if ("Ingredient".equals(concept.getConceptClassId())) {
			// This is ingredient. Just return empty list
			return concepts;
		}

		List<String> parameterList = new ArrayList<String>();
		List<String> valueList = new ArrayList<String>();
		// String vocabSchema = SqlUtil.vocabSchema();
		String myVocabSchema = "";
		if (vocabSchema != null && !vocabSchema.isBlank()) {
			myVocabSchema = vocabSchema + ".";
		}

		String sql = null;
		String sqlWithoutWhere = constructSqlSelectWithoutWhere();
		if ("NDC".equals(concept.getVocabularyId())) {
			// Use JPQL
			sql = sqlWithoutWhere 
				+ " JOIN " + myVocabSchema + "concept_relationship cr on concept.concept_id = cr.concept_id_1 "
				+ "AND cr.relationship_id = 'Maps to' " + "AND cr.invalid_reason is null "
				+ "JOIN " + myVocabSchema + "concept tar on cr.concept_id_2 = tar.id " + "AND tar.standard_concept = 'S' "
				+ "AND tar.invalid_reason is null " + "JOIN concept_ancestor ca ON ca.ancestor_concept_id = tar.concept_id "
				+ "JOIN " + myVocabSchema + "concept c ON ca.ancestor_concept_id = c.concept_id "
				+ " WHERE concept.concept_code = @med_code "
				+ "AND 'NDC' = concept.vocabulary_id " + "AND c.vocabulary_id = 'RxNorm' "
				+ "AND c.concept_class_id = 'Ingredient' " + "AND concept.invalid_reason is null";
		} else if ("RxNorm".equals(concept.getVocabularyId())) {
			// when RxNorm.
			sql = sqlWithoutWhere 
				+ " JOIN " + myVocabSchema + "concept_ancestor ca ON ca.descendant_concept_id = concept.concept_id "
				+ "JOIN " + myVocabSchema + "concept c ON ca.ancestor_concept_id = c.concept_id "
				+ " WHERE concept.concept_code = @med_code "
				+ "AND 'RxNorm' = concept.vocabulary_id " + "AND c.vocabulary_id = 'RxNorm' "
				+ "AND c.concept_class_id = 'Ingredient' " + "AND concept.invalid_reason is null "
				+ "AND c.invalid_reason is null";
		} else {
			return concepts;
		}

		parameterList.add("med_code");
		valueList.add("'"+concept.getConceptCode()+"'");
		sql = renderedSql(sql, parameterList, valueList);

		Concept entity;
		try {
			if (isBigQuery()) {
				TableResult result = runBigQuery(sql);
				List<String> columns = listOfColumns(sql);
				for (FieldValueList row : result.iterateAll()) {
					entity = construct(row, null, getSqlTableName(), columns);
					if (entity != null) {
						break;
					}
				}
			} else {
				List<Concept> myEntities = runQuery(sql, null, "concept");
				if (!myEntities.isEmpty()) {
					concepts.addAll(myEntities);					
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return concepts;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.gatech.chai.omopv5.dba.service.ConceptService#getLargestId()
	 */
	@Override
	public Long getLargestId() {
		return null;
	}

	@Override
	public Concept update(Concept entity) {
		return null;
	}

	@Override
	public Concept construct(ResultSet rs, Concept entity, String alias) throws SQLException {
		return ConceptService._construct(rs, entity, alias);
	}

	@Override
	public Concept construct(FieldValueList rowResult, Concept entity, String alias, List<String> columns) {
		return ConceptService._construct(rowResult, entity, alias, columns);
	}

}
