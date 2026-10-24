package com.openggf.graphics;

import java.lang.reflect.Field;

/** Existing renderer fixtures inject texture handles without creating a GL context. */
final class PaletteTextureTestAccess {
    private PaletteTextureTestAccess() { }

    static Object get(GraphicsManager graphics, String name) throws ReflectiveOperationException {
        return field(name).get(graphics.paletteTextures());
    }

    static void set(GraphicsManager graphics, String name, Object value) throws ReflectiveOperationException {
        field(name).set(graphics.paletteTextures(), value);
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = PaletteTextureOwner.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
