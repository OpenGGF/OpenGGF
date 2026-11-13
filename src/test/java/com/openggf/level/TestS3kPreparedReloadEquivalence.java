package com.openggf.level;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.resources.PreparableLevelLoader;
import com.openggf.level.resources.PreparedLevelBuild;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A death respawn decodes its act on the preparer thread during the fade to black.
 * The product must equal what the synchronous reload would decode at the install
 * moment (a pure function of the ROM and the captured inputs), a build whose inputs
 * no longer match must be discarded, and a load of anything else must not take it.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kPreparedReloadEquivalence {

    @AfterEach
    void cleanup() {
        LevelLoadPreparer.setEnabledForTests(true);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private static LevelManager load(int zone, int act) {
        HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
        return GameServices.level();
    }

    private static int levelIndex(LevelManager manager) {
        return manager.levels.get(manager.getCurrentZone()).get(manager.getCurrentAct()).levelIndex();
    }

    /** The worker's build, installed, equals a synchronous load of the same act, field by field. */
    @Test
    void workerBuildEqualsSynchronousDecode() throws Exception {
        int[][] acts = {{Sonic3kZoneIds.ZONE_HCZ, 0}, {Sonic3kZoneIds.ZONE_ICZ, 0},
                {Sonic3kZoneIds.ZONE_SOZ, 0}, {Sonic3kZoneIds.ZONE_DEZ, 0}};
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            for (int[] act : acts) {
                LevelManager manager = load(act[0], act[1]);
                PreparableLevelLoader loader = (PreparableLevelLoader) manager.game;
                int index = levelIndex(manager);
                Callable<PreparedLevelBuild> task = loader.prepareReloadBuildTask(index);
                assertNotNull(task, "a death reload of zone " + act[0] + " can be built ahead");
                PreparedLevelBuild build = worker.submit(task).get();
                assertTrue(loader.reloadInputsStillMatch(build));
                Level prepared = loader.installPreparedLevel(build);
                Level synchronous = manager.game.loadLevel(index);
                assertLevelsEqual(synchronous, prepared, "zone " + act[0] + " act " + act[1]);
                SessionManager.clear();
                TestEnvironment.activeGameplayMode();
            }
        } finally {
            worker.shutdownNow();
        }
    }

    /**
     * The respawn installs the build prepared at the fade's start, and the reloaded act
     * (level data after every load step, camera and player) equals a synchronous respawn.
     */
    @Test
    void respawnInstallsThePreparedBuild() throws Exception {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_HCZ, 0);
        int installs = manager.preparedLevelInstallCount();
        Level before = manager.getCurrentLevel();
        assertTrue(LevelReloadPreparationAccess.prepareCurrentLevelReload(manager));
        assertEquals(LevelLoadPreparer.Purpose.RELOAD, manager.pendingPreparedLevelLoadPurpose());
        manager.respawnPlayer();
        assertEquals(installs + 1, manager.preparedLevelInstallCount(), "the respawn used the prepared build");
        assertNull(manager.pendingPreparedLevelLoadPurpose());
        assertNotSame(before, manager.getCurrentLevel());
        Level prepared = manager.getCurrentLevel();
        int[] preparedState = {GameServices.camera().getX(), GameServices.camera().getY(),
                manager.getCurrentZone(), manager.getCurrentAct()};
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();

        LevelLoadPreparer.setEnabledForTests(false);
        LevelManager synchronous = load(Sonic3kZoneIds.ZONE_HCZ, 0);
        synchronous.respawnPlayer();
        assertLevelsEqual(synchronous.getCurrentLevel(), prepared, "respawned");
        assertArrayEquals(new int[]{GameServices.camera().getX(), GameServices.camera().getY(),
                synchronous.getCurrentZone(), synchronous.getCurrentAct()}, preparedState);
    }

    /** Inputs that changed between the fade's start and the load (the main character) discard the build. */
    @Test
    void changedInputsDiscardThePreparedBuild() throws Exception {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_HCZ, 0);
        var config = GameServices.configuration();
        String character = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        int installs = manager.preparedLevelInstallCount();
        assertTrue(LevelReloadPreparationAccess.prepareCurrentLevelReload(manager));
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                "knuckles".equals(character) ? "sonic" : "knuckles");
        try {
            manager.respawnPlayer();
        } finally {
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        }
        assertEquals(installs, manager.preparedLevelInstallCount(), "a stale build must not be installed");
        assertNull(manager.pendingPreparedLevelLoadPurpose());
    }

    /** A wrong guess (the next load is another act) is discarded, and an act transition never takes it. */
    @Test
    void anotherLoadDiscardsThePreparedReload() throws Exception {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_HCZ, 0);
        int installs = manager.preparedLevelInstallCount();
        assertTrue(LevelReloadPreparationAccess.prepareCurrentLevelReload(manager));
        manager.loadZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1);
        assertEquals(installs, manager.preparedLevelInstallCount());
        assertNull(manager.pendingPreparedLevelLoadPurpose());
        assertTrue(LevelReloadPreparationAccess.prepareCurrentLevelReload(manager));
        assertNull(manager.takePreparedActTransitionForTests(levelIndex(manager)),
                "a reload build is not an act-transition build");
    }

    /** With preparation disabled the reload loads synchronously. */
    @Test
    void disabledPreparationLoadsSynchronously() throws Exception {
        LevelLoadPreparer.setEnabledForTests(false);
        LevelManager manager = load(Sonic3kZoneIds.ZONE_HCZ, 0);
        int installs = manager.preparedLevelInstallCount();
        LevelReloadPreparationAccess.prepareCurrentLevelReload(manager);
        manager.respawnPlayer();
        assertEquals(installs, manager.preparedLevelInstallCount());
    }

    static void assertLevelsEqual(Level expected, Level actual, String what) {
        assertEquals(expected.getPatternCount(), actual.getPatternCount(), what + " pattern count");
        byte[] a = new byte[64];
        byte[] b = new byte[64];
        for (int i = 0; i < expected.getPatternCount(); i++) {
            expected.getPattern(i).copyInto(a, 0);
            actual.getPattern(i).copyInto(b, 0);
            assertArrayEquals(a, b, what + " pattern " + i);
        }
        assertEquals(expected.getChunkCount(), actual.getChunkCount(), what + " chunk count");
        for (int i = 0; i < expected.getChunkCount(); i++) {
            Chunk x = expected.getChunk(i);
            Chunk y = actual.getChunk(i);
            assertEquals(x == null, y == null, what + " chunk " + i);
            if (x != null) assertArrayEquals(x.saveState(), y.saveState(), what + " chunk " + i);
        }
        assertEquals(expected.getBlockCount(), actual.getBlockCount(), what + " block count");
        for (int i = 0; i < expected.getBlockCount(); i++) {
            Block x = expected.getBlock(i);
            Block y = actual.getBlock(i);
            assertEquals(x == null, y == null, what + " block " + i);
            if (x != null) assertArrayEquals(x.saveState(), y.saveState(), what + " block " + i);
        }
        assertArrayEquals(expected.getMap().getData(), actual.getMap().getData(), what + " layout");
        assertEquals(expected.getPaletteCount(), actual.getPaletteCount(), what + " palette count");
        for (int p = 0; p < expected.getPaletteCount(); p++) {
            for (int c = 0; c < 16; c++) {
                var x = expected.getPalette(p).getColor(c);
                var y = actual.getPalette(p).getColor(c);
                assertArrayEquals(new byte[]{x.r, x.g, x.b}, new byte[]{y.r, y.g, y.b},
                        what + " palette " + p + " colour " + c);
            }
        }
        assertEquals(expected.getObjects(), actual.getObjects(), what + " object placements");
        assertEquals(expected.getRings(), actual.getRings(), what + " ring placements");
        assertEquals(expected.getMinX(), actual.getMinX(), what);
        assertEquals(expected.getMaxX(), actual.getMaxX(), what);
        assertEquals(expected.getMinY(), actual.getMinY(), what);
        assertEquals(expected.getMaxY(), actual.getMaxY(), what);
        assertEquals(expected.getZoneIndex(), actual.getZoneIndex(), what);
    }
}
