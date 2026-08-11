package com.openggf.tools.audio.completerun.s2;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.openggf.tools.audio.completerun.s2.S2CompleteRunStateNormalizer.SourceLayer.MUSIC;
import static com.openggf.tools.audio.completerun.s2.S2CompleteRunStateNormalizer.SourceLayer.SFX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2CompleteRunStateNormalizer {
    private static final S2CompleteRunStateNormalizer.Asset EHZ =
            new S2CompleteRunStateNormalizer.Asset("music.rom.0f88c4", 0x1380, 0x1b80);
    private static final S2CompleteRunStateNormalizer.Asset JUMP =
            new S2CompleteRunStateNormalizer.Asset("sfx.native.a0", 0x8000, 0x8100);
    private static final Map<String, S2CompleteRunStateNormalizer.Asset> ASSETS =
            Map.of(EHZ.key(), EHZ, JUMP.key(), JUMP);

    @Test
    void normalizesAllGlobalQueuesTransformsAndEffectiveHardwareOwners() {
        var normalized = S2CompleteRunStateNormalizer.normalizeReference(liveState(false), ASSETS);

        assertEquals(S2CompleteRunStateNormalizer.GLOBAL_FIELDS,
                normalized.fields().stream().map(field -> field.name()).toList());
        assertEquals(10, normalized.roles().size());
        assertEquals(S2CompleteRunStateNormalizer.ACTIVE_ROLE_FIELDS,
                normalized.roles().get(HardwareRole.FM3.ordinal()).fields().stream()
                        .map(field -> field.name()).toList());
        assertEquals(0x34, roleValue(normalized, HardwareRole.DAC, "savedDac"));
        assertEquals(true, roleValue(normalized, HardwareRole.FM3, "doNotAttack"));
        assertTrue(normalized.roles().get(HardwareRole.FM3.ordinal()).active());
        assertEquals("sfx.native.a0", normalized.roles().get(HardwareRole.FM3.ordinal()).fields().getFirst().value());
        assertEquals(0x70, value(normalized, "priority"));
        assertEquals(0xa0, value(normalized, "queue0"));
        assertEquals(Map.of("active", true, "assetKey", EHZ.key(), "cursor", 0x10),
                value(normalized, "voiceTablePointer"));
        assertEquals(1, value(normalized, "ringSpeaker"));
        assertEquals(true, value(normalized, "gloopSuppressed"));
        assertEquals(3, value(normalized, "spindashPlayingCounter"));
        List<?> sourceSlots = (List<?>) value(normalized, "sourceSlots");
        assertEquals(16, sourceSlots.size());
        assertEquals("MUSIC", ((Map<?, ?>) sourceSlots.getFirst()).get("layer"));
        assertEquals("SFX", ((Map<?, ?>) sourceSlots.get(10)).get("layer"));
        assertEquals(false, ((Map<?, ?>) value(normalized, "savedMusic")).get("active"));
    }

    @Test
    void referenceAndEngineCoordinatesNormalizeToIdenticalState() {
        assertEquals(S2CompleteRunStateNormalizer.normalizeReference(liveState(false), ASSETS),
                S2CompleteRunStateNormalizer.normalizeEngine(liveState(false), ASSETS));
    }

    @Test
    void inactiveTracksCannotLeakStaleCapacityIntoCanonicalRoles() {
        var state = liveState(false);
        var normalized = S2CompleteRunStateNormalizer.normalizeReference(state, ASSETS);

        assertFalse(normalized.roles().get(HardwareRole.FM1.ordinal()).active());
        assertEquals(List.of(), normalized.roles().get(HardwareRole.FM1.ordinal()).fields());
    }

    @Test
    void oneUpRequiresAndSerializesTheExactTenTrackSavedPayload() {
        assertThrows(NullPointerException.class,
                () -> S2CompleteRunStateNormalizer.normalizeReference(liveState(true), ASSETS));

        var base = liveState(false);
        var oneUpGlobals = globals(true);
        var saved = new S2CompleteRunStateNormalizer.SavedMusic(
                savedGlobals(), base.sourceSlots().subList(0, 10));
        var state = new S2CompleteRunStateNormalizer.LiveState(oneUpGlobals, base.sourceSlots(), saved);
        var normalized = S2CompleteRunStateNormalizer.normalizeReference(state, ASSETS);
        Map<?, ?> normalizedSaved = (Map<?, ?>) value(normalized, "savedMusic");
        assertEquals(true, normalizedSaved.get("active"));
        assertEquals(0x70, normalizedSaved.get("priority"));
        assertFalse(normalizedSaved.containsKey("currentSong"));
        assertFalse(normalizedSaved.containsKey("ringSpeaker"));
        assertEquals(10, ((List<?>) normalizedSaved.get("sourceSlots")).size());
    }

    @Test
    void rejectsOutOfRangePointersWrongSlotOrderAndIncompleteServices() {
        var state = liveState(false);
        var badTrack = activeTrack("music.rom.0f88c4", 0x1b80, 6);
        var slots = new ArrayList<>(state.sourceSlots());
        slots.set(0, new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.DAC, badTrack));
        assertThrows(IllegalArgumentException.class, () -> S2CompleteRunStateNormalizer.normalizeReference(
                new S2CompleteRunStateNormalizer.LiveState(state.globals(), slots, null), ASSETS));
        assertThrows(IllegalArgumentException.class, () -> new S2CompleteRunStateNormalizer.LiveState(
                state.globals(), state.sourceSlots().reversed(), null));
        assertThrows(IllegalArgumentException.class, () -> S2CompleteRunStateNormalizer.normalizeReference(
                new S2CompleteRunStateNormalizer.LiveState(globals(false, 0xff), state.sourceSlots(), null), ASSETS));
    }

    private static Object value(com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NormalizedState state,
            String name) {
        return state.fields().stream().filter(field -> field.name().equals(name)).findFirst().orElseThrow().value();
    }

    private static Object roleValue(
            com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NormalizedState state,
            HardwareRole role, String name) {
        return state.roles().get(role.ordinal()).fields().stream()
                .filter(field -> field.name().equals(name)).findFirst().orElseThrow().value();
    }

    private static S2CompleteRunStateNormalizer.LiveState liveState(boolean oneUpWithoutSavedState) {
        List<S2CompleteRunStateNormalizer.SourceSlot> slots = new ArrayList<>();
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.DAC,
                activeTrack(EHZ.key(), 0x1390, 6)));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.FM1, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.FM2, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.FM3,
                activeTrack(EHZ.key(), 0x1390, 2)));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.FM4, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.FM5, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.FM6, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.PSG1, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.PSG2, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(MUSIC, HardwareRole.PSG3, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(SFX, HardwareRole.FM3,
                activeTrack(JUMP.key(), 0x8010, 2)));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(SFX, HardwareRole.FM4, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(SFX, HardwareRole.FM5, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(SFX, HardwareRole.PSG1, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(SFX, HardwareRole.PSG2, inactiveTrack()));
        slots.add(new S2CompleteRunStateNormalizer.SourceSlot(SFX, HardwareRole.PSG3, inactiveTrack()));
        return new S2CompleteRunStateNormalizer.LiveState(globals(oneUpWithoutSavedState), slots, null);
    }

    private static S2CompleteRunStateNormalizer.DriverGlobals globals(boolean oneUp) {
        return globals(oneUp, 0);
    }

    private static S2CompleteRunStateNormalizer.DriverGlobals globals(boolean oneUp, int dacUpdating) {
        return new S2CompleteRunStateNormalizer.DriverGlobals(
                0x70, 4, 5, 0, 0, 0, 0, dacUpdating, 0x80, List.of(0xa0, 0, 0),
                new S2CompleteRunStateNormalizer.AssetPointer(EHZ.key(), 0x1390),
                0, 0, 0, oneUp, 6, 7, false, true, 0, false, false,
                0, 0, 0x82, false, 0xff, true, 3, 2, true);
    }

    private static S2CompleteRunStateNormalizer.SavedGlobals savedGlobals() {
        return new S2CompleteRunStateNormalizer.SavedGlobals(
                0x70, 4, 5, 0, 0, 0, 0, 0, 0x80, List.of(0xa0, 0, 0),
                new S2CompleteRunStateNormalizer.AssetPointer(EHZ.key(), 0x1390),
                0, 0, 0, false, 6, 7, false, true, 0, false);
    }

    private static S2CompleteRunStateNormalizer.Track inactiveTrack() {
        return new S2CompleteRunStateNormalizer.Track(false, null, 0, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xffff,
                0xff, 0xff, 0xffff, 0xff, 0xff, 0xff, 0xff, 0xffff, 0xff,
                0xff, 0xff, 0xffff, 0xffff, List.of());
    }

    private static S2CompleteRunStateNormalizer.Track activeTrack(String asset, int pointer, int voiceControl) {
        return new S2CompleteRunStateNormalizer.Track(true, asset, pointer, 0x90, voiceControl, 1,
                0, 1, 0xc0, 2, 3, 0x2a, 4, 5, 0x1234,
                6, 7, pointer, 8, 9, 1, 10, 0x0010, 0,
                0, 0, pointer, pointer,
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
    }
}
