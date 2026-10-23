package com.openggf.game.sonic2.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.rewind.SmpsTrackSnapshot;
import com.openggf.audio.session.SmpsChipWrite;
import com.openggf.audio.session.SmpsWriteProgram;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencer.TrackType;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free native pause vectors; no emulator, renderer, or live audio device. */
class TestSonic2NativePausePolicy {
    private static final Sonic2NativePausePolicy POLICY = Sonic2NativePausePolicy.INSTANCE;
    private static final byte[] VOICE = {
            0x2D, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
            13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24
    };

    @Test
    void supportedS2ResumeReturnsToDacWithoutContinuingOrdinaryService() {
        assertDoesNotThrow(POLICY::requireSupported);
        assertFalse(POLICY.resumeContinuesService());
    }

    @Test
    void pauseHasTheExactNativeCountdownKeyOffAndAscendingKillOrder() {
        List<SmpsChipWrite> writes = POLICY.pause().writes();
        assertEquals(202, writes.size());
        assertEquals(List.of(ym(0, 0x28, 2), ym(0, 0x28, 6), ym(0, 0x28, 1),
                ym(0, 0x28, 5), ym(0, 0x28, 0), ym(0, 0x28, 4)), writes.subList(0, 6));
        for (int register = 0x30; register <= 0x8F; register++) {
            int index = 6 + 2 * (register - 0x30);
            assertEquals(ym(0, register, 0xFF), writes.get(index));
            assertEquals(ym(1, register, 0xFF), writes.get(index + 1));
        }
        assertEquals(List.of(new SmpsChipWrite.Psg(0x9F), new SmpsChipWrite.Psg(0xBF),
                new SmpsChipWrite.Psg(0xDF), new SmpsChipWrite.Psg(0xFF)), writes.subList(198, 202));
        assertThrows(UnsupportedOperationException.class, () -> writes.add(ym(0, 0, 0)));
    }

    @Test
    void resumeUsesLiteralNativeVoiceRegisterOrderOnBothPortsIncludingBothPanWrites() {
        SmpsTrackSnapshot fm1 = track(TrackType.FM, 0, VOICE,
                Map.of("pan", 0x80, "ams", 2, "fms", 5, "volumeOffset", 2));
        SmpsTrackSnapshot fm5 = track(TrackType.FM, 4, VOICE,
                Map.of("pan", 0x40, "ams", 1, "fms", 3, "volumeOffset", 2));
        List<String> actual = strings(POLICY.resume(snapshot(entry(false, List.of(fm5, fm1)))));
        assertEquals(List.of(
                "0:B4:A5", "0:B0:2D",
                "0:30:01", "0:34:02", "0:38:03", "0:3C:04",
                "0:50:05", "0:54:06", "0:58:07", "0:5C:08",
                "0:60:09", "0:64:0A", "0:68:0B", "0:6C:0C",
                "0:70:0D", "0:74:0E", "0:78:0F", "0:7C:10",
                "0:80:11", "0:84:12", "0:88:13", "0:8C:14",
                "0:B4:A5", "0:40:15", "0:44:18", "0:48:19", "0:4C:1A",
                "1:B5:53", "1:B1:2D",
                "1:31:01", "1:35:02", "1:39:03", "1:3D:04",
                "1:51:05", "1:55:06", "1:59:07", "1:5D:08",
                "1:61:09", "1:65:0A", "1:69:0B", "1:6D:0C",
                "1:71:0D", "1:75:0E", "1:79:0F", "1:7D:10",
                "1:81:11", "1:85:12", "1:89:13", "1:8D:14",
                "1:B5:53", "1:41:15", "1:45:18", "1:49:19", "1:4D:1A"), actual);
    }

    @ParameterizedTest
    @CsvSource({
            "0,254,255,128,130", "1,254,255,128,130", "2,254,255,128,130", "3,254,255,128,130",
            "4,254,255,131,130", "5,254,2,131,130", "6,254,2,131,130", "7,1,2,131,130"
    })
    void everyAlgorithmUsesNativeCarrierBitsAndEightBitOverflow(int algorithm,
            int tl0, int tl1, int tl2, int tl3) {
        byte[] voice = VOICE.clone();
        voice[0] = (byte) (0x38 | algorithm);
        voice[21] = (byte) 0xFE;
        voice[22] = (byte) 0xFF;
        voice[23] = (byte) 0x80;
        voice[24] = 0x7F;
        SmpsTrackSnapshot track = track(TrackType.FM, 0, voice, Map.of("volumeOffset", 3));
        List<SmpsChipWrite> writes = POLICY.resume(snapshot(entry(false, List.of(track)))).writes();
        assertEquals(List.of(ym(0, 0x40, tl0), ym(0, 0x44, tl1),
                ym(0, 0x48, tl2), ym(0, 0x4C, tl3)), writes.subList(23, 27));
    }

