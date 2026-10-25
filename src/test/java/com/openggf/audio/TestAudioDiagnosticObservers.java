package com.openggf.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.any;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsDriverTestAccess;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.driver.SmpsDriverServiceObserver.LifecycleEvent;
import com.openggf.audio.driver.SmpsDriverServiceObserver.LifecycleKind;
import com.openggf.audio.driver.SmpsDriverServiceObserver.LifecycleScope;
import com.openggf.audio.driver.SmpsDriverServiceObserver.ServiceKind;
import com.openggf.audio.driver.SmpsDriverServiceObserver.ServiceEvent;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.AdmissionResult;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.RejectionReason;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.SmpsAdmissionContext;
import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioPresentationSessionCommandApplier;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcm;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SampleBackedVoice;
import com.openggf.audio.presentation.ResolvedSmpsSfxSource;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.ScenePcmSource;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.presentation.SmpsAssetKey;
import com.openggf.audio.presentation.SmpsSfxInstantiation;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.audio.session.SmpsDriverSession;
import com.openggf.audio.session.SmpsSessionTestSupport;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.mockito.MockedConstruction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestAudioDiagnosticObservers {
    private static final ObjectMapper JSON = new ObjectMapper();

    private AudioManager audio;
    private AudioTestFixtures.StubSmpsLoader loader;

    @BeforeEach
    void setUp() {
        SonicConfigurationService.getInstance().resetToDefaults();
        audio = AudioManager.getInstance();
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        loader = new AudioTestFixtures.StubSmpsLoader();
        loader.musicResults.put(0x81, data("base", 0x81));
        loader.musicResults.put(0x82, data("override", 0x82));
        loader.sfxResults.put(0xA0, data("accepted", 0xA0));
        loader.sfxResults.put(0xA1, data("rejected", 0xA1));
    }

    @AfterEach
    void tearDown() {
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void preConstructionObserversReachFirstOverrideAndRestoredDriversInOrder() {
        loader.musicResults.put(0x81, new LongRunningMusicData(0x81));
        loader.musicResults.put(0x82, new LongRunningMusicData(0x82));
        List<String> events = new ArrayList<>();
        List<Long> begins = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        List<LifecycleEvent> lifecycle =
                new ArrayList<>();
        List<String> chipWrites = new ArrayList<>();

        audio.setAdmissionObserver(decision -> events.add(
                "decision:" + decision.result().resolvedSoundId() + ":"
                        + decision.result().accepted()));
        audio.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onSfxAdmitted(Admission admission) {
                events.add("sfx:" + admission.source().descriptor().id());
            }
        });
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                chipWrites.add("YM:%d:%02X:%02X".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
                chipWrites.add("PSG:%02X".formatted(value));
            }
        });
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                if (event.kind() == ServiceKind.SEQUENCER_TICK) {
                    begins.add(event.ordinal());
                }
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                assertNotNull(snapshot);
                if (event.kind() == ServiceKind.SEQUENCER_TICK) {
                    ends.add(event.ordinal());
                }
            }

            @Override
            public void onLifecycle(LifecycleEvent event) {
                lifecycle.add(event);
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));

        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        List<String> firstDriverWrites = expectedConstructorSilenceWrites();
        assertEquals(firstDriverWrites, chipWrites,
                "pre-construction installation must expose constructor"
                        + " silence without leaking blueprint or prepared"
                        + " sequencer writes");
        SmpsDriverSnapshot base = activeDriverSnapshot();
        assertEquals(List.of(0x81), sourceIds(base));

        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(List.of("decision:160:true", "sfx:160"), events,
                "registry admission and session contention publish in their"
                        + " committed participant order");

        audio.playMusic(0x82);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(List.of(0x82), sourceIds(activeDriverSnapshot()));
        assertEquals(firstDriverWrites, chipWrites,
                "music replacement reuses the persistent device without a"
                        + " second constructor burst");
        audio.presentFrame(PresentationMode.FORWARD);

        audio.restoreMusic();
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(List.of(0x81, 0xA0),
                sourceIds(activeDriverSnapshot()),
                "restore must reinstate saved logical state in the same"
                        + " persistent driver");
        audio.presentFrame(PresentationMode.FORWARD);

        assertFalse(begins.isEmpty());
        assertEquals(begins, ends);
        assertEquals(1, lifecycle.stream().filter(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.DRIVER_CREATED)
                .map(LifecycleEvent::driver).distinct().count());
        assertTrue(lifecycle.stream().anyMatch(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.SAVE));
        assertTrue(lifecycle.stream().anyMatch(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.RESTORE));
    }

    @Test
    void policyRunsOnceAtTheActualRequestBoundaryBeforeRejectedConstruction() {
        List<SmpsAdmissionContext> contexts = new ArrayList<>();
        SmpsRequestAdmissionPolicy rejecting = context -> {
            contexts.add(context);
            if (context.resolvedSoundId() == 0xA1) {
                return new AdmissionResult(false, RejectionReason.PRIORITY,
                        0x21, 0x21, 0xE1);
            }
            return new AdmissionResult(true, RejectionReason.NONE,
                    0x20, 0x61, 0xE0);
        };
        List<AdmissionResult> decisions = new ArrayList<>();
        List<Integer> contentionAdmissions = new ArrayList<>();
        audio.setAdmissionObserver(decision -> decisions.add(decision.result()));
        audio.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onSfxAdmitted(Admission admission) {
                contentionAdmissions.add(admission.source().descriptor().id());
            }
        });
        audio.setAudioProfile(profile(rejecting));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);

        audio.playSfx(0xA0);
        audio.playSfx(0xA1);
        audio.presentFrame(PresentationMode.SILENT);

        assertEquals(List.of(0xA0, 0xA1), contexts.stream()
                .map(SmpsAdmissionContext::resolvedSoundId).toList());
        assertEquals(List.of(true, false), decisions.stream()
                .map(AdmissionResult::accepted).toList());
        assertEquals(new AdmissionResult(true, RejectionReason.NONE,
                0x20, 0x61, 0xE0), decisions.getFirst());
        assertEquals(new AdmissionResult(false, RejectionReason.PRIORITY,
                0x21, 0x21, 0xE1), decisions.getLast());
        assertEquals(List.of(0xA0), contentionAdmissions,
                "a rejected whole request never reaches sequencer construction");
        assertEquals(List.of(0x81, 0xA0),
                sourceIds(activeDriverSnapshot()));
    }

    @Test
    void blockedPresentationSubmissionProducesOneRejectedDecision() {
        AtomicInteger policyEvaluations = new AtomicInteger();
        List<AdmissionResult> decisions = new ArrayList<>();
        audio.setAdmissionObserver(decision -> decisions.add(decision.result()));
        audio.setAudioProfile(profile(context -> {
            policyEvaluations.incrementAndGet();
            return new AdmissionResult(true, RejectionReason.NONE,
                    0x33, 0x72, 0xE2);
        }));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        setPresentationSfxBlocked(true);

        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);

        assertEquals(1, decisions.size());
        assertEquals(1, policyEvaluations.get(),
                "a resolved request reaches policy exactly once before the"
                        + " later presentation gate blocks insertion");
        assertFalse(decisions.getFirst().accepted());
        assertEquals(RejectionReason.BLOCKED,
                decisions.getFirst().reason());
        assertEquals(new AdmissionResult(false, RejectionReason.BLOCKED,
                0x33, 0x33, 0xE2), decisions.getFirst());
        assertEquals(1, activeDriverSnapshot().sequencers().size());
    }

    @Test
    void policyRunsOnceBeforeLateCacheMissAndNotAtResolutionFailure() {
        AtomicInteger evaluations = new AtomicInteger();
        List<AdmissionResult> decisions = new ArrayList<>();
        SmpsSfxInstantiation lateCacheMiss = new SmpsSfxInstantiation() {
            @Override
            public Admission evaluateAdmission(
                    ResolvedSmpsSfxSource source, SmpsDriver currentOwner) {
                evaluations.incrementAndGet();
                Admission base = SmpsSfxInstantiation.super
                        .evaluateAdmission(source, currentOwner);
                return new Admission(base.context(), new AdmissionResult(
                        true, RejectionReason.NONE, 0x22, 0x66, 0xE1));
            }

            @Override
            public SmpsSequencer instantiateCached(
                    ResolvedSmpsSfxSource source,
                    SmpsDriver currentOwner) {
                throw new AssertionError("standalone cache failed first");
            }

            @Override
            public com.openggf.audio.session.PreparedSmpsSfxProgram
                    prepareCached(ResolvedSmpsSfxSource source) {
                return null;
            }


            @Override
            public void observeAdmission(Admission admission) {
                decisions.add(admission.result());
            }
        };
        SmpsCoordFlagHandlerOwner testCoordFlags =
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true, testCoordFlags);
        ResolvedSmpsSfxSource resolved = new ResolvedSmpsSfxSource(
                700,
                new SmpsAssetKey("fixture", SmpsAssetKey.Route.BASE_ID,
                        0xA0, null),
                65_536, 0x60, 0, 1, 800);

        try (SmpsDriverSession session =
                     SmpsSessionTestSupport.installed(48_000)) {
            AudioVoiceRegistry registry = new AudioVoiceRegistry(
                    lateCacheMiss, factory, testCoordFlags,
                    ignored -> { }, session);
            AudioPresentationSessionCommandApplier.apply(
                    session, registry,
                    new AudioPresentationCommand.AddSmpsSfx(resolved));
            assertEquals(0, registry.orderedVoiceCount());
            assertEquals(0, session.captureLogicalSnapshot()
                    .sequencers().size());
        }

        assertEquals(1, evaluations.get(),
                "a resolved request evaluates once before cache insertion");
        assertEquals(1, decisions.size());
        assertEquals(RejectionReason.CACHE_MISS,
                decisions.getFirst().reason());
        assertEquals(new AdmissionResult(false,
                        RejectionReason.CACHE_MISS,
                        0x22, 0x22, 0xE1), decisions.getFirst(),
                "late rejection preserves evaluated identity and original"
                        + " priority while defining post-rejection priority"
                        + " as the unchanged original priority");
        AtomicInteger unresolvedEvaluations = new AtomicInteger();
        loader.sfxResults.put(0xA1, null);
        audio.setAudioProfile(profile(context -> {
            unresolvedEvaluations.incrementAndGet();
            return SmpsRequestAdmissionPolicy.PERMISSIVE.evaluate(context);
        }));
        audio.setRom(mock(Rom.class));
        audio.playSfx(0xA1);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0, unresolvedEvaluations.get(),
                "the policy has no request boundary when asset resolution"
                        + " genuinely fails before command creation");
    }

    @Test
    void resolvedSourceStillEvaluatesPolicyOnceAfterCacheEviction() {
        AtomicInteger evaluations = new AtomicInteger();
        List<AdmissionResult> decisions = new ArrayList<>();
        SmpsCoordFlagHandlerOwner testCoordFlags =
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(() -> true,
                        testCoordFlags);
        factory.setSfxAdmissionPolicy(context -> {
            evaluations.incrementAndGet();
            assertEquals(0xB7, context.resolvedSoundId());
            assertTrue(context.specialSfx());
            return new AdmissionResult(true, RejectionReason.NONE,
                    0x31, 0x72, 0xE4);
        });
        factory.setAdmissionObserver(decision ->
                decisions.add(decision.result()));
        SmpsAssetKey key = new SmpsAssetKey(
                "fixture", SmpsAssetKey.Route.BASE_ID, 0xA0, null);
        AbstractSmpsData resolvedData = data("resolved", 0xB7);
        factory.registerSmpsSfxAsset(key, resolvedData,
                AudioTestFixtures.EMPTY_DAC,
                new SmpsSequencerConfig.Builder().build(), true);
        ResolvedSmpsSfxSource source = factory.resolveSmpsSfx(
                800, key, 65_536, 0x60, 0, 1, 800);
        clearFactorySfxCache(factory);
        try (SmpsDriverSession session =
                     SmpsSessionTestSupport.installed(48_000)) {
            AudioVoiceRegistry registry = new AudioVoiceRegistry(
                    factory, factory, testCoordFlags,
                    ignored -> { }, session);
            AudioPresentationSessionCommandApplier.apply(
                    session, registry,
                    new AudioPresentationCommand.AddSmpsSfx(source));
            assertEquals(0, registry.orderedVoiceCount());
            assertEquals(0, session.captureLogicalSnapshot()
                    .sequencers().size());
        }

        assertEquals(1, evaluations.get());
        assertEquals(List.of(new AdmissionResult(false,
                RejectionReason.CACHE_MISS,
                0x31, 0x31, 0xE4)), decisions);
    }

    @Test
    void defaultPolicyIsExactlyPermissiveAndGameNeutral() {
        GameAudioProfile profile = profile(null);
        SmpsAdmissionContext context = new SmpsAdmissionContext(
                0xA0, 0xA0, 0x60,
                SmpsRequestAdmissionPolicy.NO_PRIORITY, false, false);

        assertSame(SmpsRequestAdmissionPolicy.PERMISSIVE,
                profile.getSfxAdmissionPolicy());
        assertEquals(new AdmissionResult(true, RejectionReason.NONE,
                        SmpsRequestAdmissionPolicy.NO_PRIORITY, 0x60, 0xA0),
                profile.getSfxAdmissionPolicy().evaluate(context));
    }

    @Test
    void defaultManagerLeavesDriverDiagnosticsActuallyDisabled() {
        audio.setAudioProfile(profile(null));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);

        SmpsDriverSnapshot before = activeDriverSnapshot();
        audio.setDriverServiceObserver(SmpsDriverServiceObserver.NONE);
        audio.setSfxContentionObserver(SfxContentionObserver.NONE);
        audio.presentFrame(PresentationMode.SILENT);
        assertDriverStateEquals(before, activeDriverSnapshot());
    }

    @Test
    void legacyBackendAlsoPropagatesPreConstructionObserversAndPolicy() {
        List<SmpsAdmissionContext> contexts = new ArrayList<>();
        List<Integer> admitted = new ArrayList<>();
        List<AdmissionResult> backendDecisions = new ArrayList<>();
        List<Long> serviceEnds = new ArrayList<>();
        List<LifecycleEvent> lifecycle =
                new ArrayList<>();
        List<String> writes = new ArrayList<>();
        SmpsRequestAdmissionPolicy policy = context -> {
            contexts.add(context);
            if (context.resolvedSoundId() == 0xA2) {
                return new AdmissionResult(false,
                        RejectionReason.PRIORITY,
                        0x34, 0x34, 0xE2);
            }
            return new AdmissionResult(true, RejectionReason.NONE,
                    0x31, 0x72,
                    context.resolvedSoundId() == 0xA0 ? 0xE0 : 0xE1);
        };
        GameAudioProfile backendProfile = profile(policy);
        audio.setAudioProfile(backendProfile);
        audio.setRom(mock(Rom.class));
        HeadlessSmpsAudioBackend backend = new HeadlessSmpsAudioBackend(
                SonicConfigurationService.getInstance(), null, audio);
        backend.setAdmissionObserver(decision -> {
            admitted.add(decision.result().resolvedSoundId());
            backendDecisions.add(decision.result());
        });
        backend.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onSfxAdmitted(Admission admission) {
                admitted.add(admission.source().descriptor().id());
            }
        });
        backend.setChipWriteObserver(recordingChipObserver(writes));
        backend.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                if (event.kind() == ServiceKind.SEQUENCER_TICK) {
                    serviceEnds.add(event.ordinal());
                }
            }

            @Override
            public void onLifecycle(LifecycleEvent event) {
                lifecycle.add(event);
            }
        });
        backend.setAudioProfile(backendProfile);

        backend.playSmps(new LongRunningMusicData(0x81),
                AudioTestFixtures.EMPTY_DAC,
                backendProfile.getSequencerConfig(), false);
        SmpsDriver base = backend.musicDriverForTesting();
        assertNotNull(base);
        assertTrue(lifecycle.stream().anyMatch(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.DRIVER_CREATED));
        List<String> backendConstructorWrites =
                expectedConstructorSilenceWrites();
        backendConstructorWrites.add("YM:0:2B:80");
        assertEquals(backendConstructorWrites, writes);

        backend.playSfxSmps(data("backend-sfx", 0xA0),
                AudioTestFixtures.EMPTY_DAC, 1.0f,
                backendProfile.getSequencerConfig());
        assertEquals(List.of(0xA0), contexts.stream()
                .map(SmpsAdmissionContext::resolvedSoundId).toList());
        assertEquals(List.of(0xA0, 0xE0), admitted,
                "contention admission precedes the completed request decision");
        assertEquals(new AdmissionResult(true, RejectionReason.NONE,
                0x31, 0x72, 0xE0), backendDecisions.getFirst());

        serviceOneTick(base, 0x81,
                backend.stateForTesting().currentStream());
        assertEquals(List.of(0L), serviceEnds);

        backend.playSmps(data("backend-override", 0x82),
                AudioTestFixtures.EMPTY_DAC,
                backendProfile.getSequencerConfig(), true);
        assertFalse(base == backend.musicDriverForTesting());
        assertTrue(lifecycle.stream().anyMatch(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.SAVE));

        backend.playSfxSmps(data("backend-blocked", 0xA1),
                AudioTestFixtures.EMPTY_DAC, 1.0f,
                backendProfile.getSequencerConfig());
        assertEquals(List.of(0xA0, 0xA1), contexts.stream()
                .map(SmpsAdmissionContext::resolvedSoundId).toList(),
                "the legacy path evaluates a resolved request before its"
                        + " override gate");
        assertEquals(RejectionReason.BLOCKED,
                backendDecisions.getLast().reason());
        assertEquals(new AdmissionResult(false, RejectionReason.BLOCKED,
                0x31, 0x31, 0xE1), backendDecisions.getLast());

        backend.playSfxSmps(data("backend-policy-rejected", 0xA2),
                AudioTestFixtures.EMPTY_DAC, 1.0f,
                backendProfile.getSequencerConfig());
        assertEquals(List.of(0xA0, 0xA1, 0xA2), contexts.stream()
                .map(SmpsAdmissionContext::resolvedSoundId).toList());
        assertEquals(new AdmissionResult(false, RejectionReason.PRIORITY,
                0x34, 0x34, 0xE2), backendDecisions.getLast());
    }

    @Test
    void lifecycleEventsIdentifyEachDriverWithoutAggregateStopDuplicates() {
        List<LifecycleEvent> lifecycle = new ArrayList<>();
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                lifecycle.add(event);
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        audio.playMusic(0x82);
        audio.presentFrame(PresentationMode.SILENT);

        List<LifecycleEvent> created = lifecycle.stream()
                .filter(event -> event.kind()
                        == SmpsDriverServiceObserver.LifecycleKind.DRIVER_CREATED)
                .toList();
        assertEquals(1, created.size());
        assertEquals(1, created.stream().map(LifecycleEvent::driver)
                .distinct().count());
        assertTrue(created.stream().allMatch(event -> event.scope()
                == SmpsDriverServiceObserver.LifecycleScope.DRIVER));
        assertTrue(lifecycle.stream().anyMatch(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.SAVE
                && event.scope()
                == SmpsDriverServiceObserver.LifecycleScope.DRIVER
                && event.driver().equals(created.getFirst().driver())));

        lifecycle.clear();
        audio.stopAllSfx();
        audio.presentFrame(PresentationMode.SILENT);
        List<LifecycleEvent> stopSfx = lifecycle.stream()
                .filter(event -> event.kind()
                        == SmpsDriverServiceObserver.LifecycleKind.STOP_ALL_SFX)
                .toList();
        assertEquals(1, stopSfx.size(),
                "the one persistent driver owns every SFX stop");
        assertEquals(1, stopSfx.stream().map(LifecycleEvent::driver)
                .distinct().count());
        assertTrue(stopSfx.stream().allMatch(event -> event.scope()
                == SmpsDriverServiceObserver.LifecycleScope.DRIVER));

        lifecycle.clear();
        audio.stopMusic();
        audio.presentFrame(PresentationMode.SILENT);
        List<LifecycleEvent> stopMusic = lifecycle.stream()
                .filter(event -> event.kind()
                        == SmpsDriverServiceObserver.LifecycleKind.STOP_ALL)
                .toList();
        assertEquals(0, stopMusic.size(),
                "logical music stop must not masquerade as a device-wide"
                        + " stop or allocate another driver");
    }

    @Test
    void preparedRestoreDefersCrossObserverEventsUntilCommitAndDiscardsThem() {
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();
        List<String> events = new ArrayList<>();
        audio.setChipWriteObserver(recordingChipObserver(events));
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                events.add("LIFE:" + event.kind());
            }
        });
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(snapshot);
        assertEquals(List.of(), events,
                "selected restore preparation remains invisible");
        assertTrue(audio.endReverseAudioPresentation());
        assertEquals(List.of("LIFE:RESTORE", "LIFE:STOP_ALL_SFX"), events,
                "the committed persistent-driver restore and reverse-release"
                        + " transient cleanup publish after commit");
    }

    @Test
    void failedPreparedRestoreDiscardsEventsFromAlreadyRecreatedDrivers() {
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();
        List<String> events = new ArrayList<>();
        audio.setChipWriteObserver(recordingChipObserver(events));
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                events.add("LIFE:" + event.kind());
            }
        });
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(snapshot);
        assertThrows(IllegalStateException.class,
                () -> audio.replayTimelineCommandLogically(
                        new AudioCommand.PlayMusic(
                                0x99, AudioCommand.MusicRoute.BASE_SMPS,
                                true, null)));
        assertEquals(List.of(), events,
                "failed preparation discards the complete diagnostic"
                        + " transaction without reconstructing a driver");
    }

    @Test
    void serviceCallbacksMatchEachDriverFrameTrackWalk() {
        SmpsDriver driver = SmpsDriverTestAccess.create(600.0);
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                .tempoModBase(0x100)
                .fmChannelOrder(new int[] {2}).build();
        AbstractSmpsData musicData = new LongRunningMusicData();
        SmpsSequencer music = new SmpsSequencer(
                musicData, AudioTestFixtures.EMPTY_DAC,
                driver, audio, config);
        music.setSourceDescriptor(SmpsSourceDescriptor.from(musicData));
        music.setSampleRate(600.0);
        driver.addSequencer(music, false);
        List<String> services = new ArrayList<>();
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                services.add("begin:" + event.kind() + ":"
                        + event.ordinal() + ":"
                        + event.sequencer().source().id());
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                assertNotNull(snapshot);
                services.add("end:" + event.kind() + ":"
                        + event.ordinal() + ":"
                        + event.sequencer().source().id());
            }
        });

        SmpsDriverTestAccess.read(driver, new short[0]);
        restoreTempoState(music, 10, 0, 1, 0);
        SmpsDriverTestAccess.read(driver, new short[20]);
        assertEquals(List.of(
                "begin:SEQUENCER_TICK:0:129",
                "end:SEQUENCER_TICK:0:129"), services,
                "a tempo-delay frame still performs the ROM's full track"
                        + " walk and reports one driver service");

        services.clear();
        restoreTempoState(music, 10, 250, 1, 0);
        SmpsDriverTestAccess.read(driver, new short[20]);
        assertEquals(List.of(
                "begin:SEQUENCER_TICK:1:129",
                "end:SEQUENCER_TICK:1:129"), services);

        services.clear();
        restoreTempoState(music, 10, 250, 3, 0);
        SmpsDriverTestAccess.read(driver, new short[20]);
        assertEquals(List.of(
                "begin:SEQUENCER_TICK:2:129",
                "end:SEQUENCER_TICK:2:129"), services,
                "the speed multiplier is serviced by the S3K driver's"
                        + " shared speed-up tail, not by multiplying an"
                        + " individual sequencer's track walk");
    }

    @Test
    void musicSpeedSettingDoesNotMultiplyAnSfxTrackWalk() {
        SmpsDriver driver = SmpsDriverTestAccess.create(60.0);
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                .tempoModBase(0x100)
                .fmChannelOrder(new int[] {2}).build();
        SmpsSequencer sfx = new SmpsSequencer(
                new LongRunningFmSfxData(), AudioTestFixtures.EMPTY_DAC,
                driver, audio, config);
        sfx.setSampleRate(60.0);
        driver.addSequencer(sfx, true);
        restoreDiagnosticState(sfx, 10, 250, 3, 0, 2,
                sfx.captureSnapshot().fade());
        List<Integer> maxTicksAfterService = new ArrayList<>();
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                if (event.kind() == ServiceKind.SEQUENCER_TICK) {
                    maxTicksAfterService.add(snapshot.sequencers()
                            .getFirst().snapshot().maxTicks());
                }
            }
        });

        SmpsDriverTestAccess.read(driver, new short[2]);

        assertEquals(List.of(1), maxTicksAfterService,
                "the S3K speed-up tail repeats music updates only; an SFX"
                        + " still consumes its budget once per driver pass");
    }

    @Test
    void fadeOnlyFrameAndTempoDelayTrackWalkHaveTypedServices() {
        SmpsDriver driver = SmpsDriverTestAccess.create(60.0);
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                .tempoModBase(0x100)
                .fmChannelOrder(new int[] {2}).build();
        AbstractSmpsData musicData = new LongRunningMusicData();
        SmpsSequencer music = new SmpsSequencer(
                musicData, AudioTestFixtures.EMPTY_DAC,
                driver, audio, config);
        music.setSampleRate(60.0);
        driver.addSequencer(music, false);
        List<String> events = new ArrayList<>();
        ServiceEvent[] active = {null};
        SmpsDriverTestAccess.setChipWriteObserver(driver, new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                assertNotNull(active[0], "fade write must be service-scoped");
                events.add("write:" + active[0].kind());
            }

            @Override
            public void onPsgWrite(int value) {
                assertNotNull(active[0], "fade write must be service-scoped");
                events.add("write:" + active[0].kind());
            }
        });
        driver.setServiceObserver(scopedServiceObserver(events, active));

        restoreDiagnosticState(music, 10, 0, 1, 0,
                Integer.MAX_VALUE,
                music.captureSnapshot().fade());
        int durationBeforeDelayFrame = music.getTracks().stream()
                .filter(track -> track.type == SmpsSequencer.TrackType.FM)
                .findFirst().orElseThrow().duration;
        SmpsDriverTestAccess.read(driver, new short[2]);
        assertEquals("begin:SEQUENCER_TICK:0", events.getFirst());
        assertEquals("end:SEQUENCER_TICK:0", events.getLast());
        assertTrue(events.subList(1, events.size() - 1).stream()
                .allMatch("write:SEQUENCER_TICK"::equals));
        // The walk really ran, and it produced no chip write. S1's tempo delay
        // adds 1 to every slot's DurationTimeout (s1.sounddriver.asm:1549-1561)
        // and the walk's own decrement cancels it, so a track that has already
        // read a note advances nothing on a delay frame and emits nothing. The
        // net-zero duration is the evidence the walk happened. This guard used
        // to require at least one write, which only held while the track had
        // never started: every driver seeds DurationTimeout with 1 rather than
        // 0 (s1.sounddriver.asm:823, :836; s2.sounddriver.asm:1857; skdisasm
        // Sound/Z80 Sound Driver.asm:2168-2184), so what it was really
        // measuring was the track's opening read, not a delay frame.
        int durationAfterDelayFrame = music.getTracks().stream()
                .filter(track -> track.type == SmpsSequencer.TrackType.FM)
                .findFirst().orElseThrow().duration;
        assertEquals(durationBeforeDelayFrame, durationAfterDelayFrame,
                "a tempo-delay frame walks the track and leaves its duration"
                        + " net unchanged");
        assertEquals(2, events.size(),
                "and it scopes every write it does make to its sequencer"
                        + " service, of which a delay frame makes none");

        events.clear();
        restoreDiagnosticState(music, 10, 0, 1, 0,
                Integer.MAX_VALUE,
                new SmpsSequencerSnapshot.FadeSnapshot(
                        1, 0, 0, 1, 1, true, true));
        SmpsDriverTestAccess.read(driver, new short[2]);

        assertEquals("begin:FADE_STEP:1", events.getFirst());
        int fadeEnd = events.indexOf("end:FADE_STEP:1");
        assertTrue(fadeEnd > 1);
        assertTrue(events.subList(1, fadeEnd).stream()
                .allMatch("write:FADE_STEP"::equals));
        assertTrue(events.size() > fadeEnd + 2,
                "an actual fade volume step writes inside its service");
        assertEquals("begin:SEQUENCER_TICK:2", events.get(fadeEnd + 1));
        assertEquals("end:SEQUENCER_TICK:2", events.getLast());
    }

    @Test
    void finalSfxTickAndCompletionCleanupBracketEveryWrite() {
        SmpsDriver driver = SmpsDriverTestAccess.create(60.0);
        SmpsSequencer sfx = new SmpsSequencer(
                new LongRunningFmSfxData(), AudioTestFixtures.EMPTY_DAC,
                driver, audio, new SmpsSequencerConfig.Builder().build());
        sfx.setSampleRate(60.0);
        List<String> events = new ArrayList<>();
        ServiceEvent[] active = {null};
        AtomicInteger finalTickKeyOffs = new AtomicInteger();
        int[] lastTickYmWrite = {-1, -1};
        driver.addSequencer(sfx, true);
        restoreDiagnosticState(sfx, 1, 255, 1, 0, 1,
                sfx.captureSnapshot().fade());
        SmpsDriverTestAccess.setChipWriteObserver(driver, new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                assertNotNull(active[0], "YM write escaped a service");
                events.add("write:" + active[0].kind());
                if (active[0].kind() == ServiceKind.SEQUENCER_TICK) {
                    lastTickYmWrite[0] = register;
                    lastTickYmWrite[1] = value;
                    if (register == 0x28 && (value & 0xF0) == 0) {
                        finalTickKeyOffs.incrementAndGet();
                    }
                }
            }

            @Override
            public void onPsgWrite(int value) {
                assertNotNull(active[0], "PSG write escaped a service");
                events.add("write:" + active[0].kind());
            }
        });
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                assertEquals(null, active[0], "services must not nest");
                assertEquals(0xA2, event.sequencer().source().id());
                assertTrue(event.sequencer().sfx());
                active[0] = event;
                events.add("begin:" + event.kind() + ":" + event.ordinal());
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                assertSame(active[0], event);
                if (event.kind() == ServiceKind.SEQUENCER_TICK) {
                    assertFalse(snapshot.sequencers().getFirst().snapshot()
                                    .tracks().getFirst().active(),
                            "maxTicks expiry belongs to the final literal tick");
                } else if (event.kind() == ServiceKind.COMPLETION_CLEANUP) {
                    assertEquals(List.of(), snapshot.sequencers(),
                            "cleanup publishes post-removal state");
                    assertTrue(java.util.Arrays.stream(
                                    snapshot.fmLockSequencerIds())
                            .allMatch(lock -> lock == -1),
                            "cleanup publishes post-release lock state");
                }
                events.add("end:" + event.kind() + ":" + event.ordinal());
                active[0] = null;
            }
        });

        SmpsDriverTestAccess.read(driver, new short[2]);

        int tickEnd = events.indexOf("end:SEQUENCER_TICK:0");
        int cleanupBegin = events.indexOf("begin:COMPLETION_CLEANUP:1");
        assertEquals("begin:SEQUENCER_TICK:0", events.getFirst());
        assertTrue(tickEnd > 1);
        assertTrue(events.subList(1, tickEnd).stream()
                .allMatch("write:SEQUENCER_TICK"::equals),
                "the expiry stopNote write belongs to the final tick");
        assertTrue(finalTickKeyOffs.get() >= 1,
                "maxTicks expiry keys off the still-running FM track"
                        + " before the final tick ends");
        assertEquals(0x28, lastTickYmWrite[0]);
        assertEquals(0, lastTickYmWrite[1] & 0xF0,
                "the expiry key-off is the final YM mutation of the tick");
        assertEquals(tickEnd + 1, cleanupBegin);
        assertEquals("end:COMPLETION_CLEANUP:1", events.getLast());
        assertTrue(events.subList(cleanupBegin + 1, events.size() - 1)
                .stream().allMatch("write:COMPLETION_CLEANUP"::equals),
                "forceSilence writes belong to completion cleanup");
        assertTrue(events.size() - cleanupBegin > 2,
                "completion cleanup includes forceSilence writes");
        assertEquals(null, active[0]);
    }

    @Test
    void serviceObservationPreservesMusicThenSfxSequencerTickOrder() {
        SmpsDriver driver = SmpsDriverTestAccess.create(60.0);
        SmpsSequencer music = new SmpsSequencer(
                new LongRunningMusicData(0x81),
                AudioTestFixtures.EMPTY_DAC, driver, audio,
                new SmpsSequencerConfig.Builder().build());
        SmpsSequencer sfx = new SmpsSequencer(
                new OneTickFmSfxData(), AudioTestFixtures.EMPTY_DAC,
                driver, audio,
                new SmpsSequencerConfig.Builder().build());
        music.setSampleRate(60.0);
        sfx.setSampleRate(60.0);
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);
        restoreTempoState(music, 10, 250, 1, 0);
        restoreTempoState(sfx, 10, 250, 1, 0);
        List<Integer> order = new ArrayList<>();
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                if (event.kind() == ServiceKind.SEQUENCER_TICK) {
                    order.add(event.sequencer().source().id());
                }
            }
        });

        SmpsDriverTestAccess.read(driver, new short[2]);

        assertEquals(List.of(0x81, 0xA0), order,
                "observation must not perturb the driver's established"
                        + " music-then-SFX iteration order");
    }

    @Test
    void contentionCallbacksRunAfterAdmissionAndLockMutation() {
        SmpsDriver driver = new SmpsDriver();
        AbstractSmpsData sfxData = data("ordered", 0xA0);
        SmpsSequencer sfx = new SmpsSequencer(
                sfxData, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder()
                        .fmChannelOrder(new int[] {2}).build());
        List<String> callbacks = new ArrayList<>();
        driver.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onSfxAdmitted(Admission admission) {
                assertTrue(driver.captureSnapshot().sequencers().stream()
                        .anyMatch(entry -> entry.source().id() == 0xA0),
                        "admission callback follows sequencer attachment");
                callbacks.add("admitted");
            }

            @Override
            public void onRoleArbitrated(Arbitration arbitration) {
                int lock = driver.captureSnapshot()
                        .fmLockSequencerIds()[arbitration.channel()];
                assertTrue(lock >= 0,
                        "acquired arbitration callback follows lock mutation");
                assertEquals(0xA0, driver.captureSnapshot().sequencers()
                        .get(lock).source().id());
                callbacks.add("arbitrated");
            }
        });

        driver.addSequencer(sfx, true);
        sfx.writeFm(0, 0xA2, 0x22);

        assertEquals(List.of("admitted", "arbitrated"), callbacks);
    }

    @Test
    void observerFailuresAreQuarantinedWithoutAdmissionOrServiceReplay() {
        AtomicInteger admissionAttempts = new AtomicInteger();
        audio.setAdmissionObserver(decision -> {
            if (admissionAttempts.getAndIncrement() == 0) {
                throw new IllegalStateException("admission capture failed");
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        SmpsDriverSnapshot admitted = activeDriverSnapshot();
        assertEquals(List.of(0x81, 0xA0), sourceIds(admitted));
        assertEquals(1, admissionAttempts.get());
        audio.presentFrame(PresentationMode.SILENT);
        assertDriverStateEquals(admitted, activeDriverSnapshot());
        assertEquals(1, admissionAttempts.get(),
                "a quarantined observer never replays its committed command");

        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        AtomicInteger serviceAttempts = new AtomicInteger();
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceEnd(
                    long ordinal, SmpsDriverSnapshot snapshot) {
                serviceAttempts.incrementAndGet();
                throw new IllegalStateException("service capture failed");
            }
        });
        loader.musicResults.put(0x81, new LongRunningMusicData());
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        audio.presentFrame(PresentationMode.FORWARD);
        assertTrue(serviceAttempts.get() > 0);
        assertEquals(0x81,
                activeDriverSnapshot().sequencers().getFirst().source().id());
    }

    @Test
    void sessionAdmissionObserverFailureIsQuarantinedWithoutCommandReplay() {
        AtomicInteger attempts = new AtomicInteger();
        audio.setAdmissionObserver(decision -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("standalone failed");
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));

        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0, presentationRegistry().orderedVoiceCount());
        assertEquals(List.of(0xA0), sourceIds(activeDriverSnapshot()));

        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0, presentationRegistry().orderedVoiceCount());
        assertEquals(1, attempts.get());
    }

    @Test
    void sessionCoordinationStartRunsOnceForContinuousRetrigger() {
        SmpsCoordFlagRuntimeState state = new SmpsCoordFlagRuntimeState();
        SmpsCoordFlagHandlerOwner owner = new SmpsCoordFlagHandlerOwner(state);
        owner.register("fixture", runtime -> new CoordFlagHandler() {
            @Override
            public void onSfxStart(int sfxId) {
                runtime.setSpindashRevCounter(
                        runtime.spindashRevCounter() + 1);
            }

            @Override
            public boolean handleFlag(
                    com.openggf.audio.smps.CoordFlagContext context,
                    SmpsSequencer.Track track, int command) {
                return false;
            }

            @Override
            public int flagParamLength(int command) {
                return -1;
            }
        });
        SmpsDriverSession session =
                SmpsSessionTestSupport.installed(48_000);
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true, owner,
                        AudioPresentationSourceFactory.Settings.defaults(),
                        session);
        SmpsAssetKey key = new SmpsAssetKey(
                "fixture", SmpsAssetKey.Route.BASE_ID, 0xA0, null);
        factory.registerSmpsSfxAsset(
                key, new OneTickFmSfxData(), AudioTestFixtures.EMPTY_DAC,
                new SmpsSequencerConfig.Builder()
                        .coordFlagHandler(new CoordFlagHandler() {
                            @Override
                            public boolean handleFlag(
                                    com.openggf.audio.smps.CoordFlagContext context,
                                    SmpsSequencer.Track track, int command) {
                                return false;
                            }

                            @Override
                            public int flagParamLength(int command) {
                                return -1;
                            }
                        }).build());
        ResolvedSmpsSfxSource source = factory.resolveSmpsSfx(
                900, key, 65_536, 0x60, 0xA0, 1, 900);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, owner, ignored -> { }, session);

        AudioPresentationSessionCommandApplier.apply(
                session, registry,
                new AudioPresentationCommand.AddSmpsSfx(source));
        assertEquals(1, state.spindashRevCounter());
        assertEquals(0, registry.orderedVoiceCount());

        AudioPresentationSessionCommandApplier.apply(
                session, registry,
                new AudioPresentationCommand.AddSmpsSfx(source));
        assertEquals(1, state.spindashRevCounter(),
                "continuous extension skips onSfxStart");
        assertEquals(1, session.captureLogicalSnapshot()
                .sequencers().size());
    }

    @Test
    void queuedYmObserverFailureRestoresAndRetriesOnce() {
        loader.sfxResults.put(0xA0, new OneTickFmSfxData(0xA0));
        loader.sfxResults.put(0xA1, new OneTickFmSfxData(0xA1));
        AtomicInteger failures = new AtomicInteger();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                if (failures.getAndDecrement() > 0) {
                    throw new IllegalStateException("YM failed");
                }
            }

            @Override
            public void onPsgWrite(int value) { }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        SmpsDriverSnapshot before = activeDriverSnapshot();
        failures.set(1);

        audio.playSfx(0xA1);
        audio.presentFrame(PresentationMode.SILENT);
        SmpsDriverSnapshot committed = activeDriverSnapshot();
        assertFalse(sourceIds(before).equals(sourceIds(committed)));
        assertTrue(sourceIds(committed).contains(0xA1));

        audio.setChipWriteObserver(null);
        audio.presentFrame(PresentationMode.SILENT);
        assertDriverStateEquals(committed, activeDriverSnapshot());
    }

    @Test
    void queuedPsgObserverFailureRestoresAndRetriesOnce() {
        loader.sfxResults.put(0xA0, new OneTickPsgSfxData(0xA0));
        loader.sfxResults.put(0xA1, new OneTickPsgSfxData(0xA1));
        AtomicInteger failures = new AtomicInteger();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) { }

            @Override
            public void onPsgWrite(int value) {
                if (failures.getAndDecrement() > 0) {
                    throw new IllegalStateException("PSG failed");
                }
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        SmpsDriverSnapshot before = activeDriverSnapshot();
        failures.set(1);

        audio.playSfx(0xA1);
        audio.presentFrame(PresentationMode.SILENT);
        SmpsDriverSnapshot committed = activeDriverSnapshot();
        assertFalse(sourceIds(before).equals(sourceIds(committed)));
        assertTrue(sourceIds(committed).contains(0xA1));

        audio.setChipWriteObserver(null);
        audio.presentFrame(PresentationMode.SILENT);
        assertDriverStateEquals(committed, activeDriverSnapshot());
    }

    @Test
    void queuedContentionObserverFailureRestoresAndRetriesOnce() {
        loader.sfxResults.put(0xA0, new OneTickFmSfxData());
        AtomicInteger failures = new AtomicInteger();
        audio.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onSfxAdmitted(Admission admission) {
                if (failures.getAndDecrement() > 0) {
                    throw new IllegalStateException("contention failed");
                }
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        SmpsDriverSnapshot before = activeDriverSnapshot();
        failures.set(1);

        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        SmpsDriverSnapshot committed = activeDriverSnapshot();
        assertFalse(sourceIds(before).equals(sourceIds(committed)));
        assertTrue(sourceIds(committed).contains(0xA0));

        audio.setSfxContentionObserver(null);
        audio.presentFrame(PresentationMode.SILENT);
        assertDriverStateEquals(committed, activeDriverSnapshot());
    }

    @Test
    void queuedDriverLifecycleFailureIsQuarantinedWithoutReplay() {
        AtomicInteger failures = new AtomicInteger(1);
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                if (event.kind() == SmpsDriverServiceObserver.LifecycleKind.DRIVER_CREATED
                        && failures.getAndDecrement() > 0) {
                    throw new IllegalStateException("driver failed");
                }
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));

        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0, presentationRegistry().orderedVoiceCount());
        assertEquals(List.of(0xA0), sourceIds(activeDriverSnapshot()));

        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0, presentationRegistry().orderedVoiceCount());
        assertEquals(0, failures.get());
    }

    @Test
    void observerFailuresDuringConstructionAreQuarantined() {
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(
                    int port, int register, int value) {
                throw new IllegalStateException("chip capture failed");
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));

        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(List.of(0x81), sourceIds(activeDriverSnapshot()));
    }

    @Test
    void detachedReverseStagingDiscardsDiagnosticsAndDriverOrdinals() {
        List<String> events = new ArrayList<>();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                events.add("YM:%d:%02X:%02X".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
                events.add("PSG:%02X".formatted(value));
            }
        });
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                if (event.scope() == LifecycleScope.DRIVER) {
                    events.add("LIFE:" + event.kind() + ":"
                            + event.driver().instanceOrdinal());
                } else {
                    events.add("LIFE:" + event.scope() + ":"
                            + event.kind());
                }
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        events.clear();

        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(selected);
        audio.replayTimelineCommandLogically(new AudioCommand.PlayMusic(
                0x82, AudioCommand.MusicRoute.BASE_SMPS, true, null));
        assertEquals(List.of(), events,
                "detached snapshot reconstruction, command staging, and"
                        + " cleanup must be diagnostically invisible");

        audio.restoreLogicalSnapshot(selected);
        assertTrue(audio.endReverseAudioPresentation());
        assertEquals(List.of(), lifecycleDriverOrdinals(
                events, "LIFE:DRIVER_CREATED:"),
                "staging and restore never construct another driver");
        assertEquals(List.of(0L), lifecycleDriverOrdinals(
                events, "LIFE:RESTORE:"));
        assertTrue(events.stream().noneMatch(event ->
                event.startsWith("YM:") || event.startsWith("PSG:")),
                "persistent restore emits no constructor or staging writes");
    }

    @Test
    void failedDetachedReverseStagingLeaksNoDiagnosticsOrOrdinalGap() {
        List<LifecycleEvent> lifecycle = new ArrayList<>();
        List<String> chipWrites = new ArrayList<>();
        audio.setChipWriteObserver(recordingChipObserver(chipWrites));
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                lifecycle.add(event);
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        lifecycle.clear();
        chipWrites.clear();

        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(selected);
        assertThrows(IllegalStateException.class,
                () -> audio.replayTimelineCommandLogically(
                        new AudioCommand.PlayMusic(0x99,
                                AudioCommand.MusicRoute.BASE_SMPS,
                                true, null)));
        assertEquals(List.of(), chipWrites);
        assertEquals(List.of(), lifecycle,
                "failed preparation and its cleanup are discarded together");

        assertTrue(audio.endReverseAudioPresentation());
        assertEquals(List.of(), lifecycle.stream()
                .filter(event -> event.kind() == LifecycleKind.DRIVER_CREATED)
                .map(event -> event.driver().instanceOrdinal()).toList());
        assertTrue(lifecycle.stream()
                .filter(event -> event.scope() == LifecycleScope.DRIVER)
                .allMatch(event -> event.driver().instanceOrdinal() == 0));
        assertEquals(List.of(), chipWrites);
    }

    @Test
    void observerFailuresAreQuarantinedAcrossPresentationBoundaries() {
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(
                    int port, int register, int value) {
                throw new IllegalStateException("cache chip capture failed");
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(List.of(0xA0), sourceIds(activeDriverSnapshot()));

        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot restoreTarget = audio.captureLogicalSnapshot();
        audio.setDriverServiceObserver(throwingRestoreObserver(
                "restore lifecycle capture failed"));
        audio.restoreLogicalSnapshot(restoreTarget);
        assertEquals(sourceIds(restoreTarget.presentation().smpsLogical()),
                sourceIds(activeDriverSnapshot()));

        audio.setDriverServiceObserver(SmpsDriverServiceObserver.NONE);
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot reverseTarget = audio.captureLogicalSnapshot();
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(reverseTarget);
        audio.setDriverServiceObserver(throwingRestoreObserver(
                "reverse lifecycle capture failed"));
        assertTrue(audio.endReverseAudioPresentation());
    }

    @Test
    void legacyStandalonePcmObserverFailureStillEscapesUntilTask6() {
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.captureLogicalSnapshot();
        AudioPresentationSourceFactory factory = presentationFactory();
        factory.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                if (event.kind() == LifecycleKind.SEGA_PCM_ENTER) {
                    throw new IllegalStateException(
                            "PCM lifecycle capture failed");
                }
            }
        });
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory,
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState()), ignored -> { });
        DecodedPcm pcm = factory.registerUnsigned8Mono(
                "diagnostic-pcm", new byte[] {0, 1}, 8_000);
        SampleBackedVoice pcmVoice = factory.segaPcm(900, pcm);
        assertThrows(AudioDiagnosticObserverException.class,
                () -> registry.apply(
                        AudioPresentationCommand.ReplaceRawPcm.fromVoice(
                                pcmVoice, new byte[] {0})));
        assertNotNull(registry.snapshot().rawPcmVoiceId(),
                "PCM callback follows the actual registry mutation");
    }

    @Test
    void noneObserversDoNotChangeSnapshotsWritesLocksOrConstructorSilence()
            throws Exception {
        SmpsDriver ordinary = SmpsDriverTestAccess.create(48_000);
        SmpsDriver inert = SmpsDriverTestAccess.create(48_000);
        SmpsDriverTestAccess.setChipWriteObserver(
                inert, ChipWriteObserver.NONE);
        inert.setSfxContentionObserver(SfxContentionObserver.NONE);
        inert.setServiceObserver(SmpsDriverServiceObserver.NONE);

        List<String> ordinaryWrites = new ArrayList<>();
        List<String> inertWrites = new ArrayList<>();
        SmpsDriverTestAccess.setChipWriteObserver(
                ordinary, recordingChipObserver(ordinaryWrites));
        SmpsDriverTestAccess.setChipWriteObserver(
                inert, recordingChipObserver(inertWrites));
        exerciseWrites(ordinary);
        exerciseWrites(inert);

        assertEquals(ordinaryWrites, inertWrites);
        assertEquals(JSON.valueToTree(ordinary.captureSnapshot()),
                JSON.valueToTree(inert.captureSnapshot()));

        SmpsDriver defaultDriver = SmpsDriverTestAccess.create(48_000);
        SmpsDriver explicitNoneDriver =
                SmpsDriverTestAccess.create(48_000);
        SmpsDriverTestAccess.setChipWriteObserver(
                explicitNoneDriver, ChipWriteObserver.NONE);
        explicitNoneDriver.setSfxContentionObserver(
                SfxContentionObserver.NONE);
        explicitNoneDriver.setServiceObserver(
                SmpsDriverServiceObserver.NONE);
        SmpsSequencer defaultMusic = sequencer(
                "none-music", 0x81, defaultDriver);
        SmpsSequencer explicitNoneMusic = sequencer(
                "none-music", 0x81, explicitNoneDriver);
        SmpsSequencer defaultSfx = sequencer(
                "none-sfx", 0xA0, defaultDriver);
        SmpsSequencer explicitNoneSfx = sequencer(
                "none-sfx", 0xA0, explicitNoneDriver);
        defaultDriver.addSequencer(defaultMusic, false);
        explicitNoneDriver.addSequencer(explicitNoneMusic, false);
        defaultDriver.addSequencer(defaultSfx, true);
        explicitNoneDriver.addSequencer(explicitNoneSfx, true);
        defaultSfx.writeFm(0, 0xA2, 0x22);
        explicitNoneSfx.writeFm(0, 0xA2, 0x22);

        assertDriverStateEquals(defaultDriver.captureSnapshot(),
                explicitNoneDriver.captureSnapshot());
        short[] defaultPcm = new short[256];
        short[] explicitNonePcm = new short[256];
        SmpsDriverTestAccess.read(defaultDriver, defaultPcm);
        SmpsDriverTestAccess.read(explicitNoneDriver, explicitNonePcm);
        assertArrayEquals(defaultPcm, explicitNonePcm,
                "explicit NONE must preserve future PCM");
        assertDriverStateEquals(defaultDriver.captureSnapshot(),
                explicitNoneDriver.captureSnapshot());
    }

    @Test
    void leasedObserversReachFirstRealPresentationAndEveryRebuiltSession() {
        loader.musicResults.put(0x81, new LongRunningMusicData(0x81));
        loader.sfxResults.put(0xA0, new OneTickFmSfxData(0xA0));
        List<String> writes = new ArrayList<>();
        List<Integer> requests = new ArrayList<>();
        List<Integer> admissions = new ArrayList<>();
        List<Integer> contentions = new ArrayList<>();
        List<LifecycleEvent> lifecycle = new ArrayList<>();
        List<ServiceEvent> services = new ArrayList<>();
        RecordingPresentationBackend backend = new RecordingPresentationBackend();
        audio.setBackend(backend);
        var set = new AudioManager.DiagnosticObserverSet(
                (kind, id) -> requests.add(id),
                decision -> admissions.add(decision.result().resolvedSoundId()),
                new SmpsDriverServiceObserver() {
                    @Override public void onLifecycle(LifecycleEvent event) { lifecycle.add(event); }
                    @Override public void onServiceBegin(ServiceEvent event) { services.add(event); }
                }, recordingChipObserver(writes), new SfxContentionObserver() {
                    @Override public void onSfxAdmitted(Admission admission) {
                        contentions.add(admission.source().descriptor().id());
                    }
                });
        try (var lease = audio.acquireDiagnosticObservers(set)) {
            audio.setAudioProfile(profile(null));
            audio.setRom(mock(Rom.class));
            assertEquals(60, audio.presentationFrameRate());
            assertEquals(expectedConstructorSilenceWrites(), writes,
                    "the first real session observes physical construction, before a request");
            var firstFactory = presentationFactory();
            audio.playMusic(0x81);
            audio.playSfx(0xA0);
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(List.of(0x81, 0xA0), requests);
            assertEquals(List.of(0xA0), admissions);
            assertEquals(List.of(0xA0), contentions);
            assertFalse(services.isEmpty(), "the session must actually service real SMPS data");
            assertEquals(1, backend.frames);
            assertTrue(writes.size() > expectedConstructorSilenceWrites().size(),
                    "live installation must observe writes after the constructor burst");
            assertTrue(lifecycle.stream().anyMatch(event -> event.kind() == LifecycleKind.DRIVER_CREATED));

            writes.clear();
            audio.setAudioProfile(profile(null));
            assertNotSame(firstFactory, presentationFactory());
            assertEquals(expectedConstructorSilenceWrites(), writes,
                    "base-profile replacement constructs another observed physical session");
            assertTrue(lease.active());
            writes.clear();
            var beforeRom = presentationFactory();
            audio.setRom(mock(Rom.class));
            assertNotSame(beforeRom, presentationFactory());
            assertEquals(expectedConstructorSilenceWrites(), writes);

            writes.clear();
            audio.resetState();
            assertTrue(lease.active());
            assertNull(presentationFactory());
            assertSame(set.chipWrite(), backend.chip);
            audio.setAudioProfile(profile(null));
            audio.setRom(mock(Rom.class));
            audio.presentationFrameRate();
            assertEquals(expectedConstructorSilenceWrites(), writes,
                    "leased reset preserves the installation through reconstruction");

            writes.clear();
            RecordingPresentationBackend replacement = new RecordingPresentationBackend();
            audio.setBackend(replacement);
            assertSame(set.chipWrite(), replacement.chip);
            assertTrue(lease.active());
            audio.presentationFrameRate();
            assertEquals(expectedConstructorSilenceWrites(), writes);

            writes.clear();
            RecordingPresentationBackend launch = new RecordingPresentationBackend();
            audio.setBackendForLaunch(launch);
            assertSame(set.chipWrite(), launch.chip);
            audio.presentationFrameRate();
            assertEquals(expectedConstructorSilenceWrites(), writes);

            writes.clear();
            audio.destroy();
            assertTrue(lease.active(), "destroy does not revoke diagnostic ownership");
            assertNull(presentationFactory());
            audio.ensurePresentationSink();
            audio.presentationFrameRate();
            assertEquals(expectedConstructorSilenceWrites(), writes);
            audio.playMusic(0x81);
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(List.of(0x81, 0xA0, 0x81), requests);
            assertEquals(1, launch.frames);
        }
    }

    @Test
    void bootWriteCallbackReplacesLiveObserverAfterTheFactoryIsInstalled() {
        loader.musicResults.put(0x81, new LongRunningMusicData(0x81));
        List<String> constructor = new ArrayList<>();
        List<String> live = new ArrayList<>();
        ChipWriteObserver later = recordingChipObserver(live);
        ChipWriteObserver recorder = recordingChipObserver(constructor);
        AtomicInteger callbacks = new AtomicInteger();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override public void onYm2612Write(int port, int register, int value) {
                assertFalse(Thread.holdsLock(audio), "scene-source construction is unlocked");
                recorder.onYm2612Write(port, register, value);
                if (callbacks.getAndIncrement() == 0) {
                    assertNotNull(presentationFactory(), "factory is published before driver boot");
                    audio.setChipWriteObserver(later);
                }
            }
            @Override public void onPsgWrite(int value) { recorder.onPsgWrite(value); }
        });
        audio.setAudioProfile(profile(null));
        audio.setRom(mock(Rom.class));
        audio.setScenePcmSource(null);
        List<String> expected = expectedConstructorSilenceWrites();
        assertEquals(expected.subList(0, 1), constructor);
        assertEquals(expected.subList(1, expected.size()), live,
                "the installed factory retargets the booting session after its first write");
        int bootRemainder = live.size();
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.FORWARD);
        assertTrue(live.size() > bootRemainder, "subsequent real forward writes retain the live replacement");
        assertEquals(expected.subList(0, 1), constructor);
    }

    @Test
    void sessionConstructorArgumentAndLiveFactoryInstallationsSampleDistinctBoundaries() {
        ChipWriteObserver initial = recordingChipObserver(new ArrayList<>());
        ChipWriteObserver later = recordingChipObserver(new ArrayList<>());
        List<String> order = new ArrayList<>();
        audio.setChipWriteObserver(initial);
        // This controlled constructor observes argument/installation order only.
        // The adjacent real-session tests independently prove physical boot and live writes.
        try (var sessions = mockConstruction(SmpsDriverSession.class, (session, context) -> {
            assertSame(initial, context.arguments().get(2));
            assertNull(presentationFactory());
            order.add("session.constructor");
            audio.setChipWriteObserver(later);
            doAnswer(invocation -> {
                assertSame(SmpsDriverServiceObserver.NONE, invocation.getArgument(0));
                order.add("live.driver");
                return null;
            }).when(session).setDriverServiceObserver(any());
            doAnswer(invocation -> {
                assertSame(later, invocation.getArgument(0));
                order.add("live.chip");
                return null;
            }).when(session).setChipWriteObserver(any());
            doAnswer(invocation -> {
                assertSame(SfxContentionObserver.NONE, invocation.getArgument(0));
                order.add("live.contention");
                return null;
            }).when(session).setSfxContentionObserver(any());
            doAnswer(invocation -> {
                order.add("session.install");
                return null;
            }).when(session).install();
        })) {
            audio.presentationFrameRate();
            assertEquals(1, sessions.constructed().size());
            assertNotNull(presentationFactory(), "the live factory itself is real in this control");
            assertEquals(List.of("session.constructor", "live.driver", "live.chip",
                    "live.contention", "session.install"), order);
            audio.destroy();
        }
    }

    @Test
    void factoryPropagationRereadsActualReceiverAfterASetterRebuildsPresentation() {
        List<FactoryInstallation> calls = new ArrayList<>();
        AtomicReference<Consumer<FactoryInstallation>> callback = new AtomicReference<>(ignored -> { });
        var desired = diagnosticSet(ChipWriteObserver.NONE);
        try (var construction = observeFactoryInstallations(calls, callback)) {
            audio.presentationFrameRate();
            var first = construction.constructed().getFirst();
            calls.clear();
            callback.set(call -> {
                assertTrue(Thread.holdsLock(audio));
                if (call.receiver() == first && call.slot().equals("admission")) {
                    audio.setBackend(new NullAudioBackend());
                    audio.presentationFrameRate();
                }
            });
            try (var lease = audio.acquireDiagnosticObservers(desired)) {
                var second = construction.constructed().get(1);
                assertEquals(List.of(
                        factoryCall(first, "admission", desired.admission()),
                        factoryCall(second, "admission", AudioAdmissionObserver.NONE),
                        factoryCall(second, "driver", SmpsDriverServiceObserver.NONE),
                        factoryCall(second, "chip", ChipWriteObserver.NONE),
                        factoryCall(second, "contention", SfxContentionObserver.NONE),
                        factoryCall(second, "driver", desired.driverService()),
                        factoryCall(second, "chip", desired.chipWrite()),
                        factoryCall(second, "contention", desired.sfxContention())), calls);
                assertTrue(lease.active());
            }
        }
    }

    @Test
    void factoryRollbackRereadsCurrentReceiverAfterForwardAndRollbackRebuilds() {
        List<FactoryInstallation> calls = new ArrayList<>();
        AtomicReference<Consumer<FactoryInstallation>> callback = new AtomicReference<>(ignored -> { });
        var desired = diagnosticSet(recordingChipObserver(new ArrayList<>()));
        IllegalStateException sentinel = new IllegalStateException("factory forward");
        try (var construction = observeFactoryInstallations(calls, callback)) {
            audio.presentationFrameRate();
            var first = construction.constructed().getFirst();
            calls.clear();
            AtomicInteger phase = new AtomicInteger();
            callback.set(call -> {
                if (call.receiver() == first && call.slot().equals("chip")
                        && call.observer() == desired.chipWrite()) {
                    audio.setBackend(new NullAudioBackend());
                    audio.presentationFrameRate();
                    phase.set(1);
                    throw sentinel;
                }
                if (phase.get() == 1 && call.slot().equals("admission")) {
                    phase.set(2);
                    audio.setBackend(new NullAudioBackend());
                    audio.presentationFrameRate();
                }
            });
            assertSame(sentinel, assertThrows(IllegalStateException.class,
                    () -> audio.acquireDiagnosticObservers(desired)));
            assertArrayEquals(new Throwable[0], sentinel.getSuppressed());
            var second = construction.constructed().get(1);
            var third = construction.constructed().get(2);
            assertEquals(List.of(
                    factoryCall(first, "admission", desired.admission()),
                    factoryCall(first, "driver", desired.driverService()),
                    factoryCall(first, "chip", desired.chipWrite()),
                    factoryCall(second, "admission", AudioAdmissionObserver.NONE),
                    factoryCall(second, "driver", SmpsDriverServiceObserver.NONE),
                    factoryCall(second, "chip", ChipWriteObserver.NONE),
                    factoryCall(second, "contention", SfxContentionObserver.NONE),
                    factoryCall(second, "admission", AudioAdmissionObserver.NONE),
                    factoryCall(third, "admission", AudioAdmissionObserver.NONE),
                    factoryCall(third, "driver", SmpsDriverServiceObserver.NONE),
                    factoryCall(third, "chip", ChipWriteObserver.NONE),
                    factoryCall(third, "contention", SfxContentionObserver.NONE),
                    factoryCall(third, "driver", SmpsDriverServiceObserver.NONE),
                    factoryCall(third, "chip", ChipWriteObserver.NONE),
                    factoryCall(third, "contention", SfxContentionObserver.NONE)), calls);
            callback.set(ignored -> { });
            try (var retry = audio.acquireDiagnosticObservers(desired)) { assertTrue(retry.active()); }
        }
    }

    @Test
    void clearingFactoryDuringSetterFailsAtTheNextRereadAndRollbackSkipsIt() {
        List<FactoryInstallation> calls = new ArrayList<>();
        AtomicReference<Consumer<FactoryInstallation>> callback = new AtomicReference<>(ignored -> { });
        var desired = diagnosticSet(ChipWriteObserver.NONE);
        try (var construction = observeFactoryInstallations(calls, callback)) {
            audio.presentationFrameRate();
            var first = construction.constructed().getFirst();
            calls.clear();
            callback.set(call -> { if (call.slot().equals("driver")) audio.destroy(); });
            assertThrows(NullPointerException.class, () -> audio.acquireDiagnosticObservers(desired));
            assertEquals(List.of(factoryCall(first, "admission", desired.admission()),
                    factoryCall(first, "driver", desired.driverService())), calls);
            assertNull(presentationFactory());
            try (var retry = audio.acquireDiagnosticObservers(desired)) { assertTrue(retry.active()); }
        }
    }

    @Test
    void unleasedResetClearsSlotsBeforePartialBackendFailureAndLeavesOldPresentation() {
        RecordingPresentationBackend backend = new RecordingPresentationBackend();
        audio.setBackend(backend);
        List<Integer> requests = new ArrayList<>();
        var set = diagnosticSet(recordingChipObserver(new ArrayList<>()));
        audio.setRequestObserver((kind, id) -> requests.add(id));
        audio.setAdmissionObserver(set.admission());
        audio.setDriverServiceObserver(set.driverService());
        audio.setChipWriteObserver(set.chipWrite());
        audio.setSfxContentionObserver(set.sfxContention());
        audio.setAudioProfile(profile(null));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        AudioPresentationProducer previous = AudioManagerTestDiagnostics.presentationField(
                audio, "shadowProducer", AudioPresentationProducer.class);
        var factory = presentationFactory();
        backend.failChip = new IllegalStateException("reset chip");
        assertSame(backend.failChip, assertThrows(IllegalStateException.class, audio::resetState));
        assertSame(AudioAdmissionObserver.NONE, backend.admission);
        assertSame(SmpsDriverServiceObserver.NONE, backend.driver);
        assertSame(ChipWriteObserver.NONE, backend.chip);
        assertSame(set.sfxContention(), backend.contention,
                "reset does not perform a replacement transaction or physical rollback");
        assertSame(factory, presentationFactory());
        assertSame(previous, AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", AudioPresentationProducer.class));
        assertFalse(previous.isClosed(), "the later reset teardown was never reached");
        audio.playMusic(0xE0);
        assertEquals(List.of(0x81), requests, "request slot was already cleared");
        backend.failChip = null;
        // Identity occupancy proves all five manager slots are NONE despite the retained factory.
        try (var lease = audio.acquireDiagnosticObservers(new AudioManager.DiagnosticObserverSet(
                AudioRequestObserver.NONE, AudioAdmissionObserver.NONE, SmpsDriverServiceObserver.NONE,
                ChipWriteObserver.NONE, SfxContentionObserver.NONE))) { assertTrue(lease.active()); }
        audio.resetState();
        assertTrue(previous.isClosed());
    }

    @Test
    void leasedResetFailurePreservesInstallationAndRetryRetiresPresentation() {
        RecordingPresentationBackend backend = new RecordingPresentationBackend();
        audio.setBackend(backend);
        var set = diagnosticSet(recordingChipObserver(new ArrayList<>()));
        try (var lease = audio.acquireDiagnosticObservers(set)) {
            audio.setAudioProfile(profile(null));
            audio.setRom(mock(Rom.class));
            audio.presentationFrameRate();
            AudioPresentationProducer previous = AudioManagerTestDiagnostics.presentationField(
                    audio, "shadowProducer", AudioPresentationProducer.class);
            backend.failChip = new IllegalStateException("leased reset chip");
            assertSame(backend.failChip, assertThrows(IllegalStateException.class, audio::resetState));
            assertTrue(lease.active());
            assertSame(set.admission(), backend.admission);
            assertSame(set.driverService(), backend.driver);
            assertSame(set.chipWrite(), backend.chip);
            assertSame(set.sfxContention(), backend.contention);
            assertFalse(previous.isClosed());
            assertThrows(IllegalStateException.class, () -> audio.setRequestObserver(null));
            backend.failChip = null;
            audio.resetState();
            assertTrue(previous.isClosed());
            assertTrue(lease.active());
        }
    }

    @Test
    void requestFailureIsDirectWhileConstructionQuarantinesAndPauseResumeWrapAfterEffects() {
        loader.musicResults.put(0x81, new LongRunningMusicData(0x81));
        loader.musicResults.put(0x82, new LongRunningMusicData(0x82));
        IllegalStateException requestFailure = new IllegalStateException("direct request");
        audio.setRequestObserver((kind, id) -> {
            assertFalse(Thread.holdsLock(audio), "request callback remains on the unlocked Root path");
            throw requestFailure;
        });
        assertSame(requestFailure, assertThrows(IllegalStateException.class, () -> audio.playMusic(0x81)));
        assertNull(requestFailure.getCause());
        assertArrayEquals(new Throwable[0], requestFailure.getSuppressed());
        audio.setRequestObserver(null);
        IllegalStateException constructionFailure = new IllegalStateException("constructor chip");
        List<RuntimeException> quarantined = new ArrayList<>();
        AtomicInteger chipCalls = new AtomicInteger();
        var set = diagnosticSet(new ChipWriteObserver() {
            @Override public void onYm2612Write(int port, int register, int value) {
                chipCalls.incrementAndGet();
                throw constructionFailure;
            }
            @Override public void onPsgWrite(int value) { }
        });
        try (var lease = audio.acquireDiagnosticObservers(set)) {
            audio.setAudioProfile(profile(null));
            audio.setRom(mock(Rom.class));
            audio.playMusic(0x81);
            audio.presentFrame(PresentationMode.SILENT);
            assertTrue(chipCalls.get() > 0);
            assertEquals(List.of(0x81), sourceIds(activeDriverSnapshot()));
            presentationFactory().setDiagnosticErrorSink(quarantined::add);
            audio.playMusic(0x82);
            audio.presentFrame(PresentationMode.FORWARD);
            assertFalse(quarantined.isEmpty());
            assertTrue(quarantined.stream().allMatch(failure -> failure == constructionFailure),
                    "the real session's quarantine receives the original chip exception");
            assertTrue(lease.active());
        }
        List<String> order = new ArrayList<>();
        audio.setScenePcmSource(new ScenePcmSource() {
            @Override public void render(short[] target, int frames) { }
            @Override public void onHostPause() { order.add("physical.pause"); }
            @Override public boolean onHostResume() { order.add("physical.resume"); return true; }
        });
        IllegalStateException notificationFailure = new IllegalStateException("notification");
        AudioDiagnosticObserverException alreadyWrapped = new AudioDiagnosticObserverException(notificationFailure);
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override public void onLifecycle(LifecycleEvent event) {
                assertFalse(Thread.holdsLock(audio));
                order.add("diagnostic." + event.kind());
                if (event.kind() == LifecycleKind.PAUSE) throw notificationFailure;
                if (event.kind() == LifecycleKind.RESUME) throw alreadyWrapped;
            }
        });
        var wrapped = assertThrows(AudioDiagnosticObserverException.class, audio::pause);
        assertSame(notificationFailure, wrapped.getCause());
        assertArrayEquals(new Throwable[0], wrapped.getSuppressed());
        assertSame(alreadyWrapped, assertThrows(AudioDiagnosticObserverException.class, audio::resume));
        assertEquals(List.of("physical.pause", "diagnostic.PAUSE", "physical.resume", "diagnostic.RESUME"), order);
        audio.setDriverServiceObserver(null);
    }

    private static AudioManager.DiagnosticObserverSet diagnosticSet(ChipWriteObserver chip) {
        return new AudioManager.DiagnosticObserverSet((kind, id) -> { }, decision -> { },
                new SmpsDriverServiceObserver() { }, chip, new SfxContentionObserver() { });
    }

    private record FactoryInstallation(AudioPresentationSourceFactory receiver, String slot, Object observer) { }

    private static FactoryInstallation factoryCall(AudioPresentationSourceFactory receiver, String slot, Object observer) {
        return new FactoryInstallation(receiver, slot, observer);
    }

    /** Controlled propagation receivers only; real constructor/live effects have separate controls above. */
    private static MockedConstruction<AudioPresentationSourceFactory> observeFactoryInstallations(
            List<FactoryInstallation> calls, AtomicReference<Consumer<FactoryInstallation>> callback) {
        return mockConstruction(AudioPresentationSourceFactory.class, (factory, context) -> {
            doAnswer(invocation -> recordFactoryInstallation(calls, callback, factory, "admission", invocation.getArgument(0)))
                    .when(factory).setAdmissionObserver(any());
            doAnswer(invocation -> recordFactoryInstallation(calls, callback, factory, "driver", invocation.getArgument(0)))
                    .when(factory).setDriverServiceObserver(any());
            doAnswer(invocation -> recordFactoryInstallation(calls, callback, factory, "chip", invocation.getArgument(0)))
                    .when(factory).setChipWriteObserver(any());
            doAnswer(invocation -> recordFactoryInstallation(calls, callback, factory, "contention", invocation.getArgument(0)))
                    .when(factory).setSfxContentionObserver(any());
        });
    }

    private static Object recordFactoryInstallation(List<FactoryInstallation> calls,
            AtomicReference<Consumer<FactoryInstallation>> callback,
            AudioPresentationSourceFactory factory, String slot, Object observer) {
        var call = factoryCall(factory, slot, observer);
        calls.add(call);
        callback.get().accept(call);
        return null;
    }

    private static final class RecordingPresentationBackend extends NullAudioBackend {
        private int frames;
        private AudioAdmissionObserver admission;
        private SmpsDriverServiceObserver driver;
        private ChipWriteObserver chip;
        private SfxContentionObserver contention;
        private IllegalStateException failChip;

        @Override public void setAdmissionObserver(AudioAdmissionObserver observer) { admission = observer; }
        @Override public void setDriverServiceObserver(SmpsDriverServiceObserver observer) { driver = observer; }
        @Override public void setChipWriteObserver(ChipWriteObserver observer) {
            chip = observer;
            if (failChip != null) throw failChip;
        }
        @Override public void setSfxContentionObserver(SfxContentionObserver observer) { contention = observer; }
        @Override public AudioPresentationSink createPresentationSink(
                Consumer<Throwable> failureHandler, Consumer<String> warningHandler) {
            return new AudioPresentationSink() {
                @Override public int sampleRate() { return 48_000; }
                @Override public void accept(AudioPresentationFrameView frame) { frames++; }
                @Override public void onReverseBoundary() { }
                @Override public void close() { }
            };
        }
    }

    private static SmpsDriverServiceObserver throwingRestoreObserver(
            String message) {
        return new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                if (event.kind() == LifecycleKind.RESTORE) {
                    throw new IllegalStateException(message);
                }
            }
        };
    }

    private static void restoreTempoState(
            SmpsSequencer sequencer,
            int tempoWeight,
            int tempoAccumulator,
            int speedMultiplier,
            int speedupTimeout) {
        SmpsSequencerSnapshot state = sequencer.captureSnapshot();
        restoreDiagnosticState(sequencer, tempoWeight, tempoAccumulator,
                speedMultiplier, speedupTimeout, state.maxTicks(),
                state.fade());
    }

    private static void restoreDiagnosticState(
            SmpsSequencer sequencer,
            int tempoWeight,
            int tempoAccumulator,
            int speedMultiplier,
            int speedupTimeout,
            int maxTicks,
            SmpsSequencerSnapshot.FadeSnapshot fade) {
        SmpsSequencerSnapshot state = sequencer.captureSnapshot();
        sequencer.restoreSnapshot(new SmpsSequencerSnapshot(
                state.region(), state.speedShoes(), state.sfxMode(),
                state.normalTempo(), state.commData(), state.fm6DacOff(),
                maxTicks, state.pitch(), state.sfxPriority(),
                state.specialSfx(), state.sfx(), state.psgLatchChannel(),
                speedMultiplier, speedupTimeout, state.musicTempoPercent(), state.musicTempoPhase(), fade,
                state.sampleRate(), state.samplesPerFrame(), 0.0,
                tempoWeight, tempoAccumulator, state.dividingTiming(),
                state.primed(), state.tracks()));
    }

    private static SmpsDriverServiceObserver scopedServiceObserver(
            List<String> events,
            SmpsDriverServiceObserver.ServiceEvent[] active) {
        return new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                assertEquals(null, active[0], "services must not nest");
                active[0] = event;
                events.add("begin:" + event.kind() + ":" + event.ordinal());
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                assertSame(active[0], event);
                assertNotNull(snapshot);
                events.add("end:" + event.kind() + ":" + event.ordinal());
                active[0] = null;
            }
        };
    }

    private static void serviceOneTick(SmpsDriver driver, int soundId) {
        serviceOneTick(driver, soundId, null);
    }

    private static void serviceOneTick(
            SmpsDriver driver, int soundId, AudioStream stream) {
        SmpsSequencer sequencer = driver.sequencersForTesting().stream()
                .filter(candidate -> candidate.getSourceDescriptor().id()
                        == soundId)
                .findFirst().orElseThrow();
        sequencer.setSampleRate(60.0);
        restoreTempoState(sequencer, 10, 250, 1, 0);
        if (stream == null) {
            SmpsDriverTestAccess.read(driver, new short[2]);
        } else {
            stream.read(new short[2]);
        }
    }

    private GameAudioProfile profile(SmpsRequestAdmissionPolicy policy) {
        return new AudioTestFixtures.StubAudioProfile(loader) {
            @Override
            public SmpsSequencerConfig getSequencerConfig() {
                return new SmpsSequencerConfig.Builder().build();
            }

            @Override
            public int getExtraLifeMusicId() {
                return 0x82;
            }

            @Override
            public SmpsRequestAdmissionPolicy getSfxAdmissionPolicy() {
                return policy == null
                        ? super.getSfxAdmissionPolicy()
                        : policy;
            }
        };
    }

    private SmpsDriverSnapshot activeDriverSnapshot() {
        return audio.shadowSmpsDriverSnapshotForTesting();
    }

    private static List<Integer> sourceIds(SmpsDriverSnapshot snapshot) {
        return snapshot.sequencers().stream()
                .map(entry -> entry.source().id()).toList();
    }

    private void setPresentationSfxBlocked(boolean blocked) {
        AudioManagerTestDiagnostics.presentationField(audio, "shadowRegistry", AudioVoiceRegistry.class).setSfxBlocked(blocked);
    }

    private AudioPresentationSourceFactory presentationFactory() {
        return AudioManagerTestDiagnostics.presentationField(audio, "shadowFactory", AudioPresentationSourceFactory.class);
    }

    private AudioVoiceRegistry presentationRegistry() {
        return AudioManagerTestDiagnostics.presentationField(audio, "shadowRegistry", AudioVoiceRegistry.class);
    }

    private static void clearFactorySfxCache(
            AudioPresentationSourceFactory factory) {
        try {
            var catalogField = AudioPresentationSourceFactory.class
                    .getDeclaredField("assetCatalog");
            catalogField.setAccessible(true);
            Object catalog = catalogField.get(factory);
            var programsField = catalog.getClass()
                    .getDeclaredField("programs");
            programsField.setAccessible(true);
            ((Map<?, ?>) programsField.get(catalog)).clear();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static AbstractSmpsData data(String name, int id) {
        AbstractSmpsData data = new PersistentMusicData(name);
        data.setId(id);
        return data;
    }

    private SmpsSequencer sequencer(
            String name, int id, SmpsDriver driver) {
        return new SmpsSequencer(data(name, id),
                AudioTestFixtures.EMPTY_DAC, driver, audio,
                new SmpsSequencerConfig.Builder()
                        .fmChannelOrder(new int[] {2}).build());
    }

    private static ChipWriteObserver recordingChipObserver(
            List<String> events) {
        return new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                events.add("YM:%d:%02X:%02X".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
                events.add("PSG:%02X".formatted(value));
            }
        };
    }

    private static void exerciseWrites(SmpsDriver driver) {
        driver.writeFm(driver, 0, 0x22, 0x08);
        driver.writeFm(driver, 1, 0xB4, 0xC7);
        driver.writePsg(driver, 0x84);
        driver.writePsg(driver, 0x12);
        driver.writePsg(driver, 0x92);
    }

    private static List<String> expectedConstructorSilenceWrites() {
        List<String> expected = new ArrayList<>();
        expected.addAll(List.of(
                "YM:0:28:00", "YM:0:28:04",
                "YM:0:28:01", "YM:0:28:05",
                "YM:0:28:02", "YM:0:28:06"));
        for (int register = 0x30; register < 0x90; register++) {
            expected.add("YM:0:%02X:FF".formatted(register));
            expected.add("YM:1:%02X:FF".formatted(register));
        }
        expected.addAll(List.of("PSG:9F", "PSG:BF", "PSG:DF", "PSG:FF"));
        return expected;
    }

    private static String rootMessage(Throwable failure) {
        Throwable cursor = failure;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return String.valueOf(cursor.getMessage());
    }

    private static List<Long> lifecycleDriverOrdinals(
            List<String> events, String prefix) {
        return events.stream().filter(event -> event.startsWith(prefix))
                .map(event -> Long.parseLong(event.substring(prefix.length())))
                .toList();
    }

    private static void assertDriverStateEquals(
            SmpsDriverSnapshot expected, SmpsDriverSnapshot actual) {
        assertEquals(expected.region(), actual.region());
        assertEquals(expected.readMode(), actual.readMode());
        assertEquals(expected.continuousSfxId(), actual.continuousSfxId());
        assertEquals(expected.continuousSfxFlag(),
                actual.continuousSfxFlag());
        assertEquals(expected.contSfxLoopCnt(), actual.contSfxLoopCnt());
        assertArrayEquals(expected.fmLockSequencerIds(),
                actual.fmLockSequencerIds());
        assertArrayEquals(expected.psgLockSequencerIds(),
                actual.psgLockSequencerIds());
        assertEquals(expected.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source)
                        .toList(),
                actual.sequencers().stream()
                        .map(SmpsDriverSnapshot.SequencerEntry::source)
                        .toList());
        assertEquals(expected.sequencers().stream()
                        .map(entry -> JSON.valueToTree(entry.snapshot()))
                        .toList(),
                actual.sequencers().stream()
                        .map(entry -> JSON.valueToTree(entry.snapshot()))
                        .toList());
    }

    private static final class PersistentMusicData extends AbstractSmpsData {
        private final String name;

        private PersistentMusicData(String name) {
            super(new byte[] {0}, 0);
            this.name = name;
        }

        @Override
        protected void parseHeader() {
            channels = 1;
            tempo = 1;
            fmPointers = new int[] {0};
            fmKeyOffsets = new int[] {0};
            fmVolumeOffsets = new int[] {0};
        }

        @Override public byte[] getVoice(int voiceId) { return new byte[0]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
        @Override public String toString() { return name; }
    }

    private static final class OneTickFmSfxData extends AbstractSmpsData
            implements SmpsSfxData {
        private OneTickFmSfxData() {
            this(0xA0);
        }

        private OneTickFmSfxData(int id) {
            super(new byte[] {0, (byte) 0xF2}, 0);
            setId(id);
        }

        @Override
        public int getTickMultiplier() {
            return 1;
        }

        @Override
        public List<? extends SmpsSfxTrack> getTrackEntries() {
            return List.of(new SfxTrack(5, 1, 0, 0));
        }

        @Override protected void parseHeader() { dividingTiming = 1; tempo = 1; }
        @Override public byte[] getVoice(int voiceId) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return null; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }

    private static final class OneTickPsgSfxData extends AbstractSmpsData
            implements SmpsSfxData {
        private OneTickPsgSfxData(int id) {
            super(new byte[] {0, (byte) 0xF2}, 0);
            setId(id);
        }

        @Override public int getTickMultiplier() { return 1; }
        @Override public List<? extends SmpsSfxTrack> getTrackEntries() {
            return List.of(new SfxTrack(0x80, 1, 0, 0));
        }
        @Override protected void parseHeader() { dividingTiming = 1; tempo = 1; }
        @Override public byte[] getVoice(int voiceId) { return null; }
        @Override public byte[] getPsgEnvelope(int id) { return null; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }

    private static final class LongRunningFmSfxData extends AbstractSmpsData
            implements SmpsSfxData {
        private LongRunningFmSfxData() {
            super(new byte[] {0, (byte) 0x81, 0x7F, (byte) 0xF2}, 0);
            setId(0xA2);
        }

        @Override public int getTickMultiplier() { return 1; }
        @Override public List<? extends SmpsSfxTrack> getTrackEntries() {
            return List.of(new SfxTrack(5, 1, 0, 0));
        }
        @Override protected void parseHeader() { dividingTiming = 1; tempo = 1; }
        @Override public byte[] getVoice(int voiceId) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return null; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }

    private static final class LongRunningMusicData
            extends AbstractSmpsData {
        private LongRunningMusicData() {
            this(0x81);
        }

        private LongRunningMusicData(int id) {
            super(new byte[] {0, (byte) 0x81, 0x7F, (byte) 0xF2}, 0);
            setId(id);
        }

        @Override
        protected void parseHeader() {
            channels = 1;
            dividingTiming = 1;
            tempo = 1;
            fmPointers = new int[] {1};
            fmKeyOffsets = new int[] {0};
            fmVolumeOffsets = new int[] {0};
        }

        @Override public byte[] getVoice(int voiceId) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return null; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }

    private record SfxTrack(
            int channelMask, int pointer, int transpose, int volume)
            implements SmpsSfxData.SmpsSfxTrack {
    }
}
