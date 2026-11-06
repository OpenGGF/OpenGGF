package com.openggf.audio;

import com.openggf.audio.presentation.*;
import com.openggf.audio.rewind.*;
import com.openggf.audio.session.SmpsStatefulCommandPolicy;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Production mailbox/session/history integration; no native trace or gameplay state supplies requests. */
@RequiresRom(SonicGame.SONIC_2)
class TestAudioNativePause {
    private final AudioManager audio = AudioManager.getInstance();
    private final AudioTestFixtures.RecordingAudioBackend backend = new AudioTestFixtures.RecordingAudioBackend();
    private Rom rom;
    private long clockBase;

    @AfterEach
    void close() throws Exception {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        if (rom != null) rom.close();
    }

    @Test
    void actualMailboxPauseHoldsSequencingAndResumeSkipsExactlyItsOwnInvocation() throws Exception {
        install(new Sonic2AudioProfile());
        startSong();
        var port = AudioNativePauseAccess.bind(audio);
        var capture = audio.attachShadowCaptureForTesting(60);
        var tracks = tracks();
        int publicCount = audio.commandTimeline().entryCount();
        int events = AudioTimelineInternalAccess.cursor(audio.commandTimeline());
        port.requestPause();
        assertFalse(state().presentation().smpsSession().nativePause().paused());
        step(10);
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(tracks, tracks());
        assertEquals(0x7F, source().pipeline().stopMusic());
        assertNull(source().pendingNativePause());
        drainPublishedPacket(capture);
        long pcmBefore = capture.totalStereoFrames();
        audio.playSfx(0xB5);
        step(11);
        drainPublishedPacket(capture);
        step(12);
        drainPublishedPacket(capture);
        assertEquals(tracks, tracks());
        assertTrue(capture.totalStereoFrames() > pcmBefore, "native pause must keep presenting PCM");
        assertEquals(publicCount, audio.commandTimeline().entryCount());
        assertEquals(events + 1, AudioTimelineInternalAccess.cursor(audio.commandTimeline()),
                "held services must not manufacture new pause edges");
        assertTrue(source().pipeline().queue0().requestByte() != 0, "SFX must remain queued");
        port.requestResume();
        step(13);
        assertFalse(state().presentation().smpsSession().nativePause().paused());
        assertEquals(0, source().pipeline().stopMusic());
        assertEquals(tracks, tracks(), "S2 resume service must not walk tracks");
        assertEquals(publicCount, audio.commandTimeline().entryCount());
        step(14);
        assertNotEquals(tracks, tracks(), "the following native invocation must advance");
        assertTrue(backend.calls.stream().noneMatch(s -> s.equals("pause") || s.equals("resume")));
    }

    @Test
    void failedForwardCommitKeepsPendingRequestWithoutPublishingHistoryOrPcm() throws Exception {
        var faults = new FaultService();
        install(profile(faults));
        startSong();
        var port = AudioNativePauseAccess.bind(audio);
        var capture = audio.attachShadowCaptureForTesting(60);
        port.requestPause();
        var before = source();
        var tracks = tracks();
        int cursor = AudioTimelineInternalAccess.cursor(audio.commandTimeline());
        long pcm = capture.totalStereoFrames();
        faults.failPrepare = true;
        assertThrows(IllegalStateException.class, () -> step(20));
        assertEquals(before, source());
        assertEquals(tracks, tracks());
        assertFalse(state().presentation().smpsSession().nativePause().paused());
        assertEquals(cursor, AudioTimelineInternalAccess.cursor(audio.commandTimeline()));
        assertEquals(pcm, capture.totalStereoFrames());
        assertEquals(0, pendingFrames(capture), "failed forward work cannot publish a capture packet");
        step(20);
        drainPublishedPacket(capture);
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(cursor + 1, AudioTimelineInternalAccess.cursor(audio.commandTimeline()));
        port.requestResume();
        faults.failPrepare = true;
        assertThrows(IllegalStateException.class, () -> step(21));
        assertEquals(0, pendingFrames(capture), "failed resume must also keep PCM private");
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(0xFF, source().pipeline().music0().requestByte(),
                "rollback must retain resume before its bridge consumption");
        step(21);
        assertEquals(0, source().pipeline().stopMusic());
        assertFalse(state().presentation().smpsSession().nativePause().paused());
    }

