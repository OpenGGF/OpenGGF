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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Controller-only cold Knuckles LRZ traversal, miniboss, handoff and direct Hidden Palace arrival. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzKnucklesColdRouteCapture {
    @Tag("exhaustive-audit")
    @Test
    void coldKnucklesReachesMinibossAndRestoresTraversalAndCloudEscape() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-knuckles-cold-miniboss-320.bk2"));
        assertEquals(20500, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // Intro, crusher/bridge, earned monitor, wall jumps, lower switch/door,
        // stairs and crushers, attached cloud and real spindash release, spring,
        // upper return passage, final switch/door and ordinary arena entry. All 70 original windows remain;
        // added windows cross changed wall grabs, both cloud contacts and allocation.
        // The 20454 window ends at the first initialized boss return (20499).
        var spots = Set.of(200, 500, 780, 830, 991, 1050, 1248, 1454, 1660,
                1750, 2074, 2200, 2425, 2510, 2841, 3050, 3347, 3500, 3823,
                4000, 4299, 4390, 4490, 4954, 5050, 5120, 5619, 5800, 6095,
                6250, 6841, 7050, 7177, 7450, 7783, 7900, 8348, 8550, 9044,
                9200, 9609, 9850, 9937, 9943, 9951, 10038, 10559, 11235,
                12031, 13107, 13219, 13845, 14471, 15123, 15210, 15712,
                16138, 16644, 16705, 16765, 16876, 17452, 18028, 18545,
                19221, 19588, 20108, 20200, 20300, 20349,
                3863, 3864, 5659, 5660, 9918, 9919,
                19645, 19646, 19657, 19658, 20354, 20355, 20453, 20454);
        var checked = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame == 9918) {
                    assertTrue(GameServices.level().getObjectManager()
                            .activeObjectsOfType(ToxomisterCloudInstance.class).stream()
                            .noneMatch(cloud -> cloud.routine() == 8 && cloud.attachedPlayerSlot() == 1),
                            "the first placed-cloud attachment has not happened yet");
                }
                if (frame == 9919 || frame == 9937) {
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
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                same(forward, registry.capture(), "replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(11560, session.player().getCentreX());
            assertEquals(1968, session.player().getCentreY());
            assertEquals(10, session.player().getRingCount());
            assertTrue(session.player().isHighPriority(), "crossed the arena's placed priority marker");
            var bosses = GameServices.level().getObjectManager()
                    .activeObjectsOfType(LrzMinibossInstance.class);
            assertEquals(1, bosses.size());
            var boss = bosses.getFirst();
            assertTrue(boss.isArenaGateComplete(), "the ordinary camera/music gate completed");
            assertEquals(2, boss.getState().routine, "the allocated boss actually initialized");
            assertEquals(6, boss.getCollisionProperty(), "initialized health before the fight");
            assertFalse(boss.getState().defeated);
            assertFalse(session.player().isObjectControlled());
            assertFalse(session.player().isObjectControlSuppressesMovement());
            assertFalse(session.player().isControlLocked());
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        }
    }

    @Tag("exhaustive-audit")
    @Test
    void coldKnucklesClearsMinibossAndRestoresFightAndActTwoHandoff() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-knuckles-cold-act1-clear-320.bk2"));
        assertEquals(24028, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // Actual input/owner boundaries, not a shift of the old37 windows:
        // admission, damage/hurt recovery, all six accepted hits, drill phases,
        // defeat/replacement/results, last source and fresh destination ownership.
        // Positive ring recovery/carry remains an inherited gap: this fight carries0.
        var spots = Set.of(20354, 20355, 20454, 20498, 20499,
                20500, 20548, 20850, 20851, 20903, 20904, 20980, 21034, 21226, 21258,
                21296, 21297, 21298, 21334, 21335, 21336,
                21357, 21408, 21757, 21837, 21891, 22083, 22115, 22118,
                22144, 22145, 22146, 22181, 22182, 22183,
                22214, 22265, 22614, 22694, 22748, 22940, 22972, 22975,
                22999, 23000, 23001, 23036, 23037, 23038, 23071, 23072,
                23345, 23346, 23351,
                23397, 23398, 23712, 23713, 23715, 23803, 23804, 23823, 23824,
                23928, 23929, 23973, 23974, 23982);
        assertEquals(68, spots.size());
        assertTrue(spots.stream().allMatch(frame -> frame + 45 < movie.getFrameCount()));
        assertTrue(spots.stream().noneMatch(frame -> frame < 23397 && frame + 45 >= 23397),
                "no outgoing snapshot window crosses the real Level/OM replacement");
        var checked = new HashSet<Integer>();
        var keyCounts = new HashSet<Integer>();
        var healthSeen = new HashSet<Integer>();
        var acceptedHits = new java.util.ArrayList<Integer>();
        int previousHealth = 6;
        boolean defeated = false;
        boolean historyRetired = false;
        int movedFreeTicks = 0;
        long previousDestinationX = -1;
        int sourceHistoryCurrent = -1;
        int sourceHistoryFloor = -1;
        int destinationHistoryCurrent = -1;
        int destinationHistoryFloor = -1;
        int beforeLoadXSub = -1;
        int beforeLoadYSub = -1;
        com.openggf.game.rewind.RewindController sourceController = null;
        Object destinationLevel = null;
        Object destinationObjects = null;
        var drawing = new RouteFrameDrawing(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            var sourcePlayer = assertInstanceOf(Knuckles.class, session.player());
            var sourceWorld = SessionManager.getCurrentWorldSession();
            var sourceLevel = GameServices.level().getCurrentLevel();
            var sourceObjects = GameServices.level().getObjectManager();
            var sourceMode = session.loop().getCurrentGameMode();
            int initialLives = GameServices.gameState().getLives();
            assertEquals(3, initialLives);
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 23307) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                if (frame == 23453) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, false);
                if (frame == 23397) {
                    assertSame(sourceLevel, GameServices.level().getCurrentLevel());
                    assertSame(sourceObjects, GameServices.level().getObjectManager());
                    assertEquals(0, GameServices.level().getCurrentAct());
                    assertEquals(11560, session.player().getCentreX());
                    beforeLoadXSub = session.player().getXSubpixelRaw();
                    beforeLoadYSub = session.player().getYSubpixelRaw();
                    sourceController = SessionManager.getCurrentGameplayMode().getRewindController();
                    assertNotNull(sourceController);
                    sourceHistoryCurrent = sourceController.currentFrame();
                    sourceHistoryFloor = sourceController.earliestAvailableFrame();
                    assertTrue(sourceHistoryCurrent - sourceHistoryFloor > 10,
                            "ordinary source history is nonempty, including any recorded same-world branches");
                }
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                var player = session.player();
                var level = GameServices.level();
                var objects = level.getObjectManager();
                assertFalse(player.getDead(), "death at input " + frame);
                assertEquals(initialLives, GameServices.gameState().getLives(), "life count at input " + frame);
                assertSame(sourcePlayer, player);
                assertSame(sourcePlayer, GameServices.sprites().getMainPlayable());
                assertSame(sourceWorld, SessionManager.getCurrentWorldSession());
                assertSame(sourceMode, session.loop().getCurrentGameMode());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                assertEquals(320, GameServices.camera().getWidth());
                assertEquals(9, level.getCurrentZone());
                for (var boss : objects.activeObjectsOfType(LrzMinibossInstance.class)) {
                    int health = boss.getCollisionProperty();
                    healthSeen.add(health);
                    assertTrue(health <= previousHealth, "the boss never heals at input " + frame);
                    if (health != previousHealth) {
                        assertEquals(previousHealth - 1, health, "one accepted hit at input " + frame);
                        acceptedHits.add(frame);
                        previousHealth = health;
                    }
                    defeated |= boss.getState().defeated;
                    if (frame == 23037) {
                        assertEquals(0, health);
                        assertTrue(boss.getState().defeated, "the actual killing hit starts defeat");
                    }
                }
                if (frame == 23071 || frame == 23072 || frame == 23973 || frame == 23974) {
                    int flows = objects.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow.class).size();
                    assertEquals(frame == 23072 || frame == 23973 ? 1 : 0, flows,
                            "actual drill replacement/signpost retirement at input " + frame);
                }
                if (frame == 23345 || frame == 23346 || frame == 23823 || frame == 23824) {
                    int results = objects.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance.class).size();
                    assertEquals(frame == 23346 || frame == 23823 ? 1 : 0, results,
                            "actual results birth/retirement at input " + frame);
                }
                if (frame == 23397) {
                    assertEquals(1, level.getCurrentAct(), "real seamless act load");
                    assertNotSame(sourceLevel, level.getCurrentLevel());
                    assertNotSame(sourceObjects, objects);
                    destinationLevel = level.getCurrentLevel();
                    destinationObjects = objects;
                    assertEquals(296, player.getCentreX(), "native -$2C00 rebase from11560");
                    assertEquals(beforeLoadXSub, player.getXSubpixelRaw(), "word rebase preserves X fraction");
                    assertEquals(beforeLoadYSub, player.getYSubpixelRaw(), "no Y fraction change at the load");
                    assertEquals(0, player.getRingCount(), "this ordinary trajectory carries0; positive carry is a gap");
                    var controller = SessionManager.getCurrentGameplayMode().getRewindController();
                    assertSame(sourceController, controller);
                    destinationHistoryCurrent = controller.currentFrame();
                    destinationHistoryFloor = controller.earliestAvailableFrame();
                    assertTrue(destinationHistoryCurrent > sourceHistoryCurrent);
                    assertEquals(destinationHistoryCurrent, destinationHistoryFloor,
                            "seamless retirement re-roots at actual current frame, including branch recordings");
                    assertTrue(destinationHistoryFloor > sourceHistoryFloor);
                    historyRetired = true;
                }
                if (frame < 23397) {
                    assertEquals(0, level.getCurrentAct());
                    assertSame(sourceLevel, level.getCurrentLevel());
                    assertSame(sourceObjects, objects);
                } else {
                    assertEquals(1, level.getCurrentAct());
                    assertSame(destinationLevel, level.getCurrentLevel());
                    assertSame(destinationObjects, objects);
                }
                if (frame >= 23982) {
                    long fixedX = ((long) player.getCentreX() << 16) | (player.getXSubpixelRaw() & 0xffffL);
                    if (frame > 23982) {
                        assertTrue(fixedX > previousDestinationX, "successive16.16 Right movement at input " + frame);
                        assertFalse(player.isObjectControlled());
                        assertFalse(player.isObjectControlSuppressesMovement());
                        assertFalse(player.isControlLocked());
                        assertFalse(player.getAir());
                        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
                        assertTrue(objects.activeObjectsOfType(
                                com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance.class).isEmpty());
                        assertTrue(objects.activeObjectsOfType(
                                com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow.class).isEmpty());
                        movedFreeTicks++;
                    }
                    previousDestinationX = fixedX;
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                keyCounts.add(replayLrzFightWindow(session, movie, drawing, frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6), healthSeen);
            assertEquals(java.util.List.of(21297, 21335, 22145, 22182, 23000, 23037), acceptedHits);
            assertTrue(defeated);
            assertTrue(historyRetired);
            assertEquals(45, movedFreeTicks);
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(362, session.player().getCentreX());
            assertEquals(1967, session.player().getCentreY());
            assertEquals(0, session.player().getRingCount(), "observed destination state; no positive reset proof");
            assertEquals(initialLives, GameServices.gameState().getLives());
            assertFalse(session.player().isObjectControlled(), "Act2 movement has been released");
            assertFalse(session.player().isObjectControlSuppressesMovement());
            assertFalse(session.player().isControlLocked());
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            System.out.println("LRZ_FIGHT_REPLAY windows=" + checked.size() + " keys=" + keyCounts
                    + " sourceHistory=" + sourceHistoryCurrent + "/" + sourceHistoryFloor
                    + " destinationHistory=" + destinationHistoryCurrent + "/" + destinationHistoryFloor
                    + " movedFree=" + movedFreeTicks);
        }
    }



    @Tag("exhaustive-audit")
    @Test
    void coldKnucklesReplaysIncomingRouteAndFirstActTwoDoor() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-knuckles-cold-act2-first-door-320.bk2"));
        assertEquals(26635, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // New destination scope only; the untouched fight/handoff method retains its68 windows.
        // Landing26591 has only43 later inputs: these three45-input windows END
        // just before/at/after landing, rather than claiming a post-landing start.
        var spots = Set.of(26427, 26462, 26463, 26498, 26499,
                26509, 26510, 26513, 26545, 26546, 26547,
                26572, 26573, 26574, 26579, 26580, 26589);
        assertEquals(17, spots.size());
        assertTrue(spots.stream().allMatch(frame -> frame >= 23397
                && frame + 45 < movie.getFrameCount()));
        var checked = new HashSet<Integer>();
        var keyCounts = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing(true);
        boolean admitted = false;
        boolean historyRetired = false;
        int sourceCurrent = -1;
        int sourceFloor = -1;
        int loadXSub = -1;
        int loadYSub = -1;
        com.openggf.game.rewind.RewindController sourceController = null;
        Object destinationLevel = null;
        Object destinationObjects = null;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            var sourcePlayer = assertInstanceOf(Knuckles.class, session.player());
            var sourceWorld = SessionManager.getCurrentWorldSession();
            var sourceLevel = GameServices.level().getCurrentLevel();
            var sourceObjects = GameServices.level().getObjectManager();
            var sourceMode = session.loop().getCurrentGameMode();
            assertEquals(com.openggf.game.GameMode.LEVEL, sourceMode);
            assertEquals(3, GameServices.gameState().getLives());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 23307) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                if (frame == 23453) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, false);
                if (frame == 23397) {
                    assertSame(sourceLevel, GameServices.level().getCurrentLevel());
                    assertSame(sourceObjects, GameServices.level().getObjectManager());
                    assertEquals(11560, session.player().getCentreX());
                    loadXSub = session.player().getXSubpixelRaw();
                    loadYSub = session.player().getYSubpixelRaw();
                    sourceController = SessionManager.getCurrentGameplayMode().getRewindController();
                    assertNotNull(sourceController);
                    sourceCurrent = sourceController.currentFrame();
                    sourceFloor = sourceController.earliestAvailableFrame();
                    assertTrue(sourceCurrent - sourceFloor > 10, "nonempty ordinary source history");
                }
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                var player = session.player();
                var level = GameServices.level();
                var objects = level.getObjectManager();
                assertFalse(player.getDead(), "first-door death at input " + frame);
                assertEquals(3, GameServices.gameState().getLives(), "first-door lives at input " + frame);
                assertSame(sourcePlayer, player);
                assertSame(player, GameServices.sprites().getMainPlayable());
                assertSame(sourceWorld, SessionManager.getCurrentWorldSession());
                assertSame(sourceMode, session.loop().getCurrentGameMode());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                assertEquals(320, GameServices.camera().getWidth());
                assertEquals(9, level.getCurrentZone());
                if (frame == 20499) {
                    var bosses = objects.activeObjectsOfType(LrzMinibossInstance.class);
                    assertEquals(1, bosses.size());
                    var boss = bosses.getFirst();
                    assertEquals(2, boss.getState().routine);
                    assertEquals(6, boss.getCollisionProperty(), "actual initialized incoming miniboss");
                    assertTrue(boss.isArenaGateComplete());
                    assertFalse(boss.getState().defeated);
                    assertTrue(player.isHighPriority());
                    assertEquals(10, player.getRingCount());
                    admitted = true;
                }
                if (frame == 23397) {
                    assertEquals(1, level.getCurrentAct());
                    assertNotSame(sourceLevel, level.getCurrentLevel());
                    assertNotSame(sourceObjects, objects);
                    destinationLevel = level.getCurrentLevel();
                    destinationObjects = objects;
                    assertEquals(296, player.getCentreX(), "real -$2C00 word rebase");
                    assertEquals(loadXSub, player.getXSubpixelRaw());
                    assertEquals(loadYSub, player.getYSubpixelRaw());
                    assertEquals(0, player.getRingCount(), "observed zero carry; positive carry remains open");
                    var controller = SessionManager.getCurrentGameplayMode().getRewindController();
                    assertSame(sourceController, controller);
                    assertTrue(controller.currentFrame() > sourceCurrent);
                    assertEquals(controller.currentFrame(), controller.earliestAvailableFrame());
                    assertTrue(controller.earliestAvailableFrame() > sourceFloor);
                    historyRetired = true;
                }
                if (frame < 23397) {
                    assertEquals(0, level.getCurrentAct());
                    assertSame(sourceLevel, level.getCurrentLevel());
                    assertSame(sourceObjects, objects);
                } else {
                    assertEquals(1, level.getCurrentAct());
                    assertSame(destinationLevel, level.getCurrentLevel());
                    assertSame(destinationObjects, objects);
                }
                if (frame >= 24027) {
                    assertFalse(player.isObjectControlled());
                    assertFalse(player.isObjectControlSuppressesMovement());
                    assertFalse(player.isControlLocked());
                    assertFalse(level.hasPendingFreshLevelTransitionBoundary());
                    assertTrue(objects.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance.class).isEmpty());
                    assertTrue(objects.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow.class).isEmpty());
                }
                if (frame >= 24028) assertFalse(player.isHurt(), "new Act2 hurt at input " + frame);
                if (frame == 24027) {
                    assertEquals(362, player.getCentreX());
                    assertEquals(1967, player.getCentreY());
                    assertEquals(0, player.getRingCount());
                }
                if (frame == 24528) assertEquals(1, player.getRingCount());
                if (frame == 24536) assertEquals(2, player.getRingCount());
                if (frame >= 24544) assertEquals(3, player.getRingCount());
                if (frame >= 26427) {
                    var buttons = objects.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.LrzButtonHorizontalObjectInstance.class).stream()
                            .filter(button -> button.getX() == 2744 && button.getY() == 1838).toList();
                    var doors = objects.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.LrzDoorObjectInstance.class).stream()
                            .filter(door -> door.getCentreX() == 2768 && door.triggerIndex() == 1).toList();
                    assertEquals(1, buttons.size());
                    assertEquals(1, doors.size());
                    var button = buttons.getFirst();
                    var door = doors.getFirst();
                    assertEquals(1, button.triggerIndex());
                    if (frame < 26510) {
                        assertEquals(0, button.mappingFrame());
                        assertEquals(0, door.openTimer());
                        assertFalse(door.isOpening());
                        assertFalse(door.isFullyOpen());
                    }
                    if (frame >= 26510 && frame <= 26512) assertEquals(1, button.mappingFrame());
                    if (frame >= 26513) assertEquals(0, button.mappingFrame());
                    if (frame == 26510) {
                        assertEquals(1, door.openTimer());
                        assertTrue(door.isOpening());
                        assertFalse(door.isFullyOpen());
                    }
                    if (frame == 26572) {
                        assertEquals(63, door.openTimer());
                        assertTrue(door.isOpening());
                        assertFalse(door.isFullyOpen());
                    }
                    if (frame >= 26573) {
                        assertEquals(64, door.openTimer());
                        assertFalse(door.isOpening());
                        assertTrue(door.isFullyOpen());
                        assertEquals(1824, door.getCentreY());
                    }
                }
                if (frame == 26580) assertEquals(3, player.getDoubleJumpFlag());
                if (frame == 26590) assertTrue(player.getAir());
                if (frame == 26591) {
                    assertFalse(player.getAir());
                    assertFalse(player.isJumping());
                    assertEquals(0, player.getDoubleJumpFlag());
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                keyCounts.add(replayLrzFightWindow(session, movie, drawing, frame));
            }
            drawing.checkpoint(session);
            assertTrue(admitted);
            assertTrue(historyRetired);
            assertEquals(spots, checked);
            assertEquals(1, keyCounts.size());
            assertTrue(keyCounts.iterator().next() > 0);
            assertEquals(2796, session.player().getCentreX());
            assertEquals(1900, session.player().getCentreY());
            assertEquals(4608, session.player().getXSubpixelRaw());
            assertEquals(15616, session.player().getYSubpixelRaw());
            assertEquals(336, session.player().getXSpeed());
            assertEquals(0, session.player().getYSpeed());
            assertEquals(336, session.player().getGSpeed());
            assertFalse(session.player().getAir());
            assertFalse(session.player().isJumping());
            assertEquals(0, session.player().getDoubleJumpFlag());
            assertEquals(3, session.player().getRingCount());
            assertTrue(session.player().getCentreX() > 2768 + 0x1B, "beyond the opened door solid window");
            System.out.println("LRZ_FIRST_DOOR_REPLAY windows=" + checked.size() + " keys=" + keyCounts);
        }
    }


    @Tag("exhaustive-audit")
    @Test
    void coldKnucklesRestoresActTwoTraversalToTheMiddleCorridor() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-knuckles-cold-act2-middle-320.bk2"));
        assertEquals(33763, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // First switch/door, spike chains, stepped walls, lower tube curve,
        // spring bypass, moving solids, second switch/door, cloud spindash,
        // chained-platform chamber and the long tube into the upper passage.
        var spots = Set.of(25800, 26162, 26500, 26638, 26700, 26850,
                27176, 27400, 27752, 28000, 28177, 28753, 28840, 28895,
                28935, 28964, 29055, 29115, 29200, 29655, 29750, 29847,
                29880, 30000, 30280, 30330, 30836, 30927, 31047, 31120,
                31722, 31800, 32498, 32560, 32660, 32695, 32710, 32800,
                32900, 33000, 33310, 33371, 33462, 33555, 33610, 33670,
                33705, 33717);
        var checked = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (!spots.contains(frame)) continue;
                assertEquals(9, GameServices.level().getCurrentZone());
                assertEquals(1, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Act2 restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                same(forward, registry.capture(), "Act2 replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(session.player().isObjectControlled());
            assertEquals(6678, session.player().getCentreX());
            assertEquals(1132, session.player().getCentreY());
            assertEquals(0, session.player().getRingCount());
        }
    }

    @Tag("exhaustive-audit")
    @Test
    void coldKnucklesRestoresActTwoUpperClimbsAndDoorRelease() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-knuckles-cold-act2-upper-320.bk2"));
        assertEquals(40046, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // Timed flame entry, short hop onto the safe emitter, wall transfers,
        // moving-block clearance, upper checkpoint, short switch hop, door
        // release and crossing, cloud spindash, rebound jump and next door.
        var spots = Set.of(33762, 34020, 34060, 34093, 34110, 34150, 34218,
                34245, 34279, 34339, 34400, 34425, 34575, 34605, 34640,
                34690, 34750, 35229, 35754, 35790, 35875, 35930, 35960,
                36030, 36090, 36141, 36170, 36352, 36877, 36980, 37030,
                37080, 37130, 37546, 37620, 38402, 38440, 38520, 38583,
                38614, 38640, 38645, 38675, 38855, 38916, 39036, 39064,
                39082, 39120, 39160, 39250, 39500, 39990);
        var checked = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (!spots.contains(frame)) continue;
                assertEquals(9, GameServices.level().getCurrentZone());
                assertEquals(1, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Act2 restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                same(forward, registry.capture(), "Act2 replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(session.player().isObjectControlled());
            assertEquals(8501, session.player().getCentreX());
            assertEquals(236, session.player().getCentreY());
            assertEquals(0, session.player().getRingCount());
        }
    }

    @Tag("exhaustive-audit")
    @Test
    void coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz-knuckles-cold-hpz-320.bk2"));
        assertEquals(49537, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        // Reauthored after loc_165AE clears stale glide on transport capture.
        // Door9, flame ledges and tubes, lower westward return, eastern
        // staircase/wall transfers, door10, nozzle drop, rocks/bridges and
        // direct HPZ. Stop source replay windows before the load fade;
        // destination windows exercise the replacement level independently.
        var spots = Set.of(40046, 40090, 40130, 40220, 41090, 41150, 41175, 41205,
                41260, 41280, 41320, 41330, 41350, 41361, 41380, 41420,
                41465, 41490, 41528, 41560, 41620, 41877, 41920, 42020,
                42153, 42180, 42240, 42358, 42420, 42559, 42660, 42759,
                43408, 43449, 43509, 43620, 43660, 43721, 43760, 43800,
                43850, 44183, 44244, 44304, 44350, 44444, 44845, 44880,
                44922, 45000, 45721, 45740, 45790, 45860, 46000, 46150,
                46177, 46227, 46317, 46991, 47047, 47107, 47187, 47429,
                47457, 47497, 47587, 47714, 47747, 47797, 47967, 48329,
                48367, 48437, 48497, 48680, 48735, 48790, 48828, 48857,
                48907, 48987, 49047, 49077, 49127, 49167, 49318, 49418,
                49478);
        var checked = new HashSet<Integer>();
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(320, GameServices.camera().getWidth());
            var outgoingLevel = GameServices.level().getCurrentLevel();
            boolean sawHiddenPalace = false;
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at input " + frame);
                if (frame >= 40046) {
                    int zone = GameServices.level().getCurrentZone();
                    assertTrue(zone == 9 || zone == 22, "only LRZ and HPZ on this route");
                    assertEquals(1, GameServices.level().getCurrentAct(),
                            "Knuckles goes directly to HPZ, never LRZ's boss act");
                    if (frame == 40046) outgoingLevel = GameServices.level().getCurrentLevel();
                    if (zone == 22) {
                        sawHiddenPalace = true;
                        assertNotSame(outgoingLevel, GameServices.level().getCurrentLevel());
                    }
                }
                if (!spots.contains(frame)) continue;
                assertEquals(frame < 49297 ? 9 : 22, GameServices.level().getCurrentZone());
                assertEquals(1, GameServices.level().getCurrentAct());
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                var forward = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "Act2 restore at " + frame);
                session.restoreInputHistory(movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n));
                    drawing.draw(session);
                }
                same(forward, registry.capture(), "Act2 replay at " + frame);
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertTrue(sawHiddenPalace, "real direct HPZ load");
            assertEquals(22, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(spots, checked);
            assertInstanceOf(Knuckles.class, session.player());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(session.player().isObjectControlled());
            assertEquals(1069, session.player().getCentreX());
            assertEquals(748, session.player().getCentreY());
            assertEquals(0, session.player().getRingCount());
        }
    }

    // Only the independently authored LRZ fight/handoff test uses these guards.
    private static int replayLrzFightWindow(GameplayCaptureSession session,
            com.openggf.debug.playback.Bk2Movie movie, RouteFrameDrawing drawing, int frame) {
        var world = SessionManager.getCurrentWorldSession();
        var level = GameServices.level().getCurrentLevel();
        var objects = GameServices.level().getObjectManager();
        var mode = session.loop().getCurrentGameMode();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        int act = GameServices.level().getCurrentAct();
        int lives = GameServices.gameState().getLives();
        drawing.checkpoint(session);
        var saved = registry.capture();
        assertFalse(saved.entries().isEmpty());
        assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame);
        GameplayInputBranchTool.requireRestorableCheckpoint(saved,
                GameServices.level().hasPendingFreshLevelTransitionBoundary());
        for (int n = 1; n <= 45; n++) {
            assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame + n);
            session.step(movie.getFrame(frame + n));
            drawing.draw(session);
            assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame + n);
        }
        var forward = registry.capture();
        // Check the live outgoing owners/fade before restoring, not just WorldSession identity.
        assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame);
        GameplayInputBranchTool.requireRestorableCheckpoint(forward,
                GameServices.level().hasPendingFreshLevelTransitionBoundary());
        registry.restore(saved);
        var restored = registry.capture();
        same(saved, restored, "fight restore at " + frame);
        session.restoreInputHistory(movie.getFrame(frame));
        assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame);
        GameplayInputBranchTool.requireRestorableCheckpoint(restored,
                GameServices.level().hasPendingFreshLevelTransitionBoundary());
        for (int n = 1; n <= 45; n++) {
            assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame + n);
            session.step(movie.getFrame(frame + n));
            drawing.draw(session);
            assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame + n);
        }
        var replayed = registry.capture();
        same(forward, replayed, "fight replay at " + frame);
        assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame);
        GameplayInputBranchTool.requireRestorableCheckpoint(replayed,
                GameServices.level().hasPendingFreshLevelTransitionBoundary());
        registry.restore(saved);
        var finalRestored = registry.capture();
        same(saved, finalRestored, "fight final restore at " + frame);
        session.restoreInputHistory(movie.getFrame(frame));
        assertLrzFightWindowOwners(session, world, level, objects, mode, registry, act, lives, frame);
        GameplayInputBranchTool.requireRestorableCheckpoint(finalRestored,
                GameServices.level().hasPendingFreshLevelTransitionBoundary());
        return saved.entries().size();
    }

    private static void assertLrzFightWindowOwners(GameplayCaptureSession session,
            Object world, Object level, Object objects, Object mode, Object registry,
            int act, int lives, int input) {
        assertSame(world, SessionManager.getCurrentWorldSession(), "fight WorldSession at input " + input);
        assertSame(level, GameServices.level().getCurrentLevel(), "fight Level at input " + input);
        assertSame(objects, GameServices.level().getObjectManager(), "fight ObjectManager at input " + input);
        assertSame(mode, session.loop().getCurrentGameMode(), "fight mode at input " + input);
        assertSame(registry, SessionManager.getCurrentGameplayMode().getRewindRegistry());
        assertEquals(9, GameServices.level().getCurrentZone());
        assertEquals(act, GameServices.level().getCurrentAct());
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertSame(session.player(), GameServices.sprites().getMainPlayable());
        assertInstanceOf(Knuckles.class, session.player());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        assertEquals(320, GameServices.camera().getWidth());
        assertFalse(session.player().getDead(), "fight branch death at input " + input);
        assertEquals(lives, GameServices.gameState().getLives(), "fight branch lives at input " + input);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), context + " " + key + ": " + differences);
        }
    }
}
