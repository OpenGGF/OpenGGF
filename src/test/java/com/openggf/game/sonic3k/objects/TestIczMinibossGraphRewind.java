package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseProvider.TouchRegion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Destructive registry restoration of the inline helpers and native defeat workers. */
@RequiresRom(SonicGame.SONIC_3K)
class TestIczMinibossGraphRewind {
    private static final ObjectSpawn SPAWN =
            new ObjectSpawn(0x06F0, 0x0280, Sonic3kObjectIds.ICZ_MINIBOSS, 0, 0, false, 70);

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 124, 570, 850})
    void progressedHelpersRestoreFreshAndReplayTheEncounter(int captureTick) throws Exception {
        Harness h = new Harness();
        for (int i = 0; i < captureTick; i++) h.tick();
        IczMinibossInstance original = h.boss();
        assertTrue(original.getChildComponents().isEmpty(), "inline helpers are not managed boss components");
        for (int i = 0; i < 8; i++) {
            assertEquals(captureTick == 1, original.getOrbRoutineForTesting(i) < 0,
                    "gate capture has null slots; progressed captures have real orbs");
        }
        if (captureTick == 570) {
            assertEquals(8, original.getOrbRoutineForTesting(0), "capture an active orbit");
            assertEquals(0x8B, original.getOrbCollisionFlagsForTesting(0));
        }
        assertEquals(captureTick == 1 ? 1 : 15, h.observe().draws().size(),
                "positive draw control: body, then all eight orbs and six shards after creation");
        assertEquals(captureTick == 1 ? 1 : 15, h.manager.getAllocatedSlotCount());
        assertRoundTrip(h, 1000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 37, 70})
    void pendingDefeatWorkersRestoreFreshAndReplayHandoff(int defeatTicks) throws Exception {
        Harness h = new Harness();
        for (int i = 0; i < 570; i++) h.tick();
        for (int hit = 0; hit < 6; hit++) {
            h.boss().onPlayerAttack(null, null);
            if (hit < 5) for (int i = 0; i < 33; i++) h.tick();
        }
        for (int i = 0; i < defeatTicks; i++) h.tick();
        IczMinibossExplosionControllerChild controller = h.manager
                .activeObjectsOfType(IczMinibossExplosionControllerChild.class).getFirst();
        assertSame(h.boss(), field(controller, "parent").get(controller));
        assertTrue(h.boss().getChildComponents().isEmpty(), "defeat worker owns a managed SST");
        assertRoundTrip(h, 220 - defeatTicks);
        assertTrue(h.manager.activeObjectsOfType(IczMinibossInstance.class).isEmpty());
        assertTrue(h.manager.activeObjectsOfType(IczMinibossExplosionControllerChild.class).isEmpty());
        assertEquals(0, h.manager.activeObjectsOfType(IczMinibossPostBossPaletteController.class).size(),
                "the independently started palette script has finished by this late tail");
        assertEquals(1, h.manager.activeObjectsOfType(S3kBossDefeatSignpostFlow.class).size());
    }

    private static void assertRoundTrip(Harness h, int replayTicks) throws Exception {
        Map<ObjectRefId, ObjectInstance> originals = h.identities();
        Object[] originalOrbs = ((Object[]) field(h.boss(), "orbs").get(h.boss())).clone();
        Object[] originalShards = ((Object[]) field(h.boss(), "shards").get(h.boss())).clone();
        h.sounds.clear();
        Observation captured = h.observe();
        CompositeSnapshot snapshot = h.registry.capture();
        List<Observation> uninterrupted = new ArrayList<>();
        for (int i = 0; i < replayTicks; i++) {
            h.tick();
            uninterrupted.add(h.observe());
        }
        for (ObjectInstance object : List.copyOf(h.manager.getActiveObjects())) {
            h.manager.removeDynamicObject(object);
        }
        h.manager.createDynamicObject(() -> new IczMinibossInstance(SPAWN));
        h.registry.restore(snapshot);
        h.sounds.clear();
        assertEquals(originals.keySet(), h.identities().keySet(), "exact managed identities and no divergent owner");
        h.identities().forEach((id, object) -> assertNotSame(originals.get(id), object, "fresh recreation for " + id));
        assertFreshHelpers(originalOrbs, (Object[]) field(h.boss(), "orbs").get(h.boss()));
        assertFreshHelpers(originalShards, (Object[]) field(h.boss(), "shards").get(h.boss()));
        assertEquals(captured, h.observe(), "helpers, collisions and emitted draws must restore before a tick");
        for (int i = 0; i < replayTicks; i++) {
            h.tick();
            assertEquals(uninterrupted.get(i), h.observe(), "forward dispatch " + i);
        }
    }

    private record Draw(int bucket, boolean high, int frame, int x, int y, int palette) {}
    private static void assertFreshHelpers(Object[] originals, Object[] restored) {
        assertEquals(originals.length, restored.length);
        for (int i = 0; i < originals.length; i++) {
            if (originals[i] == null) assertNull(restored[i], "absent helper slot " + i);
            else assertNotSame(originals[i], restored[i], "fresh helper slot " + i);
        }
    }

    private record Observation(Map<ObjectRefId, List<Object>> objects, List<Draw> draws,
                               List<TouchRegion> touches, List<Integer> sounds,
                               int allocatedSlots, int peakSlots, int vInt) {}

    private static final class Harness {
        final List<Draw> draws = new ArrayList<>();
        final List<Integer> sounds = new ArrayList<>();
        final GameStateManager gameState = new GameStateManager();
        final Camera camera = new Camera();
        final RewindRegistry registry = new RewindRegistry();
        final ObjectManager manager;
        int drawBucket;
        boolean drawHigh;

        Harness() {
            camera.setX((short) 0x06F0);
            camera.setY((short) 0x02B8);
            PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
            when(renderer.isReady()).thenReturn(true);
            doAnswer(call -> {
                draws.add(new Draw(drawBucket, drawHigh, call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(5)));
                return null;
            }).when(renderer).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
            ObjectRenderManager renderManager = new ObjectRenderManager(null) {
                @Override public PatternSpriteRenderer getRenderer(String key) {
                    return Sonic3kObjectArtKeys.ICZ_MINIBOSS.equals(key) ? renderer : null;
                }
            };
            StubObjectServices services = new StubObjectServices() {
                @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
                    return new com.openggf.level.objects.ObjectPlayerQuery(() -> null, List::of);
                }
                @Override public ObjectManager objectManager() { return manager; }
                @Override public Camera camera() { return camera; }
                @Override public ObjectRenderManager renderManager() { return renderManager; }
                @Override public GameStateManager gameState() { return gameState; }
                @Override public void playSfx(int id) { sounds.add(id); }
            };
            ObjectRegistry objects = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) { return new IczMinibossInstance(spawn); }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) {}
                @Override public String getPrimaryName(int id) { return "ICZMiniboss"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
            };
            manager = new ObjectManager(List.of(), objects, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            manager.reset(camera.getX());
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.createDynamicObject(() -> new IczMinibossInstance(SPAWN));
            registry.register(manager.rewindSnapshottable());
            registry.register(services.rng());
            registry.register(gameState);
            registry.register(camera);
        }

        void tick() {
            sounds.clear();
            manager.update(camera.getX(), null, List.of(), 0, false);
        }

        IczMinibossInstance boss() { return manager.activeObjectsOfType(IczMinibossInstance.class).getFirst(); }

        Map<ObjectRefId, ObjectInstance> identities() {
            var ids = manager.captureIdentityContext().requireIdentityTable();
            Map<ObjectRefId, ObjectInstance> result = new LinkedHashMap<>();
            for (ObjectInstance object : manager.getActiveObjects()) {
                if (!object.isDestroyed()) result.put(ids.idFor(object), object);
            }
            assertFalse(result.containsKey(null));
            return result;
        }

        Observation observe() throws Exception {
            draws.clear();
            Map<ObjectRefId, List<Object>> objects = new LinkedHashMap<>();
            List<TouchRegion> touches = List.of();
            for (var entry : identities().entrySet()) {
                ObjectInstance object = entry.getValue();
                List<Object> state = new ArrayList<>(List.of(object.getClass().getName(),
                        assertInstanceOf(AbstractObjectInstance.class, object).getSlotIndex(),
                        object.getSpawn() == null ? "unpositioned" : List.of(object.getX(), object.getY()),
                        scalarFields(object)));
                if (object instanceof IczMinibossInstance boss) {
                    assertTrue(boss.getChildComponents().isEmpty());
                    state.add(scalarFields(boss.getState()));
                    state.add(helperFields(boss, "shards"));
                    state.add(helperFields(boss, "orbs"));
                    state.add(boss.getCollisionFlags());
                    TouchRegion[] regions = boss.getMultiTouchRegions();
                    touches = regions == null ? List.of() : List.copyOf(Arrays.asList(regions));
                    for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
                        drawBucket = bucket;
                        drawHigh = false;
                        boss.appendRenderCommands(new ArrayList<>(), bucket, false);
                        drawHigh = true;
                        boss.appendRenderCommands(new ArrayList<>(), bucket, true);
                    }
                }
                if (object instanceof IczMinibossExplosionControllerChild controller) {
                    assertSame(boss(), field(controller, "parent").get(controller));
                }
                objects.put(entry.getKey(), List.copyOf(state));
            }
            return new Observation(objects, List.copyOf(draws), touches, List.copyOf(sounds),
                    manager.getAllocatedSlotCount(), manager.getPeakSlotCount(), manager.getVblaCounter());
        }
    }

    private static List<Object> helperFields(IczMinibossInstance boss, String name) throws Exception {
        List<Object> result = new ArrayList<>();
        for (Object helper : (Object[]) field(boss, name).get(boss)) {
            result.add(helper == null ? "absent" : scalarFields(helper));
        }
        return result;
    }

    private static Map<String, Object> scalarFields(Object target) throws IllegalAccessException {
        Map<String, Object> result = new TreeMap<>();
        for (Field field : target.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
            if (field.getType().isPrimitive() || field.getType().isEnum()) {
                field.setAccessible(true);
                result.put(field.getName(), field.get(target));
            }
        }
        return result;
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
