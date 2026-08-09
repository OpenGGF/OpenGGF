package com.openggf.tools.audio.parity;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned constants for the S1 music-driver parity interchange. */
public final class AudioParitySchema {
    public static final String VERSION = "openggf.s1_audio_parity_reference.v1";
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

    private AudioParitySchema() {
    }
}
