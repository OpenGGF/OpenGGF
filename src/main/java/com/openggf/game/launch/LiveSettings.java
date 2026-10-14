package com.openggf.game.launch;

import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.ConfigType;
import com.openggf.configuration.SonicConfiguration;

import java.util.Set;

import static com.openggf.configuration.SonicConfiguration.*;

/**
 * Settings a game suspended to the master title picks up when it resumes. Everything else is
 * part of how the game was started (characters, Borrow from, ROMs, audio core, region, frame
 * rate) and waits for the next launch; {@link SuspendedSettings} pins those.
 *
 * <p>A setting belongs here only when its readers take the new value without a reload: they
 * read it each frame, or the resume re-applies it. Aspect is live only when the game is
 * resumed into a level, where the camera, parallax, background and tilemap widths are
 * refreshed together; title, special-stage and ending screens size themselves on entry.
 */
public final class LiveSettings {
    /** Read each frame, or re-read on resume by the camera, rewind and debug owners. */
    private static final Set<SonicConfiguration> LIVE = Set.of(
            DEBUG_VIEW_ENABLED, EDITOR_ENABLED,
            LIVE_REWIND_ENABLED, REWIND_HISTORY_SECONDS, LIVE_REWIND_VHS_TEAR_BANDS,
            WIDESCREEN_DEADZONE_MODE, DISPLAY_WINDOW_AUTOSIZE,
            CONTROLLER_ENABLED, CONTROLLER_DEADZONE, CONTROLLER_PLAYER1, CONTROLLER_PLAYER2,
            // Read when a recording starts.
            CAPTURE_OUTPUT_DIR, CAPTURE_SCALE, CAPTURE_FPS, CAPTURE_CODEC, CAPTURE_AUDIO_CODEC,
            CAPTURE_QUEUE_BUDGET_MB, CAPTURE_ENCODER_THREADS, CAPTURE_ENCODER_PRESET, CAPTURE_CONTAINER,
            CAPTURE_FFMPEG_PASS1_ARGS, CAPTURE_FFMPEG_PASS2_ARGS);

    private LiveSettings() { }

    /** True when a resumed game takes this setting; {@code inLevel} admits the aspect. */
    public static boolean appliesOnResume(SonicConfiguration key, boolean inLevel) {
        if (key == DISPLAY_ASPECT) return inLevel;
        // Key bindings are looked up when pressed (InputBindingFactory's supplier, the shortcut owners).
        var meta = ConfigCatalog.meta(key);
        return LIVE.contains(key) || meta != null && meta.type() == ConfigType.KEY;
    }
}
