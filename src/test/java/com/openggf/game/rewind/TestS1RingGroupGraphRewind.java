package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.Sonic1Level;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.objects.Sonic1RingInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Stock and caller-supplied recipe controls for the childRingSpawns disposition review. */
@RequiresRom(SonicGame.SONIC_1)
class TestS1RingGroupGraphRewind {
    private static SharedLevel sharedLevel;
    private Harness harness;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
    }

    @AfterAll
    static void dispose() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @BeforeEach
    void setUp() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        Sonic1Level level = assertInstanceOf(Sonic1Level.class, GameServices.level().getCurrentLevel());
        // Select a real three-ring ROM placement, retaining its placement IDs and canonical objects.
        ObjectSpawn spawn = level.getObjects().stream()
                .filter(candidate -> level.ringObjectPlacementMapping().getOrDefault(candidate, List.of()).size() == 3)
                .min(Comparator.comparingInt(ObjectSpawn::x).thenComparingInt(ObjectSpawn::y)).orElseThrow();
        List<RingSpawn> group = level.ringObjectPlacementMapping().get(spawn);
        RingManager rings = GameServices.level().getRingManager();
        int cameraX = Math.max(0, spawn.x() - 128);
        Camera camera = new Camera() {
            @Override public short getX() { return (short) cameraX; }
            @Override public short getY() { return (short) (spawn.y() - 80); }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public RingManager ringManager() { return rings; }
            @Override public Camera camera() { return camera; }
            @Override public Sonic1Level currentLevel() { return level; }
        };
        Sonic1ObjectRegistry factory = new Sonic1ObjectRegistry();
        // A stale local fallback must not win over the current level's actual mapping.
        factory.setRingSpawnMapping(Map.of(spawn, List.of(new RingSpawn(spawn.x(), spawn.y()))));
        ObjectManager objects = new ObjectManager(List.of(spawn), factory, 0, null, null,
                GameServices.graphics(), camera, services);
        holder[0] = objects;
        objects.enableCounterBasedRespawn();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        rings.reset(cameraX);
        objects.reset(cameraX);
        fixture.sprite().setInvulnerableFrames(0);
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(rings);
        rewind.register(objects.rewindSnapshottable());
        harness = new Harness(objects, rings, rewind, spawn, group, cameraX, fixture.sprite());
    }

    @ParameterizedTest(name = "placed INIT collected mask {0}")
    @ValueSource(ints = {0, 1, 2, 7})
    void placedPreInitRebuildsCanonicalRecipeAndReplaysExactAllocation(int collectedMask) {
        Harness h = harness;
        Sonic1RingInstance original = h.parent();
        int parentSlot = original.getSlotIndex();
        assertEquals(0, original.getCollisionFlags(), "capture must precede the real INIT dispatch");
        assertEquals(2, original.getReservedChildSlotCount());
        assertRecipeIdentity(original, h.group());
        assertTrue(h.objects().isSpawnStateBitSet(h.spawn(), 7), "ObjPosLoad set the respawn block");
        h.collect(collectedMask);
        CompositeSnapshot beforeInit = h.rewind().capture();
        ObjectManagerSnapshot objectsBefore = objectSnapshot(beforeInit);
        assertEquals(1, objectsBefore.slots().size(), "parent must use placed registry restoration");
        assertTrue(objectsBefore.dynamicObjects().isEmpty(), "no fabricated dynamic INIT parent");
        assertNotNull(objectsBefore.slots().getFirst().state().compactGenericState(),
                "exercise the production compact capture path");

        h.tick(1);
        assertAllocation(collectedMask, parentSlot);
        GraphView expected = h.view();
        List<Sonic1RingInstance> forwardObjects = h.live();
        h.tick(2);
        GraphView expectedNext = h.view();

        h.rewind().restore(beforeInit);
        Sonic1RingInstance restored = h.parent();
        assertNotSame(original, restored, "force destructive placed recreation through the stock registry");
        assertRecipeIdentity(restored, h.group());
        assertEquals(objectsBefore.slots().getFirst().objectId(), h.id(restored));
        assertTrue(h.objects().isSpawnStateBitSet(h.spawn(), 7));
        for (int i = 0; i < 3; i++) {
            assertEquals((collectedMask & (1 << i)) != 0, h.rings().isCollected(h.group().get(i)));
        }
        h.tick(1);
        assertAllocation(collectedMask, parentSlot);
        assertEquals(expected, h.view(), "slot/order/identity/collection/respawn must match uninterrupted INIT");
        for (Sonic1RingInstance ring : h.live()) {
            assertTrue(forwardObjects.stream().noneMatch(before -> before == ring));
        }
        h.tick(2);
        assertEquals(expectedNext, h.view(), "the next dispatch must not duplicate the restored group");
    }

    @ParameterizedTest(name = "progressed child collection {0}")
    @ValueSource(ints = {1, 2})
    void progressedDynamicChildrenRestoreFreshWithoutDuplicatingAndReplaySparkleRetirement(int childIndex) {
        Harness h = harness;
        h.tick(1);
        assertAllocation(0, h.parent().getSlotIndex());
        Sonic1RingInstance child = h.live().stream()
                .filter(ring -> ringSpawn(ring) == h.group().get(childIndex)).findFirst().orElseThrow();
        child.onTouchResponse(h.player(), new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL),
                h.objects().getFrameCounter());
        assertTrue(h.rings().isCollected(h.group().get(childIndex)), "real child touch must collect");
        h.tick(2);
        CompositeSnapshot progressed = h.rewind().capture();
        assertEquals(2, objectSnapshot(progressed).dynamicObjects().size());
        List<Sonic1RingInstance> before = h.live();
        GraphView expectedAtRestore = h.view();
        List<GraphView> expectedForward = new ArrayList<>();
        for (int frame = 3; frame <= 42; frame++) {
            h.tick(frame);
            expectedForward.add(h.view());
        }
        assertEquals(2, h.live().size(), "positive control must reach the collected child's deletion");

        // Erase the old graph and materialize a divergent group before restoring the progressed graph.
        h.objects().reset(h.cameraX());
        h.collect(7);
        h.tick(80);
        assertTrue(h.live().isEmpty());
        h.rewind().restore(progressed);
        assertEquals(expectedAtRestore, h.view());
        assertEquals(3, h.live().size());
        for (Sonic1RingInstance restored : h.live()) {
            assertTrue(before.stream().noneMatch(old -> old == restored), "all graph objects must be fresh");
            assertSame(h.group().stream().filter(r -> r.placementId() == ringSpawn(restored).placementId())
                    .findFirst().orElseThrow(), ringSpawn(restored), "keep canonical RingManager identity");
            if (restored.getSpawn() != h.spawn()) {
                assertEquals(0, restored.getReservedChildSlotCount(), "progressed dynamic children have no recipe");
            }
        }
        for (int frame = 3; frame <= 42; frame++) {
            h.tick(frame);
            assertEquals(expectedForward.get(frame - 3), h.view(), "replay frame " + frame);
        }
    }

    @ParameterizedTest(name = "supplied dynamic INIT collected mask {0}")
    @ValueSource(ints = {0, 1, 2, 7})
    void dynamicPreInitPreservesSuppliedOrderAndReplaysAllocationAndRetirement(int collectedMask) {
        Harness stock = harness;
        // The exposed constructor accepts a recipe independent of subtype count and coordinates.
        List<RingSpawn> recipe = List.of(stock.group().get(0), stock.group().get(2), stock.group().get(1));
        Harness h = suppliedHarness(recipe, false);
        Sonic1RingInstance original = h.parent();
        int parentSlot = original.getSlotIndex();
        h.collect(collectedMask);
        CompositeSnapshot before = h.rewind().capture();
        assertEquals(1, objectSnapshot(before).dynamicObjects().size());
        assertNotNull(objectSnapshot(before).dynamicObjects().getFirst().state().compactGenericState());
        assertRecipeIdentity(original, recipe);
        h.tick(1);
        assertSuppliedAllocation(h, collectedMask, parentSlot);
        List<GraphView> forward = new ArrayList<>();
        forward.add(h.view());
        for (Sonic1RingInstance ring : h.live()) {
            ring.onTouchResponse(h.player(), new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL),
                    h.objects().getFrameCounter());
        }
        for (int frame = 2; frame <= 42; frame++) {
            h.tick(frame);
            forward.add(h.view());
        }
        assertTrue(h.live().isEmpty(), "positive control reaches forward retirement");
        h.objects().reset(h.cameraX());
        h.rewind().restore(before);
        assertNotSame(original, h.parent());
        assertRecipeIdentity(h.parent(), recipe);
        assertEquals(0, h.parent().getCollisionFlags());
        h.tick(1);
        assertSuppliedAllocation(h, collectedMask, parentSlot);
        assertEquals(forward.getFirst(), h.view());
        for (Sonic1RingInstance ring : h.live()) {
            ring.onTouchResponse(h.player(), new TouchResponseResult(7, 6, 6, TouchCategory.SPECIAL),
                    h.objects().getFrameCounter());
        }
        for (int frame = 2; frame <= 42; frame++) {
            h.tick(frame);
            assertEquals(forward.get(frame - 1), h.view(), "supplied recipe forward frame " + frame);
        }
    }

    @ParameterizedTest(name = "duplicate coordinates with persisted IDs {0}")
    @ValueSource(booleans = {true, false})
    void registryRecipeCaptureFreezesSequenceOnlyAtCaptureAndRebindsDuplicateCoordinates(boolean persistedIds) {
        Harness stock = harness;
        RingSpawn first = new RingSpawn(stock.spawn().x(), stock.spawn().y(), persistedIds ? 700 : -1);
        RingSpawn duplicate = new RingSpawn(first.x(), first.y(), persistedIds ? 701 : -1);
        RingSpawn last = new RingSpawn(first.x() + 32, first.y(), persistedIds ? 702 : -1);
        // The selected parent is the SECOND placement at these coordinates.
        List<RingSpawn> supplied = new ArrayList<>(List.of(duplicate, last, first));
        stock.rings().resyncSpawnList(List.of(first, duplicate, last));
        Harness h = suppliedHarness(supplied, true);
        Sonic1RingInstance original = h.parent();
        supplied.set(1, first);
        supplied.set(2, last);
        List<RingSpawn> capturedOrder = List.copyOf(supplied);
        assertRecipeIdentity(original, capturedOrder); // preserve the constructor's caller-owned subList
        CompositeSnapshot before = h.rewind().capture();
        supplied.set(1, last);
        supplied.set(2, first);
        assertRecipeIdentity(original, supplied); // capture itself must not freeze live construction semantics
        RingSpawn reboundFirst = new RingSpawn(first.x(), first.y(), first.placementId());
        RingSpawn reboundDuplicate = new RingSpawn(duplicate.x(), duplicate.y(), duplicate.placementId());
        RingSpawn reboundLast = new RingSpawn(last.x(), last.y(), last.placementId());
        // A fresh table owns fresh Java records, even for equal legacy values.
        h.rings().resyncSpawnList(List.of(reboundFirst, reboundDuplicate, reboundLast));
        h.objects().reset(h.cameraX());
        h.rewind().restore(before);
        assertNotSame(original, h.parent());
        assertEquals(capturedOrder, List.of(reboundDuplicate, reboundFirst, reboundLast));
        assertRecipeIdentity(h.parent(), List.of(reboundDuplicate, reboundFirst, reboundLast));
        assertSame(reboundDuplicate, ringSpawn(h.parent()), "coordinates alone would bind the wrong placement");
        assertTrue(h.rings().collectPlacedRing(reboundFirst, h.player(), 0));
        h.tick(1);
        assertEquals(List.of(duplicate.placementId(), last.placementId()),
                h.live().stream().map(r -> ringSpawn(r).placementId()).toList());
        Sonic1RingInstance child = h.live().getLast();
        assertSame(reboundLast, ringSpawn(child));
        assertEquals(h.spawn().x() + 32, child.getX(), "filtered child keeps its original recipe index");
        assertEquals(h.parent().getSlotIndex() + 1, child.getSlotIndex(), "no collected-child reservation");
        CompositeSnapshot progressed = h.rewind().capture();
        h.objects().reset(h.cameraX());
        h.rewind().restore(progressed);
        assertSame(reboundDuplicate, ringSpawn(h.parent()));
        assertSame(reboundLast, ringSpawn(h.live().getLast()), "dynamic child must also retain placement identity");
    }

    @Test
    void dynamicUnregisteredRecipeKeepsValuesEvenAtRegisteredCoordinates() {
        List<RingSpawn> canonical = harness.group();
        RingSpawn suppliedParent = new RingSpawn(canonical.getFirst().x(), canonical.getFirst().y(), 900);
        RingSpawn suppliedChild = new RingSpawn(canonical.getLast().x(), canonical.getLast().y(), 901);
        Harness h = suppliedHarness(List.of(suppliedParent, canonical.get(1), suppliedChild), false);
        h.collect(2); // only the registered child is collected
        CompositeSnapshot before = h.rewind().capture();
        h.tick(1);
        GraphView expected = h.view();
        h.objects().reset(h.cameraX());
        h.rewind().restore(before);
        assertEquals(suppliedParent, ringSpawn(h.parent()));
        assertNotSame(suppliedParent, ringSpawn(h.parent()), "default snapshot copy need not retain record identity");
        List<?> children = (List<?>) field(h.parent(), "childRingSpawns");
        assertSame(canonical.get(1), children.getFirst());
        assertEquals(suppliedChild, children.getLast());
        h.tick(1);
        assertEquals(expected, h.view());
        assertEquals(2, h.live().size());
        assertEquals(h.spawn().x() + 32, h.live().getLast().getX());
        assertEquals(h.parent().getSlotIndex() + 1, h.live().getLast().getSlotIndex());
    }

    @Test
    void unmanagedDefaultCaptureRestoresCaptureTimeRecipeWithoutAnIdentityTable() {
        ObjectSpawn spawn = harness.spawn();
        List<RingSpawn> supplied = new ArrayList<>(harness.group());
        Sonic1RingInstance parent = new Sonic1RingInstance(spawn, supplied);
        parent.setServices(new StubObjectServices()); // intentional public unmanaged path, no RingManager/table
        RingSpawn replacement = new RingSpawn(99, 123, 900);
        supplied.set(1, replacement);
        var snapshot = parent.captureRewindState();
        assertNotNull(snapshot.compactGenericState());
        supplied.set(1, harness.group().get(1));
        assertRecipeIdentity(parent, supplied);
        parent.update(1, null);
        parent.restoreRewindState(snapshot);
        assertEquals(0, parent.getCollisionFlags(), "restore the same instance to pre-INIT");
        assertEquals(List.of(replacement, harness.group().get(2)), field(parent, "childRingSpawns"));
        supplied.set(2, replacement);
        assertEquals(List.of(replacement, harness.group().get(2)), field(parent, "childRingSpawns"),
                "restored recipe is isolated from later caller mutations");
        parent.update(1, null);
        assertEquals(Sonic1RingInstance.RING_COLLISION_FLAGS, parent.getCollisionFlags());

        Sonic1RingInstance recreated = (Sonic1RingInstance) parent.recreateForRewind(
                new RewindRecreateContext(spawn, snapshot, new StubObjectServices()));
        recreated.setServices(new StubObjectServices());
        recreated.restoreRewindState(snapshot);
        assertEquals(harness.group().getFirst(), ringSpawn(recreated));
        assertEquals(List.of(replacement, harness.group().get(2)), field(recreated, "childRingSpawns"));
        recreated.update(1, null);
        assertEquals(Sonic1RingInstance.RING_COLLISION_FLAGS, recreated.getCollisionFlags());
    }

    private Harness suppliedHarness(List<RingSpawn> recipe, boolean registryPlaced) {
        Harness stock = harness;
        ObjectSpawn spawn = new ObjectSpawn(stock.spawn().x(), stock.spawn().y(), 0x25, 1, 0, false, 0);
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices old = stock.objects().getObjectServices();
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public RingManager ringManager() { return stock.rings(); }
            @Override public Camera camera() { return old.camera(); }
        };
        ObjectRegistry registry = new ObjectRegistry() {
            @Override public Sonic1RingInstance create(ObjectSpawn entry) { return new Sonic1RingInstance(entry, recipe); }
            @Override public void reportCoverage(List<ObjectSpawn> entries) { }
            @Override public String getPrimaryName(int id) { return "Supplied ring recipe"; }
        };
        ObjectManager objects = new ObjectManager(registryPlaced ? List.of(spawn) : List.of(), registry,
                0, null, null, GameServices.graphics(), old.camera(), services);
        holder[0] = objects;
        objects.enableCounterBasedRespawn();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        objects.reset(stock.cameraX());
        if (!registryPlaced) objects.addDynamicObject(registry.create(spawn));
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(stock.rings());
        rewind.register(objects.rewindSnapshottable());
        return new Harness(objects, stock.rings(), rewind, spawn, recipe, stock.cameraX(), stock.player());
    }

    private static void assertSuppliedAllocation(Harness h, int mask, int parentSlot) {
        List<Integer> expectedIds = new ArrayList<>();
        List<Integer> expectedSlots = new ArrayList<>();
        int nextSlot = parentSlot + 1;
        for (int i = 0; i < h.group().size(); i++) {
            if ((mask & (1 << i)) != 0) continue;
            RingSpawn placement = h.group().get(i);
            expectedIds.add(placement.placementId());
            expectedSlots.add(i == 0 ? parentSlot : nextSlot++);
            Sonic1RingInstance ring = h.live().stream().filter(r -> ringSpawn(r) == placement).findFirst().orElseThrow();
            assertEquals(h.spawn().x() + i * 16, ring.getX(), "native geometry follows recipe index");
            assertEquals(h.spawn().y(), ring.getY());
            assertEquals(h.spawn().x(), ring.getOutOfRangeReferenceX());
        }
        assertEquals(expectedIds, h.live().stream().map(r -> ringSpawn(r).placementId()).toList());
        assertEquals(expectedSlots, h.live().stream().map(Sonic1RingInstance::getSlotIndex).toList());
    }

    private void assertAllocation(int collectedMask, int parentSlot) {
        Harness h = harness;
        List<Integer> expectedPlacementIds = new ArrayList<>();
        List<Integer> expectedSlots = new ArrayList<>();
        int childSlot = parentSlot + 1;
        for (int i = 0; i < 3; i++) {
            if ((collectedMask & (1 << i)) != 0) continue;
            expectedPlacementIds.add(h.group().get(i).placementId());
            expectedSlots.add(i == 0 ? parentSlot : childSlot++);
        }
        assertEquals(expectedPlacementIds, h.live().stream().map(r -> ringSpawn(r).placementId()).toList());
        assertEquals(expectedSlots, h.live().stream().map(Sonic1RingInstance::getSlotIndex).toList(),
                "collected children must consume no FindFreeObj slot");
        assertEquals(collectedMask == 7, h.objects().isSpawnStateBitSet(h.spawn(), 7),
                "only the fully collected group keeps its respawn block");
        for (Sonic1RingInstance ring : h.live()) {
            assertSame(h.group().stream().filter(r -> r.placementId() == ringSpawn(ring).placementId())
                    .findFirst().orElseThrow(), ringSpawn(ring));
            assertEquals(h.spawn().x(), ring.getOutOfRangeReferenceX());
        }
    }

    private static void assertRecipeIdentity(Sonic1RingInstance parent, List<RingSpawn> group) {
        assertSame(group.getFirst(), ringSpawn(parent));
        List<?> children = (List<?>) field(parent, "childRingSpawns");
        assertEquals(group.size() - 1, children.size());
        for (int i = 0; i < children.size(); i++) assertSame(group.get(i + 1), children.get(i));
    }

    private static RingSpawn ringSpawn(Sonic1RingInstance ring) {
        return (RingSpawn) ((List<?>) field(ring, "ringSpawn")).getFirst();
    }

    private static Object field(Sonic1RingInstance ring, String name) {
        try {
            Field field = Sonic1RingInstance.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(ring);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ObjectManagerSnapshot objectSnapshot(CompositeSnapshot snapshot) {
        return assertInstanceOf(ObjectManagerSnapshot.class, snapshot.get("object-manager"));
    }

    private record RingView(ObjectRefId id, int slot, RingSpawn spawn, int x, int y,
                            int anchorX, int respawnIndex, String state, int collisionFlags) {}

    private record GraphView(List<RingView> rings, List<Long> usedSlots, List<Byte> respawn,
                             List<Long> collected, List<String> sparkles, int dynamicIdCounter) {}

    private record Harness(ObjectManager objects, RingManager rings, RewindRegistry rewind,
                           ObjectSpawn spawn, List<RingSpawn> group, int cameraX, AbstractPlayableSprite player) {
        Sonic1RingInstance parent() {
            return live().stream().filter(ring -> ring.getSpawn() == spawn).findFirst().orElseThrow();
        }

        List<Sonic1RingInstance> live() {
            return objects.getActiveObjects().stream().filter(Sonic1RingInstance.class::isInstance)
                    .map(Sonic1RingInstance.class::cast).filter(r -> !r.isDestroyed())
                    .sorted(Comparator.comparingInt(Sonic1RingInstance::getSlotIndex)).toList();
        }

        ObjectRefId id(Sonic1RingInstance ring) {
            return objects.captureIdentityContext().requireIdentityTable().idFor(ring);
        }

        void tick(int vIntRunCount) {
            objects.update(cameraX, null, List.of(), vIntRunCount);
        }

        void collect(int mask) {
            for (int i = 0; i < group.size(); i++) {
                if ((mask & (1 << i)) != 0 && !rings.isCollected(group.get(i))) {
                    assertTrue(rings.collectPlacedRing(group.get(i), player, objects.getFrameCounter()));
                }
            }
        }

        GraphView view() {
            ObjectManagerSnapshot objectState = objects.rewindSnapshottable().capture();
            var ringState = rings.capture();
            List<Byte> respawn = new ArrayList<>();
            for (byte value : objectState.placement().objState()) respawn.add(value);
            return new GraphView(live().stream().map(ring -> new RingView(id(ring), ring.getSlotIndex(),
                    ringSpawn(ring), ring.getX(), ring.getY(), ring.getOutOfRangeReferenceX(),
                    ring.getRespawnStateIndex(), field(ring, "state").toString(), ring.getCollisionFlags())).toList(),
                    Arrays.stream(objectState.usedSlotsBits()).boxed().toList(), respawn,
                    Arrays.stream(ringState.collectedWords()).boxed().toList(),
                    Arrays.stream(ringState.sparkleTimers()).map(Object::toString).toList(),
                    objectState.dynamicObjectIdCounter());
        }
    }
}
