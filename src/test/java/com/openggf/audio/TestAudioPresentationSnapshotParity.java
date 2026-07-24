package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationDependencyResolver;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcm;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SampleBackedVoice;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSfxData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.IdentityHashMap;
import java.util.Map;

class TestAudioPresentationSnapshotParity {
    private AudioManager audio;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void logicalSnapshotCapturesAndRestoresPresentationFlagsAtSameBoundary() {
        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(3);
        audio.playSfx(GameSound.RING);
        audio.presentShadowFrame(PresentationMode.SILENT);

        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        AudioPresentationSnapshot expected = selected.presentation();
        assertTrue(expected.speedShoesEnabled());
        assertEquals(3, expected.speedMultiplier());
        assertFalse(expected.ringLeft());

        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.setSpeedShoes(false);
        audio.setSpeedMultiplier(1);
        audio.resetRingSound();
        audio.presentShadowFrame(PresentationMode.SILENT);
        assertNotEquals(expected, audio.captureLogicalSnapshot().presentation());

        audio.restoreLogicalSnapshot(selected);

        assertEquals(expected, audio.captureLogicalSnapshot().presentation());
        assertEquals(selected.backend(),
                audio.captureLogicalSnapshot().backend());
    }

    @Test
    void heldReverseDefersPresentationRestoreUntilRelease() {
        audio.toggleMute(ChannelType.FM, 0);
        audio.presentShadowFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();

        audio.toggleMute(ChannelType.FM, 1);
        audio.presentShadowFrame(PresentationMode.SILENT);
        AudioPresentationSnapshot disturbed =
                audio.captureLogicalSnapshot().presentation();

        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(selected);
        assertEquals(disturbed,
                audio.captureLogicalSnapshot().presentation(),
                "held reverse must preserve the active history/cursor state");

        audio.endReverseAudioPresentation();
        assertEquals(selected.presentation(),
                audio.captureLogicalSnapshot().presentation());
    }

