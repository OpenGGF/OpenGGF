package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.sprites.ghost.GhostTraceRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.capture.*;
import com.openggf.trace.*;
import com.openggf.trace.replay.*;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.audio.AudioManagerTestDiagnostics;
import java.nio.file.Path;
import java.lang.reflect.Field;

import java.util.List;

import static com.openggf.tools.TestTraceGhostPresentationCadence.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real registered players and behavioral timers, rather than mock restore owners. */
@RequiresRom(SonicGame.SONIC_2)
class TestTraceGhostGameplayIsolation {
    @Test void seekingCannotRemoveMainPlayersLiveSpeedShoesTimer() throws Exception {
        seekPreservesTimer(false);
    }

    @Test void seekingCannotRemoveSidekicksLiveSpeedShoesTimer() throws Exception {
        seekPreservesTimer(true);
    }

    private void seekPreservesTimer(boolean sidekickOwner) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            var main = session.player();
            var sidekick = GameServices.sprites().getRegisteredSidekicks().getFirst();
            var owner = sidekickOwner ? sidekick : main;
            main.giveSpeedShoes(96);
            sidekick.giveSpeedShoes(64);
            var timers = owner.currentTimerManager();
            var code = "SpeedShoes-" + owner.getCode();
            var timer = timers.getTimerForCode(code);
            assertNotNull(timer);
            var renderer = new GhostTraceRenderer();
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0),
                    java.util.stream.IntStream.range(0, 12).mapToObj(i ->
                            withSidekick(row(i, main), i, i, sideState(main, false, true))).toList());
            var comparator = comparator(trace, renderer);
            for (int i = 0; i < 12; i++) comparator.afterFrameAdvanced(input(i), false);
            // Capture while shoes are active, then change the real owner's timer
            // independently. A seek must neither remove it nor restore an old duration.
            timers.updateDisplayPhaseTimersFor(owner);
            var timersBefore = timers.capture();
            var mainBefore = main.captureRewindState(false);
            var sidekickBefore = sidekick.captureRewindState(false);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var gameBefore = mapper.valueToTree(GameServices.gameState().capture());
            for (int cursor : new int[]{3, 1, 12, 7, 2}) {
                comparator.seekForRewind(cursor);
                assertSame(timer, timers.getTimerForCode(code), "seek must retain the behavioral timer owner");
                assertEquals(timersBefore, timers.capture(), "seek must not change any live timer duration");
                assertEquals(mainBefore, main.captureRewindState(false));
                assertEquals(sidekickBefore, sidekick.captureRewindState(false));
                assertEquals(gameBefore, mapper.valueToTree(GameServices.gameState().capture()));
            }
            renderer.reset();
            assertEquals(0, slotCount(renderer));
            assertSame(timer, timers.getTimerForCode(code));
            // Prove the retained timer still expires on the registered player.
            int remaining = timer.getTicks();
            for (int i = 0; i < remaining; i++) timers.updateDisplayPhaseTimersFor(owner);
            assertFalse(owner.hasSpeedShoes());
            assertNull(timers.getTimerForCode(code));
        }
    }

    @Test void failedSidekickAnimationRollsBackBothPrivateOwnersAndPublishesNoHistory() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            var main = session.player();
            main.giveSpeedShoes(64);
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0),
                    java.util.stream.IntStream.range(0, 6).mapToObj(i ->
                            withSidekick(row(i, main), i, i, sideState(main, false, true))).toList());
            var renderer = new GhostTraceRenderer();
            admit(renderer, trace, 2);
            var ghostMain = ghost(renderer, "main:sonic");
            var ghostTail = ghost(renderer, "sidekick0:tails");
            var mainBefore = PlayableSpriteInternalAccess.captureGhostPresentation(ghostMain);
            var tailBefore = PlayableSpriteInternalAccess.captureGhostPresentation(ghostTail);
            var realBefore = main.captureRewindState(false);
            var timersBefore = main.currentTimerManager().capture();
            var profile = ghostTail.getAnimationProfile();
            var failure = new IllegalStateException("injected sidekick animation failure");
            ghostTail.setAnimationProfile(new SpriteAnimationProfile() {
                @Override public Integer resolveAnimationId(AbstractPlayableSprite sprite, int clock, int scripts) {
                    throw failure;
                }
                @Override public int resolveFrame(AbstractPlayableSprite sprite, int clock, int frames) {
                    throw failure;
                }
            });
            try {
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> renderer.synchronizeVisualRows(trace, 3, false)));
                assertEquals(mainBefore, PlayableSpriteInternalAccess.captureGhostPresentation(ghostMain));
                assertEquals(tailBefore, PlayableSpriteInternalAccess.captureGhostPresentation(ghostTail));
                assertTrue(renderer.isVisualStateAvailable());
                assertEquals(2, field(renderer, "consumedRows"));
                assertEquals(2, field(renderer, "validHistoryRows"));
                assertEquals(realBefore, main.captureRewindState(false));
                assertEquals(timersBefore, main.currentTimerManager().capture());
            } finally {
                ghostTail.setAnimationProfile(profile);
            }
            renderer.synchronizeVisualRows(trace, 3, false);
            var reference = new GhostTraceRenderer();
            admit(reference, trace, 3);
            assertEquals(pose(ghost(reference, "main:sonic")), pose(ghostMain));
            assertEquals(pose(ghost(reference, "sidekick0:tails")), pose(ghostTail));
            renderer.reset();
            reference.reset();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void captureFinishAndFailedFinishRetireHistoryWithoutTouchingLiveTimers(boolean failStop) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0),
                    java.util.stream.IntStream.range(0, 24).mapToObj(i -> row(i, session.player())).toList());
            var movie = new com.openggf.debug.playback.Bk2Movie(Path.of("ghost-isolation.bk2"), "logkey",
                    java.util.Map.of(), java.util.stream.IntStream.range(0, 32)
                            .mapToObj(TestTraceGhostPresentationCadence::input).toList(), 1);
            var fixture = new TraceReplayDrive.DriverFixture(new RecordingFrameDriver(session.player()));
            var driver = new TraceReplayDriver(trace, movie, fixture, session.loop(), session::player, () -> {});
            driver.startPreparedLevel();
            var recorder = new CaptureRecorder(null, BackpressurePolicy.BLOCK, 1,
                    Path.of("target"), "ghost-isolation", "stamp") {
                @Override public void start(int width, int height, int fps, int sampleRate) { }
                @Override public Path stop() throws CaptureException {
                    if (failStop) throw new CaptureException("injected stop failure");
                    return outputFile();
                }
            };
            VideoFrameGrabber grabber = new VideoFrameGrabber() {
                public int width() { return 320; }
                public int height() { return 224; }
                public byte[] grab() { return new byte[320 * 224 * 4]; }
            };
            int leasesBefore = AudioManagerTestDiagnostics.producerFingerprint(GameServices.audio()).captureCount();
            var capture = new TraceCaptureSession(session.loop(), driver, grabber, target -> 0, recorder, 60);
            capture.start(320, 224, 48000);
            var renderer = (GhostTraceRenderer) field(capture, "ghostRenderer");
            try {
                session.player().giveSpeedShoes(64);
                var timers = session.player().currentTimerManager();
                var timer = timers.getTimerForCode("SpeedShoes-" + session.player().getCode());
                for (int i = driver.comparator().cursor(); i < 8; i++) {
                    driver.comparator().afterFrameAdvanced(input(i), false);
                }
                var before = session.player().captureRewindState(false);
                driver.comparator().seekForRewind(3);
                assertSame(timer, timers.getTimerForCode(timer.getCode()));
                assertEquals(before, session.player().captureRewindState(false));
            } finally {
                if (failStop) assertThrows(CaptureException.class, capture::finish);
                else capture.finish();
                GameServices.playbackDebug().endSession();
            }
            assertNull(TraceGhostHook.active());
            assertFalse(renderer.isVisualStateAvailable());
            assertEquals(0, slotCount(renderer));
            assertEquals(0, ((List<?>) field(renderer, "visualHistory")).size());
            assertEquals(leasesBefore, AudioManagerTestDiagnostics.producerFingerprint(GameServices.audio()).captureCount());
            driver.comparator().seekForRewind(2);
            assertEquals(0, slotCount(renderer), "finished capture receives no later ghost publications");
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    @Test void ghostAirToGroundRowsCannotResetTheRealGamesItemBonus() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            var main = session.player();
            var ground = row(1, main);
            var air = new TraceFrame(0, ground.input(), ground.x(), ground.y(), ground.xSpeed(),
                    ground.ySpeed(), ground.gSpeed(), ground.angle(), true, false, ground.groundMode(),
                    0, 0, 2, -1, -1, -1, 0, 0, -1, 0, 0);
            var renderer = new GhostTraceRenderer();
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0), List.of(air, ground));
            GameServices.gameState().setItemBonus(6);
            var mainBefore = main.captureRewindState(false);
            var comparator = comparator(trace, renderer);
            comparator.afterFrameAdvanced(input(0), false);
            comparator.afterFrameAdvanced(input(1), false);
            assertEquals(6, GameServices.gameState().getItemBonus(), "ghost landing must have no gameplay callback");
            assertEquals(mainBefore, main.captureRewindState(false));
        }
    }
}
