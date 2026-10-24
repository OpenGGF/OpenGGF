package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindFieldPolicy;
import com.openggf.game.rewind.schema.RewindPolicyRegistry;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic2.objects.badniks.RexonBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.RexonHeadObjectInstance;
import com.openggf.game.sonic2.objects.badniks.SolBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.SolFireballObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.OwnedMocks;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestS2RexonSolOwnershipRewind {
    private final OwnedMocks mocks = new OwnedMocks();
    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        mocks.close();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void progressedRexonFamiliesRestoreExactOwnersSparseChainsAndDeathDropScope() throws Exception {
        ObjectManager manager = manager();
        RexonBadnikInstance first = rexon(manager, 160);
        RexonBadnikInstance second = rexon(manager, 144);
        invoke(first, "createHeads");
        invoke(second, "createHeads");
        List<RexonHeadObjectInstance> firstHeads = inventory(first, "heads");
        List<RexonHeadObjectInstance> secondHeads = inventory(second, "heads");
        tickHeads(manager, 0, 44);
        assertTrue(firstHeads.stream().anyMatch(head -> nearer(head, second, first)),
                "native raised neck coordinates must defeat nearest-parent reconstruction");
        RexonHeadObjectInstance retired = firstHeads.get(1);
        manager.removeDynamicObject(retired);
        ObjectRefId firstId = id(manager, first);
        ObjectRefId secondId = id(manager, second);
        List<ObjectRefId> firstIds = ids(manager, firstHeads.stream().filter(h -> h != retired).toList());
        List<ObjectRefId> secondIds = ids(manager, secondHeads);
        Map<ObjectRefId, ObjectRefId> owners = ownerIds(manager, RexonHeadObjectInstance.class);
        RewindRegistry registry = registry(manager);
        CompositeSnapshot snapshot = registry.capture();
        List<Map<ObjectRefId, List<Object>>> expected = new ArrayList<>();
        for (int frame = 44; frame < 56; frame++) {
            tickHeads(manager, frame, 1);
            expected.add(headState(manager));
        }
        destroyGraph(manager);
        registry.restore(snapshot);
        RexonBadnikInstance restoredFirst = resolve(manager, firstId, RexonBadnikInstance.class);
        RexonBadnikInstance restoredSecond = resolve(manager, secondId, RexonBadnikInstance.class);
        assertNotSame(first, restoredFirst);
        assertNotSame(second, restoredSecond);
        assertEquals(owners, ownerIds(manager, RexonHeadObjectInstance.class));
        assertEquals(firstIds, ids(manager, inventory(restoredFirst, "heads")));
        assertEquals(secondIds, ids(manager, inventory(restoredSecond, "heads")));
        List<RexonHeadObjectInstance> restoredHeads = inventory(restoredFirst, "heads");
        assertNull(field(restoredHeads.getFirst(), "linkedHead"), "a retired chain slot must not be compacted");
        assertSame(restoredHeads.get(2), field(restoredHeads.get(1), "linkedHead"));
        for (int frame = 44; frame < 56; frame++) {
            tickHeads(manager, frame, 1);
            assertEquals(expected.get(frame - 44), headState(manager), "forward Rexon frame " + frame);
        }
        restoredHeads.getLast().onPlayerAttack(null, null);
        for (RexonHeadObjectInstance head : restoredHeads.subList(0, restoredHeads.size() - 1)) {
            assertEquals("DEATH_DROP", field(head, "state").toString());
        }
        for (RexonHeadObjectInstance head : TestS2RexonSolOwnershipRewind.<RexonHeadObjectInstance>inventory(restoredSecond, "heads")) {
            assertEquals("NORMAL", field(head, "state").toString(), "another family must not receive death drop");
        }
    }

    @Test
    void solFamiliesRestoreMixedFlyingInventoriesCountersAndUnloadDetachment() throws Exception {
        ObjectManager manager = manager();
        SolBadnikInstance first = sol(manager, 160);
        SolBadnikInstance second = sol(manager, 144);
        invoke(first, "ensureFireballsSpawned");
        invoke(second, "ensureFireballsSpawned");
        List<SolFireballObjectInstance> firstBalls = inventory(first, "fireballs");
        SolFireballObjectInstance flying = flyTowardOtherFamily(manager, first, second);
        assertTrue(nearer(flying, second, first), "native left orbit position must be nearer the other Sol");
        assertSame(first, field(flying, "parent"), "launch keeps its parent until unload");
        manager.removeDynamicObject(firstBalls.get(1));
        ObjectRefId firstId = id(manager, first);
        ObjectRefId secondId = id(manager, second);
        ObjectRefId flyingId = id(manager, flying);
        List<ObjectRefId> firstIds = ids(manager, firstBalls.stream().filter(b -> b != firstBalls.get(1)).toList());
        List<ObjectRefId> secondIds = ids(manager, inventory(second, "fireballs"));
        Map<ObjectRefId, ObjectRefId> owners = ownerIds(manager, SolFireballObjectInstance.class);
        RewindRegistry registry = registry(manager);
        CompositeSnapshot snapshot = registry.capture();
        List<Map<ObjectRefId, List<Object>>> expected = solReplay(manager, 1, 8);
        destroyGraph(manager);
        registry.restore(snapshot);
        SolBadnikInstance restoredFirst = resolve(manager, firstId, SolBadnikInstance.class);
        SolBadnikInstance restoredSecond = resolve(manager, secondId, SolBadnikInstance.class);
        assertNotSame(first, restoredFirst);
        assertEquals(owners, ownerIds(manager, SolFireballObjectInstance.class));
        assertEquals(firstIds, ids(manager, inventory(restoredFirst, "fireballs")));
        assertEquals(secondIds, ids(manager, inventory(restoredSecond, "fireballs")));
        assertEquals(3, field(restoredFirst, "fireballsRemaining"));
        assertEquals(4, field(restoredSecond, "fireballsRemaining"));
        assertEquals(expected, solReplay(manager, 1, 8));
        List<SolFireballObjectInstance> restoredBalls = inventory(restoredFirst, "fireballs");
        for (int tick = 0; tick < 256 && (int) field(restoredFirst, "fireballsRemaining") == 3; tick++) {
            solReplay(manager, tick, 1);
        }
        assertEquals(2, field(restoredFirst, "fireballsRemaining"));
        assertEquals(4, field(restoredSecond, "fireballsRemaining"));
        assertSame(restoredFirst, field(resolve(manager, flyingId, SolFireballObjectInstance.class), "parent"));
        manager.removeDynamicObject(restoredFirst);
        for (SolFireballObjectInstance ball : restoredBalls) {
            assertNull(field(ball, "parent"));
        }
        for (SolFireballObjectInstance ball : TestS2RexonSolOwnershipRewind.<SolFireballObjectInstance>inventory(restoredSecond, "fireballs")) {
            assertSame(restoredSecond, field(ball, "parent"));
        }
    }

    @Test
    void retiredSolParentDoesNotAttachFlyingChildToAnotherLiveFamily() throws Exception {
        ObjectManager manager = manager();
        SolBadnikInstance retired = sol(manager, 160);
        SolBadnikInstance live = sol(manager, 144);
        invoke(retired, "ensureFireballsSpawned");
        invoke(live, "ensureFireballsSpawned");
        List<SolFireballObjectInstance> balls = inventory(retired, "fireballs");
        SolFireballObjectInstance flying = flyTowardOtherFamily(manager, retired, live);
        manager.removeDynamicObject(retired);
        for (SolFireballObjectInstance ball : balls) {
            if (ball != flying) manager.removeDynamicObject(ball);
        }
        assertNull(field(flying, "parent"));
        ObjectRefId flyingId = id(manager, flying);
        ObjectRefId liveId = id(manager, live);
        RewindRegistry registry = registry(manager);
        CompositeSnapshot snapshot = registry.capture();
        flying.update(1, null);
        int nextX = flying.getX();
        destroyGraph(manager);
        registry.restore(snapshot);
        SolFireballObjectInstance restored = resolve(manager, flyingId, SolFireballObjectInstance.class);
        assertNull(field(restored, "parent"));
        SolBadnikInstance restoredLive = resolve(manager, liveId, SolBadnikInstance.class);
        assertEquals(4, inventory(restoredLive, "fireballs").size());
        restored.update(1, null);
        assertEquals(nextX, restored.getX());
        assertFalse(restored.isDestroyed());
    }

    @Test
    void retiredRexonParentAndNextHeadRemainOptional() throws Exception {
        ObjectManager manager = manager();
        RexonBadnikInstance retired = rexon(manager, 160);
        RexonBadnikInstance live = rexon(manager, 144);
        invoke(retired, "createHeads");
        invoke(live, "createHeads");
        tickHeads(manager, 0, 44);
        List<RexonHeadObjectInstance> heads = inventory(retired, "heads");
        RexonHeadObjectInstance survivor = heads.getFirst();
        manager.removeDynamicObject(retired);
        for (RexonHeadObjectInstance head : heads) {
            if (head != survivor) manager.removeDynamicObject(head);
        }
        ObjectRefId survivorId = id(manager, survivor);
        ObjectRefId liveId = id(manager, live);
        RewindRegistry registry = registry(manager);
        CompositeSnapshot snapshot = registry.capture();
        tickHeads(manager, 44, 1);
        Map<ObjectRefId, List<Object>> expected = headState(manager);
        destroyGraph(manager);
        registry.restore(snapshot);
        RexonHeadObjectInstance restored = resolve(manager, survivorId, RexonHeadObjectInstance.class);
        assertNull(field(restored, "parent"));
        assertNull(field(restored, "linkedHead"));
        assertEquals(5, inventory(resolve(manager, liveId, RexonBadnikInstance.class), "heads").size());
        tickHeads(manager, 44, 1);
        assertEquals(expected, headState(manager));
    }

    @Test
    void rebuiltInventoriesHaveExactPoliciesAndStayOutsideInPlaceReuse() throws Exception {
        assertEquals(RewindFieldPolicy.TRANSIENT, RewindPolicyRegistry.policyForAudit(
                RexonBadnikInstance.class.getDeclaredField("heads")).orElse(null));
        assertEquals(RewindFieldPolicy.TRANSIENT, RewindPolicyRegistry.policyForAudit(
                SolBadnikInstance.class.getDeclaredField("fireballs")).orElse(null));
        for (Class<?> type : List.of(RexonBadnikInstance.class, RexonHeadObjectInstance.class,
                SolBadnikInstance.class, SolFireballObjectInstance.class)) {
            assertFalse(ObjectManager.isRewindInPlaceReuseSafeClass(type), type.getSimpleName());
        }
    }

    private ObjectManager manager() {
        ObjectRenderManager renderManager = mocks.mock(ObjectRenderManager.class);
        Camera camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public ObjectRenderManager renderManager() { return renderManager; }
            @Override public Camera camera() { return camera; }
        };
        holder[0] = new ObjectManager(List.of(), new Sonic2ObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0].reset(0);
        return holder[0];
    }

    private static RexonBadnikInstance rexon(ObjectManager manager, int x) throws Exception {
        RexonBadnikInstance parent = manager.createDynamicObject(() -> new RexonBadnikInstance(spawn(x, 160, Sonic2ObjectIds.REXON)));
        return parent;
    }

    private static SolBadnikInstance sol(ObjectManager manager, int x) throws Exception {
        SolBadnikInstance parent = manager.createDynamicObject(() -> new SolBadnikInstance(spawn(x, 80, Sonic2ObjectIds.SOL)));
        return parent;
    }

    private static ObjectSpawn spawn(int x, int y, int objectId) {
        return new ObjectSpawn(x, y, objectId, 0, 0, false, 0);
    }

    private static RewindRegistry registry(ObjectManager manager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        return registry;
    }

    private static void destroyGraph(ObjectManager manager) {
        for (ObjectInstance object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
        assertTrue(manager.getActiveObjects().isEmpty());
    }

    private static boolean nearer(ObjectInstance child, ObjectInstance other, ObjectInstance owner) {
        return distance(child, other) < distance(child, owner);
    }

    private static long distance(ObjectInstance a, ObjectInstance b) {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }

    private static void tickHeads(ObjectManager manager, int firstFrame, int count) {
        for (int frame = firstFrame; frame < firstFrame + count; frame++) {
            for (ObjectInstance object : List.copyOf(manager.getActiveObjects())) {
                if (object instanceof RexonHeadObjectInstance head) head.update(frame, null);
            }
        }
    }

    private static SolFireballObjectInstance flyTowardOtherFamily(
            ObjectManager manager, SolBadnikInstance owner, SolBadnikInstance other) throws Exception {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) owner.getX(), (short) owner.getY());
        player.setCentreX((short) owner.getX());
        player.setCentreY((short) owner.getY());
        for (int frame = 0; frame < 256; frame++) {
            owner.update(frame, player);
            other.update(frame, null);
            for (ObjectInstance object : manager.getActiveObjects()) {
                if (object instanceof SolFireballObjectInstance ball) ball.update(frame, null);
            }
            for (SolFireballObjectInstance ball : TestS2RexonSolOwnershipRewind.<SolFireballObjectInstance>inventory(owner, "fireballs")) {
                if (field(ball, "state").toString().equals("FLYING") && nearer(ball, other, owner)) {
                    assertEquals(3, field(owner, "fireballsRemaining"));
                    return ball;
                }
            }
        }
        throw new AssertionError("native Sol animation, orbit and flight must reach the adjacent family");
    }

    private static List<Map<ObjectRefId, List<Object>>> solReplay(ObjectManager manager, int firstFrame, int count) throws Exception {
        List<Map<ObjectRefId, List<Object>>> states = new ArrayList<>();
        for (int frame = firstFrame; frame < firstFrame + count; frame++) {
            for (ObjectInstance object : List.copyOf(manager.getActiveObjects())) {
                if (object instanceof SolBadnikInstance sol) sol.update(frame, null);
                if (object instanceof SolFireballObjectInstance ball) ball.update(frame, null);
            }
            Map<ObjectRefId, List<Object>> state = new LinkedHashMap<>();
            for (ObjectInstance object : manager.getActiveObjects()) {
                if (object instanceof SolBadnikInstance sol) {
                    state.put(id(manager, sol), List.of(sol.getX(), sol.getY(),
                            field(sol, "fireballsRemaining"), field(sol, "state"), field(sol, "animFrame")));
                }
                if (object instanceof SolFireballObjectInstance ball) {
                    state.put(id(manager, ball), List.of(ball.getX(), ball.getY(), ball.isDestroyed(),
                            field(ball, "state"), field(ball, "angle"), field(ball, "xVelocity")));
                }
            }
            states.add(state);
        }
        return states;
    }

    private static Map<ObjectRefId, List<Object>> headState(ObjectManager manager) throws Exception {
        Map<ObjectRefId, List<Object>> state = new LinkedHashMap<>();
        for (ObjectInstance object : manager.getActiveObjects()) {
            if (object instanceof RexonHeadObjectInstance head) {
                state.put(id(manager, head), List.of(head.getX(), head.getY(), head.isDestroyed(),
                        field(head, "state"), field(head, "headIndex"), field(head, "oscillationPhase"),
                        field(head, "oscillationFrameCounter"), field(head, "projectileTimer")));
            }
        }
        return state;
    }

    private static Map<ObjectRefId, ObjectRefId> ownerIds(ObjectManager manager, Class<?> type) throws Exception {
        Map<ObjectRefId, ObjectRefId> owners = new LinkedHashMap<>();
        for (ObjectInstance object : manager.getActiveObjects()) {
            if (type.isInstance(object)) owners.put(id(manager, object), id(manager, (ObjectInstance) field(object, "parent")));
        }
        return owners;
    }

    private static ObjectRefId id(ObjectManager manager, ObjectInstance object) {
        return manager.captureIdentityContext().requireIdentityTable().idFor(object);
    }

    private static List<ObjectRefId> ids(ObjectManager manager, List<? extends ObjectInstance> objects) {
        return objects.stream().map(object -> id(manager, object)).toList();
    }

    private static <T> T resolve(ObjectManager manager, ObjectRefId id, Class<T> type) {
        return type.cast(manager.captureIdentityContext().requireIdentityTable().resolve(id));
    }

    @SuppressWarnings("unchecked")
    private static <T extends ObjectInstance> List<T> inventory(Object parent, String name) throws Exception {
        return (List<T>) field(parent, name);
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
