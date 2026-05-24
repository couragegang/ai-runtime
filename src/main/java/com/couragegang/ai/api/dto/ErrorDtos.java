package com.couragegang.ai.api.dto;

import io.micronaut.serde.annotation.Serdeable;

public final class ErrorDtos {

    private ErrorDtos() {}

    @Serdeable
    public record ErrorBody(String code, String message) {

        public static ErrorBody of(String code, String message) {
            return new ErrorBody(code, message);
        }
    }
}
