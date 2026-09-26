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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cold solo Tails route: flight, carrier release, both replica fights and their gated pads. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszTailsColdRouteCapture {
    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void coldGhzApproachProjectsThePreliminaryBoundsBeforeTestingTheLock(int width) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-tails-solo-cold-replicas-320.bk2"));
        var settings = new GameplayCaptureSession.Settings(width, "tails", "", "off", null, null, null);
        boolean spawned = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            assertEquals(width, GameServices.camera().getWidth());
            for (int frame = 0; frame < 4600; frame++) {
                session.step(movie.getFrame(frame)); session.render();
                assertFalse(session.player().getDead(), "approach death at " + frame);
                if (frame == 4350) {
                    var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                    var saved = registry.capture();
                    for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame+n));session.render();}
                    var forward = registry.capture();
                    registry.restore(saved);
                    same(saved, registry.capture(), "pre-lock restore width " + width);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) {session.step(movie.getFrame(frame+n));session.render();}
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
            assertTrue(spawned, "ordinary leftward approach must reach the GHZ allocation gate at width " + width);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int width) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-tails-solo-cold-replicas-" + width + ".bk2"));
        assertEquals(width == 320 ? 9675 : 9683, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(width, "tails", "", "off", null, null, null);
        var spots = Set.of(2215, 2255, 2345, 2465, 2900, 3000, 3240, 3340,
                3500, 3780, 4018, 4150, 4320, 4460, 4511, 4600, 4670, 4730,
                4870, 5020, 5090, 5140, 5200, 5390, 5630, 5700, 5820, 5870,
                5990, 6210, 6400, 6700, 6890, 6970, 7180, 7251, 7350, 7520,
                7690, 7770, 7970, 8050, 8140, 8220, 8390, 8630, 8700, 8840,
                9145, 9300, 9380, 9490, 9600).stream()
                // Wider proportional deadzone reaches the GHZ entry eight inputs later.
                .map(frame -> frame + (width == 800 && frame >= 4501 ? 8 : 0))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var checked = new HashSet<Integer>();
        int ghzHealth = 8, mtzHealth = 8, ghzHits = 0, mtzHits = 0;
        boolean ghzSeen = false, mtzSeen = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
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
            // The eight-frame shift changes the scattered-ring recollection phase.
            assertEquals(width == 320 ? 1 : 0, session.player().getRingCount());
            assertEquals(5888, session.player().getCentreX());
            assertEquals(144, session.player().getCentreY());
            var zone = com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                    .currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            assertFalse(zone.centerNativeArenaCamera(),
                    "both transports released the replica bounds and preliminary projection");
        }
    }

    @Test
    void coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-tails-solo-cold-complete-320.bk2"));
        assertEquals(17670, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        var spots = Set.of(9675, 9780, 9850, 10000, 10120, 10300, 10491, 10570,
                10640, 10690, 10820, 10980, 11100, 11230, 11300, 11400, 11700,
                11800, 11900, 12000, 12120, 12300, 12480, 12600, 12900, 13000,
                13130, 13200, 13350, 13550, 13650, 13850, 14000, 14200, 14400,
                14690, 14750, 14870, 15000, 15200, 15340, 15450, 15800, 16200,
                16500, 17000, 17500);
        var checked = new HashSet<Integer>();
        boolean seen = false;
        int health = 8, hits = 0;
        long outgoingHistory = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // Enable live history after the independent registry replay windows.
                if (frame == 17550) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame)); session.render();
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
                if (frame >= 17550 && GameServices.level().getCurrentZone() == 10) {
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
