package com.openggf.game;

import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.ConfigKeyMeta;
import com.openggf.configuration.ConfigType;
import com.openggf.configuration.SonicConfiguration;

import java.util.Locale;

/** Presentation names; persisted keys and values remain owned by ConfigCatalog. */
final class EngineSettingLabels {
    private EngineSettingLabels() { }

    static String label(SonicConfiguration key) {
        return switch (key) {
            case DISPLAY_ASPECT -> "Aspect ratio";
            case DISPLAY_WINDOW_MODE -> "Window mode";
            case DISPLAY_SCALING -> "Scaling";
            case DISPLAY_WINDOW_AUTOSIZE -> "Automatic window size";
            case SCREEN_WIDTH -> "Window width";
            case SCREEN_HEIGHT -> "Window height";
            case SCALE -> "Debug viewer scale";
            case FPS -> "Game speed (FPS)";
            case DISPLAY_SHADER_LIBRARY_ROOT -> "Shader library folder";
            case DISPLAY_SHADER_SELECTION -> "Display shader";
            case DISPLAY_SHADER_NEXT_KEY -> "Next shader shortcut";
            case DISPLAY_SHADER_PREVIOUS_KEY -> "Previous shader shortcut";
            case DISPLAY_SHADER_PICKER_KEY -> "Shader picker shortcut";
            case DISPLAY_SHADER_DEFAULT_PHASE -> "Shader rendering phase";
            case DISPLAY_COLOR_PROFILE -> "Colour profile";
            case DISPLAY_COLOR_EXPANSION -> "Colour expansion";
            case DISPLAY_COLOR_PROFILE_TOGGLE_KEY -> "Colour profile shortcut";
            case WIDESCREEN_DEADZONE_MODE -> "Wide camera movement";
            case DISPLAY_HUD_ANCHOR -> "Widescreen HUD position";
            case DISPLAY_HUD_TIME_HUNDREDTHS -> "Time in hundredths";
            case AUDIO_ENABLED -> "Music and sound effects";
            case AUDIO_HUB_MUSIC_ENABLED -> "Hub music";
            case AUDIO_HUB_MUSIC_VOLUME -> "Hub music volume";
            case MULTIPLAYER_CENSOR_PROFANITY -> "Censor profanity";
            case MULTIPLAYER_CHAT_DISABLED -> "Disable multiplayer chat";
            case AUDIO_FM_CORE -> "FM sound chip";
            case REGION -> "Audio region";
            case DAC_INTERPOLATE -> "Smooth sample playback";
            case AUDIO_INTERNAL_RATE_OUTPUT -> "Native sound chip sample rate";
            case FM6_DAC_OFF -> "FM channel 6 compatibility";
            case PAUSE_KEY -> "Engine pause shortcut";
            case CONTROLLER_ENABLED -> "Enable controllers";
            case CONTROLLER_DEADZONE -> "Controller stick deadzone";
            case CONTROLLER_PLAYER1 -> "Player 1 controller";
            case CONTROLLER_PLAYER2 -> "Player 2 controller";
            case SONIC_1_ROM -> "Sonic 1 ROM file";
            case SONIC_2_ROM -> "Sonic 2 ROM file";
            case SONIC_3K_ROM -> "Sonic 3 & Knuckles ROM file";
            case ROMS_DIRECTORY -> "ROM folder";
            case ROMS_PREFER_COMPOSITE -> "Prefer separate S3 + S&K images";
            case DEFAULT_ROM -> "Default game";
            case MAIN_CHARACTER_CODE -> "Direct-launch character";
            case SIDEKICK_CHARACTER_CODE -> "Direct-launch sidekick";
            case DATA_SELECT_EXTRA_PLAYER_COMBOS -> "Extra character teams";
            case MASTER_TITLE_SCREEN_ON_STARTUP -> "Start at the OpenGGF hub";
            case TITLE_SCREEN_ON_STARTUP -> "Show the original title screen";
            case SHOW_LEGAL_DISCLAIMER_ON_STARTUP -> "Show the startup disclaimer";
            case LEVEL_SELECT_ON_STARTUP -> "Start at level select";
            case LIVE_REWIND_ENABLED -> "Rewind";
            case DEBUG_VIEW_ENABLED -> "Debug tools";
            case LOAD_TIME_SIMULATION -> "ROM loading timing";
            case CONFIG_PRESERVE_EXPLICIT_DEFAULTS -> "Keep explicit default settings";
            case TEST_MODE_ENABLED -> "Start in trace replay mode";
            case TRACE_CATALOG_DIR -> "Trace replay folder";
            case PLAYBACK_MOVIE_PATH -> "Playback movie file";
            case CAPTURE_OUTPUT_DIR -> "Recording output folder";
            case CAPTURE_SCALE -> "Video scale";
            case CAPTURE_FPS -> "Video frame rate";
            case CAPTURE_CODEC -> "Video codec";
            case CAPTURE_AUDIO_CODEC -> "Audio codec";
            case CAPTURE_CONTAINER -> "File type";
            case CAPTURE_QUEUE_BUDGET_MB -> "Encoder queue memory";
            case CAPTURE_ENCODER_THREADS -> "Encoder threads";
            case CAPTURE_ENCODER_PRESET -> "Encoder speed preset";
            case CAPTURE_FFMPEG_PASS1_ARGS -> "Encoder command arguments";
            case CAPTURE_FFMPEG_PASS2_ARGS -> "Audio mux command arguments";
            case LIVE_REWIND_KEY -> "Rewind shortcut";
            case LIVE_REWIND_HALF_SPEED_KEY -> "Half-speed rewind modifier";
            case LIVE_REWIND_DOUBLE_SPEED_KEY -> "Double-speed rewind modifier";
            case CAPTURE_TOGGLE_KEY -> "Video recording shortcut";
            // Advanced: authored names, so acronyms and the game names keep their casing.
            case SKIP_MOD_ZONE_TITLE_CARDS -> "Skip mod zone title cards";
            case LIVE_REWIND_TAPE_COAST_ENABLED -> "Tape coast";
            case LIVE_REWIND_TAPE_COAST_ACCELERATION -> "Tape coast acceleration";
            case LIVE_REWIND_TAPE_COAST_DECELERATION -> "Tape coast deceleration";
            case LIVE_REWIND_TAPE_COAST_MAX_STEPS -> "Tape coast top speed";
            case LIVE_REWIND_TAPE_COAST_MIN_STEPS -> "Tape coast lowest speed";
            case LIVE_REWIND_VHS_EFFECT -> "VHS rewind effect";
            case LIVE_REWIND_VHS_TEAR_BANDS -> "VHS tear bands";
            case REWIND_HISTORY_SECONDS -> "Rewind history (seconds)";
            case REWIND_AUDIO_HISTORY_LIMIT_TYPE -> "Rewind sound limit";
            case REWIND_AUDIO_HISTORY_SECONDS -> "Rewind sound length (seconds)";
            case REWIND_AUDIO_HISTORY_SIZE_MB -> "Rewind sound memory (MB)";
            case CROSS_GAME_FEATURES_ENABLED -> "Cross-game features";
            case CROSS_GAME_SOURCE -> "Cross-game donor";
            case DISCORD_RICH_PRESENCE_ENABLED -> "Discord Rich Presence";
            case DISCORD_RICH_PRESENCE_SHOW_TIMER -> "Show level timer in Discord";
            case DISCORD_RICH_PRESENCE_SHOW_ZONE -> "Show zone and act in Discord";
            case EDITOR_ENABLED -> "Level editor in gameplay";
            case TEST -> "Debug test shortcut";
            case SUPER_SONIC_DEBUG_KEY -> "Super Sonic shortcut";
            case S3K_SKIP_INTROS -> "Skip S3K zone intros";
            case PLAYBACK_TOGGLE_KEY -> "Playback mode shortcut";
            case PLAYBACK_LOAD_KEY -> "Load playback movie shortcut";
            case PLAYBACK_PLAY_PAUSE_KEY -> "Playback play / pause shortcut";
            case PLAYBACK_STEP_BACK_KEY -> "Playback step back shortcut";
            case PLAYBACK_STEP_FORWARD_KEY -> "Playback step forward shortcut";
            case PLAYBACK_JUMP_BACK_KEY -> "Playback jump back shortcut";
            case PLAYBACK_JUMP_FORWARD_KEY -> "Playback jump forward shortcut";
            case PLAYBACK_FAST_RATE_KEY -> "Playback speed shortcut";
            case PLAYBACK_RESET_TO_START_KEY -> "Playback restart shortcut";
            case PLAYBACK_START_OFFSET_FRAME -> "Playback start frame";
            case RECORDING_RECORD_KEY -> "Recording shortcut";
            case TRACE_REWIND_KEY -> "Trace rewind shortcut";
            case TRACE_SHOW_DESYNC_GHOSTS -> "Show trace desync ghosts";
            case TRACE_SHOW_GAME_HUD -> "Show game HUD in traces";
            case TRACE_SHOW_DEBUG_HUD -> "Show debug HUD in traces";
            case LIVE_REWIND_DETERMINISM_AUDIT -> "Rewind determinism audit";
            case CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_OVERRIDE -> "Rebuild Sonic 1 save-screen pictures";
            case CROSS_GAME_S2_DATA_SELECT_IMAGE_GEN_OVERRIDE -> "Rebuild Sonic 2 save-screen pictures";
            case CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY -> "Log Sonic 1 save-screen camera shortcut";
            default -> contextualLabel(ConfigCatalog.meta(key));
        };
    }

