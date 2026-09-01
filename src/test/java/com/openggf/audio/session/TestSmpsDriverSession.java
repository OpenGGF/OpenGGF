package com.openggf.audio.session;

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
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.data.Rom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        producer.present(0, PresentationMode.SILENT);
        assertTrue(session.captureSnapshot().speedShoesEnabled());
        assertEquals(8, session.captureSnapshot().speedMultiplier());
        assertTrue(registry.snapshot().speedShoesEnabled());
        assertEquals(8, registry.snapshot().speedMultiplier());

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
        assertTrue(registry.snapshot().speedShoesEnabled());
        assertEquals(8, registry.snapshot().speedMultiplier());

        renderBuffer.set(producer, validBuffer);
        producer.present(2, PresentationMode.FORWARD);

        assertEquals(0, commands.size());
        assertFalse(session.captureSnapshot().speedShoesEnabled());
        assertEquals(1, session.captureSnapshot().speedMultiplier());
        assertFalse(registry.snapshot().speedShoesEnabled());
        assertEquals(1, registry.snapshot().speedMultiplier());
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
        session.queueActivation(activation(70));
        session.serviceForward();
        session.applyCommand(new SmpsSessionCommand.SetSpeedMultiplier(8));
        session.applyCommand(new SmpsSessionCommand.ResetRingAlternation(false));
        session.applyCommand(new SmpsSessionCommand.AdmitSfx(
                continuousSfx(0xBC)));
        session.applyCommand(new SmpsSessionCommand.PushOverride(
                activation(71)));
        SmpsDriverSessionSnapshot beforeSession = session.captureSnapshot();
        SmpsDriverSnapshot beforeLogical = session.captureLogicalSnapshot();
        observer.clear();

        session.applyCommand(new SmpsSessionCommand.StopSmpsSfx());

        SmpsDriverSessionSnapshot afterSession = session.captureSnapshot();
        SmpsDriverSnapshot afterLogical = session.captureLogicalSnapshot();
        assertTrue(afterLogical.sequencers().stream().noneMatch(
                SmpsDriverSnapshot.SequencerEntry::sfx));
        assertEquals(beforeLogical.sequencers().stream()
                        .filter(entry -> !entry.sfx()).toList(),
                afterLogical.sequencers());
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
                "exact E4 slot-restoration writes remain a named frontier");
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
                        "test", 7, policy.identity(), physicalSettings));
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
        AudioTestFixtures.StubSmpsData data =
                new AudioTestFixtures.StubSmpsData("sfx-" + id);
        data.setId(id);
        SmpsSourceDescriptor source = new SmpsSourceDescriptor(
                SmpsSourceDescriptor.Kind.BASE_SFX_ID, id, null, null,
                data.getZ80StartAddress(), data.getData().length,
                Arrays.hashCode(data.getData()), false, 7);
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder().build();
        SmpsDriver detached = new SmpsDriver();
        SmpsSequencer sequencer = new SmpsSequencer(
                data, SmpsSessionTestFixtures.dac(), detached, detached,
                AudioManager.getInstance(), config, source,
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE);
        SmpsSequencerTestAccess.addActiveFmTrack(sequencer, 1);
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
        return activation(id, false, false, true);
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
            SmpsSequencerTestAccess.addActiveFmTrack(sequencer, 0);
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
