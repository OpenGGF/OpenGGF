package com.openggf.game;

import com.openggf.i18n.UiMessages;
import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.ConfigType;
import com.openggf.configuration.SonicConfiguration;
import java.util.Locale;

/** Localized presentation only; persisted keys and values remain owned by ConfigCatalog. */
final class EngineSettingLabels {
    private EngineSettingLabels() { }

    /** Dynamic IDs are exhaustive against ConfigCatalog in the catalog contract checks. */
    static String label(SonicConfiguration key) {
        return UiMessages.text("settings.key." + key.name().toLowerCase(Locale.ROOT) + ".label");
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
            "SHADERS", "RENDERING AND LOADING", "DIRECT LAUNCH", "REWIND", "MODS", "DISCORD",
            "PLAYBACK AND RECORDING", "TRACE REPLAY", "DEBUG", "SETTINGS FILE");

    /**
     * The caption a Display or Advanced setting sits under, from its catalog section (and, inside
     * the display section, what the setting controls); null for tabs without captions.
     */
    static String group(SonicConfiguration key) {
        var meta = ConfigCatalog.meta(key);
        if (!meta.persisted()) return null;
        var category = com.openggf.configuration.EngineSettingsDraft.category(key);
        if (category == com.openggf.configuration.EngineSettingsDraft.Category.DISPLAY) {
            return switch (key) {
                case DISPLAY_ASPECT, DISPLAY_WINDOW_MODE, DISPLAY_SCALING, DISPLAY_WINDOW_AUTOSIZE -> "WINDOW";
                case FPS, DISPLAY_INTERPOLATION, DISPLAY_SHADER_SELECTION, DISPLAY_COLOR_PROFILE, DISPLAY_COLOR_EXPANSION,
                        DISPLAY_SPRITE_SCANLINE_LIMIT -> "PICTURE";
                case WIDESCREEN_DEADZONE_MODE, DISPLAY_HUD_ANCHOR -> "WIDESCREEN";
                default -> "HUD";
            };
        }
        if (category != com.openggf.configuration.EngineSettingsDraft.Category.ADVANCED) return null;
        String section = meta.section();
        if (section.equals("display")) return key == SonicConfiguration.DISPLAY_PACING
                ? "RENDERING AND LOADING" : "SHADERS";
        if (section.equals("gameplay")) return "RENDERING AND LOADING";
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

    static String groupCaption(String group) {
        if ("RENDERING AND LOADING".equals(group)) return UiMessages.text("settings.group.speed_and_loading");
        return UiMessages.text("settings.group." + group.toLowerCase(Locale.ROOT).replace(' ', '_'));
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
            return key == SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB ? UiMessages.text("settings.dependent.used_only_when_the_sound_limit_is_size")
                    : UiMessages.text("settings.dependent.used_only_when_the_sound_limit_is_time");
        return UiMessages.text("settings.dependent.enabled", java.util.Map.of("setting", label(parent)));
    }

    /** Short player-facing help; Details retains the catalog's technical explanation. */
    /** Concise row help; exact encoder templates remain in the full description used by Details. */
    static String rowDescription(SonicConfiguration key) {
        return switch (key) {
            case CAPTURE_FFMPEG_PASS1_ARGS -> UiMessages.text("settings.screen.encoder_arguments_summary");
            case CAPTURE_FFMPEG_PASS2_ARGS -> UiMessages.text("settings.screen.audio_mux_arguments_summary");
            default -> description(key);
        };
    }

    static String description(SonicConfiguration key) {
        return UiMessages.text("settings.key." + key.name().toLowerCase(Locale.ROOT) + ".help");
    }

    static String value(SonicConfiguration key, String raw) {
        if (key == SonicConfiguration.FPS && raw.equalsIgnoreCase("AUTO")) return UiMessages.text("settings.value.auto_display_refresh");
        if (key == SonicConfiguration.UI_LANGUAGE) return LanguagePickerScreen.displayName(raw);
        if (raw.isEmpty() && key == SonicConfiguration.CAPTURE_ENCODER_PRESET) return UiMessages.text("settings.value.encoder_default");
        if (raw.isEmpty()) return ConfigCatalog.meta(key).type() == ConfigType.KEY ? UiMessages.text("settings.value.unbound") : UiMessages.text("settings.value.NONE");
        if (ConfigCatalog.meta(key).type() == ConfigType.BOOL) return Boolean.parseBoolean(raw) ? UiMessages.text("settings.value.on") : UiMessages.text("settings.value.off");
        // Free-text values that are really names: show them as names, never as stored codes.
        if (key == SonicConfiguration.MAIN_CHARACTER_CODE || key == SonicConfiguration.SIDEKICK_CHARACTER_CODE)
            return capitalisedWords(raw);
        if (key == SonicConfiguration.DATA_SELECT_EXTRA_PLAYER_COMBOS) return ExtraTeamsEditor.summary(raw);
        if (key == SonicConfiguration.DISPLAY_SHADER_SELECTION && raw.equalsIgnoreCase("off")) return UiMessages.text("settings.value.off");
        if (key == SonicConfiguration.ROMS_DIRECTORY && raw.equals(".")) return UiMessages.text("settings.value.openggf_folder");
        // Codec and file-type codes are initialisms; presets and counts read as words and units.
        if (key == SonicConfiguration.CAPTURE_CODEC || key == SonicConfiguration.CAPTURE_AUDIO_CODEC
                || key == SonicConfiguration.CAPTURE_CONTAINER) return raw.toUpperCase(Locale.ROOT);
        if (key == SonicConfiguration.CAPTURE_ENCODER_PRESET) return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        if (key == SonicConfiguration.CAPTURE_ENCODER_THREADS && raw.trim().equals("0")) return UiMessages.text("settings.value.automatic");
        if (key == SonicConfiguration.CAPTURE_QUEUE_BUDGET_MB) return raw + " MB";
        if (key == SonicConfiguration.CAPTURE_FPS) return raw + " FPS";
        if (key == SonicConfiguration.CAPTURE_SCALE) return raw + "x";
        if (ConfigCatalog.meta(key).type() != ConfigType.ENUM) return raw;
        if (key == SonicConfiguration.DISPLAY_HUD_ANCHOR) {
            return switch (raw) {
                case "DEFAULT" -> UiMessages.text("settings.value.native_centred");
                case "LEFT_EDGE" -> UiMessages.text("settings.value.hud_left_edge");
                default -> raw;
            };
        }
        return switch (raw) {
            case "NATIVE_4_3" -> UiMessages.text("settings.value.NATIVE_4_3");
            case "WIDE_16_10" -> UiMessages.text("settings.value.WIDE_16_10");
            case "WIDE_16_9" -> UiMessages.text("settings.value.WIDE_16_9");
            case "ULTRA_21_9" -> UiMessages.text("settings.value.ULTRA_21_9");
            case "SUPER_32_9" -> UiMessages.text("settings.value.SUPER_32_9");
            case "global" -> UiMessages.text("settings.value.global");
            case "s1" -> UiMessages.text("settings.value.s1");
            case "s2" -> UiMessages.text("settings.value.s2");
            case "s3k" -> UiMessages.text("settings.value.s3k");
            case "RAW_RGB" -> UiMessages.text("settings.value.RAW_RGB");
            case "MD_ANALOG" -> UiMessages.text("settings.value.MD_ANALOG");
            case "NTSC_SOFT" -> UiMessages.text("settings.value.NTSC_SOFT");
            case "CENTER_SCALED" -> UiMessages.text("settings.value.CENTER_SCALED");
            case "PROPORTIONAL" -> UiMessages.text("settings.value.PROPORTIONAL");
            case "NTSC", "PAL" -> raw;
            default -> ConfigCatalog.meta(key).allowedValues().contains(raw)
                    ? UiMessages.text("settings.value." + raw) : raw;
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

}
