package com.openggf.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.AdmissionResult;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.RejectionReason;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.SmpsAdmissionContext;
import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.configuration.SonicConfigurationService;
import java.util.ArrayList;
import java.util.List;
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
        List<String> events = new ArrayList<>();
        List<Long> begins = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        List<SmpsDriverServiceObserver.LifecycleKind> lifecycle =
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
            public void onServiceBegin(long ordinal) {
                begins.add(ordinal);
            }

            @Override
            public void onServiceEnd(
                    long ordinal, SmpsDriverSnapshot snapshot) {
                assertNotNull(snapshot);
                ends.add(ordinal);
            }

            @Override
            public void onLifecycle(LifecycleKind kind) {
                lifecycle.add(kind);
            }
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(null);

        audio.playMusic(0x81);
        assertEquals(List.of("YM:0:2B:80"), chipWrites,
                "the observer sees the sequencer's real DAC-mode mutation but"
                        + " not the driver's earlier constructor-silence burst");
        audio.presentFrame(PresentationMode.SILENT);
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
        audio.presentFrame(PresentationMode.FORWARD);

        audio.restoreMusic();
        audio.presentFrame(PresentationMode.SILENT);
        assertSame(base, activeDriver(),
                "restore must reinstate the original observed driver");
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(List.of(0L, 1L), begins,
                "service ordinals are global across constructed drivers");
        assertEquals(begins, ends);
        assertTrue(lifecycle.contains(
                SmpsDriverServiceObserver.LifecycleKind.SAVE));
        assertTrue(lifecycle.contains(
                SmpsDriverServiceObserver.LifecycleKind.RESTORE));

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
                        0x70, 0x70, context.resolvedSoundId());
            }
            return SmpsRequestAdmissionPolicy.PERMISSIVE.evaluate(context);
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
        audio.setRom(null);
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);

        audio.playSfx(0xA0);
        audio.playSfx(0xA1);
        audio.presentFrame(PresentationMode.SILENT);

        assertEquals(List.of(0xA0, 0xA1), contexts.stream()
                .map(SmpsAdmissionContext::resolvedSoundId).toList());
        assertEquals(List.of(true, false), decisions.stream()
                .map(AdmissionResult::accepted).toList());
        assertEquals(List.of(0xA0), contentionAdmissions,
                "a rejected whole request never reaches sequencer construction");
        assertEquals(List.of(0x81, 0xA0), activeDriver().captureSnapshot()
                .sequencers().stream().map(entry -> entry.source().id()).toList());
    }

    @Test
    void blockedPresentationSubmissionProducesOneRejectedDecision() {
        List<AdmissionResult> decisions = new ArrayList<>();
        audio.setAdmissionObserver(decision -> decisions.add(decision.result()));
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(null);
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        setPresentationSfxBlocked(true);

        audio.playSfx(0xA0);
        audio.presentFrame(PresentationMode.SILENT);

        assertEquals(1, decisions.size());
        assertFalse(decisions.getFirst().accepted());
        assertEquals(RejectionReason.BLOCKED,
                decisions.getFirst().reason());
        assertEquals(1, activeDriver().captureSnapshot().sequencers().size());
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
        audio.setRom(null);
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
        List<Long> serviceEnds = new ArrayList<>();
        List<SmpsDriverServiceObserver.LifecycleKind> lifecycle =
                new ArrayList<>();
        List<String> writes = new ArrayList<>();
        SmpsRequestAdmissionPolicy policy = context -> {
            contexts.add(context);
            return SmpsRequestAdmissionPolicy.PERMISSIVE.evaluate(context);
        };
        GameAudioProfile backendProfile = profile(policy);
        audio.setAudioProfile(backendProfile);
        audio.setRom(null);
        HeadlessSmpsAudioBackend backend = new HeadlessSmpsAudioBackend(
                SonicConfigurationService.getInstance(), null);
        backend.setAdmissionObserver(decision -> admitted.add(
                decision.result().resolvedSoundId()));
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
                    long ordinal, SmpsDriverSnapshot snapshot) {
                serviceEnds.add(ordinal);
            }

            @Override
            public void onLifecycle(LifecycleKind kind) {
                lifecycle.add(kind);
            }
        });
        backend.setAudioProfile(backendProfile);

        backend.playSmps(data("backend-base", 0x81),
                AudioTestFixtures.EMPTY_DAC,
                backendProfile.getSequencerConfig(), false);
        SmpsDriver base = backend.musicDriverForTesting();
        assertNotNull(base);
        assertTrue(lifecycle.contains(
                SmpsDriverServiceObserver.LifecycleKind.DRIVER_CREATED));
        assertEquals(List.of("YM:0:2B:80"), writes);

        backend.playSfxSmps(data("backend-sfx", 0xA0),
                AudioTestFixtures.EMPTY_DAC, 1.0f,
                backendProfile.getSequencerConfig());
        assertEquals(List.of(0xA0), contexts.stream()
                .map(SmpsAdmissionContext::resolvedSoundId).toList());
        assertEquals(List.of(0xA0, 0xA0), admitted,
                "contention admission precedes the completed request decision");

        base.read(new short[128]);
        assertEquals(List.of(0L), serviceEnds);

        backend.playSmps(data("backend-override", 0x82),
                AudioTestFixtures.EMPTY_DAC,
                backendProfile.getSequencerConfig(), true);
        assertFalse(base == backend.musicDriverForTesting());
        assertTrue(lifecycle.contains(
                SmpsDriverServiceObserver.LifecycleKind.SAVE));
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
        audio.setAdmissionObserver(decision -> {
            throw new IllegalStateException("admission capture failed");
        });
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(null);
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        audio.playSfx(0xA0);
        RuntimeException admission = assertThrows(RuntimeException.class,
                () -> audio.presentFrame(PresentationMode.SILENT));
        assertTrue(rootMessage(admission).contains("admission capture failed"));

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
        audio.setAudioProfile(profile(SmpsRequestAdmissionPolicy.PERMISSIVE));
        audio.setRom(null);
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);

        RuntimeException service = assertThrows(RuntimeException.class,
                () -> audio.presentFrame(PresentationMode.FORWARD));
        assertTrue(rootMessage(service).contains("service capture failed"));
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
        audio.setRom(null);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> audio.playMusic(0x81));
        assertTrue(rootMessage(failure).contains("chip capture failed"));
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

    private static String rootMessage(Throwable failure) {
        Throwable cursor = failure;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return String.valueOf(cursor.getMessage());
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
}
