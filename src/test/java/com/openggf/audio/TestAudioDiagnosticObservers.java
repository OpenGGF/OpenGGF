package com.openggf.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.driver.SmpsDriver;
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
import com.openggf.audio.presentation.AudioPresentationDependencyResolver;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcm;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SampleBackedVoice;
import com.openggf.audio.presentation.ResolvedSmpsSfxSource;
import com.openggf.audio.presentation.SmpsAssetKey;
import com.openggf.audio.presentation.SmpsSfxInstantiation;
import com.openggf.audio.presentation.SmpsCompositeVoice;
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
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
        firstDriverWrites.add("YM:0:2B:80");
        assertEquals(firstDriverWrites, chipWrites,
                "pre-construction installation must expose constructor"
                        + " silence before the committed sequencer's"
                        + " DAC-mode write without leaking blueprint writes");
        SmpsDriver base = activeDriver();
        assertNotNull(base);

        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(List.of("sfx:160", "decision:160:true"), events,
                "request admission is reported after driver attachment");

        audio.playMusic(0x82);
        audio.presentFrame(PresentationMode.SILENT);
        SmpsDriver override = activeDriver();
        assertNotNull(override);
        assertFalse(base == override);
        List<String> writesThroughOverride = new ArrayList<>(firstDriverWrites);
        writesThroughOverride.add("YM:0:2B:80");
        writesThroughOverride.addAll(expectedConstructorSilenceWrites());
        writesThroughOverride.add("YM:0:2B:80");
        assertEquals(writesThroughOverride, chipWrites,
                "each committed music driver contributes its exact"
                        + " constructor burst in stream order");
        serviceOneTick(override, 0x82);

        audio.restoreMusic();
        audio.presentFrame(PresentationMode.SILENT);
        assertSame(base, activeDriver(),
                "restore must reinstate the original observed driver");
        serviceOneTick(base, 0x81);

        assertEquals(List.of(0L, 1L), begins,
                "service ordinals are global across constructed drivers");
        assertEquals(begins, ends);
        assertTrue(lifecycle.stream().anyMatch(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.SAVE));
        assertTrue(lifecycle.stream().anyMatch(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.RESTORE));

        chipWrites.clear();
        exerciseWrites(base);
        assertEquals("YM:0:22:08", chipWrites.getFirst());
        assertTrue(chipWrites.contains("PSG:84"));
        assertTrue(chipWrites.contains("PSG:12"));
        assertTrue(chipWrites.contains("PSG:92"));
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
        assertEquals(List.of(0x81, 0xA0), activeDriver().captureSnapshot()
                .sequencers().stream().map(entry -> entry.source().id()).toList());
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
        assertEquals(1, activeDriver().captureSnapshot().sequencers().size());
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
            public SmpsCompositeVoice instantiateStandaloneCached(
                    ResolvedSmpsSfxSource source) {
                throw new IllegalStateException("asset evicted after resolve");
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
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                lateCacheMiss, factory,
                testCoordFlags, ignored -> { });
        ResolvedSmpsSfxSource resolved = new ResolvedSmpsSfxSource(
                700,
                new SmpsAssetKey("fixture", SmpsAssetKey.Route.BASE_ID,
                        0xA0, null),
                65_536, 0x60, 0, 1, 800);

        registry.apply(new AudioPresentationCommand.AddSmpsSfx(resolved));

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
        assertEquals(0, registry.orderedVoiceCount());

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
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, testCoordFlags, ignored -> { });

        registry.apply(new AudioPresentationCommand.AddSmpsSfx(source));

        assertEquals(1, evaluations.get());
        assertEquals(List.of(new AdmissionResult(false,
                RejectionReason.CACHE_MISS,
                0x31, 0x31, 0xE4)), decisions);
        assertEquals(0, registry.orderedVoiceCount());
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

        SmpsDriver driver = activeDriver();
        assertSame(SmpsDriverServiceObserver.NONE,
                driver.serviceObserver());
        assertSame(SfxContentionObserver.NONE,
                driver.sfxContentionObserver());
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
                SonicConfigurationService.getInstance(), null);
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

        serviceOneTick(base, 0x81);
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
        assertEquals(3, created.size());
        assertEquals(List.of(0xA0, 0x81, 0x82), created.stream()
                .map(event -> event.driver().origin().soundId()).toList());
        assertEquals(3, created.stream()
                .map(event -> event.driver().instanceOrdinal())
                .distinct().count());
        assertTrue(created.stream().allMatch(event -> event.scope()
                == SmpsDriverServiceObserver.LifecycleScope.DRIVER));
        assertTrue(lifecycle.stream().anyMatch(event -> event.kind()
                == SmpsDriverServiceObserver.LifecycleKind.SAVE
                && event.scope()
                == SmpsDriverServiceObserver.LifecycleScope.REGISTRY
                && event.source()
                == SmpsDriverServiceObserver.LifecycleSource.MUSIC_OVERRIDE));

        lifecycle.clear();
        audio.stopAllSfx();
        audio.presentFrame(PresentationMode.SILENT);
        List<LifecycleEvent> stopSfx = lifecycle.stream()
                .filter(event -> event.kind()
                        == SmpsDriverServiceObserver.LifecycleKind.STOP_ALL_SFX)
                .toList();
        assertEquals(3, stopSfx.size(),
                "both live music drivers and the standalone SFX driver"
                        + " mutate once without a registry"
                        + " event masquerading as a driver stop");
        assertEquals(3, stopSfx.stream().map(LifecycleEvent::driver)
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
        assertEquals(2, stopMusic.size());
        assertEquals(2, stopMusic.stream().map(LifecycleEvent::driver)
                .distinct().count());
        assertTrue(stopMusic.stream().allMatch(event -> event.source()
                == SmpsDriverServiceObserver.LifecycleSource.DRIVER_MUTATION));
    }

    @Test
    void preparedRestoreDefersCrossObserverEventsUntilCommitAndDiscardsThem() {
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        AudioPresentationSnapshot snapshot =
                audio.captureLogicalSnapshot().presentation();
        AudioPresentationSourceFactory factory = presentationFactory();
        List<String> events = new ArrayList<>();
        audio.setChipWriteObserver(recordingChipObserver(events));
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                events.add("LIFE:" + event.kind());
            }
        });
        AudioVoiceRegistry restoring = new AudioVoiceRegistry(
                factory, factory,
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState()), ignored -> { });

        AudioVoiceRegistry.PreparedSnapshotRestore discarded =
                restoring.prepareSnapshotRestore(snapshot, factory);
        assertEquals(List.of(), events,
                "prepare must not leak committed-looking constructor events");
        restoring.discardPreparedRestore(discarded);
        assertEquals(List.of(), events,
                "discard and its cleanup mutations remain invisible");

        AudioVoiceRegistry.PreparedSnapshotRestore committed =
                restoring.prepareSnapshotRestore(snapshot, factory);
        assertEquals(List.of(), events);
        restoring.commitPreparedRestore(committed);

        List<String> expected = expectedConstructorSilenceWrites();
        expected.add("LIFE:DRIVER_CREATED");
        expected.add("YM:0:2B:80");
        expected.add("LIFE:RESTORE");
        assertEquals(expected, events,
                "commit flushes one restored driver's constructor, restore"
                        + " write, and lifecycle events in original order");
    }

    @Test
    void failedPreparedRestoreDiscardsEventsFromAlreadyRecreatedDrivers() {
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        audio.playMusic(0x82);
        audio.presentFrame(PresentationMode.SILENT);
        AudioPresentationSnapshot snapshot =
                audio.captureLogicalSnapshot().presentation();
        AudioPresentationSourceFactory factory = presentationFactory();
        List<String> events = new ArrayList<>();
        audio.setChipWriteObserver(recordingChipObserver(events));
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onLifecycle(LifecycleEvent event) {
                events.add("LIFE:" + event.kind());
            }
        });
        AtomicInteger recreations = new AtomicInteger();
        AudioPresentationDependencyResolver failing =
                new AudioPresentationDependencyResolver() {
            @Override
            public DiagnosticTransaction beginDiagnosticTransaction() {
                return factory.beginDiagnosticTransaction();
            }

            @Override
            public com.openggf.audio.presentation.DecodedPcm resolvePcm(
                    String assetId) {
                return factory.resolvePcm(assetId);
            }

            @Override
            public SmpsCompositeVoice recreateSmps(
                    PresentationVoiceSnapshot.Smps voice) {
                if (recreations.getAndIncrement() == 1) {
                    throw new IllegalStateException("second restore failed");
                }
                return factory.recreateSmps(voice);
            }
        };
        AudioVoiceRegistry restoring = new AudioVoiceRegistry(
                factory, failing,
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState()), ignored -> { });

        assertThrows(IllegalStateException.class,
                () -> restoring.prepareSnapshotRestore(snapshot, failing));
        assertEquals(List.of(), events,
                "failed preparation discards the first reconstructed"
                        + " driver's complete diagnostic transaction");
    }

    @Test
    void serviceCallbacksMatchZeroOneAndMultipleActualSequencerTicks() {
        SmpsDriver driver = new SmpsDriver(600.0);
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

        driver.read(new short[0]);
        restoreTempoState(music, 10, 0, 1, 0);
        driver.read(new short[20]);
        assertEquals(List.of(), services,
                "a real tempo frame whose accumulator does not overflow"
                        + " executes zero SMPS ticks and reports no service");

        restoreTempoState(music, 10, 250, 1, 0);
        driver.read(new short[20]);
        assertEquals(List.of(
                "begin:SEQUENCER_TICK:0:129",
                "end:SEQUENCER_TICK:0:129"), services);

        services.clear();
        restoreTempoState(music, 10, 250, 3, 0);
        driver.read(new short[20]);
        assertEquals(List.of(
                "begin:SEQUENCER_TICK:1:129",
                "end:SEQUENCER_TICK:1:129",
                "begin:SEQUENCER_TICK:2:129",
                "end:SEQUENCER_TICK:2:129",
                "begin:SEQUENCER_TICK:3:129",
                "end:SEQUENCER_TICK:3:129"), services,
                "one OVERFLOW2 tempo frame that invokes tick three times"
                        + " emits three distinct ordered services");
    }

    @Test
    void speedupSubticksConsumeSfxBudgetOnlyOnTheFinalLiteralTick() {
        SmpsDriver driver = new SmpsDriver(60.0);
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

        driver.read(new short[2]);

        assertEquals(List.of(2, 2, 1), maxTicksAfterService,
                "speed-up adds literal sequencer ticks but the shipped SFX"
                        + " budget still decrements once per tempo frame,"
                        + " on its final tick");
    }

    @Test
    void fadeOnlyFrameIsOneTypedServiceAndNoFadeZeroTickIsNone() {
        SmpsDriver driver = new SmpsDriver(60.0);
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
        driver.setChipWriteObserver(new ChipWriteObserver() {
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
        driver.read(new short[2]);
        assertEquals(List.of(), events,
                "a zero-tick tempo frame with no fade has no service");

        restoreDiagnosticState(music, 10, 0, 1, 0,
                Integer.MAX_VALUE,
                new SmpsSequencerSnapshot.FadeSnapshot(
                        1, 0, 0, 1, 1, true, true));
        driver.read(new short[2]);

        assertEquals("begin:FADE_STEP:0", events.getFirst());
        assertEquals("end:FADE_STEP:0", events.getLast());
        assertTrue(events.subList(1, events.size() - 1).stream()
                .allMatch("write:FADE_STEP"::equals));
        assertTrue(events.size() > 2,
                "an actual fade volume step writes inside its service");
    }

    @Test
    void finalSfxTickAndCompletionCleanupBracketEveryWrite() {
        SmpsDriver driver = new SmpsDriver(60.0);
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
        driver.setChipWriteObserver(new ChipWriteObserver() {
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

        driver.read(new short[2]);

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
        SmpsDriver driver = new SmpsDriver(60.0);
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

        driver.read(new short[2]);

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
    void observerFailuresAbortAdmissionAndServiceCaptureLoudly() {
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
        RuntimeException admission = assertThrows(
                AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertTrue(rootMessage(admission).contains("admission capture failed"));
        SmpsDriver admissionDriver = activeDriver();
        assertEquals(1, admissionDriver.sequencersForTesting().size(),
                "a failed registry admission observer rolls the SFX back");
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(2, admissionDriver.sequencersForTesting().size(),
                "the queued command retries and commits exactly once");
        assertEquals(2, admissionAttempts.get());

        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceEnd(
                    long ordinal, SmpsDriverSnapshot snapshot) {
                throw new IllegalStateException("service capture failed");
            }
        });
        loader.musicResults.put(0x81, new LongRunningMusicData());
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);

        SmpsDriver observedDriver = activeDriver();
        RuntimeException service = assertThrows(
                AudioDiagnosticObserverException.class,
                () -> serviceOneTick(observedDriver, 0x81));
        assertTrue(rootMessage(service).contains("service capture failed"));
    }

    @Test
    void standaloneAdmissionObserverFailureRollsBackAndRetriesQueuedCommand() {
        AtomicInteger attempts = new AtomicInteger();
        audio.setAdmissionObserver(decision -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("standalone failed");
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(mock(Rom.class));

        audio.playSfx(0xA0);
        RuntimeException failure = assertThrows(
                AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertTrue(rootMessage(failure).contains("standalone failed"));
        assertEquals(0, presentationRegistry().orderedVoiceCount());

        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(1, presentationRegistry().orderedVoiceCount());
        assertEquals(2, attempts.get());
    }

    @Test
    void coordinationStartRollsBackAndContinuousRetryDoesNotStartAgain() {
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
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(() -> true, owner);
        AtomicInteger observerFailures = new AtomicInteger();
        factory.setAdmissionObserver(decision -> {
            if (observerFailures.getAndDecrement() > 0) {
                throw new IllegalStateException("decision failed");
            }
        });
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
                factory, factory, owner, ignored -> { });

        observerFailures.set(1);
        assertThrows(AudioDiagnosticObserverException.class,
                () -> registry.apply(
                        new AudioPresentationCommand.AddSmpsSfx(source)));
        assertEquals(0, state.spindashRevCounter());
        assertEquals(0, registry.orderedVoiceCount());

        registry.apply(new AudioPresentationCommand.AddSmpsSfx(source));
        assertEquals(1, state.spindashRevCounter());
        assertEquals(1, registry.orderedVoiceCount());

        observerFailures.set(1);
        assertThrows(AudioDiagnosticObserverException.class,
                () -> registry.apply(
                        new AudioPresentationCommand.AddSmpsSfx(source)));
        assertEquals(1, state.spindashRevCounter(),
                "continuous extension skips onSfxStart even when retried");
        registry.apply(new AudioPresentationCommand.AddSmpsSfx(source));
        assertEquals(1, state.spindashRevCounter());
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
        SmpsDriver driver = activeDriver();
        SmpsDriverSnapshot before = driver.captureSnapshot();
        failures.set(1);

        audio.playSfx(0xA1);
        assertThrows(AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertDriverStateEquals(before, driver.captureSnapshot());

        audio.setChipWriteObserver(null);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(2, driver.sequencersForTesting().size());
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
        SmpsDriver driver = activeDriver();
        SmpsSequencer firstSfx = driver.sequencersForTesting().getLast();
        firstSfx.writePsg(0x90);
        SmpsDriverSnapshot before = driver.captureSnapshot();
        failures.set(1);

        audio.playSfx(0xA1);
        assertThrows(AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertDriverStateEquals(before, driver.captureSnapshot());

        audio.setChipWriteObserver(null);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(2, driver.sequencersForTesting().size());
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
        SmpsDriver driver = activeDriver();
        SmpsDriverSnapshot before = driver.captureSnapshot();
        failures.set(1);

        audio.playSfx(0xA0);
        assertThrows(AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertDriverStateEquals(before, driver.captureSnapshot());

        audio.setSfxContentionObserver(null);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(2, driver.sequencersForTesting().size());
    }

    @Test
    void queuedDriverLifecycleFailurePublishesNoStandaloneVoiceAndRetries() {
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
        assertThrows(AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertEquals(0, presentationRegistry().orderedVoiceCount());

        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(1, presentationRegistry().orderedVoiceCount());
    }

    @Test
    void observerFailuresDuringConstructionEscapeShadowFallback() {
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
        RuntimeException failure = assertThrows(
                AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertTrue(rootMessage(failure).contains("chip capture failed"));
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
        assertEquals(List.of(1L), lifecycleDriverOrdinals(
                events, "LIFE:DRIVER_CREATED:"),
                "discarded staged drivers must not consume visible identity");
        assertEquals(List.of(1L), lifecycleDriverOrdinals(
                events, "LIFE:RESTORE:"));
        List<String> constructor = expectedConstructorSilenceWrites();
        int created = events.indexOf("LIFE:DRIVER_CREATED:1");
        assertTrue(created >= constructor.size());
        assertEquals(constructor,
                events.subList(created - constructor.size(), created),
                "the one committed reconstruction flushes its constructor"
                        + " writes once and in chip order");
        assertEquals("LIFE:DRIVER_CREATED:1",
                events.get(created));
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
        assertEquals(List.of(1L), lifecycle.stream()
                .filter(event -> event.kind() == LifecycleKind.DRIVER_CREATED)
                .map(event -> event.driver().instanceOrdinal()).toList());
        assertEquals(202, expectedConstructorSilenceWrites().size());
        assertEquals(expectedConstructorSilenceWrites(),
                chipWrites.subList(0, 202));
    }

    @Test
    void observerFailuresEscapeCacheRestoreReverseAndPcmBoundaries() {
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
        RuntimeException cacheFailure = assertThrows(
                AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertTrue(rootMessage(cacheFailure)
                .contains("cache chip capture failed"));

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
        RuntimeException restoreFailure = assertThrows(
                AudioDiagnosticObserverException.class,
                () -> audio.restoreLogicalSnapshot(restoreTarget));
        assertTrue(rootMessage(restoreFailure)
                .contains("restore lifecycle capture failed"));

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
        RuntimeException reverseFailure = assertThrows(
                AudioDiagnosticObserverException.class,
                audio::endReverseAudioPresentation);
        assertTrue(rootMessage(reverseFailure)
                .contains("reverse lifecycle capture failed"));

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
        RuntimeException pcmFailure = assertThrows(
                AudioDiagnosticObserverException.class,
                () -> registry.apply(
                        AudioPresentationCommand.ReplaceRawPcm.fromVoice(
                                pcmVoice)));
        assertTrue(rootMessage(pcmFailure)
                .contains("PCM lifecycle capture failed"));
        assertNotNull(registry.snapshot().rawPcmVoiceId(),
                "PCM callback follows the actual registry mutation");
    }

    @Test
    void noneObserversDoNotChangeSnapshotsWritesLocksOrConstructorSilence()
            throws Exception {
        SmpsDriver ordinary = new SmpsDriver();
        SmpsDriver inert = new SmpsDriver();
        inert.setChipWriteObserver(ChipWriteObserver.NONE);
        inert.setSfxContentionObserver(SfxContentionObserver.NONE);
        inert.setServiceObserver(SmpsDriverServiceObserver.NONE);

        List<String> ordinaryWrites = new ArrayList<>();
        List<String> inertWrites = new ArrayList<>();
        ordinary.setChipWriteObserver(recordingChipObserver(ordinaryWrites));
        inert.setChipWriteObserver(recordingChipObserver(inertWrites));
        exerciseWrites(ordinary);
        exerciseWrites(inert);

        assertEquals(ordinaryWrites, inertWrites);
        assertEquals(JSON.valueToTree(ordinary.captureSnapshot()),
                JSON.valueToTree(inert.captureSnapshot()));

        SmpsDriver defaultDriver = new SmpsDriver();
        SmpsDriver explicitNoneDriver = new SmpsDriver();
        explicitNoneDriver.setChipWriteObserver(ChipWriteObserver.NONE);
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
        defaultDriver.read(defaultPcm);
        explicitNoneDriver.read(explicitNonePcm);
        assertArrayEquals(defaultPcm, explicitNonePcm,
                "explicit NONE must preserve future PCM");
        assertDriverStateEquals(defaultDriver.captureSnapshot(),
                explicitNoneDriver.captureSnapshot());
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
                speedMultiplier, speedupTimeout, fade,
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
        SmpsSequencer sequencer = driver.sequencersForTesting().stream()
                .filter(candidate -> candidate.getSourceDescriptor().id()
                        == soundId)
                .findFirst().orElseThrow();
        sequencer.setSampleRate(60.0);
        restoreTempoState(sequencer, 10, 250, 1, 0);
        driver.read(new short[2]);
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

    private SmpsDriver activeDriver() {
        AudioPresentationSnapshot snapshot =
                audio.captureLogicalSnapshot().presentation();
        long active = snapshot.activeMusic().voiceId();
        PresentationVoiceSnapshot voice = snapshot.voices().stream()
                .filter(candidate -> voiceId(candidate) == active)
                .findFirst().orElseThrow();
        assertTrue(voice instanceof PresentationVoiceSnapshot.Smps);
        try {
            var field = AudioManager.class.getDeclaredField("shadowRegistry");
            field.setAccessible(true);
            AudioVoiceRegistry registry = (AudioVoiceRegistry) field.get(audio);
            for (int index = 0; index < registry.orderedVoiceCount(); index++) {
                if (registry.orderedVoiceAt(index).voiceId() == active) {
                    return ((SmpsCompositeVoice)
                            registry.orderedVoiceAt(index)).driver();
                }
            }
            throw new AssertionError("active SMPS driver not found");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static long voiceId(PresentationVoiceSnapshot snapshot) {
        return switch (snapshot) {
            case PresentationVoiceSnapshot.Smps smps -> smps.voiceId();
            case PresentationVoiceSnapshot.Sample sample -> sample.voiceId();
        };
    }

    private void setPresentationSfxBlocked(boolean blocked) {
        try {
            var field = AudioManager.class.getDeclaredField("shadowRegistry");
            field.setAccessible(true);
            ((com.openggf.audio.presentation.AudioVoiceRegistry) field.get(audio))
                    .setSfxBlocked(blocked);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private AudioPresentationSourceFactory presentationFactory() {
        try {
            var field = AudioManager.class.getDeclaredField("shadowFactory");
            field.setAccessible(true);
            return (AudioPresentationSourceFactory) field.get(audio);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private AudioVoiceRegistry presentationRegistry() {
        try {
            var field = AudioManager.class.getDeclaredField("shadowRegistry");
            field.setAccessible(true);
            return (AudioVoiceRegistry) field.get(audio);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
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
        assertEquals(JSON.valueToTree(expected.synthSnapshot()),
                JSON.valueToTree(actual.synthSnapshot()));
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
