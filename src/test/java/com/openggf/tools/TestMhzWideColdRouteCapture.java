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
import org.junit.jupiter.api.Tag;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Cold 800px MHZ1-to-FBZ route through the production game loop; pad input only. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzWideColdRouteCapture {
    @Tag("exhaustive-audit")
    @org.junit.jupiter.api.Test
    void wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay() throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                800, "sonic", "", "off", null, null, null);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz2-sonic-incoming-800.bk2"));
        assertEquals(29433, movie.getFrameCount());
        var spots = Set.of(11000, 12000, 13000, 13800, 14500, 15000, 16000,
                17000, 18000, 18900, 19500, 20000, 21000, 21450, 22000, 22350);
        var checked = new HashSet<String>();
        var health = new HashSet<Integer>();
        int reload = -1;
        // Preserve full drawing: MHZ replay controls cannot isolate a drawing gap.
        var drawing = new RouteFrameDrawing(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            assertEquals(800, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "cold wide death at " + frame);
                assertRoster("sonic", false);
                var level = GameServices.level();
                var manager = level.getObjectManager();
                var pending = new HashSet<String>();
                if (spots.contains(frame)) pending.add("wide-route-" + frame);
                for (var boss : manager.activeObjectsOfType(MhzEndBossInstance.class)) {
                    if (boss.getCustomFlag(0x100) == 0) continue;
                    health.add(boss.getState().hitCount);
                    if (boss.isDefeated()) assertEquals(1, boss.getState().hitCount, "native terminal health");
                    if (boss.getState().hitCount == 9) pending.add("admission");
                    if (boss.getState().hitCount == 5) pending.add("chase-hit");
                    if (boss.getState().hitCount == 1 && (boss.getCustomFlag(0x38) & 0x80) != 0) pending.add("last-hit");
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
                if (frame == 29400) {
                    assertEquals(4, level.getCurrentZone());
                    pending.add("fbz-loaded");
                }
                pending.removeAll(checked);
                if (pending.isEmpty()) continue;
                assertEquals(com.openggf.game.GameMode.LEVEL,
                        session.loop().getCurrentGameMode(),
                        "level replay checkpoint entered another mode at " + frame);
                int horizon = Math.min(pending.contains("weather-fade") ? 90 : 45,
                        movie.getFrameCount() - frame - 1);
                // Keep registry replay on one side of the observed fresh load.
                // This test does not substitute for live-history isolation checks.
                if (frame < 28681) horizon = Math.min(horizon, 28680 - frame);
                assertTrue(horizon > 0, "independent replay interval for " + pending);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                var savedLevel = level.getCurrentLevel();
                for (int n = 1; n <= horizon; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                    assertEquals(com.openggf.game.GameMode.LEVEL,
                            session.loop().getCurrentGameMode(),
                            "level replay crossed a mode boundary at " + (frame + n));
                    assertSame(savedLevel, level.getCurrentLevel(),
                            "level replay crossed a fresh load at " + (frame + n));
                }
                var expected = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), pending + " restore " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= horizon; n++) {
                        session.step(movie.getFrame(frame + n));
                        drawing.draw(session);
                    }
                    same(expected, registry.capture(), pending + " replay " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
                checked.addAll(pending);
            }
            drawing.checkpoint(session);
            var required = new HashSet<>(Set.of("admission", "chase-hit", "last-hit", "defeat",
                    "capsule-results", "ship", "ship-carry", "fbz-loaded", "weather-fade"));
            spots.forEach(frame -> required.add("wide-route-" + frame));
            assertEquals(required, checked);
            assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9), health,
                    "loc_767F6 starts escape at one; no ninth hit to zero");
            assertEquals(28681, reload);
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
        var all = GameServices.sprites().getSidekicks();
        long carriers = all.stream().filter(s -> nativeIntroCarrier(s)).count();
        assertTrue(carriers <= (character.equals("sonic") && !pair ? 1 : 0),
                "only solo Sonic receives the native MHZ1 carry-in Tails");
        var followers = all.stream().filter(s -> !nativeIntroCarrier(s)).toList();
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

    /**
     * SpawnLevelMainSprites loc_68C2/loc_68D8 (sonic3k.asm:8212-8229): locked-on MHZ1
     * (SK_alone_flag clear) gives solo Sonic (Player_mode 1) a temporary Obj_Tails carrier
     * at Player_2, which flies off after the drop. It is not a configured follower.
     */
    private static boolean nativeIntroCarrier(com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        return sprite.getCpuController() != null && sprite.getCpuController().isTransientCarrySidekick();
    }
}
