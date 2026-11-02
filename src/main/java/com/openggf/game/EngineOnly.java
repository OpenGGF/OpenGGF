package com.openggf.game;

import java.lang.annotation.*;

/** Engine plumbing excluded from the supported creator contract. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface EngineOnly { }
