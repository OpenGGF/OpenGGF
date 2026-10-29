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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Solo traversal cannot inherit a team's boss damage or carrier-release timing. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszSoloColdRouteCapture {
    @Tag("exhaustive-audit")
    @Test
    void coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-solo-cold-replicas-320.bk2"));
        assertEquals(8300, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        // 45 replica identities mapped in the SSZ matrix; two fresh cold JVMs precede this data update.
        var spots = Set.of(2888, 3348, 3408, 3500, 3750, 3932, 4048, 4096,
                4164, 4299, 4482, 4855, 5100, 5137, 5300, 5420,
                5540, 5660, 5780, 5900, 6020, 6140, 6180, 6220,
                6260, 6340, 6420, 6516, 6701, 6813, 6889, 6938,
                7055, 7119, 7181, 7241, 7301, 7484, 7802, 7857,
                7980, 8061, 8124, 8149, 8250);
        var checked = new HashSet<Integer>();
        int ghzHealth = 8, mtzHealth = 8, ghzHits = 0, mtzHits = 0;
        boolean ghzSeen = false, mtzSeen = false;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
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
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); drawing.draw(session);
                }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
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
            assertEquals(3, session.player().getRingCount());
            assertEquals(5888, session.player().getCentreX());
            assertEquals(140, session.player().getCentreY(), "second receiving platform after transport");
            assertFalse(session.player().isObjectControlled());
        }
    }

    @Tag("exhaustive-audit")
    @Test
    void coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-solo-cold-complete-320.bk2"));
        assertEquals(14544, movie.getFrameCount());
        int historyStart = movie.getFrameCount() - 120;
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        // 49 complete identities mapped in the SSZ matrix; two fresh cold JVMs precede this data update.
        var spots = Set.of(8300, 8479, 8746, 8804, 8865, 8893, 8930, 8979,
                9077, 9276, 9451, 9517, 9618, 9678, 9800, 9924,
                9951, 10118, 10228, 10431, 11024, 11180, 11250, 11362,
                11493, 11533, 11643, 11691, 11820, 11943, 11988, 12195,
                12323, 12354, 12400, 12500, 12600, 12700, 12800, 12900,
                13000, 13100, 13195, 13300, 13500, 13700, 13900, 14250,
                movie.getFrameCount() - 170);
        var checked = new HashSet<Integer>();
        boolean seen = false;
        int health = 8, hits = 0;
        long outgoingHistory = 0;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // Enable live history after the independent registry replay windows.
                if (frame == historyStart) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
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
                if (frame >= historyStart && GameServices.level().getCurrentZone() == 10) {
                    var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                    if (rewind != null) outgoingHistory = Math.max(outgoingHistory, rewind.currentFrame());
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame + n));drawing.draw(session);}
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "solo final restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame + n));drawing.draw(session);}
                same(forward, registry.capture(), "solo final replay at " + frame);
                registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(seen); assertEquals(8, hits); assertEquals(0, health);
            assertEquals(11, GameServices.level().getCurrentZone(), "actual DEZ load");
            assertEquals(0, GameServices.level().getCurrentAct());
            // loc_6468: the next omitted-presentation dispatch releases the parked entry.
            var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
            if (GameServices.level().hasPendingFreshLevelTransitionBoundary()
                    || GameServices.level().getTransitions().isTitleCardRequested()) {
                session.step(neutral); drawing.draw(session);
            }
            assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
            assertFalse(GameServices.level().getTransitions().isTitleCardRequested());
            assertSame(GameServices.sprites().getMainPlayable(), session.player());
            assertSame(session.player(), GameServices.camera().getFocusedSprite());
            assertFalse(session.player().isObjectControlled());
            assertEquals(0, session.player().getRingCount(), "loc_63B4 publishes fresh DEZ rings");
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(48, session.player().getCentreX());
            assertEquals(2476, session.player().getCentreY());
            assertTrue(outgoingHistory > 10, "outgoing SSZ history must actually exist");
            for (int n = 0; n < 30; n++) {session.step(neutral);drawing.draw(session);}
            var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
            assertNotNull(rewind);
            assertTrue(rewind.currentFrame() < outgoingHistory, "DEZ must isolate the outgoing timeline");
        }
    }

    @Test
    void coldTailsFliesPastTheOpeningSpikesWithNegativeCameraMinimum() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-tails-solo-cold-flight-320.bk2"));
        assertEquals(2556, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        var spots = Set.of(2215, 2255, 2300, 2345, 2465);
        var checked = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at " + frame);
                assertInstanceOf(com.openggf.sprites.playable.Tails.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                if (frame == 2345) {
                    assertEquals(-0x100, GameServices.camera().getMinY());
                    assertTrue(session.player().getDoubleJumpFlag() != 0);
                    assertTrue(session.player().getCentreX() > 1280, "flight clears the raised platform");
                    assertTrue(session.player().getCentreY() < 3030, "signed minY permits ascent");
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame + n));drawing.draw(session);}
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Tails flight restore " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame + n));drawing.draw(session);}
                same(forward, registry.capture(), "Tails flight replay " + frame);
                registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            // Native opening bridge contact (82539953f) keeps the landing subpixel; measured end.
            assertEquals(2091, session.player().getCentreX());
            assertEquals(2953, session.player().getCentreY());
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
