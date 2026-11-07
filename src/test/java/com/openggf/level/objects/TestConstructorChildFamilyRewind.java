package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Constructor children precede their ordinary dynamic parents in capture order.
 * Adoption must retain each captured family even when another same-class child's
 * entry is encountered before its own parent has reconstructed.
 *
 * <p>The child has no independent recreation hook: its constructor-owned final
 * links are rebuilt by its parent, as supported by construction-child adoption.
 * Both links use the default structural policy; no custom codec repairs them.
 */
class TestConstructorChildFamilyRewind {

    @BeforeEach
    void initHeadlessGraphics() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void singleFamilyRestoresMovingChildStateAndBothStructuralLinksRepeatedly() {
        ObjectManager manager = createHarness();
        FamilyParent source = manager.createDynamicObject(() -> new FamilyParent(spawnAt(0x100)));
        assertNotNull(source);
        advance(source.child, 3);
        ObjectManagerSnapshot snapshot = manager.rewindSnapshottable().capture();
        CapturedFamily family = capturedFamily(manager, snapshot, source);

        assertEquals(List.of(family.child().objectId(), family.parent().objectId()),
                snapshot.dynamicObjects().stream().map(DynamicObjectEntry::objectId).toList());
        assertNotEquals(source.getX() + 0x10, family.child().spawn().x(),
                "a moving child's captured spawn need not match its constructor offset");

        for (int restore = 0; restore < 3; restore++) {
            manager.rewindSnapshottable().restore(snapshot);
            assertRestoredFamily(manager, family);
            FamilyParent restored = restoredParent(manager, family);
            assertNotSame(source, restored);
            assertNotSame(source.child, restored.child);
            advance(restored.child, 2);
        }
    }

    @Test
    void interleavedSameClassFamiliesKeepCapturedChildrenWithTheirOwnParentsRepeatedly() {
        ObjectManager manager = createHarness();
        FamilyParent first = manager.createDynamicObject(() -> new FamilyParent(spawnAt(0x100)));
        FamilyParent second = manager.createDynamicObject(() -> new FamilyParent(spawnAt(0x200)));
        assertNotNull(first);
        assertNotNull(second);
        advance(first.child, 3);
        advance(second.child, 7);
        ObjectManagerSnapshot snapshot = manager.rewindSnapshottable().capture();
        CapturedFamily firstFamily = capturedFamily(manager, snapshot, first);
        CapturedFamily secondFamily = capturedFamily(manager, snapshot, second);

        assertEquals(List.of(firstFamily.child().objectId(), firstFamily.parent().objectId(),
                        secondFamily.child().objectId(), secondFamily.parent().objectId()),
                snapshot.dynamicObjects().stream().map(DynamicObjectEntry::objectId).toList(),
                "ordinary dynamic admission produces child, parent, child, parent");
        assertEquals(firstFamily.child().className(), secondFamily.child().className());
        assertEquals(firstFamily.child().spawn().subtype(), secondFamily.child().spawn().subtype());
        assertNotEquals(firstFamily.parent().objectId(), secondFamily.parent().objectId());
        assertNotEquals(firstFamily.child().objectId(), secondFamily.child().objectId());
        assertNotEquals(firstFamily.child().slotIndex(), secondFamily.child().slotIndex());
        assertSame(first, first.child.parent);
        assertSame(second, second.child.parent);

        for (int restore = 0; restore < 3; restore++) {
            manager.rewindSnapshottable().restore(snapshot);
            assertEquals(4, manager.getActiveObjects().size(),
                    "restoration must neither duplicate nor discard a family member");
            assertAll("captured families after restore " + restore,
                    () -> assertRestoredFamily(manager, firstFamily),
                    () -> assertRestoredFamily(manager, secondFamily));
        }
    }

