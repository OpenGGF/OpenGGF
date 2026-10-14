package com.openggf.game.launch;

import com.openggf.configuration.SettingTiming;
import com.openggf.configuration.SonicConfiguration;

import java.util.Set;

import static com.openggf.configuration.SonicConfiguration.*;

/**
 * Settings a game suspended to the master title picks up when it resumes: every
 * {@link SettingTiming#NOW} setting, plus the game-start settings a resume refreshes. Everything
 * else is part of how the game was started (characters, Borrow from, audio core, region) or needs
 * a restart, and waits; {@link SuspendedSettings} pins those.
 *
 * <p>A setting belongs here only when its readers take the new value without a reload: they
 * read it each frame, or the resume re-applies it. Aspect is live only when the game is
 * resumed into a level, where the camera, parallax, background and tilemap widths are
 * refreshed together; title, special-stage and ending screens size themselves on entry.
 */
public final class LiveSettings {
    /**
     * Settings that otherwise wait for a game start ({@link SettingTiming#NEXT_GAME}) but whose
     * readers a resume refreshes: rewind and debug are read per frame (Engine's cached debug flag
     * is refreshed), and the camera re-reads its deadzone and the window its autosize when the
     * display size is re-applied.
     */
    private static final Set<SonicConfiguration> RESUMABLE = Set.of(
            DEBUG_VIEW_ENABLED, LIVE_REWIND_ENABLED, WIDESCREEN_DEADZONE_MODE, DISPLAY_WINDOW_AUTOSIZE);

    private LiveSettings() { }

    /** True when a resumed game takes this setting; {@code inLevel} admits the aspect. */
    public static boolean appliesOnResume(SonicConfiguration key, boolean inLevel) {
        if (key == DISPLAY_ASPECT) return inLevel;
        return switch (SettingTiming.of(key)) {
            case NOW -> true;
            case NEXT_GAME -> RESUMABLE.contains(key);
            case RESTART -> false;
        };
    }
}
