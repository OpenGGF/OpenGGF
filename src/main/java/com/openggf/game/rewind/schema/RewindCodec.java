package com.openggf.game.rewind.schema;

import java.lang.reflect.Field;
import java.util.Objects;

public interface RewindCodec {
    void capture(Field field, Object target, RewindStateBuffer scalarData, java.util.List<Object> opaqueValues);

    void restore(
            Field field,
            Object target,
            RewindStateBuffer.Reader scalarData,
            OpaqueValues opaqueValues,
            OpaqueIndex opaqueIndex);

    default void capture(
            Field field,
            Object target,
            RewindStateBuffer scalarData,
            java.util.List<Object> opaqueValues,
            RewindCaptureContext context) {

        capture(field, target, scalarData, opaqueValues);
    }

    default void restore(
            Field field,
            Object target,
            RewindStateBuffer.Reader scalarData,
            OpaqueValues opaqueValues,
            OpaqueIndex opaqueIndex,
            RewindCaptureContext context) {

        restore(field, target, scalarData, opaqueValues, opaqueIndex);
    }

    default boolean capturesFinalFields() {
        return false;
    }

    default boolean requiresExistingTargetValue() {
        return false;
    }

    final class OpaqueIndex {
        private int value;

        Object next(OpaqueValues opaqueValues) {
            Objects.requireNonNull(opaqueValues, "opaqueValues");
            if (value >= opaqueValues.length()) {
                throw new IllegalStateException("Rewind opaque value underflow at index " + value + ".");
            }
            return opaqueValues.valueAt(value++);
        }
    }

    final class OpaqueValues {
        private final Object[] values;

        private OpaqueValues(Object[] values) {
            this.values = Objects.requireNonNull(values, "values");
        }

        static OpaqueValues owned(Object[] values) {
            return new OpaqueValues(values);
        }

        private int length() {
            return values.length;
        }

        private Object valueAt(int index) {
            return values[index];
        }
    }
}
