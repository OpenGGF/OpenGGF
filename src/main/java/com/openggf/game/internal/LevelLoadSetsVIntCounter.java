package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * Game service: the host's level load clears and re-establishes the object V-INT clock itself, so a fresh load
 * does not carry the previous level's (or power-on) {@code V_int_run_count} into the new one. Without it the
 * clock is global work RAM that no level load clears (the cartridge games' {@code V_int_run_count}) and the
 * load carries it. Sonic CD's {@code levelVIntCounter} is cleared by {@code LevelStart}'s {@code .ClearFlowers}
 * (Level/Main.asm:178-184) and its load sets it before its own first main-loop iteration.
 */
public interface LevelLoadSetsVIntCounter {
    static boolean forHost(GameModule host) {
        return host != null && host.getGameService(LevelLoadSetsVIntCounter.class) != null;
    }
}
