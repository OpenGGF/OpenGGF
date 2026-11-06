package com.openggf.game.internal;

/** Internal selection of the native full-solid rejection tail for a dying player. */
@FunctionalInterface
public interface DyingSolidPushPolicy {
    boolean clearsPushOnDyingSolidPass();
}