    @Test
    void segaTransportDoesNotAcknowledgePauseUntilItsNaturalZ80ServiceReturns() throws Exception {
        install(new Sonic2AudioProfile());
        audio.playMusic(0xFA);
        step(1);
        assertNotNull(state().presentation().smpsSession().segaPcmTransport());
        int cursor = AudioTimelineInternalAccess.cursor(audio.commandTimeline());
        AudioNativePauseAccess.bind(audio).requestPause();
        step(2);
        assertEquals(Boolean.TRUE, source().pendingNativePause());
        assertEquals(Boolean.TRUE, state().presentation().smpsSession().nativePause().request());
        assertFalse(state().presentation().smpsSession().nativePause().paused());
        assertEquals(cursor, AudioTimelineInternalAccess.cursor(audio.commandTimeline()));
        int frame = 3;
        while (state().presentation().smpsSession().segaPcmTransport() != null && frame < 250) step(frame++);
        assertNull(state().presentation().smpsSession().segaPcmTransport());
        assertEquals(Boolean.TRUE, source().pendingNativePause(), "PCM completion is not a service receipt");
        step(frame);
        assertNull(source().pendingNativePause());
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(cursor + 1, AudioTimelineInternalAccess.cursor(audio.commandTimeline()));
    }

    @Test
    void actualCompressedLoadRetainsUnbridgedPauseUntilItsNativeWorkCompletes() throws Exception {
        install(new Sonic2AudioProfile());
        audio.playMusic(Sonic2Music.EMERALD_HILL.id);
        step(1);
        assertNotNull(state().presentation().smpsLogical().pendingService(), "EHZ has real ROM-backed Saxman work");
        int cursor = AudioTimelineInternalAccess.cursor(audio.commandTimeline());
        AudioNativePauseAccess.bind(audio).requestPause();
        int frame = 2;
        do {
            step(frame++);
            assertEquals(0xFE, source().pipeline().music0().requestByte());
            assertNull(source().pendingNativePause(), "blocked M68K consumption cannot manufacture a bridge result");
            assertFalse(state().presentation().smpsSession().nativePause().paused());
            assertEquals(cursor, AudioTimelineInternalAccess.cursor(audio.commandTimeline()));
        } while (state().presentation().smpsLogical().pendingService() != null && frame < 200);
        assertNull(state().presentation().smpsLogical().pendingService());
        step(frame);
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(0x7F, source().pipeline().stopMusic());
    }

    @Test
    void pendingMailboxSnapshotAndCommittedPauseEdgesSurviveRestoreAndBetweenKeyframeReplay() throws Exception {
        install(new Sonic2AudioProfile());
        startSong();
        var keyframes = new AudioKeyframeStore();
        keyframes.capture(1, audio);
        int publicCount = audio.commandTimeline().entryCount();
        var port = AudioNativePauseAccess.bind(audio);
        port.requestPause();
        var pending = state();
        port.requestResume();
        audio.restoreLogicalSnapshot(pending);
        step(2);
        step(3);
        port.requestResume();
        step(4);
        step(5);
        int cursor = AudioTimelineInternalAccess.cursor(audio.commandTimeline());
        assertEquals(0, keyframes.replayToLogicalState(audio, 3, clockBase + 3));
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(0x7F, source().pipeline().stopMusic());
        assertEquals(0, keyframes.replayToLogicalState(audio, 5, clockBase + 5));
        assertFalse(state().presentation().smpsSession().nativePause().paused());
        assertEquals(0, source().pipeline().stopMusic());
        assertEquals(publicCount, audio.commandTimeline().entryCount());
        assertEquals(cursor, AudioTimelineInternalAccess.cursor(audio.commandTimeline()), "replay must not append history");
    }

