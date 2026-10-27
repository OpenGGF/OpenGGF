package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cold solo Tails route: flight, carrier release, both replica fights and their gated pads. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszTailsColdRouteCapture {
    @Tag("exhaustive-audit")
    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void coldGhzApproachProjectsThePreliminaryBoundsBeforeTestingTheLock(int width) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-tails-solo-cold-replicas-320.bk2"));
        var settings = new GameplayCaptureSession.Settings(width, "tails", "", "off", null, null, null);
        boolean spawned = false;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            assertEquals(width, GameServices.camera().getWidth());
            for (int frame = 0; frame < 4600; frame++) {
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                assertFalse(session.player().getDead(), "approach death at " + frame);
                if (frame == 4350) {
                    var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                    drawing.checkpoint(session);
                    var saved = registry.capture();
                    for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame+n));drawing.draw(session);}
                    var forward = registry.capture();
                    registry.restore(saved);
                    same(saved, registry.capture(), "pre-lock restore width " + width);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame+n));drawing.draw(session);}
                    same(forward, registry.capture(), "pre-lock replay width " + width);
                    registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
                }
                var bosses = GameServices.level().getObjectManager()
                        .activeObjectsOfType(SszGhzBossObjectInstance.class);
                if (!bosses.isEmpty()) {
                    // loc_576E8 + loc_7A244: native camera $160/$7C0,
                    // ship offset +$110/-$40. Widescreen must preserve world geometry.
                    var boss = bosses.iterator().next();
                    assertEquals(0x270, boss.getX());
                    assertEquals(0x780, boss.getY());
                    assertEquals(8, boss.hitsRemainingForTest());
                    assertEquals(0x7C0, GameServices.camera().getY());
                    spawned = true;
                    break;
                }
            }
            drawing.checkpoint(session);
            assertTrue(spawned, "ordinary leftward approach must reach the GHZ allocation gate at width " + width);
        }
    }

    @Tag("exhaustive-audit")
    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void coldMechaEntryRetainsItsNativeWorldBox(int width) throws Exception {
        String file = width == 320 ? "ssz1-tails-solo-cold-complete-320.bk2"
                : "ssz1-tails-solo-cold-complete-800.bk2";
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of("src/test/resources/routes/s3k/" + file));
        var settings = new GameplayCaptureSession.Settings(width, "tails", "", "off", null, null, null);
        boolean initialized = false;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            assertEquals(width, GameServices.camera().getWidth());
            // 320: observed pad approach/lift before native Mecha init11571 (two fresh cold replays).
            // 800: observed pad activation/lift before native Mecha init11605 (two fresh cold replays).
            int first = width == 320 ? 11324 : 11369, second = width == 320 ? 11524 : 11516;
            for (int frame = 0; frame < (width == 320 ? 11572 : 11606); frame++) {
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                assertFalse(session.player().getDead(), "approach death at " + frame);
                if (frame == first || frame == second) {
                    var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                    drawing.checkpoint(session);
                    var saved = registry.capture();
                    for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame+n));drawing.draw(session);}
                    var forward = registry.capture();
                    registry.restore(saved);
                    same(saved, registry.capture(), "Mecha entry restore width " + width);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame+n));drawing.draw(session);}
                    same(forward, registry.capture(), "Mecha entry replay width " + width);
                    registry.restore(saved); session.restoreInputHistory(movie.getFrame(frame));
                }
                var boss = GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance.class)
                        .stream().findFirst().orElse(null);
                if (boss == null || !boss.initExecutedForTest()) continue;
                var state = com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                        .currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
                // sub_575EA pins native Camera_X=$19A0; loc_7B308 adds $20/$120/$160.
                assertEquals(0x19A0 - (width - 320) / 2, GameServices.camera().getX());
                assertEquals(0x19C0, state.bossLeftX());
                assertEquals(0x1AC0, state.bossRightX());
                assertEquals(0x1B00, boss.getX());
                assertEquals(0x660, boss.getY());
                initialized = true;
                break;
            }
            drawing.checkpoint(session);
        }
        assertTrue(initialized, "cold route must actually initialize Mecha");
    }

    @Tag("exhaustive-audit")
    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int width) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-tails-solo-cold-replicas-" + width + ".bk2"));
        assertEquals(width == 320 ? 8350 : 8425, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(width, "tails", "", "off", null, null, null);
        // Retain all 53 original 320 windows. The 2026-10-21 controller additionally covers
        // the observed carrier release, MTZ allocation/every hit/despawn and pad owner changes.
        // The 800 movie and its original 2026-10-15 windows remain independent.
        var spots = width == 320 ? Set.of(2215, 2255, 2345, 2465, 2900, 3000, 3240, 3340, 3500, 3780,
                4018, 4150, 4320, 4460, 4489, 4540, 4580, 4614, 4694, 4779, 4819, 4848, 4882, 4990, 5127,
                5167, 5256, 5319, 5470, 5640, 5785, 6015, 6160, 6221, 6381, 6436, 6511, 6641, 6771, 6830,
                6965, 7019, 7080, 7134, 7249, 7411, 7458, 7553, 7758, 7863, 7923, 8015, 8125)
                : Set.of(2215, 2255, 2345, 2465, 2900, 3000, 3240, 3340, 3500, 3780, 4018, 4150, 4320,
                4460, 4494, 4541, 4577, 4608, 4681, 4759, 4795, 4821, 4852, 4950, 5075, 5111, 5196, 5259,
                5410, 5583, 5730, 5963, 6110, 6172, 6335, 6390, 6467, 6598, 6730, 6793, 6961, 7029, 7104,
                7172, 7314, 7516, 7575, 7693, 7949, 8079, 8146, 8239, 8350);
        if (width == 320) {
            spots = new HashSet<>(spots);
            spots.addAll(Set.of(5779, 5824, 7045, 7279, 7324, 7385, 7455, 7516, 7587,
                    7619, 7651, 7835, 7967, 8116, 8171, 8236));
        }
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
                assertInstanceOf(Tails.class, session.player());
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
            // Controller outcomes: two fresh cold 320 replays retain 34 rings without damage.
            assertEquals(width == 320 ? 34 : 0, session.player().getRingCount());
            assertEquals(5888, session.player().getCentreX());
            assertEquals(144, session.player().getCentreY());
            var zone = com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                    .currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            assertFalse(zone.centerNativeArenaCamera(),
                    "both transports released the replica bounds and preliminary projection");
        }
    }

    @Tag("exhaustive-audit")
    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int width) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-tails-solo-cold-complete-" + width + ".bk2"));
        assertEquals(width == 320 ? 14923 : 15072, movie.getFrameCount());
        int historyStart = movie.getFrameCount() - 120;
        var settings = new GameplayCaptureSession.Settings(width, "tails", "", "off", null, null, null);
        // Retain all 47 original 320 windows and the independent 800 movie data.
        // Added 320 boundaries follow two fresh cold replays: carriers/cloud wrap, pad states,
        // spring launches, Mecha init/eight accepted hits, results and Death Egg launch/hover.
        // Retain all 49 original 800 indices; add reached owner boundaries and the
        // predecessors of wrap, Mecha allocation and every accepted hit to replay their dispatch.
        var spots = width == 800 ? Set.of(8225, 8298, 8347, 8451, 8535, 8660, 8793, 8848, 8897, 8932,
                9022, 9134, 9217, 9308, 9357, 9426, 9635, 9705, 9775, 9844, 9928, 10053, 10179, 10262, 10471,
                10541, 10631, 10680, 11053, 11306, 11359, 11473, 11562, 11651, 11841, 11968, 12120, 12247,
                12374, 12501, 12602, 12678, 12818, 13008, 13388, 13828, 14228, 14628, 14878,
                8425, 8603, 8606, 9008, 9027, 9061, 9064, 9068, 9144, 9145,
                9195, 9196, 9230, 9722, 9872, 9903, 9968, 10020, 10201, 10889,
                10949, 11039, 11282, 11369, 11516, 11567, 11604, 11605, 11632, 11653,
                11717, 11797, 11890, 11891, 12008, 12009, 12166, 12167, 12310, 12311,
                12367, 12368, 12651, 12652, 12697, 12698, 12743, 12744, 12872, 12883,
                13003, 13488, 13518, 13618, 13633, 13713, 14837, 14838, 14868)
                : Set.of(8001, 8074, 8123, 8227, 8310, 8435, 8568, 8623, 8671, 8706, 8796, 8908, 8991,
                9081, 9130, 9199, 9408, 9477, 9547, 9616, 9700, 9825, 9950, 10033, 10242, 10311, 10401,
                10450, 10719, 10781, 10812, 10874, 10921, 10983, 11044, 11134, 11153, 11190, 11230, 11292,
                11336, 11420, 11778, 12150, 12393, 12943, 13489,
                8350, 8529, 8540, 8683, 8781, 9022, 9041, 9084, 9088, 9174,
                9214, 9250, 9743, 9892, 9923, 9988, 10040, 10221, 10698, 10904,
                10964, 11053, 11132, 11324, 11337, 11484, 11524, 11535, 11571, 11600,
                11619, 11683, 11763, 11807, 11886, 11946, 12005, 12128, 12205, 12251,
                12613, 12741, 12742, 12862, 13339, 13369, 13469, 13484, 13565, 14689,
                14719);
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
                assertInstanceOf(Tails.class, session.player());
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
            // loc_6468 owns the deferred assembly after the load-producing tick.
            // An omitted presentation is consumed by the next ordinary capture step.
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
            assertEquals(0x30, session.player().getCentreX());
            // loc_1BE7A reads DEZ1's ($30,$9AC) start; Sonic_Init has radius$13,
            // Tails_Init radius$F. The first ordinary floor pass seats the same feet.
            assertEquals(0xF, session.player().getYRadius());
            assertEquals(0x9AC + 0x13, session.player().getCentreY() + session.player().getYRadius());
            assertTrue(com.openggf.game.internal.NativeArenaCameraFraming.current()
                    .lockedNativeHorizontalCamera().isEmpty(), "SSZ final X lock must retire at the DEZ load");
            assertTrue(outgoingHistory > 10, "outgoing SSZ history must actually exist");
            for (int n = 0; n < 30; n++) {session.step(neutral);drawing.draw(session);}
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
