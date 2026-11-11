package com.openggf.sprites.playable;

import java.util.function.Supplier;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;

/** Explicit services for solid-contact unit actors; does not open global gameplay. */
public final class SolidContactActorFixture {
    private SolidContactActorFixture() { }
    public static <T extends AbstractPlayableSprite> T construct(Supplier<T> factory) {
        return new PlayableRuntimeContext(new Sonic3kGameModule(), null, null, null, null,
                null, new GameStateManager(), null, null).construct(factory);
    }
}