    /**
     * The label a row shows. Every label reads on its own, because a long group scrolls its
     * caption (and a dependent's parent) off screen; captions group, they do not complete names.
     */
    static String rowLabel(SonicConfiguration key) {
        return label(key);
    }

    /** Group captions on the Display and Advanced tabs, in drawing order. */
    static final java.util.List<String> GROUPS = java.util.List.of(
            "WINDOW", "PICTURE", "WIDESCREEN", "HUD",
            "SHADERS", "SPEED AND LOADING", "DIRECT LAUNCH", "REWIND", "MODS", "DISCORD",
            "PLAYBACK AND RECORDING", "TRACE REPLAY", "DEBUG", "SETTINGS FILE");

    /**
     * The caption a Display or Advanced setting sits under, from its catalog section (and, inside
     * the display section, what the setting controls); null for tabs without captions.
     */
    static String group(SonicConfiguration key) {
        ConfigKeyMeta meta = ConfigCatalog.meta(key);
        if (!meta.persisted()) return null;
        var category = com.openggf.configuration.EngineSettingsDraft.category(key);
        if (category == com.openggf.configuration.EngineSettingsDraft.Category.DISPLAY) {
            return switch (key) {
                case DISPLAY_ASPECT, DISPLAY_WINDOW_MODE, DISPLAY_SCALING, DISPLAY_WINDOW_AUTOSIZE -> "WINDOW";
                case DISPLAY_SHADER_SELECTION, DISPLAY_COLOR_PROFILE, DISPLAY_COLOR_EXPANSION -> "PICTURE";
                case WIDESCREEN_DEADZONE_MODE, DISPLAY_HUD_ANCHOR -> "WIDESCREEN";
                default -> "HUD";
            };
        }
        if (category != com.openggf.configuration.EngineSettingsDraft.Category.ADVANCED) return null;
        String section = meta.section();
        if (section.equals("display")) return key == SonicConfiguration.FPS ? "SPEED AND LOADING" : "SHADERS";
        if (section.equals("gameplay")) return "SPEED AND LOADING";
        if (section.equals("characters") || section.equals("crossGame")) return "DIRECT LAUNCH";
        if (section.equals("rewind")) return "REWIND";
        if (section.equals("mods")) return "MODS";
        if (section.equals("discord")) return "DISCORD";
        if (section.equals("debug.playback") || section.equals("debug.recording")) return "PLAYBACK AND RECORDING";
        if (section.equals("debug.traceRewind") || section.equals("debug.traceRender") || section.equals("debug.testMode"))
            return "TRACE REPLAY";
        if (section.equals("config")) return "SETTINGS FILE";
        return "DEBUG";
    }

