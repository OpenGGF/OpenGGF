package com.openggf.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openggf.configuration.ConfigKeyMeta.derived;
import static com.openggf.configuration.ConfigKeyMeta.of;
import static com.openggf.configuration.ConfigKeyMeta.ofEnum;
import static com.openggf.configuration.ConfigType.BOOL;
import static com.openggf.configuration.ConfigType.DOUBLE;
import static com.openggf.configuration.ConfigType.INT;
import static com.openggf.configuration.ConfigType.KEY;
import static com.openggf.configuration.ConfigType.STRING;
import static com.openggf.configuration.SonicConfiguration.*;

/**
 * Metadata table over {@link SonicConfiguration}. The insertion order of
 * {@link #META} is the on-disk emit order: all normal sections first, then the
 * fenced {@code debug.*} block. DERIVED keys (never persisted) are included for
 * completeness but excluded from {@link #emitOrder()}.
 */
public final class ConfigCatalog {

    private static final Map<SonicConfiguration, ConfigKeyMeta> META = new LinkedHashMap<>();
    private static final List<SonicConfiguration> EMIT_ORDER;
    private static final Map<String, SonicConfiguration> BY_PATH = new LinkedHashMap<>();
    private static final Map<String, String> SECTION_TITLES = new LinkedHashMap<>();

    private static void put(SonicConfiguration key, ConfigKeyMeta meta) {
        META.put(key, meta);
    }

