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
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Positions of the shared objects of the second pass (owner {@code shared}) against the native stage fixtures
 * ({@code rehost-fixtures/<payload>/trace/aux_state.jsonl}, comparison only): over the level frames where Sonic's
 * position and the camera still equal the trace, every {@code object_near} row of the object ID (the native
 * sample after that frame's iteration) must be matched by a shared-package engine object of that ID at the same
 * centre. Nothing is taken from the trace but the comparison.
 */
class TestSonicCdShared2ObjectTrace {
    private static final Pattern NEAR = Pattern.compile(
            "\"vfc\":(\\d+),\"event\":\"object_near\",\"slot\":\\d+,\"type\":\"0x([0-9A-F]+)\",\"x\":\"0x([0-9A-F]+)\","
                    + "\"y\":\"0x([0-9A-F]+)\"");

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

    @ParameterizedTest(name = "{0} object {1}")
    @CsvSource({"R71A,25", "R71B,25", "R62D,31", "R72D,23"})
    void objectPositionsMatchTheNativeFixtureWhilePlayerAndCameraMatch(String payloadName, String idHex) throws Exception {
        CdStagePayload payload = CdStagePayload.valueOf(payloadName);
        int id = Integer.parseInt(idHex, 16);
        Path physics = CdTestMedia.nativeFile("rehost-fixtures/" + payloadName + "/trace/physics.csv");
        Path aux = CdTestMedia.nativeFile("rehost-fixtures/" + payloadName + "/trace/aux_state.jsonl");
        Map<Integer, int[]> rows = firstRowPerLevelFrame(physics);
        TreeMap<Integer, TreeSet<String>> near = nativeNear(aux, id);

        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            AbstractPlayableSprite sonic = boot(disc, payload);
            HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
            int lastMatched = 0;
            int compared = 0;
            String firstMismatch = null;
            int trail = 0;
            TreeMap<Integer, TreeSet<String>> engineByFrame = new TreeMap<>();
            StringBuilder after = new StringBuilder();
            for (int levelFrame = 1; ; levelFrame++) {
                int[] row = rows.get(levelFrame);
                if (row == null) break;
                if (levelFrame > 1) {
                    int input = row[5];
                    runner.stepFrame((input & 1) != 0, (input & 2) != 0, (input & 4) != 0, (input & 8) != 0,
                            (input & 0x70) != 0);
                }
                if ((sonic.getCentreX() & 0xFFFF) != row[3] || (sonic.getCentreY() & 0xFFFF) != row[4]) break;
                if ((GameServices.camera().getX() & 0xFFFF) != row[1] || (GameServices.camera().getY() & 0xFFFF) != row[2]) break;
                lastMatched = levelFrame;
                engineByFrame.put(levelFrame, enginePositions(id));
                TreeSet<String> expected = near.get(levelFrame);
                if (expected == null) continue;
                TreeSet<String> actual = enginePositions(id);
                compared += expected.size();
                if (firstMismatch == null && !actual.containsAll(expected)) {
                    firstMismatch = "levelFrames " + levelFrame + " native=" + expected + " engine=" + actual;
                    trail = 6;
                } else if (trail > 0) {
                    trail--;
                    after.append(" | ").append(levelFrame).append(" native=").append(expected).append(" engine=").append(actual);
                }
            }
            int firstAppeared = nativeFirstAppearance(aux, id);
            StringBuilder window = new StringBuilder();
            for (int f = firstAppeared - 2; f <= firstAppeared + 3; f++) {
                window.append(" | ").append(f).append(" engine=").append(engineByFrame.get(f));
            }
            System.out.println("Shared2 first native appearance of " + idHex + " at levelFrames " + firstAppeared + ":" + window);
            String summary = payloadName + " object " + idHex + ": compared " + compared + " native positions through levelFrames "
                    + lastMatched + "; first mismatch: " + firstMismatch + after;
            System.out.println("Shared2 object trace: " + summary);
            assertTrue(compared > 0, "no native position inside the matched window: " + summary);
            assertEquals(null, firstMismatch, summary);
        }
    }

    private static int nativeFirstAppearance(Path aux, int id) throws Exception {
        Pattern appeared = Pattern.compile("\"vfc\":(\\d+),\"event\":\"object_appeared\",\"slot\":\\d+,\"object_type\":\"0x([0-9A-F]+)\"");
        for (String line : Files.readAllLines(aux)) {
            Matcher m = appeared.matcher(line);
            if (m.find() && Integer.parseInt(m.group(2), 16) == id) return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private static TreeSet<String> enginePositions(int id) {
        TreeSet<String> out = new TreeSet<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || o.getSpawn().objectId() != id || !o.getClass().getName().contains(".objects.shared.")) continue;
            out.add(String.format("%04X,%04X", o.getX() & 0xFFFF, o.getY() & 0xFFFF));
        }
        return out;
    }

    private static TreeMap<Integer, TreeSet<String>> nativeNear(Path aux, int id) throws Exception {
        TreeMap<Integer, TreeSet<String>> out = new TreeMap<>();
        for (String line : Files.readAllLines(aux)) {
            Matcher m = NEAR.matcher(line);
            if (!m.find() || Integer.parseInt(m.group(2), 16) != id) continue;
            out.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new TreeSet<>())
                    .add(String.format("%04X,%04X", Integer.parseInt(m.group(3), 16), Integer.parseInt(m.group(4), 16)));
        }
        return out;
    }

    private AbstractPlayableSprite boot(CdDiscDataSource disc, CdStagePayload payload) throws Exception {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        EngineContext old = EngineServices.current();
        EngineContext injected = new EngineContext(config, old.graphics(), old.audio(), old.roms(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures());
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
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload),
                SonicCdZoneRegistry.actIndex(payload));
        GroundSensor.setLevelManager(GameServices.level());
        return sonic;
    }

    /** row, camera_x, camera_y, player_x, player_y, input, rings of the first row per levelFrames. */
    private static Map<Integer, int[]> firstRowPerLevelFrame(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        List<String> header = List.of(lines.getFirst().split(","));
        int frame = header.indexOf("frame"), counter = header.indexOf("gameplay_frame_counter");
        int cx = header.indexOf("camera_x"), cy = header.indexOf("camera_y");
        int px = header.indexOf("player_x"), py = header.indexOf("player_y");
        int input = header.indexOf("input"), rings = header.indexOf("rings");
        Map<Integer, int[]> result = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            result.putIfAbsent(Integer.parseInt(c[counter], 16), new int[] {Integer.parseInt(c[frame], 16),
                    Integer.parseInt(c[cx], 16), Integer.parseInt(c[cy], 16), Integer.parseInt(c[px], 16),
                    Integer.parseInt(c[py], 16), Integer.parseInt(c[input], 16), Integer.parseInt(c[rings], 16)});
        }
        return result;
    }
}
