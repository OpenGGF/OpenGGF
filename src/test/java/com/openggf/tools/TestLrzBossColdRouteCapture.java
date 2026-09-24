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
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary cold Act1 carry, placed shield pickup, mine fight, results and playable HPZ. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzBossColdRouteCapture {
    @Test void coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState() throws Exception {
        // Flash, missiles, autoscroll changes, bridge/stair/platform contacts,
        // shield pickup, arena descent, mine cycles, defeat, capsule and results.
        // Independent HPZ spots follow the load; no replay window crosses it.
        var spots = Set.of(43770, 43900, 44040, 44300, 44540, 44661, 44716,
                44840, 45000, 45141, 45211, 45422, 45522, 45586, 45685, 45761,
                45840, 46000, 46273, 46348, 46410, 46450, 46514, 46700, 46900,
                47100, 47220, 47340, 47460, 47640, 47700, 47820, 47940, 48120,
                48300, 48540, 48720, 48900, 49080, 49260, 49500, 49740, 49920,
                50160, 50400, 50640, 50820, 51000, 51240, 51480, 51630, 51690,
                51780, 51840, 51960, 52080, 52260, 52500, 52800, 52850, 52950, 52990);
        var checked = new HashSet<Integer>();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-boss-sonic-tails-cold-hpz-320.bk2"));
        assertEquals(53047, movie.getFrameCount());
        boolean sawBoss = false, sawCapsule = false, sawResults = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame < 43761) continue;
                var manager = GameServices.level().getObjectManager();
                sawBoss |= !manager.activeObjectsOfType(LrzEndBossObjectInstance.class).isEmpty();
                sawCapsule |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.class).isEmpty();
                sawResults |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.Results.class).isEmpty();
                if (frame == 43761) assertFalse(session.player().hasShield(), "cold carry has no shield");
                if (frame == 46515) {
                    assertEquals(ShieldType.FIRE, session.player().getShieldType(), "placed monitor supplies the shield");
                    assertEquals(28, session.player().getRingCount());
                }
                if (frame >= 46515) assertFalse(session.player().isHurt(), "hurt during encounter/exit at " + frame);
                if (!spots.contains(frame)) continue;
                assertEquals(22, GameServices.level().getCurrentZone());
                assertEquals(frame < 52926 ? 0 : 1, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
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
                session.restoreInputHistory(movie.getFrame(frame));
            }
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
