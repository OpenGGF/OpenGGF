package com.openggf;

import com.openggf.game.GameMode;

/**
 * Callback interface for game mode changes.
 */
@com.openggf.game.ModApi
public interface GameModeChangeListener {
    void onGameModeChanged(GameMode oldMode, GameMode newMode);
}