    @Test
    void heldReplayAndFailedMailboxStagingDoNotMutateTheLiveSelection() throws Exception {
        var faults = new FaultService();
        install(profile(faults));
        startSong();
        var selected = state();
        var keyframes = new AudioKeyframeStore();
        keyframes.capture(1, audio);
        var port = AudioNativePauseAccess.bind(audio);
        port.requestPause(); step(2);
        port.requestResume(); step(4);
        var live = source();
        var liveLatch = state().presentation().smpsSession().nativePause();
        audio.beginReverseAudioPresentation();
        keyframes.replayToLogicalState(audio, 3, clockBase + 3);
        assertEquals(live, source());
        assertEquals(liveLatch, state().presentation().smpsSession().nativePause());
        audio.endReverseAudioPresentation();
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(0x7F, source().pipeline().stopMusic());

        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(selected);
        live = source();
        faults.failSubmit = true;
        assertThrows(IllegalStateException.class, port::requestPause);
        assertEquals(live, source());
        audio.endReverseAudioPresentation();
        assertEquals(selected.forwardServiceSnapshot(), source());
        assertFalse(state().presentation().smpsSession().nativePause().paused());
    }

    @Test
    void unsupportedHostPolicyRejectsBeforeMailboxMutation() throws Exception {
        install(new Sonic2AudioProfile() {
            @Override public SmpsStatefulCommandPolicy smpsStatefulCommandPolicy() {
                return SmpsStatefulCommandPolicy.NONE;
            }
        });
        var source = source();
        var port = AudioNativePauseAccess.bind(audio);
        assertThrows(UnsupportedOperationException.class, port::requestPause);
        assertThrows(UnsupportedOperationException.class, port::requestResume);
        assertEquals(source, source());
    }

    @Test
    void nativePauseOverwritesPrimaryMusicWithoutUsingOrDiscardingFallback() throws Exception {
        install(new Sonic2AudioProfile());
        audio.playMusic(Sonic2Music.EMERALD_HILL.id);
        audio.playMusic(Sonic2Music.CHEMICAL_PLANT.id);
        var fallback = source().pipeline().music1();
        var port = AudioNativePauseAccess.bind(audio);
        port.requestPause();
        assertEquals(0xFE, source().pipeline().music0().requestByte());
        assertEquals(fallback, source().pipeline().music1());
        port.requestResume();
        assertEquals(0xFF, source().pipeline().music0().requestByte());
        assertEquals(fallback, source().pipeline().music1());
    }

    @Test
    void longPublicCommandFreeHistoryPrunesToTheActualAudioFloorAndRebranches() throws Exception {
        install(new Sonic2AudioProfile()); startSong();
        int publicCount = audio.commandTimeline().entryCount();
        var keyframes = new AudioKeyframeStore();
        keyframes.capture(0, audio);
        var port = AudioNativePauseAccess.bind(audio);
        for (int frame = 1; frame <= 64; frame++) {
            if ((frame & 1) != 0) port.requestPause(); else port.requestResume();
            step(frame);
            if (frame == 31) keyframes.capture(frame, audio);
        }
        var floor = keyframes.keyframeAtOrBefore(31);
        int floorCursor = floor.presentation().smpsSession().nativeHistoryCursor();
        int end = AudioTimelineInternalAccess.cursor(audio.commandTimeline());
        assertEquals(publicCount, audio.commandTimeline().entryCount());
        audio.pruneAudioCommandsBefore(floor.commandEntryCount());
        AudioTimelineInternalAccess.pruneBefore(audio.commandTimeline(), floorCursor);
        keyframes.discardBeforeRetainingFloor(31);
        assertEquals(floorCursor, AudioTimelineInternalAccess.firstRetainedCursor(audio.commandTimeline()));
        assertEquals(33, AudioTimelineInternalAccess.eventsFrom(audio.commandTimeline(), 0, Long.MAX_VALUE).size());
        assertEquals(0, keyframes.replayToLogicalState(audio, 41, clockBase + 41));
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(0x7F, source().pipeline().stopMusic());
        assertEquals(end, AudioTimelineInternalAccess.cursor(audio.commandTimeline()));
        audio.discardAudioCommandsAfter(clockBase + 41);
        int branch = AudioTimelineInternalAccess.cursor(audio.commandTimeline());
        port.requestResume(); step(42);
        assertEquals(branch + 1, AudioTimelineInternalAccess.cursor(audio.commandTimeline()));
        assertEquals(publicCount, audio.commandTimeline().entryCount());
        assertFalse(state().presentation().smpsSession().nativePause().paused());
    }

