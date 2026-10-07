package com.openggf.audio.smps;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.driver.SmpsDriver;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSmpsNoteListener {
    @Test void unmappedDacDurationOnlyAfterRestDoesNotInventAnAttack() {
        var dac = new DacData(Map.of(1, new byte[] {(byte) 128}),
                Map.of(0x81, new DacData.DacEntry(1, 1)));
        SmpsSequencer seq = new SmpsSequencer(new Program(new byte[] {(byte) 0x81, 1, (byte) 0x80, 1, 1}),
                dac, new SmpsDriver(), () -> { }, new SmpsSequencerConfig.Builder().build());
        seq.addTrack(new SmpsSequencer.Track(0, SmpsSequencer.TrackType.DAC, 5));
        List<Integer> attacks = new ArrayList<>();
        seq.setNoteListener(new SmpsNoteListener() {
            @Override public void attack(SmpsSequencer.Track track) { attacks.add(track.note); }
            @Override public void release(SmpsSequencer.Track track) { }
        });
        for (int index = 0; index < 3; index++) seq.serviceOuterFrame();
        assertEquals(List.of(0x81), attacks);
    }

    @Test void rawFrequencyAttackAndZeroReleaseAreObservable() {
        SmpsSequencer seq = new SmpsSequencer(new Program(new byte[] {0x34, 0x12, 1, 0, 0, 1}),
                AudioTestFixtures.EMPTY_DAC, new SmpsDriver(), () -> { }, new SmpsSequencerConfig.Builder().build());
        SmpsSequencer.Track track = new SmpsSequencer.Track(0, SmpsSequencer.TrackType.FM, 0);
        track.rawFreqMode = true; seq.addTrack(track);
        List<Integer> attacks = new ArrayList<>();
        seq.setNoteListener(new SmpsNoteListener() {
            @Override public void attack(SmpsSequencer.Track data) { attacks.add(data.rawFrequency); }
            @Override public void release(SmpsSequencer.Track data) { }
        });
        seq.serviceOuterFrame(); seq.serviceOuterFrame();
        assertEquals(List.of(0x1234), attacks);
    }
    private static final class Program extends AbstractSmpsData {
        Program(byte[] bytes) { super(bytes, 0); tempo = 0xFF; }
        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int id) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }

    @Test void repeatedPitchAndDurationOnlyAreSeparateAttacksButRestIsNot() {
        byte[] bytes = {(byte) 0x85, 1, (byte) 0x85, 1, 1, (byte) 0x80, 1};
        SmpsSequencer seq = new SmpsSequencer(new Program(bytes), AudioTestFixtures.EMPTY_DAC,
                new SmpsDriver(), new SmpsSequencerConfig.Builder().build());
        seq.addTrack(new SmpsSequencer.Track(0, SmpsSequencer.TrackType.FM, 0));
        List<Integer> attacks = new ArrayList<>();
        seq.setNoteListener(new SmpsNoteListener() {
            @Override public void attack(SmpsSequencer.Track track) { attacks.add(track.note); }
            @Override public void release(SmpsSequencer.Track track) { }
        });
        for (int index = 0; index < 4; index++) seq.serviceOuterFrame();
        assertEquals(List.of(0x85, 0x85, 0x85), attacks);
    }

    @Test void holdExtendsOneAttackInsteadOfCreatingAnother() {
        byte[] bytes = {(byte) 0x85, 1, (byte) 0xE7, (byte) 0x87, 1, (byte) 0x80, 1};
        SmpsSequencer seq = new SmpsSequencer(new Program(bytes), AudioTestFixtures.EMPTY_DAC,
                new SmpsDriver(), new SmpsSequencerConfig.Builder()
                .noteOnPrevent(SmpsSequencerConfig.NoteOnPrevent.HOLD).build());
        seq.addTrack(new SmpsSequencer.Track(0, SmpsSequencer.TrackType.FM, 0));
        List<Integer> attacks = new ArrayList<>();
        seq.setNoteListener(new SmpsNoteListener() {
            @Override public void attack(SmpsSequencer.Track track) { attacks.add(track.note); }
            @Override public void release(SmpsSequencer.Track track) { }
        });
        for (int index = 0; index < 3; index++) seq.serviceOuterFrame();
        assertEquals(List.of(0x85), attacks);
    }
}
