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
 * Spawner and slot parity of the Metallic Madness objects against the native stage fixtures
 * ({@code rehost-fixtures/<payload>/trace}, comparison only): over the level frames where Sonic's position and the
 * camera still equal the trace, every appearance of this lane's objects in a dynamic slot ($20-$7F) occurs on the same
 * level frame with the same object ID as in the native {@code aux_state.jsonl}. Each payload prints how far the engine
 * followed the native route and which of these objects fall inside that window; an object outside the window has no
 * native reference. IDs are per index (r8/object_index_1.asm for R81x, _2 for R82x, _3 for R83x), so each payload has its
 * own list.
 */
class TestMmzObjectsAppearances {
    /**
     * Stages whose every appearance in the compared window is in native's own slot (slot:ID per level frame): the
     * spawner's stored-order walk, the first window after level init and a collected ring's freed slot follow native
     * (SLOTS lane). Their slots are asserted over the whole window, not only up to the first divergence. The names are
     * the stage fixtures'; an authored movie on the same stage is not in the set.
     */
    private static final java.util.Set<String> SLOT_EXACT = java.util.Set.of("R81A", "R81B", "R81C", "R81D", "R82A", "R82B", "R82C", "R82D", "R83C", "R83D");

    private static final int FIRST_DYNAMIC_SLOT = 32;
    private static final Pattern APPEARED = Pattern.compile(
            "\"vfc\":(\\d+),\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");
    /** This lane's implemented IDs by index (object_index_1/2/3, ID = slot + 1). */
    private static final Map<Character, TreeSet<String>> MINE_BY_ACT = Map.of(
            '1', new TreeSet<>(List.of("0F", "22", "24", "27", "28", "2A", "2C", "2F", "36", "37", "38")),
            '2', new TreeSet<>(List.of("09", "0F", "1D", "22", "23", "24", "26", "2B", "2C", "33", "36")),
            '3', new TreeSet<>(List.of("09", "0F", "22", "24", "27", "2A", "2B", "2C", "33", "36")));

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
    @ValueSource(strings = {"R81A", "R81B", "R81C", "R81D", "R82A", "R82B", "R82C", "R82D", "R83C", "R83D"})
    void dynamicSlotAppearancesMatchTheNativeFixture(String payloadName) throws Exception {
        check("rehost-fixtures/" + payloadName, payloadName, payloadName);
    }

    /**
     * The movies this lane authored to reach objects the ten-second fixtures do not
     * ({@code native/rehost-fixtures-mmz2/<movie>/}: {@code <movie>/trace} is a v5 trace of an authored power-on BK2,
     * recorded by tools in {@code rehost-fixtures-mmz2/_tools}; same comparison, same rules).
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"m-scarab/R81B"})
    void authoredMovieAppearancesMatchTheNativeTrace(String movie) throws Exception {
        check("rehost-fixtures-mmz2/" + movie, movie.substring(movie.indexOf('/') + 1), movie);
    }

    private void check(String dir, String payloadName, String label) throws Exception {
        CdStagePayload payload = CdStagePayload.valueOf(payloadName);
        Path cue = CdTestMedia.usaCue();
        Path physics = CdTestMedia.nativeFile(dir + "/trace/physics.csv");
        Path aux = CdTestMedia.nativeFile(dir + "/trace/aux_state.jsonl");
        Map<Integer, int[]> rows = firstRowPerLevelFrame(physics);
        TreeMap<Integer, TreeSet<String>> nativeAppearances = nativeAppearances(aux);

        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            AbstractPlayableSprite sonic = boot(disc, payload);
            HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
            Map<ObjectInstance, Integer> seen = new IdentityHashMap<>();
            TreeMap<Integer, TreeSet<String>> engineAppearances = new TreeMap<>();
            TreeMap<Integer, TreeSet<String>> engineMmz = new TreeMap<>();   // the same, objects of this lane's classes only
            int lastMatched = 0;
            String firstDivergence = "none";
            int earlyFrame = earlyShotLevelFrame(dir);
            collect(1, seen, engineAppearances, engineMmz);
            for (int levelFrame = 1; ; levelFrame++) {
                int[] row = rows.get(levelFrame);
                if (row == null) break;
                if (levelFrame > 1) {
                    int input = row[5];
                    runner.stepFrame((input & 1) != 0, (input & 2) != 0, (input & 4) != 0, (input & 8) != 0,
                            (input & 0x70) != 0);
                    collect(levelFrame, seen, engineAppearances, engineMmz);
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
                if (levelFrame == earlyFrame || levelFrame % 4 == 0) printOnScreenObjects(label, levelFrame);
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
            TreeSet<String> mine = MINE_BY_ACT.get(payloadName.charAt(2));
            List<String> nativeMine = idsAndFrames(nativeAppearances, lastMatched, mine);
            List<String> engineMine = idsAndFrames(engineMmz, lastMatched, mine);   // a lost ring or a child of another lane may reuse an ID
            String summary = String.format("%s: matched through levelFrames %d (stopped: %s); %d native appearances compared;"
                    + " slots exact through %d (first slot divergence: %s); this lane's appearances inside the window: %s",
                    label, lastMatched, firstDivergence, compared, exactThrough, firstForeign, nativeMine);
            System.out.println("MMZOBJ appearances: " + summary);
            assertTrue(lastMatched >= 60, "compared range too short: " + summary);
            assertEquals(nativeMine, engineMine, summary);
            if (SLOT_EXACT.contains(label)) assertEquals(null, firstForeign, "every slot as native's: " + summary);
        }
    }

    /** manifest.json screenshots.early.level_frames. */
    private static int earlyShotLevelFrame(String dir) throws Exception {
        String json = Files.readString(CdTestMedia.nativeFile(dir + "/manifest.json"));
        Matcher m = Pattern.compile("\"early\"\\s*:\\s*\\{[^}]*?\"level_frames\"\\s*:\\s*(\\d+)", Pattern.DOTALL).matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** This lane's objects on screen at a level frame, for choosing native picture regions. */
    private static void printOnScreenObjects(String payload, int levelFrame) {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        StringBuilder out = new StringBuilder();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.mmz.")) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx > -0x10 && sx < 320 + 0x10 && sy > -0x10 && sy < 224 + 0x10) {
                out.append(String.format(" %s@screen(%d,%d)", o.getClass().getSimpleName(), sx, sy));
            }
        }
        if (out.length() > 0) System.out.println("MMZOBJ on screen " + payload + " levelFrames " + levelFrame + ":" + out);
    }

    /** "frame=ID" for this lane's objects, sorted, ignoring slots. */
    private static List<String> idsAndFrames(TreeMap<Integer, TreeSet<String>> appearances, int through, TreeSet<String> mine) {
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
                                TreeMap<Integer, TreeSet<String>> appearances, TreeMap<Integer, TreeSet<String>> mmz) {
        var objects = GameServices.level().getObjectManager();
        if (objects == null) return;
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (!(object instanceof AbstractObjectInstance aoi) || aoi.isDestroyed()) continue;
            int slot = aoi.getSlotIndex();
            if (slot < FIRST_DYNAMIC_SLOT || seen.containsKey(object)) continue;
            seen.put(object, slot);
            String entry = String.format("%d:%02X", slot, object.getSpawn().objectId());
            appearances.computeIfAbsent(levelFrame, k -> new TreeSet<>()).add(entry);
            if (object.getClass().getName().contains(".objects.mmz.")) {
                mmz.computeIfAbsent(levelFrame, k -> new TreeSet<>()).add(entry);
            }
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
