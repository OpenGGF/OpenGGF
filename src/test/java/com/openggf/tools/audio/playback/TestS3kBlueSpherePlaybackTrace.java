package com.openggf.tools.audio.playback;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioAdmissionObserver;
import com.openggf.audio.GameMusic;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBlueSpherePlaybackTrace {
    private static final int CAPTURE_FRAMES = 20;

    private AudioManager audio;
    private LiveCaptureAudioHandle capture;
    private final List<AudioAdmissionObserver.AudioAdmissionDecision> admissions =
            new ArrayList<>();

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        SonicConfigurationService config =
                SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @AfterEach
    void tearDown() {
        audio.setChipWriteObserver(null);
        audio.setAdmissionObserver(null);
        if (capture != null) {
            capture.close();
        }
        audio.resetState();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void rapidRetriggersKeepTheRomCarrierAttenuationAcrossServicePhases()
            throws Exception {
        BoundedAudioPlaybackTrace chipTrace =
                new BoundedAudioPlaybackTrace(100_000, 1);
        audio.setChipWriteObserver(chipTrace);
        audio.setAdmissionObserver(admissions::add);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build();
        SpecialStageProvider stage =
                GameServices.module().getSpecialStageProvider();
        assertNotNull(stage);
        stage.initializeStage(0);
        assertTrue(audio.playMusic(GameMusic.SPECIAL_STAGE));

        capture = AudioManagerTestDiagnostics.attachPresentationCapture(
                audio, audio.presentationFrameRate());
        drainFrames(fixture, null, 30);

        List<AudioPlaybackTraceSnapshot.PcmSummary> summaries =
                new ArrayList<>();
        for (int retriggerDelay : List.of(1, 2, 4, 8, 12)) {
            audio.stopAllSfx();
            drainFrames(fixture, null, 2);
            assertTrue(audio.playSfx(Sonic3kSfx.BLUE_SPHERE.id));
            drainFrames(fixture, null, retriggerDelay);

            BoundedAudioPlaybackTrace pcmTrace =
                    new BoundedAudioPlaybackTrace(20_000, 20_000);
            String marker = "blue-sphere-retrigger-" + retriggerDelay;
            chipTrace.mark(marker);
            assertTrue(audio.playSfx(Sonic3kSfx.BLUE_SPHERE.id));
            drainFrames(fixture, pcmTrace, CAPTURE_FRAMES);

            List<Integer> levels = fm5CarrierLevels(
                    chipTrace.snapshot().eventsAfter(marker));
            assertTrue(levels.size() >= 6,
                    () -> "missing FM5 carrier writes at delay "
                            + retriggerDelay + ": " + levels
                            + "; admissions=" + admissions);
            assertTrue(containsSubsequence(levels,
                            List.of(5, 5, 5, 10, 10, 10)),
                    () -> "Blue Sphere attenuation differs at retrigger delay "
                            + retriggerDelay + ": " + levels);
            assertFalse(levels.contains(0x7F),
                    () -> "synthetic maximum attenuation leaked at delay "
                            + retriggerDelay + ": " + levels);
            summaries.add(pcmTrace.snapshot().pcmSummary());
        }

        assertTrue(summaries.stream().allMatch(summary ->
                        summary.leftPeak() > 0 && summary.rightPeak() > 0),
                () -> "every pickup must reach both final PCM channels: "
                        + summaries);
    }

    @Test
    void replacingAnotherFm5SfxDoesNotRestoreMusicBeforeBlueSphereVoice()
            throws Exception {
        BoundedAudioPlaybackTrace chipTrace =
                new BoundedAudioPlaybackTrace(100_000, 1);
        audio.setChipWriteObserver(chipTrace);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build();
        SpecialStageProvider stage =
                GameServices.module().getSpecialStageProvider();
        assertNotNull(stage);
        stage.initializeStage(0);
        assertTrue(audio.playMusic(GameMusic.SPECIAL_STAGE));
        capture = AudioManagerTestDiagnostics.attachPresentationCapture(
                audio, audio.presentationFrameRate());
        drainFrames(fixture, null, 30);

        assertTrue(audio.playSfx(Sonic3kSfx.SPRING.id));
        drainFrames(fixture, null, 4);
        String marker = "spring-to-blue-sphere";
        chipTrace.mark(marker);
        assertTrue(audio.playSfx(Sonic3kSfx.BLUE_SPHERE.id));
        drainFrames(fixture, null, 4);

        List<Integer> levels = fm5CarrierLevels(
                chipTrace.snapshot().eventsAfter(marker));
        assertTrue(levels.size() >= 3, () -> "missing Blue Sphere voice: " + levels);
        assertTrue(containsSubsequence(levels, List.of(5, 5, 5)),
                () -> "retail shared-SFX overwrite must reach the Blue Sphere voice after "
                        + "draining any already-committed predecessor writes: "
                        + levels);
    }

    @Test
    void completedBlueSphereDoesNotChangeItsLaterPlaybackAtTheSameMusicPhase()
            throws Exception {
        AudioPlaybackTraceSnapshot fresh = captureAtCommonMusicPhase(false);
        AudioPlaybackTraceSnapshot afterCompleted = captureAtCommonMusicPhase(true);

        assertEquals(fm5Writes(fresh.events()), fm5Writes(afterCompleted.events()),
                "a completed pickup must not alter the later pickup's FM5 program");
        AudioPlaybackTraceComparator.Result comparison =
                AudioPlaybackTraceComparator.compare(fresh, afterCompleted);
        assertTrue(comparison.matches(), () ->
                "a completed pickup leaked history into later audible playback: "
                        + comparison.description()
                        + "; fresh=" + fresh.pcmSummary()
                        + "; replay=" + afterCompleted.pcmSummary());

        List<Integer> levels = fm5CarrierLevels(afterCompleted.events());
        assertTrue(levels.size() >= 6,
                () -> "missing completed-replay carrier writes: " + levels
                        + "; events=" + afterCompleted.events().size()
                        + "; first=" + afterCompleted.events().stream()
                                .limit(20).toList());
        assertEquals(List.of(5, 5, 5, 10, 10, 10),
                levels.subList(0, 6),
                () -> "completed Blue Sphere must restart from its ROM TL sequence: "
                        + levels);
    }

    private AudioPlaybackTraceSnapshot captureAtCommonMusicPhase(
            boolean playEarlierPickup) throws Exception {
        audio.setChipWriteObserver(null);
        if (capture != null) {
            capture.close();
            capture = null;
        }
        audio.resetState();

        BoundedAudioPlaybackTrace trace =
                new BoundedAudioPlaybackTrace(100_000, 30_000);
        audio.setChipWriteObserver(trace);

        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build();
        SpecialStageProvider stage =
                GameServices.module().getSpecialStageProvider();
        assertNotNull(stage);
        stage.initializeStage(0);
        assertTrue(audio.playMusic(GameMusic.SPECIAL_STAGE));
        capture = AudioManagerTestDiagnostics.attachPresentationCapture(
                audio, audio.presentationFrameRate());
        drainFrames(fixture, null, 30);
        if (playEarlierPickup) {
            assertTrue(audio.playSfx(Sonic3kSfx.BLUE_SPHERE.id));
        }
        drainFrames(fixture, null, 100);

        String marker = "target-blue-sphere";
        trace.mark(marker);
        assertTrue(audio.playSfx(Sonic3kSfx.BLUE_SPHERE.id));
        drainFrames(fixture, trace, 30);
        audio.setChipWriteObserver(null);
        AudioPlaybackTraceSnapshot complete = trace.snapshot();
        return new AudioPlaybackTraceSnapshot(
                complete.eventsAfter(marker), complete.pcm());
    }

    private static List<AudioPlaybackTraceEvent.Ym2612Write> fm5Writes(
            List<AudioPlaybackTraceEvent> events) {
        return events.stream()
                .filter(AudioPlaybackTraceEvent.Ym2612Write.class::isInstance)
                .map(AudioPlaybackTraceEvent.Ym2612Write.class::cast)
                .filter(write -> (write.port() == 1
                        && write.register() >= 0x30
                        && write.register() <= 0xB6
                        && (write.register() & 0x03) == 1)
                        || (write.port() == 0
                        && write.register() == 0x28
                        && (write.value() & 0x07) == 5))
                .toList();
    }

    private void drainFrames(
            HeadlessTestFixture fixture,
            BoundedAudioPlaybackTrace trace,
            int frames) {
        short[] packet =
                new short[capture.maxStereoFramesPerPacket() * 2];
        for (int frame = 0; frame < frames; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            int stereoFrames = capture.drainPresentationFrame(packet);
            if (trace != null) {
                trace.recordPcm(packet, stereoFrames);
            }
        }
    }

    private static List<Integer> fm5CarrierLevels(
            List<AudioPlaybackTraceEvent> events) {
        return events.stream()
                .filter(AudioPlaybackTraceEvent.Ym2612Write.class::isInstance)
                .map(AudioPlaybackTraceEvent.Ym2612Write.class::cast)
                .filter(write -> write.port() == 1
                        && (write.register() == 0x49
                        || write.register() == 0x45
                        || write.register() == 0x4D))
                .map(AudioPlaybackTraceEvent.Ym2612Write::value)
                .toList();
    }

    private static boolean containsSubsequence(
            List<Integer> values, List<Integer> expected) {
        for (int start = 0; start + expected.size() <= values.size(); start++) {
            if (values.subList(start, start + expected.size()).equals(expected)) {
                return true;
            }
        }
        return false;
    }
}
