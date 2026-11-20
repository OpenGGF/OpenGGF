package com.openggf.audio.smps;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kRetailEnvelopes {
    @Test
    void psgVolumeTestsBitFourOfByteSumAndKeepsOtherBitsOnBus() {
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer sequencer = sequencer(Sonic3kSmpsSequencerConfig.CONFIG, synth);
        SmpsSequencer.Track track = new SmpsSequencer.Track(0, SmpsSequencer.TrackType.PSG, 0);

        track.envValue = 0x10;
        sequencer.refreshVolume(track);
        track.volumeOffset = 0x10;
        sequencer.refreshVolume(track);
        track.volumeOffset = 0xF0;
        sequencer.refreshVolume(track);
        track.volumeOffset = 0x60;
        track.envValue = 0x00;
        sequencer.refreshVolume(track);

        assertEquals(List.of(0x9F, 0xB0, 0x90, 0xF0), synth.writes);
    }

    @Test
    void psgNoiseVolumeAddsNoiseSelectorAfterCombiningTrackAndEnvelopeBits() {
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer sequencer = sequencer(Sonic3kSmpsSequencerConfig.CONFIG, synth);
        SmpsSequencer.Track track = new SmpsSequencer.Track(0, SmpsSequencer.TrackType.PSG, 2);
        track.noiseMode = true;
        track.envValue = 0x40;

        sequencer.refreshVolume(track);

        assertEquals(List.of(0xF0), synth.writes);
    }

    @Test
    void sonic2StillClampsPsgAttenuation() {
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer sequencer = sequencer(Sonic2SmpsSequencerConfig.CONFIG, synth);
        SmpsSequencer.Track track = new SmpsSequencer.Track(0, SmpsSequencer.TrackType.PSG, 0);
        track.envValue = 0x20;

        sequencer.refreshVolume(track);

        assertEquals(List.of(0x9F), synth.writes);
    }

    @Test
    void modulationUsesSignedSamplesAndEightBitSensitivityPlusOne() {
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer sequencer = sequencer(Sonic3kSmpsSequencerConfig.CONFIG, synth);
        SmpsSequencer.Track track = addContinuingPsgTrack(sequencer);
        track.modEnvData = new byte[] {(byte) 0xFF, (byte) 0xFD, 0, (byte) 0x83};
        track.modEnvMult = 2;

        sequencer.serviceOuterFrame();
        assertEquals(List.of(0x8D, 0x0F, 0x90), synth.writes);
        synth.writes.clear();

        sequencer.serviceOuterFrame();
        assertEquals(List.of(0x87, 0x0F, 0x90), synth.writes);
        synth.writes.clear();

        sequencer.serviceOuterFrame();
        assertEquals(List.of(0x80, 0x10, 0x90), synth.writes);
        synth.writes.clear();

        sequencer.serviceOuterFrame();
        assertEquals(List.of(), synth.writes);
        assertEquals(3, track.modEnvPos);
    }

    @Test
    void resolvedLoopResumesOnItsRetailTargetWithinTheSamePass() {
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer sequencer = sequencer(Sonic3kSmpsSequencerConfig.CONFIG, synth);
        SmpsSequencer.Track track = addContinuingPsgTrack(sequencer);
        track.modEnvData = new byte[] {0, 0, 1, 3, 1, 0,
                (byte) 0xFF, (byte) 0xFD, (byte) 0xFF, 0, (byte) 0x82, 6};
        track.modEnvId = 5;
        track.modEnvPos = 10;

        sequencer.serviceOuterFrame();

        assertEquals(List.of(0x8F, 0x0F, 0x90), synth.writes);
        assertEquals(7, track.modEnvPos);
    }

    private static SmpsSequencer.Track addContinuingPsgTrack(SmpsSequencer sequencer) {
        SmpsSequencer.Track track = new SmpsSequencer.Track(0, SmpsSequencer.TrackType.PSG, 0);
        track.active = true;
        track.duration = 20;
        track.baseFnum = 0x100;
        track.modEnabled = true;
        track.modEnvId = 1;
        sequencer.addTrack(track);
        return track;
    }

    private static SmpsSequencer sequencer(SmpsSequencerConfig config, CaptureSynth synth) {
        SmpsSequencer sequencer = new SmpsSequencer(new ProgramData(),
                AudioTestFixtures.EMPTY_DAC, synth, () -> { }, config);
        return sequencer;
    }

    private static final class CaptureSynth extends VirtualSynthesizer {
        private final List<Integer> writes = new ArrayList<>();

        @Override
        public void writePsg(Object source, int value) {
            writes.add(value & 0xFF);
            super.writePsg(source, value);
        }
    }

    private static final class ProgramData extends AbstractSmpsData {
        private ProgramData() {
            super(new byte[] {(byte) 0xF2}, 0);
            tempo = 1;
        }

        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int voiceId) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return null; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }
}
