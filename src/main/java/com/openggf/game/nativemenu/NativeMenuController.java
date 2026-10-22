package com.openggf.game.nativemenu;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import java.util.Objects;

/** Owns one native screen and advances input only after its command is observed. */
public final class NativeMenuController {
    private NativeMenuScreen screen;

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

    public NativeMenuScreen.Exit update(InputHandler input, boolean fading) {
        Objects.requireNonNull(input, "input");
        try {
            if (screen == null || fading) return NativeMenuScreen.Exit.NONE;
            screen.update(input);
            return Objects.requireNonNull(screen.consumeExit(), "native menu exit");
        } finally {
            input.update();
        }
    }

    public NativeMenuScreen screen() { return screen; }

    public void close() {
        NativeMenuScreen previous = screen;
        screen = null;
        if (previous != null) previous.reset();
    }
}
