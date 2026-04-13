package com.enterprise.booking.tool;

public class PreviewToolException extends RuntimeException {

    public enum Code {
        TIMEOUT,
        SOLD_OUT,
        VALIDATION_ERROR,
        UNAVAILABLE
    }

    private final Code code;

    public PreviewToolException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public PreviewToolException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
