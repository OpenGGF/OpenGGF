package com.openggf.sprites;

import com.openggf.graphics.GraphicsManager;
import com.openggf.util.EngineCallerAccess;
import java.util.Objects;

/** Engine-only render access to the graphics context bound when a sprite was constructed. */
public final class SpriteGraphicsAccess {
    private SpriteGraphicsAccess() { }

    public static GraphicsManager graphics(AbstractSprite sprite) {
        Class<?> caller = EngineCallerAccess.callerOutside(SpriteGraphicsAccess.class);
        if (caller.getClassLoader() != SpriteGraphicsAccess.class.getClassLoader())
            throw new SecurityException("Sprite graphics access belongs to the engine renderer");
        return Objects.requireNonNull(sprite, "sprite").graphicsManager;
    }
}
