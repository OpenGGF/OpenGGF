package com.openggf.game.rewind.schema;

import com.openggf.game.sonic3k.objects.CutsceneKnucklesLbz2Instance;
import com.openggf.game.sonic3k.objects.LbzKnuxPillarInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.SongFadeTransitionInstance;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Destructive manager restoration, rather than scalar-only cameo snapshots. */
class TestLbzKnucklesInventoryRewind {
    @BeforeEach
    void headless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0x3800, 0x400, 0x4800, 0x1000, 0);
    }

    @AfterEach
    void cleanup() {
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @CsvSource({"false,0", "true,0", "false,19", "true,19", "false,-1", "true,-1", "false,600", "true,600"})
    void restoresExactPillarAndSwingGraphThenReplays(boolean reusePillar, int steps) {
        Fixture f = new Fixture();
        LbzKnuxPillarInstance existing = reusePillar ? f.pillar(0x3D60, 0x61C) : null;
        CutsceneKnucklesLbz2Instance cameo = f.cameo(0x3E28, 0x608);
        LbzKnuxPillarInstance support = cameo.supportPillarsForTest().getFirst();
        if (reusePillar) assertSame(existing, support, "reuse the already managed pillar");
        assertEquals(1, f.manager.activeObjectsOfType(LbzKnuxPillarInstance.class).size());
        assertEquals(List.of(0, 2, 4, 6), cameo.swingChildrenForTest().stream()
                .map(child -> child.getSpawn().subtype()).toList());
        assertEquals(4, cameo.swingChildrenForTest().size());
        cameo.triggerFromShip();
        f.state.setScreenShakeActive(true);
        f.runtime.setLaunchYDelta(1);
        f.step(steps);
        if (steps == -1) {
            var leader = cameo.swingChildrenForTest().getFirst();
            for (int frame = 0; frame < 600 && leader.getCrossingsRemainingForTest() != 1; frame++) f.step(1);
            assertEquals(1, leader.getCrossingsRemainingForTest(), "capture before the sixth crossing");
            assertFalse(leader.isFreeFallingForTest());
        }
        if (steps == 19) {
            assertTrue(cameo.swingChildrenForTest().stream().allMatch(c -> c.isSwingingForTest()
                    && !c.isFreeFallingForTest()));
            assertNotEquals(0x3E28, cameo.getCentreX(), "capture after the leader has moved its owner");
        }
        if (steps == 600) {
            assertTrue(cameo.swingChildrenForTest().stream().allMatch(
                    CutsceneKnucklesLbz2Instance.SwingChild::isFreeFallingForTest));
            assertTrue(cameo.isFlingRequestedForTest());
        }
        // An unrelated cameo closer to the first graph's children must never adopt them.
        CutsceneKnucklesLbz2Instance decoy = f.cameo(
                cameo.swingChildrenForTest().getLast().getCentreX(),
                cameo.swingChildrenForTest().getLast().getCentreY());
        ObjectRefId ownerId = f.id(cameo);
        ObjectRefId decoyId = f.id(decoy);
        ObjectRefId supportId = f.id(support);
        List<ObjectRefId> childIds = cameo.swingChildrenForTest().stream().map(f::id).toList();
        Map<ObjectRefId, List<Object>> capturedGraph = f.graph();
        CompositeSnapshot snapshot = f.registry.capture();
        assertNotNull(cameo.captureRewindState(f.manager.captureIdentityContext()).compactGenericState(),
                "the actual default path must reach compact capture, not silently fall back");
        List<Map<ObjectRefId, List<Object>>> replay = f.timeline(220);

        f.clear();
        CutsceneKnucklesLbz2Instance divergent = f.cameo(0x4000, 0x700);
        f.registry.restore(snapshot);

        CutsceneKnucklesLbz2Instance restored = (CutsceneKnucklesLbz2Instance) f.resolve(ownerId);
        assertNotSame(cameo, restored);
        assertNotSame(divergent, restored);
        assertGraph(capturedGraph, f.graph());
        assertEquals(childIds, restored.swingChildrenForTest().stream().map(f::id).toList());
        assertSame(f.resolve(supportId), restored.supportPillarsForTest().getFirst());
        assertNotSame(support, restored.supportPillarsForTest().getFirst());
        for (ObjectRefId childId : childIds) {
            assertSame(restored, read(f.resolve(childId), "parent"));
        }
        assertEquals(4, ((CutsceneKnucklesLbz2Instance) f.resolve(decoyId)).swingChildrenForTest().size());
        var restoredReplay = f.timeline(220);
        for (int frame = 0; frame < replay.size(); frame++) assertGraph(replay.get(frame), restoredReplay.get(frame));
    }

    @Test
    void retiredMembersAndReusedSlotsDoNotBecomeHistoricalInventory() {
        Fixture f = new Fixture();
        CutsceneKnucklesLbz2Instance owner = f.cameo(0x3E28, 0x608);
        CutsceneKnucklesLbz2Instance.SwingChild retired = owner.swingChildrenForTest().get(1);
        ObjectRefId retiredId = f.id(retired);
        int retiredSlot = retired.getSlotIndex();
        LbzKnuxPillarInstance pillar = owner.supportPillarsForTest().getFirst();
        f.manager.removeDynamicObject(retired);
        LbzKnuxPillarInstance slotReplacement = f.pillar(0x3D80, 0x61C);
        assertEquals(retiredSlot, slotReplacement.getSlotIndex(), "exercise SST reuse by another family member");
        f.manager.removeDynamicObject(pillar);
        f.pillar(0x3D60, 0x61C);
        assertFalse(owner.swingChildrenForTest().contains(retired));
        assertTrue(owner.supportPillarsForTest().isEmpty(), "a same-coordinate replacement is not the old support");
        ObjectRefId ownerId = f.id(owner);
        Map<ObjectRefId, List<Object>> graph = f.graph();
        CompositeSnapshot snapshot = f.registry.capture();
        f.clear();
        f.registry.restore(snapshot);
        CutsceneKnucklesLbz2Instance restored = (CutsceneKnucklesLbz2Instance) f.resolve(ownerId);
        assertNull(f.resolve(retiredId));
        assertEquals(3, restored.swingChildrenForTest().size());
        assertTrue(restored.supportPillarsForTest().isEmpty());
        assertGraph(graph, f.graph());
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void ownerRetirementLeavesExactOrphansToExpireWithoutAdoption(boolean removedBeforeCapture) {
        Fixture f = new Fixture();
        CutsceneKnucklesLbz2Instance owner = f.cameo(0x3E28, 0x608);
        List<ObjectRefId> childIds = owner.swingChildrenForTest().stream().map(f::id).toList();
        ObjectRefId pillarId = f.id(owner.supportPillarsForTest().getFirst());
        owner.setDestroyed(true);
        ObjectRefId ownerId = f.id(owner);
        if (removedBeforeCapture) f.manager.removeDynamicObject(owner);
        CutsceneKnucklesLbz2Instance decoy = f.cameo(0x3E2A, 0x62C);
        ObjectRefId decoyId = f.id(decoy);
        CompositeSnapshot snapshot = f.registry.capture();
        f.step(1);
        Map<ObjectRefId, List<Object>> after = f.graph();
        f.clear();
        f.registry.restore(snapshot);
        for (ObjectRefId id : childIds) {
            assertNotNull(f.resolve(id), "captured orphan must be recreated before its next dispatch");
            if (removedBeforeCapture) {
                assertNull(read(f.resolve(id), "parent"));
            } else {
                assertSame(f.resolve(ownerId), read(f.resolve(id), "parent"));
                assertTrue(f.resolve(ownerId).isDestroyed());
            }
        }
        f.step(1);
        assertGraph(after, f.graph());
        for (ObjectRefId id : childIds) assertNull(f.resolve(id), "orphan expires on its next dispatch");
        assertNotNull(f.resolve(pillarId), "layout scenery has an independent lifetime");
        assertEquals(4, ((CutsceneKnucklesLbz2Instance) f.resolve(decoyId)).swingChildrenForTest().size());
    }

    @Test
    void compactPolicyCapturesLiveLinksAndSavedAnchor() {
        for (Class<?> type : List.of(CutsceneKnucklesLbz2Instance.class,
                CutsceneKnucklesLbz2Instance.SwingChild.class, LbzKnuxPillarInstance.class)) {
            var gaps = new ArrayList<RewindFieldDispositionAudit.Gap>();
            RewindFieldDispositionAudit.auditClass(type, gaps);
            assertEquals(List.of(), gaps, "audit the actual compact path for " + type);
            assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(type));
            assertTrue(RewindSchemaRegistry.defaultObjectSubclassSchemaFor(type).unsupportedFields().isEmpty());
        }
        var childSchema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(
                CutsceneKnucklesLbz2Instance.SwingChild.class);
        assertTrue(childSchema.capturedFields().stream().anyMatch(p -> p.key().fieldName().equals("parent")
                && p.codec().requiresIdentityTable()), "native parent3 must use exact identity fixup");
        assertTrue(childSchema.capturedFields().stream().anyMatch(p -> p.key().fieldName().equals("anchorX")),
                "ROM $3A is saved at creation, not derived from the moved owner");
    }

    @Test
    void publicConstructionHasEmptyUnmanagedObservations() {
        var owner = new CutsceneKnucklesLbz2Instance(
                new ObjectSpawn(0x3E28, 0x608, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 0x18, 0, false, 0x608));
        assertAll(
                () -> assertEquals(List.of(), owner.swingChildrenForTest()),
                () -> assertEquals(List.of(), owner.supportPillarsForTest()));
    }

    @Test
    void removingServicesMakesBothLiveObservationsEmpty() {
        Fixture f = new Fixture();
        var owner = f.cameo(0x3E28, 0x608);
        assertEquals(4, owner.swingChildrenForTest().size());
        assertEquals(1, owner.supportPillarsForTest().size());
        owner.setServices(null);
        assertAll(
                () -> assertEquals(List.of(), owner.swingChildrenForTest()),
                () -> assertEquals(List.of(), owner.supportPillarsForTest()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicChildSnapshotsWithoutIdentityTablePreserveCurrentLinks(boolean explicitNoTable) {
        Fixture f = new Fixture();
        var owner = f.cameo(0x3E28, 0x608);
        var child = owner.swingChildrenForTest().getFirst();
        var otherOwner = f.cameo(0x4000, 0x700);
        var otherChild = otherOwner.swingChildrenForTest().getFirst();
        var snapshot = explicitNoTable ? child.captureRewindState(RewindCaptureContext.none())
                : child.captureRewindState();
        int capturedX = child.getCentreX();
        f.state.setScreenShakeActive(true);
        child.update(0, null);
        child.update(1, null);
        child.update(2, null);
        assertNotEquals(capturedX, child.getCentreX());
        child.restoreRewindState(snapshot);
        assertEquals(capturedX, child.getCentreX());
        assertSame(owner, read(child, "parent"));
        // Absence of a capture table carries no link authority, even when the
        // restore has a table or targets another already-constructed child.
        otherChild.restoreRewindState(snapshot);
        assertSame(otherOwner, read(otherChild, "parent"));
        otherChild.restoreRewindState(snapshot, RewindCaptureContext.withIdentityTable(new RewindIdentityTable()));
        assertSame(otherOwner, read(otherChild, "parent"));
    }

    @Test
    void tableBackedUnregisteredOwnerIsAuthoritativeNullWithoutRestoreTable() {
        Fixture f = new Fixture();
        var owner = f.cameo(0x3E28, 0x608);
        var child = owner.swingChildrenForTest().getFirst();
        var snapshot = child.captureRewindState(
                RewindCaptureContext.withIdentityTable(new RewindIdentityTable()));
        assertSame(owner, read(child, "parent"));
        child.restoreRewindState(snapshot);
        assertNull(read(child, "parent"), "a captured missing identity is authoritative, even without a restore table");
        child.update(0, null);
        assertTrue(child.isDestroyed(), "the captured orphan follows the native deletion tail");
    }

    @Test
    void constrainedNativeAdmissionCapturesRejectedOwnersChildrenBeforeDispatch() {
        var objects = new Sonic3kObjectRegistry() {
            @Override public ObjectSlotLayout objectSlotLayout() {
                var nativeSlots = ObjectSlotLayout.SONIC_3K;
                return new ObjectSlotLayout(nativeSlots.firstDynamicSlot(), 4, nativeSlots.processSlotCount(),
                        nativeSlots.twoAxisCursorPlacement(), nativeSlots.preallocatesLostRingOwnerSlot(),
                        nativeSlots.lostRingRemainderAllocatesAfterOwnerSlot());
            }
        };
        Fixture f = new Fixture(objects);
        f.services.withAudioManager(mock(com.openggf.audio.AudioManager.class));
        f.services.camera().setX((short) 0x3D00);
        f.services.camera().setY((short) 0x500);
        var constructed = new CutsceneKnucklesLbz2Instance[1];
        var admitted = f.manager.createDynamicObject(() -> {
            constructed[0] = assertInstanceOf(CutsceneKnucklesLbz2Instance.class, objects.create(
                    new ObjectSpawn(0x3E28, 0x608, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 0x18, 0, false, 0x608)));
            return constructed[0];
        });
        assertNull(admitted, "children exhaust the native slots before owner admission");
        var rejected = constructed[0];
        assertTrue(rejected.isDestroyed());
        assertEquals(-1, rejected.getSlotIndex());
        assertTrue(f.services.objectQuery().identityOf(rejected).isEmpty());
        var population = f.manager.getActiveObjects().stream()
                .sorted(Comparator.comparingInt(o -> ((AbstractObjectInstance) o).getSlotIndex())).toList();
        assertEquals(List.of(SongFadeTransitionInstance.class, LbzKnuxPillarInstance.class,
                CutsceneKnucklesLbz2Instance.SwingChild.class, CutsceneKnucklesLbz2Instance.SwingChild.class),
                population.stream().map(Object::getClass).toList());
        assertEquals(List.of(4, 5, 6, 7), population.stream()
                .map(o -> ((AbstractObjectInstance) o).getSlotIndex()).toList());
        var children = f.manager.activeObjectsOfType(CutsceneKnucklesLbz2Instance.SwingChild.class);
        assertEquals(List.of(0, 2), children.stream().map(c -> c.getSpawn().subtype()).toList());
        for (var child : children) {
            assertSame(rejected, read(child, "parent"), "failed admission never receives a removal callback");
            assertFalse(child.isDestroyed());
        }
        var ids = population.stream().map(f::id).toList();
        var childIds = children.stream().map(f::id).toList();
        CompositeSnapshot snapshot = f.registry.capture();
        f.manager.update(0x3D00, f.services.camera().getFocusedSprite(), List.of(), 0, false);
        var after = f.graph();
        assertTrue(f.manager.activeObjectsOfType(CutsceneKnucklesLbz2Instance.SwingChild.class).isEmpty());
        var replacement = f.pillar(0x3E80, 0x61C);
        assertEquals(6, replacement.getSlotIndex(), "uninterrupted orphan deletion frees the first child slot");
        var secondReplacement = f.pillar(0x3EA0, 0x61C);
        assertEquals(7, secondReplacement.getSlotIndex());
        var replacementIds = List.of(f.id(replacement), f.id(secondReplacement));
        f.clear();
        f.registry.restore(snapshot);
        assertTrue(f.manager.activeObjectsOfType(CutsceneKnucklesLbz2Instance.class).isEmpty());
        for (int i = 0; i < ids.size(); i++) {
            var restored = f.resolve(ids.get(i));
            assertNotNull(restored, "restore " + population.get(i).getClass().getSimpleName() + " " + ids.get(i));
            assertNotSame(population.get(i), restored);
            assertEquals(4 + i, ((AbstractObjectInstance) restored).getSlotIndex());
        }
        for (var id : childIds) assertNull(read(f.resolve(id), "parent"));
        f.manager.update(0x3D00, f.services.camera().getFocusedSprite(), List.of(), 0, false);
        assertGraph(after, f.graph());
        for (var id : childIds) assertNull(f.resolve(id), "restored orphan deletion matches uninterrupted dispatch");
        var replayReplacement = f.pillar(0x3E80, 0x61C);
        assertEquals(6, replayReplacement.getSlotIndex(), "replayed orphan deletion frees the same child slot");
        var secondReplayReplacement = f.pillar(0x3EA0, 0x61C);
        assertEquals(7, secondReplayReplacement.getSlotIndex());
        assertEquals(replacementIds, List.of(f.id(replayReplacement), f.id(secondReplayReplacement)),
                "post-restore allocations retain both next dynamic identities");
    }

    private static void assertGraph(Map<ObjectRefId, List<Object>> expected, Map<ObjectRefId, List<Object>> actual) {
        assertEquals(expected.keySet(), actual.keySet(), "exact managed graph membership");
        expected.forEach((id, state) -> assertEquals(state, actual.get(id), "slot, link and state for " + id));
    }

    private static Object read(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class Fixture {
        final TestObjectServices services = new TestObjectServices();
        final GameStateManager state = new GameStateManager();
        final LbzZoneRuntimeState runtime = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        final ObjectManager manager;
        final RewindRegistry registry = new RewindRegistry();

        Fixture() { this(null); }

        Fixture(ObjectRegistry objects) {
            Camera camera = new Camera();
            camera.setFocusedSprite(new TestablePlayableSprite("sonic", (short) 0x3E00, (short) 0x600));
            var zones = new ZoneRuntimeRegistry();
            zones.install(runtime);
            services.withCamera(camera).withGameState(state).withZoneRuntimeRegistry(zones);
            manager = new ObjectManager(List.of(), objects, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            services.withDirectObjectManager(manager);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            registry.register(manager.rewindSnapshottable());
        }

        CutsceneKnucklesLbz2Instance cameo(int x, int y) {
            return manager.createDynamicObject(() -> new CutsceneKnucklesLbz2Instance(
                    new ObjectSpawn(x, y, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 0x18, 0, false, y)));
        }

        LbzKnuxPillarInstance pillar(int x, int y) {
            return manager.createDynamicObject(() -> new LbzKnuxPillarInstance(
                    new ObjectSpawn(x, y, Sonic3kObjectIds.LBZ_KNUX_PILLAR, 0, 2, false, y)));
        }

        ObjectRefId id(ObjectInstance object) {
            ObjectRefId id = manager.captureIdentityContext().requireIdentityTable().idFor(object);
            assertNotNull(id);
            return id;
        }

        ObjectInstance resolve(ObjectRefId id) {
            return manager.captureIdentityContext().requireIdentityTable().resolve(id);
        }

        List<ObjectInstance> family() {
            return manager.getActiveObjects().stream().filter(object -> object instanceof CutsceneKnucklesLbz2Instance
                    || object instanceof CutsceneKnucklesLbz2Instance.SwingChild
                    || object instanceof LbzKnuxPillarInstance)
                    .sorted(Comparator.comparingInt(object -> ((AbstractObjectInstance) object).getSlotIndex())).toList();
        }

        void step(int count) {
            for (int i = 0; i < count; i++) {
                for (ObjectInstance object : family()) object.update(i, null);
                for (ObjectInstance object : family()) if (object.isDestroyed()) manager.removeDynamicObject(object);
            }
        }

        List<Map<ObjectRefId, List<Object>>> timeline(int count) {
            var result = new ArrayList<Map<ObjectRefId, List<Object>>>();
            for (int i = 0; i < count; i++) {
                step(1);
                result.add(graph());
            }
            return result;
        }

        void clear() {
            for (ObjectInstance object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
        }

        Map<ObjectRefId, List<Object>> graph() {
            var result = new LinkedHashMap<ObjectRefId, List<Object>>();
            for (ObjectInstance object : family()) {
                var state = new ArrayList<Object>();
                state.add(((AbstractObjectInstance) object).getSlotIndex());
                for (Field field : object.getClass().getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    try {
                        Object value = field.get(object);
                        if (field.getType().isPrimitive() || field.getType().isEnum()
                                || field.getType() == ObjectRefId.class) state.add(value);
                        else if (field.getName().equals("parent")) state.add(value == null ? null : id((ObjectInstance) value));
                    } catch (IllegalAccessException e) { throw new AssertionError(e); }
                }
                result.put(id(object), state);
            }
            return result;
        }
    }
}
