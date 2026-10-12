package com.openggf.net.protocol;

/** Malformed, oversized, or unknown wire input. */
@com.openggf.game.ModApi
public class ProtocolViolationException extends RuntimeException {
    public ProtocolViolationException(String message) {
        super(message);
    }

    public ProtocolViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
