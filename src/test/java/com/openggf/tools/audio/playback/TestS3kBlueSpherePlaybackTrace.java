package com.openggf.tools.audio.playback;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioAdmissionObserver;
import com.openggf.audio.GameMusic;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.YmServiceTimingProfile.SegmentKind;
import com.openggf.audio.synth.YmWriteTimeline;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBlueSpherePlaybackTrace {
    private static final int CAPTURE_FRAMES = 20;
    private static final List<ExpectedYmWrite> BLUE_SPHERE_FIRST_ATTACK = List.of(
            w(1, 0x81, 0xFF), w(1, 0x85, 0xFF), w(1, 0x89, 0xFF),
            w(1, 0x8D, 0xFF), w(1, 0xB5, 0xC0), w(1, 0xB1, 0x05),
            w(1, 0x31, 0x07), w(1, 0x39, 0x12), w(1, 0x35, 0x22),
            w(1, 0x3D, 0x32), w(1, 0x51, 0x0A), w(1, 0x59, 0x0F),
            w(1, 0x55, 0x0F), w(1, 0x5D, 0x0F), w(1, 0x61, 0x00),
            w(1, 0x69, 0x00), w(1, 0x65, 0x00), w(1, 0x6D, 0x00),
            w(1, 0x71, 0x00), w(1, 0x79, 0x10), w(1, 0x75, 0x10),
            w(1, 0x7D, 0x10), w(1, 0x81, 0x0F), w(1, 0x89, 0x0F),
            w(1, 0x85, 0x0F), w(1, 0x8D, 0x0F), w(1, 0x41, 0x21),
            w(1, 0x49, 0x05), w(1, 0x45, 0x05), w(1, 0x4D, 0x05),
            w(0, 0x28, 0x05), w(1, 0xA5, 0x23), w(1, 0xA1, 0x3F),
            w(0, 0x28, 0xF5));
    private static final List<AudioPlaybackTraceEvent>
            REVIEWED_COMMITTED_SPRING_PREDECESSOR = List.of(
            new AudioPlaybackTraceEvent.Ym2612Write(0, 0x28, 0x05),
            new AudioPlaybackTraceEvent.Ym2612Write(1, 0x91, 0x00),
            new AudioPlaybackTraceEvent.Ym2612Write(1, 0x99, 0x00),
            new AudioPlaybackTraceEvent.Ym2612Write(1, 0x95, 0x00),
            new AudioPlaybackTraceEvent.Ym2612Write(1, 0x9D, 0x00),
            new AudioPlaybackTraceEvent.PsgWrite(0x99),
            new AudioPlaybackTraceEvent.PsgWrite(0xB9),
            new AudioPlaybackTraceEvent.PsgWrite(0xFF),
            new AudioPlaybackTraceEvent.PsgWrite(0x9F),
            new AudioPlaybackTraceEvent.PsgWrite(0xBF),
            new AudioPlaybackTraceEvent.PsgWrite(0xC0),
            new AudioPlaybackTraceEvent.PsgWrite(0x00),
            new AudioPlaybackTraceEvent.PsgWrite(0xF6));
    private static final List<AudioPlaybackTraceEvent>
            REVIEWED_DRAINED_BLUE_ATTACK = List.of(
            yw(1, 0x81, 0xFF), yw(1, 0x85, 0xFF), yw(1, 0x89, 0xFF),
            yw(1, 0x8D, 0xFF), yw(1, 0xB5, 0xC0), yw(1, 0xB1, 0x05),
            yw(1, 0x31, 0x07), yw(1, 0x39, 0x12), yw(1, 0x35, 0x22),
            yw(1, 0x3D, 0x32), yw(1, 0x51, 0x0A), yw(1, 0x59, 0x0F),
            yw(1, 0x55, 0x0F), yw(1, 0x5D, 0x0F), yw(1, 0x61, 0x00),
            yw(1, 0x69, 0x00), yw(1, 0x65, 0x00), yw(1, 0x6D, 0x00),
            yw(1, 0x71, 0x00), yw(1, 0x79, 0x10), yw(1, 0x75, 0x10),
            yw(1, 0x7D, 0x10), yw(1, 0x81, 0x0F), yw(1, 0x89, 0x0F),
            yw(1, 0x85, 0x0F), yw(1, 0x8D, 0x0F), yw(1, 0x41, 0x21),
            yw(1, 0x49, 0x05), yw(1, 0x45, 0x05), yw(1, 0x4D, 0x05),
            yw(0, 0x28, 0x05), yw(1, 0xA5, 0x23), yw(1, 0xA1, 0x3F),
            new AudioPlaybackTraceEvent.Ym2612KeyOn(4, 0, 1023),
            new AudioPlaybackTraceEvent.Ym2612KeyOn(4, 1, 1023),
            new AudioPlaybackTraceEvent.Ym2612KeyOn(4, 2, 1023),
            new AudioPlaybackTraceEvent.Ym2612KeyOn(4, 3, 1023),
            yw(0, 0x28, 0xF5));

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
        audio.setDriverServiceObserver(null);
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
        AtomicReference<SmpsDriverSnapshot> blueService =
                new AtomicReference<>();
        List<SmpsDriverServiceObserver.ServiceEvent> observedServices =
                new ArrayList<>();
        audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                observedServices.add(event);
                if (event.kind() == ServiceKind.SEQUENCER_TICK
                        && event.sequencer().sfx()
                        && event.sequencer().source().kind()
                                == SmpsSourceDescriptor.Kind.BASE_SFX_ID
                        && event.sequencer().source().id()
                                == Sonic3kSfx.BLUE_SPHERE.id) {
                    blueService.compareAndSet(null, snapshot);
                }
            }
        });
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

        List<AudioPlaybackTraceEvent> replacementEvents =
                chipTrace.snapshot().eventsAfter(marker);
        assertTrue(isReviewedDrainedReplacement(replacementEvents),
                () -> "drained replacement differs from the reviewed Spring "
                        + "tail and Blue Sphere attack: " + replacementEvents);
        List<Integer> levels = fm5CarrierLevels(replacementEvents);
        assertTrue(levels.size() >= 3, () -> "missing Blue Sphere voice: " + levels);
        assertTrue(containsSubsequence(levels, List.of(5, 5, 5)),
                () -> "retail shared-SFX overwrite must reach the Blue Sphere voice after "
                        + "draining any already-committed predecessor writes: "
                        + levels);
        SmpsDriverSnapshot serviceSnapshot = blueService.get();
        assertNotNull(serviceSnapshot,
                () -> "missing Blue Sphere service snapshot: "
                        + observedServices);
        List<YmWriteTimeline.Entry> pending = serviceSnapshot.synthSnapshot()
                .ymWriteTimeline().pending();
        assertTrue(isReviewedBlueSphereReplacement(pending),
                () -> "replacement timeline contains a music restore or differs "
                        + "from the reviewed Blue Sphere source/segment sequence: "
                        + pending);
    }

    @Test
    void reviewedReplacementRejectsMusicVoiceProgrammingBeforeBlueSource() {
        List<YmWriteTimeline.Entry> pending = new ArrayList<>();
        pending.add(timelineEntry(0, SmpsSourceDescriptor.Kind.BASE_MUSIC,
                7, null, 1, 0x41, 0x18));
        for (int index = 0; index < BLUE_SPHERE_FIRST_ATTACK.size(); index++) {
            ExpectedYmWrite write = BLUE_SPHERE_FIRST_ATTACK.get(index);
            pending.add(timelineEntry(index + 1,
                    SmpsSourceDescriptor.Kind.BASE_SFX_ID,
                    Sonic3kSfx.BLUE_SPHERE.id,
                    expectedSegment(index), write.port(), write.register(),
                    write.value()));
        }

        assertFalse(isReviewedBlueSphereReplacement(pending),
                "a raw-value subsequence must not hide a music voice restore");
    }

    @Test
    void reviewedReplacementRejectsMusicB5BeforeBlueUpload() {
        List<YmWriteTimeline.Entry> pending = reviewedBlueTimeline(1);
        pending.addFirst(timelineEntry(0,
                SmpsSourceDescriptor.Kind.BASE_MUSIC, 7,
                SegmentKind.COMPLETION_RESTORE, 1, 0xB5, 0xC0));

        assertFalse(isReviewedBlueSphereReplacement(pending),
                "music B5 restore must not hide before the Blue upload");
    }

    @Test
    void reviewedReplacementRejectsOtherCompletionRestorePrefix() {
        List<YmWriteTimeline.Entry> pending = reviewedBlueTimeline(2);
        pending.addFirst(timelineEntry(1,
                SmpsSourceDescriptor.Kind.BASE_MUSIC, 7,
                SegmentKind.COMPLETION_RESTORE, 0, 0x28, 0x05));
        pending.addFirst(timelineEntry(0,
                SmpsSourceDescriptor.Kind.BASE_MUSIC, 7,
                SegmentKind.COMPLETION_RESTORE, 1, 0xB1, 0x04));

        assertFalse(isReviewedBlueSphereReplacement(pending),
                "no completion-restore prefix may precede the Blue upload");
    }

    @Test
    void reviewedDrainedReplacementRejectsInterveningNonmusicEvent() {
        List<AudioPlaybackTraceEvent> events = new ArrayList<>(
                REVIEWED_DRAINED_BLUE_ATTACK.subList(0, 10));
        events.add(new AudioPlaybackTraceEvent.PsgWrite(0x80));
        events.addAll(REVIEWED_DRAINED_BLUE_ATTACK.subList(
                10, REVIEWED_DRAINED_BLUE_ATTACK.size()));

        assertFalse(isReviewedDrainedReplacement(events),
                "an unrelated event must not hide inside the Blue attack");
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

    private static boolean isReviewedBlueSphereReplacement(
            List<YmWriteTimeline.Entry> pending) {
        if (pending.size() != BLUE_SPHERE_FIRST_ATTACK.size()
                || pending.stream().anyMatch(entry ->
                entry.sourceDescriptor().kind()
                                == SmpsSourceDescriptor.Kind.BASE_MUSIC
                        || entry.segment() == SegmentKind.COMPLETION_RESTORE)) {
            return false;
        }
        List<YmWriteTimeline.Entry> blue = pending.stream()
                .filter(entry -> entry.sourceDescriptor().kind()
                        == SmpsSourceDescriptor.Kind.BASE_SFX_ID)
                .filter(entry -> entry.sourceDescriptor().id()
                        == Sonic3kSfx.BLUE_SPHERE.id)
                .toList();
        if (blue.size() != BLUE_SPHERE_FIRST_ATTACK.size()) {
            return false;
        }
        long serviceOrdinal = blue.getFirst().serviceOrdinal();
        for (int index = 0; index < blue.size(); index++) {
            YmWriteTimeline.Entry entry = blue.get(index);
            ExpectedYmWrite expected = BLUE_SPHERE_FIRST_ATTACK.get(index);
            if (entry.serviceOrdinal() != serviceOrdinal
                    || entry.segment() != expectedSegment(index)
                    || entry.port() != expected.port()
                    || entry.register() != expected.register()
                    || entry.value() != expected.value()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isReviewedDrainedReplacement(
            List<AudioPlaybackTraceEvent> events) {
        int reviewedSize = REVIEWED_DRAINED_BLUE_ATTACK.size();
        for (int start = 0; start + reviewedSize <= events.size(); start++) {
            if (events.subList(start, start + reviewedSize).equals(
                    REVIEWED_DRAINED_BLUE_ATTACK)) {
                return true;
            }
        }
        return false;
    }

    private static YmWriteTimeline.Entry timelineEntry(
            long ordinal, SmpsSourceDescriptor.Kind kind, int id,
            SegmentKind segment, int port, int register, int value) {
        SmpsSourceDescriptor source = new SmpsSourceDescriptor(
                kind, id, null, null, 0, 1, id, false, 0);
        return new YmWriteTimeline.Entry(ordinal, ordinal, port, register,
                value, 1, kind == SmpsSourceDescriptor.Kind.BASE_MUSIC ? 1 : 2,
                source, segment);
    }

    private static List<YmWriteTimeline.Entry> reviewedBlueTimeline(
            long firstOrdinal) {
        List<YmWriteTimeline.Entry> pending = new ArrayList<>();
        for (int index = 0; index < BLUE_SPHERE_FIRST_ATTACK.size(); index++) {
            ExpectedYmWrite write = BLUE_SPHERE_FIRST_ATTACK.get(index);
            pending.add(timelineEntry(firstOrdinal + index,
                    SmpsSourceDescriptor.Kind.BASE_SFX_ID,
                    Sonic3kSfx.BLUE_SPHERE.id, expectedSegment(index),
                    write.port(), write.register(), write.value()));
        }
        return pending;
    }

    private static SegmentKind expectedSegment(int index) {
        if (index < 4) {
            return SegmentKind.SFX_MAX_RELEASE;
        }
        if (index < 30) {
            return SegmentKind.FM_VOICE_UPLOAD;
        }
        if (index == 30) {
            return SegmentKind.KEY_OFF;
        }
        return SegmentKind.FREQUENCY_AND_KEY_ON;
    }

    private static ExpectedYmWrite w(int port, int register, int value) {
        return new ExpectedYmWrite(port, register, value);
    }

    private static AudioPlaybackTraceEvent.Ym2612Write yw(
            int port, int register, int value) {
        return new AudioPlaybackTraceEvent.Ym2612Write(
                port, register, value);
    }

    private record ExpectedYmWrite(int port, int register, int value) {
    }
}
