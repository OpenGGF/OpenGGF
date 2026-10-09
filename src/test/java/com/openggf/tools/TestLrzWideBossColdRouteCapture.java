package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
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
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Cold widescreen traversal, placed shield, real mine fight, capsule and playable HPZ. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzWideBossColdRouteCapture {
    @Test
    void coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-boss-sonic-tails-cold-hpz-800.bk2"));
        assertEquals(50659, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var spots = new TreeSet<Integer>();
        for (int frame = 41400; frame <= 50200; frame += 200) spots.add(frame);
        spots.addAll(Set.of(41370, 42200, 42250, 42540, 42960, 43880, 43920,
                44020, 44080, 44600, 44670, 44840, 44860, 45240, 45340,
                45430, 49060, 49160, 49190, 49300, 49340, 49540, 50040,
                50340, 50420, 50480, 50550, 50600));
        var checked = new HashSet<Integer>();
        boolean sawBoss = false, sawCapsule = false, sawResults = false, sawDefeat = false;
        long outgoingHistory = 0;
        int hpzLoad = -1;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 50100) GameServices.configuration().setSessionOverride(
                        SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame < 41370) continue;
                assertEquals(800, GameServices.camera().getWidth());
                assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
                assertEquals(22, GameServices.level().getCurrentZone());
                assertEquals(frame < 50538 ? 0 : 1, GameServices.level().getCurrentAct());
                var manager = GameServices.level().getObjectManager();
                var bosses = manager.activeObjectsOfType(LrzEndBossObjectInstance.class);
                sawBoss |= !bosses.isEmpty();
                sawCapsule |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.class).isEmpty();
                sawResults |= !manager.activeObjectsOfType(LrzEndBossEggCapsule.Results.class).isEmpty();
                if (frame == 44853) assertEquals(14, bosses.getFirst().getCollisionProperty());
                if (frame == 49177) {
                    assertEquals(0, bosses.getFirst().getCollisionProperty());
                    sawDefeat = true;
                }
                if (frame == 44686) {
                    assertEquals(ShieldType.FIRE, session.player().getShieldType(), "placed monitor supplies shield");
                    assertEquals(30, session.player().getRingCount());
                }
                if (frame >= 44686 && frame < 50538) {
                    assertFalse(session.player().isHurt(), "hurt in encounter/exit at " + frame);
                    assertEquals(ShieldType.FIRE, session.player().getShieldType());
                }
                if (frame >= 50100 && frame < 50538) outgoingHistory = Math.max(outgoingHistory,
                        SessionManager.getCurrentGameplayMode().getRewindController().currentFrame());
                if (GameServices.level().getCurrentAct() == 1 && hpzLoad < 0) hpzLoad = frame;
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); drawing.draw(session); }
                    var replay = registry.capture();
                    assertEquals(forward.entries().keySet(), replay.entries().keySet());
                    for (String key : forward.entries().keySet()) {
                        var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                        assertTrue(differences.isEmpty(), "input " + frame + " cycle " + cycle + " " + key + ": " + differences);
                    }
                }
                registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(sawBoss && sawDefeat && sawCapsule && sawResults, "real encounter publication chain");
            assertEquals(50538, hpzLoad);
            assertFalse(session.player().isObjectControlled());
            assertTrue(session.player().getCentreX() > 100, "ordinary movement after HPZ load");
            assertEquals(2796, session.player().getCentreY());
            assertTrue(outgoingHistory > 10);
            assertTrue(SessionManager.getCurrentGameplayMode().getRewindController().currentFrame() < outgoingHistory,
                    "HPZ full load retires the outgoing boss timeline");
            System.out.println("LRZ wide cold boss clear: " + checked.size() + " two-cycle rewind windows");
        }
    }
}
