package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.bosses.Sonic1FalseFloorInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFalseFloorBlockRewindGenericRestore {

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @CsvSource({"1,0", "1,5", "4,9", "7,0"})
    void partialCollapseReplaysThroughCleanupAfterDestructiveRestore(
            int retiredBlocks, int extraTicks) throws Exception {
        ObjectManager objectManager = collapsingFloorManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        tick(objectManager);
        Sonic1FalseFloorInstance originalMaster = findMaster(objectManager);
        assertNotNull(originalMaster);
        originalMaster.signalDisintegrate();
        for (int tick = 0; falseFloorBlocks(objectManager).size() > 8 - retiredBlocks; tick++) {
            assertTrue(tick < 150, "uninterrupted collapse must retire the requested blocks");
            tick(objectManager);
        }
        for (int tick = 0; tick < extraTicks; tick++) {
            tick(objectManager);
        }
        assertEquals(retiredBlocks, readInt(originalMaster, "currentFrame"));
        assertEquals(8 - retiredBlocks, falseFloorBlocks(objectManager).size());

        List<Sonic1FalseFloorInstance.FalseFloorBlock> originalBlocks = falseFloorBlocks(objectManager);
        List<Integer> survivingOrder = new ArrayList<>();
        for (var block : originalBlocks) {
            survivingOrder.add(readInt(block, "blockIndex"));
        }
        assertEquals(IntStream.range(retiredBlocks, 8).boxed().toList(), survivingOrder);
        CollapseState atCapture = collapseState(objectManager);
        assertFalse(atCapture.fragments().isEmpty(), "capture must contain moving fragments");
        RewindRegistry registry = new RewindRegistry();
        registry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();

        List<CollapseState> uninterrupted = new ArrayList<>();
        for (int tick = 0; tick < 200; tick++) {
            tick(objectManager);
            uninterrupted.add(collapseState(objectManager));
        }
        assertTrue(originalMaster.isDestroyed(), "uninterrupted master must complete cleanup");
        assertEquals(List.of(), falseFloorBlocks(objectManager));
        assertEquals(Map.of(), uninterrupted.getLast().fragments(), "all fragments must retire");

        registry.restore(snapshot);
        Sonic1FalseFloorInstance restoredMaster = findMaster(objectManager);
        assertNotNull(restoredMaster);
        assertNotSame(originalMaster, restoredMaster, "restore must recreate the master");
        List<Sonic1FalseFloorInstance.FalseFloorBlock> restoredBlocks = falseFloorBlocks(objectManager);
        assertEquals(8 - retiredBlocks, restoredBlocks.size());
        for (var block : restoredBlocks) {
            assertFalse(originalBlocks.contains(block), "restore must recreate surviving blocks");
        }
        assertEquals(new HashSet<>(restoredBlocks), new HashSet<>(readChildBlocks(restoredMaster)),
                "the rebuilt inventory must contain exactly the fresh surviving children");
        assertEquals(atCapture, collapseState(objectManager),
                "surviving identities, order, timer and moving fragments must restore exactly");
        List<CollapseState> replayed = new ArrayList<>();
        int firstDifference = -1;
        for (int tick = 0; tick < uninterrupted.size(); tick++) {
            tick(objectManager);
            CollapseState state = collapseState(objectManager);
            replayed.add(state);
            if (firstDifference < 0 && !uninterrupted.get(tick).equals(state)) {
                firstDifference = tick;
            }
        }
        int comparisonTick = firstDifference < 0 ? uninterrupted.size() - 1 : firstDifference;
        assertAll(
                () -> assertEquals(uninterrupted.get(comparisonTick), replayed.get(comparisonTick),
                        "restored collapse must match uninterrupted dispatch at forward tick " + comparisonTick),
                () -> assertTrue(restoredMaster.isDestroyed(), "restored master must complete cleanup"),
                () -> assertEquals(List.of(), falseFloorBlocks(objectManager), "all restored blocks must retire"));
    }

    private record MasterState(ObjectRefId id, int routine, int frame, int timer,
                               int halfWidth, int offsetX) {}

    private record LiveBlockState(ObjectRefId id, int index, int x, int y, boolean go, boolean broken) {}

    private record FragmentState(int x, int y, int subY, int yVel, int frame) {}

    private record CollapseState(MasterState master, List<LiveBlockState> blocks,
                                 Map<ObjectRefId, FragmentState> fragments) {}

    private static CollapseState collapseState(ObjectManager objectManager) throws Exception {
        var ids = objectManager.captureIdentityContext().requireIdentityTable();
        Sonic1FalseFloorInstance master = findMaster(objectManager);
        if (master != null && master.isSolidFor(null)) {
            assertEquals((8 - readInt(master, "currentFrame")) * 16, master.getBalanceWidthPixels(),
                    "FFloor_Solid obActWid must follow the original break count");
        }
        MasterState masterState = master == null ? null : new MasterState(ids.idFor(master),
                readInt(master, "routine"), readInt(master, "currentFrame"),
                readInt(master, "disintegrateTimer"), readInt(master, "currentHalfWidth"),
                readInt(master, "solidOffsetX"));
        List<LiveBlockState> blocks = new ArrayList<>();
        for (var block : falseFloorBlocks(objectManager)) {
            blocks.add(new LiveBlockState(ids.idFor(block), readInt(block, "blockIndex"),
                    block.getX(), block.getY(), readBoolean(block, "goSignal"), readBoolean(block, "broken")));
        }
        Map<ObjectRefId, FragmentState> fragments = new LinkedHashMap<>();
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (object instanceof Sonic1FalseFloorInstance.FalseFloorFragment fragment && !fragment.isDestroyed()) {
                fragments.put(ids.idFor(fragment), new FragmentState(fragment.getX(), fragment.getY(),
                        readInt(fragment, "subY"), readInt(fragment, "yVel"), readInt(fragment, "mappingFrame")));
            }
        }
        return new CollapseState(masterState, blocks, fragments);
    }

    private static void tick(ObjectManager objectManager) {
        objectManager.update(0x2000, null, List.of(), 0, false);
    }

    private static ObjectManager collapsingFloorManager() {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0x2000; }
            @Override public short getY() { return 0x500; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> null, List::of); }
        };
        ObjectSpawn spawn = new ObjectSpawn(0x2080, 0x5D0,
                Sonic1ObjectIds.FALSE_FLOOR, 0, 0, false, 0);
        ObjectManager objectManager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(),
                0, null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = objectManager;
        objectManager.reset(0x2000);
        objectManager.createDynamicObject(() -> new Sonic1FalseFloorInstance(spawn));
        return objectManager;
    }

    @Test
    void falseFloorBlocksRestoreWithIdentityScalarsAndMasterRelink() throws Exception {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mockCamera();
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };

        ObjectSpawn spawn = new ObjectSpawn(160, 240,
                Sonic1ObjectIds.FALSE_FLOOR, 0, 0, false, 0);
        ObjectManager objectManager = new ObjectManager(
                List.of(spawn), new Sonic1ObjectRegistry(),
                0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = objectManager;
        objectManager.reset(0);

        Sonic1FalseFloorInstance master = findMaster(objectManager);
        assertNotNull(master, "false-floor master must be active after reset");
        master.update(1, null);

        List<Sonic1FalseFloorInstance.FalseFloorBlock> blocks = falseFloorBlocks(objectManager);
        assertEquals(8, blocks.size(), "master update must production-spawn 8 floor blocks");

        Map<Integer, BlockSnapshot> expectedByIndex = new HashMap<>();
        var identityTable = objectManager.captureIdentityContext().requireIdentityTable();
        for (Sonic1FalseFloorInstance.FalseFloorBlock block : blocks) {
            int blockIndex = readInt(block, "blockIndex");
            ObjectRefId objectId = identityTable.idFor(block);
            assertNotNull(objectId, "captured block must have a dynamic rewind identity");
            expectedByIndex.put(blockIndex, new BlockSnapshot(
                    blockIndex,
                    readInt(block, "currentX"),
                    readInt(block, "currentY"),
                    objectId));
        }
        assertEquals(8, expectedByIndex.size(), "blockIndex values must be unique");

        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewindRegistry.capture();

        for (Sonic1FalseFloorInstance.FalseFloorBlock block : blocks) {
            objectManager.removeDynamicObject(block);
        }
        assertEquals(0, falseFloorBlocks(objectManager).size(),
                "test must remove the live blocks before restore");

        rewindRegistry.restore(snapshot);

        List<Sonic1FalseFloorInstance.FalseFloorBlock> restoredBlocks =
                falseFloorBlocks(objectManager);
        assertEquals(8, restoredBlocks.size(),
                "restore must recreate exactly the captured block set");

        var restoredIdentityTable = objectManager.captureIdentityContext().requireIdentityTable();
        Set<Integer> restoredIndexes = new HashSet<>();
        for (Sonic1FalseFloorInstance.FalseFloorBlock restored : restoredBlocks) {
            int blockIndex = readInt(restored, "blockIndex");
            BlockSnapshot expected = expectedByIndex.get(blockIndex);
            assertNotNull(expected, "unexpected restored blockIndex " + blockIndex);
            assertTrue(restoredIndexes.add(blockIndex),
                    "duplicate restored blockIndex " + blockIndex);
            assertEquals(expected.currentX(), readInt(restored, "currentX"),
                    "currentX scalar must restore for block " + blockIndex);
            assertEquals(expected.currentY(), readInt(restored, "currentY"),
                    "currentY scalar must restore for block " + blockIndex);
            assertEquals(expected.objectId(), restoredIdentityTable.idFor(restored),
                    "dynamic rewind identity must restore for block " + blockIndex);
        }
        assertEquals(expectedByIndex.keySet(), restoredIndexes,
                "restored block indexes must match the captured set exactly");

        Sonic1FalseFloorInstance restoredMaster = findMaster(objectManager);
        assertNotNull(restoredMaster, "master must still be active after restore");
        assertNotSame(master, restoredMaster, "the list must rebuild on a fresh master");
        List<Sonic1FalseFloorInstance.FalseFloorBlock> relinked =
                readChildBlocks(restoredMaster);
        assertEquals(8, relinked.size(),
                "restored master childBlocks list must be relinked");
        assertEquals(new HashSet<>(restoredBlocks), new HashSet<>(relinked),
                "master childBlocks must reference the live restored block instances");
        for (int i = 0; i < relinked.size(); i++) {
            assertEquals(i, readInt(relinked.get(i), "blockIndex"),
                    "reattach order must preserve left-to-right blockIndex order");
        }

        Sonic1FalseFloorInstance.FalseFloorBlock first = relinked.get(0);
        assertFalse(readBoolean(first, "goSignal"), "precondition: first block not signaled");
        restoredMaster.signalDisintegrate();
        restoredMaster.update(2, null);
        restoredMaster.update(3, null);
        assertSame(first, readChildBlocks(restoredMaster).get(0),
                "signal path must still target the relinked restored first block");
        assertTrue(readBoolean(first, "goSignal"),
                "master signalDisintegrate update must signal the restored child block");
    }

    private record BlockSnapshot(
            int blockIndex,
            int currentX,
            int currentY,
            ObjectRefId objectId) {}

    private static Sonic1FalseFloorInstance findMaster(ObjectManager objectManager) {
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (object instanceof Sonic1FalseFloorInstance master && !object.isDestroyed()) {
                return master;
            }
        }
        return null;
    }

    private static List<Sonic1FalseFloorInstance.FalseFloorBlock> falseFloorBlocks(
            ObjectManager objectManager) {
        List<Sonic1FalseFloorInstance.FalseFloorBlock> blocks = new ArrayList<>();
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (object instanceof Sonic1FalseFloorInstance.FalseFloorBlock block
                    && !object.isDestroyed()) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    @SuppressWarnings("unchecked")
    private static List<Sonic1FalseFloorInstance.FalseFloorBlock> readChildBlocks(
            Sonic1FalseFloorInstance master) throws Exception {
        Field field = findField(Sonic1FalseFloorInstance.class, "childBlocks");
        field.setAccessible(true);
        return new ArrayList<>((List<Sonic1FalseFloorInstance.FalseFloorBlock>) field.get(master));
    }

    private static int readInt(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean readBoolean(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // Walk superclass chain.
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
