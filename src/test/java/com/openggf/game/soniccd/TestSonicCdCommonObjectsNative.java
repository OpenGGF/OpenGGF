package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.GroundSensor;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native checks of the second common-object batch over {@code rehost-fixtures} movies.
 *
 * <p>Title card: ObjTitleCard_WaitPLC clears {@code ctrlLocked} once PLC 2 has drained at 18 tiles
 * a V-int (Title Card/Main.asm:144-148, 181-186; Functions (General).asm:704-760); the fixture
 * manifest's {@code control_release_trace_row} is the native row where the lock is first clear.
 *
 * <p>Appearances: every dynamic-slot appearance of a second-batch object ID (slot, ID, row) equals
 * the trace's {@code object_appeared} events over the rows where player and camera match, up to an
 * appearance frontier where an unhosted zone object changes slot use (none remains: R51A's, at row
 * 419, went when Quartz Quadrant's platform was hosted). In R11C the Good Future badniks (Palmtree Panic
 * objects) explode at row 0 and their explosions become flowers at row 22 (Explosion/Main.asm:98-106).
 */
class TestSonicCdCommonObjectsNative {
    private static final Pattern APPEARED = Pattern.compile(
            "\"frame\":(\\d+),.*\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");
    private static final Pattern RELEASE = Pattern.compile("\"control_release_trace_row\":\\s*(\\d+)");
    private static final Set<Integer> BATCH_IDS = Set.of(0x06, 0x12, 0x13, 0x14, 0x16, 0x17, 0x1B, 0x1F, 0x24,
            0x2D, 0x3A, 0x3B);
    private static final int FIRST_DYNAMIC_SLOT = 32;

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

    @ParameterizedTest
    @CsvSource({"R11A,260,-1", "R51A,520,-1", "R11C,520,-1", "R12A,520,-1"})
    void titleCardReleaseAndBatchAppearancesEqualNative(String name, int last, int appearanceFrontier) throws Exception {
        CdStagePayload payload = CdStagePayload.valueOf(name);
        String manifest = Files.readString(CdTestMedia.nativeFile("rehost-fixtures/" + name + "/manifest.json"));
        Matcher release = RELEASE.matcher(manifest);
        assertTrue(release.find());
        int nativeRelease = Integer.parseInt(release.group(1));
        int[] engineRelease = {-1};
        Map<ObjectInstance, Integer> seen = new IdentityHashMap<>();
        TreeSet<String> engine = new TreeSet<>();
        var result = CdFixtureReplay.replay(tempDir, payload, last, row -> {
            var level = (CdLevel) GameServices.level().getCurrentLevel();
            if (engineRelease[0] < 0 && !level.flags().ctrlLocked) engineRelease[0] = row;
            for (ObjectInstance object : GameServices.level().getObjectManager().getActiveObjects()) {
                if (!(object instanceof AbstractObjectInstance aoi) || aoi.isDestroyed()) continue;
                if (aoi.getSlotIndex() < FIRST_DYNAMIC_SLOT || seen.containsKey(object)) continue;
                seen.put(object, row);
                if (BATCH_IDS.contains(object.getSpawn().objectId())) {
                    engine.add(String.format("%04d:%d:%02X", row, aoi.getSlotIndex(), object.getSpawn().objectId()));
                }
            }
        });
        int compareTo = result.firstErrorRow() < 0 ? last : result.firstErrorRow() - 1;
        TreeSet<String> nativeSet = new TreeSet<>();
        for (String line : Files.readAllLines(CdTestMedia.nativeFile("rehost-fixtures/" + name + "/trace/aux_state.jsonl"))) {
            Matcher m = APPEARED.matcher(line);
            if (!m.find()) continue;
            int row = Integer.parseInt(m.group(1));
            int slot = Integer.parseInt(m.group(2));
            int id = Integer.parseInt(m.group(3), 16);
            if (row <= compareTo && slot >= FIRST_DYNAMIC_SLOT && BATCH_IDS.contains(id)) {
                nativeSet.add(String.format("%04d:%d:%02X", row, slot, id));
            }
        }
        TreeSet<String> engineInRange = new TreeSet<>();
        for (String e : engine) if (Integer.parseInt(e.substring(0, 4)) <= compareTo) engineInRange.add(e);
        assertEquals(nativeRelease, engineRelease[0], name + ": first row with ctrlLocked clear");
        int end = appearanceFrontier < 0 ? compareTo + 1 : appearanceFrontier;
        assertEquals(before(nativeSet, end), before(engineInRange, end), name + " appearances before row " + end);
        if (appearanceFrontier >= 0) {
            assertTrue(!nativeSet.equals(engineInRange), name + ": the documented frontier no longer differs");
        }
        if (name.equals("R12A")) assertEquals(10, nativeSet.size(), "R12A compares boulders, blocks and animals");
    }

    private static TreeSet<String> before(TreeSet<String> events, int row) {
        TreeSet<String> out = new TreeSet<>();
        for (String e : events) if (Integer.parseInt(e.substring(0, 4)) < row) out.add(e);
        return out;
    }
}
