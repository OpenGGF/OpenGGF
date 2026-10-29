package com.openggf.game.internal;

import com.openggf.control.InputHandler;

/** Game-owned native controller consumer, separate from live configuration UI. */
@FunctionalInterface
public interface NativeLevelInputOwner {
    /** Consumes the already published sample; true reserves Start from native pause. */
    boolean handleInput(InputHandler input);
}
