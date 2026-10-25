package com.openggf.game.nativemenu;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import java.util.Objects;

/** Owns one native screen and advances input only after its command is observed. */
public final class NativeMenuController {
    private NativeMenuScreen screen;
    private NativeMenuScreen completionCandidate;

    public static boolean handles(GameMode mode) {
        return mode == GameMode.OPTIONS || mode == GameMode.COMPETITION_MENU
                || mode == GameMode.COMPETITION || mode == GameMode.COMPETITION_RESULTS;
    }

    public static com.openggf.game.resources.PlcLifecyclePhase phase(GameMode mode) {
        return switch (mode) {
            case OPTIONS, COMPETITION_MENU -> com.openggf.game.resources.PlcLifecyclePhase.LEVEL_SELECT;
            case COMPETITION -> com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL;
            case COMPETITION_RESULTS -> com.openggf.game.resources.PlcLifecyclePhase.TWO_PLAYER_RESULTS;
            default -> throw new IllegalArgumentException("Not a native menu mode: " + mode);
        };
    }

    public boolean open(NativeMenuScreen candidate) {
        if (candidate == null) return false;
        if (!handles(candidate.mode())) {
            throw new IllegalArgumentException("Unsupported native screen mode: " + candidate.mode());
        }
        close();
        screen = candidate;
        try {
            candidate.initialize();
        } catch (RuntimeException | Error failure) {
            screen = null;
            candidate.reset();
            throw failure;
        }
        return true;
    }

    /** An incomplete or failed outer frame must not supply a later completion. */
    public void beginFrame() {
        completionCandidate = null;
    }

    public NativeMenuScreen.Exit update(InputHandler input, boolean fading) {
        beginFrame();
        Objects.requireNonNull(input, "input");
        NativeMenuScreen updated = screen;
        NativeMenuScreen.Exit exit;
        try {
            if (updated == null || fading) return NativeMenuScreen.Exit.NONE;
            updated.update(input);
            exit = Objects.requireNonNull(updated.consumeExit(), "native menu exit");
        } finally {
            input.update();
        }
        if (screen == updated) completionCandidate = updated;
        return exit;
    }

    /** Called only after the outer lifecycle returns successfully, before presentation. */
    public boolean completeFrame() {
        NativeMenuScreen completed = completionCandidate;
        completionCandidate = null;
        if (completed == null || screen != completed) return false;
        completed.afterFrame();
        return true;
    }

    public NativeMenuScreen screen() { return screen; }

    public void close() {
        beginFrame();
        NativeMenuScreen previous = screen;
        screen = null;
        if (previous != null) previous.reset();
    }
}
