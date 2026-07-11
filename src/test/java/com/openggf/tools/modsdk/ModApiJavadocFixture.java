package com.openggf.tools.modsdk;

import com.openggf.game.ModApi;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;

@ModApi
@Deprecated(since = "1.0")
public class ModApiJavadocFixture<T extends Number & Comparable<T>>
        extends ArrayList<T> implements Serializable {
    protected @FixtureTypeUse T value;

    @Deprecated
    public <R extends CharSequence> @FixtureTypeUse R convert(
            @FixtureParameter @FixtureTypeUse T input) throws IOException {
        throw new IOException(input.toString());
    }
}

@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
@interface FixtureTypeUse { }

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@interface FixtureParameter { }

@ModApi
@interface ModApiJavadocAnnotationFixture {
    String value() default "default-value";
}
