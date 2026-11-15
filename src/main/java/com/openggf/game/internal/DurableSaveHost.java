package com.openggf.game.internal;

import java.nio.file.Path;

/**
 * A game whose progress lives in its own save model (Sonic CD's backup RAM) rather than the shared
 * data-select slots. A player's launch attaches it to the save root before the game's first program
 * runs; tests, replays and capture tools never do, so their saves last for the session only. The
 * module registers it with {@link com.openggf.game.GameModule#getGameService(Class)}.
 */
public interface DurableSaveHost {
    /** Keep this session's saves under {@code saveRoot} (read at start, written on each game-driven change). */
    void attachDurableSaves(Path saveRoot);
}
