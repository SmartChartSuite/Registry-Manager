package edu.gatech.chai.omoponfhir.omopv5.r4.utilities;

public enum QueryRequest {
    RUNNING(0x0001, "RUNNING", "Case is running for case monitoring."),
    END(0x0002, "END", "Case is at the end of active monitoring period."),
    REQUEST_PENDING(0x0004, "REQUEST_PENDING", "Request to Query for case is queued but not yet in running queue."), 
    TIMED_OUT(0x0008, "TIMED_OUT", "Retry count reached the maximum allowed count. Giving up."), 
    PAUSED(0x0010, "PAUSED", "Case is paused by system admin."), 
    ERROR_IN_CLIENT(0x0100, "ERROR_IN_CLIENT", "Error occurred on client side during Request. Request is paused."), 
    ERROR_IN_SERVER(0x0200, "ERROR_IN_SERVER", "Error occurred on server side during Request. Request will be made again until it reaches the maximum retry-count."), 
    RESULT_PARSE_ERROR(0x0201, "RESULT_PARSE_ERROR", "Error occurred when parsing the server response data"),
    ERROR_UNKNOWN(0x0400, "ERROR_UNKNOWN", "Unknown error occurred. Ex) Some resources in result bundle failed to be stored in db."), 
    INVALID(0x0000, "INVALID", "Invalid Status. Retrigger with a valid status is required.");

    private int code;
    private String codeString;
    private String desc;

    QueryRequest(int code, String codeString, String desc) {
        this.code = code;
        this.codeString = codeString;
        this.desc = desc;
    }

    public int getCode() {
        return this.code;
    }

    public String getCodeString() {
        return this.codeString;
    }

    public String getDesc() {
        return this.desc;
    }
    
    public static QueryRequest codeEnumOf(String s) {
        QueryRequest retv = INVALID;

        if ("RUNNING".equals(s)) retv = RUNNING;
        else if ("END".equals(s)) retv =  END;
        else if ("REQUEST_PENDING".equals(s)) retv = REQUEST_PENDING;
        else if ("TIMED_OUT".equals(s)) return TIMED_OUT;
        else if ("PAUSED".equals(s)) return PAUSED;
        else if ("ERROR_IN_CLIENT".equals(s)) return ERROR_IN_CLIENT;
        else if ("ERROR_IN_SERVER".equals(s)) return ERROR_IN_SERVER;
        else if ("RESULT_PARSE_ERROR".equals(s)) return RESULT_PARSE_ERROR;
        else if ("ERROR_UNKNOWN".equals(s)) return ERROR_UNKNOWN;

        return retv;
    }

    public static int codeOf(String s) {
        int retv = INVALID.code;

        if ("RUNNING".equals(s)) retv = RUNNING.code;
        else if ("END".equals(s)) retv =  END.code;
        else if ("REQUEST_PENDING".equals(s)) retv = REQUEST_PENDING.code;
        else if ("TIMED_OUT".equals(s)) return TIMED_OUT.code;
        else if ("PAUSED".equals(s)) return PAUSED.code;
        else if ("ERROR_IN_CLIENT".equals(s)) return ERROR_IN_CLIENT.code;
        else if ("ERROR_IN_SERVER".equals(s)) return ERROR_IN_SERVER.code;
        else if ("RESULT_PARSE_ERROR".equals(s)) return RESULT_PARSE_ERROR.code;
        else if ("ERROR_UNKNOWN".equals(s)) return ERROR_UNKNOWN.code;

        return retv;
    }

    public static String descOf(String s) {
        String retv = INVALID.desc;

        if ("RUNNING".equals(s)) retv = RUNNING.desc;
        else if ("END".equals(s)) retv =  END.desc;
        else if ("REQUEST_PENDING".equals(s)) retv = REQUEST_PENDING.desc;
        else if ("TIMED_OUT".equals(s)) return TIMED_OUT.desc;
        else if ("PAUSED".equals(s)) return PAUSED.desc;
        else if ("ERROR_IN_CLIENT".equals(s)) return ERROR_IN_CLIENT.desc;
        else if ("ERROR_IN_SERVER".equals(s)) return ERROR_IN_SERVER.desc;
        else if ("RESULT_PARSE_ERROR".equals(s)) return RESULT_PARSE_ERROR.desc;
        else if ("ERROR_UNKNOWN".equals(s)) return ERROR_UNKNOWN.desc;

        return retv;
    }
}