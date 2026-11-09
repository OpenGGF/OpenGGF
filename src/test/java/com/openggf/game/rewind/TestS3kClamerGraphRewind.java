package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.ClamerObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kClamerGraphRewind {
    private static final String SPRING_CLASS =
            "com.openggf.game.sonic3k.objects.ClamerObjectInstance$ClamerSpringChild";
    private static final ObjectSpawn CLAMER_SPAWN =
            new ObjectSpawn(0x0578, 0x0690, Sonic3kObjectIds.CLAMER, 0, 0, false, 0x0690, 11);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void twoRoutineClamerSpringsKeepTheirCapturedParentsWhenAnotherClamerIsCloser() throws Exception {
        // Clamer SPRING_OFFSET_Y: the spring is eight pixels above its actual parent.
        int springOffsetY = -8;
        ObjectSpawn upperSpawn = new ObjectSpawn(
                CLAMER_SPAWN.x(), CLAMER_SPAWN.y() + springOffsetY,
                Sonic3kObjectIds.CLAMER, 0, 0, false,
                CLAMER_SPAWN.y() + springOffsetY, CLAMER_SPAWN.layoutIndex() + 1);
        Harness harness = Harness.create(List.of(CLAMER_SPAWN, upperSpawn));
        ObjectManager manager = harness.objectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        // First pass leaves WAIT_OFFSCREEN; the second initializes through owned spawnChild.
        manager.update(harness.camera().getX(), null, List.of(), 0);
        manager.update(harness.camera().getX(), null, List.of(), 1);

        List<ClamerObjectInstance> parents = manager.activeObjectsOfType(ClamerObjectInstance.class);
        assertEquals(2, parents.size(), "fixture must have two normally placed Clamers");
        ClamerObjectInstance lower = parents.stream()
                .filter(parent -> parent.getY() == CLAMER_SPAWN.y()).findFirst().orElseThrow();
        ClamerObjectInstance upper = parents.stream()
                .filter(parent -> parent.getY() == upperSpawn.y()).findFirst().orElseThrow();
        ObjectInstance lowerSpring = (ObjectInstance) readObjectField(lower, "springChildSlot");
        assertNotNull(lowerSpring, "the normal Clamer initialization must create its spring");
        assertEquals(2, liveSprings(manager).size());

        Map<ObjectRefId, ObjectRefId> expectedParents = new LinkedHashMap<>();
        for (ClamerObjectInstance parent : List.of(lower, upper)) {
            ObjectInstance spring = (ObjectInstance) readObjectField(parent, "springChildSlot");
            assertNotNull(spring);
            assertSame(parent, readObjectField(spring, "parent"), "normal owned birth must use its real parent");
            expectedParents.put(objectId(manager, spring), objectId(manager, parent));
        }
        var adapter = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshot = adapter.capture();
        assertCapturedSpringFamilies(snapshot, expectedParents);
        var capturedLowerSpring = snapshot.dynamicObjects().stream()
                .filter(entry -> objectId(manager, lowerSpring).equals(entry.objectId()))
                .findFirst().orElseThrow();
        assertEquals(lowerSpring.getX(), capturedLowerSpring.spawn().x(),
                "the dynamic entry must capture the spring's parent-relative x");
        assertEquals(lowerSpring.getY(), capturedLowerSpring.spawn().y(),
                "the dynamic entry must capture the spring's parent-relative y");
        assertTrue(distanceSquared(upper, capturedLowerSpring.spawn())
                        < distanceSquared(lower, capturedLowerSpring.spawn()),
                "the snapshot spawn passed to the hook must be strictly closer to the other Clamer");

        for (int restore = 0; restore < 2; restore++) {
            Map<ObjectRefId, ObjectInstance> previous = new LinkedHashMap<>();
            for (var family : expectedParents.entrySet()) {
                previous.put(family.getKey(), manager.objectQuery().resolve(family.getKey()).orElseThrow());
                previous.put(family.getValue(), manager.objectQuery().resolve(family.getValue()).orElseThrow());
            }
            adapter.restore(snapshot);
            assertEquals(2, liveSprings(manager).size(), "fresh restore must retain exactly two springs");
            for (var family : expectedParents.entrySet()) {
                ObjectInstance spring = manager.objectQuery().resolve(family.getKey()).orElseThrow();
                ClamerObjectInstance parent = (ClamerObjectInstance)
                        manager.objectQuery().resolve(family.getValue()).orElseThrow();
                assertNotSame(previous.get(family.getKey()), spring,
                        "the spring must be freshly recreated, not retained from the source graph");
                assertNotSame(previous.get(family.getValue()), parent,
                        "the Clamer parent must be freshly reconstructed");
                assertSame(parent, readObjectField(spring, "parent"),
                        "spring must use its captured family parent even when another Clamer is closer");
                assertSame(spring, readObjectField(parent, "springChildSlot"),
                        "each Clamer must point back at its own captured spring id");
                assertEquals(parent.getX(), spring.getX());
                assertEquals(parent.getY() + springOffsetY, spring.getY());
            }
            if (restore == 0) {
                manager.update(harness.camera().getX(), null, List.of(), 2);
                snapshot = adapter.capture();
                assertCapturedSpringFamilies(snapshot, expectedParents);
            }
        }
    }


    @Test
    void clamerKnownMissingWrongTypeAndExplicitNoParentNeverUseNearestFallback() throws Exception {
        ClamerFamilyBoundary boundary = clamerFamilyBoundary();
        var entry = boundary.entry();
        ObjectRefId ownerId = entry.constructionFamily().parentId();
        ObjectRefId absentId = new ObjectRefId(ownerId.slotIndex(), ownerId.generation() + 1,
                ownerId.spawnId(), ownerId.dynamicId(), ownerId.kind());
        assertTrue(boundary.manager().objectQuery().resolve(absentId).isEmpty(),
                "boundary input must name an absent parent identity");
        assertSame(boundary.sourceSpring(), boundary.manager().objectQuery()
                .resolve(entry.objectId()).orElseThrow(),
                "wrong-type input must name a real managed spring, not a Clamer");
        // Deliberately altered snapshot metadata tests the hook contract, not a normal birth.
        for (var family : List.of(
                new ObjectManagerSnapshot.ConstructionFamily(true, absentId),
                new ObjectManagerSnapshot.ConstructionFamily(true, entry.objectId()),
                new ObjectManagerSnapshot.ConstructionFamily(true, null))) {
            ObjectInstance recreated = ObjectRewindDynamicCodecs.genericRecreate(
                    clamerEntryWithFamily(entry, family),
                    new DynamicObjectRecreateContext(boundary.manager()));
            assertNull(recreated,
                    "known Clamer family must not borrow the nearest unrelated parent: " + family);
        }
    }

    @Test
    void clamerLegacyAndUnknownFamiliesKeepNearestParentCompatibility() throws Exception {
        ClamerFamilyBoundary boundary = clamerFamilyBoundary();
        for (var family : new ObjectManagerSnapshot.ConstructionFamily[] {
                null, new ObjectManagerSnapshot.ConstructionFamily(false, null)}) {
            ObjectInstance recreated = ObjectRewindDynamicCodecs.genericRecreate(
                    clamerEntryWithFamily(boundary.entry(), family),
                    new DynamicObjectRecreateContext(boundary.manager()));
            assertNotNull(recreated, "legacy/unknown family must keep its historical recreate hook");
            assertNotSame(boundary.sourceSpring(), recreated);
            assertSame(boundary.nearest(), readObjectField(recreated, "parent"),
                    "legacy/unknown Clamer family must retain nearest-parent compatibility");
        }
    }

    private static ClamerFamilyBoundary clamerFamilyBoundary() throws Exception {
        int springOffsetY = -8; // Clamer SPRING_OFFSET_Y.
        ObjectSpawn upperSpawn = new ObjectSpawn(CLAMER_SPAWN.x(), CLAMER_SPAWN.y() + springOffsetY,
                Sonic3kObjectIds.CLAMER, 0, 0, false, CLAMER_SPAWN.y() + springOffsetY,
                CLAMER_SPAWN.layoutIndex() + 1);
        Harness harness = Harness.create(List.of(CLAMER_SPAWN, upperSpawn));
        ObjectManager manager = harness.objectManager();
        manager.update(harness.camera().getX(), null, List.of(), 0);
        manager.update(harness.camera().getX(), null, List.of(), 1);
        ClamerObjectInstance lower = manager.activeObjectsOfType(ClamerObjectInstance.class).stream()
                .filter(parent -> parent.getY() == CLAMER_SPAWN.y()).findFirst().orElseThrow();
        ClamerObjectInstance upper = manager.activeObjectsOfType(ClamerObjectInstance.class).stream()
                .filter(parent -> parent.getY() == upperSpawn.y()).findFirst().orElseThrow();
        ObjectInstance spring = (ObjectInstance) readObjectField(lower, "springChildSlot");
        assertNotNull(spring, "normal routine initialization must create the source spring");
        assertSame(lower, readObjectField(spring, "parent"));
        var entry = manager.rewindSnapshottable().capture().dynamicObjects().stream()
                .filter(candidate -> objectId(manager, spring).equals(candidate.objectId()))
                .findFirst().orElseThrow();
        assertNotNull(entry.constructionFamily());
        assertTrue(entry.constructionFamily().known());
        assertEquals(objectId(manager, lower), entry.constructionFamily().parentId(),
                "the unmodified capture must name the normal source owner");
        assertEquals(spring.getX(), entry.spawn().x());
        assertEquals(spring.getY(), entry.spawn().y());
        assertTrue(distanceSquared(upper, entry.spawn()) < distanceSquared(lower, entry.spawn()),
                "nearest fallback must choose the other Clamer from the actual captured spawn");
        return new ClamerFamilyBoundary(manager, upper, spring, entry);
    }

    private record ClamerFamilyBoundary(ObjectManager manager, ClamerObjectInstance nearest,
            ObjectInstance sourceSpring, ObjectManagerSnapshot.DynamicObjectEntry entry) { }

    private static ObjectManagerSnapshot.DynamicObjectEntry clamerEntryWithFamily(
            ObjectManagerSnapshot.DynamicObjectEntry entry,
            ObjectManagerSnapshot.ConstructionFamily family) {
        return new ObjectManagerSnapshot.DynamicObjectEntry(entry.className(), entry.spawn(), entry.slotIndex(),
                entry.state(), entry.playerOwner(), entry.objectId(), entry.ownerModId(),
                entry.rewindableAuxiliary(), entry.playerOwnerId(), family);
    }

    private static void assertCapturedSpringFamilies(
            ObjectManagerSnapshot snapshot, Map<ObjectRefId, ObjectRefId> expectedParents) {
        for (var family : expectedParents.entrySet()) {
            var child = snapshot.dynamicObjects().stream()
                    .filter(entry -> family.getKey().equals(entry.objectId())).findFirst().orElseThrow();
            assertEquals(SPRING_CLASS, child.className());
            assertNotNull(child.constructionFamily(), "owned routine spring birth must record family metadata");
            assertTrue(child.constructionFamily().known());
            assertEquals(family.getValue(), child.constructionFamily().parentId(),
                    "captured spring family id must already name its normal source owner");
        }
    }

    private static long distanceSquared(ObjectInstance parent, ObjectSpawn capturedSpawn) {
        long dx = parent.getX() - capturedSpawn.x();
        long dy = parent.getY() - capturedSpawn.y();
        return dx * dx + dy * dy;
    }

    @Test
    void clamerSpringRestoresFreshExactStateAndRelinksParent() throws Exception {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        objectManager.update(harness.camera().getX(), null, List.of(), 0);
        objectManager.update(harness.camera().getX(), null, List.of(), 1);
        ClamerObjectInstance sourceClamer = only(objectManager, ClamerObjectInstance.class);
        ObjectInstance sourceSpring = onlySpring(objectManager);
        ObjectRefId clamerId = objectId(objectManager, sourceClamer);
        ObjectRefId springId = objectId(objectManager, sourceSpring);

        writeIntField(sourceClamer, "routine", 0x04);
        writeIntField(sourceClamer, "mappingFrame", 3);
        writeIntField(sourceClamer, "springCprop", 2);
        writeBooleanField(sourceClamer, "springInListLastFrame", false);
        writeIntField(sourceClamer, "closeTimer", 5);
        writeIntField(sourceClamer, "autoCloseAnimIndex", 4);
        writeIntField(sourceClamer, "autoCloseAnimTimer", 7);
        writeBooleanField(sourceClamer, "springFiredFlag", true);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceSpring);
        ObjectInstance divergentSpring = objectManager.createDynamicObject(() -> constructSpring(sourceClamer));
        assertEquals(1, liveSprings(objectManager).size(),
                "diverge step should leave one unrelated Clamer spring before restore");

        rewindRegistry.restore(snapshot);

        ClamerObjectInstance restoredClamer =
                objectById(objectManager, ClamerObjectInstance.class, clamerId);
        ObjectInstance restoredSpring = objectByIdRaw(objectManager, Class.forName(SPRING_CLASS), springId);
        assertEquals(1, liveSprings(objectManager).size(),
                "restore must keep exactly the captured Clamer spring");
        assertNotSame(sourceClamer, restoredClamer,
                "restore must recreate the Clamer parent");
        assertNotSame(sourceSpring, restoredSpring,
                "restore must recreate the Clamer spring child");
        assertNotSame(divergentSpring, restoredSpring,
                "restore must drop the divergent Clamer spring child");
        assertEquals(0x04, readIntField(restoredClamer, "routine"),
                "restored Clamer must keep captured routine state");
        assertEquals(3, readIntField(restoredClamer, "mappingFrame"),
                "restored Clamer must keep captured mapping frame state");
        assertEquals(2, readIntField(restoredClamer, "springCprop"),
                "restored Clamer must keep captured spring cprop state");
        assertFalse(readBooleanField(restoredClamer, "springInListLastFrame"),
                "restored Clamer must keep captured spring list state");
        assertEquals(5, readIntField(restoredClamer, "closeTimer"),
                "restored Clamer must keep captured close timer state");
        assertEquals(4, readIntField(restoredClamer, "autoCloseAnimIndex"),
                "restored Clamer must keep captured auto-close animation index");
        assertEquals(7, readIntField(restoredClamer, "autoCloseAnimTimer"),
                "restored Clamer must keep captured auto-close animation timer");
        assertTrue(readBooleanField(restoredClamer, "springFiredFlag"),
                "restored Clamer must keep captured spring-fired flag");
        assertSame(restoredClamer, readObjectField(restoredSpring, "parent"),
                "Clamer spring parent must resolve to the restored Clamer");
        assertSame(restoredSpring, readObjectField(restoredClamer, "springChildSlot"),
                "restored Clamer must point at the restored spring child");
        assertNotSame(sourceClamer, readObjectField(restoredSpring, "parent"),
                "Clamer spring must not retain the stale pre-restore parent");
        assertEquals(restoredClamer.getX(), restoredSpring.getX(),
                "restored spring should read x through the restored parent");
        assertEquals(restoredClamer.getY() - 8, restoredSpring.getY(),
                "restored spring should read y through the restored parent");
    }

    @Test
    void clamerFamilyUsesGenericRecreateWithoutExplicitS3kCodecs() throws Exception {
        assertTrue(RewindRecreatable.class.isAssignableFrom(ClamerObjectInstance.class),
                "Clamer must restore through generic recreate");
        assertTrue(RewindRecreatable.class.isAssignableFrom(Class.forName(SPRING_CLASS)),
                "Clamer spring must restore through generic graph recreate");
    }

    @Test
    void clamerSpringSlotFailsLoudlyWhenTargetHasNoRewindIdentity() {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        objectManager.update(harness.camera().getX(), null, List.of(), 0);
        ClamerObjectInstance sourceClamer = only(objectManager, ClamerObjectInstance.class);
        ObjectInstance unmanagedSpring = constructSpring(sourceClamer);
        writeObjectField(sourceClamer, "springChildSlot", unmanagedSpring);

        RewindRegistry rewindRegistry = registryFor(objectManager);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, rewindRegistry::capture);
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "capturing an unmanaged Clamer springChildSlot reference must fail loudly");
    }

    private record Harness(ObjectManager objectManager, Camera camera, ObjectServices services) {
        static Harness create() {
            return create(List.of(CLAMER_SPAWN));
        }

        static Harness create(List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCameraAtClamer();
            ObjectPlayerQuery playerQuery = new ObjectPlayerQuery(() -> null, List::of);
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public ObjectPlayerQuery playerQuery() { return playerQuery; }
            };
            ObjectManager objectManager = new ObjectManager(
                    spawns,
                    new Sonic3kObjectRegistry(),
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(camera.getX());
            return new Harness(objectManager, camera, services);
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static ObjectInstance constructSpring(ClamerObjectInstance parent) {
        try {
            Class<?> type = Class.forName(SPRING_CLASS);
            Constructor<?> ctor = type.getDeclaredConstructor(ClamerObjectInstance.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(parent);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to construct Clamer spring", e);
        }
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager,
            Class<T> type,
            ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable()
                        .idFor(object)))
                .findFirst()
                .orElseThrow();
    }

    private static ObjectInstance objectByIdRaw(
            ObjectManager objectManager,
            Class<?> type,
            ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable()
                        .idFor(object)))
                .findFirst()
                .orElseThrow();
    }

    private static ObjectInstance onlySpring(ObjectManager objectManager) {
        List<ObjectInstance> springs = liveSprings(objectManager);
        assertEquals(1, springs.size(), "expected exactly one live Clamer spring");
        return springs.getFirst();
    }

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> !object.isDestroyed())
                .toList();
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static List<ObjectInstance> liveSprings(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(SPRING_CLASS))
                .filter(object -> !object.isDestroyed())
                .toList();
    }

    private static Camera mockCameraAtClamer() {
        return new Camera() {
            @Override public short getX() { return 0x0500; }
            @Override public short getY() { return 0x0620; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    private static Object readObjectField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.get(target);
    }

    private static int readIntField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.getInt(target);
    }

    private static boolean readBooleanField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.getBoolean(target);
    }

    private static void writeIntField(Object target, String name, int value) throws Exception {
        Field field = field(target, name);
        field.setInt(target, value);
    }

    private static void writeBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = field(target, name);
        field.setBoolean(target, value);
    }

    private static void writeObjectField(Object target, String name, Object value) {
        try {
            Field field = field(target, name);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + name + " on " + target.getClass(), e);
        }
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
}
