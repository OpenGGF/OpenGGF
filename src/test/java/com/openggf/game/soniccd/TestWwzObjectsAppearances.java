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
 * Spawner and slot parity of the Wacky Workbench objects against the native stage fixtures
 * ({@code rehost-fixtures/<payload>/trace}, comparison only): over the level frames where Sonic's
 * position and the camera still equal the trace, every object that appears in a dynamic slot
 * ($20-$7F) appears in the same slot, with the same object ID, on the same level frame. Each payload prints
 * how far the engine followed the native route and which of this lane's objects fall inside that window; an
 * object outside the window has no native reference.
 */
class TestWwzObjectsAppearances {
    /**
     * Stages whose every appearance in the compared window is in native's own slot (slot:ID per level frame): the
     * spawner's stored-order walk, the first window after level init and a collected ring's freed slot follow native
     * (SLOTS lane). Their slots are asserted over the whole window, not only up to the first divergence.
     */
    private static final java.util.Set<String> SLOT_EXACT = java.util.Set.of("R61A", "R61B", "R61C", "R62A", "R62B", "R62C");

    private static final int FIRST_DYNAMIC_SLOT = 32;
    private static final Pattern APPEARED = Pattern.compile(
            "\"vfc\":(\\d+),\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");
    /**
     * This lane's IDs: launcher, freezer, Goddess statue, piston, electric beams, bouncing and spinning platforms, snake
     * blocks, Eggman statue, Bata-pyon, Semi and Minomusi. The explosions and points that a kill makes in a badnik's
     * slot are checked through the badnik's own entries in {@code object_near}.
     */
    private static final TreeSet<String> MINE = new TreeSet<>(List.of("04", "05", "08", "20", "21", "23", "28", "2A",
            "2B", "30", "32", "33"));
    private static final Pattern NEAR = Pattern.compile(
            "\"vfc\":(\\d+),\"event\":\"object_near\",\"slot\":(\\d+),\"type\":\"0x([0-9A-F]+)\","
                    + "\"x\":\"0x([0-9A-F]+)\",\"y\":\"0x([0-9A-F]+)\",\"routine\":\"0x([0-9A-F]+)\"");

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
    @ValueSource(strings = {"R61A", "R61B", "R61C", "R61D", "R62A", "R62B", "R62C", "R62D", "R63C", "R63D"})
    void dynamicSlotAppearancesMatchTheNativeFixture(String payloadName) throws Exception {
        CdStagePayload payload = CdStagePayload.valueOf(payloadName);
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
            TreeMap<Integer, Map<Integer, int[]>> engineObjects = new TreeMap<>();
            collect(1, seen, engineAppearances);
            snapshot(1, engineObjects);
            for (int levelFrame = 1; ; levelFrame++) {
                int[] row = rows.get(levelFrame);
                if (row == null) break;
                if (levelFrame > 1) {
                    int input = row[5];
                    runner.stepFrame((input & 1) != 0, (input & 2) != 0, (input & 4) != 0, (input & 8) != 0,
                            (input & 0x70) != 0);
                    collect(levelFrame, seen, engineAppearances);
                    snapshot(levelFrame, engineObjects);
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
            System.out.println("WWZOBJ appearances: " + summary);
            assertTrue(lastMatched >= 60, "compared range too short: " + summary);
            Near near = compareNear(aux, engineObjects, lastMatched, 0);
            System.out.println("WWZOBJ near: " + payloadName + " " + near.text());
            assertEquals(nativeMine, engineMine, summary);
            if (SLOT_EXACT.contains(payloadName)) assertEquals(null, firstForeign, "every slot as native's: " + summary);
            // Every near event of this lane's objects (position and routine byte) equals the engine's object in that slot.
            assertEquals(near.compared(), near.equal(), payloadName + " near events: " + near.text());
            // Negative control: the same comparison with the native X shifted by one pixel cannot match.
            Near control = compareNear(aux, engineObjects, lastMatched, 1);
            if (near.compared() > 0) assertEquals(0, control.equal(), payloadName + " control: " + control.text());
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
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.wwz.")) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx > -0x40 && sx < 320 + 0x40 && sy > -0x40 && sy < 224 + 0x40) {
                out.append(String.format(" %s@screen(%d,%d)", o.getClass().getSimpleName(), sx, sy));
            }
        }
        System.out.println("WWZOBJ on screen " + payload + " levelFrames " + levelFrame + ":" + out);
    }

    /** "frame=ID" for this lane's objects, sorted, ignoring slots. */
    private static List<String> idsAndFrames(TreeMap<Integer, TreeSet<String>> appearances, int through) {
        List<String> out = new ArrayList<>();
        for (var entry : appearances.entrySet()) {
            if (entry.getKey() > through) break;
            List<String> frame = new ArrayList<>();
            for (String appearance : entry.getValue()) {
                String id = appearance.substring(appearance.indexOf(':') + 1);
                if (MINE.contains(id)) frame.add(entry.getKey() + "=" + id);
            }
            java.util.Collections.sort(frame);
            out.addAll(frame);
        }
        return out;
    }

