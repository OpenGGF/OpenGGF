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

    @Test void coldTeamDeflectsExplodingRockFragmentsWithoutLosingShield() throws Exception {
        runColdRoute("shrapnel");
    }

    @Test void coldTeamCrossesFallingLavaWithoutLosingFireShield() throws Exception {
        runColdRoute("fire");
    }

    @Test void coldTeamBouncesFromCrusherWithoutPrematureRepeatHit() throws Exception {
        runColdRoute("crusher");
    }

    @Test void coldTeamLeavesCrusherAndRidesSecondDashElevator() throws Exception {
        runColdRoute("lower-east");
    }

    private void runColdRoute(String route) throws Exception {
        boolean lowerEastRoute = route.equals("lower-east");
        boolean crusherRoute = route.equals("crusher") || lowerEastRoute;
        boolean fireRoute = route.equals("fire") || crusherRoute;
        boolean shrapnelRoute = route.equals("shrapnel") || fireRoute;
        boolean chargeRoute = route.equals("charge") || shrapnelRoute;
        boolean elevatorRoute = route.equals("elevator") || chargeRoute;
        boolean shieldRoute = !route.equals("corkscrew");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz1-sonic-tails-cold-"
                        + route + "-320.bk2"));
        // Short earlier route: intro, rocks/door, platforms, button, capture,
        // scripted ride, native release, lower platform and westbound descent.
        var spots = lowerEastRoute ? Set.of(6230, 6320, 6450, 6540, 6700, 7180, 7260, 7420, 7500)
                : crusherRoute ? Set.of(6020, 6055, 6066, 6090, 6140)
                : fireRoute ? Set.of(5750, 5775, 5800, 5860, 5907, 5908, 5939, 5950)
                : shrapnelRoute ? Set.of(5550, 5585, 5591, 5620, 5650)
                : chargeRoute ? Set.of(5140, 5175, 5200, 5300, 5400)
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
                if (frame == 2000) {
                    // Camera is beyond $D00. Sprite_OnScreen_Test must release
                    // earlier LRZ solids, not retain them in the native slot pool.
                    for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                        if (object instanceof com.openggf.game.sonic3k.objects.LrzSinkingRockObjectInstance
                                || object instanceof com.openggf.game.sonic3k.objects.LrzFallingSpikeObjectInstance
                                || object instanceof com.openggf.game.sonic3k.objects.LrzSmashingSpikePlatformObjectInstance
                                || object instanceof com.openggf.game.sonic3k.objects.LrzCollapsingBridgeInstance) {
                            assertTrue(object.getX() >= 2000,
                                    "stale slot: " + object.getClass().getSimpleName() + " x=" + object.getX());
                        }
                    }
                }
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
                if (shrapnelRoute && frame == 5591) {
                    assertTrue(session.player().hasShield());
                    assertFalse(session.player().isHurt());
                    assertEquals(2787, session.player().getCentreX());
                    assertEquals(1706, session.player().getCentreY());
                    assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.badniks.IwamodokiShrapnelInstance.class)
                            .stream().anyMatch(fragment -> fragment.getCollisionFlags() == 0),
                            "production shield touch must deflect an actual fragment");
                }
                if (fireRoute && frame == 5776) {
                    assertTrue(session.player().hasShield());
                    assertFalse(session.player().isHurt());
                    assertEquals(3224, session.player().getCentreX());
                    assertEquals(1807, session.player().getCentreY());
                }
                if (fireRoute && frame == 5940) {
                    // Native button slot precedes its door: the opening starts
                    // on contact, leaving room to advance here without a side hit.
                    assertEquals(3574, session.player().getCentreX());
                    assertEquals(72, session.player().getXSpeed());
                }
                if (crusherRoute && frame == 6066) {
                    assertEquals(3948, session.player().getCentreX());
                    assertEquals(1758, session.player().getCentreY());
                    assertEquals(0x800, session.player().getXSpeed(),
                            "an already-hit piece cannot bounce the new fire dash again");
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
            if (lowerEastRoute) {
                assertEquals(4917, session.player().getCentreX());
                assertEquals(1712, session.player().getCentreY());
            }
            assertEquals(lowerEastRoute ? 103 : shrapnelRoute ? 99 : shieldRoute ? 95 : 93,
                    session.player().getRingCount());
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        }
    }
}
