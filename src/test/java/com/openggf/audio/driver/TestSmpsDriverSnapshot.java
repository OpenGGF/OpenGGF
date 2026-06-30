package com.openggf.audio.driver;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestSmpsDriverSnapshot {

    @Test
    void captureAndRestoreRoundTripsSequencersLocksLatchesAndContinuousSfxState() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = newSequencer("music", 0x81, driver);
        SmpsSequencer sfx = newSequencer("sfx", 0xBC, driver);
        sfx.setFallbackVoiceData(music.getSmpsData());

        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);
        driver.startContinuousSfx(0xBC, 3);
        assertTrue(driver.extendContinuousSfx(0xBC, 3));
        driver.decrementContSfxLoopCnt();
        driver.writeFm(sfx, 0, 0xA0, 0x22);
        driver.writePsg(sfx, 0x80 | (2 << 5) | 0x04);
        driver.writePsg(sfx, 0x06);

        SmpsDriverSnapshot snapshot = driver.captureSnapshot();

        driver.stopAll();
        driver.restoreSnapshot(snapshot);

        SmpsDriverSnapshot restored = driver.captureSnapshot();
        assertEquals(2, restored.sequencers().size());
        assertFalse(restored.sequencers().get(0).sfx());
        assertTrue(restored.sequencers().get(1).sfx());
        assertEquals(SmpsSourceDescriptor.from(music.getSmpsData()),
                restored.sequencers().get(0).source());
        assertEquals(SmpsSourceDescriptor.from(sfx.getSmpsData()),
                restored.sequencers().get(1).source());
        assertEquals(SmpsSourceDescriptor.from(music.getSmpsData()),
                restored.sequencers().get(1).fallbackVoiceSource());
        assertEquals(1, restored.fmLockSequencerIds()[0]);
        assertEquals(1, restored.psgLockSequencerIds()[2]);
        assertEquals(2, restored.sequencers().get(1).snapshot().psgLatchChannel());
        assertEquals(0xBC, restored.continuousSfxId());
        assertTrue(restored.continuousSfxFlag());
        assertEquals(2, restored.contSfxLoopCnt());
    }

    @Test
    void restoreCanResolveSequencerDependenciesFromSourceDescriptors() {
        SmpsDriver sourceDriver = new SmpsDriver();
        SmpsSequencer music = newSequencer("music", 0x81, sourceDriver);
        SmpsSequencer sfx = newSequencer("sfx", 0xBC, sourceDriver);
        sfx.setFallbackVoiceData(music.getSmpsData());
        sourceDriver.addSequencer(music, false);
        sourceDriver.addSequencer(sfx, true);

        SmpsDriverSnapshot snapshot = sourceDriver.captureSnapshot();
        AbstractSmpsData resolvedMusic = newData("resolved-music", 0x81);
        AbstractSmpsData resolvedSfx = newData("resolved-sfx", 0xBC);
        SmpsSequencerConfig resolvedConfig = new SmpsSequencerConfig.Builder()
                .tempoModBase(0x200)
                .build();
        var resolvedDac = new com.openggf.audio.smps.DacData(
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                144);

        SmpsDriver targetDriver = new SmpsDriver();
        targetDriver.restoreSnapshot(snapshot, new SmpsDriverSnapshot.DependencyResolver() {
            @Override
            public AbstractSmpsData resolveSmpsData(SmpsDriverSnapshot.SequencerEntry entry) {
                return entry.source().id() == 0x81 ? resolvedMusic : resolvedSfx;
            }

            @Override
            public com.openggf.audio.smps.DacData resolveDacData(SmpsDriverSnapshot.SequencerEntry entry) {
                return resolvedDac;
            }

            @Override
            public AudioManager resolveAudioManager(SmpsDriverSnapshot.SequencerEntry entry) {
                return AudioManager.getInstance();
            }

            @Override
            public SmpsSequencerConfig resolveConfig(SmpsDriverSnapshot.SequencerEntry entry) {
                return resolvedConfig;
            }
        });

        SmpsDriverSnapshot restored = targetDriver.captureSnapshot();
        assertSame(resolvedMusic, restored.sequencers().get(0).smpsData());
        assertSame(resolvedSfx, restored.sequencers().get(1).smpsData());
        assertSame(resolvedDac, restored.sequencers().get(0).dacData());
        assertSame(resolvedConfig, restored.sequencers().get(1).config());
        assertEquals(SmpsSourceDescriptor.from(resolvedMusic),
                restored.sequencers().get(1).fallbackVoiceSource());
    }

    @Test
    void capturePreservesRouteAwareSourceDescriptorsAndFallbackRoute() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = newSequencer("music", 0x81, driver);
        SmpsSequencer sfx = newSequencer("sfx", 0xBC, driver);
        music.setSourceDescriptor(SmpsSourceDescriptor.baseMusic(music.getSmpsData()));
        sfx.setSourceDescriptor(SmpsSourceDescriptor.baseSfx(sfx.getSmpsData()));
        sfx.setFallbackVoiceData(music.getSmpsData());
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);

        SmpsDriverSnapshot snapshot = driver.captureSnapshot();

        assertEquals(SmpsSourceDescriptor.Kind.BASE_MUSIC, snapshot.sequencers().get(0).source().kind());
        assertEquals(SmpsSourceDescriptor.Kind.BASE_SFX_ID, snapshot.sequencers().get(1).source().kind());
        assertEquals(SmpsSourceDescriptor.Kind.BASE_MUSIC,
                snapshot.sequencers().get(1).fallbackVoiceSource().kind());
    }

    @Test
    void restoreWithResolverIsAtomicWhenSourceIsMissing() {
        SmpsDriver sourceDriver = new SmpsDriver();
        sourceDriver.addSequencer(newSequencer("music", 0x81, sourceDriver), false);
        sourceDriver.addSequencer(newSequencer("sfx", 0xBC, sourceDriver), true);
        SmpsDriverSnapshot snapshot = sourceDriver.captureSnapshot();

        SmpsDriver targetDriver = new SmpsDriver();
        targetDriver.addSequencer(newSequencer("existing", 0x90, targetDriver), false);
        SmpsDriverSnapshot before = targetDriver.captureSnapshot();

        assertThrows(IllegalStateException.class, () -> targetDriver.restoreSnapshot(
                snapshot,
                new SmpsDriverSnapshot.DependencyResolver() {
                    @Override
                    public AbstractSmpsData resolveSmpsData(SmpsDriverSnapshot.SequencerEntry entry) {
                        if (entry.source().id() == 0xBC) {
                            throw new IllegalStateException("missing source");
                        }
                        return newData("resolved-music", 0x81);
                    }

                    @Override
                    public com.openggf.audio.smps.DacData resolveDacData(SmpsDriverSnapshot.SequencerEntry entry) {
                        return AudioTestFixtures.EMPTY_DAC;
                    }

                    @Override
                    public AudioManager resolveAudioManager(SmpsDriverSnapshot.SequencerEntry entry) {
                        return AudioManager.getInstance();
                    }

                    @Override
                    public SmpsSequencerConfig resolveConfig(SmpsDriverSnapshot.SequencerEntry entry) {
                        return new SmpsSequencerConfig.Builder().build();
                    }
                }));

        SmpsDriverSnapshot after = targetDriver.captureSnapshot();
        assertEquals(before.sequencers().size(), after.sequencers().size());
        assertEquals(before.sequencers().get(0).source(), after.sequencers().get(0).source());
    }

    @Test
    void restoreRoundTripsSynthSnapshotAfterLogicalSequencerRestore() {
        SmpsDriver uninterrupted = configuredDriver();
        SmpsDriver restored = configuredDriver();
        primeSynth(uninterrupted);
        primeSynth(restored);

        uninterrupted.renderFrames(new short[74], 0, 37);
        restored.renderFrames(new short[74], 0, 37);

        SmpsDriverSnapshot snapshot = uninterrupted.captureSnapshot();
        perturbSynth(uninterrupted);
        short[] expected = new short[192];
        uninterrupted.renderFrames(expected, 0, expected.length / 2);

        perturbSynth(restored);
        restored.restoreSnapshot(snapshot);
        perturbSynth(restored);
        short[] actual = new short[192];
        restored.renderFrames(actual, 0, actual.length / 2);

        assertArrayEquals(expected, actual);
    }

    private static SmpsSequencer newSequencer(String name, int id, SmpsDriver driver) {
        AbstractSmpsData data = newData(name, id);
        return new SmpsSequencer(
                data,
                AudioTestFixtures.EMPTY_DAC,
                driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().build());
    }

    private static AbstractSmpsData newData(String name, int id) {
        AbstractSmpsData data = new AudioTestFixtures.StubSmpsData(name);
        data.setId(id);
        return data;
    }

    private static SmpsDriver configuredDriver() {
        SmpsDriver driver = new SmpsDriver();
        driver.setDacData(new DacData(
                Map.of(1, new byte[] { 0, 24, 64, 127, (byte) 255, (byte) 196, 96, 32, 8, 0 }),
                Map.of(0x81, new DacData.DacEntry(1, 4)),
                295));
        driver.setDacInterpolate(true);
        return driver;
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
}
