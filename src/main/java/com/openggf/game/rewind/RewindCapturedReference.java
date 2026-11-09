package com.openggf.game.rewind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Engine-local intent to capture a mutable ObjectInstance reference by identity.
 * Ordinary actors honor this declaration in their compact projection; scalar
 * fallback still omits references. Exclusions win, and final or non-object
 * declarations are invalid. This does not change codec or reconstruction rules.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RewindCapturedReference {
    String reason();
}
