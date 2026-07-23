package com.openggf.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsCompositeVoice {
    private static final int MAX_STEREO_FRAMES = 8;

    @Test
    void musicAndOwnedSfxRenderThroughOneComposite() {
        NoDeviceBackend backend = backend(false);
        AbstractSmpsData music = data("music", 0x81);
        AbstractSmpsData sfx = data("sfx", 0xB0);
        DacData dacData = dacData();
        backend.playSmps(music, dacData, config(), false);
        SmpsDriver musicDriver = backend.musicDriverForTesting();
        AudioStream currentStream = backend.currentStreamForTesting();
        SmpsCompositeVoice voice = composite(musicDriver);

        backend.playSfxSmps(sfx, dacData, 1.0f, config());

        assertSame(musicDriver, backend.musicDriverForTesting());
        assertSame(currentStream, backend.currentStreamForTesting());
        assertNull(backend.sfxStreamForTesting());
        assertSame(musicDriver, voice.driver());
        assertEquals(2, ((PresentationVoiceSnapshot.Smps) voice.snapshot()).driver().sequencers().size());

        RecordingSmpsDriver capacityDriver = new RecordingSmpsDriver();
        SmpsCompositeVoice capacityVoice = composite(capacityDriver);
        assertThrows(IllegalArgumentException.class,
                () -> capacityVoice.mixInto(new long[(MAX_STEREO_FRAMES + 1) * 2], MAX_STEREO_FRAMES + 1));
        assertEquals(0, capacityDriver.readCalls, "capacity rejection must happen before SmpsDriver.read");
    }

    @Test
    void driverChannelLocksAndPriorityRemainInsideComposite() {
        NoDeviceBackend backend = backend(false);
        backend.playSmps(data("music", 0x81), dacData(), config(), false);
        SmpsDriver musicDriver = backend.musicDriverForTesting();
        SmpsCompositeVoice voice = composite(musicDriver);
        backend.playSfxSmps(data("low", 0xB0), dacData(), 1.0f, config());
        backend.playSfxSmps(data("high", 0xB1), dacData(), 1.0f, config());

        PresentationVoiceSnapshot.Smps snapshot = (PresentationVoiceSnapshot.Smps) voice.snapshot();

        assertSame(musicDriver, backend.musicDriverForTesting());
        assertSame(backend.currentStreamForTesting(), voice.driver());
        assertNull(backend.sfxStreamForTesting());
        assertEquals(3, snapshot.driver().sequencers().size());
        assertEquals(0x20, snapshot.driver().sequencers().get(1).snapshot().sfxPriority());
        assertEquals(0x60, snapshot.driver().sequencers().get(2).snapshot().sfxPriority());
        assertFalse(snapshot.driver().sequencers().get(0).sfx());
        assertTrue(snapshot.driver().sequencers().get(1).sfx());
        assertTrue(snapshot.driver().sequencers().get(2).sfx());
    }

    @Test
    void dacFallbackAndContinuousSfxRemainInsideComposite() {
        NoDeviceBackend backend = backend(true);
        DacData dacData = dacData();
        AbstractSmpsData music = data("music", 0x81);
        AbstractSmpsData continuousSfx = data("continuous", 0xBC);
        backend.playSmps(music, dacData, config(), false);
        SmpsCompositeVoice voice = composite(backend.musicDriverForTesting());
        backend.playSfxSmps(continuousSfx, dacData, 1.0f, config());
        backend.playSfxSmps(continuousSfx, dacData, 1.0f, config());

        PresentationVoiceSnapshot.Smps snapshot = (PresentationVoiceSnapshot.Smps) voice.snapshot();

        assertSame(backend.musicDriverForTesting(), voice.driver());
        assertSame(backend.currentStreamForTesting(), voice.driver());
        assertNull(backend.sfxStreamForTesting());
        assertEquals(2, snapshot.driver().sequencers().size(),
                "continuous retrigger must extend the owned SFX instead of creating a standalone sequencer");
        assertEquals(0xBC, snapshot.driver().continuousSfxId());
        assertTrue(snapshot.driver().continuousSfxFlag());
        assertEquals(continuousSfx.getChannels() + continuousSfx.getPsgChannels(),
                snapshot.driver().contSfxLoopCnt());
        assertEquals(snapshot.driver().sequencers().get(0).source(),
                snapshot.driver().sequencers().get(1).fallbackVoiceSource());
        assertSame(dacData, snapshot.driver().sequencers().get(0).dacData());
        assertSame(dacData, snapshot.driver().sequencers().get(1).dacData());
    }

    @Test
    void standaloneSfxDriverIsASeparateCompositeOnlyWithoutMusicOwner() {
        NoDeviceBackend backend = backend(false);
        backend.playSfxSmps(data("first", 0xB0), dacData(), 1.0f, config());
        SmpsDriver standaloneSfxDriver = (SmpsDriver) backend.sfxStreamForTesting();
        SmpsCompositeVoice standalone = composite(standaloneSfxDriver);
        backend.playSfxSmps(data("second", 0xB1), dacData(), 1.0f, config());

        assertNull(backend.currentStreamForTesting());
        assertSame(standaloneSfxDriver, backend.sfxStreamForTesting());
        assertSame(standaloneSfxDriver, standalone.driver());
        assertEquals(2, ((PresentationVoiceSnapshot.Smps) standalone.snapshot()).driver().sequencers().size());
    }

    @Test
    void snapshotRestoreReproducesDriverStateAndNextPcm() {
        SmpsDriver driver = new SmpsDriver();
        SmpsCompositeVoice voice = composite(driver, 128);
        primeSynth(driver);
        voice.mixInto(new long[74], 37);
        PresentationVoiceSnapshot.Smps snapshot = (PresentationVoiceSnapshot.Smps) voice.snapshot();
        long[] expected = new long[192];
        voice.mixInto(expected, expected.length / 2);

        voice.restore(snapshot, SmpsDriverSnapshot.liveReferences());
        long[] actual = new long[192];
        voice.mixInto(actual, actual.length / 2);

        assertArrayEquals(expected, actual);
    }

    @Test
    void stopDelegatesToDriverStopAll() {
        RecordingSmpsDriver driver = new RecordingSmpsDriver();
        SmpsCompositeVoice voice = composite(driver);
        driver.addSequencer(sequencer("music", 0x81, driver), false);

        voice.stop();

        assertEquals(1, driver.stopAllCalls);
        assertTrue(voice.isComplete());
    }

    private static SmpsCompositeVoice composite(SmpsDriver driver) {
        return composite(driver, MAX_STEREO_FRAMES);
    }

    private static SmpsCompositeVoice composite(SmpsDriver driver, int maxStereoFrames) {
        return new SmpsCompositeVoice(42, 0, 0x81, AudioSourceDescriptor.baseMusic(0x81),
                maxStereoFrames, driver);
    }

    private static AbstractSmpsData data(String name, int id) {
        AbstractSmpsData data = new AudioTestFixtures.StubSmpsData(name);
        data.setId(id);
        return data;
    }

    private static SmpsSequencer sequencer(String name, int id, SmpsDriver driver) {
        return new SmpsSequencer(data(name, id), AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), config());
    }

    private static DacData dacData() {
        return new DacData(Map.of(1, new byte[] {0, 24, 64, 127}),
                Map.of(0x81, new DacData.DacEntry(1, 4)), 295);
    }

    private static void primeSynth(SmpsDriver driver) {
        driver.setDacData(dacData());
        driver.setDacInterpolate(true);
        driver.writeFm(driver, 0, 0x22, 0x0B);
        driver.writeFm(driver, 0, 0x2B, 0x80);
        driver.setInstrument(driver, 0, new byte[] {
                0x32, 0x71, 0x0D, 0x33, 0x01, 0x5F, 0x5F, 0x5F, 0x5F,
                0x14, 0x0E, 0x0E, 0x0E, 0x08, 0x08, 0x08, 0x08,
                0x0F, 0x0F, 0x0F, 0x0F, 0x1B, 0x16, 0x1F, 0x00
        });
        driver.writeFm(driver, 0, 0xA4, 0x22);
        driver.writeFm(driver, 0, 0xA0, 0x69);
        driver.writeFm(driver, 0, 0xB4, 0xC7);
        driver.writeFm(driver, 0, 0x28, 0xF0);
        driver.playDac(driver, 0x81);
        driver.writePsg(driver, 0x84);
        driver.writePsg(driver, 0x12);
        driver.writePsg(driver, 0x92);
    }

    private static NoDeviceBackend backend(boolean continuousSfx) {
        NoDeviceBackend backend = new NoDeviceBackend();
        backend.setAudioProfile(new AudioTestFixtures.StubAudioProfile(new AudioTestFixtures.StubSmpsLoader()) {
            @Override
            public SmpsSequencerConfig getSequencerConfig() {
                return config();
            }

            @Override
            public boolean isContinuousSfx(int sfxId) {
                return continuousSfx && sfxId == 0xBC;
            }

            @Override
            public int getSfxPriority(int soundId) {
                return soundId == 0xB0 ? 0x20 : soundId == 0xB1 ? 0x60 : 0x70;
            }
        });
        backend.init();
        return backend;
    }

    private static SmpsSequencerConfig config() {
        return new SmpsSequencerConfig.Builder().build();
    }

    private static final class NoDeviceBackend extends AbstractSmpsAudioBackend {
        private NoDeviceBackend() {
            super(SonicConfigurationService.getInstance(), null);
        }

        private AudioStream currentStreamForTesting() {
            return currentStream;
        }

        private AudioStream sfxStreamForTesting() {
            return sfxStream;
        }

        @Override protected int getDeviceSampleRate() { return 48_000; }
        @Override protected void hookInitDevice() { }
        @Override protected void hookDestroyDevice() { }
        @Override protected void hookStartStream() { }
        @Override protected void hookStopStreamSource() { }
        @Override protected void hookUpdateStream() { }
        @Override protected void hookStopAndClearMusicSource() { }
        @Override protected void hookStopAndUnqueueAllMusicBuffers() { }
        @Override protected void hookStopAndClearAllMusicBuffers() { }
        @Override protected void hookRestartStreamIfDry() { }
        @Override protected void hookStopAndDeleteWavSfxSources() { }
        @Override protected void hookUploadStreamBuffer(int bufferId, short[] pcm, int sampleRate) { }
        @Override protected void hookPlayWavSfx(String sfxName, float pitch) { }
        @Override protected void hookCleanupStoppedWavSfx() { }
        @Override protected void hookPause() { }
        @Override protected void hookResume() { }
    }

    private static final class RecordingSmpsDriver extends SmpsDriver {
        private int readCalls;
        private int stopAllCalls;

        @Override
        public int read(short[] buffer, int length) {
            readCalls++;
            for (int sample = 0; sample < length; sample += 2) {
                buffer[sample] = (short) (100 + sample / 2);
                buffer[sample + 1] = (short) (-100 - sample / 2);
            }
            return length;
        }

        @Override
        public void stopAll() {
            stopAllCalls++;
            super.stopAll();
        }
    }
}
