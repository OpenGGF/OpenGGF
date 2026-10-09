package com.openggf.sprites;

import com.openggf.graphics.GraphicsManager;
import com.openggf.game.session.EngineCallerInspectionBootstrap;
import java.util.Objects;

/** Engine-only render access to the graphics context bound when a sprite was constructed. */
public final class SpriteGraphicsAccess {
    private static final StackWalker CALLERS = EngineCallerInspectionBootstrap.create();
    private SpriteGraphicsAccess() { }

    public static GraphicsManager graphics(AbstractSprite sprite) {
        Class<?> caller = CALLERS.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != SpriteGraphicsAccess.class && type.getClassLoader() != null)
                .findFirst().orElseThrow());
        if (caller.getClassLoader() != SpriteGraphicsAccess.class.getClassLoader())
            throw new SecurityException("Sprite graphics access belongs to the engine renderer");
        return Objects.requireNonNull(sprite, "sprite").graphicsManager;
    }
}
