package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary cold LRZ1 approach, scripted corkscrew and lower westbound route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzColdRouteCapture {
    @Test void coldTeamTraversesCorkscrewAndRestoresItsHorizontalExit() throws Exception {
        runColdRoute("corkscrew");
    }

    @Test void coldTeamDeflectsShootingTriggerProjectileAndRetainsShield() throws Exception {
        runColdRoute("shield");
    }

    @Test void coldTeamJumpsOffDashElevatorWithoutBeingLiftedAgain() throws Exception {
        runColdRoute("elevator");
    }

    @Test void coldTeamRechargesDashElevatorWithoutFractionalChargeAcceleratingIt() throws Exception {
        runColdRoute("charge");
    }

    private void runColdRoute(String route) throws Exception {
        boolean chargeRoute = route.equals("charge");
        boolean elevatorRoute = route.equals("elevator") || chargeRoute;
        boolean shieldRoute = !route.equals("corkscrew");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz1-sonic-tails-cold-"
                        + route + "-320.bk2"));
        // Short earlier route: intro, rocks/door, platforms, button, capture,
        // scripted ride, native release, lower platform and westbound descent.
        var spots = chargeRoute ? Set.of(5140, 5175, 5200, 5300, 5400)
                : elevatorRoute ? Set.of(4900, 4930, 4945, 4951) : shieldRoute ? Set.of(4510, 4540, 4563, 4590, 4650, 4700, 4780, 4850)
                : Set.of(200, 600, 950, 1300, 1800, 2200, 2600, 2900,
                3100, 3140, 3250, 3370, 3410, 3470, 3530, 3555, 3600,
                3750, 4000, 4200, 4400);
        var checked = new java.util.HashSet<Integer>();
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame)); session.render();
                assertFalse(session.player().getDead(), "death at " + frame);
                if (frame == 3394) {
                    assertTrue(session.player().isObjectControlled());
                    assertEquals(0, session.player().getAngle(), "loc_422E6 clears approach angle");
                }
                if (frame == 3558) {
                    // Comparison-only native row3558, immediately after loc_42396:
                    // position $1235,$057C; velocity $F000,0; ground speed $F000.
                    assertFalse(session.player().isObjectControlled());
                    assertEquals(0x1235, session.player().getCentreX());
                    assertEquals(0x057C, session.player().getCentreY());
                    assertEquals(-0x1000, session.player().getXSpeed());
                    assertEquals(0, session.player().getYSpeed());
                    assertEquals(-0x1000, session.player().getGSpeed());
                }
                if (shieldRoute && frame == 4568) {
                    assertTrue(session.player().hasShield(), "projectile must be deflected, not consume shield");
                    assertFalse(session.player().isHurt());
                    assertEquals(2424, session.player().getCentreX());
                    assertEquals(1233, session.player().getCentreY());
                    assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.LrzShootingTriggerProjectileInstance.class)
                            .stream().anyMatch(shot -> shot.getCollisionFlags() == 0 && shot.xVelocity() < 0),
                            "actual shield touch must turn the incoming shot away and clear its damage");
                }
                if (elevatorRoute && frame == 4951) {
                    // Sonic_Jump skips position integration on launch; the
                    // elevator's SolidObjectFull stale-rider branch only unseats.
                    assertTrue(session.player().getAir());
                    assertEquals(1421, session.player().getCentreY());
                    assertEquals(-0x680, session.player().getYSpeed());
                    assertFalse(session.player().isOnObject());
                }
                if (chargeRoute && frame == 5176) {
                    // Native comparison row: fractional charge decay leaves the
                    // lift moving one pixel, not the low-byte-driven 31 pixels.
                    assertEquals(2208, session.player().getCentreX());
                    assertEquals(1444, session.player().getCentreY());
                    assertFalse(session.player().getAir());
                    assertTrue(session.player().isOnObject());
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                previousInput.set(session, movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var replay = registry.capture();
                assertEquals(forward.entries().keySet(), replay.entries().keySet());
                for (String key : forward.entries().keySet()) {
                    var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                    assertTrue(differences.isEmpty(), "input " + frame + " " + key + ": " + differences);
                }
                registry.restore(saved);
                previousInput.set(session, movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            if (!elevatorRoute) {
                assertEquals(shieldRoute ? 2206 : 2746, session.player().getCentreX());
                assertEquals(shieldRoute ? 1334 : 1186, session.player().getCentreY());
            }
            assertEquals(shieldRoute ? 95 : 93, session.player().getRingCount());
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        }
    }
}