    @Test
    void restingFmRestoresButInactiveOverriddenAndPsgTracksDoNotRequireVoices() {
        List<SmpsTrackSnapshot> tracks = List.of(
                track(TrackType.FM, 2, null, Map.of("overridden", true)),
                track(TrackType.FM, 99, null, Map.of("active", false)),
                track(TrackType.PSG, 0, null),
                track(TrackType.FM, 0, VOICE, Map.of("resting", true)));
        assertEquals(27, POLICY.resume(snapshot(entry(false, tracks))).writes().size());
    }

    @Test
    void liveSlotsUseDacThenMusicFmThenSfxRamOrderRegardlessOfEntryOrder() {
        SmpsDriverSnapshot logical = snapshot(
                entry(true, List.of(track(TrackType.FM, 4, VOICE))),
                entry(false, List.of(track(TrackType.FM, 5, VOICE),
                        track(TrackType.DAC, 5, null), track(TrackType.FM, 0, VOICE))),
                entry(true, List.of(track(TrackType.FM, 3, VOICE))),
                entry(true, List.of(track(TrackType.FM, 2, VOICE))));
        List<SmpsChipWrite> writes = POLICY.resume(logical).writes();
        assertEquals(162, writes.size());
        List<SmpsChipWrite> beginnings = new ArrayList<>();
        for (int index = 0; index < writes.size(); index += 27) beginnings.add(writes.get(index));
        assertEquals(List.of(ym(1, 0xB6, 0xC0), ym(0, 0xB4, 0xC0), ym(1, 0xB6, 0xC0),
                ym(0, 0xB6, 0xC0), ym(1, 0xB4, 0xC0), ym(1, 0xB5, 0xC0)), beginnings);
    }

    @Test
    void dacWithoutCachedVoiceResolvesItsIndexFromTheSelectedProgram() {
        byte[] different = VOICE.clone();
        different[0] = 0x3F;
        different[24] = (byte) 0xFF;
        SmpsTrackSnapshot dac = track(TrackType.DAC, 5, null, Map.of("voiceId", 1));
        SmpsDriverSnapshot logical = snapshot(entry(false, List.of(dac), program(VOICE, different)));
        List<SmpsChipWrite> writes = POLICY.resume(logical).writes();
        assertEquals(27, writes.size());
        assertEquals(ym(1, 0xB2, 0x3F), writes.get(1));
        assertEquals(ym(1, 0x4E, 0xFF), writes.get(26));
        assertNull(dac.voiceData(), "projection must not hydrate the DAC's logical cache");
        SmpsTrackSnapshot fm = track(TrackType.FM, 0, null);
        assertEquals(ym(0, 0xB0, VOICE[0] & 0xFF), POLICY.resume(snapshot(
                entry(false, List.of(fm)))).writes().get(1));
        assertNull(fm.voiceData(), "default instrument lookup must not upload a logical cache");
    }

    @Test
    void missingMalformedOrOutOfRangeActiveVoiceRejectsTheWholeProjection() {
        for (SmpsTrackSnapshot invalid : List.of(
                track(TrackType.FM, 1, new byte[24]),
                track(TrackType.FM, 1, new byte[26]),
                track(TrackType.DAC, 5, null, Map.of("voiceId", -1)),
                track(TrackType.DAC, 5, null, Map.of("voiceId", 256)))) {
            SmpsDriverSnapshot logical = snapshot(entry(false,
                    List.of(track(TrackType.FM, 0, VOICE), invalid)));
            assertThrows(IllegalStateException.class, () -> POLICY.resume(logical));
        }
        assertThrows(IllegalStateException.class, () -> POLICY.resume(snapshot(
                entry(false, List.of(track(TrackType.DAC, 5, null)), program()))));
        assertThrows(IllegalStateException.class, () -> POLICY.resume(snapshot(
                entry(false, List.of(track(TrackType.FM, 0, null)), program()))));
    }

    @Test
    void duplicateEligibleNativeSlotsAreRejectedAcrossAndWithinSequencers() {
        for (boolean sfx : List.of(false, true)) {
            int channel = sfx ? 2 : 0;
            SmpsTrackSnapshot track = track(TrackType.FM, channel, VOICE);
            assertThrows(IllegalStateException.class,
                    () -> POLICY.resume(snapshot(entry(sfx, List.of(track)), entry(sfx, List.of(track)))));
            assertThrows(IllegalStateException.class,
                    () -> POLICY.resume(snapshot(entry(sfx, List.of(track, track)))));
        }
        SmpsTrackSnapshot dac = track(TrackType.DAC, 5, null);
        assertThrows(IllegalStateException.class,
                () -> POLICY.resume(snapshot(entry(false, List.of(dac, dac)))));
    }

    @Test
    void activeTracksOutsideShippedNativeSlotsAreNotSilentlyDropped() {
        for (SmpsTrackSnapshot track : List.of(track(TrackType.FM, 0, VOICE),
                track(TrackType.FM, 5, VOICE), track(TrackType.DAC, 5, VOICE))) {
            assertThrows(IllegalStateException.class,
                    () -> POLICY.resume(snapshot(entry(true, List.of(track)))));
        }
        assertThrows(IllegalStateException.class,
                () -> POLICY.resume(snapshot(entry(false, List.of(track(TrackType.DAC, 0, VOICE))))));
    }

