package com.booklane.booking.adapters.http;

import org.springframework.http.HttpStatus;

public final class HttpFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public HttpFailure(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