    /** This lane's live objects of the level frame: slot to {id, x, y, routine (-1 if not a zone object)}. */
    /** A number per engine object, kept across frames: {@code object_near} events are followed by object, not slot. */
    private static final Map<ObjectInstance, Integer> OBJECT_IDS = new IdentityHashMap<>();

    private static void snapshot(int levelFrame, TreeMap<Integer, Map<Integer, int[]>> out) {
        var objects = GameServices.level().getObjectManager();
        if (objects == null) return;
        Map<Integer, int[]> frame = new TreeMap<>();
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (!(object instanceof AbstractObjectInstance aoi) || aoi.isDestroyed()) continue;
            if (!MINE.contains(String.format("%02X", object.getSpawn().objectId()))) continue;
            int routine = object instanceof com.openggf.game.soniccd.objects.wwz.WwzObject v ? v.routine() : -1;
            int identity = OBJECT_IDS.computeIfAbsent(object, o -> OBJECT_IDS.size());
            frame.put(aoi.getSlotIndex(), new int[] {object.getSpawn().objectId(), object.getX() & 0xFFFF,
                    object.getY() & 0xFFFF, routine, identity});
        }
        out.put(levelFrame, frame);
    }

    /**
     * The trace's {@code object_near} events of this lane's IDs against the engine's objects: position and routine byte,
     * exact, per ID, through the matched window. A native slot is paired with an engine object the first frame it is
     * seen (same ID, position and routine) and followed by that object while its events continue: other lanes' objects
     * (animals, a killed badnik's explosion) shift slot numbers, which the appearance check reports separately.
     */
    private record Near(String text, int compared, int equal) { }

    private static Near compareNear(Path aux, TreeMap<Integer, Map<Integer, int[]>> engine, int through, int shift)
            throws Exception {
        Map<String, int[]> tally = new TreeMap<>();
        Map<String, String> first = new TreeMap<>();
        Map<Integer, int[]> pairs = new java.util.HashMap<>();      // native slot -> {type, engine object id, last frame}
        for (String line : Files.readAllLines(aux)) {
            Matcher m = NEAR.matcher(line);
            if (!m.find()) continue;
            int frame = Integer.parseInt(m.group(1));
            String type = m.group(3);
            if (frame > through || !MINE.contains(type)) continue;
            int slot = Integer.parseInt(m.group(2));
            int id = Integer.parseInt(type, 16);
            int x = Integer.parseInt(m.group(4), 16), y = Integer.parseInt(m.group(5), 16);
            int routine = Integer.parseInt(m.group(6), 16);
            int[] t = tally.computeIfAbsent(type, k -> new int[3]);
            t[0]++;
            Map<Integer, int[]> now = engine.getOrDefault(frame, Map.of());
            int[] pair = pairs.get(slot);
            if (pair != null && (pair[0] != id || pair[2] != frame - 1 && pair[2] != frame)) pair = null;
            int[] mine = null;
            if (pair != null) {
                for (int[] candidate : now.values()) if (candidate[4] == pair[1]) mine = candidate;
            } else {
                for (int[] candidate : now.values()) {
                    boolean taken = false;
                    for (int[] other : pairs.values()) taken |= other[1] == candidate[4] && other[0] == id && other[2] >= frame - 1;
                    if (!taken && candidate[0] == id && candidate[1] == x + shift && candidate[2] == y
                            && (candidate[3] < 0 || candidate[3] == routine)) {
                        mine = candidate;
                        break;
                    }
                }
            }
            boolean same = mine != null && mine[0] == id && mine[1] == x + shift && mine[2] == y
                    && (mine[3] < 0 || mine[3] == routine);
            if (mine != null) pairs.put(slot, new int[] {id, mine[4], frame});
            if (same) t[1]++;
            else {
                first.putIfAbsent(type, String.format("lf %d slot %d native x=%04X y=%04X r=%02X engine=%s", frame, slot,
                        x, y, routine, mine == null ? "no engine object of that ID, position and routine"
                                : String.format("x=%04X y=%04X r=%02X", mine[1], mine[2], mine[3] & 0xFF)));
            }
        }
        StringBuilder text = new StringBuilder();
        int compared = 0;
        int equal = 0;
        for (var e : tally.entrySet()) {
            text.append(String.format("$%s %d/%d", e.getKey(), e.getValue()[1], e.getValue()[0]));
            if (first.containsKey(e.getKey())) text.append(" first mismatch ").append(first.get(e.getKey()));
            text.append("; ");
            compared += e.getValue()[0];
            equal += e.getValue()[1];
        }
        return new Near(text.toString(), compared, equal);
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