    /**
     * The setting this one only matters under, or null. Each pairing is read that way by its owner:
     * {@code RewindSpeedController} ignores the coast parameters unless the coast is on, the VHS pass
     * exists only with the effect, {@code PcmHistoryRing} sizes by time or by size, Discord presence
     * ignores its options while off, and cross-game start-up reads the donor only when enabled.
     */
    static SonicConfiguration parent(SonicConfiguration key) {
        return switch (key) {
            case LIVE_REWIND_TAPE_COAST_ACCELERATION, LIVE_REWIND_TAPE_COAST_DECELERATION,
                    LIVE_REWIND_TAPE_COAST_MAX_STEPS, LIVE_REWIND_TAPE_COAST_MIN_STEPS -> SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED;
            case LIVE_REWIND_VHS_TEAR_BANDS -> SonicConfiguration.LIVE_REWIND_VHS_EFFECT;
            case REWIND_AUDIO_HISTORY_SECONDS, REWIND_AUDIO_HISTORY_SIZE_MB -> SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE;
            case DISCORD_RICH_PRESENCE_SHOW_TIMER, DISCORD_RICH_PRESENCE_SHOW_ZONE -> SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED;
            case CROSS_GAME_SOURCE -> SonicConfiguration.CROSS_GAME_FEATURES_ENABLED;
            default -> null;
        };
    }

