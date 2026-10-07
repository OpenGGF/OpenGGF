package com.openggf.audio.smps;

import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Native saved RAM and rest-frequency branches, independent of any song timestamps. */
class TestSmpsSavedDurationRest {
    static Stream<Arguments> drivers() {
        return Stream.of(
                Arguments.of("S1", Sonic1SmpsSequencerConfig.CONFIG, 0x7F),
                Arguments.of("S2", Sonic2SmpsSequencerConfig.CONFIG, 0xFF),
                Arguments.of("S3K", Sonic3kSmpsSequencerConfig.CONFIG, 0));
    }

    static Stream<Arguments> driverTracks() {
        return drivers().flatMap(a -> Stream.of(SmpsSequencer.TrackType.FM,
                SmpsSequencer.TrackType.PSG, SmpsSequencer.TrackType.DAC)
                .map(type -> Arguments.of(a.get()[0], a.get()[1], a.get()[2], type)));
    }

    @ParameterizedTest(name = "{0} {3} saved duration, rest and snapshot")
    @MethodSource("driverTracks")
    void restAndDividerChangesPreserveSavedDurationThroughSnapshot(String driver,
            SmpsSequencerConfig config, int tempo, SmpsSequencer.TrackType type) {
        // SetDuration saves 4*2=8. A rest without a duration reuses that RAM
        // byte, as does the next note after another divider change. Only the
        // final explicit duration replaces it with 4*3=12. Native owners:
        // S1 SetDuration/FinishTrackUpdate/DACUpdateTrack; S2/S3K zSetDuration
        // and zFinishTrackUpdate. No rest routine clears SavedDuration.
        byte[] program = {(byte) 0xE5, 2, (byte) 0x81, 4,
                (byte) 0xE5, 1, (byte) 0x80,
                (byte) 0xE5, 3, (byte) 0x82, (byte) 0x83, 4, (byte) 0xF2};
        program = withNativeDividerFlags(driver, program);
        var seq = sequencer(config, tempo, program, type);
        seq.serviceOuterFrame();
        assertEquals(8, seq.trackAt(0).scaledDuration);
        advanceToNote(seq, 0x80);
        var rest = seq.trackAt(0);
        assertEquals(8, rest.duration, driver + "/" + type);
        assertEquals(8, rest.scaledDuration);
        assertEquals(4, rest.rawDuration);
        assertEquals(1, rest.dividingTiming);
        assertEquals(type != SmpsSequencer.TrackType.DAC, rest.resting);

        var snapshot = seq.captureSnapshot();
        advanceToNote(seq, 0x82);
        assertEquals(8, seq.trackAt(0).duration);
        assertEquals(8, seq.trackAt(0).scaledDuration);
        assertEquals(3, seq.trackAt(0).dividingTiming);
        advanceToNote(seq, 0x83);
        assertEquals(12, seq.trackAt(0).duration);
        assertEquals(12, seq.trackAt(0).scaledDuration);

        seq.restoreSnapshot(snapshot);
        var expected = tail(seq);
        seq.restoreSnapshot(snapshot);
        assertEquals(expected, tail(seq), "rest/saved frequency/saved duration replay");
        assertFalse(seq.trackAt(0).active);
    }

