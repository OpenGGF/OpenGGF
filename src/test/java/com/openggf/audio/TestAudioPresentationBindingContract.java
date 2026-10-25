package com.openggf.audio;

import com.openggf.audio.driver.SmpsRequestAdmissionPolicy;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.session.SmpsDriverSession;
import com.openggf.audio.session.SmpsStatefulCommandPolicy;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.synth.NativeCompatiblePcmOutput;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.openggf.audio.AudioTestFixtures.EMPTY_DAC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;

/**
 * Physical-composition baseline on frozen SourcePublication commit 04a5a6c7
 * (AudioManager blob 5990cb9e), before any PresentationBinding extraction.
 * All stimuli use existing profile/backend/sink callbacks and real sessions.
 * Reflection only observes original Root/owner slots; it never injects state.
 * These ROM-free controls characterize publication, not native audio parity.
 */
class TestAudioPresentationBindingContract {
    private AudioManager audio;
    private Backend backend;
    private final List<Backend> backends = new ArrayList<>();
    private final List<AudioPresentationProducer> producers = new ArrayList<>();
    private final List<SmpsDriverSession> sessions = new ArrayList<>();
    private final List<LiveCaptureAudioHandle> leases = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SonicConfigurationService.getInstance().resetToDefaults();
        // Copying a constructed manager would copy owners enclosing the original.
        audio = spy(AudioManager.class);
        backend = newBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        for (Backend installed : backends) {
            installed.onProfile = ignored -> { };
            installed.onDonor = ignored -> { };
            installed.sink.onClose = () -> { };
            installed.sink.onRate = () -> { };
        }
        // A successful outer close intentionally orphans current slots. Always
        // release every captured replacement, even if an assertion failed first.
        List<Executable> cleanup = new ArrayList<>();
        cleanup.add(audio::destroy);
        for (LiveCaptureAudioHandle lease : leases) cleanup.add(lease::close);
        for (AudioPresentationProducer producer : producers) cleanup.add(producer::close);
        for (SmpsDriverSession session : sessions) cleanup.add(session::close);
        assertAll("release Root and all observed partial/reentrant resources", cleanup);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void coordinationPreparationRetainsItsOwnerAcrossPhysicalBackendReplacement(boolean basePublication) {
        Profile candidate = new Profile("candidate");
        CoordFlagHandler handler = mock(CoordFlagHandler.class);
        AtomicReference<SmpsCoordFlagHandlerOwner> prepared = new AtomicReference<>();
        AtomicReference<SmpsCoordFlagHandlerOwner> reentrant = new AtomicReference<>();
        AtomicReference<AudioPresentationProducer> replacement = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        List<String> order = new ArrayList<>();
        Backend next = newBackend();
        Object previousBase = base();
        candidate.onCoordination = owner -> {
            owner.register("candidate", ignored -> handler);
            if (callbacks.incrementAndGet() != 1) {
                order.add("realize.coordination");
                return;
            }
            order.add("prepare.enter");
            prepared.set(owner);
            assertNull(root("presentationCoordFlagHandlers"));
            assertSame(previousBase, base());
            next.onProfile = profile -> {
                if (profile == null) {
                    order.add("swap.backend.previous");
                } else {
                    assertSame(candidate, profile);
                    observeAttempt(order, owner, reentrant.get(), previousBase);
                }
            };
            next.onDonor = profile -> {
                assertSame(candidate, profile);
                observeAttempt(order, owner, reentrant.get(), previousBase);
            };
            audio.setBackend(next);
            assertEquals(60, audio.presentationFrameRate());
            replacement.set(rememberProducer());
            reentrant.set(root("presentationCoordFlagHandlers"));
            assertNotSame(owner, reentrant.get());
            assertSame(reentrant.get(), field(root("shadowFactory"), "coordFlagHandlers"));
            assertEquals(Set.of(), membership());
            order.add("prepare.realized");
            next.sink.onClose = () -> {
                order.add("replacement.close");
                assertSame(owner, root("presentationCoordFlagHandlers"),
                        "prepared owner publishes even though the physical binding changed");
                assertEquals(Set.of("candidate"), membership());
                assertSame(candidate, audio.getAudioProfile(), "logical publication precedes replacement");
            };
            order.add("prepare.exit");
        };

        if (basePublication) {
            audio.setAudioProfile(candidate);
        } else {
            audio.registerDonorLoader("candidate", candidate.loader, EMPTY_DAC, Profile.CONFIG, candidate);
        }

        assertSame(handler, prepared.get().handlerFor("candidate"));
        assertNotNull(replacement.get(), "the preparation callback must actually realize a producer");
        if (basePublication) {
            assertEquals(List.of("prepare.enter", "swap.backend.previous", "prepare.realized",
                    "prepare.exit", "attempt.candidate", "replacement.close", "realize.coordination"), order);
            assertTrue(replacement.get().isClosed());
            assertNotSame(replacement.get(), rememberProducer());
            assertNotSame(prepared.get(), root("presentationCoordFlagHandlers"));
            assertEquals(2, callbacks.get());
        } else {
            assertEquals(List.of("prepare.enter", "swap.backend.previous", "prepare.realized",
                    "prepare.exit", "attempt.candidate"), order);
            assertSame(replacement.get(), root("shadowProducer"));
            assertFalse(replacement.get().isClosed());
            assertSame(prepared.get(), root("presentationCoordFlagHandlers"));
            assertSame(reentrant.get(), field(root("shadowFactory"), "coordFlagHandlers"),
                    "publishing prepared membership does not retrofit the reentrant factory");
            assertSame(previousBase, base());
            assertEquals(1, callbacks.get());
        }
        assertEquals(Set.of("candidate"), membership());
        assertEquals(basePublication ? Arrays.asList(null, candidate) : Arrays.asList((GameAudioProfile) null),
                next.profiles);
        assertEquals(basePublication ? List.of() : List.of(candidate), next.donors);
    }

