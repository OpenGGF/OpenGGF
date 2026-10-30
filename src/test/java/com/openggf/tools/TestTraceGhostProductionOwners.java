package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.TraceSessionLauncher;
import com.openggf.capture.*;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineServices;
import com.openggf.sprites.ghost.GhostTraceRenderer;
import com.openggf.trace.*;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.replay.TraceGhostHook;
import com.openggf.trace.replay.TraceReplayDriver;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static com.openggf.tools.TestTraceGhostPresentationCadence.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real launcher activation and offline stepping, with original ROM art under EGL. */
class TestTraceGhostProductionOwners {
    @Test
    void liveLauncherPublishesGhostRowsBeforeAnyPictureAndRestoresSeeks() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s1"), 0, 0, settings);
            Engine engine = new Engine(EngineServices.current());
            set(engine, "gameLoop", session.loop());
            setStatic(Engine.class, "instance", engine);
            TraceData trace = trace(session, "s1");
            var launcher = launcher(trace, movie());
            try {
                Method activate = TraceSessionLauncher.class.getDeclaredMethod("finishStandaloneReplayLaunch", GameLoop.class);
                activate.setAccessible(true);
                activate.invoke(launcher, session.loop());
                assertSame(launcher, TraceSessionLauncher.active(), "the production activation must succeed");
                var comparator = (LiveTraceComparator) get(launcher, "comparator");
                var renderer = (GhostTraceRenderer) get(launcher, "ghostRenderer");
                int initial = comparator.cursor();
                for (int i = initial; i < 12; i++) comparator.afterFrameAdvanced(input(i), false);
                var expected = pose(ghost(renderer, "main:sonic"));
                for (int i = 0; i < 12; i++) launcher.renderGhosts();
                assertEquals(expected, pose(ghost(renderer, "main:sonic")));
                comparator.seekForRewind(4);
                var prefix = new GhostTraceRenderer();
                prefix.synchronizeVisualRows(trace, 4, true);
                assertEquals(pose(ghost(prefix, "main:sonic")), pose(ghost(renderer, "main:sonic")));
                comparator.setVisualRowObserver(null);
            } finally {
                TraceGhostHook.clear(TraceGhostHook.active());
                GameServices.playbackDebug().endSession();
                setStatic(TraceSessionLauncher.class, "activeSession", null);
                Engine.clearGlobalInstance();
            }
        }
    }

    @Test
    void offlineCaptureOwnsGhostUpdatesAndDetachesAfterFinish() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings);
             var grabber = new GlReadPixelsGrabber(320, 224)) {
            session.boot(rom("s2"), 0, 0, settings);
            TraceData trace = trace(session, "s2");
            var fixture = new TraceReplayDrive.DriverFixture(new RecordingFrameDriver(session.player()));
            var driver = new TraceReplayDriver(trace, movie(), fixture, session.loop(), session::player, () -> {});
            driver.startPreparedLevel();
            var recorder = new MemoryRecorder();
            var capture = new TraceCaptureSession(session.loop(), driver, grabber,
                    target -> 0, recorder, 60);
            capture.start(320, 224, 48000);
            var renderer = (GhostTraceRenderer) get(capture, "ghostRenderer");
            try {
                // Admit several rows while no capture picture is requested.
                int initial = driver.comparator().cursor();
                for (int i = initial; i < initial + 4; i++) driver.comparator().afterFrameAdvanced(input(i), false);
                var reference = new GhostTraceRenderer();
                reference.synchronizeVisualRows(trace, driver.comparator().cursor(), true);
                assertEquals(pose(ghost(reference, "main:sonic")), pose(ghost(renderer, "main:sonic")));
                int cursor = driver.comparator().cursor();
                assertTrue(capture.stepAndCapture(), "use the actual loop, layered renderer, GL readback and recorder");
                assertEquals(cursor + 1, driver.comparator().cursor());
                reference.synchronizeVisualRows(trace, cursor + 1, false);
                assertEquals(pose(ghost(reference, "main:sonic")), pose(ghost(renderer, "main:sonic")));
                var expected = pose(ghost(renderer, "main:sonic"));
                for (int i = 0; i < 8; i++) {
                    TraceGhostHook.active().renderGhostsForLayer(session.player().getPriorityBucket(),
                            session.player().isHighPriority());
                }
                assertEquals(expected, pose(ghost(renderer, "main:sonic")));
                assertEquals(1, recorder.frames);
                assertTrue(recorder.nonblank, "capture must submit real rendered pixels");
            } finally {
                capture.finish();
                GameServices.playbackDebug().endSession();
            }
            assertNull(TraceGhostHook.active());
            assertEquals(0, slotCount(renderer));
            driver.comparator().seekForRewind(2);
            assertEquals(0, slotCount(renderer), "finished capture must receive no later row publications");
        }
    }

    private static TraceData trace(GameplayCaptureSession session, String game) {
        return TraceFixtures.trace(TraceFixtures.metadata(game, 0, 0),
                java.util.stream.IntStream.range(0, 24).mapToObj(i -> row(i, session.player())).toList());
    }

    private static Bk2Movie movie() {
        return new Bk2Movie(Path.of("synthetic-cadence.bk2"), "logkey", java.util.Map.of(),
                java.util.stream.IntStream.range(0, 32).mapToObj(TestTraceGhostPresentationCadence::input).toList(), 1);
    }

    private static TraceSessionLauncher launcher(TraceData trace, Bk2Movie movie) throws Exception {
        Constructor<TraceSessionLauncher> constructor = TraceSessionLauncher.class.getDeclaredConstructor(
                TraceEntry.class, TraceData.class, Bk2Movie.class, TraceReplaySessionBootstrap.ConfigSnapshot.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new TraceEntry(Path.of("synthetic-cadence"), "s1", 0, 0, 24, 0, 0,
                null, null, trace.metadata()), trace, movie, TraceReplaySessionBootstrap.snapshotGameplayConfig());
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setStatic(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static final class MemoryRecorder extends CaptureRecorder {
        int frames;
        boolean nonblank;
        MemoryRecorder() { super(null, BackpressurePolicy.BLOCK, 1, Path.of("target"), "ghost-test", "stamp"); }
        @Override public void start(int width, int height, int fps, int sampleRate) {}
        @Override public void submit(CapturedFrame frame) {
            frames++;
            for (byte pixel : frame.rgba()) if (pixel != 0) nonblank = true;
        }
        @Override public Path stop() { return outputFile(); }
    }
}
