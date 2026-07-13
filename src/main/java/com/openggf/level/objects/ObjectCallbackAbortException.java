package com.openggf.level.objects;

/** Neutral abort signal propagated without coupling object runtime code to mods. */
public class ObjectCallbackAbortException extends RuntimeException {
    protected ObjectCallbackAbortException(String message, Throwable cause) {
        super(message, cause);
    }
}
