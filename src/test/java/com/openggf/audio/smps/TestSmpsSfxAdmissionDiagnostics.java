package com.openggf.audio.smps;

import com.openggf.audio.AudioTestFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsSfxAdmissionDiagnostics {
    @ParameterizedTest
    @CsvSource({"3, 1, 1", "6, 32992, -1", "6, 0, -1"})
    void rejectedEntryIdentifiesItsProgramAndRelocation(int channel, int raw, int relocated) {
        var data = new InvalidSfx(channel, raw);
        var sequencer = new SmpsSequencer(data, AudioTestFixtures.EMPTY_DAC,
                () -> {}, new SmpsSequencerConfig.Builder().build());
        var failure = assertThrows(IllegalArgumentException.class, sequencer::validateSfxAdmissionMetadata);
        assertTrue(failure.getMessage().endsWith(
                "sfxId=0xD6 entry=1 channel=0x%02X rawPointer=0x%04X relocatedPointer=%d programLength=16"
                        .formatted(channel, raw, relocated)), failure::getMessage);
    }

    private record Track(int channelMask, int pointer, int transpose, int volume) implements SmpsSfxData.SmpsSfxTrack { }

    private static final class InvalidSfx extends AbstractSmpsData implements SmpsSfxData {
        private final int channel;
        private final int pointer;

        InvalidSfx(int channel, int pointer) {
            super(new byte[16], 0);
            this.channel = channel;
            this.pointer = pointer;
            setId(0xD6);
        }

        public int getTickMultiplier() { return 1; }
        public List<? extends SmpsSfxTrack> getTrackEntries() {
            return List.of(new Track(5, 1, 0, 0), new Track(channel, pointer, 0, 0));
        }
        protected void parseHeader() { dividingTiming = 1; }
        public byte[] getVoice(int id) { return null; }
        public byte[] getPsgEnvelope(int id) { return null; }
        public int getBaseNoteOffset() { return 0; }
        public int read16(int offset) { return 0; }
    }
}
