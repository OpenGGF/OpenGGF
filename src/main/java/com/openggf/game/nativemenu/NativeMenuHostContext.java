package com.openggf.game.nativemenu;

import com.openggf.control.InputHandler;
import com.openggf.game.GameModule;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;

import java.util.Objects;
import java.util.function.Consumer;

/** Immutable composition capabilities for one native menu visit. */
public record NativeMenuHostContext(EngineContext engine, GameModule rootModule,
        InputHandler input, Consumer<GameplayModeContext> publishMode) {
    public NativeMenuHostContext {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(rootModule, "rootModule");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(publishMode, "publishMode");
    }
}
