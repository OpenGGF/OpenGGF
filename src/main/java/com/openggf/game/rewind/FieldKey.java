package com.openggf.game.rewind;

import java.lang.reflect.Field;
import java.util.Objects;

@com.openggf.game.ModApi
public record FieldKey(String declaringClassName, String fieldName) {
    public FieldKey {
        Objects.requireNonNull(declaringClassName, "declaringClassName");
        Objects.requireNonNull(fieldName, "fieldName");
    }

    public static FieldKey of(Field field) {
        return new FieldKey(field.getDeclaringClass().getName(), field.getName());
    }

    @Override
    public String toString() {
        return declaringClassName + "#" + fieldName;
    }
}
