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

import edu.gatech.chai.omopv5.model.entity.ConditionOccurrence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.google.cloud.bigquery.FieldValueList;

/**
 * The Class ConditionOccurrenceServiceImp.
 */
@Service
public class ConditionOccurrenceServiceImp extends BaseEntityServiceImp<ConditionOccurrence>
        implements ConditionOccurrenceService{
    
    /**
     * Instantiates a new condition occurrence service imp.
     */
    public ConditionOccurrenceServiceImp() {
        super(ConditionOccurrence.class);
    }

	@Override
	public ConditionOccurrence construct(ResultSet rs, ConditionOccurrence entity, String alias) throws SQLException {
		return ConditionOccurrenceService._construct(rs, entity, alias);
	}

	@Override
	public ConditionOccurrence construct(FieldValueList rowResult, ConditionOccurrence entity, String alias,
			List<String> columns) {
		return ConditionOccurrenceService._construct(rowResult, entity, alias, columns);
	}

}