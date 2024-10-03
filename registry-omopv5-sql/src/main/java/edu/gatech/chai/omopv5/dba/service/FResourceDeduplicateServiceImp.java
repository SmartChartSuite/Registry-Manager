package edu.gatech.chai.omopv5.dba.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.google.cloud.bigquery.FieldValueList;

import org.springframework.stereotype.Service;

import edu.gatech.chai.omopv5.model.entity.FResourceDeduplicate;

@Service
public class FResourceDeduplicateServiceImp extends BaseEntityServiceImp<FResourceDeduplicate> implements FResourceDeduplicateService {

    public FResourceDeduplicateServiceImp() {
        super(FResourceDeduplicate.class);
    }

    @Override
	public FResourceDeduplicate construct(ResultSet rs, FResourceDeduplicate entity, String alias) throws SQLException {
		return FResourceDeduplicateService._construct(rs, entity, alias);
	}

	@Override
	public FResourceDeduplicate construct(FieldValueList rowResult, FResourceDeduplicate entity, String alias, List<String> columns) {
		return FResourceDeduplicateService._construct(rowResult, entity, alias, columns);
	}

}