    @Test
    void deferredTargetReplayAdvancesBothLegacyAndPresentationLogicalState() {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        AudioTestFixtures.StubSmpsData music =
                new AudioTestFixtures.StubSmpsData("target-music");
        music.setId(0x81);
        AudioTestFixtures.StubSmpsData sfx =
                new AudioTestFixtures.StubSmpsData("target-sfx");
        sfx.setId(0xA0);
        loader.musicResults.put(0x81, music);
        loader.sfxResults.put(0xA0, sfx);
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader) {
            @Override
            public com.openggf.audio.smps.SmpsSequencerConfig
                    getSequencerConfig() {
                return new com.openggf.audio.smps.SmpsSequencerConfig.Builder()
                        .build();
            }
        });
        audio.setRom(null);
        AudioKeyframeStore keyframes = new AudioKeyframeStore();
        audio.beginCommandTimelineFrame(10);
        keyframes.capture(10, audio);

        audio.beginCommandTimelineFrame(11);
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(3);
        audio.playMusic(0x81);
        audio.playSfx(0xA0);
        audio.resetRingSound();
        audio.presentShadowFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot expected = audio.captureLogicalSnapshot();

        audio.beginCommandTimelineFrame(12);
        audio.setSpeedShoes(false);
        audio.setSpeedMultiplier(1);
        audio.playSfx(GameSound.RING);
        audio.presentShadowFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot disturbed = audio.captureLogicalSnapshot();

        audio.beginReverseAudioPresentation();
        assertEquals(5, keyframes.replayToLogicalState(audio, 11));
        assertPresentationLogicalEquals(
                disturbed.presentation(),
                audio.captureLogicalSnapshot().presentation());
        PresentationVoiceSnapshot.Smps stagedMusic =
                (PresentationVoiceSnapshot.Smps) audio
                        .deferredReverseLogicalSnapshotForTesting()
                        .presentation().voices().getFirst();
        assertEquals(List.of(
                        com.openggf.audio.rewind.SmpsSourceDescriptor.Kind
                                .BASE_MUSIC,
                        com.openggf.audio.rewind.SmpsSourceDescriptor.Kind
                                .BASE_SFX_ID),
                stagedMusic.driver().sequencers().stream()
                        .map(entry -> entry.source().kind()).toList());
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        audio.endReverseAudioPresentation();

        AudioLogicalSnapshot actual = audio.captureLogicalSnapshot();
        assertEquals(expected.backend(), actual.backend());
        assertEquals(expected.presentation().activeMusic(),
                actual.presentation().activeMusic());
        assertTrue(actual.presentation().speedShoesEnabled());
        assertEquals(3, actual.presentation().speedMultiplier());
        assertEquals(1, actual.presentation().voices().size(),
                "reverse release removes replayed transient SFX");
        assertEquals(expected.ringLeft(), actual.ringLeft());
    }

    @Test
    void heldReplayNeverTouchesLiveSmpsBackendBeforeAtomicRelease() {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        for (int id : new int[] {0x80, 0x81, 0x82}) {
            AudioTestFixtures.StubSmpsData music =
                    new AudioTestFixtures.StubSmpsData("music-" + id);
            music.setId(id);
            loader.musicResults.put(id, music);
        }
        AudioTestFixtures.StubSmpsData sfx =
                new AudioTestFixtures.StubSmpsData("sfx-a0");
        sfx.setId(0xA0);
        loader.sfxResults.put(0xA0, sfx);
        audio.setAudioProfile(configuredProfile(loader));
        audio.setRom(null);
        LWJGLAudioBackend liveBackend =
                new LWJGLAudioBackend(
                        SonicConfigurationService.getInstance());
        audio.setBackend(liveBackend);
        liveBackend.setAudioProfile(audio.getAudioProfile());

        audio.beginCommandTimelineFrame(1);
        audio.playMusic(0x80);
        audio.presentShadowFrame(PresentationMode.SILENT);
        AudioKeyframeStore keyframes = new AudioKeyframeStore();
        keyframes.capture(1, audio);

        audio.beginCommandTimelineFrame(2);
        audio.playMusic(0x81);
        audio.playSfx(0xA0);
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(3);
        audio.presentShadowFrame(PresentationMode.SILENT);

        audio.beginCommandTimelineFrame(3);
        audio.playMusic(0x82);
        audio.presentShadowFrame(PresentationMode.SILENT);
        AudioBackendLogicalSnapshot disturbed =
                liveBackend.captureLogicalSnapshot();
        com.openggf.audio.driver.SmpsDriver disturbedDriver =
                liveBackend.musicDriverForTesting();

        audio.beginReverseAudioPresentation();
        assertEquals(4, keyframes.replayToLogicalState(audio, 2));
        assertSame(disturbedDriver, liveBackend.musicDriverForTesting());
        assertStableLiveBackend(disturbed,
                liveBackend.captureLogicalSnapshot());

        assertTrue(audio.commitDeferredReverseLogicalRestore());
        assertSame(disturbedDriver, liveBackend.musicDriverForTesting());
        assertStableLiveBackend(disturbed,
                liveBackend.captureLogicalSnapshot());

        audio.endReverseAudioPresentation();
        AudioBackendLogicalSnapshot released =
                liveBackend.captureLogicalSnapshot();
        assertEquals(AudioSourceDescriptor.baseMusic(0x81),
                released.currentMusic());
        assertNotNull(released.musicDriver());
        assertNotNull(released.musicDriver().sequencers().stream()
                .filter(com.openggf.audio.rewind.SmpsDriverSnapshot
                        .SequencerEntry::sfx)
                .findFirst().orElse(null));
        assertTrue(released.speedShoesEnabled());
        assertEquals(3, released.speedMultiplier());
    }

    @Test
    void backendCommitFailureLeavesDualReleaseExactlyRetryable()
            throws Exception {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        for (int id : new int[] {0x80, 0x81}) {
            AbstractSmpsData music = persistentRestTrack(id);
            music.setId(id);
            loader.musicResults.put(id, music);
        }
        audio.setAudioProfile(configuredS3kProfile(loader));
        audio.setRom(null);
        FailingCommitLwjglBackend backend =
                new FailingCommitLwjglBackend();
        audio.setBackend(backend);

        audio.playMusic(0x80);
        audio.setRewindHistoryArmed(true);
        for (int frame = 0; frame < 4; frame++) {
            audio.beginCommandTimelineFrame(frame);
            audio.presentOuterFrame(PresentationMode.FORWARD);
        }
        audio.beginCommandTimelineFrame(4);
        AudioKeyframeStore keyframes = new AudioKeyframeStore();
        keyframes.capture(4, audio);
        audio.beginCommandTimelineFrame(5);
        audio.playMusic(0x81);
        audio.presentOuterFrame(PresentationMode.FORWARD);

        audio.beginReverseAudioPresentation();
        audio.presentOuterFrame(PresentationMode.REVERSE);
        keyframes.replayToLogicalState(audio, 4);
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        ReleaseFingerprint before = releaseFingerprint(audio, backend);

        backend.failAfterNextPublication();
        audio.endReverseAudioPresentation();

        assertEquals(before, releaseFingerprint(audio, backend),
                "failed backend publication must preserve identities, "
                        + "registry, history, cursor, selection and crossfade");
        assertNotNull(audio.deferredReverseLogicalSnapshotForTesting(),
                "selected target must remain available for retry");

        audio.endReverseAudioPresentation();
        assertEquals(AudioSourceDescriptor.baseMusic(0x80),
                backend.captureLogicalSnapshot().currentMusic());
        assertEquals(null,
                audio.deferredReverseLogicalSnapshotForTesting());
    }

    @Test
    void dualSnapshotsRestoreIndependentEqualCoordFlagCounters() {
        LWJGLAudioBackend backend =
                new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        audio.setBackend(backend);
        audio.captureLogicalSnapshot();
        backend.legacyCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(7);
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(7);

        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();
        backend.legacyCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(99);
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(55);
        audio.restoreLogicalSnapshot(snapshot);

        assertEquals(7, backend.legacyCoordFlagHandlersForTesting().state()
                .spindashRevCounter());
        assertEquals(7, audio.presentationCoordFlagHandlersForTesting().state()
                .spindashRevCounter());
        assertFalse(backend.legacyCoordFlagHandlersForTesting().state()
                == audio.presentationCoordFlagHandlersForTesting().state());
    }

    @Test
    void controlledReferenceRendererMatchesExactClockCursorsForOneHundredTwentyFrames() {
        final int sampleRate = 11;
        final int frameRate = 3;
        DecodedPcm pcm = ramp("parity-loop", sampleRate, 127);
        AudioPresentationDependencyResolver resolver = resolver(pcm);
        AudioVoiceRegistry reference = registry(resolver);
        reference.apply(new AudioPresentationCommand.ReplaceMusic(
                AudioPresentationCommand.MusicVoiceEntry.fromVoice(
                        0x81, AudioSourceDescriptor.baseMusic(0x81),
                        SampleBackedVoice.loopingMusic(
                                1, pcm, sampleRate, 1.0f))));
        AudioPresentationMixer referenceMixer = new AudioPresentationMixer(4);
        AudioFrameClock referenceClock =
                new AudioFrameClock(sampleRate, frameRate);

        for (int frame = 0; frame < 120; frame++) {
            referenceMixer.mix(reference,
                    referenceClock.samplesForNextFrame());
        }
        AudioPresentationSnapshot selected = reference.snapshot();
        List<short[]> expected = new ArrayList<>();
        for (int frame = 0; frame < 10; frame++) {
            int frames = referenceClock.samplesForNextFrame();
            expected.add(java.util.Arrays.copyOf(
                    referenceMixer.mix(reference, frames), frames * 2));
        }

        AudioVoiceRegistry restored = registry(resolver);
        restored.restore(selected, resolver);
        AudioPresentationMixer restoredMixer = new AudioPresentationMixer(4);
        AudioFrameClock restoredClock =
                new AudioFrameClock(sampleRate, frameRate);
        for (int frame = 0; frame < 120; frame++) {
            restoredClock.samplesForNextFrame();
        }
        for (int frame = 0; frame < 10; frame++) {
            int frames = restoredClock.samplesForNextFrame();
            assertArrayEquals(expected.get(frame), java.util.Arrays.copyOf(
                    restoredMixer.mix(restored, frames), frames * 2));
        }
        assertEquals(reference.snapshot(), restored.snapshot(),
                "exact packet sizes and durable cursors must remain equal");
    }

    @Test
    void managerProductionProducerKeepsFractionalBoundaryForOneHundredTwentyFrames()
            throws Exception {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        for (int id : new int[] {0x81, 0x82}) {
            AbstractSmpsData music = persistentS3kMusic(id);
            music.setId(id);
            loader.musicResults.put(id, music);
        }
        AbstractSmpsData sfx = persistentS3kSfx(0xA0);
        sfx.setId(0xA0);
        loader.sfxResults.put(0xA0, sfx);
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader) {
            @Override public com.openggf.audio.smps.SmpsSequencerConfig
                    getSequencerConfig() {
                return Sonic3kSmpsSequencerConfig.CONFIG;
            }
            @Override public String presentationGameId() {
                return "s3k";
            }
            @Override public void configurePresentationCoordFlagHandlers(
                    SmpsCoordFlagHandlerOwner owner) {
                owner.register("s3k", Sonic3kCoordFlagHandler::new);
            }
            @Override
            public int getInvincibilityMusicId() {
                return 0x82;
            }
        });
        audio.setRom(null);
        audio.registerDonorLoader(
                "s3k", loader, loader.loadDacData(),
                Sonic3kSmpsSequencerConfig.CONFIG);
        HeadlessSmpsAudioBackend realBackend =
                new HeadlessSmpsAudioBackend(
                        SonicConfigurationService.createStandalone(),
                        PerformanceProfiler.getInstance());
        audio.setBackend(realBackend);
        assertTrue(audio.getBackend() instanceof HeadlessSmpsAudioBackend);
        assertFalse(realBackend.legacyCoordFlagHandlersForTesting().state()
                == audio.presentationCoordFlagHandlersForTesting().state());
        realBackend.legacyCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(7);
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(7);
        audio.playMusic(0x81);
        audio.playSfx(0xA0);
        byte[] rawPcm = new byte[20_000];
        for (int index = 0; index < rawPcm.length; index++) {
            rawPcm[index] = (byte) (index * 17);
        }
        audio.submitShadowRawPcmForTesting(rawPcm, 8_000);
        try (LiveCaptureAudioHandle capture =
                     audio.attachShadowCaptureForTesting(60)) {
            short[] packet =
                    new short[capture.maxStereoFramesPerPacket() * 2];
            for (int frame = 0; frame < 120; frame++) {
                audio.beginCommandTimelineFrame(frame);
                if (frame == 5) {
                    audio.setSpeedShoes(true);
                } else if (frame == 11) {
                    audio.playDonorMusic("s3k", 0x82);
                } else if (frame == 17) {
                    audio.setSpeedMultiplier(3);
                } else if (frame == 31) {
                    audio.toggleMute(ChannelType.FM, 2);
                } else if (frame == 47) {
                    audio.toggleSolo(ChannelType.PSG, 1);
                } else if (frame == 63) {
                    assertTrue(audio.captureLogicalSnapshot().ringLeft());
                    audio.playSfx(GameSound.RING);
                }
                audio.presentOuterFrame(PresentationMode.FORWARD);
                capture.drainPresentationFrame(packet);
                if (frame == 11) {
                    assertEquals(AudioSourceDescriptor.donorMusic(
                                    "s3k", 0x82),
                            audio.captureLogicalSnapshot().presentation()
                                    .activeMusic().sourceDescriptor(),
                            "S3K donor override must survive its first "
                                    + "production packet");
                }
            }

            AudioLogicalSnapshot boundary = audio.captureLogicalSnapshot();
            assertEquals(120,
                    audio.shadowParitySnapshotForTesting().presentedFrames());
            assertEquals(96_000, capture.totalStereoFrames());
            assertEquals(96_000,
                    capture.clockSnapshot().totalSamplesProduced());
            assertFalse(boundary.ringLeft(),
                    "real ring command must alternate left to right");
            assertFalse(boundary.presentation().ringLeft());
            assertEquals(boundary.backend()
                            .legacyCoordFlagRuntimeState()
                            .spindashRevCounter(),
                    boundary.presentation().coordFlagRuntimeState()
                            .spindashRevCounter(),
                    "real S3K music/SFX owners must reach equal state");
            assertTrue(boundary.presentation().speedShoesEnabled());
            assertEquals(3, boundary.presentation().speedMultiplier());
            assertEquals(1 << 2, boundary.presentation().fmMuteMask());
            assertEquals(1 << 1, boundary.presentation().psgSoloMask());
            assertNotNull(boundary.presentation().activeMusic());
            assertEquals(AudioSourceDescriptor.donorMusic(
                            "s3k", 0x82),
                    boundary.presentation().activeMusic()
                            .sourceDescriptor());
            assertEquals(1,
                    boundary.presentation().overrideStack().size());
            assertEquals(AudioSourceDescriptor.baseMusic(0x81),
                    boundary.presentation().overrideStack().getFirst()
                            .sourceDescriptor());
            assertNotNull(boundary.presentation().rawPcmVoiceId());
            assertTrue(boundary.presentation().voices().stream()
                    .filter(PresentationVoiceSnapshot.Smps.class::isInstance)
                    .map(PresentationVoiceSnapshot.Smps.class::cast)
                    .flatMap(voice -> voice.driver().sequencers().stream())
                    .anyMatch(
                            com.openggf.audio.rewind.SmpsDriverSnapshot
                                    .SequencerEntry::sfx));

            long before = capture.totalStereoFrames();
            int[] packetSizes = new int[10];
            short[][] actualPackets = new short[10][];

            AudioPresentationSourceFactory referenceFactory =
                    audio.shadowFactoryForTesting();
            AudioVoiceRegistry reference = new AudioVoiceRegistry(
                    referenceFactory, referenceFactory,
                    new SmpsCoordFlagHandlerOwner(
                            new SmpsCoordFlagRuntimeState()),
                    warning -> {
                        throw new AssertionError(warning);
                    });
            reference.restore(
                    boundary.presentation(), referenceFactory);
            assertPresentationSnapshotExactly(
                    boundary.presentation(), reference.snapshot());
            AudioPresentationMixer referenceMixer =
                    new AudioPresentationMixer(
                            capture.maxStereoFramesPerPacket());
            AudioFrameClock referenceClock =
                    new AudioFrameClock(48_000, 60);
            for (int frame = 0; frame < 120; frame++) {
                referenceClock.samplesForNextFrame();
            }
            for (int frame = 0; frame < 10; frame++) {
                audio.beginCommandTimelineFrame(120 + frame);
                audio.presentOuterFrame(PresentationMode.FORWARD);
                packetSizes[frame] =
                        capture.drainPresentationFrame(packet);
                actualPackets[frame] = java.util.Arrays.copyOf(
                        packet, packetSizes[frame] * 2);
                int referenceFrames =
                        referenceClock.samplesForNextFrame();
                assertEquals(referenceFrames, packetSizes[frame]);
                assertArrayEquals(
                        java.util.Arrays.copyOf(
                                referenceMixer.mix(
                                        reference, referenceFrames),
                                referenceFrames * 2),
                        actualPackets[frame],
                        "manager producer PCM must match independently "
                                + "reconstructed source-bearing state");
            }
            assertEquals(8_000,
                    capture.totalStereoFrames() - before);
            assertArrayEquals(
                    new int[] {800, 800, 800, 800, 800,
                            800, 800, 800, 800, 800},
                    packetSizes);
            assertPresentationSnapshotExactly(
                    audio.captureLogicalSnapshot().presentation(),
                    reference.snapshot());

            realBackend.legacyCoordFlagHandlersForTesting().state()
                    .setSpindashRevCounter(99);
            audio.presentationCoordFlagHandlersForTesting().state()
                    .setSpindashRevCounter(55);
            audio.restoreLogicalSnapshot(boundary);
            assertEquals(boundary.backend()
                            .legacyCoordFlagRuntimeState()
                            .spindashRevCounter(),
                    realBackend.legacyCoordFlagHandlersForTesting().state()
                            .spindashRevCounter());
            assertEquals(boundary.presentation().coordFlagRuntimeState()
                            .spindashRevCounter(),
                    audio.presentationCoordFlagHandlersForTesting().state()
                            .spindashRevCounter());
            assertFalse(realBackend.legacyCoordFlagHandlersForTesting().state()
                    == audio.presentationCoordFlagHandlersForTesting().state());
        }
    }

    private static AudioVoiceRegistry registry(
            AudioPresentationDependencyResolver resolver) {
        return new AudioVoiceRegistry(
                new com.openggf.audio.presentation.SmpsSfxInstantiation() {
                    @Override
                    public com.openggf.audio.smps.SmpsSequencer instantiateCached(
                            com.openggf.audio.presentation.ResolvedSmpsSfxSource source,
                            com.openggf.audio.driver.SmpsDriver currentOwner) {
                        throw new AssertionError("no SFX SMPS expected");
                    }

                    @Override
                    public SmpsCompositeVoice instantiateStandaloneCached(
                            com.openggf.audio.presentation.ResolvedSmpsSfxSource source) {
                        throw new AssertionError("no SFX SMPS expected");
                    }
                },
                resolver,
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState()),
                warning -> {
                    throw new AssertionError(warning);
                });
    }

    private static GameAudioProfile configuredProfile(
            AudioTestFixtures.StubSmpsLoader loader) {
        return new AudioTestFixtures.StubAudioProfile(loader) {
            @Override
            public com.openggf.audio.smps.SmpsSequencerConfig
                    getSequencerConfig() {
                return new com.openggf.audio.smps.SmpsSequencerConfig.Builder()
                        .build();
            }
        };
    }

    private static GameAudioProfile configuredS3kProfile(
            AudioTestFixtures.StubSmpsLoader loader) {
        return new AudioTestFixtures.StubAudioProfile(loader) {
            @Override public com.openggf.audio.smps.SmpsSequencerConfig
                    getSequencerConfig() {
                return Sonic3kSmpsSequencerConfig.CONFIG;
            }
            @Override public String presentationGameId() {
                return "s3k";
            }
            @Override public void configurePresentationCoordFlagHandlers(
                    SmpsCoordFlagHandlerOwner owner) {
                owner.register("s3k", Sonic3kCoordFlagHandler::new);
            }
        };
    }

    private static void assertStableLiveBackend(
            AudioBackendLogicalSnapshot expected,
            AudioBackendLogicalSnapshot actual) {
        assertEquals(expected.currentMusic(), actual.currentMusic());
        assertEquals(expected.sfxBlocked(), actual.sfxBlocked());
        assertEquals(expected.pendingRestore(), actual.pendingRestore());
        assertEquals(expected.speedShoesEnabled(),
                actual.speedShoesEnabled());
        assertEquals(expected.speedMultiplier(), actual.speedMultiplier());
        assertEquals(expected.overrideStack(), actual.overrideStack());
        assertEquals(expected.musicDriver().sequencers(),
                actual.musicDriver().sequencers(),
                "held replay must not erase or replace live sequencers");
        assertEquals(expected.standaloneSfxDriver(),
                actual.standaloneSfxDriver());
    }

    private static void assertPresentationLogicalEquals(
            AudioPresentationSnapshot expected,
            AudioPresentationSnapshot actual) {
        assertEquals(expected.activeMusic(), actual.activeMusic());
        assertEquals(expected.overrideStack(), actual.overrideStack());
        assertEquals(expected.standaloneSmpsVoiceId(),
                actual.standaloneSmpsVoiceId());
        assertEquals(expected.rawPcmVoiceId(), actual.rawPcmVoiceId());
        assertEquals(expected.fmMuteMask(), actual.fmMuteMask());
        assertEquals(expected.fmSoloMask(), actual.fmSoloMask());
        assertEquals(expected.psgMuteMask(), actual.psgMuteMask());
        assertEquals(expected.psgSoloMask(), actual.psgSoloMask());
        assertEquals(expected.sfxBlocked(), actual.sfxBlocked());
        assertEquals(expected.pendingRestore(), actual.pendingRestore());
        assertEquals(expected.speedShoesEnabled(),
                actual.speedShoesEnabled());
        assertEquals(expected.speedMultiplier(), actual.speedMultiplier());
        assertEquals(expected.ringLeft(), actual.ringLeft());
        assertEquals(expected.coordFlagRuntimeState(),
                actual.coordFlagRuntimeState());
        assertEquals(expected.voices().size(), actual.voices().size());
        for (int index = 0; index < expected.voices().size(); index++) {
            PresentationVoiceSnapshot left = expected.voices().get(index);
            PresentationVoiceSnapshot right = actual.voices().get(index);
            assertEquals(left.getClass(), right.getClass());
            if (left instanceof PresentationVoiceSnapshot.Smps leftSmps
                    && right instanceof PresentationVoiceSnapshot.Smps
                            rightSmps) {
                assertEquals(leftSmps.voiceId(), rightSmps.voiceId());
                assertEquals(leftSmps.musicId(), rightSmps.musicId());
                assertEquals(leftSmps.sourceDescriptor(),
                        rightSmps.sourceDescriptor());
                assertEquals(leftSmps.driver().continuousSfxId(),
                        rightSmps.driver().continuousSfxId());
                assertEquals(leftSmps.driver().sequencers().stream()
                                .map(entry -> entry.source()).toList(),
                        rightSmps.driver().sequencers().stream()
                                .map(entry -> entry.source()).toList());
            } else if (left instanceof PresentationVoiceSnapshot.Sample
                    leftSample
                    && right instanceof PresentationVoiceSnapshot.Sample
                            rightSample) {
                assertEquals(leftSample, rightSample);
            }
        }
    }

    private static void assertPresentationSnapshotExactly(
            AudioPresentationSnapshot expected,
            AudioPresentationSnapshot actual) {
        assertPresentationLogicalEquals(expected, actual);
        assertEquals(expected.nextVoiceId(), actual.nextVoiceId());
        for (int index = 0; index < expected.voices().size(); index++) {
            if (expected.voices().get(index)
                    instanceof PresentationVoiceSnapshot.Smps left
                    && actual.voices().get(index)
                    instanceof PresentationVoiceSnapshot.Smps right) {
                assertDriverSnapshotExactly(
                        left.driver(), right.driver());
            }
        }
    }

    private static void assertDriverSnapshotExactly(
            com.openggf.audio.rewind.SmpsDriverSnapshot expected,
            com.openggf.audio.rewind.SmpsDriverSnapshot actual) {
        assertEquals(expected.region(), actual.region());
        assertEquals(expected.readMode(), actual.readMode());
        assertEquals(expected.continuousSfxId(),
                actual.continuousSfxId());
        assertEquals(expected.continuousSfxFlag(),
                actual.continuousSfxFlag());
        assertEquals(expected.contSfxLoopCnt(), actual.contSfxLoopCnt());
        assertArrayEquals(expected.fmLockSequencerIds(),
                actual.fmLockSequencerIds());
        assertArrayEquals(expected.psgLockSequencerIds(),
                actual.psgLockSequencerIds());
        assertEquals(expected.sequencers().size(),
                actual.sequencers().size());
        for (int index = 0; index < expected.sequencers().size();
                index++) {
            var left = expected.sequencers().get(index);
            var right = actual.sequencers().get(index);
            assertEquals(left.sfx(), right.sfx());
            assertEquals(left.source(), right.source());
            assertEquals(left.fallbackVoiceSource(),
                    right.fallbackVoiceSource());
            assertDeepSnapshotEquals(left.snapshot(), right.snapshot());
        }
        assertDeepSnapshotEquals(expected.synthSnapshot(),
                actual.synthSnapshot());
    }

    private static void assertDeepSnapshotEquals(Object expected,
            Object actual) {
        assertDeepSnapshotEquals(expected, actual,
                new IdentityHashMap<>());
    }

    private static void assertDeepSnapshotEquals(Object expected,
            Object actual, Map<Object, Object> seen) {
        if (expected == actual) {
            return;
        }
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass());
        if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual));
            for (int index = 0; index < Array.getLength(expected); index++) {
                assertDeepSnapshotEquals(Array.get(expected, index),
                        Array.get(actual, index), seen);
            }
            return;
        }
        if (expected instanceof List<?> left
                && actual instanceof List<?> right) {
            assertEquals(left.size(), right.size());
            for (int index = 0; index < left.size(); index++) {
                assertDeepSnapshotEquals(left.get(index), right.get(index),
                        seen);
            }
            return;
        }
        if (!expected.getClass().isRecord()) {
            assertEquals(expected, actual);
            return;
        }
        if (seen.put(expected, actual) != null) {
            return;
        }
        for (RecordComponent component
                : expected.getClass().getRecordComponents()) {
            try {
                assertDeepSnapshotEquals(
                        component.getAccessor().invoke(expected),
                        component.getAccessor().invoke(actual), seen);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static ReleaseFingerprint releaseFingerprint(
            AudioManager manager, AbstractSmpsAudioBackend backend)
            throws Exception {
        Object producer = field(AudioManager.class, "shadowProducer")
                .get(manager);
        Object history = field(producer.getClass(), "history").get(producer);
        Object registry = field(producer.getClass(), "registry").get(producer);
        int voiceCount = (int) registry.getClass()
                .getMethod("orderedVoiceCount").invoke(registry);
        List<Integer> voiceIdentities = new ArrayList<>();
        for (int index = 0; index < voiceCount; index++) {
            Object voice = registry.getClass()
                    .getMethod("orderedVoiceAt", int.class)
                    .invoke(registry, index);
            voiceIdentities.add(System.identityHashCode(voice));
        }
        AudioLogicalSnapshot logical = manager.captureLogicalSnapshot();
        return new ReleaseFingerprint(
                System.identityHashCode(backend.musicDriverForTesting()),
                System.identityHashCode(field(
                        AbstractSmpsAudioBackend.class, "sfxStream")
                        .get(backend)),
                logical.ringLeft(),
                logical.commandTimelineFrame(),
                logical.commandTimelineNextOrder(),
                logical.backend().currentMusic(),
                logical.backend().speedShoesEnabled(),
                logical.backend().speedMultiplier(),
                logical.presentation().activeMusic(),
                logical.presentation().overrideStack(),
                logical.presentation().fmMuteMask(),
                logical.presentation().fmSoloMask(),
                logical.presentation().psgMuteMask(),
                logical.presentation().psgSoloMask(),
                logical.presentation().speedShoesEnabled(),
                logical.presentation().speedMultiplier(),
                logical.presentation().ringLeft(),
                voiceIdentities,
                System.identityHashCode(field(
                        producer.getClass(), "reverseCursor").get(producer)),
                System.identityHashCode(field(
                        producer.getClass(), "selectedRestore").get(producer)),
                System.identityHashCode(field(
                        producer.getClass(), "preparedSelectedRestore")
                        .get(producer)),
                field(history.getClass(), "nextFrameIndex").getLong(history),
                field(history.getClass(), "storedFrames").getInt(history),
                field(history.getClass(), "epoch").getLong(history),
                field(producer.getClass(), "releaseCrossfadeRemaining")
                        .getInt(producer),
                field(producer.getClass(), "lastReverseLeft")
                        .getShort(producer),
                field(producer.getClass(), "lastReverseRight")
                        .getShort(producer),
                field(producer.getClass(), "reverseActive")
                        .getBoolean(producer),
                field(producer.getClass(), "reverseFrameOutput")
                        .getBoolean(producer),
                field(producer.getClass(), "hasLastReverseFrame")
                        .getBoolean(producer));
    }

    private static Field field(Class<?> owner, String name)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record ReleaseFingerprint(
            int musicDriverIdentity,
            int sfxDriverIdentity,
            boolean managerRingLeft,
            long timelineFrame,
            int timelineOrder,
            AudioSourceDescriptor backendMusic,
            boolean backendSpeedShoes,
            int backendSpeedMultiplier,
            AudioPresentationSnapshot.MusicSlotSnapshot presentationMusic,
            List<AudioPresentationSnapshot.MusicSlotSnapshot>
                    presentationOverrides,
            int fmMuteMask,
            int fmSoloMask,
            int psgMuteMask,
            int psgSoloMask,
            boolean presentationSpeedShoes,
            int presentationSpeedMultiplier,
            boolean presentationRingLeft,
            List<Integer> voiceIdentities,
            int reverseCursorIdentity,
            int selectedRestoreIdentity,
            int preparedRestoreIdentity,
            long historyNextFrame,
            int historyStoredFrames,
            long historyEpoch,
            int crossfadeRemaining,
            short lastReverseLeft,
            short lastReverseRight,
            boolean reverseActive,
            boolean reverseFrameOutput,
            boolean hasLastReverseFrame) {
    }

    private static final class FailingCommitLwjglBackend
            extends LWJGLAudioBackend {
        private boolean failAfterPublication;

        private FailingCommitLwjglBackend() {
            super(SonicConfigurationService.createStandalone());
        }

        void failAfterNextPublication() {
            failAfterPublication = true;
        }

        @Override
        public void commitLogicalRestore(PreparedLogicalRestore prepared) {
            super.commitLogicalRestore(prepared);
            if (failAfterPublication) {
                failAfterPublication = false;
                throw new IllegalStateException(
                        "injected post-publication failure");
            }
        }

        @Override protected void hookInitDevice() {
        }
        @Override protected void hookDestroyDevice() {
        }
        @Override protected void hookStartStream() {
        }
        @Override protected void hookStopStreamSource() {
        }
        @Override protected void hookUpdateStream() {
        }
        @Override protected void hookStopAndClearMusicSource() {
        }
        @Override protected void hookStopAndUnqueueAllMusicBuffers() {
        }
        @Override protected void hookStopAndClearAllMusicBuffers() {
        }
        @Override protected void hookRestartStreamIfDry() {
        }
        @Override protected void hookUploadStreamBuffer(
                int bufferId, short[] pcm, int sampleRate) {
        }
        @Override protected void hookPlayWavSfx(
                String sfxName, float pitch) {
        }
        @Override protected void hookStopAndDeleteWavSfxSources() {
        }
        @Override protected void hookCleanupStoppedWavSfx() {
        }
        @Override protected void hookPause() {
        }
        @Override protected void hookResume() {
        }
    }

    private static AudioPresentationDependencyResolver resolver(
            DecodedPcm pcm) {
        return new AudioPresentationDependencyResolver() {
            @Override
            public DecodedPcm resolvePcm(String assetId) {
                assertEquals(pcm.assetId(), assetId);
                return pcm;
            }

            @Override
            public SmpsCompositeVoice recreateSmps(
                    PresentationVoiceSnapshot.Smps snapshot) {
                throw new AssertionError("no SMPS voice expected");
            }
        };
    }

    private static DecodedPcm ramp(
            String assetId, int sampleRate, int frames) {
        short[] samples = new short[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            samples[frame * 2] = (short) (frame * 13 - 500);
            samples[frame * 2 + 1] = (short) (700 - frame * 7);
        }
        return new DecodedPcm(assetId, 2, sampleRate, samples);
    }

    private static AbstractSmpsData persistentRestTrack(int id) {
        byte[] data = new byte[2_049];
        for (int index = 1; index < data.length; index += 2) {
            data[index] = (byte) 0x80;
            data[index + 1] = 0x7F;
        }
        return new AbstractSmpsData(data, 0) {
            {
                setId(id);
            }

            @Override
            protected void parseHeader() {
                channels = 1;
                fmPointers = new int[] {1};
                fmKeyOffsets = new int[] {0};
                fmVolumeOffsets = new int[] {0};
            }

            @Override
            public byte[] getVoice(int voiceId) {
                return new byte[25];
            }

            @Override
            public byte[] getPsgEnvelope(int envelopeId) {
                return new byte[] {0};
            }

            @Override
            public int read16(int offset) {
                return 0;
            }

            @Override
            public int getBaseNoteOffset() {
                return 0;
            }
        };
    }

    private static AbstractSmpsData persistentS3kMusic(int id) {
        byte[] data = new byte[0x4_000];
        setLe16(data, 0, 0x80);
        data[2] = 2;
        data[3] = 0;
        data[4] = 1;
        data[5] = (byte) 0x80;
        setLe16(data, 6, 0);
        setLe16(data, 10, 0x100);
        writePersistentS3kTrack(data, 0x100);
        Sonic3kSmpsData source = new Sonic3kSmpsData(data, 0);
        source.setId(id);
        return source;
    }

    private static AbstractSmpsData persistentS3kSfx(int id) {
        byte[] data = new byte[0x4_000];
        setLe16(data, 0, 0x80);
        data[2] = 1;
        data[3] = 1;
        data[4] = (byte) 0x80;
        data[5] = 0x02;
        setLe16(data, 6, 0x100);
        writePersistentS3kTrack(data, 0x100);
        Sonic3kSfxData source =
                new Sonic3kSfxData(data, 0, 0, 0);
        source.setId(id);
        return source;
    }

    private static void setLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void writePersistentS3kTrack(
            byte[] data, int offset) {
        data[offset] = (byte) 0x80;
        data[offset + 1] = 0x7F;
        data[offset + 2] = (byte) 0xF6;
        setLe16(data, offset + 3, offset);
    }
}
