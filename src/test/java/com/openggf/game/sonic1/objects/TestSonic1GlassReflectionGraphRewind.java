package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.game.OscillationManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.DeletedDynamicRewindCodecs;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1GlassReflectionGraphRewind {

    private static final ObjectSpawn FAR_GLASS =
            new ObjectSpawn(0x0080, 0x0180, Sonic1ObjectIds.MZ_GLASS_BLOCK, 0x04, 0, false, 10);
    private static final ObjectSpawn NEAR_GLASS =
            new ObjectSpawn(0x0200, 0x01C0, Sonic1ObjectIds.MZ_GLASS_BLOCK, 0x04, 0, false, 11);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        OscillationManager.resetForSonic1();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    /**
     * S1 bug-triage row 4 investigation (docs/plans/s1-bug-batch-ledger-2026-07-05.md,
     * "Glass reflection oscillates incorrectly"): a subtype-4 (switch-activated
     * lowering) glass block reflection's Y motion, verified against
     * "docs/s1disasm/_incObj/30 MZ Large Green Glass Blocks.asm".
     * <p>
     * Glass_Type04 (lines 209-215) branches on {@code btst #3,obSubtype(a0)}:
     * bit 3 clear (the block itself) runs Glass_ChkSwitch's switch-gated
     * glass_dist countdown; bit 3 SET (the reflection -- its subtype always has
     * bit 3 set, since Glass_Main does {@code addq.b #8,obSubtype(a1)} when
     * creating it) instead falls straight through to
     * {@code move.b (v_oscillate+$12).w,d0 / subi.w #$10,d0} and NEVER reads
     * glass_dist at all, even though Glass_Reflect34/012 (lines 89-93, 105-110)
     * unconditionally copy the parent's glass_dist into the reflection's own
     * memory one instruction earlier -- that copy is simply discarded for
     * types 3-4. So a switch-activated block's reflection oscillating
     * independently while the block itself lowers and stops is genuine,
     * verified ROM behavior (present in the retail "World REV01" ROM this
     * project targets), not an engine defect. This test locks in that
     * ROM-accurate independence rather than "fixing" it to track the parent,
     * which the ledger's initial (untriaged) symptom description suggested but
     * the disassembly disproves. See docs/plans/s1-bug-batch-ledger-2026-07-05.md
     * row 4 for the ledger disposition.
     */
    @Test
    void type4ReflectionOscillatesIndependentlyOfParentGlassDistPerRom() {
        ObjectSpawn blockSpawn = new ObjectSpawn(0x0100, 0x0300, Sonic1ObjectIds.MZ_GLASS_BLOCK, 0x04, 0, false, 50);
        Sonic1GlassBlockObjectInstance parent = new Sonic1GlassBlockObjectInstance(blockSpawn);
        int reflectSubtype = reflectedSubtype(blockSpawn);
        Sonic1GlassReflectionInstance reflection =
                new Sonic1GlassReflectionInstance(blockSpawn, parent, reflectSubtype, isTall(blockSpawn));

        // Fix the shared oscillator at a known frame so both reflection.update()
        // calls below observe the identical (v_oscillate+$12) byte -- isolating
        // glass_dist as the only thing that changes between them.
        OscillationManager.update(37);
        int expectedOscByte = OscillationManager.getByte(0x10);

        // Block still fully raised (initial glass_dist = $90, INITIAL_GLASS_DIST).
        writeInt(parent, "glassDist", 0x90);
        reflection.update(1, null);
        int yWhileRaised = readInt(reflection, "y");

        // Block fully lowered by the switch (glass_dist driven to 0 by
        // Glass_ChkSwitch's "subq.w #2,glass_dist(a0)" loop).
        writeInt(parent, "glassDist", 0);
        reflection.update(1, null);
        int yWhileLowered = readInt(reflection, "y");

        assertEquals(yWhileRaised, yWhileLowered,
                "ROM Glass_Type04's bit-3-set (reflection) path never reads glass_dist, so the "
                        + "reflection's Y must not change when the parent's glass_dist changes");
        assertEquals(blockSpawn.y() - (expectedOscByte - 0x10), yWhileLowered,
                "reflection Y must follow the oscillator formula from Glass_Type04 "
                        + "(v_oscillate+$12) - $10, not the parent's glass_dist");
    }

    @Test
    void glassReflectionRestoresFreshWithIdentityScalarsAndNearestParentRelink() {
        Harness harness = Harness.create(List.of(FAR_GLASS, NEAR_GLASS));
        ObjectManager objectManager = harness.objectManager();
        Sonic1GlassBlockObjectInstance nearParent =
                liveParentAt(objectManager, NEAR_GLASS.x());
        Sonic1GlassBlockObjectInstance farParent =
                liveParentAt(objectManager, FAR_GLASS.x());
        Sonic1GlassReflectionInstance before = objectManager.createDynamicObject(
                () -> new Sonic1GlassReflectionInstance(
                        NEAR_GLASS, nearParent, reflectedSubtype(NEAR_GLASS), isTall(NEAR_GLASS)));
        writeInt(before, "x", 0x0224);
        writeInt(before, "y", 0x0142);
        writeInt(before, "baseY", 0x01D8);
        writeInt(before, "glassDist", 0x56);

        ObjectRefId beforeId = objectId(objectManager, before);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();
        objectManager.removeDynamicObject(before);
        Sonic1GlassReflectionInstance replacement = objectManager.createDynamicObject(
                () -> new Sonic1GlassReflectionInstance(
                        FAR_GLASS, farParent, reflectedSubtype(FAR_GLASS), isTall(FAR_GLASS)));

        rewindRegistry.restore(snapshot);

        Sonic1GlassReflectionInstance restored = onlyReflection(objectManager);
        Sonic1GlassBlockObjectInstance restoredNearParent =
                liveParentAt(objectManager, NEAR_GLASS.x());
        Sonic1GlassBlockObjectInstance restoredFarParent =
                liveParentAt(objectManager, FAR_GLASS.x());
        assertNotSame(before, restored, "restore must recreate the removed reflection");
        assertNotSame(replacement, restored, "restore must drop unrelated post-snapshot reflections");
        assertEquals(beforeId, objectId(objectManager, restored),
                "reflection rewind identity must be preserved");
        assertSame(restoredNearParent, readObject(restored, "parent"),
                "reflection must relink to the nearest live glass block");
        assertNotSame(restoredFarParent, readObject(restored, "parent"),
                "reflection must not relink to the farther live glass block");
        assertEquals(0x0224, readInt(restored, "x"), "x scalar must restore exactly");
        assertEquals(0x0142, readInt(restored, "y"), "y scalar must restore exactly");
        assertEquals(0x01D8, readInt(restored, "baseY"), "baseY scalar must restore exactly");
        assertEquals(0x56, readInt(restored, "glassDist"), "glassDist scalar must restore exactly");
        assertEquals(reflectedSubtype(NEAR_GLASS), readInt(restored, "reflectSubtype"),
                "reflectSubtype must be derived from the captured spawn subtype");
        assertEquals(isTall(NEAR_GLASS), readBoolean(restored, "isTall"),
                "isTall must be derived from the captured spawn subtype");

        restored.update(1, new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0180));
        assertFalse(restored.isDestroyed(), "restored reflection must survive with its relinked parent");
        assertSame(restoredNearParent, readObject(restored, "parent"),
                "update must continue following the relinked parent");
    }

    @Test
    void directGenericRecreateWithoutLiveGlassBlockReturnsNull() {
        Harness harness = Harness.create(List.of());
        ObjectInstance recreated = genericRecreate(harness.objectManager(), NEAR_GLASS);

        assertNull(recreated, "generic recreate must drop a reflection when no live glass block exists");
    }

    @Test
    void directGenericRecreateRelinksNearestLiveGlassBlock() {
        Harness harness = Harness.create(List.of(FAR_GLASS, NEAR_GLASS));
        ObjectInstance recreated = genericRecreate(harness.objectManager(), NEAR_GLASS);

        Sonic1GlassReflectionInstance reflection =
                assertInstanceOf(Sonic1GlassReflectionInstance.class, recreated);
        assertSame(liveParentAt(harness.objectManager(), NEAR_GLASS.x()),
                readObject(reflection, "parent"),
                "direct generic recreate must relink to the nearest live glass block");
        assertEquals(reflectedSubtype(NEAR_GLASS), readInt(reflection, "reflectSubtype"));
        assertEquals(isTall(NEAR_GLASS), readBoolean(reflection, "isTall"));
    }

    @Test
    void glassBlockAndReflectionRestoreFreshWithoutDropsDoublesOrStaleReferences() {
        Harness harness = Harness.create(List.of());
        ObjectManager objectManager = harness.objectManager();
        Sonic1GlassBlockObjectInstance beforeParent = objectManager.createDynamicObject(
                () -> new Sonic1GlassBlockObjectInstance(NEAR_GLASS));
        Sonic1GlassReflectionInstance beforeReflection = objectManager.createDynamicObject(
                () -> new Sonic1GlassReflectionInstance(
                        NEAR_GLASS, beforeParent, reflectedSubtype(NEAR_GLASS), isTall(NEAR_GLASS)));
        writeObject(beforeParent, "reflectionChild", beforeReflection);
        writeInt(beforeParent, "x", 0x0220);
        writeInt(beforeParent, "y", 0x0140);
        writeInt(beforeParent, "baseY", 0x01E0);
        writeInt(beforeParent, "glassDist", 0xA0);
        writeInt(beforeReflection, "x", 0x0220);
        writeInt(beforeReflection, "y", 0x0150);
        writeInt(beforeReflection, "baseY", 0x01E0);
        writeInt(beforeReflection, "glassDist", 0x90);

        ObjectRefId parentId = objectId(objectManager, beforeParent);
        ObjectRefId reflectionId = objectId(objectManager, beforeReflection);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();
        objectManager.removeDynamicObject(beforeReflection);
        objectManager.removeDynamicObject(beforeParent);
        Sonic1GlassBlockObjectInstance replacementParent = objectManager.createDynamicObject(
                () -> new Sonic1GlassBlockObjectInstance(FAR_GLASS));
        Sonic1GlassReflectionInstance replacementReflection = objectManager.createDynamicObject(
                () -> new Sonic1GlassReflectionInstance(
                        FAR_GLASS, replacementParent, reflectedSubtype(FAR_GLASS), isTall(FAR_GLASS)));
        writeObject(replacementParent, "reflectionChild", replacementReflection);

        rewindRegistry.restore(snapshot);

        assertEquals(1, liveGlassBlocks(objectManager).size(),
                "restore must keep exactly the captured glass block");
        assertEquals(1, liveReflections(objectManager).size(),
                "restore must keep exactly the captured reflection");
        Sonic1GlassBlockObjectInstance restoredParent =
                assertInstanceOf(Sonic1GlassBlockObjectInstance.class,
                        objectWithId(objectManager, parentId));
        Sonic1GlassReflectionInstance restoredReflection =
                assertInstanceOf(Sonic1GlassReflectionInstance.class,
                        objectWithId(objectManager, reflectionId));
        assertNotSame(beforeParent, restoredParent, "restore must recreate the removed glass block");
        assertNotSame(beforeReflection, restoredReflection, "restore must recreate the removed reflection");
        assertNotSame(replacementParent, restoredParent, "restore must drop unrelated post-snapshot parents");
        assertNotSame(replacementReflection, restoredReflection,
                "restore must drop unrelated post-snapshot reflections");
        assertSame(restoredReflection, readObject(restoredParent, "reflectionChild"),
                "parent reflectionChild must point to the restored reflection");
        assertSame(restoredParent, readObject(restoredReflection, "parent"),
                "reflection parent must point to the restored glass block");
        assertEquals(0x0220, readInt(restoredParent, "x"), "parent x scalar must restore exactly");
        assertEquals(0x0140, readInt(restoredParent, "y"), "parent y scalar must restore exactly");
        assertEquals(0x01E0, readInt(restoredParent, "baseY"), "parent baseY scalar must restore exactly");
        assertEquals(0xA0, readInt(restoredParent, "glassDist"),
                "parent glassDist scalar must restore exactly");
        assertEquals(0x0220, readInt(restoredReflection, "x"), "reflection x scalar must restore exactly");
        assertEquals(0x0150, readInt(restoredReflection, "y"), "reflection y scalar must restore exactly");
        assertEquals(0x01E0, readInt(restoredReflection, "baseY"),
                "reflection baseY scalar must restore exactly");
        assertEquals(0x90, readInt(restoredReflection, "glassDist"),
                "reflection glassDist scalar must restore exactly");
    }

    @Test
    void glassBlockReflectionBackrefStillRequiresRewindIdentity() {
        Harness harness = Harness.create(List.of());
        ObjectManager objectManager = harness.objectManager();
        Sonic1GlassBlockObjectInstance parent = objectManager.createDynamicObject(
                () -> new Sonic1GlassBlockObjectInstance(NEAR_GLASS));
        Sonic1GlassReflectionInstance unmanagedReflection =
                new Sonic1GlassReflectionInstance(
                        NEAR_GLASS, parent, reflectedSubtype(NEAR_GLASS), isTall(NEAR_GLASS));
        writeObject(parent, "reflectionChild", unmanagedReflection);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> registryFor(objectManager).capture(),
                "required glass reflection backrefs must fail loudly when the target has no rewind identity");
        assertTrue(thrown.getMessage().contains("no registered id for object reference"));
    }

    @Test
    void glassBlockAndReflectionUseGenericRecreateWithoutExplicitDynamicCodecs() {
        assertFalse(DeletedDynamicRewindCodecs.hasRegisteredDynamicCodec(
                        Sonic1GlassBlockObjectInstance.class.getName()),
                "glass block must restore through generic recreate, not a dynamic codec");
        assertFalse(DeletedDynamicRewindCodecs.hasRegisteredDynamicCodec(
                        Sonic1GlassReflectionInstance.class.getName()),
                "glass reflection must restore through generic recreate, not a dynamic codec");
    }

    private static ObjectInstance genericRecreate(ObjectManager objectManager, ObjectSpawn spawn) {
        PerObjectRewindSnapshot state = new PerObjectRewindSnapshot(
                false, false, false, 0, 0, 0, 0, false, 0, false, false, 0, -1, null, null, null);
        ObjectManagerSnapshot.DynamicObjectEntry entry =
                new ObjectManagerSnapshot.DynamicObjectEntry(
                        Sonic1GlassReflectionInstance.class.getName(), spawn, 0, state);
        return ObjectRewindDynamicCodecs.genericRecreate(
                entry, new DynamicObjectRecreateContext(objectManager));
    }

    private static int reflectedSubtype(ObjectSpawn spawn) {
        return ((spawn.subtype() & 0xFF) + 8) & 0x0F;
    }

    private static boolean isTall(ObjectSpawn spawn) {
        return (spawn.subtype() & 0xFF) < 3;
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create(List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
            };
            ObjectManager objectManager = new ObjectManager(
                    spawns, new Sonic1ObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager);
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static ObjectInstance objectWithId(ObjectManager objectManager, ObjectRefId id) {
        List<ObjectInstance> matches = objectManager.getActiveObjects().stream()
                .filter(object -> id.equals(objectManager.captureIdentityContext().requireIdentityTable().idFor(object)))
                .toList();
        assertEquals(1, matches.size(), "expected one live object for rewind id " + id);
        return matches.getFirst();
    }

    private static Sonic1GlassBlockObjectInstance liveParentAt(ObjectManager objectManager, int x) {
        List<Sonic1GlassBlockObjectInstance> matches = liveGlassBlocks(objectManager).stream()
                .filter(object -> object.getX() == x)
                .toList();
        assertEquals(1, matches.size(), "expected one live glass block at X " + x);
        return matches.getFirst();
    }

    private static List<Sonic1GlassBlockObjectInstance> liveGlassBlocks(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object instanceof Sonic1GlassBlockObjectInstance)
                .map(Sonic1GlassBlockObjectInstance.class::cast)
                .filter(object -> !object.isDestroyed())
                .toList();
    }

    private static Sonic1GlassReflectionInstance onlyReflection(ObjectManager objectManager) {
        List<Sonic1GlassReflectionInstance> reflections = liveReflections(objectManager);
        assertEquals(1, reflections.size(), "expected exactly one live glass reflection");
        return reflections.getFirst();
    }

    private static List<Sonic1GlassReflectionInstance> liveReflections(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == Sonic1GlassReflectionInstance.class)
                .map(Sonic1GlassReflectionInstance.class::cast)
                .filter(object -> !object.isDestroyed())
                .toList();
    }

    private static int readInt(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static boolean readBoolean(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static Object readObject(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static void writeInt(Object target, String fieldName, int value) {
        try {
            findField(target.getClass(), fieldName).setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void writeObject(Object target, String fieldName, Object value) {
        try {
            findField(target.getClass(), fieldName).set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
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
