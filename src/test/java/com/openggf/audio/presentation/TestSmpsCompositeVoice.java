package com.openggf.audio.presentation;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
        RecordingSmpsDriver driver = new RecordingSmpsDriver();
        SmpsCompositeVoice voice = composite(driver);
        List<PresentationVoice> voices = new ArrayList<>();
        voices.add(voice);
        SmpsSequencer music = sequencer("music", 0x81, driver);
        SmpsSequencer sfx = sequencer("sfx", 0xB0, driver);

        assertSame(driver, voice.driver());
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);
        assertSame(driver, voice.driver());
        assertEquals(1, voices.size());
        assertSame(voice, voices.get(0));

        assertThrows(IllegalArgumentException.class,
                () -> voice.mixInto(new long[(MAX_STEREO_FRAMES + 1) * 2], MAX_STEREO_FRAMES + 1));
        assertEquals(0, driver.readCalls, "capacity rejection must happen before SmpsDriver.read");

        long[] mixed = new long[MAX_STEREO_FRAMES * 2];
        voice.mixInto(mixed, MAX_STEREO_FRAMES);

        assertEquals(1, driver.readCalls);
        assertArrayEquals(new long[] {100, -100, 101, -101, 102, -102, 103, -103,
                104, -104, 105, -105, 106, -106, 107, -107}, mixed);
    }

    @Test
    void driverChannelLocksAndPriorityRemainInsideComposite() {
        SmpsDriver driver = new SmpsDriver();
        SmpsCompositeVoice voice = composite(driver);
        SmpsSequencer music = sequencer("music", 0x81, driver);
        SmpsSequencer lowPriority = sequencer("low", 0xB0, driver);
        SmpsSequencer highPriority = sequencer("high", 0xB1, driver);
        lowPriority.setSfxPriority(0x20);
        highPriority.setSfxPriority(0x60);
        driver.addSequencer(music, false);
        driver.addSequencer(lowPriority, true);
        driver.addSequencer(highPriority, true);
        driver.writeFm(lowPriority, 0, 0xA0, 0x22);
        driver.writeFm(highPriority, 0, 0xA0, 0x44);

        PresentationVoiceSnapshot.Smps snapshot = (PresentationVoiceSnapshot.Smps) voice.snapshot();

        assertSame(driver, voice.driver());
        assertEquals(2, snapshot.driver().fmLockSequencerIds()[0]);
        assertEquals(0x60, snapshot.driver().sequencers().get(2).snapshot().sfxPriority());
        assertFalse(snapshot.driver().sequencers().get(0).sfx());
        assertTrue(snapshot.driver().sequencers().get(1).sfx());
        assertTrue(snapshot.driver().sequencers().get(2).sfx());
    }

    @Test
    void dacFallbackAndContinuousSfxRemainInsideComposite() {
        SmpsDriver driver = new SmpsDriver();
        SmpsCompositeVoice voice = composite(driver);
        DacData dacData = dacData();
        SmpsSequencer music = sequencer("music", 0x81, driver, dacData);
        SmpsSequencer sfx = sequencer("continuous", 0xBC, driver, dacData);
        sfx.setFallbackVoiceData(music.getSmpsData());
        driver.setDacData(dacData);
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);
        driver.startContinuousSfx(0xBC, 3);
        assertTrue(driver.extendContinuousSfx(0xBC, 3));

        PresentationVoiceSnapshot.Smps snapshot = (PresentationVoiceSnapshot.Smps) voice.snapshot();

        assertSame(driver, voice.driver());
        assertEquals(0xBC, snapshot.driver().continuousSfxId());
        assertTrue(snapshot.driver().continuousSfxFlag());
        assertEquals(3, snapshot.driver().contSfxLoopCnt());
        assertEquals(snapshot.driver().sequencers().get(0).source(),
                snapshot.driver().sequencers().get(1).fallbackVoiceSource());
        assertSame(dacData, snapshot.driver().sequencers().get(0).dacData());
    }

    @Test
    void standaloneSfxDriverIsASeparateCompositeOnlyWithoutMusicOwner() {
        RecordingSmpsDriver standaloneSfxDriver = new RecordingSmpsDriver();
        SmpsCompositeVoice standalone = composite(standaloneSfxDriver);
        List<PresentationVoice> voices = new ArrayList<>();
        standaloneSfxDriver.addSequencer(sequencer("sfx", 0xB0, standaloneSfxDriver), true);
        voices.add(standalone);

        assertNull(standaloneSfxDriver.firstMusicSequencer());
        assertEquals(1, voices.size());
        assertSame(standaloneSfxDriver, ((SmpsCompositeVoice) voices.get(0)).driver());
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

    private static SmpsSequencer sequencer(String name, int id, SmpsDriver driver) {
        return sequencer(name, id, driver, AudioTestFixtures.EMPTY_DAC);
    }

    private static SmpsSequencer sequencer(String name, int id, SmpsDriver driver, DacData dacData) {
        AbstractSmpsData data = new AudioTestFixtures.StubSmpsData(name);
        data.setId(id);
        return new SmpsSequencer(data, dacData, driver, AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().build());
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