    @Test
    void retiredAndExcludedParentsCannotDonateChildrenToAnotherLiveFamily() {
        for (boolean excluded : List.of(false, true)) {
            ObjectManager manager = createHarness();
            FamilyParent orphanOwner = manager.createDynamicObject(() -> new FamilyParent(spawnAt(0x100)));
            FamilyParent liveOwner = manager.createDynamicObject(() -> new FamilyParent(spawnAt(0x200)));
            if (excluded) {
                manager.removeDynamicObject(orphanOwner);
                manager.addAuxiliaryDynamicObject(orphanOwner);
            } else {
                manager.removeDynamicObject(orphanOwner);
            }
            var snapshot = manager.rewindSnapshottable().capture();
            var orphan = entryFor(snapshot, manager.captureIdentityContext().requireIdentityTable()
                    .encodeObject(orphanOwner.child));
            assertTrue(orphan.constructionFamily().known());
            assertNull(orphan.constructionFamily().parentId());
            var live = capturedFamily(manager, snapshot, liveOwner);
            manager.rewindSnapshottable().restore(snapshot);
            assertRestoredFamily(manager, live);
            assertEquals(2, manager.getActiveObjects().stream()
                    .filter(o -> o instanceof FamilyParent || o instanceof FamilyChild).count());
        }
    }

    @Test
    void independentlyRecreatableChildSurvivesWhenCapturedParentCannotRecreate() {
        var manager = createHarness();
        var source = manager.createDynamicObject(() -> new DeferredRoutineParent(spawnAt(0x100)));
        source.spawnLater();
        var snapshot = manager.rewindSnapshottable().capture();
        var childEntry = snapshot.dynamicObjects().getLast();
        assertNotNull(childEntry.constructionFamily().parentId());
        manager.rewindSnapshottable().restore(snapshot);
        assertEquals(1, manager.getActiveObjects().size());
        var child = assertInstanceOf(RoutineChild.class, manager.getActiveObjects().iterator().next());
        assertSame(child, manager.captureIdentityContext().requireIdentityTable().resolve(childEntry.objectId()));
        assertEquals(childEntry.slotIndex(), child.getSlotIndex());
        var recaptured = manager.rewindSnapshottable().capture().dynamicObjects().getFirst();
        assertTrue(recaptured.constructionFamily().known());
        assertNull(recaptured.constructionFamily().parentId(), "absent owners are not captured identities");
    }