    static {
        // ---- DERIVED (never on disk) ----
        put(SCREEN_WIDTH_PIXELS, derived(INT, "Derived screen width in pixels (from display.aspect)"));
        put(SCREEN_HEIGHT_PIXELS, derived(INT, "Derived screen height in pixels (always 224)"));

        // ───────────────── NORMAL SECTIONS ─────────────────

        // display
        put(DISPLAY_ASPECT, ofEnum("display", "aspect",
                "Display aspect preset; resolves screen pixel width, height stays 224",
                Set.of("NATIVE_4_3", "WIDE_16_10", "WIDE_16_9", "ULTRA_21_9", "SUPER_32_9")));
        put(DISPLAY_WINDOW_AUTOSIZE, of("display", "windowAutosize", BOOL,
                "Derive the window size from the aspect preset at the 2x baseline"));
        put(WIDESCREEN_DEADZONE_MODE, ofEnum("display", "deadzoneMode",
                "Camera horizontal deadzone behaviour on wide screens",
                Set.of("CENTER_SCALED", "PROPORTIONAL")));
        put(DISPLAY_COLOR_PROFILE, ofEnum("display", "colorProfile",
                "Display-only color profile for Mega Drive palette presentation",
                Set.of("RAW_RGB", "MD_ANALOG", "NTSC_SOFT")));
        put(DISPLAY_COLOR_PROFILE_TOGGLE_KEY, of("display", "colorProfileToggleKey", KEY,
                "Runtime key to cycle the display color profile"));
        put(FPS, of("display", "fps", INT, "Frames per second to render (changes game speed)"));

        // input (player-agnostic leaf first, then per-player subsections)
        put(PAUSE_KEY, of("input", "pause", KEY, "Toggle pause"));
        put(UP, of("input.player1", "up", KEY, "Player 1: look up"));
        put(DOWN, of("input.player1", "down", KEY, "Player 1: crouch/roll"));
        put(LEFT, of("input.player1", "left", KEY, "Player 1: move left"));
        put(RIGHT, of("input.player1", "right", KEY, "Player 1: move right"));
        put(JUMP, of("input.player1", "jump", KEY, "Player 1: jump"));
        put(P2_UP, of("input.player2", "up", KEY, "Player 2: look up"));
        put(P2_DOWN, of("input.player2", "down", KEY, "Player 2: crouch/roll"));
        put(P2_LEFT, of("input.player2", "left", KEY, "Player 2: move left"));
        put(P2_RIGHT, of("input.player2", "right", KEY, "Player 2: move right"));
        put(P2_JUMP, of("input.player2", "jump", KEY, "Player 2: jump"));
        put(P2_START, of("input.player2", "start", KEY, "Player 2: start"));

        // audio
        put(AUDIO_ENABLED, of("audio", "enabled", BOOL, "Enable music and SFX"));
        put(REGION, ofEnum("audio", "region", "Region for audio timing", Set.of("NTSC", "PAL")));
        put(DAC_INTERPOLATE, of("audio", "dacInterpolate", BOOL, "DAC interpolation (smoother sound)"));
        put(AUDIO_INTERNAL_RATE_OUTPUT, of("audio", "internalRateOutput", BOOL,
                "Output audio at the internal YM2612 rate (~53kHz)"));
        put(PSG_NOISE_SHIFT_EVERY_TOGGLE, of("audio", "psgNoiseShiftEveryToggle", BOOL,
                "PSG noise LFSR clock mode: true=shift on every toggle (MAME), false=positive edges (GPGX)"));
        put(FM6_DAC_OFF, of("audio", "fm6DacOff", BOOL,
                "Mute FM6 when a note plays on it while DAC is enabled (SMPSPlay parity hack)"));

        // characters
        put(MAIN_CHARACTER_CODE, of("characters", "main", STRING, "Sprite code of the main playable character"));
        put(SIDEKICK_CHARACTER_CODE, of("characters", "sidekick", STRING,
                "Sprite code of the CPU sidekick; empty string disables the sidekick"));
        put(DATA_SELECT_EXTRA_PLAYER_COMBOS, of("characters", "dataSelectExtraCombos", STRING,
                "Semicolon-separated extra player combos for the data select screen"));

        // roms
        put(SONIC_1_ROM, of("roms", "sonic1", STRING, "Filename of the Sonic 1 ROM"));
        put(SONIC_2_ROM, of("roms", "sonic2", STRING, "Filename of the Sonic 2 ROM"));
        put(SONIC_3K_ROM, of("roms", "sonic3k", STRING, "Filename of the Sonic 3&K ROM"));
        put(DEFAULT_ROM, ofEnum("roms", "default", "Which game to load by default",
                Set.of("s1", "s2", "s3k")));

        // startup
        put(TITLE_SCREEN_ON_STARTUP, of("startup", "titleScreen", BOOL, "Show the title screen on startup"));
        put(MASTER_TITLE_SCREEN_ON_STARTUP, of("startup", "masterTitleScreen", BOOL,
                "Show the master (game-selection) title screen on startup"));
        put(SHOW_LEGAL_DISCLAIMER_ON_STARTUP, of("startup", "legalDisclaimer", BOOL,
                "Show the legal disclaimer screen before the master title screen"));

        // rewind (player-facing live rewind)
        put(LIVE_REWIND_ENABLED, of("rewind", "liveEnabled", BOOL,
                "Enable held-key rewind during ordinary live play"));
        put(LIVE_REWIND_KEY, of("rewind", "liveKey", KEY, "Key held to rewind during live play"));
        put(LIVE_REWIND_TAPE_COAST_ENABLED, of("rewind", "tapeCoastEnabled", BOOL,
                "Continue rewinding with a decelerating tape-coast after key release"));
        put(LIVE_REWIND_TAPE_COAST_ACCELERATION, of("rewind", "tapeCoastAcceleration", DOUBLE,
                "Per-tick speed increase while tape-coast is held"));
        put(LIVE_REWIND_TAPE_COAST_DECELERATION, of("rewind", "tapeCoastDeceleration", DOUBLE,
                "Per-tick speed decrease after release"));
        put(LIVE_REWIND_TAPE_COAST_MAX_STEPS, of("rewind", "tapeCoastMaxSteps", DOUBLE,
                "Maximum rewind steps per tick"));
        put(LIVE_REWIND_TAPE_COAST_MIN_STEPS, of("rewind", "tapeCoastMinSteps", DOUBLE,
                "Minimum rewind steps per tick; below 1.0 gives slow-motion rewind"));
        put(REWIND_AUDIO_HISTORY_LIMIT_TYPE, ofEnum("rewind", "audioHistoryLimitType",
                "How the rewind audio PCM history ring is sized", Set.of("time", "size")));
        put(REWIND_AUDIO_HISTORY_SECONDS, of("rewind", "audioHistorySeconds", INT,
                "Seconds of PCM history kept when audioHistoryLimitType=time"));
        put(REWIND_AUDIO_HISTORY_SIZE_MB, of("rewind", "audioHistorySizeMb", INT,
                "Megabytes of PCM history kept when audioHistoryLimitType=size"));

        // crossGame (user-facing donation toggles)
        put(CROSS_GAME_FEATURES_ENABLED, of("crossGame", "enabled", BOOL,
                "Enable cross-game feature donation (e.g. S2 sprites in S1)"));
        put(CROSS_GAME_SOURCE, ofEnum("crossGame", "source",
                "Donor game for cross-game features", Set.of("s2", "s3k")));

        // discord
        put(DISCORD_RICH_PRESENCE_ENABLED, of("discord", "enabled", BOOL,
                "Enable Discord Rich Presence updates"));
        put(DISCORD_RICH_PRESENCE_SHOW_TIMER, of("discord", "showTimer", BOOL,
                "Show the level timer in Rich Presence"));
        put(DISCORD_RICH_PRESENCE_SHOW_ZONE, of("discord", "showZone", BOOL,
                "Show the zone and act in Rich Presence"));

        // ───────────────── DEBUG BLOCK ─────────────────

        // debug.flags
        put(DEBUG_VIEW_ENABLED, of("debug.flags", "debugView", BOOL, "Show the on-screen debug HUD"));
        put(EDITOR_ENABLED, of("debug.flags", "editor", BOOL, "Allow entering the level editor from gameplay"));
        put(DEBUG_COLLISION_VIEW_ENABLED, of("debug.flags", "collisionView", BOOL,
                "Draw the collision overlay"));

        // debug.keys
        put(TEST, of("debug.keys", "test", KEY, "Debug-only test button"));
        put(NEXT_ACT, of("debug.keys", "nextAct", KEY, "Advance to the next act"));
        put(NEXT_ZONE, of("debug.keys", "nextZone", KEY, "Advance to the next zone"));
        put(DEBUG_MODE_KEY, of("debug.keys", "debugMode", KEY, "Toggle debug movement mode"));
        put(FRAME_STEP_KEY, of("debug.keys", "frameStep", KEY, "Step forward one frame while paused"));
        put(DEBUG_LAST_CHECKPOINT_KEY, of("debug.keys", "lastCheckpoint", KEY,
                "Teleport to the last checkpoint"));
        put(LEVEL_SELECT_KEY, of("debug.keys", "levelSelect", KEY, "Open the level select screen"));
        put(SUPER_SONIC_DEBUG_KEY, of("debug.keys", "superSonic", KEY, "Toggle Super Sonic debug mode"));
        put(GIVE_EMERALDS_KEY, of("debug.keys", "giveEmeralds", KEY, "Give all chaos emeralds"));
        put(SPECIAL_STAGE_KEY, of("debug.keys", "specialStage", KEY, "Toggle special stage mode"));
        put(SPECIAL_STAGE_COMPLETE_KEY, of("debug.keys", "specialStageComplete", KEY,
                "Complete the special stage with an emerald"));
        put(SPECIAL_STAGE_FAIL_KEY, of("debug.keys", "specialStageFail", KEY, "Fail the special stage"));
        put(SPECIAL_STAGE_SPRITE_DEBUG_KEY, of("debug.keys", "specialStageSpriteDebug", KEY,
                "Toggle the special stage sprite debug viewer"));
        put(SPECIAL_STAGE_PLANE_DEBUG_KEY, of("debug.keys", "specialStagePlaneDebug", KEY,
                "Cycle special stage plane visibility debug modes"));

        // debug.startup
        put(LEVEL_SELECT_ON_STARTUP, of("debug.startup", "levelSelectOnStartup", BOOL,
                "Open Level Select on startup instead of loading the first zone"));
        put(S3K_SKIP_INTROS, of("debug.startup", "s3kSkipIntros", BOOL,
                "Skip S3K zone intro sequences (AIZ biplane, etc.)"));

        // debug.playback
        put(PLAYBACK_MOVIE_PATH, of("debug.playback", "moviePath", STRING,
                "Path to a BizHawk BK2 movie for playback debugging"));
        put(PLAYBACK_TOGGLE_KEY, of("debug.playback", "toggleKey", KEY, "Toggle playback mode"));
        put(PLAYBACK_LOAD_KEY, of("debug.playback", "loadKey", KEY, "Load/reload the BK2 movie"));
        put(PLAYBACK_PLAY_PAUSE_KEY, of("debug.playback", "playPauseKey", KEY, "Toggle playback play/pause"));
        put(PLAYBACK_STEP_BACK_KEY, of("debug.playback", "stepBackKey", KEY, "Step the cursor back one frame"));
        put(PLAYBACK_STEP_FORWARD_KEY, of("debug.playback", "stepForwardKey", KEY,
                "Step the cursor forward one frame"));
        put(PLAYBACK_JUMP_BACK_KEY, of("debug.playback", "jumpBackKey", KEY,
                "Jump the cursor back by a larger interval"));
        put(PLAYBACK_JUMP_FORWARD_KEY, of("debug.playback", "jumpForwardKey", KEY,
                "Jump the cursor forward by a larger interval"));
        put(PLAYBACK_FAST_RATE_KEY, of("debug.playback", "fastRateKey", KEY,
                "Cycle playback rate (1x/2x/4x/8x)"));
        put(PLAYBACK_RESET_TO_START_KEY, of("debug.playback", "resetToStartKey", KEY,
                "Reset the cursor to the start offset"));
        put(PLAYBACK_START_OFFSET_FRAME, of("debug.playback", "startOffsetFrame", INT,
                "Starting frame offset for BK2 playback"));

        // debug.traceRewind
        put(TRACE_REWIND_KEY, of("debug.traceRewind", "key", KEY,
                "Key held in Trace Test Mode to rewind deterministic engine state"));

        // debug.traceRender (Trace Test Mode + capture visibility)
        put(TRACE_SHOW_DESYNC_GHOSTS, of("debug.traceRender", "showDesyncGhosts", BOOL,
                "Render the desync ghost(s) in Trace Test Mode and trace capture"));
        put(TRACE_SHOW_GAME_HUD, of("debug.traceRender", "showGameHud", BOOL,
                "Render the game HUD (rings/score/time) during trace replay and capture"));
        put(TRACE_SHOW_DEBUG_HUD, of("debug.traceRender", "showDebugHud", BOOL,
                "Render the debug HUD during trace replay and capture (per-panel toggles still apply)"));

        // debug.testMode
        put(TEST_MODE_ENABLED, of("debug.testMode", "enabled", BOOL,
                "Replace the master title with the trace picker (dev-only)"));
        put(TRACE_CATALOG_DIR, of("debug.testMode", "catalogDir", STRING,
                "Directory scanned for traces when test mode is enabled"));

        // debug.crossGame (debug tooling)
        put(CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_OVERRIDE, of("debug.crossGame",
                "s1DataSelectImageGenOverride", BOOL, "Force regeneration of the S1 data-select image cache"));
        put(CROSS_GAME_S2_DATA_SELECT_IMAGE_GEN_OVERRIDE, of("debug.crossGame",
                "s2DataSelectImageGenOverride", BOOL, "Force regeneration of the S2 data-select image cache"));
        put(CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY, of("debug.crossGame",
                "s1DataSelectImageCoordLogKey", KEY,
                "Log the current camera position as an S1 data-select preview override"));

        // debug.window (DEPRECATED manual window/scale)
        put(SCREEN_WIDTH, of("debug.window", "width", INT,
                "DEPRECATED manual window width; used only when display.windowAutosize=false"));
        put(SCREEN_HEIGHT, of("debug.window", "height", INT,
                "DEPRECATED manual window height; used only when display.windowAutosize=false"));
        put(SCALE, of("debug.window", "scale", DOUBLE,
                "DEPRECATED AWT debug-viewer scale factor"));

        // ---- Derived index structures ----
        List<SonicConfiguration> order = new ArrayList<>();
        for (Map.Entry<SonicConfiguration, ConfigKeyMeta> e : META.entrySet()) {
            ConfigKeyMeta m = e.getValue();
            if (m.persisted()) {
                order.add(e.getKey());
                BY_PATH.put(m.path(), e.getKey());
            }
        }
        EMIT_ORDER = List.copyOf(order);

        // Top-level normal sections only. debug.* sub-sections are intentionally untitled
        // (the writer fences the whole debug block with a single banner instead).
        SECTION_TITLES.put("display", "Display");
        SECTION_TITLES.put("input", "Input");
        SECTION_TITLES.put("audio", "Audio");
        SECTION_TITLES.put("characters", "Characters");
        SECTION_TITLES.put("roms", "ROMs");
        SECTION_TITLES.put("startup", "Startup");
        SECTION_TITLES.put("rewind", "Rewind (live)");
        SECTION_TITLES.put("crossGame", "Cross-Game");
        SECTION_TITLES.put("discord", "Discord Rich Presence");
    }

    private ConfigCatalog() {
    }

    public static ConfigKeyMeta meta(SonicConfiguration key) {
        return META.get(key);
    }

    /** Persisted keys only, in on-disk emit order (insertion order of META). */
    public static List<SonicConfiguration> emitOrder() {
        return EMIT_ORDER;
    }

    /** Reverse lookup: dotted {@code section.leaf} path → key, or {@code null} if unknown. */
    public static SonicConfiguration byPath(String path) {
        return BY_PATH.get(path);
    }

    /**
     * Human title for a TOP-LEVEL section (the first dotted segment, e.g. {@code "input"} or
     * {@code "audio"}) — pass the first segment, not a full sub-section path like
     * {@code "input.player1"}. Returns the raw name if no title is registered.
     */
    public static String title(String topLevelSection) {
        return SECTION_TITLES.getOrDefault(topLevelSection, topLevelSection);
    }
}
