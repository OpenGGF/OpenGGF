package com.openggf.game.nativemenu;

import com.openggf.control.InputHandler;
import com.openggf.game.GameplayLaunchTeam;

/** Internal host contract for a ROM-native menu outside the title provider. */
public interface NativeMenuScreen {
    void initialize();
    void update(InputHandler input);
    void draw();
    void setClearColor();
    void reset();
    Exit consumeExit();

    enum Destination { NONE, LEVEL, COMPETITION, TITLE_SCREEN }

    /** A launch team applies to this launch only; native options retain emerald progress. */
    record Exit(Destination destination, GameplayLaunchTeam team, boolean resetProgress) {
        public static final Exit NONE = new Exit(Destination.NONE, null, false);
        public Exit {
            java.util.Objects.requireNonNull(destination, "destination");
        }
    }
}
