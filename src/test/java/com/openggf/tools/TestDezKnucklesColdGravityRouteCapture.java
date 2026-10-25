package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kDezGravitySwapObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezGravityTubeObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Direct, title-enabled Knuckles DEZ2 coverage; no seamless/campaign or boss claim. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDezKnucklesColdGravityRouteCapture {
    private static final Path INPUT = Path.of(
            "src/test/resources/routes/s3k/dez2-knuckles-cold-first-gravity-320.bk2");
    private static final Set<String> WINDOWS = Set.of("title", "tube1-entry", "writer1",
            "tube1-mid", "tube1-release", "tube2-entry", "writer2", "tube2-release", "normal");

    @Test
    void coldKnucklesCrossesTheFirstGravityWritersAndTubesUninterrupted() throws Exception {
        runColdRoute(false);
    }

    @Test
    void coldKnucklesCrossesTheFirstGravityWritersAndTubesWithWorldReplay() throws Exception {
        runColdRoute(true);
    }

    private void runColdRoute(boolean rewind) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(INPUT);
        assertEquals(1020, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false, null, null, false, null, false);
        var windows = new LinkedHashMap<String, Integer>();
        var projection = MessageDigest.getInstance("SHA-256");
        int titleReleased = -1, gravityChanges = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 1, settings);
            assertEquals(0x140, session.player().getCentreX());
            assertEquals(0x3AC, session.player().getCentreY(), "ROM direct Knuckles start, not mode2 transport $3B0");
            assertFalse(GameServices.gameState().isReverseGravityActive());
            var level = GameServices.level().getCurrentLevel();
            var world = new World(SessionManager.getCurrentGameplayMode().getWorldSession(),
                    level, GameServices.level().getObjectManager(),
                    assertInstanceOf(S3kDezZoneRuntimeState.class, GameServices.zoneRuntimeState()),
                    GameServices.gameState().getLives(), level.getMap().getData().clone());
            // Original DEZ2 table: ROM $1FA188, zero-based raw six-byte records.
            assertEquals(494, level.getObjects().size());
            placement(level.getObjects().get(29), 29, 0x5B, 0x700, 0x3C0, 0x03C0, 0, 0);
            placement(level.getObjects().get(30), 30, 0x5A, 0x700, 0x3C0, 0x03C0, 0x10, 0);
            placement(level.getObjects().get(73), 73, 0x5B, 0xB00, 0x3C0, 0x23C0, 0, 1);
            placement(level.getObjects().get(74), 74, 0x5A, 0xB00, 0x3C0, 0x03C0, 0x10, 0);

            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                var input = movie.getFrame(frame);
                assertEquals(0, input.p2InputMask());
                assertEquals(0, input.p2ActionMask());
                assertFalse(input.p1StartPressed() || input.p2StartPressed());
                var beforeMode = session.loop().getCurrentGameMode();
                boolean beforeGravity = GameServices.gameState().isReverseGravityActive();
                var firstWriter = swap(29);
                var secondWriter = swap(73);
                var firstTube = tube(30);
                var secondTube = tube(74);
                session.step(input);
                session.renderFrame();
                sameWorld(session, world);
                boolean inverted = GameServices.gameState().isReverseGravityActive();
                var currentFirst = tube(30);
                var currentSecond = tube(74);
                var afterFirstWriter = swap(29);
                var afterSecondWriter = swap(73);
                continuousRide(session, firstTube, currentFirst);
                continuousRide(session, secondTube, currentSecond);
                var support = session.player().getLatchedSolidObjectInstance();
                // Compact run-local observation, not a native journal or whole-world digest.
                String row = session.stateLine(frame, input) + "|gravity=" + inverted
                        + "|writer29=" + writerProjection(afterFirstWriter)
                        + "|writer73=" + writerProjection(afterSecondWriter)
                        + "|tube30=" + currentFirst.riding() + ":" + currentFirst.angle()
                        + "|tube74=" + currentSecond.riding() + ":" + currentSecond.angle()
                        + "|support=" + (support == null ? -1 : support.getSpawn().layoutIndex())
                        + "|lives=" + world.lives() + "|fg=4|bg=8\n";
                projection.update(row.getBytes(StandardCharsets.UTF_8));

                if (beforeMode == GameMode.TITLE_CARD
                        && session.loop().getCurrentGameMode() == GameMode.LEVEL) {
                    titleReleased = frame;
                    assertFalse(session.player().isControlLocked());
                }
                // Stay inside the title mode: its actual release at input85 is
                // a GameLoop history boundary, outside this world-only restore.
                if (frame == 20) {
                    assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
                    window("title", frame, windows, session, movie, world, rewind);
                }
                if (!firstTube.riding() && currentFirst.riding()) {
                    assertFalse(inverted, "first tube mounts before its co-located forward writer");
                    window("tube1-entry", frame, windows, session, movie, world, rewind);
                }
                if (inverted != beforeGravity) {
                    gravityChanges++;
                    if (inverted) {
                        crossing(firstWriter, afterFirstWriter, session);
                        assertTrue(currentFirst.riding(), "actual record30 tube owns P1 at writer29");
                        window("writer1", frame, windows, session, movie, world, rewind);
                    } else {
                        crossing(secondWriter, afterSecondWriter, session);
                        assertTrue(currentSecond.riding(), "actual record74 tube owns P1 at writer73");
                        window("writer2", frame, windows, session, movie, world, rewind);
                    }
                }
                if (inverted && currentFirst.riding() && session.player().getCentreX() >= 0x740) {
                    window("tube1-mid", frame, windows, session, movie, world, rewind);
                }
                if (firstTube.riding() && !currentFirst.riding()) {
                    assertTrue(inverted);
                    released(session, currentFirst, 0x780);
                    window("tube1-release", frame, windows, session, movie, world, rewind);
                }
                if (!secondTube.riding() && currentSecond.riding()) {
                    assertTrue(inverted, "second tube mounts inside the genuinely inverted corridor");
                    window("tube2-entry", frame, windows, session, movie, world, rewind);
                }
                if (secondTube.riding() && !currentSecond.riding()) {
                    assertFalse(inverted);
                    released(session, currentSecond, 0xB80);
                    window("tube2-release", frame, windows, session, movie, world, rewind);
                }
                if (frame >= 900 && !inverted && !session.player().getAir()
                        && session.player().getCentreX() > 0xB80) {
                    assertEquals(0, input.p1InputMask(), "authored control release after corridor");
                    assertEquals(0, input.p1ActionMask());
                    assertNull(session.player().getLatchedSolidObjectInstance());
                    assertFalse(session.player().isOnObject());
                    window("normal", frame, windows, session, movie, world, rewind);
                }
            }
            assertEquals(WINDOWS, windows.keySet());
            assertTrue(titleReleased > windows.get("title") && titleReleased < windows.get("tube1-entry"));
            String[] ordered = {"tube1-entry", "writer1", "tube1-mid", "tube1-release",
                    "tube2-entry", "writer2", "tube2-release", "normal"};
            for (int i = 1; i < ordered.length; i++) {
                assertTrue(windows.get(ordered[i - 1]) < windows.get(ordered[i]), ordered[i]);
            }
            assertEquals(2, gravityChanges, "both actual source-bound writes, no extra gravity transition");
            assertFalse(GameServices.gameState().isReverseGravityActive());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertFalse(session.player().isObjectControlled() || session.player().isControlLocked());
            assertFalse(session.player().getAir());
            assertNull(session.player().getLatchedSolidObjectInstance());
            assertTrue(session.player().getCentreX() > 0xB80, "playable continuation beyond record74 span");
            assertEquals(0, session.player().getXSpeed());
            assertEquals(0, session.player().getYSpeed());
            assertEquals(0, session.player().getGSpeed());
            System.out.println("DEZ2 Knuckles direct FG4/BG8; title release=" + titleReleased
                    + "; source-bound windows=" + windows + "; rewind=" + rewind
                    + "; projectionSha256=" + HexFormat.of().formatHex(projection.digest())
                    + "; end=" + session.player().getCentreX() + "," + session.player().getCentreY()
                    + "; rings=" + session.player().getRingCount() + "; lives=" + world.lives());
        }
    }

    private static void crossing(Swap before, Swap after, GameplayCaptureSession session) {
        assertNotNull(before.instance());
        assertSame(before.instance(), after.instance(), "same live raw-placement writer on the ordinary step");
        assertTrue(before.seeded());
        assertFalse(before.right());
        assertTrue(after.seeded() && after.right(), "source $32 latch actually consumed left-to-right");
        assertTrue(session.player().getCentreX() >= after.instance().getX());
        int y = session.player().getCentreY();
        assertTrue(y >= 0x3A0 && y < 0x3E0, "source signed [Y-$20,Y+$20) writer band");
    }

    private static void continuousRide(GameplayCaptureSession session, Tube before, Tube after) {
        if (!after.riding()) return;
        assertSame(after.instance(), session.player().getLatchedSolidObjectInstance(), "actual interact owner");
        assertTrue(session.player().isOnObject());
        assertFalse(session.player().getAir());
        assertFalse(after.instance().isRidingForTest(false));
        if (before.riding()) {
            assertSame(before.instance(), after.instance());
            assertEquals((before.angle() + 8) & 0xFF, after.angle(), "native narrow-tube angle progression");
            assertEquals(before.angle(), session.player().getFlipAngle() & 0xFF);
        }
    }

    private static void released(GameplayCaptureSession session, Tube tube, int rightEdge) {
        assertTrue(session.player().getCentreX() >= rightEdge, "actual span exit, not a mounted endpoint");
        assertNotSame(tube.instance(), session.player().getLatchedSolidObjectInstance());
        assertFalse(session.player().isOnObject());
        assertTrue(session.player().getAir());
    }

    private static void window(String name, int frame, Map<String, Integer> checked,
                               GameplayCaptureSession session,
                               com.openggf.debug.playback.Bk2Movie movie, World world, boolean rewind) {
        if (checked.putIfAbsent(name, frame) != null) return;
        assertTrue(frame + 45 < movie.getFrameCount());
        var mode = SessionManager.getCurrentGameplayMode();
        safeRestoreBoundary();
        if (!rewind) return; // The independent ordinary witness never captures/restores a branch.
        var registry = mode.getRewindRegistry();
        var capturedMode = session.loop().getCurrentGameMode();
        var saved = registry.capture();
        for (int n = 1; n <= 45; n++) {
            session.step(movie.getFrame(frame + n)); session.renderFrame(); sameWorld(session, world);
            assertEquals(capturedMode, session.loop().getCurrentGameMode(), "no cross-mode restore window");
            safeRestoreBoundary();
        }
        var forward = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            same(saved, registry.capture(), name + " restore " + cycle);
            session.restoreInputHistory(movie.getFrame(frame));
            for (int n = 1; n <= 45; n++) {
                session.step(movie.getFrame(frame + n)); session.renderFrame(); sameWorld(session, world);
                assertEquals(capturedMode, session.loop().getCurrentGameMode(), "no cross-mode restore window");
                safeRestoreBoundary();
            }
            same(forward, registry.capture(), name + " replay " + cycle);
        }
        registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
    }

    private static void sameWorld(GameplayCaptureSession session, World world) {
        assertSame(world.session(), SessionManager.getCurrentGameplayMode().getWorldSession());
        assertSame(world.level(), GameServices.level().getCurrentLevel());
        assertSame(world.objects(), GameServices.level().getObjectManager());
        assertSame(world.runtime(), GameServices.zoneRuntimeState());
        assertEquals(11, GameServices.level().getCurrentZone());
        assertEquals(1, GameServices.level().getCurrentAct());
        assertEquals(4, world.runtime().foregroundRoutine(), "direct FG assembly retained");
        assertEquals(8, world.runtime().backgroundRoutine(), "direct BG assembly retained");
        assertArrayEquals(world.geometry(), world.level().getMap().getData(), "corridor layout unchanged");
        assertEquals(320, GameServices.camera().getWidth());
        assertEquals(224, GameServices.camera().getHeight());
        assertEquals(world.lives(), GameServices.gameState().getLives(), "no life loss");
        assertInstanceOf(Knuckles.class, session.player());
        assertSame(GameServices.sprites().getMainPlayable(), session.player());
        assertSame(session.player(), GameServices.camera().getFocusedSprite());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        assertFalse(session.player().getDead() || session.player().isDebugMode() || session.player().isSuperSonic());
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), context + " " + key + ": " + differences);
        }
    }

    private static void safeRestoreBoundary() {
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertFalse(SessionManager.getCurrentGameplayMode().getFadeManager().hasPendingCompletion());
    }

    private static <T extends ObjectInstance> T placed(int index, Class<T> type) {
        T found = null;
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object.getSpawn() != null && object.getSpawn().layoutIndex() == index) {
                assertNull(found, "unique actual raw record " + index);
                found = assertInstanceOf(type, object);
                assertEquals(GameServices.level().getCurrentLevel().getObjects().get(index), object.getSpawn());
            }
        }
        return found;
    }

    private static Swap swap(int index) throws ReflectiveOperationException {
        var object = placed(index, S3kDezGravitySwapObjectInstance.class);
        if (object == null) return new Swap(null, false, false);
        var extra = object.captureRewindState(RewindCaptureContext.none()).objectSubclassExtra();
        // Read the existing authoritative snapshot fields; do not expose or write runtime state.
        var seeded = extra.getClass().getDeclaredMethod("sideLatchSeeded");
        var right = extra.getClass().getDeclaredMethod("playerOnRight");
        seeded.setAccessible(true); right.setAccessible(true);
        return new Swap(object, (boolean) seeded.invoke(extra), (boolean) right.invoke(extra));
    }

    private static String writerProjection(Swap swap) {
        return swap.instance() == null ? "absent" : swap.instance().getSlotIndex()
                + ":" + swap.seeded() + ":" + swap.right();
    }

    private static Tube tube(int index) {
        var object = placed(index, S3kDezGravityTubeObjectInstance.class);
        return object == null ? new Tube(null, false, 0)
                : new Tube(object, object.isRidingForTest(true), object.angleForTest(true));
    }

    private static void placement(ObjectSpawn spawn, int index, int id, int x, int y,
                                  int rawY, int subtype, int flags) {
        assertEquals(index, spawn.layoutIndex());
        assertEquals(id, spawn.objectId());
        assertEquals(x, spawn.x()); assertEquals(y, spawn.y());
        assertEquals(rawY, spawn.rawYWord());
        assertEquals(subtype, spawn.subtype()); assertEquals(flags, spawn.renderFlags());
    }

    private record World(com.openggf.game.session.WorldSession session, Level level,
                         ObjectManager objects, S3kDezZoneRuntimeState runtime,
                         int lives, byte[] geometry) { }
    private record Swap(S3kDezGravitySwapObjectInstance instance, boolean seeded, boolean right) { }
    private record Tube(S3kDezGravityTubeObjectInstance instance, boolean riding, int angle) { }
}