    /** Whether {@code key} has an effect with its parent stored as {@code parentText}. */
    static boolean inEffect(SonicConfiguration key, String parentText) {
        return switch (key) {
            case REWIND_AUDIO_HISTORY_SECONDS -> !"size".equalsIgnoreCase(parentText);
            case REWIND_AUDIO_HISTORY_SIZE_MB -> "size".equalsIgnoreCase(parentText);
            default -> parent(key) == null || Boolean.parseBoolean(parentText);
        };
    }

    /** The help line when a dependent setting has no effect right now. */
    static String notInEffect(SonicConfiguration key) {
        SonicConfiguration parent = parent(key);
        if (parent == SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE)
            return key == SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB ? "Used only when the sound limit is Size."
                    : "Used only when the sound limit is Time.";
        return "Used only while " + label(parent) + " is on.";
    }

    /** Short player-facing help; Details retains the catalog's technical explanation. */
    static String description(SonicConfiguration key) {
        return switch (key) {
            case DISPLAY_ASPECT -> "Choose the shape of the game screen.";
            case DISPLAY_WINDOW_MODE -> "Choose windowed, borderless or exclusive fullscreen.";
            case DISPLAY_SCALING -> "Use whole-number pixels or fit the screen while keeping its proportions.";
            case DISPLAY_WINDOW_AUTOSIZE -> "Fit the window when the game screen shape changes.";
            case SCREEN_WIDTH -> "Set the window width when automatic sizing is off.";
            case SCREEN_HEIGHT -> "Set the window height when automatic sizing is off.";
            case DISPLAY_SHADER_SELECTION -> "Choose a visual effect for the game screen.";
            case WIDESCREEN_DEADZONE_MODE -> "Choose how closely the camera follows you on wide screens.";
            case AUDIO_ENABLED -> "Turn game music and sound effects on or off.";
            case MULTIPLAYER_CENSOR_PROFANITY -> "Mask listed profanity in multiplayer chat and sender names. Applies when saved.";
            case MULTIPLAYER_CHAT_DISABLED -> "Hide player chat and discard new messages. Connection and race notices stay visible.";
            case CONTROLLER_DEADZONE -> "Ignore small stick movements to prevent unwanted movement.";
            case ROMS_DIRECTORY -> "Choose the folder containing your game ROM files.";
            case CAPTURE_OUTPUT_DIR -> "Folder for recorded videos.";
            case CAPTURE_SCALE -> "Whole-pixel upscale applied to recorded videos.";
            case CAPTURE_FPS -> "Frame rate of recorded videos.";
            case CAPTURE_CODEC -> "FFV1, H264 or H265; all three are lossless.";
            case CAPTURE_AUDIO_CODEC -> "FLAC is lossless; AAC and MP3 lose detail.";
            case CAPTURE_CONTAINER -> "MKV plays every codec; MP4 is more portable.";
            case CAPTURE_QUEUE_BUDGET_MB -> "Memory that absorbs encoder stalls before the game waits.";
            case CAPTURE_ENCODER_THREADS -> "Encoder threads; Automatic uses one per core.";
            case CAPTURE_ENCODER_PRESET -> "Faster presets make larger files; quality is unchanged.";
            case START -> "Player 1 Start, the game's own pause. A pad's Start uses the engine pause.";
            case LIVE_REWIND_KEY -> "Hold to rewind during play; a pad's left bumper also rewinds.";
            case LIVE_REWIND_HALF_SPEED_KEY -> "Hold with the rewind key to rewind at half speed.";
            case LIVE_REWIND_DOUBLE_SPEED_KEY -> "Hold with the rewind key to rewind at double speed.";
            case CAPTURE_TOGGLE_KEY -> "Start or stop a video recording of play.";
            case CAPTURE_FFMPEG_PASS1_ARGS -> "The encoder's arguments; Details lists the placeholders it fills in.";
            case CAPTURE_FFMPEG_PASS2_ARGS -> "The sound mux's arguments; leave empty to save the video without sound.";
            case AUDIO_HUB_MUSIC_ENABLED -> "Play the selected game's title theme quietly in the hub.";
            case AUDIO_HUB_MUSIC_VOLUME -> "Hub music volume; it dips while a dialog is open.";
            case DAC_INTERPOLATE -> "Smooth sampled sounds such as drums; off matches the hardware.";
            case AUDIO_FM_CORE -> "Accurate copies the sound chip exactly; Fast uses less processor time.";
            case AUDIO_INTERNAL_RATE_OUTPUT -> "Play sound at the chip's own rate, about 53 kHz, without converting it.";
            case FM6_DAC_OFF -> "Silence FM channel 6 notes while that channel plays samples, as SMPSPlay does.";
            case MASTER_TITLE_SCREEN_ON_STARTUP -> "Open the OpenGGF hub when OpenGGF starts.";
            case SHOW_LEGAL_DISCLAIMER_ON_STARTUP -> "Show the legal disclaimer before the hub.";
            case DEBUG_VIEW_ENABLED -> "Allow the debug overlay; it starts hidden until you show it.";
            case DISPLAY_SHADER_NEXT_KEY -> "Switch to the next display shader during play.";
            case DISPLAY_SHADER_PREVIOUS_KEY -> "Switch to the previous display shader during play.";
            case DISPLAY_SHADER_PICKER_KEY -> "Open the searchable shader picker during play.";
            case DISPLAY_COLOR_PROFILE_TOGGLE_KEY -> "Cycle the colour profile during play.";
            case DISPLAY_COLOR_PROFILE -> "Raw RGB, a Mega Drive analogue look or a soft NTSC television look.";
            case DISPLAY_COLOR_EXPANSION -> "Full uses the whole colour range; Limited stops colours just short of full brightness.";
            case DISPLAY_HUD_ANCHOR -> "On wide screens, keep the HUD in the centred game frame or move it to the left edge.";
            case DISPLAY_HUD_TIME_HUNDREDTHS -> "Show hundredths of a second in the HUD time, as Sonic CD does.";
            case DISPLAY_SHADER_LIBRARY_ROOT -> "The folder OpenGGF searches for your own display shaders.";
            case DISPLAY_SHADER_DEFAULT_PHASE -> "The drawing stage a shader uses when it does not choose one.";
            case FPS -> "Frames per second. Changing it changes the game's speed.";
            case LOAD_TIME_SIMULATION -> "How long the original game's loading pauses last during play.";
            case CONFIG_PRESERVE_EXPLICIT_DEFAULTS -> "When an older settings file is converted, keep settings written at their default value.";
            case MAIN_CHARACTER_CODE -> "The character a direct launch plays as; the hub uses Game Setup.";
            case SIDEKICK_CHARACTER_CODE -> "The computer-controlled partner in a direct launch; leave empty for none.";
            case DATA_SELECT_EXTRA_PLAYER_COMBOS -> "Extra teams offered on the save screen, separated by semicolons.";
            case SKIP_MOD_ZONE_TITLE_CARDS -> "Skip title cards a mod's added zones cannot show.";
            case LIVE_REWIND_TAPE_COAST_ENABLED -> "Keep rewinding briefly after you let go, slowing to a stop.";
            case LIVE_REWIND_TAPE_COAST_ACCELERATION -> "How quickly rewind speeds up while you hold it.";
            case LIVE_REWIND_TAPE_COAST_DECELERATION -> "How quickly rewind slows down after you let go.";
            case LIVE_REWIND_TAPE_COAST_MAX_STEPS -> "The most frames rewind steps back each frame.";
            case LIVE_REWIND_TAPE_COAST_MIN_STEPS -> "The fewest frames rewind steps back each frame; below 1 is slow motion.";
            case LIVE_REWIND_VHS_EFFECT -> "Show a VHS picture-search effect while rewinding.";
            case LIVE_REWIND_VHS_TEAR_BANDS -> "Add the rolling tear bands to the VHS effect.";
            case REWIND_HISTORY_SECONDS -> "How many seconds of play you can rewind.";
            case REWIND_AUDIO_HISTORY_LIMIT_TYPE -> "Keep rewind sound for a length of time or up to a memory size.";
            case REWIND_AUDIO_HISTORY_SECONDS -> "Seconds of sound kept for rewind.";
            case REWIND_AUDIO_HISTORY_SIZE_MB -> "Megabytes of memory kept for rewind sound.";
            case CROSS_GAME_FEATURES_ENABLED -> "Borrow another game's characters and features in a direct launch.";
            case CROSS_GAME_SOURCE -> "The game that lends its characters and features.";
            case DISCORD_RICH_PRESENCE_ENABLED -> "Show what you are playing in Discord.";
            case DISCORD_RICH_PRESENCE_SHOW_TIMER -> "Show the level timer in Discord.";
            case DISCORD_RICH_PRESENCE_SHOW_ZONE -> "Show the zone and act in Discord.";
            case RECORDING_RECORD_KEY -> "Shift and this key open recordings; this key alone stops a recording.";
            case TRACE_REWIND_KEY -> "Hold to rewind during a trace replay.";
            case LIVE_REWIND_DETERMINISM_AUDIT -> "Replay each rewind segment and log the first difference. Doubles frame cost.";
            case TEST_MODE_ENABLED -> "Open the trace replay picker instead of the hub at start-up.";
            case TRACE_CATALOG_DIR -> "The folder searched for trace replays.";
            case CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_OVERRIDE -> "Rebuild the cached Sonic 1 save-screen pictures.";
            case CROSS_GAME_S2_DATA_SELECT_IMAGE_GEN_OVERRIDE -> "Rebuild the cached Sonic 2 save-screen pictures.";
            case CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY -> "Log the camera position as a Sonic 1 save-screen picture.";
            case S3K_SKIP_INTROS -> "Skip zone intro scenes, such as the Angel Island biplane.";
            case SCALE -> "Scale of the old debug viewer; nothing uses it now.";
            default -> ConfigCatalog.meta(key).description();
        };
    }

