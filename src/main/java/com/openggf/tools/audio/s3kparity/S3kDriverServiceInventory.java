package com.openggf.tools.audio.s3kparity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static com.openggf.tools.audio.s3kparity.S3kSmpsReachabilityInventory.SourceBehavior.NORMAL;
import static com.openggf.tools.audio.s3kparity.S3kSmpsReachabilityInventory.Status.MISSING;
import static com.openggf.tools.audio.s3kparity.S3kSmpsReachabilityInventory.Status.PARTIAL;
import static com.openggf.tools.audio.s3kparity.S3kSmpsReachabilityInventory.TimingStatus.UNAVAILABLE;

/** Canonical first-slice rows for S3K behavior owned outside stream bytecode. */
public final class S3kDriverServiceInventory {

    private static final String SCHEMA = "openggf.s3k-smps-first-slice-inventory.v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final Map<String, String> LOCKED_ON_SOURCE_CONDITIONS = Map.of(
            "SonicDriverVer", "4",
            "fix_sndbugs", "0",
            "FixMusicAndSFXDataBugs", "0",
            "FixBugs", "0");

    private static final Set<String> FIRST_SLICE_ROOTS = Set.of(
            "sfx.59.collapse", "sfx.b6.spindash-release", "music.2c.invincibility");

    private S3kDriverServiceInventory() {
    }

    public record FirstSliceInventory(
            String romSha1,
            S3kSmpsReachabilityInventory.Dialect dialect,
            Map<String, String> sourceConditions,
            S3kSmpsReachabilityInventory.InventoryLimits limits,
            Map<String, S3kSmpsReachabilityInventory.InventoryResult> streams,
            List<S3kSmpsReachabilityInventory.Behavior> serviceRows) {

        public FirstSliceInventory {
            if (romSha1 == null || !romSha1.matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("ROM SHA-1 must be lowercase hexadecimal");
            }
            Objects.requireNonNull(dialect, "dialect");
            sourceConditions = Map.copyOf(sourceConditions);
            Objects.requireNonNull(limits, "limits");
            streams = Map.copyOf(streams);
            serviceRows = List.copyOf(serviceRows);
            if (dialect != S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4) {
                throw new IllegalArgumentException("first-slice artifact requires locked-on V4");
            }
            if (!sourceConditions.equals(LOCKED_ON_SOURCE_CONDITIONS)) {
                throw new IllegalArgumentException("locked-on source conditions differ");
            }
            if (!streams.keySet().equals(FIRST_SLICE_ROOTS)) {
                throw new IllegalArgumentException("first-slice stream roots differ");
            }
            if (streams.values().stream().anyMatch(result -> !result.frontiers().isEmpty())) {
                throw new IllegalArgumentException("first-slice stream has an open frontier");
            }
            Set<String> serviceKeys = firstSliceRows().stream()
                    .map(S3kSmpsReachabilityInventory.Behavior::key)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            validateCompleteFirstSlice(serviceKeys, serviceRows);
        }
    }

    public static List<S3kSmpsReachabilityInventory.Behavior> firstSliceRows() {
        return List.of(
                row("s3k.service.order", PARTIAL, "SmpsDriver",
                        "Z80 Sound Driver.asm:zDriverUpdate"),
                row("s3k.tempo.carry-service", PARTIAL, "SmpsSequencer",
                        "Z80 Sound Driver.asm:TempoWait/zUpdateMusic"),
                row("s3k.speed.extra-service", PARTIAL, "SmpsDriver",
                        "Z80 Sound Driver.asm:zDoSpeedUp"),
                row("s3k.sfx.admission-restore", PARTIAL, "SmpsDriver",
                        "Z80 Sound Driver.asm:zPlaySFX/zStopTrack"),
                row("s3k.sfx.continuous", PARTIAL, "SmpsDriver",
                        "Z80 Sound Driver.asm:zUpdateContinuousSFX"),
                row("s3k.note-fill", PARTIAL, "SmpsSequencer",
                        "Z80 Sound Driver.asm:zTrackNoteFillUpdate"),
                row("s3k.collapse.modulation-psg-noise", MISSING, "SmpsSequencer",
                        "Sound/SFX/59 - Collapse.asm"),
                row("s3k.pause-fade-jingle-stop-all", PARTIAL, "AudioVoiceRegistry",
                        "Z80 Sound Driver.asm:zPauseMusic/zFadeOutMusic/zStopAllSound"),
                row("s3k.dac-fm6", PARTIAL, "Ym2612Chip",
                        "Z80 Sound Driver.asm:zDACUpdate"),
                row("s3k.sega-pcm", PARTIAL, "AudioVoiceRegistry",
                        "Z80 Sound Driver.asm:zPlaySegaSound"),
                row("s3k.pal.full-driver-repeat", PARTIAL, "SmpsDriver",
                        "Z80 Sound Driver.asm:zPALUpdate"),
                row("s3k.ring-speaker.alternation", PARTIAL, "GameAudioProfile",
                        "Z80 Sound Driver.asm:zRingSpeaker"));
    }

    public static void validateCompleteFirstSlice(
            Set<String> requiredKeys,
            List<S3kSmpsReachabilityInventory.Behavior> rows) {
        Set<String> actual = new HashSet<>();
        for (S3kSmpsReachabilityInventory.Behavior row : List.copyOf(rows)) {
            if (!actual.add(row.key())) {
                throw new IllegalArgumentException("duplicate service inventory key: " + row.key());
            }
        }
        if (!actual.equals(Set.copyOf(requiredKeys))) {
            Set<String> missing = new HashSet<>(requiredKeys);
            missing.removeAll(actual);
            Set<String> extra = new HashSet<>(actual);
            extra.removeAll(requiredKeys);
            throw new IllegalArgumentException("service inventory differs: missing="
                    + missing + " extra=" + extra);
        }
    }

    /** Writes one stable, newline-terminated research artifact. */
    public static String writeCanonicalJson(FirstSliceInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("rom_sha1", inventory.romSha1());
        body.put("dialect", inventory.dialect().name());

        ObjectNode conditions = body.putObject("source_conditions");
        new TreeMap<>(inventory.sourceConditions()).forEach(conditions::put);

        ObjectNode limits = body.putObject("limits");
        limits.put("max_states", inventory.limits().maxStates());
        limits.put("max_edges", inventory.limits().maxEdges());
        limits.put("max_call_depth", inventory.limits().maxCallDepth());
        limits.put("max_overlay_bytes", inventory.limits().maxOverlayBytes());

        ArrayNode streams = body.putArray("streams");
        int frontierCount = 0;
        Map<String, S3kSmpsReachabilityInventory.InventoryResult> sortedStreams =
                new TreeMap<>(inventory.streams());
        for (Map.Entry<String, S3kSmpsReachabilityInventory.InventoryResult> entry
                : sortedStreams.entrySet()) {
            S3kSmpsReachabilityInventory.InventoryResult result = entry.getValue();
            ObjectNode stream = streams.addObject();
            stream.put("key", entry.getKey());
            stream.put("state_count", result.states().size());
            stream.put("edge_count", result.edges().size());
            stream.put("frontier_count", result.frontiers().size());
            frontierCount = Math.addExact(frontierCount, result.frontiers().size());
            ArrayNode behaviors = stream.putArray("behaviors");
            result.behaviors().stream()
                    .sorted(BEHAVIOR_ORDER)
                    .forEach(row -> writeBehavior(behaviors.addObject(), row));
        }
        body.put("frontier_count", frontierCount);

        ArrayNode services = body.putArray("driver_services");
        inventory.serviceRows().stream()
                .sorted(BEHAVIOR_ORDER)
                .forEach(row -> writeBehavior(services.addObject(), row));

        try {
            String canonicalBody = JSON.writeValueAsString(body);
            ObjectNode document = JsonNodeFactory.instance.objectNode();
            document.put("schema", SCHEMA);
            document.put("body_sha256", sha256(canonicalBody));
            document.set("body", body);
            return JSON.writeValueAsString(document) + "\n";
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("cannot serialize first-slice inventory", failure);
        }
    }

    private static void writeBehavior(
            ObjectNode json,
            S3kSmpsReachabilityInventory.Behavior row) {
        json.put("key", row.key());
        json.put("status", row.status().name());
        json.put("source_behavior", row.sourceBehavior().name());
        json.put("timing_status", row.timingStatus().name());
        writeSortedStrings(json.putArray("roots"), row.roots());
        writeSortedStrings(json.putArray("track_types"), row.trackTypes());
        json.put("runtime_owner", row.runtimeOwner());
        json.put("source_citation", row.sourceCitation());
        writeSortedStrings(json.putArray("evidence_ids"), row.evidenceIds());
    }

    private static void writeSortedStrings(ArrayNode json, Set<String> values) {
        values.stream().sorted().forEach(json::add);
    }

    private static final Comparator<S3kSmpsReachabilityInventory.Behavior> BEHAVIOR_ORDER =
            Comparator.comparing(S3kSmpsReachabilityInventory.Behavior::key)
                    .thenComparing(row -> String.join("\u0000", row.trackTypes().stream()
                            .sorted().toList()))
                    .thenComparing(S3kSmpsReachabilityInventory.Behavior::runtimeOwner)
                    .thenComparing(S3kSmpsReachabilityInventory.Behavior::sourceCitation);

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static S3kSmpsReachabilityInventory.Behavior row(
            String key,
            S3kSmpsReachabilityInventory.Status status,
            String runtimeOwner,
            String sourceCitation) {
        return new S3kSmpsReachabilityInventory.Behavior(
                key,
                status,
                NORMAL,
                UNAVAILABLE,
                FIRST_SLICE_ROOTS,
                Set.of("DRIVER_GLOBAL"),
                runtimeOwner,
                sourceCitation,
                Set.of());
    }
}
