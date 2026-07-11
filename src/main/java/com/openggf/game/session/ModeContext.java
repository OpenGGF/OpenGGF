package com.openggf.game.session;

import com.openggf.game.GameMode;

@com.openggf.game.ModApi
public interface ModeContext {
    GameMode getGameMode();

    void destroy();
}
