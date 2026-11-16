package com.openggf.game.internal;

import com.openggf.game.session.GameplayModeContext;

/** Native entry work which must retire before an isolated director places its cast. */
public interface SceneEntryPreparation {
    boolean pending(GameplayModeContext world);
}
