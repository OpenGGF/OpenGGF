package com.openggf.graphics.shaderlib;

public class UnsupportedShaderException extends Exception {
    public UnsupportedShaderException(String message) {
        super(message);
    }

    public UnsupportedShaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
