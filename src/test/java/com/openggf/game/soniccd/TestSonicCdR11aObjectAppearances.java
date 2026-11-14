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
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
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
import java.util.IdentityHashMap;
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
 * Spawner and slot parity against the native K2 route ({@code rehost-k2/trace-r1}, comparison
 * only): over the level frames where Sonic's position still equals the trace, every object
 * that appears in a dynamic slot ($20-$7F) appears in the same slot, with the same object ID,
 * on the same level frame, and the ring counter equals the trace's {@code rings} column.
 *
 * <p>Native appearances are the recorder's {@code object_appeared} events keyed by
 * {@code vfc} (levelFrames). Engine frame k is levelFrames k+1 (K3b's opening-idle mapping);
 * the input for that step is the trace row first sampled at that levelFrames value.
 */
class TestSonicCdR11aObjectAppearances {
    /**
     * Level frame at which the comparable window ends. Until Obj3DPlant was hosted the frontier was frame 393, where
     * the plant allocates its four children. With it hosted (3D Ramp/Main.asm:24-85) every native appearance before
     * this frame now matches in ID, slot and frame; the window ends because the camera leaves the native route
     * (the 3D ramp's player and camera handling, another lane). No other unhosted object allocates children before it:
     * the unhosted objects that appear ($12 floating block at 393 and 447, $34 spin tunnel tag at 447) spawn as
     * placeholders that take their slot, which is all native shows of them. Move this when the window grows.
     */
    static final int COMPARABLE_WINDOW_END = 471;
    /** The frame at which the plant at slot 34 allocates its four row-and-front children (native slots 39-42). */
    private static final int PLANT_FRAME = 393;

    private static final int FIRST_DYNAMIC_SLOT = 32;
    private static final Pattern APPEARED = Pattern.compile(
            "\"vfc\":(\\d+),\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");

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
    void dynamicSlotAppearancesAndRingCountMatchTheNativeRoute() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Path physics = CdTestMedia.nativeFile("rehost-k2/trace-r1/physics.csv");
        Path aux = CdTestMedia.nativeFile("rehost-k2/trace-r1/aux_state.jsonl");
        Map<Integer, int[]> rows = firstRowPerLevelFrame(physics);
        TreeMap<Integer, TreeSet<String>> nativeAppearances = nativeAppearances(aux);

        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            AbstractPlayableSprite sonic = boot(disc);
            HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
            Map<ObjectInstance, Integer> seen = new IdentityHashMap<>();
            TreeMap<Integer, TreeSet<String>> engineAppearances = new TreeMap<>();
            List<String> ringMismatches = new ArrayList<>();
            int lastMatched = 0;        // player and camera equal native: the spawner's inputs
            int lastPlayerMatched = 0;  // player equals native: touch and ring collection
            String firstDivergence = "none";
            collect(1, seen, engineAppearances);
            for (int levelFrame = 1; ; levelFrame++) {
                int[] row = rows.get(levelFrame);
                if (row == null) break;
                if (levelFrame > 1) {
                    int input = row[5];
                    runner.stepFrame((input & 1) != 0, (input & 2) != 0, (input & 4) != 0, (input & 8) != 0,
                            (input & 0x70) != 0);
                    collect(levelFrame, seen, engineAppearances);
                }
                if ((sonic.getCentreX() & 0xFFFF) != row[3] || (sonic.getCentreY() & 0xFFFF) != row[4]) {
                    firstDivergence += String.format("; levelFrames=%d player engine=%04X,%04X native=%04X,%04X",
                            levelFrame, sonic.getCentreX() & 0xFFFF, sonic.getCentreY() & 0xFFFF, row[3], row[4]);
                    break;
                }
                int rings = GameServices.level().getLevelGamestate().getRings();
                if (rings != row[6]) {
                    ringMismatches.add(String.format("levelFrames=%d rings engine=%d native=%d", levelFrame, rings, row[6]));
                }
                lastPlayerMatched = levelFrame;
                if (lastMatched == levelFrame - 1 && (GameServices.camera().getX() & 0xFFFF) == row[1]
                        && (GameServices.camera().getY() & 0xFFFF) == row[2]) {
                    lastMatched = levelFrame;
                } else if (lastMatched == levelFrame - 1) {
                    firstDivergence = String.format("levelFrames=%d camera engine=%04X,%04X native=%04X,%04X",
                            levelFrame, GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                            row[1], row[2]);
                }
            }
            int nonZeroRingRows = 0;
            for (int frame = 1; frame <= lastPlayerMatched; frame++) {
                if (rows.get(frame) != null && rows.get(frame)[6] != 0) nonZeroRingRows++;
            }

            List<String> mismatches = new ArrayList<>();
            int compared = 0;
            for (int frame = 1; frame <= lastMatched; frame++) {
                TreeSet<String> expected = nativeAppearances.getOrDefault(frame, new TreeSet<>());
                TreeSet<String> actual = engineAppearances.getOrDefault(frame, new TreeSet<>());
                compared += expected.size();
                if (!expected.equals(actual)) {
                    mismatches.add("levelFrames=" + frame + " native=" + expected + " engine=" + actual);
                }
            }
            String summary = String.format("player and camera matched through levelFrames %d, player through %d"
                    + " (divergence: %s); %d native appearances compared; ring counter compared on %d frames,"
                    + " %d of them non-zero", lastMatched, lastPlayerMatched, firstDivergence, compared,
                    lastPlayerMatched, nonZeroRingRows);
            System.out.println("OBJ0 appearances: " + summary);
            assertTrue(lastMatched >= 160 && nonZeroRingRows > 0, "compared range too short: " + summary);
            // Every appearance in the window matches exactly, through and past the plant's allocation at 393.
            assertTrue(lastMatched >= COMPARABLE_WINDOW_END - 1, "compared range ends before the frontier: " + summary);
            String shown = String.join("\n", mismatches.subList(0, Math.min(20, mismatches.size())));
            assertEquals(List.of(), mismatches.subList(0, Math.min(20, mismatches.size())),
                    summary + "; appearances differ from native:\n" + shown);
            TreeSet<String> plant = engineAppearances.getOrDefault(PLANT_FRAME, new TreeSet<>());
            assertTrue(plant.containsAll(List.of("34:2C", "39:2C", "40:2C", "41:2C", "42:2C")),
                    summary + "; the plant did not allocate its four children at the native frame: " + plant);
            assertEquals(List.of(), ringMismatches.subList(0, Math.min(20, ringMismatches.size())),
                    summary + "; ring counter");
        }
    }

    /** Records objects newly present in a dynamic slot after {@code levelFrame}'s iteration. */
    private static void collect(int levelFrame, Map<ObjectInstance, Integer> seen,
                                TreeMap<Integer, TreeSet<String>> appearances) {
        var objects = GameServices.level().getObjectManager();
        if (objects == null) return;
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (!(object instanceof AbstractObjectInstance aoi) || aoi.isDestroyed()) continue;
            int slot = aoi.getSlotIndex();
            if (slot < FIRST_DYNAMIC_SLOT || seen.containsKey(object)) continue;
            seen.put(object, slot);
            appearances.computeIfAbsent(levelFrame, k -> new TreeSet<>())
                    .add(String.format("%d:%02X", slot, object.getSpawn().objectId()));
        }
    }

    private AbstractPlayableSprite boot(CdDiscDataSource disc) throws Exception {
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
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A),
                SonicCdZoneRegistry.actIndex(CdStagePayload.R11A));
        GroundSensor.setLevelManager(GameServices.level());
        return sonic;
    }

    private static TreeMap<Integer, TreeSet<String>> nativeAppearances(Path aux) throws Exception {
        TreeMap<Integer, TreeSet<String>> out = new TreeMap<>();
        for (String line : Files.readAllLines(aux)) {
            Matcher m = APPEARED.matcher(line);
            if (!m.find()) continue;
            int slot = Integer.parseInt(m.group(2));
            if (slot < FIRST_DYNAMIC_SLOT) continue;
            out.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new TreeSet<>())
                    .add(String.format("%d:%02X", slot, Integer.parseInt(m.group(3), 16)));
        }
        return out;
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
