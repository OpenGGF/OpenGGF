package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationSnapshot;
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
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.ArrayList;
import java.util.List;

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
        audio.setBackend(new NullAudioBackend() {
            @Override
            public int outputSampleRate() {
                return 44_101;
            }
        });
        try (LiveCaptureAudioHandle capture =
                     audio.attachShadowCaptureForTesting(60)) {
            short[] packet =
                    new short[capture.maxStereoFramesPerPacket() * 2];
            for (int frame = 0; frame < 120; frame++) {
                audio.beginCommandTimelineFrame(frame);
                if (frame == 5) {
                    audio.setSpeedShoes(true);
                } else if (frame == 17) {
                    audio.setSpeedMultiplier(3);
                } else if (frame == 31) {
                    audio.toggleMute(ChannelType.FM, 2);
                } else if (frame == 47) {
                    audio.toggleSolo(ChannelType.PSG, 1);
                } else if (frame == 63) {
                    audio.resetRingSound();
                }
                audio.presentOuterFrame(PresentationMode.FORWARD);
                capture.drainPresentationFrame(packet);
            }

            AudioLogicalSnapshot boundary = audio.captureLogicalSnapshot();
            assertEquals(120,
                    audio.shadowParitySnapshotForTesting().presentedFrames());
            assertEquals(88_202, capture.totalStereoFrames());
            assertEquals(88_202,
                    capture.clockSnapshot().totalSamplesProduced());
            assertTrue(boundary.presentation().speedShoesEnabled());
            assertEquals(3, boundary.presentation().speedMultiplier());
            assertEquals(1 << 2, boundary.presentation().fmMuteMask());
            assertEquals(1 << 1, boundary.presentation().psgSoloMask());

            long before = capture.totalStereoFrames();
            int[] packetSizes = new int[10];
            for (int frame = 0; frame < 10; frame++) {
                audio.beginCommandTimelineFrame(120 + frame);
                audio.presentOuterFrame(PresentationMode.FORWARD);
                packetSizes[frame] =
                        capture.drainPresentationFrame(packet);
            }
            assertEquals(7_350,
                    capture.totalStereoFrames() - before);
            assertArrayEquals(
                    new int[] {735, 735, 735, 735, 735,
                            735, 735, 735, 735, 735},
                    packetSizes);
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
}
