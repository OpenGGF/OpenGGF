package com.openggf.configuration;

/**
 * When a saved setting takes effect, as shown on Settings rows. The switch in {@link #of} has no
 * default, so a new {@link SonicConfiguration} key does not compile until someone decides, from
 * where it is read, whether it needs a restart.
 *
 * <p>Classified from each key's readers (2026-10-15): {@link #NOW} keys are read when used (key
 * bindings through {@code InputBindingFactory}'s per-frame supplier, ROM paths rescanned on save,
 * capture settings read when a recording starts). {@link #NEXT_GAME} keys are read when a game or
 * level starts (a new audio backend per launch, the team and donor, the launch profile, rewind
 * tuning when the recorder installs). {@link #RESTART} keys are read once when OpenGGF starts:
 * frame rate and window size (Engine construction), display shaders and the colour profile
 * (display initialisation), Discord (GameLoop construction), the rewind VHS pass, start-up
 * screens, and turning sound off (the title's audio backend stays open across launches).
 */
public enum SettingTiming {
    NOW,
    NEXT_GAME,
    RESTART;

    public static SettingTiming of(SonicConfiguration key) {
        return switch (key) {
            case HUB_CONTINUE_S1_CHARACTER, HUB_CONTINUE_S2_CHARACTER, HUB_CONTINUE_S3K_CHARACTER,
                    AUDIO_HUB_MUSIC_ENABLED, AUDIO_HUB_MUSIC_VOLUME, HUB_LAST_GAME,
                    MULTIPLAYER_CENSOR_PROFANITY, MULTIPLAYER_CHAT_DISABLED,
                    HUB_CONTINUE_S1_ZONE, HUB_CONTINUE_S1_ACT, HUB_CONTINUE_S2_ZONE, HUB_CONTINUE_S2_ACT,
                    HUB_CONTINUE_S3K_ZONE, HUB_CONTINUE_S3K_ACT,
                    VERSION, SCREEN_WIDTH_PIXELS, SCREEN_HEIGHT_PIXELS, CONFIG_PRESERVE_EXPLICIT_DEFAULTS,
                    UP, DOWN, LEFT, RIGHT, P1_A, P1_B, P1_C, JUMP, START,
                    P2_UP, P2_DOWN, P2_LEFT, P2_RIGHT, P2_A, P2_B, P2_C, P2_JUMP, P2_START,
                    CONTROLLER_ENABLED, CONTROLLER_DEADZONE, CONTROLLER_PLAYER1, CONTROLLER_PLAYER2,
                    PAUSE_KEY, FRAME_STEP_KEY, DEBUG_MODE_KEY, EDITOR_ENABLED,
                    TEST, NEXT_ACT, NEXT_ZONE, DEBUG_LAST_CHECKPOINT_KEY, LEVEL_SELECT_KEY,
                    SUPER_SONIC_DEBUG_KEY, GIVE_EMERALDS_KEY, HYPER_FORM_DEBUG_KEY, GIVE_SUPER_EMERALDS_KEY,
                    SPECIAL_STAGE_KEY, SPECIAL_STAGE_COMPLETE_KEY, SPECIAL_STAGE_FAIL_KEY,
                    SPECIAL_STAGE_SPRITE_DEBUG_KEY, SPECIAL_STAGE_PLANE_DEBUG_KEY,
                    PLAYBACK_MOVIE_PATH, PLAYBACK_TOGGLE_KEY, PLAYBACK_LOAD_KEY, PLAYBACK_PLAY_PAUSE_KEY,
                    PLAYBACK_STEP_BACK_KEY, PLAYBACK_STEP_FORWARD_KEY, PLAYBACK_JUMP_BACK_KEY,
                    PLAYBACK_JUMP_FORWARD_KEY, PLAYBACK_FAST_RATE_KEY, PLAYBACK_RESET_TO_START_KEY,
                    PLAYBACK_START_OFFSET_FRAME, RECORDING_RECORD_KEY, TRACE_REWIND_KEY,
                    CAPTURE_OUTPUT_DIR, CAPTURE_SCALE, CAPTURE_FPS, CAPTURE_CODEC, CAPTURE_AUDIO_CODEC,
                    CAPTURE_CONTAINER, CAPTURE_QUEUE_BUDGET_MB, CAPTURE_ENCODER_THREADS, CAPTURE_ENCODER_PRESET,
                    CAPTURE_FFMPEG_PASS1_ARGS, CAPTURE_FFMPEG_PASS2_ARGS, CAPTURE_TOGGLE_KEY,
                    LIVE_REWIND_KEY, LIVE_REWIND_HALF_SPEED_KEY, LIVE_REWIND_DOUBLE_SPEED_KEY,
                    LIVE_REWIND_VHS_TEAR_BANDS, REWIND_HISTORY_SECONDS, SKIP_MOD_ZONE_TITLE_CARDS,
                    SONIC_1_ROM, SONIC_2_ROM, SONIC_3K_ROM, ROMS_DIRECTORY, ROMS_PREFER_COMPOSITE,
                    TRACE_CATALOG_DIR, DISPLAY_WINDOW_MODE, DISPLAY_SCALING,
                    // Applied to the live palettes after saving (Full/Limited RGB range).
                    DISPLAY_COLOR_EXPANSION -> NOW;
            case DISPLAY_ASPECT, WIDESCREEN_DEADZONE_MODE, DISPLAY_HUD_ANCHOR, DISPLAY_HUD_TIME_HUNDREDTHS,
                    DISPLAY_WINDOW_AUTOSIZE, LOAD_TIME_SIMULATION,
                    REGION, DAC_INTERPOLATE, AUDIO_INTERNAL_RATE_OUTPUT, AUDIO_FM_CORE, FM6_DAC_OFF,
                    MAIN_CHARACTER_CODE, SIDEKICK_CHARACTER_CODE, DATA_SELECT_EXTRA_PLAYER_COMBOS,
                    DEBUG_VIEW_ENABLED, LIVE_REWIND_ENABLED, LIVE_REWIND_DETERMINISM_AUDIT,
                    LIVE_REWIND_TAPE_COAST_ENABLED, LIVE_REWIND_TAPE_COAST_ACCELERATION,
                    LIVE_REWIND_TAPE_COAST_DECELERATION, LIVE_REWIND_TAPE_COAST_MAX_STEPS,
                    LIVE_REWIND_TAPE_COAST_MIN_STEPS, REWIND_AUDIO_HISTORY_LIMIT_TYPE,
                    REWIND_AUDIO_HISTORY_SECONDS, REWIND_AUDIO_HISTORY_SIZE_MB,
                    TITLE_SCREEN_ON_STARTUP, LEVEL_SELECT_ON_STARTUP, S3K_SKIP_INTROS,
                    TRACE_SHOW_DESYNC_GHOSTS, TRACE_SHOW_GAME_HUD, TRACE_SHOW_DEBUG_HUD,
                    CROSS_GAME_FEATURES_ENABLED, CROSS_GAME_SOURCE,
                    CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_OVERRIDE, CROSS_GAME_S2_DATA_SELECT_IMAGE_GEN_OVERRIDE,
                    CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY,
                    LAUNCH_S1_REWIND, LAUNCH_S1_CROSS_GAME_SOURCE, LAUNCH_S1_DEBUG_TOOLS, LAUNCH_S1_ASPECT,
                    LAUNCH_S1_MAIN_CHARACTER, LAUNCH_S1_SIDEKICK,
                    LAUNCH_S2_REWIND, LAUNCH_S2_CROSS_GAME_SOURCE, LAUNCH_S2_DEBUG_TOOLS, LAUNCH_S2_ASPECT,
                    LAUNCH_S2_MAIN_CHARACTER, LAUNCH_S2_SIDEKICK,
                    LAUNCH_S3K_REWIND, LAUNCH_S3K_CROSS_GAME_SOURCE, LAUNCH_S3K_DEBUG_TOOLS, LAUNCH_S3K_ASPECT,
                    LAUNCH_S3K_MAIN_CHARACTER, LAUNCH_S3K_SIDEKICK -> NEXT_GAME;
            // SCALE has no runtime reader today; kept with the other window settings.
            case SCREEN_WIDTH, SCREEN_HEIGHT, SCALE, FPS, AUDIO_ENABLED,
                    DISPLAY_COLOR_PROFILE, DISPLAY_COLOR_PROFILE_TOGGLE_KEY,
                    DISPLAY_SHADER_LIBRARY_ROOT, DISPLAY_SHADER_SELECTION, DISPLAY_SHADER_NEXT_KEY,
                    DISPLAY_SHADER_PREVIOUS_KEY, DISPLAY_SHADER_PICKER_KEY, DISPLAY_SHADER_DEFAULT_PHASE,
                    LIVE_REWIND_VHS_EFFECT, DEFAULT_ROM, MASTER_TITLE_SCREEN_ON_STARTUP,
                    SHOW_LEGAL_DISCLAIMER_ON_STARTUP, TEST_MODE_ENABLED,
                    DISCORD_RICH_PRESENCE_ENABLED, DISCORD_RICH_PRESENCE_SHOW_TIMER,
                    DISCORD_RICH_PRESENCE_SHOW_ZONE -> RESTART;
        };
    }
}
