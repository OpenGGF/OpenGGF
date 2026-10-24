package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.Sonic1Level;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.objects.Sonic1RingInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Stock placed-parent controls for the childRingSpawns disposition review. */
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
        return (RingSpawn) field(ring, "ringSpawn");
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
