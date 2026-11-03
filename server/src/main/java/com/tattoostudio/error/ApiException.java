package com.tattoostudio.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** An expected API error: HTTP status + {@link ErrorCode} + a message for the user. */
public class ApiException extends ResponseStatusException {

    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(status, message);
        this.code = code;
        getBody().setProperty("code", code.name());
    }

    public ErrorCode getCode() {
        return code;
    }
}
