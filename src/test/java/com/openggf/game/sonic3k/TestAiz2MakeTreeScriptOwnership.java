package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizBgTreeInstance;
import com.openggf.game.sonic3k.objects.AizBgTreeSpawnerInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Obj_AIZ2MakeTree script ownership through the live AIZ2 ObjectManager
 * (docs/skdisasm/sonic3k.asm:105553-105583).
 *
 * <ul>
 *   <li>loc_50632 (:105564-105568): the terminator test precedes every
 *       allocation attempt, so the maker outlives its last tree by one pass.</li>
 *   <li>loc_50640 (:105571-105580): one unsigned word compare and at most one
 *       AllocateObjectAfterCurrent per pass. A failure keeps $30. The second
 *       script word becomes the tree's priority.</li>
 * </ul>
 *
 * <p>Only types and fields that predate the corrected owner are referenced, so
 * the same file is the old-source control. Thresholds are driven through the
 * AIZ events' smooth scroll X, the engine's Events_fg_1. Camera X stays at
 * $4500: at or past the $44D0 activation, below the $4880 tree delete.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestAiz2MakeTreeScriptOwnership {
    private static final int CAMERA_X = 0x4500;
    private static final int CAMERA_Y = 0x0200;
    private static final int SMOOTH_BASE = 0x4500;
    /** AIZMakeTreeScript thresholds, locked-on ROM $23C13C. */
    private static final int[] ROM_THRESHOLDS = {
            0x000, 0x032, 0x08E, 0x103, 0x179, 0x1C6, 0x233, 0x2A0, 0x30A,
            0x37C, 0x3C7, 0x401, 0x439, 0x46E, 0x4CA, 0x50C, 0x557};

    private HeadlessTestFixture fixture;
    private ObjectManager objects;
    private Sonic3kAIZEvents events;
    private Camera camera;
    private int frame;

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        objects = GameServices.level().getObjectManager();
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        events = manager.getAizEvents();
        assertNotNull(events, "AIZ2 events must be live");
        camera = fixture.camera();
        camera.setX((short) CAMERA_X);
        camera.setY((short) CAMERA_Y);
        camera.setMinX((short) CAMERA_X);
        camera.setMaxX((short) CAMERA_X);
        events.setAiz2ResizeRoutine(0x10);
        events.setBattleshipSmoothScrollXRaw(SMOOTH_BASE);
        // Placement around the fixed camera settles before the maker is added.
        stepObjects();
        stepObjects();
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void makeTreeAllocatesItsTreeAfterItselfAndTheTreeRunsThatPass() throws Exception {
        int makerSlot = slotAboveEveryOccupant();
        addMakeTreeAt(makerSlot);
        setDistance(0);

        stepObjects();

        List<AizBgTreeInstance> trees = trees();
        assertEquals(1, trees.size(), "the activation pass falls into loc_50632 and allocates entry 0");
        assertEquals(makerSlot + 1, trees.getFirst().getSlotIndex(),
                "AllocateObjectAfterCurrent takes the next free SST after the maker, not the lowest free SST");
        assertTrue(getBoolean(trees.getFirst(), "positionInitialized"),
                "a tree allocated after the maker runs Obj_AIZ2BGTree init and Move in the same pass");
    }

    @Test
    void makeTreeMakesOneAllocationAttemptPerPass() {
        addMakeTreeAt(slotAboveEveryOccupant());
        setDistance(0);
        stepObjects();
        assertEquals(1, trees().size());

        // Entries 1..5 ($32..$1C6) are all due at once.
        setDistance(0x200);
        stepObjects();
        assertEquals(2, trees().size(), "loc_50640 makes one allocation attempt per pass");
        stepObjects();
        assertEquals(3, trees().size(), "the next due entry waits for the next pass");
    }

    @Test
    void failedAllocationRetriesTheSameScriptEntryOnTheNextPass() throws Exception {
        int makerSlot = slotAboveEveryOccupant();
        AizBgTreeSpawnerInstance maker = addMakeTreeAt(makerSlot);
        List<Filler> fillers = fillEveryFreeDynamicSlot();
        setDistance(0);

        stepObjects();

        assertEquals(0, trees().size(), "no SST is free after the maker, so AllocateObjectAfterCurrent fails");
        assertEquals(0, getInt(maker, "scriptIndex"), "bne.s locret_50662 skips the $30 write");

        Filler above = fillers.stream()
                .filter(f -> f.getSlotIndex() > makerSlot)
                .min(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .orElseThrow();
        int freedSlot = above.getSlotIndex();
        objects.removeDynamicObject(above);

        stepObjects();

        List<AizBgTreeInstance> trees = trees();
        assertEquals(1, trees.size(), "the same entry is retried once an SST after the maker is free");
        assertEquals(freedSlot, trees.getFirst().getSlotIndex());
        assertEquals(1, getInt(maker, "scriptIndex"), "$30 advances only with a successful allocation");
    }

    @Test
    void everyAizMakeTreeScriptEntryAllocatesATree() {
        addMakeTreeAt(slotAboveEveryOccupant());
        for (int threshold : ROM_THRESHOLDS) {
            setDistance(threshold);
            stepObjects();
        }

        assertEquals(ROM_THRESHOLDS.length, trees().size(),
                "all 17 AIZMakeTreeScript entries allocate; none is skipped for visibility");
    }

    @Test
    void makeTreeDeletesOnThePassAfterItsLastTree() throws Exception {
        AizBgTreeSpawnerInstance maker = addMakeTreeAt(slotAboveEveryOccupant());
        int[][] script = engineScript();
        for (int[] entry : script) {
            setDistance(entry[0]);
            stepObjects();
        }
        assertEquals(script.length, getInt(maker, "scriptIndex"), "precondition: the last entry was allocated");

        assertTrue(alive(maker), "the pass that allocates the last tree does not reach the terminator test");
        stepObjects();
        assertFalse(alive(maker), "the following pass reads the $FFFF terminator and deletes the maker");
    }

    @Test
    void scriptThresholdUsesAnUnsignedWordCompare() {
        addMakeTreeAt(slotAboveEveryOccupant());
        setDistance(0);
        stepObjects();
        assertEquals(1, trees().size());

        // Events_fg_1 two below the recorded $2E: sub.w gives $FFFE, which passes
        // threshold $32 under cmp.w/blo.
        setDistance(-2);
        stepObjects();

        assertEquals(2, trees().size(), "the Events_fg_1 delta is compared as an unsigned word");
    }

    @Test
    void treeSpritePriorityFollowsTheScriptWord() {
        addMakeTreeAt(slotAboveEveryOccupant());
        setDistance(0);
        stepObjects();
        setDistance(0x32);
        stepObjects();

        List<AizBgTreeInstance> trees = trees();
        assertEquals(2, trees.size());
        assertEquals(5, trees.get(0).getPriorityBucket(), "entry 0 writes priority $280 (bucket 5)");
        assertEquals(7, trees.get(1).getPriorityBucket(), "entry 1 writes priority $380 (bucket 7)");
        assertFalse(trees.get(0).isHighPriority(), "make_art_tile(..,0,0): behind high-priority plane tiles");
        assertFalse(trees.get(1).isHighPriority(), "make_art_tile(..,0,0): behind high-priority plane tiles");
    }

    @Test
    void midScriptStateRestoresAndReplaysLikeTheUninterruptedRun() throws Exception {
        GameplayModeContext gameplayMode = TestEnvironment.activeGameplayMode();
        assertNotNull(gameplayMode);
        RewindRegistry registry = gameplayMode.getRewindRegistry();
        addMakeTreeAt(slotAboveEveryOccupant());
        for (int i = 0; i < 4; i++) {
            setDistance(ROM_THRESHOLDS[i]);
            stepObjects();
        }
        Map<Integer, String> captured = occupants();
        int capturedIndex = getInt(liveMaker(), "scriptIndex");
        CompositeSnapshot snapshot = registry.capture();
        int capturedFrame = frame;

        List<Map<Integer, String>> reference = new ArrayList<>();
        for (int i = 4; i < 8; i++) {
            setDistance(ROM_THRESHOLDS[i]);
            stepObjects();
            reference.add(occupants());
        }

        registry.restore(snapshot);
        frame = capturedFrame;
        assertEquals(captured, occupants(), "restore recreates the maker and every tree in its captured SST");
        assertEquals(capturedIndex, getInt(liveMaker(), "scriptIndex"), "the script position is captured state");

        for (int i = 4; i < 8; i++) {
            setDistance(ROM_THRESHOLDS[i]);
            stepObjects();
            assertEquals(reference.get(i - 4), occupants(), "replay pass " + (i - 4));
        }
    }

    private AizBgTreeSpawnerInstance addMakeTreeAt(int slot) {
        AizBgTreeSpawnerInstance maker = new AizBgTreeSpawnerInstance();
        objects.addDynamicObjectAtSlot(maker, slot);
        return maker;
    }

    /** Leaves two free SSTs below the maker, so lowest-free and after-current allocation differ. */
    private int slotAboveEveryOccupant() {
        int highest = occupants().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        return highest + 3;
    }

    private void setDistance(int delta) {
        events.setBattleshipSmoothScrollXRaw(SMOOTH_BASE + delta);
    }

    private void stepObjects() {
        frame++;
        objects.update(camera.getX(), null, List.of(), frame, false);
    }

    private List<Filler> fillEveryFreeDynamicSlot() {
        List<Filler> fillers = new ArrayList<>();
        while (objects.hasFreeDynamicSlot()) {
            Filler filler = new Filler();
            objects.addDynamicObject(filler);
            fillers.add(filler);
        }
        return fillers;
    }

    private List<AizBgTreeInstance> trees() {
        return objects.getActiveObjects().stream()
                .filter(o -> o instanceof AizBgTreeInstance && !o.isDestroyed())
                .map(AizBgTreeInstance.class::cast)
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .toList();
    }

    private AizBgTreeSpawnerInstance liveMaker() {
        return objects.getActiveObjects().stream()
                .filter(o -> o instanceof AizBgTreeSpawnerInstance && !o.isDestroyed())
                .map(AizBgTreeSpawnerInstance.class::cast)
                .findFirst().orElseThrow();
    }

    private boolean alive(AizBgTreeSpawnerInstance maker) {
        return objects.getActiveObjects().contains(maker) && !maker.isDestroyed();
    }

    /** Slot -> class:bucket for every live SST occupant. */
    private Map<Integer, String> occupants() {
        Map<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (object instanceof AbstractObjectInstance instance && !object.isDestroyed()
                    && instance.getSlotIndex() >= 0) {
                slots.put(instance.getSlotIndex(),
                        object.getClass().getSimpleName() + ":" + object.getPriorityBucket());
            }
        }
        return slots;
    }

    /** AIZMakeTreeScript as the maker reads it: locked-on ROM $23C13C up to the negative terminator. */
    private static int[][] engineScript() throws Exception {
        byte[] rom = GameServices.rom().getRom().readBytes(0x23C13C, 0x46);
        java.util.List<int[]> entries = new java.util.ArrayList<>();
        for (int offset = 0; (rom[offset] & 0x80) == 0; offset += 4) {
            entries.add(new int[] {((rom[offset] & 0xFF) << 8) | (rom[offset + 1] & 0xFF),
                    ((rom[offset + 2] & 0xFF) << 8) | (rom[offset + 3] & 0xFF)});
        }
        return entries.toArray(int[][]::new);
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static int getInt(Object target, String name) throws Exception {
        return field(target, name).getInt(target);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        return field(target, name).getBoolean(target);
    }

    /** Inert persistent SST occupant used only to exhaust allocation. */
    private static final class Filler extends AbstractObjectInstance {
        Filler() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "MakeTreeTestFiller");
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // invisible
        }

        @Override
        public boolean isPersistent() {
            return true;
        }
    }
}
