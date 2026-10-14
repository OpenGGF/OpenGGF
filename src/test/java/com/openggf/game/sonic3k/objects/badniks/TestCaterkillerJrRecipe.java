package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.mods.objects.CaterkillerJrFactory;
import com.openggf.mods.objects.CaterkillerJrRecipe;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Second Strike longer native Caterkiller task: bounded recipes and exact SST rewind graphs. */
class TestCaterkillerJrRecipe {
    private ObjectManager manager;
    private TestablePlayableSprite player;
    private GameStateManager score;
    private Camera camera;
    private ObjectServices services;
    private int viewX;

    @BeforeEach void setup() {
        GraphicsManager.getInstance().initHeadless();
        player = new TestablePlayableSprite("sonic", (short) 700, (short) 300);
        score = new GameStateManager();
        camera = new Camera() {
            @Override public short getX() { return (short) viewX; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 1024; }
            @Override public short getHeight() { return 768; }
        };
        services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public Camera camera() { return camera; }
            @Override public GameStateManager gameState() { return score; }
            @Override public ObjectRenderManager renderManager() {
                return new ObjectRenderManager(new Sonic3kObjectArtProvider());
            }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> player, List::of);
            }
        };
        manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 768, 0);
    }

    @AfterEach void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test void stockDefaultRetainsLiteralNativeRolesDelaysAndInitOnlyDispatches() {
        var head = manager.createDynamicObject(() -> new CaterkillerJrHeadInstance(stockSpawn(1)));
        assertEquals(0, head.getCollisionFlags());
        head.update(0, player);
        assertEquals(0, bodies().size(), "wait-offscreen release consumes its own dispatch");
        assertEquals(0x180, head.getX());
        head.update(1, player);
        assertEquals(0x180, head.getX(), "routine zero initializes without movement");
        assertEquals(0x17, head.getCollisionFlags());
        assertSegments(head, new int[]{11, 23, 35, 47, 55, 63},
                new int[]{1, 1, 1, 2, 3, 3}, new boolean[]{true, true, true, false, false, false});
        head.update(2, player);
        assertEquals(0x17f, head.getX());
    }

    @Test void doubleLengthUsesExplicitMiddleThinAndTailRolesWithTheirOwnDelays() {
        var head = longHead(2);
        activate(head);
        assertSegments(head, new int[]{11, 23, 35, 47, 59, 71, 83, 95, 103, 111, 119, 127},
                new int[]{1, 1, 1, 1, 1, 1, 2, 2, 3, 3, 3, 3},
                new boolean[]{true, true, true, false, false, false, false, false, false, false, false, false});
    }

    @Test void supportedRecipeRejectsNullAndExposesOnlyTwoBoundedPresets() throws Exception {
        assertThrows(NullPointerException.class, () -> CaterkillerJrFactory.create(stockSpawn(1), null));
        assertThrows(NullPointerException.class, () -> CaterkillerJrFactory.create(null, CaterkillerJrRecipe.DOUBLE_LENGTH));
        assertEquals(List.of("STOCK", "DOUBLE_LENGTH"),
                java.util.Arrays.stream(CaterkillerJrRecipe.values()).map(Enum::name).toList());
        assertEquals(AbstractBadnikInstance.class, CaterkillerJrFactory.class
                .getMethod("create", ObjectSpawn.class, CaterkillerJrRecipe.class).getReturnType(),
                "native implementation classes must not enter the supported signature");
    }

    @Test void pendingRecipeSurvivesRecreationBeforeItsFirstAllocation() {
        var head = longHead(3);
        var id = id(head);
        var captured = manager.rewindSnapshottable().capture();
        activate(head);
        manager.removeDynamicObject(head);
        manager.rewindSnapshottable().restore(captured);
        var restored = byId(id, CaterkillerJrHeadInstance.class);
        assertNotSame(head, restored);
        activate(restored);
        assertEquals(12, segments(restored).size(), "recreated uninitialized owner must keep its recipe");
    }

    @Test void adjacentGraphsRestoreExactIdentitiesThenReplayAndRetireIndependently() {
        var a = longHead(4);
        var b = manager.createDynamicObject(() -> new CaterkillerJrHeadInstance(stockSpawn(5)));
        activate(a); activate(b);
        var aId = id(a); var bId = id(b);
        var aIds = segments(a).stream().map(this::id).toList();
        var bIds = segments(b).stream().map(this::id).toList();
        // Same source coordinates deliberately make nearest-head reconstruction ambiguous.
        tickGraphs(80);
        var captured = manager.rewindSnapshottable().capture();
        tickGraphs(160);
        var expected = manager.rewindSnapshottable().capture();
        a.onUnload(); b.onUnload();
        manager.removeDynamicObject(a); manager.removeDynamicObject(b);
        longHead(6);
        manager.rewindSnapshottable().restore(captured);
        var restoredA = byId(aId, CaterkillerJrHeadInstance.class);
        var restoredB = byId(bId, CaterkillerJrHeadInstance.class);
        assertEquals(aIds, segments(restoredA).stream().map(this::id).toList());
        assertEquals(bIds, segments(restoredB).stream().map(this::id).toList());
        for (var body : segments(restoredA)) assertSame(restoredA, field(body, "ownerRef"));
        for (var body : segments(restoredB)) assertSame(restoredB, field(body, "ownerRef"));
        assertEquals(captured.dynamicObjects(), manager.rewindSnapshottable().capture().dynamicObjects());
        tickGraphs(160);
        assertEquals(expected.dynamicObjects(), manager.rewindSnapshottable().capture().dynamicObjects(),
                "swing, activation and projectile animation must replay exactly");
        restoredA.onUnload();
        assertTrue(segments(restoredA).stream().allMatch(ObjectInstance::isDestroyed));
        assertTrue(segments(restoredB).stream().noneMatch(ObjectInstance::isDestroyed));
        manager.rewindSnapshottable().restore(captured);
        assertEquals(18, bodies().size(), "rewind must recover one captured graph, without duplicates");
        restoredA = byId(aId, CaterkillerJrHeadInstance.class);
        restoredA.onUnload();
        assertEquals(6, bodies().size(), "teardown after a second restore reaches only this owner's bodies");
    }

    @Test void headAttackRetiresAllTwelveBodiesAndRewindDoesNotRepeatDestruction() {
        var head = longHead(7);
        activate(head);
        var headId = id(head);
        var captured = manager.rewindSnapshottable().capture();
        head.onPlayerAttack(player, null);
        assertTrue(head.isDestroyed());
        assertEquals(0, bodies().size());
        int firstScore = score.getScore();
        int firstEffects = manager.getActiveObjects().size();
        head.onPlayerAttack(player, null);
        assertEquals(firstScore, score.getScore());
        assertEquals(firstEffects, manager.getActiveObjects().size(), "one final hit allocates effects once");
        manager.rewindSnapshottable().restore(captured);
        var restored = byId(headId, CaterkillerJrHeadInstance.class);
        assertEquals(12, bodies().size());
        assertFalse(restored.isDestroyed());
        restored.onUnload();
        assertEquals(firstScore, score.getScore(), "unload is not a combat hit");
        assertEquals(0, bodies().size());
    }

    @Test void partialAndFullSlotExhaustionRemainOneAttemptAndRestoreOnlySuccessfulChildren() {
        for (int available : new int[]{0, 5}) {
            var head = longHead(10 + available);
            var fillers = new ArrayList<AbstractObjectInstance>();
            AbstractObjectInstance filler;
            while ((filler = manager.createDynamicObject(() -> new Filler(stockSpawn(100)))) != null) fillers.add(filler);
            for (int i = 0; i < available; i++) manager.removeDynamicObject(fillers.removeLast());
            activate(head);
            assertEquals(available, segments(head).size());
            assertEquals(available, bodies().size());
            var headId = id(head);
            var childIds = segments(head).stream().map(this::id).toList();
            var captured = manager.rewindSnapshottable().capture();
            manager.removeDynamicObject(fillers.removeLast());
            head.update(2, player);
            assertEquals(available, bodies().size(), "later free slots must not retry failed children");
            manager.rewindSnapshottable().restore(captured);
            var restored = byId(headId, CaterkillerJrHeadInstance.class);
            assertEquals(childIds, segments(restored).stream().map(this::id).toList());
            restored.update(3, player);
            assertEquals(available, bodies().size());
            restored.onUnload();
            assertEquals(0, bodies().size());
            // Clear this iteration through the real manager, including all occupied filler slots.
            for (var object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
        }
    }

    @Test void configuredHeadKeepsStockSwingTimingForAnEntireDirectionChange() {
        var stock = manager.createDynamicObject(() -> new CaterkillerJrHeadInstance(stockSpawn(20)));
        var longer = longHead(21);
        activate(stock); activate(longer);
        boolean reversed = false;
        for (int frame = 2; frame < 400; frame++) {
            stock.update(frame, player); longer.update(frame, player);
            assertEquals(stock.getX(), longer.getX());
            assertEquals(stock.getY(), longer.getY());
            assertEquals(field(stock, "phase"), field(longer, "phase"));
            reversed |= ((Integer) field(stock, "xVelocity")) > 0;
        }
        assertTrue(reversed, "coverage must cross the fast-swing direction change");
    }

    @Test void captureImmediatelyAfterRemovingHeadHasNoDanglingBodyOwnerIdentity() {
        var head = longHead(30);
        activate(head);
        var children = List.copyOf(segments(head));
        manager.removeDynamicObject(head);
        assertTrue(children.stream().allMatch(ObjectInstance::isDestroyed));
        for (var child : children) assertNull(field(child, "ownerRef"));
        var captured = assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
        manager.rewindSnapshottable().restore(captured);
        assertEquals(0, bodies().size());
        assertEquals(0, manager.getActiveObjects().stream().filter(CaterkillerJrHeadInstance.class::isInstance).count());
    }

    @Test void removedBodyIsNotReboundOrReallocatedByRestoredOwner() {
        var head = longHead(31);
        activate(head);
        var removed = segments(head).get(4);
        var removedId = id(removed);
        var headId = id(head);
        manager.removeDynamicObject(removed);
        assertNull(field(removed, "ownerRef"));
        assertEquals(11, segments(head).size());
        var captured = manager.rewindSnapshottable().capture();
        manager.rewindSnapshottable().restore(captured);
        var restored = byId(headId, CaterkillerJrHeadInstance.class);
        assertEquals(11, segments(restored).size());
        assertFalse(segments(restored).stream().map(this::id).anyMatch(removedId::equals));
        restored.update(3, player);
        assertEquals(11, bodies().size());
        restored.onUnload();
        assertEquals(0, bodies().size());
    }

    @Test void unloadAndFreshSpawnUseNewSlotIdentitiesWithoutOldChildren() {
        var old = longHead(32);
        activate(old);
        var oldChildren = List.copyOf(segments(old));
        var oldIds = oldChildren.stream().map(this::id).toList();
        int oldSlot = old.getSlotIndex();
        manager.removeDynamicObject(old);
        // The next execution boundary removes the head's already-destroyed bodies.
        manager.update(0, player, List.of(), 3, false);
        assertEquals(0, bodies().size());
        var fresh = longHead(32);
        assertEquals(oldSlot, fresh.getSlotIndex());
        activate(fresh);
        assertEquals(12, bodies().size());
        assertTrue(segments(fresh).stream().map(this::id).noneMatch(oldIds::contains));
        assertTrue(oldChildren.stream().allMatch(ObjectInstance::isDestroyed));
        var captured = manager.rewindSnapshottable().capture();
        manager.rewindSnapshottable().restore(captured);
        assertEquals(12, bodies().size());
    }

    @Test void actualLayoutUnloadAndRespawnRecreateTwelveChildrenWithFreshIdentities() {
        var placed = new ObjectSpawn(0x180, 0x130, 0, 0, 0, false, 0x130, 35,
                "second-strike", "second-strike:caterkiller");
        var registry = new Sonic3kObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) {
                return CaterkillerJrFactory.create(spawn, CaterkillerJrRecipe.DOUBLE_LENGTH);
            }
        };
        manager = new ObjectManager(List.of(placed), registry, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        manager.enableExecThenLoadPlacement();
        manager.enablePermanentDestroyLatch();
        manager.reset(0);
        var old = (CaterkillerJrHeadInstance) manager.getActiveObjectForRewind(placed);
        assertNotNull(old);
        activate(old);
        var oldChildren = List.copyOf(segments(old));
        var oldHeadId = id(old);
        var oldIds = oldChildren.stream().map(this::id).toList();
        var captured = manager.rewindSnapshottable().capture();
        // Use production Load_Sprites ordering and actual cursor crossings.
        for (int x = 0x80; x <= 0x1000; x += 0x80) {
            viewX = x;
            AbstractObjectInstance.updateCameraBounds(viewX, 0, viewX + 1024, 768, 0);
            manager.update(viewX, player, List.of(), 3 + x / 0x80, false);
        }
        assertNull(manager.getActiveObjectForRewind(placed));
        assertEquals(0, bodies().size());
        assertTrue(oldChildren.stream().allMatch(ObjectInstance::isDestroyed));
        assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
        for (int x = 0xF80; x >= 0; x -= 0x80) {
            viewX = x;
            AbstractObjectInstance.updateCameraBounds(viewX, 0, viewX + 1024, 768, 0);
            manager.update(viewX, player, List.of(), 40 + (0x1000 - x) / 0x80, false);
        }
        var fresh = (CaterkillerJrHeadInstance) manager.getActiveObjectForRewind(placed);
        assertNotNull(fresh); assertNotSame(old, fresh);
        fresh.update(5, player);
        assertEquals(12, bodies().size());
        assertTrue(segments(fresh).stream().map(this::id).noneMatch(oldIds::contains));
        manager.rewindSnapshottable().restore(captured);
        var restored = byId(oldHeadId, CaterkillerJrHeadInstance.class);
        assertEquals(oldIds, segments(restored).stream().map(this::id).toList());
        assertEquals(12, bodies().size());
        restored.onUnload();
        assertEquals(0, bodies().size());
    }

    @Test void poweredAttackKeepsStockRemovalBoundaryAndDoesNotRetireAdjacentGraph() {
        var a = longHead(33); var b = longHead(34);
        activate(a); activate(b);
        var bId = id(b);
        var captured = manager.rewindSnapshottable().capture();
        a.onPoweredScreenAttack(player);
        assertTrue(a.isDestroyed());
        // Stock's powered path retires the head before onUnload tears down bodies.
        manager.update(0, player, List.of(), 3, false);
        assertEquals(12, bodies().size());
        assertTrue(segments(b).stream().noneMatch(ObjectInstance::isDestroyed));
        assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
        manager.rewindSnapshottable().restore(captured);
        assertEquals(24, bodies().size());
        assertEquals(12, segments(byId(bId, CaterkillerJrHeadInstance.class)).size());
    }

    private void assertSegments(CaterkillerJrHeadInstance head, int[] delays, int[] frames, boolean[] fires) {
        var children = segments(head);
        assertEquals(delays.length, children.size());
        for (int i = 0; i < children.size(); i++) {
            var body = children.get(i);
            assertTrue(body.getSlotIndex() > head.getSlotIndex(), "native after-current allocation");
            assertEquals(frames[i], field(body, "mappingFrame"));
            assertEquals(fires[i], field(body, "canFire"));
            assertEquals(frames[i] == 3 ? Sonic3kObjectArtKeys.MONKEY_DUDE : Sonic3kObjectArtKeys.CATERKILLER_JR,
                    field(body, "rendererKey"));
            assertEquals(frames[i] == 3 ? 0x98 : 0x97, body.getCollisionFlags());
            assertEquals(5, body.getPriorityBucket());
            for (int tick = 0; tick <= delays[i]; tick++) {
                body.update(tick, player);
                assertEquals(0x180, body.getX());
                assertEquals("WAITING", field(body, "state").toString());
            }
            body.update(delays[i] + 1, player);
            assertEquals("ACTIVE", field(body, "state").toString());
            assertEquals(0x180, body.getX(), "activation dispatch does not move");
            if (fires[i]) assertEquals(new int[]{56, 48, 40}[i], field(body, "fireTimer"));
            body.update(delays[i] + 2, player);
            assertEquals(0x17f, body.getX(), "first motion uses native -$100 x velocity");
        }
    }

    private CaterkillerJrHeadInstance longHead(int layout) {
        return manager.createDynamicObject(() -> configuredHead(layout));
    }

    private CaterkillerJrHeadInstance configuredHead(int layout) {
        var spawn = new ObjectSpawn(0x180, 0x130, 0, 0, 0, false, 0x130, layout,
                "second-strike", "second-strike:caterkiller");
        return (CaterkillerJrHeadInstance) CaterkillerJrFactory.create(spawn, CaterkillerJrRecipe.DOUBLE_LENGTH);
    }

    private static ObjectSpawn stockSpawn(int layout) {
        return new ObjectSpawn(0x180, 0x130, Sonic3kObjectIds.CATERKILLER_JR, 0, 0, false, layout);
    }
    private void activate(CaterkillerJrHeadInstance head) { head.update(0, player); head.update(1, player); }
    private void tickGraphs(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            for (var object : List.copyOf(manager.getActiveObjects())) if (!object.isDestroyed()) object.update(frame, player);
        }
    }
    @SuppressWarnings("unchecked") private static List<CaterkillerJrBodyInstance> segments(CaterkillerJrHeadInstance head) {
        return (List<CaterkillerJrBodyInstance>) field(head, "bodySegments");
    }
    private List<CaterkillerJrBodyInstance> bodies() {
        return manager.getActiveObjects().stream().filter(CaterkillerJrBodyInstance.class::isInstance)
                .filter(object -> !object.isDestroyed()).map(CaterkillerJrBodyInstance.class::cast)
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }
    private ObjectRefId id(ObjectInstance object) {
        return manager.captureIdentityContext().requireIdentityTable().idFor(object);
    }
    private <T> T byId(ObjectRefId id, Class<T> type) {
        return manager.getActiveObjects().stream().filter(type::isInstance).filter(object -> id.equals(id(object)))
                .map(type::cast).findFirst().orElseThrow();
    }
    private static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        throw new AssertionError("No field " + name);
    }
    public static final class Filler extends AbstractObjectInstance implements SpawnRewindRecreatable {
        public Filler(ObjectSpawn spawn) { super(spawn, "filler"); }
        @Override public void update(int frame, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }
}
