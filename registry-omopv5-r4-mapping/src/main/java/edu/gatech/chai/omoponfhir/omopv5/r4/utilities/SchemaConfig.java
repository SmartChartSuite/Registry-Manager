package edu.gatech.chai.omoponfhir.omopv5.r4.utilities;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SchemaConfig {

	@Value("${schema.registry}")
    private String dataSchema;

	@Value("${schema.vocabularies}")
	private String vocabSchema;

    @Value("${rcapi.requesttype}")
	private String requestType;

    public String getDataSchema() {
        return this.dataSchema;
    }

    public void setDataSchema(String dataSchema) {
        this.dataSchema = dataSchema;
    }
    
    public String getVocabSchema() {
        return this.vocabSchema;
    }

    public void setVocabSchema(String vocabSchema) {
        this.vocabSchema = vocabSchema;
    }

    public String getRequestType() {
        return this.requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }
}