    @Test
    void aPreparedReverseTargetIsInvalidatedByLaterNativeReplayAndCursorSelection() throws Exception {
        install(new Sonic2AudioProfile()); startSong();
        var beforePause = state();
        var port = AudioNativePauseAccess.bind(audio);
        port.requestPause(); step(2);
        var paused = state();
        port.requestResume(); step(4);
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(beforePause);
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        AudioNativePauseAccess.replay(audio, true, paused.forwardServiceSnapshot());
        AudioNativePauseAccess.advanceHistoryCursor(audio,
                paused.presentation().smpsSession().nativeHistoryCursor());
        assertTrue(audio.endReverseAudioPresentation());
        assertTrue(state().presentation().smpsSession().nativePause().paused());
        assertEquals(paused.forwardServiceSnapshot(), source());
        assertEquals(paused.presentation().smpsSession().nativeHistoryCursor(),
                state().presentation().smpsSession().nativeHistoryCursor());
    }

    @Test
    void failedIngressAndHeldRollbackPublishNeitherPendingInputNorSelectedState() throws Exception {
        var faults = new FaultService(); install(profile(faults)); startSong();
        var before = state();
        var port = AudioNativePauseAccess.bind(audio);
        faults.failSubmit = true;
        assertThrows(IllegalStateException.class, port::requestPause);
        assertEquals(before.forwardServiceSnapshot(), source());
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(before);
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        faults.failSubmit = true;
        faults.failRestoreAt = 2; // Restores live data, then reports a rollback failure.
        var failure = assertThrows(IllegalStateException.class, port::requestPause);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(before.forwardServiceSnapshot(), source());
        assertTrue(audio.endReverseAudioPresentation());
        assertEquals(before.forwardServiceSnapshot(), source());
        assertFalse(state().presentation().smpsSession().nativePause().paused());
    }

    @ParameterizedTest
    @EnumSource(AudioManager.ReverseReleaseFailurePoint.class)
    void nativeServiceAndSessionReleaseRemainCoherentOnBothSidesOfCommit(
            AudioManager.ReverseReleaseFailurePoint failure) throws Exception {
        install(new Sonic2AudioProfile()); startSong();
        var port = AudioNativePauseAccess.bind(audio);
        port.requestPause(); step(2);
        var paused = state();
        port.requestResume(); step(4);
        var running = source();
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(paused);
        audio.failNextReverseReleaseForTesting(failure);
        boolean committed = audio.endReverseAudioPresentation();
        if (failure == AudioManager.ReverseReleaseFailurePoint.BEFORE_PRODUCER_COMMIT) {
            assertFalse(committed);
            assertEquals(running, source());
            assertFalse(state().presentation().smpsSession().nativePause().paused());
            assertTrue(audio.endReverseAudioPresentation());
        } else assertTrue(committed);
        assertEquals(paused.forwardServiceSnapshot(), source());
        assertTrue(state().presentation().smpsSession().nativePause().paused());
    }

