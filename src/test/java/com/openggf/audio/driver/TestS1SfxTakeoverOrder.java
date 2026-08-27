package com.openggf.audio.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestS1SfxTakeoverOrder {

    @Test
    void sonic1FmTakeoverStartsWithTheSfxRegisterInsteadOfSyntheticReset() {
        SmpsDriver driver = new SmpsDriver();
        RecordingObserver observer = new RecordingObserver();
        driver.setChipWriteObserver(observer);
        SmpsSequencer sfx = sequencer(driver, Sonic1SmpsSequencerConfig.CONFIG);
        driver.addSequencer(sfx, true);
        observer.events.clear();

        sfx.writeFm(1, 0xB1, 0x3C);

        assertEquals(List.of("YM:1:B1:3C"), observer.events,
                "S1 SetVoice must be the first visible FM5 takeover write");
    }

    @Test
    void legacyProfilesRetainTheirExistingSyntheticTakeoverPolicy() {
        SmpsDriver driver = new SmpsDriver();
        RecordingObserver observer = new RecordingObserver();
        driver.setChipWriteObserver(observer);
        SmpsSequencer sfx = sequencer(driver, new SmpsSequencerConfig.Builder().build());
        driver.addSequencer(sfx, true);
        observer.events.clear();

        sfx.writeFm(1, 0xB1, 0x3C);

        assertEquals(List.of("YM:0:28:05", "YM:1:B1:3C"), observer.events);
    }

    private static SmpsSequencer sequencer(SmpsDriver driver, SmpsSequencerConfig config) {
        return new SmpsSequencer(new SingleFm5SfxData(), AudioTestFixtures.EMPTY_DAC,
                driver, () -> {}, config);
    }

    private static final class SingleFm5SfxData extends AbstractSmpsData
            implements SmpsSfxData {
        private SingleFm5SfxData() {
            super(new byte[] {0, (byte) 0xF2}, 0);
            setId(0xC1);
        }

        @Override public int getTickMultiplier() { return 1; }
        @Override public List<? extends SmpsSfxTrack> getTrackEntries() {
            return List.of(new Track(5, 1, 0, 0));
        }
        @Override protected void parseHeader() { dividingTiming = 1; tempo = 1; }
        @Override public byte[] getVoice(int voiceId) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return null; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }

    private record Track(int channelMask, int pointer, int transpose, int volume)
            implements SmpsSfxData.SmpsSfxTrack {
    }

    private static final class RecordingObserver implements ChipWriteObserver {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onYm2612Write(int port, int register, int value) {
            events.add("YM:%d:%02X:%02X".formatted(port, register, value));
        }

        @Override
        public void onPsgWrite(int value) {
            events.add("PSG:%02X".formatted(value));
        }
    }
}
