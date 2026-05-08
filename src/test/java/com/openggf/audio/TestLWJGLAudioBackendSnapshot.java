package com.openggf.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestLWJGLAudioBackendSnapshot {
    @Test
    void restoreLogicalSnapshotPreservesCapturedSmpsDriverSynthState() {
        SmpsDriver source = configuredDriver();
        addSequencer(source);
        primeSynth(source);
        source.renderFrames(new short[74], 0, 37);

        AudioBackendLogicalSnapshot snapshot = new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x81),
                false,
                false,
                false,
                1,
                List.of(),
                source.captureSnapshot(),
                null);

        perturbSynth(source);
        short[] expected = new short[192];
        source.renderFrames(expected, 0, expected.length / 2);

        LWJGLAudioBackend backend = new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        backend.restoreLogicalSnapshot(snapshot);
        SmpsDriver restored = backend.musicDriverForTesting();
        assertNotNull(restored);

        perturbSynth(restored);
        short[] actual = new short[192];
        restored.renderFrames(actual, 0, actual.length / 2);

        assertArrayEquals(expected, actual);
    }

    @Test
    void restoreLogicalSnapshotRebindsRuntimeAndDropsUnrestorableOverridePlaceholders() {
        SmpsDriver source = configuredDriver();
        addSequencer(source);
        RecordingRuntime runtime = new RecordingRuntime();
        AudioBackendLogicalSnapshot snapshot = new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x81),
                false,
                true,
                false,
                1,
                List.of(AudioSourceDescriptor.baseMusic(0x82)),
                source.captureSnapshot(),
                null);

        LWJGLAudioBackend backend = new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        backend.attachDeterministicAudioRuntime(runtime);
        backend.restoreLogicalSnapshot(snapshot);

        assertSame(backend.musicDriverForTesting(), runtime.musicStream);
        assertEquals(1, runtime.flushes);
        AudioBackendLogicalSnapshot restored = backend.captureLogicalSnapshot();
        assertEquals(List.of(), restored.overrideStack());
        assertFalse(restored.pendingRestore());
    }

    @Test
    void restoreLogicalSnapshotCanPreserveRuntimePresentationFifo() {
        SmpsDriver source = configuredDriver();
        addSequencer(source);
        RecordingRuntime runtime = new RecordingRuntime();
        AudioBackendLogicalSnapshot snapshot = new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x81),
                false,
                false,
                false,
                1,
                List.of(),
                source.captureSnapshot(),
                null);

        LWJGLAudioBackend backend = new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        backend.attachDeterministicAudioRuntime(runtime);
        backend.restoreLogicalSnapshot(snapshot, SmpsDriverSnapshot.liveReferences(), true);

        assertSame(backend.musicDriverForTesting(), runtime.musicStream);
        assertEquals(0, runtime.flushes,
                "reverse restores must preserve queued reverse presentation PCM");
    }

    private static SmpsDriver configuredDriver() {
        SmpsDriver driver = new SmpsDriver();
        driver.setDacData(dacData());
        driver.setDacInterpolate(true);
        return driver;
    }

    private static void addSequencer(SmpsDriver driver) {
        AbstractSmpsData data = new AudioTestFixtures.StubSmpsData("music");
        data.setId(0x81);
        SmpsSequencer sequencer = new SmpsSequencer(
                data,
                dacData(),
                driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().build());
        driver.addSequencer(sequencer, false);
    }

    private static DacData dacData() {
        return new DacData(
                Map.of(1, new byte[] { 0, 24, 64, 127, (byte) 255, (byte) 196, 96, 32, 8, 0 }),
                Map.of(0x81, new DacData.DacEntry(1, 4)),
                295);
    }

    private static void primeSynth(SmpsDriver driver) {
        driver.writeFm(driver, 0, 0x22, 0x0B);
        driver.writeFm(driver, 0, 0x2B, 0x80);
        driver.setInstrument(driver, 0, new byte[] {
                0x32,
                0x71, 0x0D, 0x33, 0x01,
                0x5F, 0x5F, 0x5F, 0x5F,
                0x14, 0x0E, 0x0E, 0x0E,
                0x08, 0x08, 0x08, 0x08,
                0x0F, 0x0F, 0x0F, 0x0F,
                0x1B, 0x16, 0x1F, 0x00
        });
        driver.writeFm(driver, 0, 0xA4, 0x22);
        driver.writeFm(driver, 0, 0xA0, 0x69);
        driver.writeFm(driver, 0, 0xB4, 0xC7);
        driver.writeFm(driver, 0, 0x28, 0xF0);
        driver.playDac(driver, 0x81);
        driver.writePsg(driver, 0x80 | 0x04);
        driver.writePsg(driver, 0x12);
        driver.writePsg(driver, 0x90 | 0x02);
        driver.writePsg(driver, 0xE4);
        driver.writePsg(driver, 0xF0 | 0x04);
    }

    private static void perturbSynth(SmpsDriver driver) {
        driver.writeFm(driver, 0, 0x2A, 0x5A);
        driver.writeFm(driver, 0, 0x40, 0x23);
        driver.writePsg(driver, 0xE7);
        driver.writePsg(driver, 0xF2);
        driver.playDac(driver, 0x81);
    }

    private static final class RecordingRuntime implements DeterministicAudioRuntime {
        private Object musicStream;
        private int flushes;

        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
        }

        @Override
        public boolean providesPresentationPcm() {
            return true;
        }

        @Override
        public void setMusicStream(AudioStream stream) {
            musicStream = stream;
        }

        @Override
        public void flushPresentationFifo() {
            flushes++;
        }
    }
}
