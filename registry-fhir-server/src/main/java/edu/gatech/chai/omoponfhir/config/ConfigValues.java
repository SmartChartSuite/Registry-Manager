package edu.gatech.chai.omoponfhir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfigValues {

	@Value("${schema.registry}")
    private String dataSchema;

	@Value("${schema.vocabularies}")
	private String vocabSchema;

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
}