    private void observeAttempt(List<String> order, SmpsCoordFlagHandlerOwner prepared,
                                SmpsCoordFlagHandlerOwner reentrant, Object previousBase) {
        order.add("attempt.candidate");
        assertNotNull(reentrant, "backend attempt follows the completed physical replacement");
        assertSame(reentrant, root("presentationCoordFlagHandlers"));
        assertNotSame(prepared, reentrant);
        assertEquals(Set.of(), membership(), "prepared membership is not published early");
        assertSame(previousBase, base(), "backend attempt precedes logical source publication");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void existingCoordinationAttemptRetainsPreparedOwnerWhileMembershipFollowsReplacement(boolean basePublication) {
        Profile original = new Profile("original");
        Profile candidate = new Profile("candidate");
        audio.setAudioProfile(original);
        audio.presentationFrameRate();
        AudioPresentationProducer previousProducer = rememberProducer();
        SmpsCoordFlagHandlerOwner preparedOwner = root("presentationCoordFlagHandlers");
        Object previousBase = base();
        CoordFlagHandler handler = mock(CoordFlagHandler.class);
        AtomicReference<SmpsCoordFlagHandlerOwner> currentOwner = new AtomicReference<>();
        AtomicReference<AudioPresentationProducer> replacement = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        List<String> order = new ArrayList<>();
        Backend next = newBackend();
        next.onProfile = profile -> {
            if (profile == original) {
                order.add("swap.backend.previous");
            } else {
                assertSame(candidate, profile);
                observeExistingAttempt(order, preparedOwner, currentOwner.get(), previousBase);
            }
        };
        next.onDonor = profile -> {
            assertSame(candidate, profile);
            observeExistingAttempt(order, preparedOwner, currentOwner.get(), previousBase);
        };
        candidate.onCoordination = owner -> {
            owner.register("candidate", ignored -> handler);
            if (callbacks.incrementAndGet() != 1) {
                order.add("realize.coordination");
                return;
            }
            order.add("configure.enter");
            assertSame(preparedOwner, owner, "existing-owner application retains its prepared receiver");
            assertSame(previousProducer, root("shadowProducer"));
            assertFalse(previousProducer.isClosed());
            assertEquals(Set.of("original"), membership());
            audio.setBackend(next);
            assertTrue(previousProducer.isClosed(), "the actual configuration callback retires a real producer");
            assertEquals(1, backend.sink.closes);
            audio.presentationFrameRate();
            replacement.set(rememberProducer());
            currentOwner.set(root("presentationCoordFlagHandlers"));
            assertNotSame(preparedOwner, currentOwner.get());
            assertSame(currentOwner.get(), field(root("shadowFactory"), "coordFlagHandlers"));
            next.sink.onClose = () -> {
                order.add("replacement.close");
                assertSame(currentOwner.get(), root("presentationCoordFlagHandlers"));
                assertEquals(Set.of("original", "candidate"), membership());
                assertSame(candidate, audio.getAudioProfile());
            };
            order.add("configure.exit");
        };

        if (basePublication) audio.setAudioProfile(candidate);
        else audio.registerDonorLoader("candidate", candidate.loader, EMPTY_DAC, Profile.CONFIG, candidate);

        assertSame(handler, preparedOwner.handlerFor("candidate"));
        assertNotNull(replacement.get());
        if (basePublication) {
            assertEquals(List.of("configure.enter", "swap.backend.previous", "configure.exit",
                    "attempt.candidate", "replacement.close", "realize.coordination"), order);
            assertTrue(replacement.get().isClosed());
            assertNotSame(replacement.get(), rememberProducer());
            assertEquals(Set.of("candidate"), membership());
            assertEquals(2, callbacks.get());
        } else {
            assertEquals(List.of("configure.enter", "swap.backend.previous", "configure.exit", "attempt.candidate"), order);
            assertSame(previousBase, base());
            assertSame(replacement.get(), root("shadowProducer"));
            assertFalse(replacement.get().isClosed());
            assertSame(currentOwner.get(), root("presentationCoordFlagHandlers"));
            assertEquals(Set.of("original", "candidate"), membership(),
                    "existing-owner publication adds membership to the then-current set");
            IllegalArgumentException absent = assertThrows(IllegalArgumentException.class,
                    () -> currentOwner.get().handlerFor("candidate"));
            assertEquals("no coord-flag handler registered for candidate", absent.getMessage(),
                    "registration stayed on the prepared owner; publication did not replay configuration");
            assertEquals(1, callbacks.get());
        }
        assertEquals(basePublication ? List.of(original, candidate) : List.of(original), next.profiles);
        assertEquals(basePublication ? List.of() : List.of(candidate), next.donors);
    }

    private void observeExistingAttempt(List<String> order, SmpsCoordFlagHandlerOwner prepared,
                                       SmpsCoordFlagHandlerOwner current, Object previousBase) {
        order.add("attempt.candidate");
        assertNotNull(current);
        assertSame(current, root("presentationCoordFlagHandlers"));
        assertNotSame(prepared, current);
        assertEquals(Set.of("original"), membership());
        assertSame(previousBase, base());
    }

    @Test
    void constructionCapturesFactoryReceiverBeforePolicyButReadsLaterPhysicalSlotsLive() {
        Profile profile = new Profile("base");
        SmpsRequestAdmissionPolicy outerPolicy = context -> SmpsRequestAdmissionPolicy.PERMISSIVE.evaluate(context);
        SmpsRequestAdmissionPolicy nestedPolicy = context -> SmpsRequestAdmissionPolicy.PERMISSIVE.evaluate(context);
        AtomicReference<AudioPresentationSourceFactory> capturedFactory = new AtomicReference<>();
        AtomicReference<AudioPresentationSourceFactory> nestedFactory = new AtomicReference<>();
        AtomicReference<AudioPresentationProducer> nestedProducer = new AtomicReference<>();
        AtomicInteger policies = new AtomicInteger();
        List<String> order = new ArrayList<>();
        Backend next = newBackend();
        audio.setAudioProfile(profile);
        Object capturedBase = base();
        profile.onAdmission = () -> {
            if (policies.incrementAndGet() == 1) {
                order.add("outer.policy.enter");
                AudioPresentationSourceFactory factory = root("shadowFactory");
                capturedFactory.set(factory);
                sessions.add(field(factory, "smpsSession"));
                assertSame(SmpsRequestAdmissionPolicy.PERMISSIVE, field(factory, "sfxAdmissionPolicy"));
                assertNull(root("shadowProducer"));
                assertNull(root("shadowCommands"));
                audio.setBackend(next);
                assertEquals(60, audio.presentationFrameRate());
                nestedProducer.set(rememberProducer());
                nestedFactory.set(root("shadowFactory"));
                assertNotSame(factory, nestedFactory.get());
                assertSame(nestedPolicy, field(nestedFactory.get(), "sfxAdmissionPolicy"));
                next.sink.onRate = () -> {
                    order.add("later.sink.rate");
                    assertSame(nestedFactory.get(), root("shadowFactory"));
                    assertSame(outerPolicy, field(factory, "sfxAdmissionPolicy"),
                            "the original receiver receives the outer argument before later live reads");
                    assertNotNull(root("shadowCommands"));
                    assertNotNull(root("shadowRegistry"));
                    assertNotNull(root("shadowResolver"));
                };
                order.add("outer.policy.exit");
                return outerPolicy;
            }
            order.add("nested.policy");
            return nestedPolicy;
        };

        assertEquals(60, audio.presentationFrameRate());

        AudioPresentationProducer outerProducer = rememberProducer();
        assertEquals(2, policies.get());
        assertEquals(List.of("outer.policy.enter", "nested.policy", "outer.policy.exit",
                "later.sink.rate", "later.sink.rate"), order,
                "Root's live sink check precedes the producer's own compatible-sink validation");
        assertSame(capturedBase, base());
        assertSame(nestedFactory.get(), root("shadowFactory"));
        assertSame(outerPolicy, field(capturedFactory.get(), "sfxAdmissionPolicy"));
        assertSame(nestedPolicy, field(nestedFactory.get(), "sfxAdmissionPolicy"),
                "a fresh receiver lookup after evaluating the argument would corrupt this policy");
        assertNotSame(nestedProducer.get(), outerProducer);
        assertFalse(nestedProducer.get().isClosed(), "outer construction is not an atomic install/retire");
        assertSame(next.sink, field(outerProducer, "sink"));
        assertSame(next.sink, root("presentationSink"));
        assertSame(field(capturedFactory.get(), "smpsSession"), root("shadowSmpsSession"),
                "session local remains captured while factory/sink slots are read live");
        assertNotSame(field(nestedFactory.get(), "smpsSession"), root("shadowSmpsSession"));
        assertSame(root("shadowSmpsSession"), field(root("shadowRegistry"), "smpsSession"));
        assertSame(field(nestedFactory.get(), "coordFlagHandlers"),
                field(root("shadowRegistry"), "coordFlagHandlers"));
        assertEquals(1, backend.sink.closes);
        assertEquals(0, next.sink.closes);
    }

    @Test
    void restorePolicyFailureLeavesPartialFactoryThenProfileRefreshAndRomNoOpDiffer() {
        Profile original = new Profile("original");
        RuntimeException failure = new IllegalStateException("restore policy after factory publication");
        AtomicInteger restores = new AtomicInteger();
        AtomicReference<AudioPresentationSourceFactory> partial = new AtomicReference<>();
        original.onRestore = () -> {
            restores.incrementAndGet();
            partial.set(root("shadowFactory"));
            assertNotNull(partial.get(), "failure must occur after real factory publication");
            sessions.add(field(partial.get(), "smpsSession"));
            assertSame(root("presentationCoordFlagHandlers"), field(partial.get(), "coordFlagHandlers"));
            assertEquals(Set.of("original"), membership());
            throw failure;
        };
        audio.setAudioProfile(original);
        Object originalBase = base();

        assertSame(failure, assertThrows(IllegalStateException.class, audio::presentationFrameRate));

        assertEquals(1, restores.get());
        assertSame(originalBase, base());
        assertPartialFactory(partial.get());
        assertSame(SmpsRequestAdmissionPolicy.PERMISSIVE, field(partial.get(), "sfxAdmissionPolicy"));
        Profile refreshed = new Profile("refreshed");
        SmpsRequestAdmissionPolicy refreshedPolicy = context -> SmpsRequestAdmissionPolicy.PERMISSIVE.evaluate(context);
        AtomicInteger refreshes = new AtomicInteger();
        refreshed.onAdmission = () -> {
            refreshes.incrementAndGet();
            assertSame(refreshed, audio.getAudioProfile());
            assertSame(partial.get(), root("shadowFactory"));
            assertNull(root("shadowProducer"));
            return refreshedPolicy;
        };
        audio.setAudioProfile(refreshed);

        assertEquals(1, refreshes.get(), "profile publication refreshes an existing partial factory");
        assertSame(refreshedPolicy, field(partial.get(), "sfxAdmissionPolicy"));
        assertPartialFactory(partial.get());
        long profileGeneration = generation();
        Rom rom = new Rom();
        audio.setRom(rom);

        assertSame(rom, component(base(), "rom"));
        assertSame(refreshed.loader, component(base(), "loader"), "ROM preparation still publishes its logical dependencies");
        assertEquals(profileGeneration + 1, generation());
        assertEquals(1, refreshes.get(), "ROM publication must not evaluate admission or refresh the partial factory");
        assertSame(refreshedPolicy, field(partial.get(), "sfxAdmissionPolicy"));
        assertEquals(1, restores.get(), "neither publication retries construction");
        assertPartialFactory(partial.get());
    }

    private void assertPartialFactory(AudioPresentationSourceFactory factory) {
        assertNotNull(factory);
        assertSame(factory, root("shadowFactory"));
        assertNotNull(root("shadowTuning"));
        assertNotNull(root("presentationCoordFlagHandlers"));
        assertSame(backend.sink, root("presentationSink"));
        assertEquals(0, backend.sink.closes);
        for (String slot : List.of("shadowCommands", "shadowRegistry", "shadowResolver", "shadowRequestService",
                "shadowProducer", "shadowSmpsSession", "shadowParity", "standaloneTuning", "nativeCompatiblePcmCapture")) {
            assertNull(root(slot), slot);
        }
        assertEquals(0, (int) root("shadowFrameRate"));
        assertEquals(0, (int) root("standaloneFrameRate"));
        assertEquals(0, audio.nativeCompatiblePcmSessions());
        assertFalse((boolean) root("presentationSpeakerFailed"));
    }

    @Test
    void statefulPolicyFailureCountsNativeStageBeforeSessionPublicationAndRetryCountsAgain() {
        var configuration = GameServices.configuration();
        configuration.setSessionOverride(SonicConfiguration.AUDIO_FM_CORE, "accurate");
        try {
            Profile profile = new Profile("native-stage");
            RuntimeException failure = new IllegalStateException("stateful policy before session construction");
            AtomicInteger policies = new AtomicInteger();
            NativeCompatiblePcmOutput.Config config = NativeCompatiblePcmOutput.Config.withFilter(NativeCompatiblePcmOutput.Filter.NONE);
            audio.setAudioProfile(profile);
            audio.setNativeCompatiblePcmCapture(config);
            profile.onStateful = () -> {
                int invocation = policies.incrementAndGet();
                assertEquals(invocation, audio.nativeCompatiblePcmSessions(), "count is visible inside the actual policy callback");
                assertNotNull(root("shadowTuning"));
                assertNotNull(root("presentationCoordFlagHandlers"));
                assertNull(root("shadowFactory"));
                assertNull(root("shadowSmpsSession"));
                assertNull(root("shadowProducer"));
                if (invocation < 3) throw failure;
                return SmpsStatefulCommandPolicy.NONE;
            };

            for (int attempt = 1; attempt <= 2; attempt++) {
                assertSame(failure, assertThrows(IllegalStateException.class, audio::presentationFrameRate));
                assertEquals(attempt, policies.get());
                assertEquals(attempt, audio.nativeCompatiblePcmSessions());
                assertNull(root("shadowSmpsSession"));
                assertNull(root("shadowFactory"));
                assertNull(root("shadowProducer"));
                assertEquals(0, (int) root("shadowFrameRate"));
                assertSame(config, root("nativeCompatiblePcmCapture"));
            }
            assertEquals(60, audio.presentationFrameRate());
            rememberProducer();
            assertEquals(3, policies.get());
            assertEquals(3, audio.nativeCompatiblePcmSessions());
            assertNotNull(root("shadowSmpsSession"));
            assertThrows(IllegalStateException.class, () -> audio.setNativeCompatiblePcmCapture(config));
            audio.destroy();
            assertSame(config, root("nativeCompatiblePcmCapture"), "retirement retains native configuration");
            assertEquals(3, audio.nativeCompatiblePcmSessions(), "retirement does not reset the count");
        } finally {
            configuration.clearSessionOverrides();
        }
    }

    @Test
    void offOwnerBaseMutationPublishesLogicalSourceButTempoSnapshotPrecedesHeldCarryAndClose() throws Exception {
        Profile original = new Profile("original");
        Profile candidate = new Profile("candidate");
        audio.setAudioProfile(original);
        audio.setRom(new Rom());
        audio.beginCaptureMode(48_000, 60);
        LiveCaptureAudioHandle live = rememberLease(audio.beginLiveCaptureAudio(60));
        AudioPresentationProducer producer = rememberProducer();
        SmpsDriverSession session = root("shadowSmpsSession");
        AtomicInteger rendered = new AtomicInteger();
        audio.setScenePcmSource((target, frames) -> {
            rendered.incrementAndGet();
            for (int frame = 0; frame < frames; frame++) {
                target[frame * 2] = (short) (700 + frame);
                target[frame * 2 + 1] = (short) (-900 - frame);
            }
        });
        audio.setMusicTempoPercent(73);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(1, rendered.get(), "a real final PCM packet must reach the captures");
        assertEquals(73, session.captureSnapshot().musicTempoPercent());
        audio.beginReverseAudioPresentation();
        assertTrue(audio.preparePostBoundaryReverseRelease());
        assertTrue(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        assertTrue(AudioManagerTestDiagnostics.postBoundaryReverseTarget(audio));
        Object selected = audio.deferredReverseLogicalSnapshotForTesting();
        Object captures = root("captureLeases");
        LiveCaptureAudioHandle offline = rememberLease(field(captures, "offlineCaptureHandle"));
        Object liveCapture = field(live, "capture");
        short[] pending = ((short[]) field(liveCapture, "pending")).clone();
        assertEquals(700, pending[0]);
        assertEquals(-900, pending[1]);
        assertTrue((boolean) field(liveCapture, "fresh"));
        assertEquals(800, (int) field(liveCapture, "pendingStereoFrames"));
        Object liveClock = live.clockSnapshot();
        long liveTotal = live.totalStereoFrames();
        var before = producer.transactionFingerprint();
        assertEquals(2, before.captureCount());
        assertNotEquals(0L, before.preparedSelectedRestoreIdentity().token());
        assertFalse((boolean) field(captures, "liveCaptureAwaitingRebind"));
        long generation = generation();
        AtomicInteger backendAttempts = new AtomicInteger();
        backend.onProfile = profile -> {
            assertSame(candidate, profile);
            assertSame(original, audio.getAudioProfile(), "backend configuration precedes logical publication");
            backendAttempts.incrementAndGet();
        };
        AtomicReference<Throwable> observed = new AtomicReference<>();
        Thread worker = Thread.ofPlatform().name("binding-tempo-off-owner").daemon().start(() -> {
            try {
                audio.setAudioProfile(candidate);
            } catch (Throwable failure) {
                observed.set(failure);
            }
        });
        worker.join(5_000);
        assertFalse(worker.isAlive(), "bounded off-owner call must complete");

        Throwable failure = assertInstanceOf(IllegalStateException.class, observed.get());
        assertEquals("SMPS driver session accessed off its owner thread", failure.getMessage());
        assertTrue(Arrays.stream(failure.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(SmpsDriverSession.class.getName()) && frame.getMethodName().equals("captureSnapshot")),
                "the real tempo snapshot, rather than a producer/capture check, rejects the call");
        assertEquals(1, backendAttempts.get());
        assertSame(candidate, audio.getAudioProfile(), "failure occurs after logical publication, with no source rollback");
        assertSame(candidate.loader, component(base(), "loader"));
        assertSame(Profile.CONFIG, component(base(), "config"));
        assertEquals(generation + 1, generation());
        assertSame(producer, root("shadowProducer"));
        assertSame(session, root("shadowSmpsSession"));
        assertFalse(producer.isClosed());
        assertEquals(0, backend.sink.closes);
        assertTrue(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        assertTrue(AudioManagerTestDiagnostics.postBoundaryReverseTarget(audio));
        assertSame(selected, audio.deferredReverseLogicalSnapshotForTesting());
        assertSame(offline, field(captures, "offlineCaptureHandle"));
        assertSame(live, field(captures, "activeLiveCaptureAudioHandle"));
        assertSame(liveCapture, field(live, "capture"));
        assertFalse((boolean) field(captures, "liveCaptureAwaitingRebind"));
        assertFalse((boolean) field(live, "closed"));
        assertFalse((boolean) field(liveCapture, "closed"));
        assertArrayEquals(pending, field(liveCapture, "pending"));
        assertTrue((boolean) field(liveCapture, "fresh"));
        assertEquals(800, (int) field(liveCapture, "pendingStereoFrames"));
        assertEquals(liveClock, live.clockSnapshot());
        assertEquals(liveTotal, live.totalStereoFrames());
        var after = producer.transactionFingerprint();
        assertEquals(before.preparedSelectedRestoreIdentity().token(), after.preparedSelectedRestoreIdentity().token());
        assertEquals(before.captureCount(), after.captureCount());
        assertEquals(before.history(), after.history());
        assertEquals(before.clock(), after.clock());
        assertEquals(before.reverseCursor(), after.reverseCursor());
        assertEquals(73, session.captureSnapshot().musicTempoPercent(), "the original session remains usable by its owner");
        short[] livePacket = new short[1600];
        short[] offlinePacket = new short[1600];
        assertEquals(800, live.drainPresentationFrame(livePacket));
        assertEquals(800, audio.drainCaptureFrame(offlinePacket));
        assertArrayEquals(pending, livePacket);
        assertArrayEquals(livePacket, offlinePacket);
    }

    @Test
    void successfulReentrantCloseReconcilesLiveCurrentProducerThenUnconditionallyClearsCurrentSlots() {
        audio.beginCaptureMode(48_000, 60);
        LiveCaptureAudioHandle live = rememberLease(audio.beginLiveCaptureAudio(60));
        AudioPresentationProducer original = rememberProducer();
        Backend next = newBackend();
        AtomicReference<AudioPresentationProducer> replacement = new AtomicReference<>();
        AtomicReference<LiveCaptureAudioHandle> offline = new AtomicReference<>();
        AtomicReference<LiveCaptureAudioHandle> rebound = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        backend.sink.onClose = () -> {
            callbacks.incrementAndGet();
            assertTrue(original.isClosed(), "the callback runs after accepted close");
            assertFalse(Thread.holdsLock(audio), "destroy remains unlocked outside nested calls");
            audio.setBackend(next);
            audio.presentationFrameRate();
            replacement.set(rememberProducer());
            audio.beginCaptureMode(48_000, 60);
            Object captures = root("captureLeases");
            offline.set(rememberLease(field(captures, "offlineCaptureHandle")));
            rebound.set(rememberLease(field(live, "capture")));
            assertSame(live, field(captures, "activeLiveCaptureAudioHandle"));
            assertFalse((boolean) field(captures, "liveCaptureAwaitingRebind"));
            assertEquals(2, replacement.get().transactionFingerprint().captureCount());
            assertSame(next.sink, root("presentationSink"));
            assertNotNull(root("shadowFactory"));
            assertNotNull(root("shadowSmpsSession"));
            assertNotNull(root("presentationCoordFlagHandlers"));
        };

        try {
            audio.destroy(); // callback and outer reconciliation both return normally

            assertEquals(1, callbacks.get(), "the successful replacement must actually execute");
            assertTrue(original.isClosed());
            assertNotSame(original, replacement.get());
            assertFalse(replacement.get().isClosed(), "outer clearing does not close the replacement");
            for (String slot : List.of("presentationSink", "shadowProducer", "shadowFactory", "shadowCommands",
                    "shadowRegistry", "shadowResolver", "shadowSmpsSession", "shadowRequestService", "shadowParity",
                    "shadowTuning", "standaloneTuning", "presentationCoordFlagHandlers")) {
                assertNull(root(slot), "outer normal completion clears the CURRENT " + slot);
            }
            assertEquals(0, (int) root("shadowFrameRate"));
            assertEquals(Set.of(), membership());
            Object captures = root("captureLeases");
            assertSame(offline.get(), field(captures, "offlineCaptureHandle"),
                    "reconciliation inspected the open current producer, not the closed receiver");
            assertSame(live, field(captures, "activeLiveCaptureAudioHandle"));
            assertSame(rebound.get(), field(live, "capture"));
            assertFalse((boolean) field(live, "closed"));
            assertEquals(2, replacement.get().transactionFingerprint().captureCount());
            assertEquals(800, audio.drainCaptureFrame(new short[1600]));
            assertEquals(800, live.drainPresentationFrame(new short[1600]));
            assertEquals(0, next.sink.closes, "the replacement resources need explicit finally cleanup");
        } finally {
            // Root may have lost every current slot, or an assertion may have
            // interrupted its clearing. These references cover both outcomes.
            backend.sink.onClose = () -> { };
            List<Executable> cleanup = new ArrayList<>();
            if (offline.get() != null) cleanup.add(offline.get()::close);
            if (rebound.get() != null) cleanup.add(rebound.get()::close);
            if (replacement.get() != null) cleanup.add(replacement.get()::close);
            assertAll("release captured reentrant producer and leases in finally", cleanup);
        }
    }

    private Backend newBackend() {
        Backend created = new Backend();
        backends.add(created);
        return created;
    }

    private AudioPresentationProducer rememberProducer() {
        AudioPresentationProducer producer = root("shadowProducer");
        assertNotNull(producer);
        producers.add(producer);
        return producer;
    }

    private LiveCaptureAudioHandle rememberLease(LiveCaptureAudioHandle lease) {
        leases.add(lease);
        return lease;
    }

    private Set<String> membership() { return root("presentationCoordHandlerGameIds"); }
    private Object base() { return field(AudioManagerTestDiagnostics.sourcePublication(audio), "baseAudioSource"); }
    private long generation() { return (long) component(base(), "generation"); }

    @SuppressWarnings("unchecked")
    private <T> T root(String name) {
        try {
            Field slot = AudioManager.class.getDeclaredField(name);
            slot.setAccessible(true);
            return (T) slot.get(audio);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot observe Root." + name, failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            Field slot = owner.getClass().getDeclaredField(name);
            slot.setAccessible(true);
            return (T) slot.get(owner);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot observe " + owner.getClass().getName() + "." + name, failure);
        }
    }

    private static Object component(Object record, String name) {
        try {
            var accessor = record.getClass().getDeclaredMethod(name);
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class Profile extends AudioTestFixtures.StubAudioProfile {
        static final SmpsSequencerConfig CONFIG = new SmpsSequencerConfig.Builder().build();
        final AudioTestFixtures.StubSmpsLoader loader = new AudioTestFixtures.StubSmpsLoader();
        final String gameId;
        Consumer<SmpsCoordFlagHandlerOwner> onCoordination = ignored -> { };
        BooleanSupplier onRestore = () -> true;
        Supplier<SmpsRequestAdmissionPolicy> onAdmission = () -> SmpsRequestAdmissionPolicy.PERMISSIVE;
        Supplier<SmpsStatefulCommandPolicy> onStateful = () -> SmpsStatefulCommandPolicy.NONE;

        Profile(String gameId) { super(null); this.gameId = gameId; }
        @Override public String presentationGameId() { return gameId; }
        @Override public com.openggf.audio.smps.SmpsLoader createSmpsLoader(Rom rom) { return loader; }
        @Override public SmpsSequencerConfig getSequencerConfig() { return CONFIG; }
        @Override public void configurePresentationCoordFlagHandlers(SmpsCoordFlagHandlerOwner owner) { onCoordination.accept(owner); }
        @Override public boolean blocksSfxDuringMusicRestoreFadeIn() { return onRestore.getAsBoolean(); }
        @Override public SmpsRequestAdmissionPolicy getSfxAdmissionPolicy() { return onAdmission.get(); }
        @Override public SmpsStatefulCommandPolicy smpsStatefulCommandPolicy() { return onStateful.get(); }
    }

    private static final class Backend extends NullAudioBackend {
        final Sink sink = new Sink();
        final List<GameAudioProfile> profiles = new ArrayList<>();
        final List<GameAudioProfile> donors = new ArrayList<>();
        Consumer<GameAudioProfile> onProfile = ignored -> { };
        Consumer<GameAudioProfile> onDonor = ignored -> { };
        @Override public void setAudioProfile(GameAudioProfile profile) { profiles.add(profile); onProfile.accept(profile); }
        @Override public void registerAudioProfileCoordHandlers(GameAudioProfile profile) { donors.add(profile); onDonor.accept(profile); }
        @Override public AudioPresentationSink createPresentationSink(Consumer<Throwable> failure, Consumer<String> warning) { return sink; }
    }

    private static final class Sink implements AudioPresentationSink {
        Runnable onRate = () -> { };
        Runnable onClose = () -> { };
        int closes;
        @Override public int sampleRate() { onRate.run(); return 48_000; }
        @Override public void accept(AudioPresentationFrameView frame) { }
        @Override public void onReverseBoundary() { }
        @Override public void close() { closes++; onClose.run(); }
    }
}
