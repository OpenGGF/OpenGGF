package com.openggf.game.soniccd.program;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.nativemenu.NativeMenuScreen;

import java.util.logging.Logger;

/**
 * The native-program host's placeholder for a program this build does not host (Time Attack,
 * D.A. Garden, Visual Mode, sound test, backup RAM manager, best staff times, demos, FMVs,
 * endings). It runs in the {@link GameMode#OPTIONS} host and returns to the title on its first
 * update, which is where IPX continues once each of these programs returns. A later program lane
 * replaces it with its own {@link NativeMenuScreen} for the same mode.
 */
public final class CdNotHostedScreen implements NativeMenuScreen {
    private static final Logger LOGGER = Logger.getLogger(CdNotHostedScreen.class.getName());
    private final CdSceneCoordinator coordinator;
    private boolean returned;

    public CdNotHostedScreen(CdSceneCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override public GameMode mode() { return GameMode.OPTIONS; }

    @Override
    public void initialize() {
        returned = false;
        var steps = coordinator.notHosted();
        LOGGER.info("Sonic CD program not hosted: " + (steps.isEmpty() ? "none" : steps.get(steps.size() - 1)));
    }

    @Override public void update(InputHandler input) { returned = true; }
    @Override public void draw() { }
    @Override public void setClearColor() { }
    @Override public void reset() { returned = false; }

    @Override
    public Exit consumeExit() {
        if (!returned) return Exit.NONE;
        returned = false;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }
}
