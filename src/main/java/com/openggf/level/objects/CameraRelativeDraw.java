package com.openggf.level.objects;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Engine metadata for draw paths that read camera positions, independently of gameplay culling. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CameraRelativeDraw {
    Use value();
    enum Use { CULL_ONLY, SCREEN_POSITION }
}
