package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Solo traversal cannot inherit a team's boss damage or carrier-release timing. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszSoloColdRouteCapture {
    @Test
    void coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-solo-cold-replicas-320.bk2"));
        assertEquals(11200, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        var spots = Set.of(2888, 3440, 3540, 3818, 4168, 4400, 4700, 4930,
                5020, 5070, 5328, 5470, 5630, 6200, 6400, 6600, 6800, 7000,
                7199, 7339, 7450, 7750, 7800, 7850, 7920, 7970, 8150, 8430,
                8458, 8570, 8626, 8690, 8890, 9040, 9300, 9600, 9840, 10000,
                10300, 10410, 10500, 11050);
        var checked = new HashSet<Integer>();
        int ghzHealth = 8, mtzHealth = 8, ghzHits = 0, mtzHits = 0;
        boolean ghzSeen = false, mtzSeen = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at " + frame);
                assertInstanceOf(Sonic.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                var objects = GameServices.level().getObjectManager();
                for (var boss : objects.activeObjectsOfType(SszGhzBossObjectInstance.class)) {
                    ghzSeen = true;
                    int health = boss.hitsRemainingForTest();
                    assertTrue(health <= ghzHealth, "GHZ replica cannot regain health");
                    ghzHits += ghzHealth - health;
                    ghzHealth = health;
                }
                for (var boss : objects.activeObjectsOfType(SszMtzBossObjectInstance.class)) {
                    mtzSeen = true;
                    int health = boss.hitsRemainingForTest();
                    assertTrue(health <= mtzHealth, "MTZ replica cannot regain health");
                    mtzHits += mtzHealth - health;
                    mtzHealth = health;
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
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertTrue(ghzSeen && mtzSeen);
            assertEquals(8, ghzHits);
            assertEquals(8, mtzHits);
            assertTrue(GameServices.level().getObjectManager()
                    .activeObjectsOfType(SszGhzBossObjectInstance.class).isEmpty());
            assertTrue(GameServices.level().getObjectManager()
                    .activeObjectsOfType(SszMtzBossObjectInstance.class).isEmpty());
            assertEquals(10, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(28, session.player().getRingCount());
            assertEquals(5887, session.player().getCentreX());
            assertEquals(1068, session.player().getCentreY());
        }
    }

    @Test
    void coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-solo-cold-complete-320.bk2"));
        assertEquals(19845, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        var spots = Set.of(11200, 11542, 12000, 12500, 12860, 12920,
                13200, 13542, 13700, 13912, 14230, 14310, 14484, 14600,
                14885, 15200, 15500, 15875, 16000, 16260, 16306, 16426,
                16486, 16516, 16600, 16660, 16720, 16800, 16900, 17026,
                17116, 17200, 17300, 17416, 17506, 17566, 17800, 18286,
                18700, 19300, 19700);
        var checked = new HashSet<Integer>();
        boolean seen = false;
        int health = 8, hits = 0;
        long outgoingHistory = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // Enable live history after the independent registry replay windows.
                if (frame == 19750) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame)); session.render();
                assertFalse(session.player().getDead(), "death at " + frame);
                assertInstanceOf(Sonic.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                var bosses = GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance.class);
                for (var boss : bosses) {
                    seen = true;
                    int next = boss.getCollisionProperty();
                    assertTrue(next <= health, "Mecha cannot regain health");
                    hits += health - next; health = next;
                }
                if (frame >= 19750 && GameServices.level().getCurrentZone() == 10) {
                    var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                    if (rewind != null) outgoingHistory = Math.max(outgoingHistory, rewind.currentFrame());
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame + n));session.render();}
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "solo final restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame + n));session.render();}
                same(forward, registry.capture(), "solo final replay at " + frame);
                registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertTrue(seen); assertEquals(8, hits); assertEquals(0, health);
            assertEquals(11, GameServices.level().getCurrentZone(), "actual DEZ load");
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(48, session.player().getCentreX());
            assertEquals(2476, session.player().getCentreY());
            assertTrue(outgoingHistory > 10, "outgoing SSZ history must actually exist");
            var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
            for (int n = 0; n < 30; n++) {session.step(neutral);session.render();}
            var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
            assertNotNull(rewind);
            assertTrue(rewind.currentFrame() < outgoingHistory, "DEZ must isolate the outgoing timeline");
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " " + key + ": " + differences);
        }
    }
}
