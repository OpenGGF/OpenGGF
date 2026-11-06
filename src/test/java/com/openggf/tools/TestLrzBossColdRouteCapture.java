package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzEndBossEggCapsule;
import com.openggf.game.sonic3k.objects.LrzEndBossObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary cold Act1 carry, placed shield pickup, mine fight, results and playable HPZ. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzBossColdRouteCapture {
    @Tag("exhaustive-audit")
    @Test void coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState() throws Exception {
        // Flash, missiles, autoscroll changes, bridge/stair/platform contacts,
        // shield pickup, arena descent, mine cycles, defeat, capsule and results.
        // Independent HPZ spots follow the load; no replay window crosses it.
        var spots = Set.of(43781, 43911, 44051, 44311, 44551, 44672, 44727,
                44851, 45011, 45152, 45222, 45433, 45533, 45597, 45696, 45772,
                45851, 46011, 46284, 46359, 46421, 46461, 46525, 46711, 46911,
                47111, 47231, 47351, 47471, 47651, 47711, 47831, 47951, 48131,
                48311, 48551, 48731, 48911, 49091, 49271, 49511, 49751, 49931,
                50171, 50411, 50651, 50831, 51011, 51251, 51491, 51641, 51701,
                51791, 51851, 51971, 52091, 52271, 52511, 52811, 52861, 52972, 53012);
        var checked = new HashSet<Integer>();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-boss-sonic-tails-cold-hpz-320.bk2"));
        assertEquals(53069, movie.getFrameCount());
        boolean sawBoss = false, sawCapsule = false, sawResults = false;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame < 43772) continue;
                var manager = GameServices.level().getObjectManager();
                sawBoss |= !manager.activeObjectsOfType(LrzEndBossObjectInstance.class).isEmpty();
                sawCapsule |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.class).isEmpty();
                sawResults |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.Results.class).isEmpty();
                if (frame == 43772) assertFalse(session.player().hasShield(), "cold carry has no shield");
                if (frame == 46526) {
                    assertEquals(ShieldType.FIRE, session.player().getShieldType(), "placed monitor supplies the shield");
                    assertEquals(28, session.player().getRingCount());
                }
                if (frame >= 46526) assertFalse(session.player().isHurt(), "hurt during encounter/exit at " + frame);
                if (!spots.contains(frame)) continue;
                assertEquals(22, GameServices.level().getCurrentZone());
                assertEquals(frame < 52948 ? 0 : 1, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); drawing.draw(session);
                }
                var replay = registry.capture();
                assertEquals(forward.entries().keySet(), replay.entries().keySet());
                for (String key : forward.entries().keySet()) {
                    var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                    assertTrue(differences.isEmpty(), "input " + frame + " " + key + ": " + differences);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(sawBoss && sawCapsule && sawResults, "real boss/capsule/results publication chain");
            assertEquals(22, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(393, session.player().getCentreX());
            assertEquals(2796, session.player().getCentreY());
            assertEquals(3, session.player().getRingCount());
            assertFalse(session.player().isObjectControlled());
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        }
    }
}
