package com.openggf.game.sonic3k.audio.smps;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FixBugs=0 semantic state tests for S3K's shared PlaybackControl bit zero. */
class TestSonic3kFm3SpecialMode {
    private static final DacData EMPTY_DAC = new DacData(
            new HashMap<>(), new HashMap<>(), 297);

    @Test
    void fm3F3SetsSpecialModeWithoutPsgWritesAndLeavesRawFrequencyIndependent() {
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer sequencer = fmSequencer(2,
                new byte[] {(byte) 0xF3, (byte) 0xE7,
                        (byte) 0xFD, 0x01, (byte) 0xF2}, synth);

        sequencer.advanceSamples(20_000);

        SmpsSequencer.Track fm3 = onlyTrack(sequencer);
        assertTrue(fm3.fm3SpecialMode);
        assertTrue(fm3.rawFreqMode);
        assertTrue(synth.psgWrites == 0,
                "FM3 F3 must not take the PSG-noise write path");
        assertEquals(0x45, fm3.pos,
                "F3 and FD must each consume exactly one operand before F2");
    }

    @Test
    void fm3F3ZeroClearsOnlyTheRetainedFm3State() {
        SmpsSequencer sequencer = fmSequencer(2,
                new byte[] {(byte) 0xF3, 0x00, (byte) 0xF2},
                new CaptureSynth());
        SmpsSequencer.Track fm3 = onlyTrack(sequencer);
        fm3.fm3SpecialMode = true;
        fm3.rawFreqMode = true;

        sequencer.advanceSamples(20_000);

        assertFalse(fm3.fm3SpecialMode);
        assertTrue(fm3.rawFreqMode,
                "F3 must not overload the independent FD raw-frequency state");
        assertEquals(0x43, fm3.pos);
    }

    @Test
    void fm3FeConsumesAllFourOperandsRetainsBitAndEmitsNoSpecialModeWrite() {
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer sequencer = fmSequencer(2,
                new byte[] {(byte) 0xFE, 0x01, 0x02, 0x03, 0x04,
                        (byte) 0xF2}, synth);

        sequencer.advanceSamples(20_000);

        SmpsSequencer.Track fm3 = onlyTrack(sequencer);
        assertTrue(fm3.fm3SpecialMode);
        assertEquals(0x46, fm3.pos,
                "FE must consume its four operands on the FixBugs=0 path");
        assertEquals(0, synth.fm3ModeWrites,
                "this tranche retains state only; it must not emit YM $27 writes");
    }

    @Test
    void nonFm3FeStillConsumesAllFourOperandsWithoutRetainingTheBit() {
        SmpsSequencer sequencer = fmSequencer(1,
                new byte[] {(byte) 0xFE, 0x01, 0x02, 0x03, 0x04,
                        (byte) 0xF2}, new CaptureSynth());

        sequencer.advanceSamples(20_000);

        SmpsSequencer.Track fm2 = onlyTrack(sequencer);
        assertFalse(fm2.fm3SpecialMode);
        assertEquals(0x46, fm2.pos);
    }

    @Test
    void nonFm3F3StillConsumesItsOperandWithoutChangingFm3State() {
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer sequencer = fmSequencer(3,
                new byte[] {(byte) 0xF3, (byte) 0xE7, (byte) 0xF2}, synth);

        sequencer.advanceSamples(20_000);

        SmpsSequencer.Track fm4 = onlyTrack(sequencer);
        assertFalse(fm4.fm3SpecialMode);
        assertEquals(0x43, fm4.pos);
        assertEquals(0, synth.psgWrites);
    }

    @Test
    void replacementTrackStartsWithSpecialModeClear() {
        SmpsSequencer original = fmSequencer(2,
                new byte[] {(byte) 0xFE, 1, 2, 3, 4, (byte) 0xF2},
                new CaptureSynth());
        original.advanceSamples(20_000);

        SmpsSequencer replacement = fmSequencer(2,
                new byte[] {(byte) 0xF2}, new CaptureSynth());

        assertTrue(onlyTrack(original).fm3SpecialMode);
        assertFalse(onlyTrack(replacement).fm3SpecialMode,
                "reinitialization must use Track's default state rather than leak FM3 mode");
    }

    private static SmpsSequencer fmSequencer(int channel, byte[] stream,
            CaptureSynth synth) {
        byte[] data = new byte[0x100];
        int headerIndex = channel + 1; // index 0 is DAC in the S3K FM table
        data[2] = (byte) (headerIndex + 1);
        data[4] = 1;
        data[5] = (byte) 0x80;
        setLe16(data, 0x06 + headerIndex * 4, 0x40);
        System.arraycopy(stream, 0, data, 0x40, stream.length);
        return new SmpsSequencer(new Sonic3kSmpsData(data, 0), EMPTY_DAC,
                synth, Sonic3kSmpsSequencerConfig.CONFIG);
    }

    private static SmpsSequencer.Track onlyTrack(SmpsSequencer sequencer) {
        assertEquals(1, sequencer.getTracks().size());
        return sequencer.getTracks().getFirst();
    }

    private static void setLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static final class CaptureSynth extends VirtualSynthesizer {
        private int psgWrites;
        private int fm3ModeWrites;

        @Override
        public void writePsg(Object source, int value) {
            psgWrites++;
            super.writePsg(source, value);
        }

        @Override
        public void writeFm(Object source, int port, int register, int value) {
            if ((register & 0xFF) == 0x27) {
                fm3ModeWrites++;
            }
            super.writeFm(source, port, register, value);
        }
    }
}
