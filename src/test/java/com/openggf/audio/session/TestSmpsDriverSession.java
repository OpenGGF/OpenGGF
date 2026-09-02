package com.openggf.audio.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.ChannelType;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationCommandQueue;
import com.openggf.audio.presentation.AudioPresentationDependencyResolver;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcm;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsSequencerTestAccess;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsPhysicalPolicy;
import com.openggf.game.sonic3k.audio.Sonic3kStatefulCommandPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsDriverSession {
    @Test
    void onePhysicalAndLogicalIdentitySurviveAllTransitions() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());

        session.install();
        SmpsDriver identity = session.logicalDriverForTesting();
        Object physicalIdentity = session.physicalIdentityForTesting();
        session.queueActivation(activation(1));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.PushOverride(
                activation(2)));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.RestoreOverride());
        session.serviceForward();
        session.queueActivation(activation(3, true));
        session.serviceForward();
        SmpsDriverSessionSnapshot snapshot = session.captureSnapshot();
        SmpsDriverSnapshot logical = identity.captureSnapshot();
        SmpsDriverSession.PreparedRestore restore = session.prepareRestore(
                snapshot, logical, ignored ->
                        SmpsSessionTestFixtures.dac());
        session.commitRestore(restore);

        assertSame(identity, session.logicalDriverForTesting());
        assertSame(physicalIdentity, session.physicalIdentityForTesting());
    }

    @Test
    void logicalConstructionRecreationAndDiscardAreWriteFree() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);
        session.install();
        observer.clear();

        SmpsDriverSession.LiveMutationToken mutation =
                session.captureLiveMutation();
        PreparedSmpsMusicActivation prepared = activation(4);
        session.queueActivation(prepared);
        session.rollbackLiveMutation(mutation);

        assertTrue(observer.events().isEmpty());
        assertFalse(session.hasPendingActivation());
    }

    @Test
    void liveRollbackRestoresRetainedS3kE4PrerequisiteState() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        SmpsDriver driver = session.logicalDriverForTesting();
        SmpsSequencer sequencer = new SmpsSequencer(
                new AudioTestFixtures.StubSmpsData("fm3-special"),
                AudioTestFixtures.EMPTY_DAC, driver, AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().build());
        SmpsSequencer.Track fm3 = SmpsSequencerTestAccess.addActiveFmTrack(
                sequencer, 2);
        fm3.fm3SpecialMode = true;
        fm3.customSsgEgPresent = true;
        fm3.customSsgEgPayload[0] = 0x22;
        fm3.customSsgEgPayloadKnown = true;
        driver.addSequencer(sequencer, false);

        SmpsDriverSession.LiveMutationToken token = session.captureLiveMutation();
        fm3.fm3SpecialMode = false;
        fm3.customSsgEgPresent = false;
        fm3.customSsgEgPayload[0] = 0;
        fm3.customSsgEgPayloadKnown = false;
        session.rollbackLiveMutation(token);

        var restored = session.captureLogicalSnapshot().sequencers().getFirst()
                .snapshot().tracks().getFirst();
        assertTrue(restored.fm3SpecialMode());
        assertTrue(restored.customSsgEgPresent());
        assertArrayEquals(new int[] {0x22, 0, 0, 0}, restored.customSsgEgPayload());
        assertTrue(restored.customSsgEgPayloadKnown());
    }

    @Test
    void multipleLogicalOperationsRenderOnceAndPublishOnePacket() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        session.applyCommand(new SmpsSessionCommand.SetSpeedShoes(true));
        session.applyCommand(new SmpsSessionCommand.SetSpeedMultiplier(2));
        session.applyCommand(new SmpsSessionCommand.ResetRingAlternation(false));
        session.serviceForward();
        short[] pcm = new short[128];

        assertEquals(64, session.renderFrames(pcm, 0, 64));
        assertEquals(1, session.renderInvocationCountForTesting());
        assertEquals(64, session.renderedStereoFramesForTesting());
    }

    @Test
    void fadeCommandOpensScopedEpochForImmediateDacStop() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);
        session.install();
        session.queueActivation(activationWithDacTrack(10));
        observer.clear();

        session.applyCommand(new SmpsSessionCommand.FadeMusic(40, 3));

        assertFalse(session.captureLogicalSnapshot().sequencers().get(0)
                .snapshot().tracks().get(0).active());
    }

    @Test
    void explicitMusicStopSilencesOwnedPhysicalChannels() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);
        session.install();
        session.queueActivation(activationWithFmTrack(11));
        observer.clear();

        session.applyCommand(new SmpsSessionCommand.StopMusic());

        assertFalse(observer.events().isEmpty());
        assertTrue(session.captureLogicalSnapshot().sequencers().isEmpty());
        short[] stopped = new short[64];
        session.renderFrames(stopped, 0, 32);
        assertTrue(Arrays.equals(new short[64], stopped));
    }

    @Test
    void integralFastForwardServicesOnceAndRendersExactSourceFrames() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        session.queueActivation(activation(5));

        session.serviceForward();
        session.renderFrames(new short[2_940], 0, 1_470);

        assertEquals(1, session.serviceInvocationCountForTesting());
        assertEquals(1, session.renderInvocationCountForTesting());
        assertEquals(1_470, session.renderedStereoFramesForTesting());
    }

    @Test
    void fractionalFastForwardUsesSamePacketInterval() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        session.queueActivation(activation(6));
        int sourceFramesNeeded = 919;

        session.serviceForward();
        session.renderFrames(new short[sourceFramesNeeded * 2], 0,
                sourceFramesNeeded);

        assertEquals(1, session.serviceInvocationCountForTesting());
        assertEquals(1, session.renderInvocationCountForTesting());
        assertEquals(sourceFramesNeeded,
                session.renderedStereoFramesForTesting());
    }

    @Test
    void emptyLogicalStateRendersAndRewindsPhysicalTail() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        SmpsPhysicalPort port = session.openTestEpoch(
                SmpsSessionTestFixtures.owner(31));
        port.writeFm(0, 0x28, 0xF0);
        session.closeTestEpoch(port.epoch());
        SmpsDriverSessionSnapshot physical = session.captureSnapshot();
        SmpsDriverSnapshot logical = session.logicalDriverForTesting()
                .captureSnapshot();
        short[] expected = new short[64];
        session.renderFrames(expected, 0, 32);

        SmpsDriverSession.PreparedRestore restore = session.prepareRestore(
                physical, logical, ignored -> null);
        session.commitRestore(restore);
        short[] actual = new short[64];
        session.renderFrames(actual, 0, 32);

        assertTrue(logical.sequencers().isEmpty());
        assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    void silentAndReverseDoNotServiceOrRender() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory.Settings settings =
                new AudioPresentationSourceFactory.Settings(
                        44_100, SmpsSequencer.Region.NTSC,
                        false, false, false, false, 1,
                        () -> { }, new DecodedPcmCache(), ignored -> null);
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true, handlers, settings, session);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationProducer producer = new AudioPresentationProducer(
                44_100, 60, 44_100, 32, registry,
                new AudioPresentationCommandQueue(),
                new AudioPresentationMixer(735),
                new NoDeviceAudioSink(44_100), session);

        producer.present(0, PresentationMode.SILENT);
        producer.beginReverse(1.0);
        producer.present(1, PresentationMode.REVERSE);

        assertEquals(0, session.serviceInvocationCountForTesting());
        assertEquals(0, session.renderInvocationCountForTesting());
        producer.close();
    }

    @Test
    void pendingGlobalStopRestoresWithoutWritesThenStopsOnce() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);
        session.install();
        session.retainGlobalStop();
        SmpsDriverSessionSnapshot snapshot = session.captureSnapshot();
        SmpsDriverSnapshot logical = session.logicalDriverForTesting()
                .captureSnapshot();
        session.serviceForward();
        observer.clear();

        SmpsDriverSession.PreparedRestore restore = session.prepareRestore(
                snapshot, logical, ignored -> null);
        session.commitRestore(restore);
        assertTrue(observer.events().isEmpty());
        assertEquals(SmpsServiceOutcome.GLOBAL_STOP_CONSUMED,
                session.serviceForward());
        int firstStopWrites = observer.events().size();
        assertEquals(SmpsServiceOutcome.ORDINARY,
                session.serviceForward());

        assertEquals(202, firstStopWrites);
        assertEquals(firstStopWrites, observer.events().size());
    }

    static Stream<Arguments> shippedGlobalStopCommands() {
        return Stream.of(
                Arguments.of(0xE0,
                        new AudioPresentationCommand.RetainGlobalStop(0xE0)),
                Arguments.of(0xE2,
                        new AudioPresentationCommand.RetainGlobalStop(0xE2)),
                Arguments.of(0xFE,
                        new AudioPresentationCommand
                                .StopRawPcmAndRetainGlobalStop(0xFE)));
    }

    private static IntStream e4WriteFailureBoundaries() {
        // FM3 E4 with this test music: ten silence/key writes, then 27 restore
        // writes (mode, pan/algorithm, 20 operator params, four TL values).
        return IntStream.rangeClosed(1, 37);
    }

    @ParameterizedTest(name = "command {0}")
    @MethodSource("shippedGlobalStopCommands")
    void shippedGlobalStopResetsControlsAtomicallyAndNextMusicStartsNormal(
            int sourceCommandId,
            AudioPresentationCommand stopCommand) throws Exception {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        commands.submit(new AudioPresentationCommand.SetSpeedShoes(true),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);
        commands.submit(new AudioPresentationCommand.SetSpeedMultiplier(8),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);
        commands.submit(new AudioPresentationCommand.ResetRingAlternation(false),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);
        producer.present(0, PresentationMode.SILENT);
        handlers.state().setSpindashRevCounter(9);
        assertTrue(session.captureSnapshot().speedShoesEnabled());
        assertEquals(8, session.captureSnapshot().speedMultiplier());
        assertFalse(session.captureSnapshot().ringLeft());
        assertTrue(registry.snapshot().speedShoesEnabled());
        assertEquals(8, registry.snapshot().speedMultiplier());
        assertFalse(registry.snapshot().ringLeft());
        assertEquals(9, registry.snapshot().coordFlagRuntimeState()
                .spindashRevCounter());

        commands.submit(stopCommand, () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);
        Field renderBuffer = AudioPresentationProducer.class
                .getDeclaredField("smpsSourcePcm");
        renderBuffer.setAccessible(true);
        short[] validBuffer = (short[]) renderBuffer.get(producer);
        renderBuffer.set(producer, new short[0]);

        assertThrows(IllegalArgumentException.class,
                () -> producer.present(1, PresentationMode.FORWARD));
        assertEquals(1, commands.size(),
                "failed command " + sourceCommandId + " must remain queued");
        assertTrue(session.captureSnapshot().speedShoesEnabled());
        assertEquals(8, session.captureSnapshot().speedMultiplier());
        assertFalse(session.captureSnapshot().ringLeft());
        assertTrue(registry.snapshot().speedShoesEnabled());
        assertEquals(8, registry.snapshot().speedMultiplier());
        assertFalse(registry.snapshot().ringLeft());
        assertEquals(9, registry.snapshot().coordFlagRuntimeState()
                .spindashRevCounter());

        renderBuffer.set(producer, validBuffer);
        producer.present(2, PresentationMode.FORWARD);

        assertEquals(0, commands.size());
        assertFalse(session.captureSnapshot().speedShoesEnabled());
        assertEquals(1, session.captureSnapshot().speedMultiplier());
        assertTrue(session.captureSnapshot().ringLeft());
        assertFalse(registry.snapshot().speedShoesEnabled());
        assertEquals(1, registry.snapshot().speedMultiplier());
        assertTrue(registry.snapshot().ringLeft());
        assertEquals(0, registry.snapshot().coordFlagRuntimeState()
                .spindashRevCounter());
        session.queueActivation(activation(0x21));
        var nextMusic = session.captureLogicalSnapshot()
                .sequencers().getFirst().snapshot();
        assertFalse(nextMusic.speedShoes());
        assertEquals(1, nextMusic.speedMultiplier());
    }

    @Test
    void retainedStopOutranksAndCancelsEveryPendingActivation() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);
        session.install();
        observer.clear();
        session.queueActivation(activation(7));
        session.retainGlobalStop();

        assertEquals(SmpsServiceOutcome.GLOBAL_STOP_CONSUMED,
                session.serviceForward());
        assertEquals(202, observer.events().size());
        assertFalse(session.hasPendingActivation());
        assertTrue(session.logicalDriverForTesting()
                .captureSnapshot().sequencers().isEmpty());
    }

    @Test
    void stopSfxOnlyReleasesOwnershipButPreservesSessionGlobals() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);
        session.install();
        session.queueActivation(activationWithFmTrack(70));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.SetSpeedMultiplier(8));
        session.applyCommand(new SmpsSessionCommand.ResetRingAlternation(false));
        session.applyCommand(new SmpsSessionCommand.PushOverride(
                activationWithFmTrack(71)));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(
                continuousSfx(0xBC)));
        SmpsDriverSessionSnapshot beforeSession = session.captureSnapshot();
        SmpsDriverSnapshot beforeLogical = session.captureLogicalSnapshot();
        SmpsDriverSnapshot.SequencerEntry beforeMusic = beforeLogical
                .sequencers().stream().filter(entry -> !entry.sfx())
                .findFirst().orElseThrow();
        assertTrue(beforeMusic.snapshot().tracks().getFirst().overridden(),
                "the test must exercise active SFX ownership of a music track");
        observer.clear();

        session.applyCommand(new SmpsSessionCommand.StopSmpsSfx());

        SmpsDriverSessionSnapshot afterSession = session.captureSnapshot();
        SmpsDriverSnapshot afterLogical = session.captureLogicalSnapshot();
        assertTrue(afterLogical.sequencers().stream().noneMatch(
                SmpsDriverSnapshot.SequencerEntry::sfx));
        SmpsDriverSnapshot.SequencerEntry afterMusic = afterLogical
                .sequencers().stream().filter(entry -> !entry.sfx())
                .findFirst().orElseThrow();
        assertEquals(beforeMusic.source(), afterMusic.source());
        assertFalse(afterMusic.snapshot().tracks().getFirst().overridden(),
                "E4 must release SFX ownership from the surviving music track");
        assertTrue(Arrays.stream(afterLogical.fmLockSequencerIds())
                .allMatch(owner -> owner == -1));
        assertEquals(beforeLogical.continuousSfxId(),
                afterLogical.continuousSfxId());
        assertEquals(beforeLogical.continuousSfxFlag(),
                afterLogical.continuousSfxFlag());
        assertEquals(beforeLogical.contSfxLoopCnt(),
                afterLogical.contSfxLoopCnt());
        assertEquals(beforeLogical.savedOverrides(),
                afterLogical.savedOverrides());
        assertEquals(beforeLogical.pendingService(),
                afterLogical.pendingService());
        assertEquals(beforeSession.speedMultiplier(),
                afterSession.speedMultiplier());
        assertEquals(beforeSession.selectedDacSource(),
                afterSession.selectedDacSource());
        assertTrue(observer.events().isEmpty(),
                "the default host retains the legacy logical-only E4 boundary");
    }

    @Test
    void s3kE4CommitsItsNativeFm3ProgramBeforeReleasingLiveOwnership() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings = SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(settings, policy,
                observer, new SmpsSessionProfileFingerprint("s3k", 7,
                        policy.identity(), settings,
                        Sonic3kStatefulCommandPolicy.INSTANCE.identity()),
                new SmpsDriverSessionConfiguration(
                        Sonic3kStatefulCommandPolicy.INSTANCE));
        session.install();
        session.queueActivation(e4MusicFm3(70));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(e4SfxFm3(0xBC)));
        SmpsDriverSnapshot before = session.captureLogicalSnapshot();
        assertTrue(before.sequencers().stream().anyMatch(
                SmpsDriverSnapshot.SequencerEntry::sfx));
        AtomicInteger lifecycle = new AtomicInteger();
        session.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
            }

            @Override
            public void onServiceEnd(ServiceEvent event,
                    SmpsDriverSnapshot snapshot) {
            }

            @Override
            public void onLifecycle(LifecycleEvent event) {
                if (event.kind()
                        == SmpsDriverServiceObserver.LifecycleKind.STOP_ALL_SFX) {
                    lifecycle.incrementAndGet();
                }
            }
        });
        observer.clear();

        session.applyCommand(new SmpsSessionCommand.StopSmpsSfx());

        assertEquals(List.of(
                "YM:0:82:FF", "YM:0:86:FF", "YM:0:8A:FF", "YM:0:8E:FF",
                "YM:0:42:7F", "YM:0:46:7F", "YM:0:4A:7F", "YM:0:4E:7F",
                "YM:0:28:02", "YM:0:28:02", "YM:0:27:0F"),
                observer.events().subList(0, 11));
        SmpsDriverSnapshot after = session.captureLogicalSnapshot();
        assertTrue(after.sequencers().stream().noneMatch(
                SmpsDriverSnapshot.SequencerEntry::sfx));
        assertFalse(after.sequencers().stream().filter(entry -> !entry.sfx())
                .findFirst().orElseThrow().snapshot().tracks().getFirst()
                .overridden());
        assertEquals(before.continuousSfxId(), after.continuousSfxId());
        assertEquals(before.continuousSfxFlag(), after.continuousSfxFlag());
        assertEquals(before.contSfxLoopCnt(), after.contSfxLoopCnt());
        assertEquals(1, lifecycle.get(),
                "E4 publishes its one logical stop only after physical commit");
        session.close();
    }

    @Test
    void s3kE4RejectsAnActiveSfxOutsideItsSevenNativeSlotsWithoutMutation() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings = SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(settings, policy,
                observer, new SmpsSessionProfileFingerprint("s3k", 7,
                        policy.identity(), settings,
                        Sonic3kStatefulCommandPolicy.INSTANCE.identity()),
                new SmpsDriverSessionConfiguration(
                        Sonic3kStatefulCommandPolicy.INSTANCE));
        session.install();
        session.queueActivation(activationWithFmTrack(70));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(
                continuousSfx(0xBC)));
        SmpsDriverSnapshot before = session.captureLogicalSnapshot();
        observer.clear();
        AtomicInteger lifecycle = new AtomicInteger();
        session.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
            }

            @Override
            public void onServiceEnd(ServiceEvent event,
                    SmpsDriverSnapshot snapshot) {
            }

            @Override
            public void onLifecycle(LifecycleEvent event) {
                lifecycle.incrementAndGet();
            }
        });

        session.applyCommand(new SmpsSessionCommand.StopSmpsSfx());

        SmpsDriverSnapshot after = session.captureLogicalSnapshot();
        assertTrue(observer.events().isEmpty());
        assertEquals(before.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source).toList(),
                after.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source).toList());
        assertEquals(before.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::sfx).toList(),
                after.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::sfx).toList());
        assertArrayEquals(before.fmLockSequencerIds(),
                after.fmLockSequencerIds());
        assertTrue(after.sequencers().stream().anyMatch(
                SmpsDriverSnapshot.SequencerEntry::sfx));
        assertEquals(0, lifecycle.get());
        session.close();
    }

    @ParameterizedTest
    @MethodSource("e4WriteFailureBoundaries")
    void s3kE4EveryPhysicalWriteFailureRollsBackPhysicalLogicalAndObservers(
            int failureAt) {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings = SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(settings, policy,
                observer, new SmpsSessionProfileFingerprint("s3k", 7,
                        policy.identity(), settings,
                        Sonic3kStatefulCommandPolicy.INSTANCE.identity()),
                new SmpsDriverSessionConfiguration(
                        Sonic3kStatefulCommandPolicy.INSTANCE));
        session.install();
        session.queueActivation(e4MusicFm3(70));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(e4SfxFm3(0xBC)));
        SmpsDriverSessionSnapshot beforeSession = session.captureSnapshot();
        SmpsDriverSnapshot beforeLogical = session.captureLogicalSnapshot();
        AtomicInteger lifecycle = new AtomicInteger();
        session.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
            }

            @Override
            public void onServiceEnd(ServiceEvent event,
                    SmpsDriverSnapshot snapshot) {
            }

            @Override
            public void onLifecycle(LifecycleEvent event) {
                lifecycle.incrementAndGet();
            }
        });
        AtomicInteger writes = new AtomicInteger();
        session.setPhysicalWriteInterceptorForTesting(write -> {
            if (writes.incrementAndGet() == failureAt) {
                throw new IllegalStateException("injected E4 write failure");
            }
        });
        observer.clear();

        assertThrows(IllegalStateException.class,
                () -> session.applyCommand(new SmpsSessionCommand.StopSmpsSfx()));

        assertEquals(SmpsSessionTestFixtures.json(beforeSession),
                SmpsSessionTestFixtures.json(session.captureSnapshot()));
        SmpsDriverSnapshot afterLogical = session.captureLogicalSnapshot();
        assertEquals(beforeLogical.continuousSfxId(),
                afterLogical.continuousSfxId());
        assertEquals(beforeLogical.continuousSfxFlag(),
                afterLogical.continuousSfxFlag());
        assertEquals(beforeLogical.contSfxLoopCnt(),
                afterLogical.contSfxLoopCnt());
        assertEquals(beforeLogical.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source).toList(),
                afterLogical.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source).toList());
        assertEquals(beforeLogical.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::sfx).toList(),
                afterLogical.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::sfx).toList());
        assertArrayEquals(beforeLogical.fmLockSequencerIds(),
                afterLogical.fmLockSequencerIds());
        assertArrayEquals(beforeLogical.psgLockSequencerIds(),
                afterLogical.psgLockSequencerIds());
        assertEquals(beforeLogical.sequencers().getFirst().snapshot().tracks()
                        .getFirst().overridden(),
                afterLogical.sequencers().getFirst().snapshot().tracks()
                        .getFirst().overridden());
        assertEquals(beforeLogical.sequencers().get(1).snapshot().tracks()
                        .getFirst().active(),
                afterLogical.sequencers().get(1).snapshot().tracks()
                        .getFirst().active());
        assertTrue(observer.events().isEmpty());
        assertEquals(0, lifecycle.get());
        session.close();
    }

    @Test
    void s3kE4LogicalMutationFailureRollsBackTheCompletedPhysicalProgram() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings = SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(settings, policy,
                observer, new SmpsSessionProfileFingerprint("s3k", 7,
                        policy.identity(), settings,
                        Sonic3kStatefulCommandPolicy.INSTANCE.identity()),
                new SmpsDriverSessionConfiguration(
                        Sonic3kStatefulCommandPolicy.INSTANCE));
        session.install();
        session.queueActivation(e4MusicFm3(70));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(e4SfxFm3(0xBC)));
        SmpsDriverSessionSnapshot beforeSession = session.captureSnapshot();
        SmpsDriverSnapshot before = session.captureLogicalSnapshot();
        AtomicBoolean observedCompletedLogicalMutation = new AtomicBoolean();
        AtomicInteger lifecycle = new AtomicInteger();
        session.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
            }

            @Override
            public void onServiceEnd(ServiceEvent event,
                    SmpsDriverSnapshot snapshot) {
            }

            @Override
            public void onLifecycle(LifecycleEvent event) {
                lifecycle.incrementAndGet();
            }
        });
        session.setStatefulLogicalMutationInterceptorForTesting(() -> {
            SmpsDriverSnapshot afterLogicalMutation =
                    session.captureLogicalSnapshot();
            boolean sfxReleased = afterLogicalMutation.sequencers().stream()
                    .noneMatch(SmpsDriverSnapshot.SequencerEntry::sfx);
            boolean musicReleased = !afterLogicalMutation.sequencers().stream()
                    .filter(entry -> !entry.sfx()).findFirst().orElseThrow()
                    .snapshot().tracks().getFirst().overridden();
            if (!sfxReleased || !musicReleased) {
                throw new IllegalStateException(
                        "E4 failure seam ran before logical mutation");
            }
            observedCompletedLogicalMutation.set(true);
            throw new IllegalStateException("injected E4 logical failure");
        });
        observer.clear();

        assertThrows(IllegalStateException.class,
                () -> session.applyCommand(new SmpsSessionCommand.StopSmpsSfx()));

        SmpsDriverSnapshot after = session.captureLogicalSnapshot();
        assertTrue(observedCompletedLogicalMutation.get(),
                "the injected boundary must follow E4's logical mutation");
        assertEquals(SmpsSessionTestFixtures.json(beforeSession),
                SmpsSessionTestFixtures.json(session.captureSnapshot()));
        assertTrue(observer.events().isEmpty());
        assertEquals(0, lifecycle.get(),
                "lifecycle publication belongs after the transaction commit");
        assertEquals(before.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source).toList(),
                after.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source).toList());
        assertArrayEquals(before.fmLockSequencerIds(),
                after.fmLockSequencerIds());
        assertTrue(after.sequencers().stream().anyMatch(
                SmpsDriverSnapshot.SequencerEntry::sfx));
        session.close();
    }

    @Test
    void s3kE4PsgNoiseWriteFailureUsesThePhysicalPsgCapabilityAndRollsBack() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings = SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(settings, policy,
                observer, new SmpsSessionProfileFingerprint("s3k", 7,
                        policy.identity(), settings,
                        Sonic3kStatefulCommandPolicy.INSTANCE.identity()),
                new SmpsDriverSessionConfiguration(
                        Sonic3kStatefulCommandPolicy.INSTANCE));
        session.install();
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(
                e4SfxPsg3Noise(0xBC)));
        SmpsDriverSessionSnapshot beforeSession = session.captureSnapshot();
        AtomicInteger psgWrites = new AtomicInteger();
        session.setPhysicalWriteInterceptorForTesting(write -> {
            if (write instanceof SmpsChipWrite.Psg) {
                psgWrites.incrementAndGet();
                throw new IllegalStateException("injected E4 PSG failure");
            }
        });
        observer.clear();

        assertThrows(IllegalStateException.class,
                () -> session.applyCommand(new SmpsSessionCommand.StopSmpsSfx()));

        assertEquals(1, psgWrites.get(),
                "the failure must be raised by the first physical PSG write");
        assertEquals(SmpsSessionTestFixtures.json(beforeSession),
                SmpsSessionTestFixtures.json(session.captureSnapshot()));
        assertTrue(session.captureLogicalSnapshot().sequencers().stream()
                .anyMatch(SmpsDriverSnapshot.SequencerEntry::sfx));
        assertTrue(observer.events().isEmpty());
        session.close();
    }

    @Test
    void psgSilenceWritesExactRomProgramWithoutMutatingSessionState() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings =
                SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(
                settings, policy, observer,
                new SmpsSessionProfileFingerprint(
                        "s3k", 7, policy.identity(), settings),
                SmpsDriverSessionConfiguration.DEFAULT);
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        session.queueActivation(activationWithFmTrack(70));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.SetSpeedShoes(true));
        session.applyCommand(new SmpsSessionCommand.SetSpeedMultiplier(8));
        session.applyCommand(new SmpsSessionCommand.ResetRingAlternation(false));
        session.applyCommand(new SmpsSessionCommand.PushOverride(
                activationWithFmTrack(71)));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(
                continuousSfx(0xBC)));
        SmpsDriverSessionSnapshot beforeSession = session.captureSnapshot();
        SmpsDriverSnapshot beforeLogical = session.captureLogicalSnapshot();
        Object physicalIdentity = session.physicalIdentityForTesting();
        assertFalse(beforeSession.physical().outputSilenced(),
                "active SMPS output starts ungated");
        observer.clear();
        commands.submit(new AudioPresentationCommand.SilencePsg(0xE3),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);

        producer.present(0, PresentationMode.SILENT);

        assertEquals(List.of(
                "PSG:9F", "PSG:BF", "PSG:DF", "PSG:FF"),
                observer.events());
        assertSame(physicalIdentity, session.physicalIdentityForTesting());
        SmpsDriverSnapshot afterLogical = session.captureLogicalSnapshot();
        assertEquals(beforeLogical.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source)
                        .toList(),
                afterLogical.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source)
                        .toList());
        assertEquals(beforeLogical.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::sfx)
                        .toList(),
                afterLogical.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::sfx)
                        .toList());
        assertArrayEquals(beforeLogical.fmLockSequencerIds(),
                afterLogical.fmLockSequencerIds());
        assertArrayEquals(beforeLogical.psgLockSequencerIds(),
                afterLogical.psgLockSequencerIds());
        assertEquals(beforeLogical.savedOverrides().size(),
                afterLogical.savedOverrides().size());
        assertEquals(beforeLogical.savedOverrides().getFirst().logical()
                        .sequencers().getFirst().source(),
                afterLogical.savedOverrides().getFirst().logical()
                        .sequencers().getFirst().source());
        assertEquals(beforeLogical.pendingService(),
                afterLogical.pendingService());
        assertEquals(beforeLogical.sequencers().getFirst().snapshot()
                        .tracks().getFirst().overridden(),
                afterLogical.sequencers().getFirst().snapshot()
                        .tracks().getFirst().overridden());
        assertEquals(beforeLogical.sequencers().getFirst().snapshot()
                        .dividingTiming(),
                afterLogical.sequencers().getFirst().snapshot()
                        .dividingTiming());
        assertEquals(beforeLogical.sequencers().getFirst().snapshot()
                        .tempoAccumulator(),
                afterLogical.sequencers().getFirst().snapshot()
                        .tempoAccumulator());
        SmpsDriverSessionSnapshot afterSession = session.captureSnapshot();
        assertEquals(beforeSession.pendingGlobalCommand(),
                afterSession.pendingGlobalCommand());
        assertEquals(beforeSession.selectedDacSource(),
                afterSession.selectedDacSource());
        assertEquals(beforeSession.speedShoesEnabled(),
                afterSession.speedShoesEnabled());
        assertEquals(beforeSession.speedMultiplier(),
                afterSession.speedMultiplier());
        assertEquals(beforeSession.ringLeft(), afterSession.ringLeft());
        assertFalse(afterSession.physical().outputSilenced(),
                "transient E3 must not gate active SMPS output");

        observer.clear();
        producer.present(1, PresentationMode.FORWARD);
        assertTrue(observer.events().stream().noneMatch(
                        event -> event.startsWith("PSG:")),
                "the next service must not emit E3 a second time");
    }

    @Test
    void psgSilencePreservesTheInstalledIdleOutputGate() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings =
                SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(
                settings, policy, observer,
                new SmpsSessionProfileFingerprint(
                        "s3k", 7, policy.identity(), settings),
                SmpsDriverSessionConfiguration.DEFAULT);
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        SmpsDriverSessionSnapshot beforeSession = session.captureSnapshot();
        JsonNode beforeLogical = SmpsSessionTestFixtures.json(
                session.captureLogicalSnapshot());
        Object physicalIdentity = session.physicalIdentityForTesting();
        assertTrue(beforeSession.physical().outputSilenced(),
                "an installed idle session starts output-gated");
        observer.clear();
        commands.submit(new AudioPresentationCommand.SilencePsg(0xE3),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);

        producer.present(0, PresentationMode.SILENT);

        assertEquals(List.of(
                "PSG:9F", "PSG:BF", "PSG:DF", "PSG:FF"),
                observer.events());
        assertSame(physicalIdentity, session.physicalIdentityForTesting());
        SmpsDriverSessionSnapshot afterSession = session.captureSnapshot();
        assertTrue(afterSession.physical().outputSilenced(),
                "transient E3 must not wake an output-gated SMPS device");
        assertEquals(SmpsSessionTestFixtures.json(beforeSession),
                SmpsSessionTestFixtures.json(afterSession));
        assertEquals(beforeLogical, SmpsSessionTestFixtures.json(
                session.captureLogicalSnapshot()));
        short[] rendered = new short[128];
        session.renderFrames(rendered, 0, 64);
        assertArrayEquals(new short[128], rendered);
    }

    @Test
    void psgSilenceObserverFailureCannotPartiallyApplyOrReplay() {
        AtomicBoolean failFirstPsg = new AtomicBoolean();
        AtomicInteger psgCallbacks = new AtomicInteger();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings =
                SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(
                settings, policy, new com.openggf.audio.synth.ChipWriteObserver() {
                    @Override
                    public void onYm2612Write(
                            int port, int register, int value) {
                    }

                    @Override
                    public void onPsgWrite(int value) {
                        psgCallbacks.incrementAndGet();
                        if (failFirstPsg.getAndSet(false)) {
                            throw new IllegalStateException(
                                    "injected PSG observer failure");
                        }
                    }
                }, new SmpsSessionProfileFingerprint(
                        "s3k", 7, policy.identity(), settings),
                SmpsDriverSessionConfiguration.DEFAULT);
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        psgCallbacks.set(0);
        failFirstPsg.set(true);
        commands.submit(new AudioPresentationCommand.SilencePsg(0xE3),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);

        producer.present(0, PresentationMode.SILENT);

        assertEquals(0, commands.size(),
                "diagnostic failure follows the committed command");
        assertEquals(4, psgCallbacks.get(),
                "one failing observer callback cannot suppress later writes");
        assertArrayEquals(new int[] {15, 15, 15, 15},
                session.captureSnapshot().physical().synth().psg()
                        .attenuations());
        producer.present(1, PresentationMode.SILENT);
        assertEquals(4, psgCallbacks.get(),
                "the committed command must not replay");
    }

    @Test
    void commandDependencyActivationAndDriverFailuresRollbackOnce() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        SmpsDriver identity = session.logicalDriverForTesting();
        SmpsDriverSession.LiveMutationToken token =
                session.captureLiveMutation();
        session.queueActivation(activation(8));
        session.serviceForward();
        session.rollbackLiveMutation(token);

        assertSame(identity, session.logicalDriverForTesting());
        assertTrue(identity.captureSnapshot().sequencers().isEmpty());
        assertFalse(session.hasPendingActivation());
        assertThrows(IllegalStateException.class,
                () -> session.rollbackLiveMutation(token));
    }

    @Test
    void baseProfileReplacementClosesAndReinitializesSession() {
        SmpsSessionTestFixtures.RecordingObserver firstObserver =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession first = SmpsSessionTestFixtures.session(firstObserver);
        first.install();
        Object firstPhysical = first.physicalIdentityForTesting();
        first.close();

        SmpsSessionTestFixtures.RecordingObserver secondObserver =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession second = SmpsSessionTestFixtures.session(secondObserver);
        second.install();

        assertNotEquals(firstPhysical, second.physicalIdentityForTesting());
        assertEquals(202, firstObserver.events().size());
        assertEquals(202, secondObserver.events().size());
        assertThrows(IllegalStateException.class, first::installed);
    }

    @Test
    void donorChangePreservesSessionFingerprint() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        SmpsSessionProfileFingerprint fingerprint =
                session.captureSnapshot().profile();

        session.queueActivation(activation(9, true));
        session.serviceForward();

        assertSame(fingerprint, session.captureSnapshot().profile());
    }

    @Test
    void restoreOwnsControlStateUsedByTheNextActivation() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        session.applyCommand(new SmpsSessionCommand.SetSpeedShoes(true));
        session.applyCommand(new SmpsSessionCommand.SetSpeedMultiplier(4));
        SmpsDriverSessionSnapshot selectedSession =
                session.captureSnapshot();
        SmpsDriverSnapshot selectedLogical =
                session.captureLogicalSnapshot();

        session.applyCommand(new SmpsSessionCommand.SetSpeedShoes(false));
        session.applyCommand(new SmpsSessionCommand.SetSpeedMultiplier(1));
        session.commitRestore(session.prepareRestore(
                selectedSession, selectedLogical, ignored -> null));
        session.queueActivation(activation(10));

        var activated = session.captureLogicalSnapshot()
                .sequencers().getFirst().snapshot();
        assertTrue(activated.speedShoes(),
                "restored speed-shoes state must own later activations");
        assertEquals(4, activated.speedMultiplier(),
                "abandoned-timeline multiplier must not leak forward");
    }

    @Test
    void serviceFailureRetainsTheAppliedCommandBatchAtItsRetryPosition() {
        AtomicBoolean failActivation = new AtomicBoolean(true);
        SmpsPhysicalPolicy delegate =
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalPolicy policy = new SmpsPhysicalPolicy() {
            @Override
            public Identity identity() {
                return new Identity("test-fail-once-activation");
            }

            @Override
            public SmpsWriteProgram boot() {
                return delegate.boot();
            }

            @Override
            public SmpsWriteProgram stopAll() {
                return delegate.stopAll();
            }

            @Override
            public SmpsWriteProgram activateMusic(
                    SmpsMusicActivation activation) {
                if (failActivation.getAndSet(false)) {
                    throw new IllegalStateException(
                            "injected service failure");
                }
                return delegate.activateMusic(activation);
            }
        };
        SmpsPhysicalDevice.Settings physicalSettings =
                SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(
                physicalSettings, policy,
                new SmpsSessionTestFixtures.RecordingObserver(),
                new SmpsSessionProfileFingerprint(
                        "test", 7, policy.identity(), physicalSettings),
                SmpsDriverSessionConfiguration.DEFAULT);
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory.Settings settings =
                new AudioPresentationSourceFactory.Settings(
                        44_100, SmpsSequencer.Region.NTSC,
                        false, false, false, false, 1,
                        () -> { }, new DecodedPcmCache(), ignored -> null);
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true, handlers, settings, session);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = new AudioPresentationProducer(
                44_100, 60, 44_100, 32, registry, commands,
                new AudioPresentationMixer(735),
                new NoDeviceAudioSink(44_100), session);
        PreparedSmpsMusicActivation prepared = activation(12);
        AudioSourceDescriptor source = AudioSourceDescriptor.baseMusic(12);
        commands.submit(new AudioPresentationCommand.ReplaceMusic(
                        new AudioPresentationCommand.MusicVoiceEntry(
                                12, source,
                                new AudioPresentationCommand
                                        .SmpsVoiceDescriptor(
                                        12, 0, 12, source, 735,
                                        prepared))),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);

        assertThrows(IllegalStateException.class,
                () -> producer.present(0, PresentationMode.FORWARD));
        assertEquals(1, commands.size(),
                "a service failure must leave the applied batch queued");
        assertTrue(session.captureLogicalSnapshot().sequencers().isEmpty(),
                "the failed service transaction must roll back logical state");

        producer.present(0, PresentationMode.SILENT);

        assertEquals(0, commands.size());
        assertEquals(12, session.captureLogicalSnapshot()
                .sequencers().getFirst().source().id());
    }

    @Test
    void renderFailureRetainsTheAppliedCommandBatchAtItsRetryPosition()
            throws Exception {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        commands.submit(
                new AudioPresentationCommand.SetSpeedMultiplier(4),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);
        Field renderBuffer = AudioPresentationProducer.class
                .getDeclaredField("smpsSourcePcm");
        renderBuffer.setAccessible(true);
        short[] validBuffer = (short[]) renderBuffer.get(producer);
        renderBuffer.set(producer, new short[0]);

        assertThrows(IllegalArgumentException.class,
                () -> producer.present(0, PresentationMode.FORWARD));
        assertEquals(1, commands.size(),
                "render failure must leave the applied batch queued");
        assertEquals(1, session.captureSnapshot().speedMultiplier(),
                "render failure must roll session controls back");

        renderBuffer.set(producer, validBuffer);
        producer.present(1, PresentationMode.SILENT);

        assertEquals(0, commands.size());
        assertEquals(4, session.captureSnapshot().speedMultiplier());
    }

    @Test
    void failedForwardRequestDefersCommandPublicationAndRestoresItsMailbox()
            throws Exception {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        Sonic2SoundRequestService requests = new Sonic2SoundRequestService();
        List<Sonic2SoundRequestService.Event> observed = new ArrayList<>();
        requests.addObserver(observed::add);
        List<AudioCommand> published = new ArrayList<>();
        AudioPresentationProducer producer = new AudioPresentationProducer(
                44_100, 60, 44_100, 32, registry, commands,
                new AudioPresentationMixer(735),
                new NoDeviceAudioSink(44_100), session, requests,
                published::add);
        AudioCommand.PlaySfx ringRight = new AudioCommand.PlaySfx(
                0xB5, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null);
        requests.submitSound(0xB5, ringRight);
        Sonic2SoundRequestService.Snapshot before = requests.snapshot();
        Field renderBuffer = AudioPresentationProducer.class
                .getDeclaredField("smpsSourcePcm");
        renderBuffer.setAccessible(true);
        short[] validBuffer = (short[]) renderBuffer.get(producer);
        renderBuffer.set(producer, new short[0]);

        assertThrows(IllegalArgumentException.class,
                () -> producer.present(0, PresentationMode.FORWARD));

        assertEquals(List.of(), published,
                "a pre-seal failure must not publish a request consequence");
        assertEquals(List.of(), observed,
                "a pre-seal failure must not publish request diagnostics");
        assertEquals(before, requests.snapshot(),
                "a pre-seal failure must retain the source mailbox exactly");
        assertEquals(0, commands.size(),
                "a failed request must not leave a retry command in the durable queue");

        renderBuffer.set(producer, validBuffer);
        producer.present(1, PresentationMode.FORWARD);

        assertEquals(List.of(new AudioCommand.PlaySfx(
                0xCE, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null)),
                published, "the restored mailbox must publish its consequence once");
        assertEquals(4, observed.size(),
                "the retried ring request publishes submission, transfer, decision, and dispatch once");
    }

    @Test
    void compatibilityForwardConsumerFailurePrecedesTheSessionQueueSeal() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        commands.submit(new AudioPresentationCommand.SetSpeedMultiplier(4),
                () -> true, () -> { });
        Sonic2SoundRequestService requests = new Sonic2SoundRequestService();
        AudioCommand.PlaySfx ringRight = new AudioCommand.PlaySfx(
                0xB5, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null);
        requests.submitSound(0xB5, ringRight);
        Sonic2SoundRequestService.Snapshot before = requests.snapshot();
        AtomicInteger consumerCalls = new AtomicInteger();
        AudioPresentationProducer producer = new AudioPresentationProducer(
                44_100, 60, 44_100, 32, registry, commands,
                new AudioPresentationMixer(735),
                new NoDeviceAudioSink(44_100), session, requests, command -> {
                    consumerCalls.incrementAndGet();
                    throw new IllegalStateException(
                            "injected compatibility consumer failure");
                });

        assertThrows(IllegalStateException.class,
                () -> producer.present(0, PresentationMode.FORWARD));

        assertEquals(1, consumerCalls.get(),
                "the compatibility consumer must be attempted exactly once before seal");
        assertEquals(1, commands.size(),
                "a pre-seal compatibility failure retains the durable queue prefix");
        assertEquals(1, session.captureSnapshot().speedMultiplier(),
                "the session mutation must roll back when compatibility preparation fails");
        assertEquals(before, requests.snapshot(),
                "the source mailbox must remain retryable after compatibility preparation fails");
    }

    @Test
    void feTransactionRollsBackRawPcmAndRetainedStopTogether()
            throws Exception {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        DecodedPcm pcm = factory.registerUnsigned8Mono(
                "sega/transaction", new byte[] {0, 64, (byte) 0xFF},
                44_100);
        registry.apply(AudioPresentationCommand.ReplaceRawPcm.fromVoice(
                factory.segaPcm(90, pcm)));
        long rawVoiceId = registry.snapshot().rawPcmVoiceId();
        commands.submit(
                new AudioPresentationCommand
                        .StopRawPcmAndRetainGlobalStop(0xFE),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);
        Field renderBuffer = AudioPresentationProducer.class
                .getDeclaredField("smpsSourcePcm");
        renderBuffer.setAccessible(true);
        short[] validBuffer = (short[]) renderBuffer.get(producer);
        renderBuffer.set(producer, new short[0]);

        assertThrows(IllegalArgumentException.class,
                () -> producer.present(0, PresentationMode.FORWARD));
        assertEquals(1, commands.size(),
                "failed FE remains at the same retry position");
        assertEquals(rawVoiceId, registry.snapshot().rawPcmVoiceId(),
                "raw PCM removal must roll back with the driver stop");
        assertEquals(SmpsPendingGlobalCommand.NONE,
                session.captureSnapshot().pendingGlobalCommand(),
                "the retained stop must roll back with raw PCM");

        renderBuffer.set(producer, validBuffer);
        producer.present(1, PresentationMode.SILENT);

        assertEquals(0, commands.size());
        assertEquals(null, registry.snapshot().rawPcmVoiceId());
        assertEquals(SmpsPendingGlobalCommand.STOP_ALL,
                session.captureSnapshot().pendingGlobalCommand(),
                "silent FE keeps its physical stop for the next forward service");
    }

    @Test
    void registryCommitPreparationFailureRetainsTheBatchAndRollsBackSession() {
        AtomicBoolean failPreparation = new AtomicBoolean(true);
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioPresentationDependencyResolver resolver =
                resolverWithDiagnostics(factory, () -> {
                    if (failPreparation.getAndSet(false)) {
                        throw new IllegalStateException(
                                "injected registry prepare failure");
                    }
                }, () -> { });
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, resolver, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        commands.submit(
                new AudioPresentationCommand.SetSpeedMultiplier(4),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);

        assertThrows(IllegalStateException.class,
                () -> producer.present(0, PresentationMode.SILENT));
        assertEquals(1, commands.size());
        assertEquals(1, session.captureSnapshot().speedMultiplier(),
                "participant prepare failure must roll back session controls");

        producer.present(1, PresentationMode.SILENT);

        assertEquals(0, commands.size());
        assertEquals(4, session.captureSnapshot().speedMultiplier());
    }

    @Test
    void compositeRegistryPreparationFailureRollsBackS3kE4WithoutNestedSessionMutation() {
        AtomicBoolean failPreparation = new AtomicBoolean(true);
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsPhysicalPolicy policy = Sonic3kSmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings = SmpsSessionTestFixtures.settings();
        SmpsDriverSession session = new SmpsDriverSession(settings, policy,
                observer, new SmpsSessionProfileFingerprint("s3k", 7,
                        policy.identity(), settings,
                        Sonic3kStatefulCommandPolicy.INSTANCE.identity()),
                new SmpsDriverSessionConfiguration(
                        Sonic3kStatefulCommandPolicy.INSTANCE));
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(factory,
                resolverWithDiagnostics(factory, () -> {
                    if (failPreparation.getAndSet(false)) {
                        throw new IllegalStateException(
                                "injected registry preparation failure");
                    }
                }, () -> { }), handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(
                e4SfxPsg3Noise(0xBC)));
        SmpsDriverSessionSnapshot before = session.captureSnapshot();
        observer.clear();
        commands.submit(new AudioPresentationCommand.StopSmpsSfx(0xE4),
                () -> true, producer::applyPendingCommandsAtOwnerBoundary);

        assertThrows(IllegalStateException.class,
                () -> producer.present(0, PresentationMode.SILENT));

        assertEquals(1, commands.size(),
                "the failed composite must retain its E4 command for retry");
        assertEquals(SmpsSessionTestFixtures.json(before),
                SmpsSessionTestFixtures.json(session.captureSnapshot()));
        assertTrue(session.captureLogicalSnapshot().sequencers().stream()
                .anyMatch(SmpsDriverSnapshot.SequencerEntry::sfx));
        assertTrue(observer.events().isEmpty());

        producer.present(1, PresentationMode.SILENT);

        assertEquals(0, commands.size());
        assertTrue(session.captureLogicalSnapshot().sequencers().stream()
                .noneMatch(SmpsDriverSnapshot.SequencerEntry::sfx));
        session.close();
    }

    @Test
    void diagnosticPublicationFailureCannotRetainOrReplayCommittedBatch() {
        AtomicInteger diagnosticCommits = new AtomicInteger();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory = sessionFactory(
                session, handlers);
        AudioPresentationDependencyResolver resolver =
                resolverWithDiagnostics(factory, () -> { }, () -> {
                    diagnosticCommits.incrementAndGet();
                    throw new IllegalStateException(
                            "injected diagnostic publication failure");
                });
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, resolver, handlers, ignored -> { }, session);
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = sessionProducer(
                session, registry, commands);
        commands.submit(
                new AudioPresentationCommand.ToggleMute(
                        ChannelType.FM, 0),
                () -> true,
                producer::applyPendingCommandsAtOwnerBoundary);

        producer.present(0, PresentationMode.SILENT);

        assertEquals(0, commands.size(),
                "diagnostic publication follows the irreversible commit");
        assertTrue(session.captureSnapshot()
                .physical().synth().ym().mutes()[0]);
        assertEquals(1, diagnosticCommits.get());

        producer.present(1, PresentationMode.SILENT);

        assertTrue(session.captureSnapshot()
                .physical().synth().ym().mutes()[0],
                "the committed toggle must not replay after observer failure");
        assertEquals(2, diagnosticCommits.get(),
                "each presentation owns a new diagnostic transaction");
    }

    private static AudioPresentationSourceFactory sessionFactory(
            SmpsDriverSession session,
            SmpsCoordFlagHandlerOwner handlers) {
        AudioPresentationSourceFactory.Settings settings =
                new AudioPresentationSourceFactory.Settings(
                        44_100, SmpsSequencer.Region.NTSC,
                        false, false, false, false, 1,
                        () -> { }, new DecodedPcmCache(), ignored -> null);
        return new AudioPresentationSourceFactory(
                () -> true, handlers, settings, session);
    }

    private static AudioPresentationProducer sessionProducer(
            SmpsDriverSession session,
            AudioVoiceRegistry registry,
            AudioPresentationCommandQueue commands) {
        return new AudioPresentationProducer(
                44_100, 60, 44_100, 32, registry, commands,
                new AudioPresentationMixer(735),
                new NoDeviceAudioSink(44_100), session);
    }

    private static AudioPresentationDependencyResolver
            resolverWithDiagnostics(
            AudioPresentationSourceFactory factory,
            Runnable prepare,
            Runnable commit) {
        return new AudioPresentationDependencyResolver() {
            @Override
            public DiagnosticTransaction beginDiagnosticTransaction() {
                return new DiagnosticTransaction() {
                    @Override
                    public void endPreparation() {
                        prepare.run();
                    }

                    @Override
                    public void commit() {
                        commit.run();
                    }

                    @Override
                    public void discard() {
                    }
                };
            }

            @Override
            public DecodedPcm resolvePcm(String assetId) {
                return factory.resolvePcm(assetId);
            }

            @Override
            public com.openggf.audio.smps.DacData resolveDac(
                    SmpsSourceDescriptor source) {
                return factory.resolveDac(source);
            }

        };
    }

    private static PreparedSmpsMusicActivation activation(int id) {
        return activation(id, false);
    }

    private static PreparedSmpsSfxProgram continuousSfx(int id) {
        return continuousSfx(id, 0);
    }

    private static PreparedSmpsSfxProgram e4SfxFm3(int id) {
        return continuousSfx(id, 2);
    }

    private static PreparedSmpsSfxProgram e4SfxPsg3Noise(int id) {
        AudioTestFixtures.StubSmpsData data =
                new AudioTestFixtures.StubSmpsData("psg-sfx-" + id);
        data.setId(id);
        SmpsSourceDescriptor source = new SmpsSourceDescriptor(
                SmpsSourceDescriptor.Kind.BASE_SFX_ID, id, null, null,
                data.getZ80StartAddress(), data.getData().length,
                Arrays.hashCode(data.getData()), false, 7);
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .sfxChannelOwnershipMode(
                        SmpsSequencerConfig.SfxChannelOwnershipMode.ADMISSION)
                .build();
        SmpsDriver detached = new SmpsDriver();
        SmpsSequencer sequencer = new SmpsSequencer(
                data, SmpsSessionTestFixtures.dac(), detached, detached,
                AudioManager.getInstance(), config, source,
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE);
        SmpsSequencer.Track psg = SmpsSequencerTestAccess.addActivePsgTrack(
                sequencer, 2);
        psg.noiseMode = true;
        psg.psgNoiseParam = 7;
        psg.rawPsgNoise = 0xE7;
        psg.rawPsgNoiseKnown = true;
        sequencer.setIsSfx(true);
        return new PreparedSmpsSfxProgram(
                new SmpsDriverSnapshot.SequencerEntry(
                        true, source,
                        SmpsSequencer.SourceDescriptorTrust
                                .PRECOMPUTED_IMMUTABLE,
                        null, data, SmpsSessionTestFixtures.dac(),
                        AudioManager.getInstance(), config,
                        sequencer.captureSnapshot()),
                id, 1);
    }

    private static PreparedSmpsSfxProgram continuousSfx(int id, int channel) {
        AudioTestFixtures.StubSmpsData data =
                new AudioTestFixtures.StubSmpsData("sfx-" + id);
        data.setId(id);
        SmpsSourceDescriptor source = new SmpsSourceDescriptor(
                SmpsSourceDescriptor.Kind.BASE_SFX_ID, id, null, null,
                data.getZ80StartAddress(), data.getData().length,
                Arrays.hashCode(data.getData()), false, 7);
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .sfxChannelOwnershipMode(
                        SmpsSequencerConfig.SfxChannelOwnershipMode.ADMISSION)
                .build();
        SmpsDriver detached = new SmpsDriver();
        SmpsSequencer sequencer = new SmpsSequencer(
                data, SmpsSessionTestFixtures.dac(), detached, detached,
                AudioManager.getInstance(), config, source,
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE);
        SmpsSequencerTestAccess.addActiveFmTrack(sequencer, channel);
        sequencer.setIsSfx(true);
        return new PreparedSmpsSfxProgram(
                new SmpsDriverSnapshot.SequencerEntry(
                        true, source,
                        SmpsSequencer.SourceDescriptorTrust
                                .PRECOMPUTED_IMMUTABLE,
                        null, data, SmpsSessionTestFixtures.dac(),
                        AudioManager.getInstance(), config,
                        sequencer.captureSnapshot()),
                id, 1);
    }

    private static PreparedSmpsMusicActivation activationWithDacTrack(
            int id) {
        return activation(id, false, true);
    }

    private static PreparedSmpsMusicActivation activationWithFmTrack(
            int id) {
        return activationWithFmTrack(id, 0);
    }

    private static PreparedSmpsMusicActivation e4MusicFm3(int id) {
        return activationWithFmTrack(id, 2);
    }

    private static PreparedSmpsMusicActivation activationWithFmTrack(
            int id, int channel) {
        return activation(id, false, false, true, channel);
    }

    private static PreparedSmpsMusicActivation activation(
            int id, boolean donor) {
        return activation(id, donor, false);
    }

    private static PreparedSmpsMusicActivation activation(
            int id, boolean donor, boolean withDacTrack) {
        return activation(id, donor, withDacTrack, false);
    }

    private static PreparedSmpsMusicActivation activation(
            int id, boolean donor, boolean withDacTrack,
            boolean withFmTrack) {
        return activation(id, donor, withDacTrack, withFmTrack, 0);
    }

    private static PreparedSmpsMusicActivation activation(
            int id, boolean donor, boolean withDacTrack,
            boolean withFmTrack, int fmChannel) {
        AudioTestFixtures.StubSmpsData data =
                new AudioTestFixtures.StubSmpsData("music-" + id);
        data.setId(id);
        SmpsSourceDescriptor source = new SmpsSourceDescriptor(
                donor ? SmpsSourceDescriptor.Kind.DONOR_MUSIC
                        : SmpsSourceDescriptor.Kind.BASE_MUSIC,
                id, null, donor ? "donor" : null,
                data.getZ80StartAddress(), data.getData().length,
                Arrays.hashCode(data.getData()), false, 7);
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder().build();
        SmpsDriver detached = new SmpsDriver();
        SmpsSequencer sequencer = new SmpsSequencer(
                data, SmpsSessionTestFixtures.dac(), detached, detached,
                AudioManager.getInstance(), config, source,
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE);
        if (withDacTrack) {
            SmpsSequencerTestAccess.addActiveDacTrack(sequencer);
        }
        if (withFmTrack) {
            SmpsSequencer.Track fm = SmpsSequencerTestAccess.addActiveFmTrack(
                    sequencer, fmChannel);
            fm.voiceData = new byte[25];
            for (int index = 0; index < fm.voiceData.length; index++) {
                fm.voiceData[index] = (byte) index;
            }
            fm.pan = 0xC0;
        }
        SmpsDriverSnapshot.SequencerEntry entry =
                new SmpsDriverSnapshot.SequencerEntry(
                        false, source,
                        SmpsSequencer.SourceDescriptorTrust
                                .PRECOMPUTED_IMMUTABLE,
                        null, data, SmpsSessionTestFixtures.dac(),
                        AudioManager.getInstance(), config,
                        sequencer.captureSnapshot());
        SmpsLogicalTransitionPolicy transition =
                new SmpsLogicalTransitionPolicy() {
                    @Override
                    public Result prepareMusicStart(
                            SmpsDriverSnapshot current,
                            SmpsDriverSnapshot.SequencerEntry incoming) {
                        return replacement(current, incoming);
                    }

                    @Override
                    public Result prepareOverrideRestore(
                            SmpsDriverSnapshot current,
                            SmpsDriverSnapshot saved) {
                        return new Result(saved, SmpsWriteProgram.EMPTY);
                    }

                    private Result replacement(
                            SmpsDriverSnapshot current,
                            SmpsDriverSnapshot.SequencerEntry incoming) {
                        return new Result(
                                new SmpsDriverSnapshot(
                                        current.region(), current.readMode(),
                                        0, false, 0,
                                        current.palUpdateCounter(),
                                        List.of(incoming),
                                        new int[] {-1, -1, -1, -1, -1, -1},
                                        new int[] {-1, -1, -1, -1}),
                                SmpsWriteProgram.EMPTY);
                    }
                };
        return new PreparedSmpsMusicActivation(
                new SmpsMusicActivation(source, 0), entry, transition,
                new SmpsDacSelection(source,
                        SmpsSessionTestFixtures.dac()));
    }

    @Test
    void composingCapturingAndClosingInertSessionEmitNoWrites() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);

        assertTrue(observer.events().isEmpty());
        assertFalse(session.installed());
        assertFalse(session.captureSnapshot().initialized());
        assertTrue(observer.events().isEmpty());

        session.close();
        assertTrue(observer.events().isEmpty());
        assertThrows(IllegalStateException.class, session::captureSnapshot);
    }

    @Test
    void legacyCompatibilityPolicyMatchesCurrentDefaultBehaviorAndIdentity() {
        SmpsPhysicalPolicy first =
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalPolicy second =
                new LegacyCompatibilitySmpsPhysicalPolicy();
        GameAudioProfile profile = new MinimalProfile();
        SmpsSessionTestFixtures.RecordingObserver legacyObserver =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsSessionTestFixtures.RecordingObserver policyObserver =
                new SmpsSessionTestFixtures.RecordingObserver();

        new VirtualSynthesizer(44_100, legacyObserver);
        SmpsPhysicalDevice device = new SmpsPhysicalDevice(
                SmpsSessionTestFixtures.settings(), policyObserver);
        device.apply(first.boot());

        assertSame(first, profile.smpsPhysicalPolicy());
        assertEquals(first.identity(), second.identity());
        assertInstanceOf(SmpsPhysicalPolicy.Identity.class,
                first.identity());
        assertEquals(legacyObserver.events(), policyObserver.events());
        assertEquals(first.boot(), first.stopAll());
        assertEquals(List.of(new SmpsChipWrite.Ym2612(
                        0, 0x2B, 0x80)),
                first.activateMusic(new SmpsMusicActivation(
                        SmpsSessionTestFixtures.source(6), 1)).writes());
    }

    @Test
    void sessionLiveMutationTokensAreSingleUseAndSessionBound() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsDriverSession other = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        var before = SmpsSessionTestFixtures.json(session.captureSnapshot());
        SmpsDriverSession.LiveMutationToken token =
                session.captureLiveMutation();

        session.applyChannelMasks(0x15, 0x05);
        session.rollbackLiveMutation(token);

        assertEquals(before, SmpsSessionTestFixtures.json(
                session.captureSnapshot()));
        assertThrows(IllegalStateException.class,
                () -> session.rollbackLiveMutation(token));

        SmpsDriverSession.LiveMutationToken crossSession =
                session.captureLiveMutation();
        assertThrows(IllegalArgumentException.class,
                () -> other.commitLiveMutation(crossSession));
        session.commitLiveMutation(crossSession);
        assertThrows(IllegalStateException.class,
                () -> session.commitLiveMutation(crossSession));
    }

    @Test
    void presentationCompositionInstallsOnePersistentSessionAndClosesItWriteFree() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory.Settings settings =
                new AudioPresentationSourceFactory.Settings(
                        44_100, com.openggf.audio.smps.SmpsSequencer.Region.NTSC,
                        false, false, false, false, 1,
                        () -> { }, new DecodedPcmCache(), ignored -> null);
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true, handlers, settings, session);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationProducer producer = new AudioPresentationProducer(
                44_100, 60, 44_100, 32, registry,
                new AudioPresentationCommandQueue(),
                new AudioPresentationMixer(735),
                new NoDeviceAudioSink(44_100), session);

        assertFalse(observer.events().isEmpty());
        assertTrue(session.installed());
        int writesAfterInstall = observer.events().size();

        producer.close();

        assertEquals(writesAfterInstall, observer.events().size());
        assertThrows(IllegalStateException.class, session::installed);
    }

    private static final class MinimalProfile implements GameAudioProfile {
        @Override
        public SmpsLoader createSmpsLoader(Rom rom) {
            return null;
        }

        @Override
        public SmpsSequencerConfig getSequencerConfig() {
            return null;
        }

        @Override
        public int getSpeedShoesOnCommandId() {
            return 0;
        }

        @Override
        public int getSpeedShoesOffCommandId() {
            return 0;
        }

        @Override
        public int getInvincibilityMusicId() {
            return 0;
        }

        @Override
        public int getExtraLifeMusicId() {
            return 0;
        }

        @Override
        public int getDrowningMusicId() {
            return 0;
        }

        @Override
        public Map<com.openggf.audio.GameSound, Integer> getSoundMap() {
            return Map.of();
        }
    }
}