    static final class DeferredRoutineParent extends AbstractObjectInstance implements RewindRecreatable {
        DeferredRoutineParent(ObjectSpawn spawn) { super(spawn, "deferred-routine"); }
        void spawnLater() { spawnFreeChild(() -> new RoutineChild(getSpawn())); }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) { return null; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    @Test
    void oldConstructionContextRecipeRetainsDirectCallerCompatibility() {
        var manager = createHarness();
        var source = manager.createDynamicObject(() -> new ContextOnly(spawnAt(0x100)));
        var recreated = source.recreateForRewind(new RewindRecreateContext(source.getSpawn(),
                source.captureRewindState(), manager.objectServicesForRewind()));
        assertInstanceOf(ContextOnly.class, recreated);
    }

    static final class ContextOnly extends AbstractObjectInstance implements SpawnConstructionContextRewindRecreatable {
        ContextOnly(ObjectSpawn spawn) { super(spawn, "context-only"); assertSame(CONSTRUCTION_CONTEXT.get(), services()); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    @Test
    void freshUninstrumentedAdmissionCannotStealAKnownFamiliesCandidate() {
        var manager = createHarness();
        var source = manager.createDynamicObject(() -> new FamilyParent(spawnAt(0x100)));
        var independent = new FamilyChild(source);
        manager.addDynamicObject(independent);
        var snapshot = manager.rewindSnapshottable().capture();
        var family = capturedFamily(manager, snapshot, source);
        var raw = snapshot.dynamicObjects().getLast();
        assertTrue(!raw.constructionFamily().known());
        assertNull(raw.constructionFamily().parentId());
        manager.rewindSnapshottable().restore(snapshot);
        assertRestoredFamily(manager, family);
        assertEquals(2, manager.getActiveObjects().size());
    }

    @Test
    void legacyEntriesKeepExplicitLegacySemanticsOnRecapture() {
        ObjectManager manager = createHarness();
        FamilyParent source = manager.createDynamicObject(() -> new FamilyParent(spawnAt(0x100)));
        var snapshot = manager.rewindSnapshottable().capture();
        var legacy = snapshot.dynamicObjects().stream().map(e -> new DynamicObjectEntry(
                e.className(), e.spawn(), e.slotIndex(), e.state(), e.playerOwner(), e.objectId(),
                e.ownerModId(), e.rewindableAuxiliary(), e.playerOwnerId())).toList();
        manager.rewindSnapshottable().restore(withDynamics(snapshot, legacy));
        var recaptured = manager.rewindSnapshottable().capture();
        recaptured.dynamicObjects().forEach(e -> assertNull(e.constructionFamily()));
        assertRestoredFamily(manager, capturedFamilyFromEntries(snapshot, source.child.updates));
    }

    @Test
    void nestedConstructorGenerationsAndSameSubtypeSiblingsKeepTheirFamily() {
        ObjectManager manager = createHarness();
        manager.createDynamicObject(() -> new NestedParent(spawnAt(0x100)));
        manager.createDynamicObject(() -> new NestedParent(spawnAt(0x200)));
        var snapshot = manager.rewindSnapshottable().capture();
        for (int repeat = 0; repeat < 3; repeat++) {
            manager.rewindSnapshottable().restore(snapshot);
            assertEquals(8, manager.getActiveObjects().size());
            for (var object : manager.getActiveObjects()) {
                if (object instanceof NestedParent parent) {
                    assertSame(parent, parent.child.parent);
                    assertSame(parent.child, parent.child.first.parent);
                    assertSame(parent.child, parent.child.second.parent);
                    assertNotSame(parent.child.first, parent.child.second);
                }
            }
            assertEquals(snapshot.dynamicObjects().stream().map(DynamicObjectEntry::constructionFamily).toList(),
                    manager.rewindSnapshottable().capture().dynamicObjects().stream()
                            .map(DynamicObjectEntry::constructionFamily).toList());
        }
    }

    @Test
    void routineSpawnWithoutConstructorCandidateStillUsesGenericRecreation() {
        ObjectManager manager = createHarness();
        var source = manager.createDynamicObject(() -> new RoutineParent(spawnAt(0x100)));
        source.spawnLater();
        var snapshot = manager.rewindSnapshottable().capture();
        manager.rewindSnapshottable().restore(snapshot);
        assertEquals(2, manager.getActiveObjects().size());
        assertEquals(snapshot.dynamicObjects().stream().map(DynamicObjectEntry::constructionFamily).toList(),
                manager.rewindSnapshottable().capture().dynamicObjects().stream()
                        .map(DynamicObjectEntry::constructionFamily).toList());
    }

    private static CapturedFamily capturedFamilyFromEntries(ObjectManagerSnapshot snapshot, int updates) {
        return new CapturedFamily(snapshot.dynamicObjects().get(1), snapshot.dynamicObjects().get(0), updates);
    }

    private static ObjectManagerSnapshot withDynamics(ObjectManagerSnapshot s, List<DynamicObjectEntry> entries) {
        return new ObjectManagerSnapshot(s.usedSlotsBits(), s.slots(), s.frameCounter(), s.vblaCounter(),
                s.currentExecSlot(), s.peakSlotCount(), s.bucketsDirty(), s.childSpawns(), entries,
                s.placement(), s.solidContactRiding(), s.solidContactState(), s.planeSwitchers(),
                s.touchResponseOverlap(), s.dynamicObjectIdCounter(), s.collisionResponseState(),
                s.sineCosineInputFraction());
    }

    static final class NestedParent extends AbstractObjectInstance implements SpawnRewindRecreatable {
        final NestedChild child;
        NestedParent(ObjectSpawn spawn) { super(spawn, "nested"); child = spawnChild(() -> new NestedChild(this)); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    static final class NestedChild extends AbstractObjectInstance {
        final NestedParent parent;
        final Grandchild first;
        final Grandchild second;
        NestedChild(NestedParent parent) {
            super(parent.getSpawn(), "nested-child"); this.parent = parent;
            first = spawnChild(() -> new Grandchild(this));
            second = spawnChild(() -> new Grandchild(this));
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    static final class Grandchild extends AbstractObjectInstance {
        final NestedChild parent;
        Grandchild(NestedChild parent) { super(parent.getSpawn(), "grandchild"); this.parent = parent; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    static final class RoutineParent extends AbstractObjectInstance implements SpawnRewindRecreatable {
        RoutineParent(ObjectSpawn spawn) { super(spawn, "routine"); }
        void spawnLater() { spawnFreeChild(() -> new RoutineChild(getSpawn())); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    static final class RoutineChild extends AbstractObjectInstance implements SpawnRewindRecreatable {
        RoutineChild(ObjectSpawn spawn) { super(spawn, "routine-child"); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static CapturedFamily capturedFamily(
            ObjectManager manager, ObjectManagerSnapshot snapshot, FamilyParent source) {
        RewindIdentityTable identities = manager.captureIdentityContext().requireIdentityTable();
        DynamicObjectEntry parent = entryFor(snapshot, identities.encodeObject(source));
        DynamicObjectEntry child = entryFor(snapshot, identities.encodeObject(source.child));
        return new CapturedFamily(parent, child, source.child.updates);
    }

    private static DynamicObjectEntry entryFor(
            ObjectManagerSnapshot snapshot, com.openggf.game.rewind.identity.ObjectRefId id) {
        assertNotNull(id, "every admitted family member must have a stable captured identity");
        return snapshot.dynamicObjects().stream()
                .filter(entry -> id.equals(entry.objectId())).findFirst().orElseThrow();
    }

    private static FamilyParent restoredParent(ObjectManager manager, CapturedFamily family) {
        return assertInstanceOf(FamilyParent.class,
                manager.captureIdentityContext().requireIdentityTable().resolve(family.parent().objectId()));
    }

    private static void assertRestoredFamily(ObjectManager manager, CapturedFamily family) {
        RewindIdentityTable identities = manager.captureIdentityContext().requireIdentityTable();
        FamilyParent parent = assertInstanceOf(FamilyParent.class,
                identities.resolve(family.parent().objectId()));
        FamilyChild child = assertInstanceOf(FamilyChild.class,
                identities.resolve(family.child().objectId()));
        assertAll("family at parent x=" + family.parent().spawn().x(),
                () -> assertTrue(manager.getActiveObjects().contains(parent)),
                () -> assertTrue(manager.getActiveObjects().contains(child)),
                () -> assertEquals(family.parent().slotIndex(), parent.getSlotIndex()),
                () -> assertEquals(family.child().slotIndex(), child.getSlotIndex()),
                () -> assertEquals(family.parent().spawn().x(), parent.getX()),
                () -> assertEquals(family.child().spawn().x(), child.getX()),
                () -> assertEquals(family.child().spawn().y(), child.getY()),
                () -> assertEquals(family.updates(), child.updates),
                () -> assertSame(parent, child.parent,
                        "the captured child's structural parent must be its captured parent identity"),
                () -> assertSame(child, parent.child,
                        "the captured parent must retain its own captured child identity"));
    }

    private static void advance(FamilyChild child, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            child.update(frame, null);
        }
    }

    private static ObjectSpawn spawnAt(int x) {
        return new ObjectSpawn(x, 0x80, 0x7001, 0, 0, false, 0);
    }

    private static ObjectManager createHarness() {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        };
        holder[0] = new ObjectManager(List.of(), null, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0].reset(0);
        return holder[0];
    }

    private record CapturedFamily(DynamicObjectEntry parent, DynamicObjectEntry child, int updates) { }

    static final class FamilyParent extends AbstractObjectInstance
            implements SpawnConstructionContextRewindRecreatable {
        final FamilyChild child;

        FamilyParent(ObjectSpawn spawn) {
            super(spawn, "ConstructorFamilyParent");
            child = spawnChild(() -> new FamilyChild(this));
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    static final class FamilyChild extends AbstractObjectInstance {
        final FamilyParent parent;
        int updates;

        FamilyChild(FamilyParent parent) {
            super(new ObjectSpawn(parent.getX() + 0x10, parent.getY(), 0x7002, 0, 0, false, 0),
                    "ConstructorFamilyChild");
            this.parent = parent;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            updates++;
            updateDynamicSpawn(getX() + 1, getY());
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
