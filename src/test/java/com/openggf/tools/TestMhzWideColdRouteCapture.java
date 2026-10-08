package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.MhzShipSequenceControllerInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossPaletteFadeController;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Cold 800px MHZ1-to-FBZ route through the production game loop; pad input only. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzWideColdRouteCapture {
    @org.junit.jupiter.api.Test
    void wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay() throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                800, "sonic", "", "off", null, null, null);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz2-sonic-incoming-800.bk2"));
        assertEquals(25548, movie.getFrameCount());
        var spots = Set.of(11000, 12000, 13000, 13800, 14500, 15000, 16000,
                17000, 18000, 18900, 19500, 20000, 21000, 21450, 22000, 22350);
        var checked = new HashSet<String>();
        int reload = -1;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            assertEquals(800, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "cold wide death at " + frame);
                assertRoster("sonic", false);
                var level = GameServices.level();
                var manager = level.getObjectManager();
                var pending = new HashSet<String>();
                if (spots.contains(frame)) pending.add("wide-route-" + frame);
                for (var boss : manager.activeObjectsOfType(MhzEndBossInstance.class)) {
                    if (boss.getCustomFlag(0x100) == 0) continue;
                    if (boss.getState().hitCount == 9) pending.add("admission");
                    if (boss.getState().hitCount == 5) pending.add("chase-hit");
                    if (boss.getState().hitCount == 1) pending.add("last-hit");
                    if (boss.isDefeated()) pending.add("defeat");
                    if (boss.isDefeated() && GameServices.gameState().isEndOfLevelActive()) {
                        pending.add("capsule-results");
                    }
                }
                if (!manager.activeObjectsOfType(MhzShipSequenceControllerInstance.class).isEmpty()
                        && level.getCurrentAct() == 1) {
                    pending.add("ship");
                    if (session.player().isObjectControlled()) pending.add("ship-carry");
                }
                if (!manager.activeObjectsOfType(MhzEndBossPaletteFadeController.class).isEmpty()) {
                    pending.add("weather-fade");
                }
                if (level.getCurrentZone() == 4 && reload < 0) reload = frame;
                if (frame == 25500) {
                    assertEquals(4, level.getCurrentZone());
                    pending.add("fbz-loaded");
                }
                pending.removeAll(checked);
                if (pending.isEmpty()) continue;
                int horizon = Math.min(pending.contains("weather-fade") ? 90 : 45,
                        movie.getFrameCount() - frame - 1);
                // Keep registry replay on one side of the observed fresh load.
                // This test does not substitute for live-history isolation checks.
                if (frame < 25397) horizon = Math.min(horizon, 25396 - frame);
                assertTrue(horizon > 0, "independent replay interval for " + pending);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= horizon; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                var expected = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), pending + " restore " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= horizon; n++) {
                        session.step(movie.getFrame(frame + n));
                        session.render();
                    }
                    same(expected, registry.capture(), pending + " replay " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
                checked.addAll(pending);
            }
            var required = new HashSet<>(Set.of("admission", "chase-hit", "last-hit", "defeat",
                    "capsule-results", "ship", "ship-carry", "fbz-loaded", "weather-fade"));
            spots.forEach(frame -> required.add("wide-route-" + frame));
            assertEquals(required, checked);
            assertEquals(25397, reload);
            assertEquals(4, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled());
            assertFalse(session.player().isControlLocked());
            assertTrue(session.player().getCentreX() > 104, "ordinary movement in playable FBZ");
            System.out.println("MHZ 800px cold chain: " + checked.size() + " named whole-world replay spots");
        }
    }

    private static void assertRoster(String character, boolean pair) {
        assertEquals(com.openggf.game.CharacterKey.parsePersisted(character),
                GameServices.sprites().getMainPlayable().characterKey());
        var followers = GameServices.sprites().getSidekicks();
        assertEquals(pair ? 1 : 0, followers.size());
        if (pair) assertEquals(com.openggf.game.CharacterKey.TAILS, followers.getFirst().characterKey());
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences
                    + (expected.get(key) instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot objects
                    ? " dynamic classes=" + objects.dynamicObjects().stream()
                            .map(entry -> entry.objectId() + "=" + entry.className()).toList() : ""));
        }
    }
}
