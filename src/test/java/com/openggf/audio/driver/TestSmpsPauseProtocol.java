package com.openggf.audio.driver;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.synth.ChipWriteObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsPauseProtocol {

    @Test
    void s1PausePansAndKeysOffAllFmThenSilencesPsg() {
        List<String> writes = pauseWrites(
                SmpsSequencerConfig.PausePolicy.S1_PAN_KEYOFF);

        assertEquals(List.of(
                "ym:0:b4:00", "ym:1:b4:00",
                "ym:0:b5:00", "ym:1:b5:00",
                "ym:0:b6:00", "ym:1:b6:00",
                "ym:0:28:00", "ym:0:28:01", "ym:0:28:02",
                "ym:0:28:04", "ym:0:28:05", "ym:0:28:06",
                "psg:9f", "psg:bf", "psg:df", "psg:ff"), writes);
    }

    @Test
    void s2PauseUsesDestructiveSilencerBeforePsg() {
        List<String> writes = pauseWrites(
                SmpsSequencerConfig.PausePolicy.S2_SILENCE_RELOAD);

        assertEquals(List.of(
                "ym:0:28:02", "ym:0:28:06", "ym:0:28:01",
                "ym:0:28:05", "ym:0:28:00", "ym:0:28:04"),
                writes.subList(0, 6));
        assertEquals("ym:0:30:ff", writes.get(6));
        assertEquals("ym:1:8f:ff", writes.get(197));
        assertEquals(List.of("psg:9f", "psg:bf", "psg:df", "psg:ff"),
                writes.subList(198, 202));
        assertEquals(202, writes.size());
    }

    @Test
    void s3kPauseLeavesFm6DacAndRepeatsRetailPsgSilence() {
        List<String> writes = pauseWrites(
                SmpsSequencerConfig.PausePolicy.S3K_FM1_TO_5);

        assertEquals(List.of("psg:9f", "psg:bf", "psg:df", "psg:ff"),
                writes.subList(0, 4));
        assertEquals(List.of(
                "ym:0:b4:00", "ym:0:b5:00", "ym:0:b6:00",
                "ym:1:b4:00", "ym:1:b5:00"), writes.subList(4, 9));
        assertEquals(List.of(
                "ym:0:28:00", "ym:0:28:01", "ym:0:28:02",
                "ym:0:28:03", "ym:0:28:04", "ym:0:28:05"),
                writes.subList(9, 15));
        assertEquals(List.of("psg:9f", "psg:bf", "psg:df", "psg:ff"),
                writes.subList(15, 19));
    }

    @Test
    void resumeRestoresPanForS1AndS3kButReloadsTheS2Voice() {
        List<String> s1 = resumeWrites(
                SmpsSequencerConfig.PausePolicy.S1_PAN_KEYOFF);
        List<String> s2 = resumeWrites(
                SmpsSequencerConfig.PausePolicy.S2_SILENCE_RELOAD);
        List<String> s3k = resumeWrites(
                SmpsSequencerConfig.PausePolicy.S3K_FM1_TO_5);

        assertEquals(List.of("ym:0:b4:c0"), s1);
        assertEquals(List.of("ym:0:b4:c0"), s3k);
        assertEquals(26, s2.size(),
                "S2 reloads the 25-byte voice register sequence on resume");
        assertTrue(s2.contains("ym:0:b4:c0"));
    }

    @Test
    void pausedHardwareAdvanceMovesDacWithoutServicingTracks() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer sequencer = new SmpsSequencer(
                new OneFmTrackData(), AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), new SmpsSequencerConfig.Builder()
                        .pausePolicy(SmpsSequencerConfig.PausePolicy.S1_PAN_KEYOFF)
                        .build());
        driver.addSequencer(sequencer, false);
        driver.setDacData(new DacData(
                Map.of(1, new byte[4096]),
                Map.of(0x81, new DacData.DacEntry(1, 4)), 295));
        driver.playDac(null, 0x81);
        driver.writeFm(null, 0, 0x2B, 0x80);
        var beforeSequencer = sequencer.captureSnapshot();
        double beforeDacPosition = driver.captureSynthSnapshot().ym().dacPos();

        driver.advancePausedHardware(800);

        assertTrue(driver.captureSynthSnapshot().ym().dacPos()
                        > beforeDacPosition,
                "the independent DAC loop continues while the driver is paused");
        var afterSequencer = sequencer.captureSnapshot();
        assertEquals(beforeSequencer.sampleCounter(),
                afterSequencer.sampleCounter());
        assertEquals(beforeSequencer.tempoAccumulator(),
                afterSequencer.tempoAccumulator());
        assertEquals(beforeSequencer.tracks().getFirst().pos(),
                afterSequencer.tracks().getFirst().pos());
        assertEquals(beforeSequencer.tracks().getFirst().duration(),
                afterSequencer.tracks().getFirst().duration());
        assertEquals(beforeSequencer.tracks().getFirst().modStepCounter(),
                afterSequencer.tracks().getFirst().modStepCounter(),
                "pause must not service music, SFX, envelopes or modulation");
    }

    private static List<String> pauseWrites(
            SmpsSequencerConfig.PausePolicy policy) {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .pausePolicy(policy)
                .build();
        driver.addSequencer(new SmpsSequencer(
                new AudioTestFixtures.StubSmpsData("pause"),
                AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), config), false);
        List<String> writes = new ArrayList<>();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                writes.add("ym:%d:%02x:%02x".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
                writes.add("psg:%02x".formatted(value));
            }
        });

        driver.pauseAudio();

        return writes;
    }

    private static List<String> resumeWrites(
            SmpsSequencerConfig.PausePolicy policy) {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .fmChannelOrder(new int[] {0})
                .pausePolicy(policy)
                .build();
        SmpsSequencer sequencer = new SmpsSequencer(
                new OneFmTrackData(), AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), config);
        assertEquals(1, sequencer.trackCount());
        driver.addSequencer(sequencer, false);
        List<String> writes = new ArrayList<>();
        driver.setChipWriteObserver(recording(writes));

        driver.resumeAudio();

        return writes;
    }

    private static ChipWriteObserver recording(List<String> writes) {
        return new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                writes.add("ym:%d:%02x:%02x".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
                writes.add("psg:%02x".formatted(value));
            }
        };
    }

    private static final class OneFmTrackData extends AbstractSmpsData {
        private OneFmTrackData() {
            super(new byte[] {0, (byte) 0xF2}, 0);
        }

        @Override
        protected void parseHeader() {
            fmPointers = new int[] {1};
            fmKeyOffsets = new int[] {0};
            fmVolumeOffsets = new int[] {0};
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return new byte[25];
        }

        @Override
        public byte[] getPsgEnvelope(int id) {
            return new byte[0];
        }

        @Override
        public int read16(int offset) {
            return 0;
        }

        @Override
        public int getBaseNoteOffset() {
            return 0;
        }
    }
}
