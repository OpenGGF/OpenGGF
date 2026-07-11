package com.openggf.mods.validation;

import java.util.Objects;

/** Stable structural-validator diagnostic produced without defining author classes. */
public record ModValidationFinding(Severity severity, String code, String className,
                                   String member, String message) {
    public ModValidationFinding {
        Objects.requireNonNull(severity, "severity");
        code = require(code, "code");
        className = className == null ? "" : className;
        member = member == null ? "" : member;
        message = require(message, "message");
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be nonblank");
        return value;
    }

    public enum Severity { WARNING, ERROR }
}
