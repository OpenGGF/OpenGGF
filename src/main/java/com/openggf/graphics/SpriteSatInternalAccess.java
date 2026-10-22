package com.openggf.graphics;

import java.util.List;

/** Engine-only native raster/fragment bridge, outside the published GraphicsManager API. */
public final class SpriteSatInternalAccess {
    private SpriteSatInternalAccess() { }

    private static void engineCaller() {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(SpriteSatInternalAccess.class);
        if (caller.getClassLoader() != SpriteSatInternalAccess.class.getClassLoader()) {
            throw new SecurityException("Native SAT assembly belongs to the engine renderer");
        }
    }

    public static void beginCollection(GraphicsManager graphics, SpriteSatMaskPostProcessor.Raster raster) {
        engineCaller();
        graphics.beginSpriteSatCollection(raster);
    }

    public static void submitGroups(GraphicsManager graphics, List<SpriteSatEntry> geometry, Runnable producer) {
        engineCaller();
        graphics.submitSpriteSatGroups(geometry, producer);
    }
}
