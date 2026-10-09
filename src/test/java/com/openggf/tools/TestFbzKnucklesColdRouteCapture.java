package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.FbzDezPlayerLauncherObjectInstance;
import com.openggf.game.sonic3k.objects.FbzFloatingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.FbzMinibossInstance;
import com.openggf.game.sonic3k.objects.FbzSnakePlatformObjectInstance;
import com.openggf.game.sonic3k.objects.FbzRotatingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.FbzWireCageObjectInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSignpostInstance;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Frozen pad-only Knuckles route, independent of the adaptive author's controller. */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("fbz-route")
class TestFbzKnucklesColdRouteCapture {
    @Test
    void coldKnucklesCompletesActOneWithWholeWorldReplay() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/fbz1-knuckles-cold-320.bk2"));
        assertEquals(22055, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        var spots = Set.of(600, 1800, 2300, 2500, 2750, 3000, 3400, 3800, 4500, 5000,
                6000, 7500, 9000, 10500, 12000, 13200, 14000, 15000, 16000,
                16420, 16470, 16520, 17500, 18000, 19000, 20000, 21000, 21480, 21800);
        var checked = new HashSet<String>();
        var impacts = new HashSet<Integer>();
        boolean bossSeen = false, signSeen = false, resultsSeen = false;
        int reload = -1;
        int replayWindows = 0;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 4, 0, settings);
            assertEquals(320, GameServices.camera().getWidth());
            // The recording starts after the production one-shot setup pass.
            // Consume it with an ordinary neutral GameLoop step, as the capture does.
            session.step(null);
            drawing.draw(session);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                var player = session.player();
                assertInstanceOf(Knuckles.class, player);
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                assertFalse(player.getDead(), "death at " + frame);
                var manager = GameServices.level().getObjectManager();
                for (var boss : manager.activeObjectsOfType(FbzMinibossInstance.class)) {
                    bossSeen = true;
                    impacts.add(boss.scriptedImpactCount());
                }
                signSeen |= !manager.activeObjectsOfType(S3kSignpostInstance.class).isEmpty();
                resultsSeen |= !manager.activeObjectsOfType(S3kResultsScreenObjectInstance.class).isEmpty();
                if (GameServices.level().getCurrentAct() == 1 && reload < 0) reload = frame;
                var pending = new HashSet<String>();
                if (spots.contains(frame)) pending.add("frame-" + frame);
                if (player.getAir() && player.getDoubleJumpFlag() != 0) pending.add("glide");
                if (player.isOnObject() && !player.getAir()) {
                    var owner = player.getLatchedSolidObjectInstance();
                    if (owner instanceof FbzFloatingPlatformObjectInstance platform) {
                        pending.add(platform.getOutOfRangeReferenceX() == 0xD04 ? "upper-lift" : "floating-platform");
                    }
                    if (owner instanceof FbzRotatingPlatformObjectInstance rotor) {
                        pending.add("rotating-platform");
                        if (rotor.getOutOfRangeReferenceX() == 0x2C80 && rotor.getY() < 0x6D0) {
                            pending.add("upper-carousel");
                        }
                    }
                    if (owner instanceof FbzDezPlayerLauncherObjectInstance) pending.add("launcher");
                    if (owner instanceof FbzWireCageObjectInstance) pending.add("wire-cage");
                    if (owner instanceof FbzSnakePlatformObjectInstance) pending.add("snake-platform");
                }
                int checkpoint = GameServices.level().getCheckpointState().getLastCheckpointIndex();
                if (checkpoint > 0) pending.add("checkpoint-" + checkpoint);
                for (var boss : manager.activeObjectsOfType(FbzMinibossInstance.class)) {
                    pending.add("boss-impact-" + boss.scriptedImpactCount());
                }
                if (signSeen) pending.add("signpost");
                if (resultsSeen) pending.add("results");
                pending.removeAll(checked);
                if (pending.isEmpty()) continue;
                // Recorded reload is 21436. Windows stay on one side of that
                // intentional world boundary; this test makes no history-isolation claim.
                int horizon = Math.min(45, movie.getFrameCount() - frame - 1);
                if (frame < 21436) horizon = Math.min(horizon, 21435 - frame);
                assertTrue(horizon > 0, "replay spot must have an independent forward window");
                checked.addAll(pending);
                replayWindows++;
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= horizon; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "restore " + pending + " cycle " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= horizon; n++) {
                        session.step(movie.getFrame(frame + n));
                        drawing.draw(session);
                    }
                    same(forward, registry.capture(), "replay " + pending + " cycle " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            for (int spot : spots) assertTrue(checked.contains("frame-" + spot));
            assertTrue(checked.containsAll(Set.of("glide", "floating-platform", "launcher",
                    "rotating-platform", "upper-carousel", "snake-platform", "signpost", "results", "checkpoint-5")), checked.toString());
            assertTrue(bossSeen && signSeen && resultsSeen);
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6), impacts);
            assertEquals(21436, reload);
            assertEquals(4, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(343, session.player().getCentreX());
            assertEquals(1516, session.player().getCentreY());
            assertFalse(session.player().isControlLocked());
            assertFalse(session.player().isObjectControlled());
            var right = new Bk2FrameInput(0, 8, 0, false, "");
            int releasedX = session.player().getCentreX();
            for (int n = 0; n < 24; n++) {
                session.step(right);
                drawing.draw(session);
            }
            assertTrue(session.player().getCentreX() > releasedX + 8, "ordinary movement in Act 2");
            assertFalse(session.player().getDead());
            System.out.println("FBZ Knuckles frozen route: " + replayWindows + " whole-world replay windows for " + checked.size() + " named spots");
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
