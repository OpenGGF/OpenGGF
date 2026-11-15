package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A: Palmtree Panic Act 1 Present loads through LevelManager from the disc, and
 * Sonic's resting position and the camera over the opening idle frames equal the native
 * BizHawk trace {@code rehost-k2/trace-r1/physics.csv} (comparison only).
 *
 * <p>Row N is sampled after native main-loop iteration {@code gameplay_frame_counter}
 * (levelFrames, Main.asm:400). Iteration 1 runs ObjSonic_Init only (Sonic/Main.asm:135-157,
 * ending in rts), so the loaded engine state is compared with levelFrames=1 and engine
 * frame k with the first row whose levelFrames is k+1. Right is first held at trace row
 * 171 (BK2 2559, offset 2388), so rows 0-170 carry no input.
 */
class TestSonicCdR11aOpeningIdle {
    private static final int FIRST_INPUT_ROW = 171;

    @TempDir
    Path tempDir;
    private EngineContext previous;

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
    }

    @AfterEach
    void cleanup() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
    }

    @Test
    void openingIdleFramesMatchTheNativeTrace() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Path trace = CdTestMedia.nativeFile("rehost-k2/trace-r1/physics.csv");
        Map<Integer, int[]> byLevelFrame = firstRowPerLevelFrame(trace);

        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            EngineContext old = EngineServices.current();
            EngineContext injected = new EngineContext(config, old.graphics(), old.audio(), old.roms(),
                    old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(),
                    old.crossGameFeatures());
            EngineServices.configure(injected);

            SonicCdGameModule root = new SonicCdGameModule();
            GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                    injected, root, ModuleResolutionService.LaunchPolicy.DETERMINISTIC, disc);
            GameplaySessionFactory.attachManagers(gameplay, injected);
            injected.graphics().initHeadless();
            GameModuleRegistry.setCurrent(SessionManager.requireCurrentGameModule());

            var team = GameplayTeamBootstrap.registerActiveTeam(
                    SessionManager.requireCurrentGameModule(), GameServices.sprites(), config);
            AbstractPlayableSprite sonic = team.mainSprite();
            GameServices.camera().setFocusedSprite(sonic);
            int zone = SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A);
            int act = SonicCdZoneRegistry.actIndex(CdStagePayload.R11A);
            GameServices.level().loadZoneAndAct(zone, act);
            GroundSensor.setLevelManager(GameServices.level());
            CdLevel level = assertInstanceOf(CdLevel.class, GameServices.level().getCurrentLevel());
            assertEquals(CdStagePayload.R11A, level.payload());

            List<String> mismatches = new ArrayList<>();
            compare(1, byLevelFrame.get(1), sonic, mismatches);
            HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
            int compared = 1;
            for (int frame = 1; ; frame++) {
                int[] row = byLevelFrame.get(frame + 1);
                if (row == null || row[0] >= FIRST_INPUT_ROW) break;
                runner.stepIdleFrames(1);
                compare(frame + 1, row, sonic, mismatches);
                compared++;
            }
            assertTrue(compared > 100, "Expected the idle window to cover over 100 level frames, got " + compared);
            assertTrue(mismatches.isEmpty(), compared + " level frames compared; mismatches:\n"
                    + String.join("\n", mismatches.subList(0, Math.min(20, mismatches.size()))));
        }
    }

    private static void compare(int levelFrame, int[] row, AbstractPlayableSprite sonic, List<String> mismatches) {
        int[] actual = {row[0], GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                sonic.getCentreX() & 0xFFFF, sonic.getCentreY() & 0xFFFF};
        String[] names = {"row", "camera_x", "camera_y", "player_x", "player_y"};
        for (int i = 1; i < actual.length; i++) {
            if (actual[i] != row[i]) {
                mismatches.add(String.format("levelFrames=%d row=%d %s engine=%04X native=%04X",
                        levelFrame, row[0], names[i], actual[i], row[i]));
            }
        }
    }

    /** row, camera_x, camera_y, player_x, player_y of the first row for each levelFrames value. */
    private static Map<Integer, int[]> firstRowPerLevelFrame(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        List<String> header = List.of(lines.getFirst().split(","));
        int frame = header.indexOf("frame"), counter = header.indexOf("gameplay_frame_counter");
        int cx = header.indexOf("camera_x"), cy = header.indexOf("camera_y");
        int px = header.indexOf("player_x"), py = header.indexOf("player_y");
        Map<Integer, int[]> result = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            result.putIfAbsent(Integer.parseInt(c[counter], 16), new int[] {Integer.parseInt(c[frame], 16),
                    Integer.parseInt(c[cx], 16), Integer.parseInt(c[cy], 16),
                    Integer.parseInt(c[px], 16), Integer.parseInt(c[py], 16)});
        }
        return result;
    }
}
