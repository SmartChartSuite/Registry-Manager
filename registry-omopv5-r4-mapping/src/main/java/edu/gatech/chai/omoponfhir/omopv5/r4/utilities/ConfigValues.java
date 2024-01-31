package edu.gatech.chai.omoponfhir.omopv5.r4.utilities;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfigValues {

	@Value("${schema.registry}")
    private String dataSchema;

	@Value("${schema.vocabularies}")
	private String vocabSchema;

    @Value("${rcapi.jobpackage}")
	private String jobPackage;

    @Value("${rcapi.host}")
    private String rcapiHostUrl;

    @Value("${rcapi.basicauth}")
    private String rcapiBasicAuth;

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

    public String getJobPackage() {
        return this.jobPackage;
    }

    public void setJobPackage(String jobPackage) {
        this.jobPackage = jobPackage;
    }

    public String getRcApiHostUrl() {
        return this.rcapiHostUrl;
    }

    public void setRcApiHostUrl(String rcapiHostUrl) {
        this.rcapiHostUrl = rcapiHostUrl;
    }

    public String getRcApiBasicAuth() {
        return this.rcapiBasicAuth;
    }

    public void setRcApiBasicAuth(String rcapiBasicAuth) {
        this.rcapiBasicAuth = rcapiBasicAuth;
    }
}
