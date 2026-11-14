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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Spawner and slot parity of the shared objects (owner {@code shared}) against the native stage fixtures
 * ({@code rehost-fixtures/<payload>/trace}, comparison only), the method of {@link TestPpzObjectsAAppearances}:
 * over the level frames where Sonic's position and the camera still equal the trace, every appearance of a shared
 * routine's object ID (the stage's {@code <routine>Id} catalogue columns, i.e. its ObjectIndex slots) occurs on
 * the same level frame with the same ID. Slots are printed exact-through, not asserted, since other owners' objects
 * still take or free slots.
 */
class TestSonicCdSharedObjectAppearances {
    /**
     * Stages whose every appearance in the compared window is in native's own slot (slot:ID per level frame): the
     * spawner's stored-order walk, the first window after level init and a collected ring's freed slot follow native
     * (SLOTS lane). Their slots are asserted over the whole window, not only up to the first divergence.
     */
    private static final java.util.Set<String> SLOT_EXACT = java.util.Set.of("R11A", "R31B", "R32A", "R43C", "R51A", "R51B", "R51C", "R53D", "R61B", "R62A", "R71A", "R71B", "R72A", "R72B", "R82A", "R82D");

    private static final int FIRST_DYNAMIC_SLOT = 32;
    private static final Pattern APPEARED = Pattern.compile(
            "\"vfc\":(\\d+),\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");
    /** The shared catalogue's object ID columns (tools/soniccd/object_catalogue_shared.py ROUTINES). */
    private static final List<String> ID_COLUMNS = List.of("rollTunnelId", "blockId", "fallingBlockId", "crusherId",
            "collapseFloorId", "qqzSpikesId", "switchId", "switchByActId", "wwzDoorId", "wwzPlatformId", "spikeBallId",
            "wwzPohBeeId", "hDoorId", "hDoorSplashId", "tunnelSplashId", "spikeChainId", "wwzTubeDoorId", "movingSpringId", "swingId", "hotaruId",
            "qqzTunnelPathId", "wwzTunnelPathId", "mmzTunnelPathId", "qqzPlatformId", "wwzCrusherId", "mmzTubeDoorId", "dangoId");
    private TreeSet<String> mine = new TreeSet<>();

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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"R11A", "R31B", "R32A", "R42C", "R43C", "R51C", "R53D", "R61B", "R63C", "R72A", "R82D", "R62A", "R62D", "R71B", "R72B", "R72C", "R72D", "R51A", "R52A", "R51B", "R71A", "R82A"})
    void dynamicSlotAppearancesMatchTheNativeFixture(String payloadName) throws Exception {
        CdStagePayload payload = CdStagePayload.valueOf(payloadName);
        mine = new TreeSet<>();
        for (String column : ID_COLUMNS) {
            int id = com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue.offset(payload, column);
            if (id >= 0) mine.add(String.format("%02X", id));
        }
        Path cue = CdTestMedia.usaCue();
        Path physics = CdTestMedia.nativeFile("rehost-fixtures/" + payloadName + "/trace/physics.csv");
        Path aux = CdTestMedia.nativeFile("rehost-fixtures/" + payloadName + "/trace/aux_state.jsonl");
        Map<Integer, int[]> rows = firstRowPerLevelFrame(physics);
        TreeMap<Integer, TreeSet<String>> nativeAppearances = nativeAppearances(aux);

        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            AbstractPlayableSprite sonic = boot(disc, payload);
            HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
            Map<ObjectInstance, Integer> seen = new IdentityHashMap<>();
            TreeMap<Integer, TreeSet<String>> engineAppearances = new TreeMap<>();
            int lastMatched = 0;
            String firstDivergence = "none";
            int earlyFrame = earlyShotLevelFrame(payloadName);
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
                    firstDivergence = String.format("levelFrames=%d player engine=%04X,%04X native=%04X,%04X",
                            levelFrame, sonic.getCentreX() & 0xFFFF, sonic.getCentreY() & 0xFFFF, row[3], row[4]);
                    break;
                }
                if ((GameServices.camera().getX() & 0xFFFF) != row[1] || (GameServices.camera().getY() & 0xFFFF) != row[2]) {
                    firstDivergence = String.format("levelFrames=%d camera engine=%04X,%04X native=%04X,%04X",
                            levelFrame, GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                            row[1], row[2]);
                    break;
                }
                lastMatched = levelFrame;
                if (levelFrame == earlyFrame) printOnScreenObjects(payloadName, levelFrame);
            }

            // (1) Exact ID, slot and frame until the first appearance that another lane's object changes: unhosted
            // animals ($24, deleted in a bad future by CheckAnimalPrescence) and flowers ($1F, made by the explosion)
            // take or free slots, which shifts every later slot number but no ID or frame.
            // (2) Across the whole window every appearance of this lane's objects occurs on the same frame with the
            // same ID, whatever its slot.
            String firstForeign = null;
            int exactThrough = lastMatched;
            int compared = 0;
            for (int frame = 1; frame <= lastMatched; frame++) {
                TreeSet<String> expected = nativeAppearances.getOrDefault(frame, new TreeSet<>());
                TreeSet<String> actual = engineAppearances.getOrDefault(frame, new TreeSet<>());
                compared += expected.size();
                if (!expected.equals(actual) && firstForeign == null) {
                    firstForeign = "levelFrames=" + frame + " native=" + expected + " engine=" + actual;
                    exactThrough = frame - 1;
                }
            }
            List<String> nativeMine = idsAndFrames(nativeAppearances, lastMatched);
            List<String> engineMine = idsAndFrames(engineAppearances, lastMatched);
            String summary = String.format("%s: matched through levelFrames %d (stopped: %s); %d native appearances compared;"
                    + " slots exact through %d (first slot divergence: %s); this lane's appearances inside the window: %s",
                    payloadName, lastMatched, firstDivergence, compared, exactThrough, firstForeign, nativeMine);
            System.out.println("Shared object appearances: " + summary);
            assertTrue(lastMatched >= 60, "compared range too short: " + summary);
            assertTrue(!mine.isEmpty(), payloadName + " indexes no shared routine");
            assertEquals(nativeMine, engineMine, summary);
            if (SLOT_EXACT.contains(payloadName)) assertEquals(null, firstForeign, "every slot as native's: " + summary);
        }
    }

    /** manifest.json screenshots.early.level_frames. */
    private static int earlyShotLevelFrame(String payload) throws Exception {
        String json = Files.readString(CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json"));
        Matcher m = Pattern.compile("\"early\"\\s*:\\s*\\{[^}]*?\"level_frames\"\\s*:\\s*(\\d+)", Pattern.DOTALL).matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** This lane's objects on screen at a level frame, for choosing native picture regions. */
    private static void printOnScreenObjects(String payload, int levelFrame) {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        StringBuilder out = new StringBuilder();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.shared.")) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx > -0x40 && sx < 320 + 0x40 && sy > -0x40 && sy < 224 + 0x40) {
                out.append(String.format(" %s@screen(%d,%d)", o.getClass().getSimpleName(), sx, sy));
            }
        }
        System.out.println("Shared objects on screen " + payload + " levelFrames " + levelFrame + ":" + out);
    }

    /** "frame=ID" for this lane's objects, sorted, ignoring slots. */
    private List<String> idsAndFrames(TreeMap<Integer, TreeSet<String>> appearances, int through) {
        List<String> out = new ArrayList<>();
        for (var entry : appearances.entrySet()) {
            if (entry.getKey() > through) break;
            List<String> frame = new ArrayList<>();
            for (String appearance : entry.getValue()) {
                String id = appearance.substring(appearance.indexOf(':') + 1);
                if (mine.contains(id)) frame.add(entry.getKey() + "=" + id);
            }
            java.util.Collections.sort(frame);
            out.addAll(frame);
        }
        return out;
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
