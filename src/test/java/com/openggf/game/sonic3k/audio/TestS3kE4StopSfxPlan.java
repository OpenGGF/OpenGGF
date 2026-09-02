package com.openggf.game.sonic3k.audio;

import com.openggf.audio.driver.SmpsChannelOwnershipProjection;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.session.SmpsChipWrite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kE4StopSfxPlan {
    @Test
    void incompleteProjectionRejectsBeforeItCanProduceAnyChipWrite() {
        S3kE4Projection projection = projection(false, null, null);

        S3kE4StopSfxPlan plan = S3kE4StopSfxPlan.prepare(projection);

        assertFalse(plan.accepted());
        assertTrue(plan.writes().isEmpty());
    }

    @Test
    void fm3UsesTheShippedSilenceThenRestoreOrder() {
        S3kE4Projection.S3kE4Track sfx = track(
                S3kE4Projection.S3kE4Slot.FM3, true, false, false,
                false, 0x00, null, 0, 0xC0, false, null);
        byte[] voice = new byte[25];
        for (int index = 0; index < voice.length; index++) {
            voice[index] = (byte) (index + 1);
        }
        voice[21] = (byte) 0x80;
        voice[22] = 0x22;
        voice[23] = (byte) 0xFE;
        voice[24] = 0x44;
        S3kE4Projection.S3kE4Track music = track(
                S3kE4Projection.S3kE4Slot.FM3, true, false, true,
                true, 0x00, voice, 7, 0x80, true,
                new int[] {0, 0x0E, 0, 0x0F});

        S3kE4StopSfxPlan plan = S3kE4StopSfxPlan.prepare(
                projection(true, sfx, music));

        assertTrue(plan.accepted());
        assertEquals(expectedFm3Writes(), plan.writes());
    }

    @ParameterizedTest
    @MethodSource("nativeSlotSilencePrograms")
    void everyNativeSlotUsesItsExactFixBugsOffSilenceProgram(
            S3kE4Projection.S3kE4Slot slot,
            List<SmpsChipWrite> expected) {
        S3kE4Projection.S3kE4Track sfx = track(slot, true, false, false,
                false, 0, null, 0, 0xC0, false, null);

        S3kE4StopSfxPlan plan = S3kE4StopSfxPlan.prepare(
                projection(true, slot, sfx, null));

        assertTrue(plan.accepted());
        assertEquals(expected, plan.writes());
    }

    @Test
    void noAttackAndOverrideKeepTheUnconditionalRawKeyWriteButSuppressTheRest() {
        S3kE4Projection.S3kE4Track tied = track(
                S3kE4Projection.S3kE4Slot.FM3, true, true, false,
                false, 0, null, 0, 0xC0, false, null);
        S3kE4Projection.S3kE4Track overridden = track(
                S3kE4Projection.S3kE4Slot.FM3, true, false, true,
                false, 0, null, 0, 0xC0, false, null);

        assertEquals(List.of(new SmpsChipWrite.Ym2612(0, 0x82, 0xFF),
                        new SmpsChipWrite.Ym2612(0, 0x86, 0xFF),
                        new SmpsChipWrite.Ym2612(0, 0x8A, 0xFF),
                        new SmpsChipWrite.Ym2612(0, 0x8E, 0xFF),
                        new SmpsChipWrite.Ym2612(0, 0x42, 0x7F),
                        new SmpsChipWrite.Ym2612(0, 0x46, 0x7F),
                        new SmpsChipWrite.Ym2612(0, 0x4A, 0x7F),
                        new SmpsChipWrite.Ym2612(0, 0x4E, 0x7F),
                        new SmpsChipWrite.Ym2612(0, 0x28, 0x02)),
                S3kE4StopSfxPlan.prepare(projection(true, tied, null)).writes());
        assertEquals(List.of(new SmpsChipWrite.Ym2612(0, 0x28, 0x02)),
                S3kE4StopSfxPlan.prepare(projection(true, overridden, null))
                        .writes());
    }

    @Test
    void psgNoiseRelatchesOnlyTheSignedRawMusicOperandAfterTheYmHazard() {
        S3kE4Projection.S3kE4Track sfx = track(
                S3kE4Projection.S3kE4Slot.PSG3, true, false, false,
                true, 7, null, 0, 0xC0, false, null);
        S3kE4Projection.S3kE4Track music = trackWithRawPsgNoise(
                S3kE4Projection.S3kE4Slot.PSG3, true, true, 0xE7);

        assertEquals(List.of(new SmpsChipWrite.Ym2612(0, 0x28, 0xC0),
                        new SmpsChipWrite.Psg(0xE7)),
                S3kE4StopSfxPlan.prepare(projection(true,
                        S3kE4Projection.S3kE4Slot.PSG3, sfx, music)).writes());
    }

    private static Stream<Arguments> nativeSlotSilencePrograms() {
        return Stream.of(S3kE4Projection.S3kE4Slot.values()).map(slot ->
                Arguments.of(slot, expectedSilenceWrites(slot)));
    }

    private static List<SmpsChipWrite> expectedSilenceWrites(
            S3kE4Projection.S3kE4Slot slot) {
        List<SmpsChipWrite> writes = new ArrayList<>();
        if (slot.trackType() == com.openggf.audio.smps.SmpsSequencer.TrackType.FM) {
            int port = slot.channel() < 3 ? 0 : 1;
            int channel = slot.channel() % 3;
            for (int offset = 0; offset <= 12; offset += 4) {
                writes.add(new SmpsChipWrite.Ym2612(port,
                        0x80 + offset + channel, 0xFF));
            }
            for (int offset = 0; offset <= 12; offset += 4) {
                writes.add(new SmpsChipWrite.Ym2612(port,
                        0x40 + offset + channel, 0x7F));
            }
        }
        writes.add(new SmpsChipWrite.Ym2612(0, 0x28,
                slot.rawVoiceControl()));
        if (slot.trackType() == com.openggf.audio.smps.SmpsSequencer.TrackType.FM) {
            writes.add(new SmpsChipWrite.Ym2612(0, 0x28,
                    slot.rawVoiceControl()));
        }
        return List.copyOf(writes);
    }

    private static List<SmpsChipWrite> expectedFm3Writes() {
        List<SmpsChipWrite> writes = new ArrayList<>();
        for (int register = 0x82; register <= 0x8E; register += 4) {
            writes.add(new SmpsChipWrite.Ym2612(0, register, 0xFF));
        }
        for (int register = 0x42; register <= 0x4E; register += 4) {
            writes.add(new SmpsChipWrite.Ym2612(0, register, 0x7F));
        }
        writes.add(new SmpsChipWrite.Ym2612(0, 0x28, 0x02));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x28, 0x02));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x27, 0x4F));
        writes.add(new SmpsChipWrite.Ym2612(0, 0xB6, 0x80));
        writes.add(new SmpsChipWrite.Ym2612(0, 0xB2, 0x01));
        int[] offsets = {0, 8, 4, 12};
        int[] groups = {0x30, 0x50, 0x60, 0x70, 0x80};
        for (int group = 0; group < groups.length; group++) {
            for (int operator = 0; operator < offsets.length; operator++) {
                writes.add(new SmpsChipWrite.Ym2612(0,
                        groups[group] + offsets[operator] + 2,
                        2 + group * 4 + operator));
            }
        }
        writes.add(new SmpsChipWrite.Ym2612(0, 0x42, 0x07));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x4A, 0x22));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x46, 0x05));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x4E, 0x44));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x92, 0x00));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x9A, 0x0E));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x96, 0x00));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x9E, 0x0F));
        return List.copyOf(writes);
    }

    private static S3kE4Projection projection(boolean complete,
            S3kE4Projection.S3kE4Track fm3Sfx,
            S3kE4Projection.S3kE4Track fm3Music) {
        return projection(complete, S3kE4Projection.S3kE4Slot.FM3,
                fm3Sfx, fm3Music);
    }

    private static S3kE4Projection projection(boolean complete,
            S3kE4Projection.S3kE4Slot occupiedSlot,
            S3kE4Projection.S3kE4Track sfx,
            S3kE4Projection.S3kE4Track music) {
        List<S3kE4Projection.SlotProjection> slots = new ArrayList<>();
        for (S3kE4Projection.S3kE4Slot slot
                : S3kE4Projection.S3kE4Slot.values()) {
            slots.add(new S3kE4Projection.SlotProjection(slot,
                    S3kE4Projection.Availability.AVAILABLE,
                    slot == occupiedSlot ? sfx : null,
                    slot == occupiedSlot ? music : null));
        }
        return new S3kE4Projection(complete, slots);
    }

    private static S3kE4Projection.S3kE4Track track(
            S3kE4Projection.S3kE4Slot slot, boolean playing,
            boolean noAttack, boolean overriding, boolean special,
            int psgNoise, byte[] voice, int volume, int pan,
            boolean customSsgEgPresent, int[] customSsgEgPayload) {
        return new S3kE4Projection.S3kE4Track(
                new SmpsChannelOwnershipProjection.TrackCoordinate(0,
                        slot.ordinal(), false, source()),
                slot.rawVoiceControl(), playing, noAttack, overriding,
                special, 0, OptionalInt.empty(), 0, source(), voice,
                volume, pan, psgNoise, OptionalInt.empty(),
                customSsgEgPayload, customSsgEgPresent);
    }

    private static S3kE4Projection.S3kE4Track trackWithRawPsgNoise(
            S3kE4Projection.S3kE4Slot slot, boolean playing,
            boolean overriding, int rawPsgNoise) {
        return new S3kE4Projection.S3kE4Track(
                new SmpsChannelOwnershipProjection.TrackCoordinate(0,
                        slot.ordinal(), false, source()),
                slot.rawVoiceControl(), playing, false, overriding,
                true, 1, OptionalInt.empty(), 0, source(), null,
                0, 0xC0, rawPsgNoise & 0x0F,
                OptionalInt.of(rawPsgNoise), null, false);
    }

    private static SmpsSourceDescriptor source() {
        return new SmpsSourceDescriptor(SmpsSourceDescriptor.Kind.UNKNOWN,
                0, null, null, 0, 0, 0, false, 0);
    }
}
