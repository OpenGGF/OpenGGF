package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.game.PlayableEntity;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAizCollapsingLogBridgeGraphRewind {

    private static final ObjectSpawn FIRE_PARENT_SPAWN =
            new ObjectSpawn(0x0300, 0x0200, Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE,
                    0x85, 1, true, 17);

    @BeforeEach
    void initHeadless() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        GameplaySessionFactory.attachManagers(TestEnvironment.activeGameplayMode(), EngineServices.current(), mockCamera());
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x800, 0x500, 0);
    }

    @AfterEach
    void tearDown() {
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void collapsingLogBridgeFamilyRestoresFreshWithoutDropsAndPreservesFireSegmentState() {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);

        AizCollapsingLogBridgeObjectInstance sourceParent =
                objectManager.createDynamicObject(
                        () -> new AizCollapsingLogBridgeObjectInstance(FIRE_PARENT_SPAWN));
        AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment sourceSegment =
                objectManager.createDynamicObject(
                        () -> new AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment(
                                0x02B0, 0x0200, 4, 0x18,
                                Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE_FIRE, true));
        seedSegmentState(sourceSegment);

        ObjectRefId parentId = objectId(objectManager, sourceParent);
        ObjectRefId segmentId = objectId(objectManager, sourceSegment);
        CompositeSnapshot snapshot = registryFor(objectManager).capture();

        objectManager.removeDynamicObject(sourceSegment);
        objectManager.removeDynamicObject(sourceParent);
        AizCollapsingLogBridgeObjectInstance replacementParent =
                objectManager.createDynamicObject(
                        () -> new AizCollapsingLogBridgeObjectInstance(
                                new ObjectSpawn(0x0100, 0x0100,
                                        Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE,
                                        0, 0, false, 18)));
        AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment replacementSegment =
                objectManager.createDynamicObject(
                        () -> new AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment(
                                0x0100, 0x0100, 0, 1,
                                Sonic3kObjectArtKeys.AIZ_COLLAPSING_LOG_BRIDGE, false));

        RewindRegistry rewindRegistry = registryFor(objectManager);
        rewindRegistry.restore(snapshot);

        assertEquals(1, liveObjects(objectManager, AizCollapsingLogBridgeObjectInstance.class).size(),
                "restore must keep exactly the captured bridge parent");
        assertEquals(1, liveObjects(objectManager,
                        AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment.class).size(),
                "restore must keep exactly the captured bridge segment");

        AizCollapsingLogBridgeObjectInstance restoredParent =
                objectById(objectManager, AizCollapsingLogBridgeObjectInstance.class, parentId);
        AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment restoredSegment =
                objectById(objectManager,
                        AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment.class,
                        segmentId);

        assertNotSame(sourceParent, restoredParent, "restore must recreate the bridge parent");
        assertNotSame(sourceSegment, restoredSegment, "restore must recreate the segment");
        assertNotSame(replacementParent, restoredParent, "restore must drop the divergent parent");
        assertNotSame(replacementSegment, restoredSegment, "restore must drop the divergent segment");

        assertEquals(0x02B0, readIntField(restoredSegment, "fixedX"),
                "segment fixedX must be rebuilt from its captured spawn");
        assertTrue(readBooleanField(restoredSegment, "isFireVariant"),
                "fire segment final variant must be rebuilt from its captured spawn");
        assertEquals(Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE_FIRE,
                readObjectField(restoredSegment, "artKey"),
                "fire segment art key must be rebuilt from its captured spawn");
        assertSegmentMutableState(restoredSegment);

        RewindIdentityTable restoredTable = objectManager.captureIdentityContext().requireIdentityTable();
        assertEquals(parentId, restoredTable.idFor(restoredParent),
                "restored parent must retain its captured rewind identity");
        assertEquals(segmentId, restoredTable.idFor(restoredSegment),
                "restored segment must retain its captured rewind identity");
    }

    @Test
    void brittleSupportAndWarningRestoreFreshAndReplayToTheSameBreakup() throws Exception {
        var sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        var tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        ObjectManager manager = Harness.create(sonic, tails).objectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var parent = manager.createDynamicObject(() -> new AizCollapsingLogBridgeObjectInstance(
                new ObjectSpawn(0x180, 0x200, 0x2C, 8, 0, true, 19), 10));
        setIntField(parent, "brittleWarningTick", 9);
        var start = AizCollapsingLogBridgeObjectInstance.class.getDeclaredMethod("startCollapse");
        start.setAccessible(true);
        start.invoke(parent);
        var standing = new PlayerSolidContactResult(ContactKind.TOP, true, false, false, false, null, null, 0);
        for (var player : List.of(sonic, tails)) {
            player.setCentreX((short) (0x180 + (player == sonic ? -48 : 48)));
            player.setCentreY((short) 0x1E0);
            player.setAir(false);
            player.setOnObject(true);
            parent.recordStandingPlayer(player, standing);
        }
        for (int tick = 1; tick <= 5; tick++) manager.update(0, sonic, List.of(tails), tick, false);
        var sourceSegments = liveObjects(manager, AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment.class);
        assertEquals(6, sourceSegments.size());
        for (var segment : sourceSegments) assertEquals(5, readIntField(segment, "delayTimer"));
        assertTrue(sonic.isOnObject()); assertTrue(tails.isOnObject());
        RewindRegistry registry = registryFor(manager);
        CompositeSnapshot snapshot = registry.capture();
        var sonicState = sonic.captureRewindState();
        var tailsState = tails.captureRewindState();
        assertEquals(PlayerRefId.mainPlayer(), manager.captureIdentityContext().requireIdentityTable().encodePlayer(sonic));
        assertEquals(PlayerRefId.sidekick(0), manager.captureIdentityContext().requireIdentityTable().encodePlayer(tails));
        ObjectRefId id = objectId(manager, parent);
        var segmentIds = sourceSegments.stream().map(segment -> objectId(manager, segment)).toList();
        var expected = new ArrayList<BrittleFrame>();
        for (int tick = 6; tick <= 140; tick++) {
            manager.update(0, sonic, List.of(tails), tick, false);
            expected.add(brittleFrame(manager, sonic, tails));
            if (tick == 9) { assertTrue(sonic.isOnObject()); assertTrue(tails.isOnObject()); }
            if (tick == 10) { assertTrue(sonic.getAir()); assertTrue(tails.getAir()); }
        }
        assertTrue(liveObjects(manager, AizCollapsingLogBridgeObjectInstance.class).isEmpty());
        assertTrue(liveObjects(manager, AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment.class).isEmpty());
        List.copyOf(manager.getActiveObjects()).forEach(manager::removeDynamicObject);
        sonic.restoreRewindState(sonicState);
        tails.restoreRewindState(tailsState);
        registry.restore(snapshot);
        assertEquals(PlayerRefId.mainPlayer(), manager.captureIdentityContext().requireIdentityTable().encodePlayer(sonic));
        assertEquals(PlayerRefId.sidekick(0), manager.captureIdentityContext().requireIdentityTable().encodePlayer(tails));
        var restored = objectById(manager, AizCollapsingLogBridgeObjectInstance.class, id);
        assertNotSame(parent, restored);
        assertEquals(10, readIntField(restored, "brittleSupportTicks"));
        assertEquals(9, readIntField(restored, "brittleWarningTick"));
        var restoredSegments = liveObjects(manager, AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment.class);
        assertEquals(segmentIds, restoredSegments.stream().map(segment -> objectId(manager, segment)).toList());
        for (int i = 0; i < restoredSegments.size(); i++) {
            assertNotSame(sourceSegments.get(i), restoredSegments.get(i));
            assertTrue(readBooleanField(restoredSegments.get(i), "brittleWarning"));
            assertEquals(5, readIntField(restoredSegments.get(i), "delayTimer"));
        }
        for (int tick = 6; tick <= 140; tick++) {
            manager.update(0, sonic, List.of(tails), tick, false);
            assertEquals(expected.get(tick - 6), brittleFrame(manager, sonic, tails), "complete graph replay update " + tick);
        }
    }

    private record BrittleFrame(List<DynamicObjectEntry> graph, List<List<Integer>> positions,
                                List<Integer> sonic, List<Integer> tails) { }

    private static BrittleFrame brittleFrame(ObjectManager manager, TestablePlayableSprite sonic,
                                            TestablePlayableSprite tails) {
        return new BrittleFrame(manager.rewindSnapshottable().capture().dynamicObjects(),
                manager.getActiveObjects().stream().filter(object -> !object.isDestroyed())
                        .map(object -> List.of(((AbstractObjectInstance) object).getSlotIndex(), object.getX(), object.getY())).toList(),
                riderState(sonic), riderState(tails));
    }

    private static List<Integer> riderState(TestablePlayableSprite player) {
        return List.of(player.getAir() ? 1 : 0, player.isOnObject() ? 1 : 0, player.getPushing() ? 1 : 0,
                (int) player.getCentreX(), (int) player.getCentreY(),
                player.getAnimationManager().captureRewindState().lastAnimationId());
    }

    @Test
    void collapsingLogBridgeFamilyUsesRewindRecreatableWithoutExplicitCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(AizCollapsingLogBridgeObjectInstance.class),
                "AizCollapsingLogBridgeObjectInstance must restore through generic recreate");
        assertTrue(RewindRecreatable.class.isAssignableFrom(
                        AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment.class),
                "CollapsingLogSegment must restore through generic recreate");
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create() {
            return create(null, null);
        }
        static Harness create(TestablePlayableSprite main, TestablePlayableSprite side) {
            ObjectManager[] holder = new ObjectManager[1];
            var gameplay = TestEnvironment.activeGameplayMode();
            Camera camera = gameplay.getCamera();
            if (main != null) {
                gameplay.getSpriteManager().addSprite(main);
                camera.setFocusedSprite(main);
            }
            if (side != null) {
                side.setCpuControlled(true);
                gameplay.getSpriteManager().addSprite(side, "tails");
            }
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public List<PlayableEntity> sidekicks() { return side == null ? List.of() : List.of(side); }
                @Override public ObjectPlayerQuery playerQuery() {
                    return new ObjectPlayerQuery(() -> main, () -> side == null ? List.of() : List.of(side));
                }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(),
                    new Sonic3kObjectRegistry(),
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager);
        }
    }

    private static void seedSegmentState(
            AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment segment) {
        setIntField(segment, "mappingFrame", 6);
        setIntField(segment, "delayTimer", 0x22);
        setIntField(segment, "animFrameTimer", 1);
        Object motion = readObjectField(segment, "motion");
        setIntField(motion, "y", 0x0244);
        setIntField(motion, "ySub", 0x55);
        setIntField(motion, "yVel", 0x0120);
    }

    private static void assertSegmentMutableState(
            AizCollapsingLogBridgeObjectInstance.CollapsingLogSegment segment) {
        assertEquals(6, readIntField(segment, "mappingFrame"),
                "segment mappingFrame must restore exactly");
        assertEquals(0x22, readIntField(segment, "delayTimer"),
                "segment delayTimer must restore exactly");
        assertEquals(1, readIntField(segment, "animFrameTimer"),
                "segment animFrameTimer must restore exactly");
        Object motion = readObjectField(segment, "motion");
        assertEquals(0x0244, readIntField(motion, "y"), "segment motion y must restore exactly");
        assertEquals(0x55, readIntField(motion, "ySub"), "segment motion ySub must restore exactly");
        assertEquals(0x0120, readIntField(motion, "yVel"), "segment motion yVel must restore exactly");
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        RewindIdentityTable table = objectManager.captureIdentityContext().requireIdentityTable();
        ObjectRefId id = table.idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager, Class<T> type, ObjectRefId id) {
        return liveObjects(objectManager, type).stream()
                .filter(object -> objectId(objectManager, object).equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }

    private static <T extends ObjectInstance> List<T> liveObjects(
            ObjectManager objectManager,
            Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .sorted(Comparator.comparingInt(TestAizCollapsingLogBridgeGraphRewind::slotIndex))
                .toList();
    }

    private static int slotIndex(ObjectInstance object) {
        return object instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static boolean readBooleanField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static Object readObjectField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static void setIntField(Object target, String fieldName, int value) {
        try {
            findField(target.getClass(), fieldName).setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x800; }
            @Override public short getHeight() { return 0x500; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
