package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.badniks.ToxomisterCloudInstance;
import com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Controller-only cold Knuckles LRZ1 traversal, miniboss and playable Act2 handoff. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzKnucklesColdRouteCapture {
    @Test
    void coldKnucklesReachesMinibossAndRestoresTraversalAndCloudEscape() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-knuckles-cold-miniboss-320.bk2"));
        assertEquals(20410, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // Intro, crusher/bridge, earned monitor, wall jumps, lower switch/door,
        // stairs and crushers, attached cloud and real spindash release, spring,
        // upper return passage, final switch/door and native arena entry.
        var spots = Set.of(200, 500, 780, 830, 991, 1050, 1248, 1454, 1660,
                1750, 2074, 2200, 2425, 2510, 2841, 3050, 3347, 3500, 3823,
                4000, 4299, 4390, 4490, 4954, 5050, 5120, 5619, 5800, 6095,
                6250, 6841, 7050, 7177, 7450, 7783, 7900, 8348, 8550, 9044,
                9200, 9609, 9850, 9937, 9943, 9951, 10038, 10559, 11235,
                12031, 13107, 13219, 13845, 14471, 15123, 15210, 15712,
                16138, 16644, 16705, 16765, 16876, 17452, 18028, 18545,
                19221, 19588, 20108, 20200, 20300, 20349);
        var checked = new HashSet<Integer>();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame == 9937) {
                    assertTrue(GameServices.level().getObjectManager()
                            .activeObjectsOfType(ToxomisterCloudInstance.class).stream()
                            .anyMatch(cloud -> cloud.routine() == 8 && cloud.attachedPlayerSlot() == 1),
                            "the placed cloud attached through real contact");
                }
                if (frame == 9943) {
                    assertTrue(session.player().getSpindash());
                    assertEquals(9, session.player().getAnimationId(), "loc_8FE50 reads the live animation byte");
                    assertTrue(GameServices.level().getObjectManager()
                            .activeObjectsOfType(ToxomisterCloudInstance.class).stream()
                            .noneMatch(cloud -> cloud.routine() == 8 && cloud.attachedPlayerSlot() == 1),
                            "spindash released the cloud");
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(11362, session.player().getCentreX());
            assertEquals(1969, session.player().getCentreY());
            assertEquals(10, session.player().getRingCount());
            assertTrue(session.player().isHighPriority(), "crossed the arena's placed priority marker");
            assertEquals(1, GameServices.level().getObjectManager()
                    .activeObjectsOfType(LrzMinibossInstance.class).size());
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        }
    }

    @Test
    void coldKnucklesClearsMinibossAndRestoresFightAndActTwoHandoff() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-knuckles-cold-act1-clear-320.bk2"));
        assertEquals(25763, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // Arena entry, hand volleys, damage and ring recovery, drill strikes,
        // low health, defeat/replacement/results and both sides of the act load.
        // The last source replay ends before the seamless load itself.
        var spots = Set.of(20410, 20550, 20740, 20830, 21000, 21250, 21300,
                21347, 21450, 21580, 21640, 21890, 22165, 22185, 22330,
                22500, 22890, 23045, 23052, 23280, 23420, 23780, 23860,
                23907, 24160, 24300, 24520, 24720, 24776, 24777, 24850,
                24920, 25000, 25050, 25150, 25550, 25700);
        var checked = new HashSet<Integer>();
        var healthSeen = new HashSet<Integer>();
        boolean defeated = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                for (var boss : GameServices.level().getObjectManager()
                        .activeObjectsOfType(LrzMinibossInstance.class)) {
                    healthSeen.add(boss.getCollisionProperty());
                    defeated |= boss.getState().defeated;
                }
                if (frame == 25112) {
                    assertEquals(1, GameServices.level().getCurrentAct(), "real seamless act load");
                    assertEquals(296, session.player().getCentreX(), "native -$2C00 rebase");
                    assertEquals(1, session.player().getRingCount(), "ring carries until the Act2 title reset");
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "fight restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    session.render();
                }
                same(forward, registry.capture(), "fight replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6), healthSeen);
            assertTrue(defeated);
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(430, session.player().getCentreX());
            assertEquals(1969, session.player().getCentreY());
            assertEquals(0, session.player().getRingCount(), "Act2 title reset the act counters");
            assertFalse(session.player().isObjectControlled(), "Act2 movement has been released");
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), context + " " + key + ": " + differences);
        }
    }
}