    @Test
    void savedMusicOverridesAreNotRestoredIntoTheForeground() {
        SmpsDriverSnapshot backup = snapshot(entry(false, List.of(track(TrackType.FM, 0, VOICE))));
        SmpsDriverSnapshot logical = new SmpsDriverSnapshot(SmpsSequencer.Region.NTSC,
                SmpsDriver.ReadMode.HYBRID, 0, false, 0, 5, List.of(),
                new int[6], new int[4], List.of(new SmpsDriverSnapshot.SavedOverride(backup)));
        assertEquals(SmpsWriteProgram.EMPTY, POLICY.resume(logical));
    }

    @Test
    void projectionIsDeterministicAndChangesNeitherLogicalTrackNorVoiceBytes() {
        SmpsTrackSnapshot track = track(TrackType.FM, 0, VOICE,
                Map.of("duration", 42, "pos", 27, "resting", true, "volumeOffset", 5));
        SmpsDriverSnapshot logical = snapshot(entry(false, List.of(track)));
        byte[] before = track.voiceData();
        SmpsWriteProgram first = POLICY.resume(logical);
        assertEquals(first, POLICY.resume(logical));
        assertArrayEquals(before, track.voiceData());
        assertEquals(42, track.duration());
        assertEquals(27, track.pos());
        assertTrue(track.resting());
        for (SmpsChipWrite write : first.writes()) {
            SmpsChipWrite.Ym2612 ym = assertInstanceOf(SmpsChipWrite.Ym2612.class, write);
            assertNotEquals(0x28, ym.register());
            assertNotEquals(0x2B, ym.register());
            assertFalse(ym.register() >= 0xA0 && ym.register() <= 0xAE,
                    "resume must not resend frequency or retrigger notes");
        }
    }

    private static SmpsChipWrite.Ym2612 ym(int port, int register, int value) {
        return new SmpsChipWrite.Ym2612(port, register, value);
    }

    private static List<String> strings(SmpsWriteProgram program) {
        return program.writes().stream().map(write -> {
            SmpsChipWrite.Ym2612 ym = assertInstanceOf(SmpsChipWrite.Ym2612.class, write);
            return "%d:%02X:%02X".formatted(ym.port(), ym.register(), ym.value());
        }).toList();
    }

    private static SmpsDriverSnapshot snapshot(SmpsDriverSnapshot.SequencerEntry... entries) {
        return new SmpsDriverSnapshot(SmpsSequencer.Region.NTSC, SmpsDriver.ReadMode.HYBRID,
                0, false, 0, 5, List.of(entries), new int[6], new int[4], List.of());
    }

    private static SmpsDriverSnapshot.SequencerEntry entry(boolean sfx, List<SmpsTrackSnapshot> tracks) {
        return entry(sfx, tracks, program(VOICE));
    }

    private static SmpsDriverSnapshot.SequencerEntry entry(boolean sfx, List<SmpsTrackSnapshot> tracks,
            AbstractSmpsData data) {
        SmpsSequencerSnapshot state = new SmpsSequencerSnapshot(SmpsSequencer.Region.NTSC,
                false, sfx, 0, 0, false, 0, 1.0f, 0, false, sfx, -1, 1, 0, 100, 0,
                new SmpsSequencerSnapshot.FadeSnapshot(0, 0, 0, 0, 0, false, false),
                44100.0, 735.0, 0.0, 0, 0, 1, true, tracks);
        return new SmpsDriverSnapshot.SequencerEntry(sfx, SmpsSourceDescriptor.from(data),
                null, data, null, () -> { }, Sonic2SmpsSequencerConfig.CONFIG, state);
    }

    private static Sonic2SmpsData program(byte[]... voices) {
        byte[] data = new byte[0x10 + 25 * voices.length];
        if (voices.length != 0) data[0] = 0x10;
        data[4] = 1;
        data[5] = (byte) 0x80;
        for (int index = 0; index < voices.length; index++) {
            System.arraycopy(voices[index], 0, data, 0x10 + index * 25, 25);
        }
        return new Sonic2SmpsData(data, 0);
    }

    private static SmpsTrackSnapshot track(TrackType type, int channel, byte[] voice) {
        return track(type, channel, voice, Map.of());
    }

    private static SmpsTrackSnapshot track(TrackType type, int channel, byte[] voice,
            Map<String, Object> fields) {
        Map<String, Object> selected = new HashMap<>(fields);
        selected.put("type", type);
        selected.put("channelId", channel);
        selected.put("voiceData", voice);
        selected.putIfAbsent("active", true);
        selected.putIfAbsent("pan", 0xC0);
        RecordComponent[] components = SmpsTrackSnapshot.class.getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            if (selected.containsKey(component.getName())) values[index] = selected.get(component.getName());
            else if (component.getType() == boolean.class) values[index] = false;
            else if (component.getType() == int.class) values[index] = 0;
            else if (component.getType() == short.class) values[index] = (short) 0;
            else if (component.getType().isPrimitive()) throw new AssertionError("unhandled snapshot primitive");
        }
        try {
            return SmpsTrackSnapshot.class.getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("cannot construct explicit snapshot vector", error);
        }
    }
}
