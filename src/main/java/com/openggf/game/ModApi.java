package com.openggf.game;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as part of the supported compiled-mod compatibility surface.
 * Public and protected signatures of marked types follow the semantic mod API
 * version; unmarked engine types carry no compatibility promise.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@com.openggf.game.ModApi
public @interface ModApi {
}
