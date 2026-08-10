package com.openggf.tools.audio.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.rewind.SmpsTrackSnapshot;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencer.Region;
import com.openggf.audio.smps.SmpsSequencer.TrackType;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS1AudioStateNormalizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final S1AudioStateNormalizer.GhzAssetRange GHZ =
            new S1AudioStateNormalizer.GhzAssetRange(476636, 478532);

    @Test
    void registryMakesEverySourceAndComparisonDecisionExecutable() {
        Map<String, S1AudioFieldRegistry.Field> fields = new HashMap<>();
        S1AudioFieldRegistry.fields().forEach(field -> fields.put(field.name(), field));

        assertEquals(S1AudioFieldRegistry.Comparison.GATE, fields.get("tempoReload").comparison());
        assertEquals(S1AudioFieldRegistry.Signedness.SIGNED_BYTE, fields.get("volume").signedness());
        assertEquals(S1AudioFieldRegistry.Applicability.PSG_ONLY,
                fields.get("envelopeCursor").applicability());
        assertEquals("SmpsTrackSnapshot.voiceId (FM/DAC) or instrumentId (PSG)",
                fields.get("voiceOrEnvelope").source());
        assertEquals(S1AudioFieldRegistry.Comparison.DIAGNOSTIC, fields.get("resting").comparison());
        assertEquals(S1AudioFieldRegistry.Comparison.DIAGNOSTIC, fields.get("noteFillPhase").comparison());
        assertEquals(S1AudioFieldRegistry.Comparison.DIAGNOSTIC,
                fields.get("modulationPhase").comparison());
        assertTrue(fields.values().stream().allMatch(field -> !field.source().isBlank()));
    }

    @Test
    void normalizesFixedRolesSignedFieldsChannelsAndLiveContainers() {
        SmpsTrackSnapshot fm1 = track(52, TrackType.FM, 0, true, 12, 16,
                0xFE, 0xFF, 0xFD, 0x123, 4, 0xC0, 1, 2, 7, 99,
                new int[] {4, 88, 2, 77}, new int[] {38, 1112, 1792, 9999}, 2,
                true, true, true, 0, 0, 0, 0, 0);
        SmpsTrackSnapshot psg3 = track(100, TrackType.PSG, 2, true, 3, 5,
                0, 0, 0, 0x2AB, 0, 0, 0, 0, 44, 6,
                new int[] {9, 8, 7}, new int[] {12}, 1,
                false, false, false, 0, 0, 0, 0, 0);
        // Break caught: stale fields on an inactive track leak into the gating state.
        SmpsTrackSnapshot inactiveFm6 = track(1800, TrackType.FM, 5, false, 255, 255,
                127, 127, 127, 0x7FF, 7, 0xC0, 3, 7, 255, 255,
                new int[] {255}, new int[] {1234}, 1,
                true, true, true, 255, 255, 127, 255, 32767);

        S1AudioStateNormalizer.NormalizedState normalized = S1AudioStateNormalizer.normalize(
                sequencer(List.of(psg3, inactiveFm6, fm1), 21, 3), GHZ, Set.of(0, 2));

        assertEquals(AudioParitySchema.ROLES, normalized.tracks().stream().map(AudioParityTrackState::role).toList());
        assertFalse(normalized.tracks().get(0).active());
        AudioParityTrackState fm = normalized.tracks().get(1);
        assertEquals(-2, fm.transpose());
        assertEquals(-1, fm.volume());
        assertEquals(-3, fm.detune());
        assertEquals(52, fm.sequencePosition());
        assertEquals(0x2123, fm.baseFrequency());
        assertEquals(0xC0, fm.pan());
        assertEquals(1, fm.ams());
        assertEquals(2, fm.fms());
        assertEquals(7, fm.voiceOrEnvelope());
        assertEquals(List.of(4, 2), fm.loopCounters());
        assertEquals(List.of(38L, 1112L), fm.returnStack());
        assertFalse(normalized.tracks().get(6).active());
        AudioParityTrackState psg = normalized.tracks().get(9);
        assertEquals("PSG3", psg.hardware());
        assertEquals(0x2AB, psg.baseFrequency());
        assertEquals(6, psg.voiceOrEnvelope());
        assertEquals(44, psg.envelopeCursor());
        assertEquals(List.of(9, 7), psg.loopCounters());
    }

    @Test
    void validatesUniqueHardwareRolesAndGhzRelativeCoordinates() {
        SmpsTrackSnapshot fm1 = track(0, TrackType.FM, 0, true, 1, 1,
                0, 0, 0, 0, 0, 0xC0, 0, 0, 0, 0, new int[0], new int[0], 0,
                false, false, false, 0, 0, 0, 0, 0);
        SmpsTrackSnapshot duplicate = track(1, TrackType.FM, 0, true, 1, 1,
                0, 0, 0, 0, 0, 0xC0, 0, 0, 0, 0, new int[0], new int[0], 0,
                false, false, false, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> S1AudioStateNormalizer.normalize(
                sequencer(List.of(fm1, duplicate), 21, 3), GHZ, Set.of()));

        SmpsTrackSnapshot pastEnd = track(GHZ.length(), TrackType.FM, 0, true, 1, 1,
                0, 0, 0, 0, 0, 0xC0, 0, 0, 0, 0, new int[0], new int[0], 0,
                false, false, false, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> S1AudioStateNormalizer.normalize(
                sequencer(List.of(pastEnd), 21, 3), GHZ, Set.of()));

        SmpsTrackSnapshot badReturn = track(0, TrackType.FM, 0, true, 1, 1,
                0, 0, 0, 0, 0, 0xC0, 0, 0, 0, 0, new int[0], new int[] {GHZ.length() + 1}, 1,
                false, false, false, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> S1AudioStateNormalizer.normalize(
                sequencer(List.of(badReturn), 21, 3), GHZ, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> S1AudioStateNormalizer.normalize(
                sequencer(List.of(fm1), 21, 3), GHZ, Set.of(256)));

        SmpsTrackSnapshot malformedPan = track(0, TrackType.FM, 0, true, 1, 1,
                0, 0, 0, 0, 0, 0xC1, 0, 0, 0, 0, new int[0], new int[0], 0,
                false, false, false, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> S1AudioStateNormalizer.normalize(
                sequencer(List.of(malformedPan), 21, 3), GHZ, Set.of()));
    }

    @Test
    void promotesOnlyProvenS1TempoPhaseAndExcludesUnprovenDerivedPhases() {
        SmpsTrackSnapshot first = track(12, TrackType.FM, 0, true, 4, 8,
                0, 0, 0, 0x100, 2, 0xC0, 0, 0, 1, 0, new int[0], new int[0], 0,
                false, false, false, 0x80, 3, 5, 7, 11);
        SmpsTrackSnapshot differentDiagnostics = track(12, TrackType.FM, 0, true, 4, 8,
                0, 0, 0, 0x100, 2, 0xC0, 0, 0, 1, 0, new int[0], new int[0], 0,
                false, false, false, 0x40, 99, -5, 33, -200);

        S1AudioStateNormalizer.NormalizedState a = S1AudioStateNormalizer.normalize(
                sequencer(List.of(first), 21, 3), GHZ, Set.of());
        S1AudioStateNormalizer.NormalizedState b = S1AudioStateNormalizer.normalize(
                sequencer(List.of(differentDiagnostics), 22, 4), GHZ, Set.of());

        assertEquals(a.tracks(), b.tracks(), "rest/note-fill/modulation phase are diagnostic only");
        assertEquals(21, a.global().tempoReload());
        assertEquals(3, a.global().tempoTimeout());
        assertEquals(22, b.global().tempoReload());
        assertEquals(4, b.global().tempoTimeout());
    }

    @Test
    void productionS1TimeoutTransitionUsesTheMappedTempoFieldsDirectly() {
        SmpsSequencer sequencer = new SmpsSequencer(new AudioTestFixtures.StubSmpsData("tempo"),
                AudioTestFixtures.EMPTY_DAC, () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        sequencer.setNormalTempo(21);
        sequencer.recalculateTempo();

        S1AudioStateNormalizer.NormalizedState before = S1AudioStateNormalizer.normalize(
                sequencer.captureSnapshot(), GHZ, Set.of());
        sequencer.read(new short[0], 0);
        S1AudioStateNormalizer.NormalizedState after = S1AudioStateNormalizer.normalize(
                sequencer.captureSnapshot(), GHZ, Set.of());

        assertEquals(21, before.global().tempoReload());
        assertEquals(21, before.global().tempoTimeout());
        assertEquals(21, after.global().tempoReload());
        assertEquals(20, after.global().tempoTimeout());
    }

    @Test
    void openGgfGoldenInputProducesTheExistingCanonicalContract() throws IOException {
        JsonNode golden = JSON.readTree(Files.readString(Path.of(
                "src/test/resources/audio/parity/s1/normalization-contract-v1.json")));
        JsonNode input = golden.path("openGgf");
        Set<Integer> loopIndices = Set.copyOf(toInts(golden.path("activeLoopIndices")));
        List<SmpsTrackSnapshot> tracks = new ArrayList<>();
        for (JsonNode node : input.path("tracks")) {
            if (!node.path("active").asBoolean()) {
                continue;
            }
            String role = node.path("role").asText();
            TrackType type = role.equals("DAC") ? TrackType.DAC : role.startsWith("PSG") ? TrackType.PSG : TrackType.FM;
            int channel = role.equals("DAC") ? 5 : role.startsWith("PSG")
                    ? Integer.parseInt(role.substring(3)) - 1 : Integer.parseInt(role.substring(2)) - 1;
            tracks.add(track(node.path("position").asInt(), type, channel, true,
                    node.path("duration").asInt(), node.path("scaledDuration").asInt(),
                    node.path("transpose").asInt(), node.path("volume").asInt(), node.path("detune").asInt(),
                    type == TrackType.FM ? node.path("baseFrequency").asInt() & 0x7FF : node.path("baseFrequency").asInt(),
                    type == TrackType.FM ? node.path("baseFrequency").asInt() >>> 11 : 0,
                    node.path("pan").asInt(), node.path("ams").asInt(), node.path("fms").asInt(),
                    type == TrackType.PSG ? node.path("envPos").asInt() : node.path("voiceId").asInt(),
                    type == TrackType.PSG ? node.path("instrumentId").asInt() : 0,
                    toIntArray(node.path("loopCounters")),
                    toIntArray(node.path("returnStack")), node.path("returnSp").asInt(),
                    node.path("tieNext").asBoolean(), node.path("overridden").asBoolean(),
                    node.path("modEnabled").asBoolean(), 0, 0, 0, 0, 0));
        }
        JsonNode global = input.path("global");
        SmpsSequencerSnapshot snapshot = sequencer(tracks, global.path("tempoReload").asInt(),
                global.path("tempoTimeout").asInt());
        S1AudioStateNormalizer.NormalizedState state = S1AudioStateNormalizer.normalize(snapshot, GHZ, loopIndices);
        AudioParityTick tick = new AudioParityTick(0, state.global(), state.tracks(), List.of(
                new AudioParityChipWrite("ym2612", 0, 34, 17),
                new AudioParityChipWrite("ym2612", 1, 42, 128),
                new AudioParityChipWrite("psg", null, null, 159)));

        assertEquals(golden.path("expectedCanonicalJson").asText(), AudioParityJsonl.canonicalGatingJson(tick));
    }

    private static SmpsSequencerSnapshot sequencer(List<SmpsTrackSnapshot> tracks, int tempoWeight,
            int tempoAccumulator) {
        return new SmpsSequencerSnapshot(Region.NTSC, false, false, tempoWeight, 0, false,
                Integer.MAX_VALUE, 1.0f, 0, false, false, 0, 1, 0,
                new SmpsSequencerSnapshot.FadeSnapshot(0, 0, 0, 0, 0, false, false),
                44100, 735, 0, tempoWeight, tempoAccumulator, 1, true, tracks);
    }

    private static SmpsTrackSnapshot track(int pos, TrackType type, int channelId, boolean active,
            int duration, int scaledDuration, int transpose, int volume, int detune,
            int baseFnum, int baseBlock, int pan, int ams, int fms, int voiceId, int instrumentOrEnv,
            int[] loops, int[] stack, int returnSp, boolean tie, boolean overridden, boolean modEnabled,
            int note, int fill, int modRateCounter, int modCurrentDelta, int modAccumulator) {
        int instrumentId = type == TrackType.PSG ? instrumentOrEnv : 0;
        int envPos = type == TrackType.PSG ? voiceId : 0;
        int actualVoiceId = type == TrackType.PSG ? 0 : voiceId;
        return new SmpsTrackSnapshot(pos, type, channelId, duration, note, active, overridden,
                scaledDuration, scaledDuration, fill, transpose, volume, tie, pan, ams, fms,
                null, null, actualVoiceId, baseFnum, baseBlock, loops, -1, stack, returnSp, 1,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, modRateCounter, 0, (short) modAccumulator,
                modCurrentDelta, modEnabled, modEnabled, detune, 0, null, 0, 0, 0, false,
                false, 0, instrumentId, false, 0, 0, 0, null, envPos, 0, false, false,
                null, 0, 0, false, 0, false, new int[0], false,
                false, false, 0, false, false, 0);
    }

    private static List<Integer> toInts(JsonNode array) {
        List<Integer> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asInt()));
        return values;
    }

    private static int[] toIntArray(JsonNode array) {
        return toInts(array).stream().mapToInt(Integer::intValue).toArray();
    }
}
