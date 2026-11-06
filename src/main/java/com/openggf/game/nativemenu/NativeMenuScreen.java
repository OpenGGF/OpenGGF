package com.openggf.game.nativemenu;

import com.openggf.control.InputHandler;
import com.openggf.game.GameplayLaunchTeam;

/** Internal host contract for a ROM-native menu outside the title provider. */
public interface NativeMenuScreen {
    default com.openggf.game.GameMode mode() { return com.openggf.game.GameMode.OPTIONS; }
    void initialize();
    void update(InputHandler input);
    /**
     * Completes synchronous transition setup after this update's outer PLC frame
     * has finished. Loads may run their existing entry iterations here; this is
     * not another input, menu, or gameplay update.
     */
    default void afterFrame() { }
    void draw();
    void setClearColor();
    void reset();
    Exit consumeExit();

    enum Destination { NONE, LEVEL, COMPETITION, TITLE_SCREEN }

    /** A launch team applies to this launch only; native options retain emerald progress. */
    record Exit(Destination destination, GameplayLaunchTeam team, boolean resetProgress, int zone, int act) {
        public Exit(Destination destination, GameplayLaunchTeam team, boolean resetProgress) {
            this(destination, team, resetProgress, 0, 0);
        }
        public static final Exit NONE = new Exit(Destination.NONE, null, false);
        public Exit {
            java.util.Objects.requireNonNull(destination, "destination");
            if (zone < 0 || act < 0) throw new IllegalArgumentException("Negative native launch zone/act");
        }
    }
}