    private AudioLogicalSnapshot state() { return audio.captureLogicalSnapshot(); }
    private int pendingFrames(LiveCaptureAudioHandle capture) {
        try {
            var field = capture.getClass().getDeclaredField("pendingStereoFrames");
            field.setAccessible(true);
            return field.getInt(capture);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void drainPublishedPacket(LiveCaptureAudioHandle capture) {
        assertEquals(800, pendingFrames(capture), "one 48 kHz / 60 Hz packet must be published");
        assertEquals(800, capture.drainPresentationFrame(new short[capture.maxStereoFramesPerPacket() * 2]));
        assertEquals(0, pendingFrames(capture), "the single committed packet must be consumed exactly");
    }
    private Sonic2SoundRequestService.Snapshot source() {
        return (Sonic2SoundRequestService.Snapshot) state().forwardServiceSnapshot();
    }
    private List<String> tracks() {
        return state().presentation().smpsLogical().sequencers().stream().flatMap(e ->
                e.snapshot().tracks().stream().map(t -> e.sfx() + ":" + t.type() + ":" + t.channelId()
                        + ":" + t.pos() + ":" + t.duration() + ":" + t.note())).toList();
    }
    private void step(int frame) { audio.beginCommandTimelineFrame(clockBase + frame); audio.presentFrame(PresentationMode.FORWARD); }
    private void startSong() {
        audio.playMusic(Sonic2Music.EMERALD_HILL.id);
        int frame = 1;
        do { step(frame++); }
        while ((state().presentation().smpsLogical().pendingService() != null
                || state().presentation().smpsLogical().sequencers().isEmpty()) && frame < 200);
        assertFalse(state().presentation().smpsLogical().sequencers().isEmpty());
        for (int i = 0; i < 4; i++) step(frame++);
        clockBase = audio.commandTimeline().currentFrame();
    }
    private void install(Sonic2AudioProfile profile) throws Exception {
        audio.resetState(); audio.setBackend(backend);
        clockBase = 0;
        rom = new Rom(); assertTrue(rom.open(RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath()));
        audio.setRom(rom); audio.setAudioProfile(profile); audio.setSoundMap(profile.getSoundMap());
    }
    private Sonic2AudioProfile profile(FaultService service) {
        return new Sonic2AudioProfile() {
            @Override public AudioRequestService createAudioRequestService() { return service; }
        };
    }

    private static final class FaultService implements AudioRequestService, AudioNativePauseAccess.Mailbox {
        final Sonic2SoundRequestService owned = new Sonic2SoundRequestService();
        boolean failPrepare, failSubmit;
        int failRestoreAt;
        @Override public void submitNativePause(boolean paused) {
            owned.submitNativePause(paused);
            if (failSubmit) { failSubmit = false; throw new IllegalStateException("failed staged native ingress"); }
        }
        @Override public void submitMusic(int id, AudioCommand command) { owned.submitMusic(id, command); }
        @Override public void submitSound(int id, AudioCommand command) { owned.submitSound(id, command); }
        @Override public Snapshot snapshot() { return owned.snapshot(); }
        @Override public void restore(Snapshot snapshot) {
            owned.restore(snapshot);
            if (failRestoreAt > 0 && --failRestoreAt == 0) throw new IllegalStateException("failed source rollback");
        }
        @Override public ForwardBoundary beginForwardBoundary() {
            var b = owned.beginForwardBoundary();
            return new ForwardBoundary() {
                @Override public void service(Consumer<AudioCommand> sink) { b.service(sink); }
                @Override public AudioPresentationCommand.NativePause nativePauseRequest() { return b.nativePauseRequest(); }
                @Override public void onNativePauseConsumed() { b.onNativePauseConsumed(); }
                @Override public void reserveOutcome(AudioPresentationCommandResolver.OutcomeReservation r) { b.reserveOutcome(r); }
                @Override public void applyOutcome(AudioPresentationCommandResolver.AppliedOutcome o) { b.applyOutcome(o); }
                @Override public void prepareCommit() {
                    if (failPrepare) { failPrepare = false; throw new IllegalStateException("failed native forward prepare"); }
                    b.prepareCommit();
                }
                @Override public CommittedReceipt commit() { return b.commit(); }
                @Override public void publishDiagnostics(CommittedReceipt r) { b.publishDiagnostics(r); }
                @Override public void rollback() { b.rollback(); }
            };
        }
    }
}