    private static String contextualLabel(ConfigKeyMeta meta) {
        String prefix = "";
        String section = meta.section();
        if (section.startsWith("input.player1")) prefix = "Player 1 ";
        else if (section.startsWith("input.player2")) prefix = "Player 2 ";
        else if (section.startsWith("launch.")) prefix = switch (section.substring(7)) {
            case "s1" -> "S1: ";
            case "s2" -> "S2: ";
            default -> "S3K: ";
        };
        else if (section.startsWith("discord")) prefix = "Discord: ";
        else if (section.startsWith("debug.playback")) prefix = "Playback: ";
        else if (section.startsWith("debug.trace")) prefix = "Trace: ";
        else if (section.startsWith("debug.crossGame")) prefix = "Cross-game: ";
        String words = words(meta.leaf());
        // Player buttons read as "Player 1 Up"; engine hotkeys get "shortcut".
        if (meta.type() == ConfigType.KEY && !section.startsWith("input.player")
                && !words.toLowerCase(Locale.ROOT).contains("key")) words += " shortcut";
        return prefix + words;
    }

    static String value(SonicConfiguration key, String raw) {
        if (raw.isEmpty() && key == SonicConfiguration.CAPTURE_ENCODER_PRESET) return "Encoder default";
        if (raw.isEmpty()) return ConfigCatalog.meta(key).type() == ConfigType.KEY ? "Unbound" : "None";
        if (ConfigCatalog.meta(key).type() == ConfigType.BOOL) return Boolean.parseBoolean(raw) ? "On" : "Off";
        // Free-text values that are really names: show them as names, never as stored codes.
        if (key == SonicConfiguration.MAIN_CHARACTER_CODE || key == SonicConfiguration.SIDEKICK_CHARACTER_CODE)
            return capitalisedWords(raw);
        if (key == SonicConfiguration.DISPLAY_SHADER_SELECTION && raw.equalsIgnoreCase("off")) return "Off";
        if (key == SonicConfiguration.ROMS_DIRECTORY && raw.equals(".")) return "OpenGGF folder";
        // Codec and file-type codes are initialisms; presets and counts read as words and units.
        if (key == SonicConfiguration.CAPTURE_CODEC || key == SonicConfiguration.CAPTURE_AUDIO_CODEC
                || key == SonicConfiguration.CAPTURE_CONTAINER) return raw.toUpperCase(Locale.ROOT);
        if (key == SonicConfiguration.CAPTURE_ENCODER_PRESET) return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        if (key == SonicConfiguration.CAPTURE_ENCODER_THREADS && raw.trim().equals("0")) return "Automatic";
        if (key == SonicConfiguration.CAPTURE_QUEUE_BUDGET_MB) return raw + " MB";
        if (key == SonicConfiguration.CAPTURE_FPS) return raw + " FPS";
        if (key == SonicConfiguration.CAPTURE_SCALE) return raw + "x";
        if (ConfigCatalog.meta(key).type() != ConfigType.ENUM) return raw;
        if (key == SonicConfiguration.DISPLAY_HUD_ANCHOR) {
            return switch (raw) {
                case "DEFAULT" -> "Native (centred)";
                case "LEFT_EDGE" -> "Screen left edge";
                default -> raw;
            };
        }
        return switch (raw) {
            case "NATIVE_4_3" -> "Native 4:3";
            case "WIDE_16_10" -> "Wide 16:10";
            case "WIDE_16_9" -> "Wide 16:9";
            case "ULTRA_21_9" -> "Ultra-wide 21:9";
            case "SUPER_32_9" -> "Super-wide 32:9";
            case "global" -> "Use engine setting";
            case "s1" -> "Sonic 1";
            case "s2" -> "Sonic 2";
            case "s3k" -> "Sonic 3 & Knuckles";
            case "RAW_RGB" -> "Raw RGB";
            case "MD_ANALOG" -> "Mega Drive analogue";
            case "NTSC_SOFT" -> "Soft NTSC";
            case "CENTER_SCALED" -> "Native centre deadzone";
            case "PROPORTIONAL" -> "Scale with screen width";
            case "NTSC", "PAL" -> raw;
            default -> words(raw);
        };
    }

    /** "tails,knuckles" reads as "Tails, Knuckles"; separators other than commas are kept. */
    private static String capitalisedWords(String raw) {
        StringBuilder out = new StringBuilder();
        boolean start = true;
        for (char c : raw.toCharArray()) {
            if (c == ',') { out.append(", "); start = true; continue; }
            if (Character.isWhitespace(c) && start) continue;
            out.append(start ? Character.toUpperCase(c) : c);
            start = !Character.isLetterOrDigit(c);
        }
        return out.toString();
    }

    private static String words(String value) {
        String text = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return text;
        text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        return text.replace("Fps", "FPS").replace(" fps", " FPS").replace("dac", "DAC")
                .replace("Dac", "DAC").replace("fm ", "FM ").replace("Ffmpeg", "FFmpeg")
                .replace("tls", "TLS").replace("Mb", "MB").replace(" mb", " MB")
                .replace("Hud", "HUD").replace(" hud", " HUD");
    }
}
