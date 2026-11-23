package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.AudioPresentationForwardService;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.AppliedOutcome;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.OutcomeReservation;
import com.openggf.audio.presentation.AudioRequestService;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService.Decision;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService.DecisionReason;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.BlueBallsObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RenderFlagPassTestSupport;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2RequestConsequencesRom {
    private Rom rom;

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
        if (rom != null) {
            rom.close();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(Sonic2Music.class)
    void musicMailboxUsesNativeByteAndRetainsPlaybackIdentity(Sonic2Music music) {
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);

        audio.playMusic(music.id);
        for (int frame = 0; frame < 4; frame++) {
            assertDoesNotThrow(() -> audio.presentFrame(PresentationMode.FORWARD));
        }

        assertEquals(List.of(music.nativeId), events.stream()
                .filter(Sonic2SoundRequestService.Submission.class::isInstance)
                .map(Sonic2SoundRequestService.Submission.class::cast)
                .map(Sonic2SoundRequestService.Submission::rawRequestId).toList());
        AudioCommand.PlayMusic command = (AudioCommand.PlayMusic)
                audio.commandTimeline().entries().getFirst().command();
        assertEquals(music.id, command.musicId(),
                "native mailbox translation must not change the resolved song");
    }

    @Test
    void chemicalPlantBlueBallsSubmitEveryVisibleGloopAndPlayOnOddDispatches() {
        ObjectServices services = TestEnvironment.objectServices();
        BlueBallsObjectInstance.resetGlobalState();
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);
        List<Integer> playedOn = new ArrayList<>();

        // Obj1D_MoveStraight (s2.asm:48429-48440) calls PlaySoundLocal (s2.asm:1555-1562).
        // zPlaySound_CheckGloop (s2.sounddriver.asm:2138-2149) complements the initially
        // clear zGloopFlag (s2.sounddriver.asm:4092) and plays when it becomes nonzero.
        for (int event = 1; event <= 4; event++) {
            int previousCommands = audio.commandTimeline().entryCount();
            triggerBlueBallGloop(services, 0, 0, 320);
            audio.presentFrame(PresentationMode.FORWARD);
            if (audio.commandTimeline().entryCount() > previousCommands) {
                AudioCommand.PlaySfx command = (AudioCommand.PlaySfx)
                        audio.commandTimeline().entries().getLast().command();
                assertEquals(0xDA, command.sfxId());
                playedOn.add(event);
            }
        }

        assertEquals(List.of(0xDA, 0xDA, 0xDA, 0xDA), submittedSounds(events),
                "each on-screen Obj1D event writes its raw gloop request");
        assertEquals(List.of(1, 3), playedOn);
    }

    @Test
    void offScreenAndOverwrittenBlueBallEventsDoNotAdvanceGloopPhase() {
        ObjectServices services = TestEnvironment.objectServices();
        BlueBallsObjectInstance.resetGlobalState();
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);

        triggerBlueBallGloop(services, -168, -168, 320);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(List.of(), submittedSounds(events));
        assertEquals(0, requestSnapshot(audio).pipeline().gloopFlag());

        triggerBlueBallGloop(services, 0, 0, 320);
        assertTrue(audio.playSfx(0xA0)); // A later SFX0 write wins the same frame.
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(List.of(0xDA, 0xA0), submittedSounds(events));
        assertEquals(0, requestSnapshot(audio).pipeline().gloopFlag());

        triggerBlueBallGloop(services, 0, 0, 320);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(0xFF, requestSnapshot(audio).pipeline().gloopFlag());
        assertEquals(List.of(0xA0, 0xDA), audio.commandTimeline().entries().stream()
                .map(entry -> ((AudioCommand.PlaySfx) entry.command()).sfxId()).toList());
    }

    @Test
    void blueBallSoundUsesPreviousSpritePassAtTheScreenEdge() {
        ObjectServices services = TestEnvironment.objectServices();
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);

        // The centre at x=160 was 7 px past the right edge. BuildSprites
        // admits it with Obj1D's width_pixels of 8, even if the camera moves.
        triggerBlueBallGloop(services, -167, -180, 320);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(List.of(0xDA), submittedSounds(events));
        assertEquals(0xFF, requestSnapshot(audio).pipeline().gloopFlag());

        // At exactly 8 px past the right edge the retained bit is clear.
        // Moving the camera onto the ball cannot retroactively admit the sound.
        triggerBlueBallGloop(services, -168, -152, 320);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(List.of(0xDA), submittedSounds(events));
        assertEquals(0xFF, requestSnapshot(audio).pipeline().gloopFlag());

        triggerBlueBallGloop(services, -247, -260, 400);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(List.of(0xDA, 0xDA), submittedSounds(events));
        assertEquals(0, requestSnapshot(audio).pipeline().gloopFlag());
    }

    @Test
    void blueBallInitDoesNotSetTheLocalSoundGate() {
        ObjectServices services = TestEnvironment.objectServices();
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        BlueBallsObjectInstance ball = newBlueBall(services);
        try {
            ball.update(1, null);
            // The ordinary engine pass visits the object even though Obj1D_Init
            // returned without reaching MarkObjGone / DisplaySprite.
            RenderFlagPassTestSupport.pass(ball, false);
            ball.update(2, null);
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(List.of(), submittedSounds(events));
        } finally {
            ball.onUnload();
        }
    }

    private static BlueBallsObjectInstance newBlueBall(ObjectServices services) {
        ObjectSpawn spawn = new ObjectSpawn(64, 112, Sonic2ObjectIds.BLUE_BALLS,
                0x10, 0, false, 0);
        BlueBallsObjectInstance ball = new BlueBallsObjectInstance(spawn, "BlueBalls");
        ball.setServices(services);
        return ball;
    }

    private static void triggerBlueBallGloop(ObjectServices services, int renderCameraX,
                                             int updateCameraX, int width) {
        BlueBallsObjectInstance ball = newBlueBall(services);
        try {
            // Begin outside the vertical render band, then establish the previous
            // BuildSprites result immediately before the $180 velocity event.
            AbstractObjectInstance.updateCameraBounds(0, 256, width, 480, 0);
            ball.update(1, null);
            RenderFlagPassTestSupport.pass(ball, false);
            ball.update(2, null);
            for (int step = 1; step < 64; step++) {
                ball.update(2 + step, null);
            }
            AbstractObjectInstance.updateCameraBounds(renderCameraX, 0, renderCameraX + width, 224, 0);
            RenderFlagPassTestSupport.pass(ball, false);
            AbstractObjectInstance.updateCameraBounds(updateCameraX, 0, updateCameraX + width, 224, 0);
            ball.update(66, null); // (-$480 + 64 * $18) == $180
        } finally {
            ball.onUnload();
            AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        }
    }

    private static List<Integer> submittedSounds(List<Sonic2SoundRequestService.Event> events) {
        return events.stream()
                .filter(Sonic2SoundRequestService.Submission.class::isInstance)
                .map(Sonic2SoundRequestService.Submission.class::cast)
                .map(Sonic2SoundRequestService.Submission::rawRequestId).toList();
    }

    @Test
    void oneUpStopsSfxAndClearsPriorityBeforeSaving() {
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);

        assertTrue(audio.playSfx(0xBF));
        audio.presentFrame(PresentationMode.FORWARD);
        Sonic2SoundRequestService.Snapshot beforeOneUp = requestSnapshot(audio);
        assertEquals(0x7F, beforeOneUp.pipeline().sfxPriorityValue());

        audio.playMusic(Sonic2Music.EXTRA_LIFE.id);
        audio.presentFrame(PresentationMode.FORWARD);

        Sonic2SoundRequestService.Snapshot afterOneUp = requestSnapshot(audio);
        assertTrue(afterOneUp.pipeline().oneUpPlaying(), events.toString());
        assertEquals(0, afterOneUp.pipeline().sfxPriorityValue());
        assertEquals(0, afterOneUp.pipeline().savedOneUpSfxPriorityValue(),
                "retail zPlayMusic calls zStopSoundEffects before saving");
        assertFalse(audio.shadowSmpsDriverSnapshotForTesting().sequencers()
                        .stream().anyMatch(entry -> entry.snapshot().sfx()),
                "pre-one-up SFX must not survive in the saved driver region");
    }

    @Test
    void oneUpRefusesRingBeforeChangingSpeakerAndClearsPriorityForTheNextRequest() {
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);
        startLevelMusic(audio);
        audio.playMusic(Sonic2Music.EXTRA_LIFE.id);
        audio.presentFrame(PresentationMode.FORWARD);

        assertTrue(audio.playSfx(0xB5));
        AudioLogicalSnapshot queuedRing = audio.captureLogicalSnapshot();
        audio.presentFrame(PresentationMode.FORWARD);
        var afterRing = requestSnapshot(audio).pipeline();
        assertTrue(audio.playSfx(0xC0));
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(List.of("B5 ACCEPTED_PRIORITY 00->70", "C0 ACCEPTED_PRIORITY 00->6F"),
                sfxDecisions(events), () -> events + "\nafter ring: " + afterRing);
        assertEquals(0, afterRing.sfxPriorityValue());
        assertEquals(0, afterRing.ringSpeaker(), "the next ring still selects the left speaker");
        assertEquals(0, requestSnapshot(audio).pipeline().sfxPriorityValue());
        assertFalse(hasActiveSfx(audio), "both requests were refused by the one-up flag");

        audio.restoreLogicalSnapshot(queuedRing);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(afterRing, requestSnapshot(audio).pipeline(),
                "replay makes the same refusal without advancing the speaker");
    }

    @Test
    void oneUpFadeRestoresSavedPriorityAndOnlyReachedRefusalsClearIt() {
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);
        startLevelMusic(audio);
        assertTrue(audio.playSfx(0xBF));
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(0x7F, requestSnapshot(audio).pipeline().sfxPriorityValue());
        audio.playMusic(Sonic2Music.EXTRA_LIFE.id);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(0, requestSnapshot(audio).pipeline().sfxPriorityValue());

        // zCycleQueue can latch 72h for F7h, which zPlaySoundByIndex ignores
        // before the SFX refusal guard (s2.sounddriver.asm:1520-1536,1570-1573).
        assertTrue(audio.playSfx(0xF7));
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(0x72, requestSnapshot(audio).pipeline().sfxPriorityValue());

        for (int frame = 0; frame < 900
                && !audio.captureLogicalSnapshot().presentation().pendingRestore(); frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
        assertTrue(audio.captureLogicalSnapshot().presentation().pendingRestore(),
                "the real jingle reached cfFadeInToPrevious");
        assertEquals(0, requestSnapshot(audio).pipeline().sfxPriorityValue(),
                "E4 restores the saved byte before the next queue cycle");
        assertFalse(requestSnapshot(audio).pipeline().oneUpPlaying());

        events.clear();
        assertTrue(audio.playSfx(0xB5));
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(List.of("B5 ACCEPTED_PRIORITY 00->70"), sfxDecisions(events));
        assertEquals(0, requestSnapshot(audio).pipeline().sfxPriorityValue());
        assertEquals(0, requestSnapshot(audio).pipeline().ringSpeaker());
        assertTrue(musicFadingIn(audio));
        AudioLogicalSnapshot atRestore = audio.captureLogicalSnapshot();

        assertTrue(audio.playSfx(0xF7));
        audio.presentFrame(PresentationMode.FORWARD);
        events.clear();
        assertTrue(audio.playSfx(0xB5));
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(List.of("B5 REJECTED_PRIORITY 72->72"), sfxDecisions(events));
        assertEquals(0x72, requestSnapshot(audio).pipeline().sfxPriorityValue(),
                "queue rejection never reaches zKillSFXPrio");

        assertTrue(audio.playSfx(0xA0));
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(0, requestSnapshot(audio).pipeline().sfxPriorityValue(),
                "jump passes the queue and reaches the fade-in refusal");
        assertFalse(hasActiveSfx(audio));

        audio.restoreLogicalSnapshot(atRestore);
        for (int frame = 0; frame < 400 && musicFadingIn(audio); frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
        assertFalse(musicFadingIn(audio));
        assertEquals(0, requestSnapshot(audio).pipeline().sfxPriorityValue());
        assertTrue(audio.playSfx(0xB5));
        audio.presentFrame(PresentationMode.FORWARD);
        assertTrue(hasActiveSfx(audio), "ring plays after the fade");
        assertEquals(0xFF, requestSnapshot(audio).pipeline().ringSpeaker());
        assertEquals(0x70, requestSnapshot(audio).pipeline().sfxPriorityValue());
        for (int frame = 0; frame < 120 && hasActiveSfx(audio); frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
        assertFalse(hasActiveSfx(audio));
        assertEquals(0, requestSnapshot(audio).pipeline().sfxPriorityValue(),
                "a natural SFX track stop releases the latch");
    }

    private static List<String> sfxDecisions(List<Sonic2SoundRequestService.Event> events) {
        return events.stream().filter(Decision.class::isInstance).map(Decision.class::cast)
                .filter(decision -> decision.reason() == DecisionReason.ACCEPTED_PRIORITY
                        || decision.reason() == DecisionReason.REJECTED_PRIORITY)
                .map(decision -> "%02X %s %02X->%02X".formatted(decision.rawRequestId(),
                        decision.reason(), decision.priorityBefore(), decision.priorityAfter()))
                .toList();
    }

    private static void startLevelMusic(AudioManager audio) {
        audio.playMusic(Sonic2Music.EMERALD_HILL.id);
        for (int frame = 0; frame < 60; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
    }

    private static boolean musicFadingIn(AudioManager audio) {
        return audio.shadowSmpsDriverSnapshotForTesting().sequencers().stream()
                .anyMatch(entry -> !entry.sfx() && entry.snapshot().fade().active()
                        && !entry.snapshot().fade().fadeOut());
    }

    private static boolean hasActiveSfx(AudioManager audio) {
        return audio.shadowSmpsDriverSnapshotForTesting().sequencers().stream()
                .anyMatch(SmpsDriverSnapshot.SequencerEntry::sfx);
    }

    @Test
    void diagnosticFailureCannotRetryOrDuplicateACommittedRequest() {
        AtomicInteger callbacks = new AtomicInteger();
        AudioManager audio = install(event -> {
            callbacks.incrementAndGet();
            throw new IllegalStateException("seeded observer failure");
        });

        assertTrue(audio.playSfx(0xA0));
        audio.presentFrame(PresentationMode.FORWARD);
        int committedCallbacks = callbacks.get();
        assertTrue(committedCallbacks > 0);
        assertEquals(1, audio.commandTimeline().entryCount());

        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(committedCallbacks, callbacks.get());
        assertEquals(1, audio.commandTimeline().entryCount());
    }

    @Test
    void nullAndThrowingRomResolutionLeaveFullLiveStateExactAndRetryOnce() {
        for (FailureKind failure : List.of(
                FailureKind.NULL_SFX, FailureKind.THROW_SFX,
                FailureKind.NULL_MUSIC, FailureKind.THROW_MUSIC)) {
            List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
            RejectingSonic2AudioProfile profile =
                    new RejectingSonic2AudioProfile(events::add);
            CountingSink sink = new CountingSink();
            AudioManager audio = install(profile, new SinkBackend(sink));
            assertTrue(audio.playSfx(0xB5));
            audio.presentFrame(PresentationMode.FORWARD);
            LiveCaptureAudioHandle firstCapture =
                    audio.attachShadowCaptureForTesting(60);
            LiveCaptureAudioHandle secondCapture =
                    audio.attachShadowCaptureForTesting(60);

            profile.failure = failure;
            if (failure.music()) {
                audio.playMusic(Sonic2Music.EMERALD_HILL.id);
            } else {
                assertTrue(audio.playSfx(0xBF));
            }
            AudioLogicalSnapshot logicalBefore =
                    audio.captureLogicalSnapshot();
            SmpsDriverSnapshot driverBefore =
                    audio.shadowSmpsDriverSnapshotForTesting();
            var parityBefore = audio.shadowParitySnapshot();
            int timelineBefore = audio.commandTimeline().entryCount();
            int diagnosticsBefore = events.size();
            int sinkBefore = sink.deliveries;
            Sonic2SoundRequestService.Snapshot requestBefore =
                    requestSnapshot(audio);

            audio.presentFrame(PresentationMode.FORWARD);

            assertDeepEquals(logicalBefore,
                    audio.captureLogicalSnapshot(),
                    failure + " logical state");
            assertDeepEquals(driverBefore,
                    audio.shadowSmpsDriverSnapshotForTesting(),
                    failure + " full driver/session state");
            assertEquals(parityBefore, audio.shadowParitySnapshot(),
                    failure + " parity");
            assertEquals(timelineBefore,
                    audio.commandTimeline().entryCount(),
                    failure + " timeline");
            assertEquals(diagnosticsBefore, events.size(),
                    failure + " diagnostics");
            assertEquals(sinkBefore, sink.deliveries,
                    failure + " speaker delivery");
            assertCaptureEmpty(firstCapture, failure + " first capture");
            assertCaptureEmpty(secondCapture, failure + " second capture");
            assertEquals(requestBefore, requestSnapshot(audio),
                    failure + " request mailbox/queue/priority");

            profile.failure = FailureKind.NONE;
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(timelineBefore + 1,
                    audio.commandTimeline().entryCount(),
                    failure + " retries the same request exactly once");
            assertTrue(events.size() > diagnosticsBefore,
                    failure + " publishes diagnostics only after commit");
            assertEquals(sinkBefore + 1, sink.deliveries,
                    failure + " successful retry reaches the sink once");
            assertCaptureOnce(firstCapture, sink.lastPacket,
                    failure + " first capture retry");
            assertCaptureOnce(secondCapture, sink.lastPacket,
                    failure + " second capture retry");

            audio.resetState();
            rom.close();
            rom = null;
        }
    }

    @Test
    void diagnosticResolutionFailureEscapesAndRetryKeepsExactRequest() {
        RejectingSonic2AudioProfile profile =
                new RejectingSonic2AudioProfile(ignored -> { });
        AudioManager audio = install(profile);
        profile.failure = FailureKind.DIAGNOSTIC_MUSIC;
        audio.playMusic(Sonic2Music.EMERALD_HILL.id);
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        int timelineBefore = audio.commandTimeline().entryCount();

        AudioDiagnosticObserverException diagnostic = assertThrows(
                AudioDiagnosticObserverException.class,
                () -> audio.presentFrame(PresentationMode.FORWARD));
        assertEquals("seeded diagnostic rejection",
                diagnostic.getCause().getMessage());
        assertDeepEquals(before, audio.captureLogicalSnapshot(),
                "diagnostic rejection rollback");

        profile.failure = FailureKind.NONE;
        assertDoesNotThrow(() -> audio.presentFrame(PresentationMode.FORWARD));
        assertEquals(timelineBefore + 1,
                audio.commandTimeline().entryCount());
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(timelineBefore + 1,
                audio.commandTimeline().entryCount(),
                "retry must publish the request once");
    }

    @Test
    void reserveFailureRollsBackCandidateAndRetryKeepsExactRequest() {
        ReserveRejectingSonic2AudioProfile profile =
                new ReserveRejectingSonic2AudioProfile();
        AudioManager audio = install(profile);
        assertTrue(audio.playSfx(0xF9));
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        int timelineBefore = audio.commandTimeline().entryCount();

        IllegalStateException reserveFailure = assertThrows(
                IllegalStateException.class,
                () -> audio.presentFrame(PresentationMode.FORWARD));
        assertEquals("seeded reserve rejection", reserveFailure.getMessage());
        assertDeepEquals(before, audio.captureLogicalSnapshot(),
                "reserve rejection rollback");

        assertDoesNotThrow(() -> audio.presentFrame(PresentationMode.FORWARD));
        assertEquals(timelineBefore + 1,
                audio.commandTimeline().entryCount());
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(timelineBefore + 1,
                audio.commandTimeline().entryCount(),
                "retry must publish the request once");
    }

    private AudioManager install(
            java.util.function.Consumer<Sonic2SoundRequestService.Event>
                    observer) {
        return install(new Sonic2AudioProfile(observer));
    }

    private AudioManager install(Sonic2AudioProfile profile) {
        return install(profile, new NullAudioBackend());
    }

    private AudioManager install(
            Sonic2AudioProfile profile, NullAudioBackend backend) {
        File file = RomTestUtils.ensureSonic2RomAvailable();
        rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(backend);
        audio.setRom(rom);
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        return audio;
    }

    private static void assertCaptureEmpty(
            LiveCaptureAudioHandle capture, String message) {
        assertEquals(0, capture.totalStereoFrames(), message);
        assertEquals(0, pendingFrames(capture), message);
    }

    private static void assertCaptureOnce(
            LiveCaptureAudioHandle capture, short[] expected, String message) {
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        assertEquals(800, pendingFrames(capture), message);
        int frames = capture.drainPresentationFrame(packet);
        assertEquals(800, frames, message);
        assertArrayEquals(expected, packet, message);
        assertEquals(0, pendingFrames(capture),
                message + " must consume exactly one published packet");
    }

    private static int pendingFrames(LiveCaptureAudioHandle capture) {
        try {
            var field = capture.getClass().getDeclaredField("pendingStereoFrames");
            field.setAccessible(true);
            return field.getInt(capture);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class SinkBackend extends NullAudioBackend {
        private final CountingSink sink;

        private SinkBackend(CountingSink sink) {
            this.sink = sink;
        }

        @Override public int outputSampleRate() { return sink.sampleRate(); }

        @Override
        public AudioPresentationSink createPresentationSink(
                Consumer<Throwable> failureHandler,
                Consumer<String> warningHandler) {
            return sink;
        }
    }

    private static final class CountingSink implements AudioPresentationSink {
        private int deliveries;
        private short[] lastPacket;

        @Override public int sampleRate() { return 48_000; }

        @Override
        public void accept(AudioPresentationFrameView frame) {
            deliveries++;
            lastPacket = new short[frame.stereoFrames() * 2];
            frame.copyTo(lastPacket, 0);
        }

        @Override public void onReverseBoundary() { }
        @Override public void close() { }
    }

    private enum FailureKind {
        NONE, NULL_MUSIC, THROW_MUSIC, NULL_SFX, THROW_SFX,
        DIAGNOSTIC_MUSIC;

        boolean music() {
            return this == NULL_MUSIC || this == THROW_MUSIC;
        }
    }

    private static final class RejectingSonic2AudioProfile
            extends Sonic2AudioProfile {
        private FailureKind failure = FailureKind.NONE;

        private RejectingSonic2AudioProfile(
                java.util.function.Consumer<
                        Sonic2SoundRequestService.Event> observer) {
            super(observer);
        }

        @Override
        public SmpsLoader createSmpsLoader(Rom rom) {
            SmpsLoader delegate = super.createSmpsLoader(rom);
            return new SmpsLoader() {
                @Override
                public AbstractSmpsData loadMusic(int musicId) {
                    return switch (failure) {
                        case NULL_MUSIC -> null;
                        case THROW_MUSIC -> throw new IllegalStateException(
                                "seeded music loader rejection");
                        case DIAGNOSTIC_MUSIC ->
                                throw new AudioDiagnosticObserverException(
                                        new IllegalStateException(
                                                "seeded diagnostic rejection"));
                        default -> delegate.loadMusic(musicId);
                    };
                }

                @Override
                public AbstractSmpsData loadSfx(int sfxId) {
                    return switch (failure) {
                        case NULL_SFX -> null;
                        case THROW_SFX -> throw new IllegalStateException(
                                "seeded SFX loader rejection");
                        default -> delegate.loadSfx(sfxId);
                    };
                }

                @Override public AbstractSmpsData loadSfx(String name) {
                    return delegate.loadSfx(name);
                }

                @Override public DacData loadDacData() {
                    return delegate.loadDacData();
                }

                @Override public int findMusicOffset(int musicId) {
                    return delegate.findMusicOffset(musicId);
                }
            };
        }
    }

    private static final class ReserveRejectingSonic2AudioProfile
            extends Sonic2AudioProfile {
        private final ReserveRejectingRequestService requestService =
                new ReserveRejectingRequestService();

        @Override
        public AudioRequestService createAudioRequestService() {
            return requestService;
        }
    }

    private static final class ReserveRejectingRequestService
            implements AudioRequestService {
        private final Sonic2SoundRequestService delegate =
                new Sonic2SoundRequestService();
        private boolean rejectNextReserve = true;

        @Override public void submitMusic(int id, AudioCommand command) {
            delegate.submitMusic(id, command);
        }

        @Override public void submitSound(int id, AudioCommand command) {
            delegate.submitSound(id, command);
        }

        @Override public Snapshot snapshot() { return delegate.snapshot(); }

        @Override public void restore(
                AudioPresentationForwardService.Snapshot snapshot) {
            delegate.restore(snapshot);
        }

        @Override
        public ForwardBoundary beginForwardBoundary() {
            ForwardBoundary owned = delegate.beginForwardBoundary();
            return new ForwardBoundary() {
                @Override public void service(Consumer<AudioCommand> sink) {
                    owned.service(sink);
                }

                @Override public void reserveOutcome(
                        OutcomeReservation reservation) {
                    if (rejectNextReserve) {
                        rejectNextReserve = false;
                        throw new IllegalStateException(
                                "seeded reserve rejection");
                    }
                    owned.reserveOutcome(reservation);
                }

                @Override public void applyOutcome(AppliedOutcome outcome) {
                    owned.applyOutcome(outcome);
                }

                @Override public void prepareCommit() {
                    owned.prepareCommit();
                }

                @Override public CommittedReceipt commit() {
                    return owned.commit();
                }

                @Override public void publishDiagnostics(
                        CommittedReceipt receipt) {
                    owned.publishDiagnostics(receipt);
                }

                @Override public void rollback() { owned.rollback(); }
            };
        }
    }

    private static Sonic2SoundRequestService.Snapshot requestSnapshot(
            AudioManager audio) {
        return (Sonic2SoundRequestService.Snapshot) audio
                .captureLogicalSnapshot().forwardServiceSnapshot();
    }

    private static void assertDeepEquals(
            Object expected, Object actual, String path) {
        if (expected == actual) {
            return;
        }
        if (expected == null || actual == null
                || expected.getClass() != actual.getClass()) {
            assertEquals(expected, actual, path);
            return;
        }
        Class<?> type = expected.getClass();
        if (type.isArray()) {
            int length = Array.getLength(expected);
            assertEquals(length, Array.getLength(actual), path + ".length");
            for (int index = 0; index < length; index++) {
                assertDeepEquals(Array.get(expected, index),
                        Array.get(actual, index), path + "[" + index + "]");
            }
            return;
        }
        if (expected instanceof Iterable<?> left
                && actual instanceof Iterable<?> right) {
            var leftIterator = left.iterator();
            var rightIterator = right.iterator();
            int index = 0;
            while (leftIterator.hasNext() && rightIterator.hasNext()) {
                assertDeepEquals(leftIterator.next(), rightIterator.next(),
                        path + "[" + index++ + "]");
            }
            assertEquals(leftIterator.hasNext(), rightIterator.hasNext(),
                    path + ".size");
            return;
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                try {
                    assertDeepEquals(component.getAccessor().invoke(expected),
                            component.getAccessor().invoke(actual),
                            path + "." + component.getName());
                } catch (ReflectiveOperationException failure) {
                    fail("cannot inspect " + path, failure);
                }
            }
            return;
        }
        assertEquals(expected, actual, path);
    }
}
