package com.openggf.tools.audio.parity;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned constants for the S1 music-driver parity interchange. */
public final class AudioParitySchema {
    public static final String VERSION = "openggf.s1_audio_parity_reference.v1";
    public static final String REFERENCE_CAPTURE = "s1_ghz_music_driver_reference";
    public static final String OPENGGF_CAPTURE = "s1_ghz_music_driver_openggf";
    public static final String S1_REV01_SHA1 = "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b";
    public static final String S1_REV01_CRC32 = "afe05eee";
    public static final String BK2_SHA256 = "622ff642d0b0835a4f77bee568f2413f288ead3306a8bc2a93e8d8f77f24ca9c";
    public static final String BK2_CORE = "Genplus-gx";
    public static final String BK2_EMULATOR = "Version 2.11";
    public static final String BK2_GAME = "Sonic The Hedgehog (W) (REV01) [!]";
    public static final int BK2_INPUT_ROWS = 989;
    public static final String BK2_OPAQUE_HASH = "09DADB5071EB35050067A32462E39C5F";
    public static final int MAX_INVOCATIONS = 36_000;
    public static final String METADATA_TYPE = "capture_metadata";
    public static final String TICK_TYPE = "tick";
    public static final List<String> ROLES = List.of(
            "DAC", "FM1", "FM2", "FM3", "FM4", "FM5", "FM6", "PSG1", "PSG2", "PSG3");
    public static final Map<String, String> HARDWARE_BY_ROLE = Map.ofEntries(
            Map.entry("DAC", "DAC"), Map.entry("FM1", "FM1"), Map.entry("FM2", "FM2"),
            Map.entry("FM3", "FM3"), Map.entry("FM4", "FM4"), Map.entry("FM5", "FM5"),
            Map.entry("FM6", "FM6"), Map.entry("PSG1", "PSG1"), Map.entry("PSG2", "PSG2"),
            Map.entry("PSG3", "PSG3"));
    public static final Set<String> METADATA_FIELDS = Set.of(
            "type", "schema", "capture", "cycle_start", "period", "terminal_record_count", "rom",
            "callback_contract", "diagnostic_fields", "gating_fields", "launch_update_music_invocations",
            "movie");
    public static final List<String> DIAGNOSTIC_GLOBAL_FIELDS = List.of(
            "priority", "pause", "fade flags", "queues", "sound id", "voice selector", "DAC update",
            "1-up", "speed-up reload", "communication", "ring speaker", "push");
    public static final List<String> DIAGNOSTIC_TRACK_FIELDS = List.of(
            "resting", "note fill", "modulation phase", "raw status", "raw voice control");
    public static final List<String> GATING_GLOBAL_FIELDS = List.of(
            "tempo timeout", "tempo reload", "speed-up", "fade state");
    public static final List<String> GATING_TRACK_FIELDS = List.of(
            "active", "role", "hardware", "overridden", "do not attack", "modulation enabled",
            "sequence position", "transpose", "volume", "pan/AMS/FMS", "voice/envelope", "duration",
            "duration reload", "PSG envelope cursor", "base frequency", "detune", "live loop counters",
            "live return stack");

    private AudioParitySchema() {
    }
}
