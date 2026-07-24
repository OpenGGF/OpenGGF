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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLWJGLAudioBackendSnapshot {
    @Test
    void logicalRestoreSynchronizesFmAndPsgUserMasksAndToggleSemantics() {
        SmpsDriver music = configuredDriver();
        addSequencer(music);
        SmpsDriver sfx = configuredDriver();
        addSfxSequencer(sfx);
        LWJGLAudioBackend backend =
                new LWJGLAudioBackend(
                        SonicConfigurationService.getInstance());
        backend.restoreLogicalSnapshot(new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x81), false, false,
                false, 1, List.of(), music.captureSnapshot(),
                sfx.captureSnapshot()));

        backend.toggleMute(ChannelType.FM, 2);
        backend.toggleSolo(ChannelType.PSG, 1);
        AudioBackendLogicalSnapshot selected =
                backend.captureLogicalSnapshot();
        assertEquals(1 << 2, selected.fmUserMuteMask());
        assertEquals(1 << 1, selected.psgUserSoloMask());

        backend.toggleMute(ChannelType.FM, 2);
        backend.toggleSolo(ChannelType.PSG, 1);
        backend.toggleSolo(ChannelType.FM, 4);
        backend.toggleMute(ChannelType.PSG, 3);
        backend.restoreLogicalSnapshot(selected);

        assertTrue(backend.isMuted(ChannelType.FM, 2));
        assertFalse(backend.isSoloed(ChannelType.FM, 4));
        assertTrue(backend.isSoloed(ChannelType.PSG, 1));
        assertFalse(backend.isMuted(ChannelType.PSG, 3));
        assertSynthMasks(selected.musicDriver(),
                backend.captureLogicalSnapshot().musicDriver());
        assertSynthMasks(selected.standaloneSfxDriver(),
                backend.captureLogicalSnapshot().standaloneSfxDriver());

        backend.toggleMute(ChannelType.FM, 2);
        backend.toggleSolo(ChannelType.PSG, 1);
        assertFalse(backend.isMuted(ChannelType.FM, 2),
                "next toggle must invert restored FM user state");
        assertFalse(backend.isSoloed(ChannelType.PSG, 1),
                "next toggle must invert restored PSG user state");
    }

    @Test
    void preparedRestoreLeavesLiveDriversUntouchedUntilCommit() {
        SmpsDriver liveSource = configuredDriver();
        addSequencer(liveSource);
        SmpsDriver targetMusic = configuredDriver();
        addSequencer(targetMusic);
        SmpsDriver targetSfx = configuredDriver();
        addSfxSequencer(targetSfx);
        LWJGLAudioBackend backend =
                new LWJGLAudioBackend(
                        SonicConfigurationService.getInstance());
        backend.restoreLogicalSnapshot(new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x80), false, false,
                false, 1, List.of(), liveSource.captureSnapshot(), null));
        AudioBackendLogicalSnapshot before =
                backend.captureLogicalSnapshot();
        SmpsDriver liveDriver = backend.musicDriverForTesting();
        AudioBackendLogicalSnapshot target =
                new AudioBackendLogicalSnapshot(
                        AudioSourceDescriptor.baseMusic(0x81), false, false,
                        true, 3, List.of(),
                        targetMusic.captureSnapshot(),
                        targetSfx.captureSnapshot());

        AudioBackend.PreparedLogicalRestore prepared =
                backend.prepareLogicalRestore(
                        target, SmpsDriverSnapshot.liveReferences(), true);

        assertSame(liveDriver, backend.musicDriverForTesting());
        AudioBackendLogicalSnapshot afterPrepare =
                backend.captureLogicalSnapshot();
        assertEquals(before.currentMusic(), afterPrepare.currentMusic());
        assertEquals(before.speedShoesEnabled(),
                afterPrepare.speedShoesEnabled());
        assertEquals(before.speedMultiplier(),
                afterPrepare.speedMultiplier());
        assertEquals(before.musicDriver().sequencers(),
                afterPrepare.musicDriver().sequencers());

        backend.commitLogicalRestore(prepared);
        assertNotSame(liveDriver, backend.musicDriverForTesting());
        AudioBackendLogicalSnapshot committed =
                backend.captureLogicalSnapshot();
        assertEquals(target.currentMusic(), committed.currentMusic());
        assertEquals(target.speedShoesEnabled(),
                committed.speedShoesEnabled());
        assertEquals(target.speedMultiplier(), committed.speedMultiplier());
        assertNotNull(committed.musicDriver());
        assertNotNull(committed.standaloneSfxDriver());
    }

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
    void restoreLogicalSnapshotReconstructsIndependentDriverBitExactly() {
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

        LWJGLAudioBackend freshBackend = new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        freshBackend.restoreLogicalSnapshot(snapshot);
        SmpsDriver fresh = freshBackend.musicDriverForTesting();
        assertNotNull(fresh);

        LWJGLAudioBackend reuseBackend = new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        reuseBackend.restoreLogicalSnapshot(snapshot);
        SmpsDriver first = reuseBackend.musicDriverForTesting();
        // Dirty the instance so the second restore must fully overwrite it.
        perturbSynth(first);
        first.renderFrames(new short[128], 0, 64);
        reuseBackend.restoreLogicalSnapshot(snapshot);
        SmpsDriver reused = reuseBackend.musicDriverForTesting();
        assertNotSame(first, reused,
                "restore preparation must not mutate the live driver before commit");

        perturbSynth(fresh);
        perturbSynth(reused);
        short[] expected = new short[192];
        short[] actual = new short[192];
        fresh.renderFrames(expected, 0, expected.length / 2);
        reused.renderFrames(actual, 0, actual.length / 2);

        assertArrayEquals(expected, actual,
                "reused driver instance must render bit-exactly like a freshly constructed restore");
    }

    @Test
    void failedMusicPreparationPreservesExactLiveStateAndRetrySucceeds() {
        SmpsDriver source = configuredDriver();
        addSequencer(source);
        primeSynth(source);
        source.renderFrames(new short[74], 0, 37);
        AudioBackendLogicalSnapshot snapshot = new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x81), false, false,
                true, 3, List.of(), source.captureSnapshot(), null);

        LWJGLAudioBackend backend =
                new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        backend.restoreLogicalSnapshot(snapshot);
        SmpsDriver live = backend.musicDriverForTesting();
        AudioBackendLogicalSnapshot before = backend.captureLogicalSnapshot();

        SmpsDriverSnapshot.DependencyResolver failure =
                new SmpsDriverSnapshot.DependencyResolver() {
                    @Override public AbstractSmpsData resolveSmpsData(
                            SmpsDriverSnapshot.SequencerEntry entry) {
                        throw new IllegalStateException("music unavailable");
                    }
                    @Override public DacData resolveDacData(
                            SmpsDriverSnapshot.SequencerEntry entry) {
                        return entry.dacData();
                    }
                    @Override public MusicRestoreSink resolveAudioManager(
                            SmpsDriverSnapshot.SequencerEntry entry) {
                        return entry.audioManager();
                    }
                    @Override public SmpsSequencerConfig resolveConfig(
                            SmpsDriverSnapshot.SequencerEntry entry) {
                        return entry.config();
                    }
                };

        assertThrows(IllegalStateException.class,
                () -> backend.restoreLogicalSnapshot(snapshot, failure, true));
        assertSame(live, backend.musicDriverForTesting());
        AudioBackendLogicalSnapshot afterFailure =
                backend.captureLogicalSnapshot();
        assertEquals(before.currentMusic(), afterFailure.currentMusic());
        assertEquals(before.speedShoesEnabled(),
                afterFailure.speedShoesEnabled());
        assertEquals(before.speedMultiplier(),
                afterFailure.speedMultiplier());
        assertEquals(before.legacyCoordFlagRuntimeState(),
                afterFailure.legacyCoordFlagRuntimeState());

        backend.restoreLogicalSnapshot(
                snapshot, SmpsDriverSnapshot.liveReferences(), true);
        assertNotSame(live, backend.musicDriverForTesting());
        AudioBackendLogicalSnapshot afterRetry =
                backend.captureLogicalSnapshot();
        assertEquals(snapshot.currentMusic(), afterRetry.currentMusic());
        assertEquals(snapshot.speedShoesEnabled(),
                afterRetry.speedShoesEnabled());
        assertEquals(snapshot.speedMultiplier(),
                afterRetry.speedMultiplier());
    }

    @Test
    void failedStandaloneSfxPreparationPreservesMusicIdentityAndRetrySucceeds() {
        SmpsDriver music = configuredDriver();
        addSequencer(music);
        SmpsDriver sfx = configuredDriver();
        addSfxSequencer(sfx);
        AudioBackendLogicalSnapshot target = new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x81), false, false,
                false, 1, List.of(), music.captureSnapshot(),
                sfx.captureSnapshot());
        LWJGLAudioBackend backend =
                new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        backend.restoreLogicalSnapshot(new AudioBackendLogicalSnapshot(
                target.currentMusic(), false, false, false, 1, List.of(),
                target.musicDriver(), null));
        SmpsDriver liveMusic = backend.musicDriverForTesting();

        SmpsDriverSnapshot.DependencyResolver failure =
                resolverFailing(entry -> entry.sfx());
        assertThrows(IllegalStateException.class,
                () -> backend.restoreLogicalSnapshot(target, failure, true));
        assertSame(liveMusic, backend.musicDriverForTesting());
        assertEquals(null,
                backend.captureLogicalSnapshot().standaloneSfxDriver());

        backend.restoreLogicalSnapshot(
                target, SmpsDriverSnapshot.liveReferences(), true);
        assertNotNull(
                backend.captureLogicalSnapshot().standaloneSfxDriver());
    }

    @Test
    void restoreLogicalSnapshotRejectsUnrestorableOverrideBeforeRuntimeRebind() {
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
        assertThrows(IllegalStateException.class,
                () -> backend.restoreLogicalSnapshot(snapshot));
        assertEquals(0, runtime.flushes);
        assertSame(null, runtime.musicStream);
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

    private static void assertSynthMasks(
            SmpsDriverSnapshot expected,
            SmpsDriverSnapshot actual) {
        assertArrayEquals(
                expected.synthSnapshot().ym().mutes(),
                actual.synthSnapshot().ym().mutes());
        assertArrayEquals(
                expected.synthSnapshot().psg().mutes(),
                actual.synthSnapshot().psg().mutes());
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

    private static void addSfxSequencer(SmpsDriver driver) {
        AbstractSmpsData data = new AudioTestFixtures.StubSmpsData("sfx");
        data.setId(0xA0);
        SmpsSequencer sequencer = new SmpsSequencer(
                data, dacData(), driver, AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().build());
        sequencer.setSfxMode(true);
        driver.addSequencer(sequencer, true);
    }

    private static SmpsDriverSnapshot.DependencyResolver resolverFailing(
            java.util.function.Predicate<SmpsDriverSnapshot.SequencerEntry>
                    predicate) {
        return new SmpsDriverSnapshot.DependencyResolver() {
            @Override public AbstractSmpsData resolveSmpsData(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                if (predicate.test(entry)) {
                    throw new IllegalStateException("dependency unavailable");
                }
                return entry.smpsData();
            }
            @Override public DacData resolveDacData(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                return entry.dacData();
            }
            @Override public MusicRestoreSink resolveAudioManager(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                return entry.audioManager();
            }
            @Override public SmpsSequencerConfig resolveConfig(
                    SmpsDriverSnapshot.SequencerEntry entry) {
                return entry.config();
            }
        };
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
