package com.openggf;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Temporary source-era access to visit state; contains no substitute production operations. */
final class SuspensionTestAccess {
    private SuspensionTestAccess() { }

    static void set(Object target, String name, Object value) {
        try { field(target, name).set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @SuppressWarnings("unchecked")
    static <T> T get(Object target, String name) {
        try { return (T) field(target, name).get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException absent) { /* Mockito/subclass callers retain the real owner. */ }
        }
        throw new NoSuchFieldException(name);
    }

    static <T> T call(Object target, String name) { return call(target, name, new Class<?>[0]); }

    @SuppressWarnings("unchecked")
    static <T> T call(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = Engine.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return (T) method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
