package com.openggf.route;

/** A cooperative cancellation signal. It can stop work; it never selects a result. */
@FunctionalInterface
public interface Cancellation {
    Cancellation NONE = () -> false;

    boolean cancelled();
}