    @ParameterizedTest(name = "{0} PSG rest followed by positive durations")
    @MethodSource("drivers")
    void durationOnlyPsgChecksTheSavedFrequencyBeforeAttacking(String driver,
            SmpsSequencerConfig config, int tempo) {
        // S1 PSGSetFreq stores FFFF on rest; PSGDoNoteOn's bmi PSGSetRest
        // checks that word even after PSGDoNext cleared the rest bit. S2
        // zPSGDoNoteOn tests FreqHigh bit7 likewise. S3K zRestTrack keeps the
        // frequency, so positive durations can re-attack it (DelayFreq.KEEP).
        // Run the same stream on a tone and on PSG3 tone-clocked noise.
        for (boolean noise : List.of(false, true)) {
            byte[] program = {(byte) 0xF3, (byte) (noise ? 0xE7 : 0),
                    (byte) 0x81, 2, (byte) 0x80, 3,
                    (byte) 0xE5, 2, 3, 4,
                    (byte) 0xE5, 1, (byte) 0x82, (byte) 0xF2};
            program = withNativeDividerFlags(driver, program);
            int firstDurationEnd = driver.equals("S3K") ? 10 : 9;
            int secondDurationEnd = firstDurationEnd + 1;
            var seq = sequencer(config, tempo, program, SmpsSequencer.TrackType.PSG);
            List<Integer> attacks = new ArrayList<>();
            seq.setNoteListener(new SmpsNoteListener() {
                public void attack(SmpsSequencer.Track track) { attacks.add(track.note); }
                public void release(SmpsSequencer.Track track) { }
            });
            seq.serviceOuterFrame();
            advanceToNote(seq, 0x80);
            assertTrue(seq.trackAt(0).resting);
            boolean reset = config.getDelayFreq() == SmpsSequencerConfig.DelayFreq.RESET;
            if (reset) assertEquals(0xFFFF, seq.trackAt(0).baseFnum);
            int guard = 0;
            while (seq.trackAt(0).pos < secondDurationEnd && guard++ < 40) {
                seq.serviceOuterFrame();
                if (seq.trackAt(0).pos == firstDurationEnd || seq.trackAt(0).pos == secondDurationEnd)
                    assertEquals(reset, seq.trackAt(0).resting, driver + " noise=" + noise);
            }
            assertEquals(secondDurationEnd, seq.trackAt(0).pos);
            assertEquals(8, seq.trackAt(0).duration);
            assertEquals(8, seq.trackAt(0).scaledDuration);
            assertEquals(2, seq.trackAt(0).dividingTiming);
            var snapshot = seq.captureSnapshot();
            var expected = tail(seq);
            assertEquals(reset ? List.of(0x81, 0x82) : List.of(0x81, 0x80, 0x80, 0x82), attacks);
            attacks.clear();
            seq.restoreSnapshot(snapshot);
            assertEquals(expected, tail(seq));
            assertEquals(List.of(0x82), attacks);
        }
    }

    private static byte[] withNativeDividerFlags(String driver, byte[] generatedStream) {
        // These generated streams contain E5 only as a divider command. S3K
        // uses the FF04 meta command, cfSetTempoDivider, rather than S1/S2 E5.
        if (!driver.equals("S3K")) return generatedStream;
        var out = new ByteArrayOutputStream();
        for (byte value : generatedStream) {
            if ((value & 0xFF) == 0xE5) {
                out.write(0xFF);
                out.write(0x04);
            } else out.write(value);
        }
        return out.toByteArray();
    }

    private static SmpsSequencer sequencer(SmpsSequencerConfig config, int tempo,
            byte[] program, SmpsSequencer.TrackType type) {
        // Tiny generated DAC fixture, not game assets, makes valid attacks
        // observable for all three source kinds without a device or global host.
        var dac = new DacData(Map.of(0, new byte[] {0}), Map.of(
                0x81, new DacData.DacEntry(0, 1), 0x82, new DacData.DacEntry(0, 1),
                0x83, new DacData.DacEntry(0, 1)));
        var seq = new SmpsSequencer(new TestSmpsSequencerCadence.ProgramMusicData(tempo, program),
                dac, () -> { }, config);
        seq.addTrack(new SmpsSequencer.Track(0, type, type == SmpsSequencer.TrackType.PSG ? 2 : 0));
        return seq;
    }

    private static void advanceToNote(SmpsSequencer seq, int note) {
        int guard = 0;
        while (seq.trackAt(0).active && seq.trackAt(0).note != note && guard++ < 50)
            seq.serviceOuterFrame();
        assertEquals(note, seq.trackAt(0).note);
    }

    private record State(int pos, int note, int duration, int saved, int divider,
            int frequency, boolean resting, boolean active) { }

    private static List<State> tail(SmpsSequencer seq) {
        List<State> states = new ArrayList<>();
        for (int guard = 0; seq.trackAt(0).active && guard < 80; guard++) {
            seq.serviceOuterFrame();
            var t = seq.trackAt(0);
            states.add(new State(t.pos, t.note, t.duration, t.scaledDuration,
                    t.dividingTiming, t.baseFnum, t.resting, t.active));
        }
        assertFalse(seq.trackAt(0).active, "bounded native stream reaches its stop");
        return states;
    }
}
