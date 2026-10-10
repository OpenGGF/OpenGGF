package com.openggf.tests;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Value comparison for native snapshots: record equality does not compare their cloned history arrays. */
public final class MutatorPlayerStateAssertions {
    private MutatorPlayerStateAssertions() { }

    public static void assertNativeStateEquals(Object expected, Object actual) {
        assertNativeStateEquals(expected, actual, "native state");
    }

    public static void assertNativeStateEquals(Object expected, Object actual, String path) {
        if (expected != null && actual != null && expected.getClass().isRecord()) {
            assertEquals(expected.getClass(), actual.getClass(), path);
            for (var component : expected.getClass().getRecordComponents()) {
                try {
                    assertNativeStateEquals(component.getAccessor().invoke(expected),
                            component.getAccessor().invoke(actual), path + "." + component.getName());
                } catch (IllegalAccessException | InvocationTargetException failure) {
                    throw new AssertionError("Cannot inspect " + path, failure);
                }
            }
        } else {
            assertTrue(Objects.deepEquals(expected, actual), () -> path + ": expected " + expected + ", actual " + actual);
        }
    }
}
