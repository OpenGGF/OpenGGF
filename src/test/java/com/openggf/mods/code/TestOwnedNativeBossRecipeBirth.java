package com.openggf.mods.code;

import com.openggf.Engine;
import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.difficulty.MarbleGardenBossRecipe;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.events.Sonic3kMGZEvents;
import com.openggf.game.sonic3k.objects.MgzDrillingRobotnikInstance;
import com.openggf.game.sonic3k.objects.MgzEndBossInstance;
import com.openggf.game.sonic3k.objects.MgzEndBossKnuxInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Real creator registration, dynamic recipe quarantine and native MGZ arena commitment
 * must preserve the Engine frame-abort boundary. Recovery callbacks are observed here;
 * a complete Engine/title teardown and cold boss routes are separate coverage.
 */
@Isolated
class TestOwnedNativeBossRecipeBirth {
    private static final String OWNER = "birth-recipe-owner";

    @BeforeEach
    void headlessConfiguration() {
        com.openggf.tests.TestEnvironment.resetAll();
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void reset() {
        SessionManager.clear();
        GameServices.configuration().resetToDefaults();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sonic", "tails"})
    void firstDynamicRecipeFaultAbortsTheFrameBeforeRetainedCallbacksCanContinue(String character) throws Exception {
        var world = approach(character);
        var retained = world.module.getGameService(AizMinibossAttackPolicy.class);
        assertNotNull(retained, "a real cached callback graph is present before the fault");
        var recovery = new ArrayList<String>();

        boolean completed = runFrame(() -> {
            advanceArena(world.events);
            retained.followupDelayTicks(3);
        }, () -> recovery.add("discard"), () -> recovery.add("title"));

        assertFalse(completed, "new creator faults must reach Engine recovery, not become optional STOCK");
        assertEquals(List.of("discard", "title"), recovery);
        assertEquals(1, world.providerCalls.get());
        assertEquals(0, world.retainedCallbackCalls.get(), "no retained creator callback later in the failed frame");
        assertFalse(world.boundary.isOwnerAvailable(OWNER));
        assertNull(world.module.getGameService(MarbleGardenBossRecipe.class));
        assertNull(world.module.getGameService(AizMinibossAttackPolicy.class));
        assertTrue(world.objects.getActiveObjects().isEmpty(), "the fault precedes native boss allocation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sonic", "tails"})
    void previouslyWithdrawnOptionalRecipeUsesOneStockBossWithoutRetryingTheOwner(String character) throws Exception {
        var world = approach(character);
        assertThrows(ModFaultBoundary.CallbackAborted.class, () -> world.boundary.run(OWNER, () -> {
            throw new IllegalStateException("owner failed before this fresh native intake");
        }));
        advanceArena(world.events);
        assertEquals(0, world.providerCalls.get(), "fresh lookup skips a known unavailable provider");
        assertTrue(world.events.isBossSpawned());
        assertTrue(world.nativeEvents.isBossActive());
        var objects = List.copyOf(world.objects.getActiveObjects());
        assertEquals(1, objects.size());
        var boss = assertInstanceOf(MgzEndBossInstance.class, objects.getFirst());
        int expectedId = Sonic3kObjectIds.MGZ_END_BOSS;
        assertEquals(expectedId, world.state.getCurrentBossId());
        assertEquals(expectedId, boss.getSpawn().objectId());
        assertEquals(0x3D20, boss.getSpawn().x());
        assertEquals(0x668, boss.getSpawn().y());
        assertTrue(boss.getSlotIndex() >= 0);
        assertFalse(boss.isDestroyed());
        Field preset = MgzDrillingRobotnikInstance.class.getDeclaredField("counterCadence");
        preset.setAccessible(true);
        assertFalse(preset.getBoolean(boss));
        advanceArena(world.events);
        assertEquals(0, world.providerCalls.get());
        assertEquals(1, world.objects.getActiveObjects().size());
        assertSame(boss, world.objects.getActiveObjects().iterator().next());
    }

    @Test
    void firstPlacedKnucklesRecipeFaultAbortsTheFrameWithOnlyItsOriginalDormantRoot() throws Exception {
        var world = approachPlacedKnuckles();
        var boss = assertInstanceOf(MgzEndBossKnuxInstance.class,
                world.objects.getActiveObjectForRewind(KNUCKLES_PLACEMENT));
        var retained = world.module.getGameService(AizMinibossAttackPolicy.class);
        assertNotNull(retained);
        var recovery = new ArrayList<String>();

        boolean completed = runFrame(() -> {
            advancePlacedKnuckles(world, 0);
            retained.followupDelayTicks(3);
        }, () -> recovery.add("discard"), () -> recovery.add("title"));

        assertFalse(completed);
        assertEquals(List.of("discard", "title"), recovery);
        assertEquals(1, world.providerCalls.get());
        assertEquals(0, world.retainedCallbackCalls.get());
        assertFalse(world.boundary.isOwnerAvailable(OWNER));
        assertNull(world.module.getGameService(MarbleGardenBossRecipe.class));
        assertNull(world.module.getGameService(AizMinibossAttackPolicy.class));
        assertEquals(List.of(boss), List.copyOf(world.objects.getActiveObjects()),
                "the placed root predates admission; the failing intake must not create opening children");
        assertFalse(world.nativeEvents.isBossActive());
        assertEquals(0, world.state.getCurrentBossId());
        assertTrue(boss.isHidden());
        assertEquals(0, boss.getCollisionFlags());
        assertNull(boss.getMultiTouchRegions());
    }

    @Test
    void previouslyWithdrawnPlacedKnucklesRecipeStagesTheSameStockRootWithoutRetry() throws Exception {
        var world = approachPlacedKnuckles();
        var boss = assertInstanceOf(MgzEndBossKnuxInstance.class,
                world.objects.getActiveObjectForRewind(KNUCKLES_PLACEMENT));
        assertThrows(ModFaultBoundary.CallbackAborted.class, () -> world.boundary.run(OWNER, () -> {
            throw new IllegalStateException("owner failed before native placed admission");
        }));
        // One native admission update plus raw120 signed-decrement staging updates.
        for (int frame = 0; frame < 122; frame++) advancePlacedKnuckles(world, frame);
        assertEquals(0, world.providerCalls.get());
        assertTrue(world.nativeEvents.isBossActive());
        assertEquals(Sonic3kObjectIds.MGZ_END_BOSS_KNUX, world.state.getCurrentBossId());
        assertEquals(KNUCKLES_PLACEMENT, boss.getSpawn());
        assertSame(boss, world.objects.getActiveObjectForRewind(KNUCKLES_PLACEMENT));
        assertEquals(List.of(boss), List.copyOf(world.objects.getActiveObjects()));
        assertTrue(boss.isHidden(), "camera handoff precedes the first fight initializer");
        assertEquals(0, boss.getCollisionFlags());
        assertNull(boss.getMultiTouchRegions());
        Field preset = MgzDrillingRobotnikInstance.class.getDeclaredField("counterCadence");
        preset.setAccessible(true);
        assertFalse(preset.getBoolean(boss));
        for (int frame = 122; frame < 126; frame++) advancePlacedKnuckles(world, frame);
        assertEquals(0, world.providerCalls.get());
        assertSame(boss, world.objects.getActiveObjectForRewind(KNUCKLES_PLACEMENT));
        assertEquals(1, world.objects.activeObjectsOfType(MgzEndBossKnuxInstance.class).size());
        assertFalse(boss.isHidden());
        assertTrue(world.objects.getActiveObjects().size() > 1,
                "ordinary native initialization creates opening children only after staging");
    }

    private static final ObjectSpawn KNUCKLES_PLACEMENT = new ObjectSpawn(
            0x3E78, 0, Sonic3kObjectIds.MGZ_END_BOSS_KNUX, 0, 0, false, 0, 0x7FE / 6);

    private static World approachPlacedKnuckles() {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var world = new World(List.of(KNUCKLES_PLACEMENT));
        world.camera.setX((short) 0x3D78);
        world.camera.setY((short) 0x28);
        world.objects.reset(0x3D78);
        assertEquals(0, world.providerCalls.get(), "placement materialization is not encounter admission");
        assertEquals(1, world.objects.activeObjectsOfType(MgzEndBossKnuxInstance.class).size());
        return world;
    }

    private static void advancePlacedKnuckles(World world, int frame) {
        world.objects.update(0x3D78, null, List.of(), frame, false);
    }

    private static World approach(String character) {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var world = new World();
        world.camera.setX((short) 0x3A00);
        world.camera.setY((short) 0x600);
        advanceArena(world.events);
        assertEquals(0, world.providerCalls.get(), "the approach gate does not consult a recipe");
        world.camera.setX((short) 0x3C80);
        return world;
    }

    private static void advanceArena(Sonic3kMGZEvents events) {
        try {
            Method gate = Sonic3kMGZEvents.class.getDeclaredMethod("updateAct2BossArena");
            gate.setAccessible(true);
            gate.invoke(events);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean runFrame(Runnable frame, Runnable discard, Runnable title) throws Exception {
        Method boundary = Engine.class.getDeclaredMethod("runFrameWithModAbort",
                Runnable.class, Runnable.class, Runnable.class);
        boundary.setAccessible(true);
        return (boolean) boundary.invoke(null, frame, discard, title);
    }

    private static final class World {
        final ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        final AtomicInteger providerCalls = new AtomicInteger();
        final AtomicInteger retainedCallbackCalls = new AtomicInteger();
        final SonicConfigurationService configuration = GameServices.configuration();
        final Camera camera = new Camera(configuration);
        final GameStateManager state = new GameStateManager();
        final AudioManager audio = mock(AudioManager.class);
        final GameModule module;
        final AbstractLevelEventManager nativeEvents;
        final ObjectManager objects;
        final Sonic3kMGZEvents events;

        World() {
            this(List.of());
        }

        World(List<ObjectSpawn> placements) {
            var base = new Sonic3kGameModule();
            nativeEvents = (AbstractLevelEventManager) base.getLevelEventProvider();
            var registration = new ModContext(OWNER, "s3k", ModAssetRoot.forTests(OWNER));
            registration.registerServiceBundle("recipe", () -> GameServiceBundle.builder()
                    .dynamicService(MarbleGardenBossRecipe.class, () -> {
                        providerCalls.incrementAndGet();
                        throw new IllegalStateException("first creator recipe invocation failed");
                    }).service(AizMinibossAttackPolicy.class, hits -> {
                        retainedCallbackCalls.incrementAndGet();
                        return 16;
                    }).build());
            module = OwnedModServices.decorate(base, registration.freeze(), boundary);
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public Camera camera() { return camera; }
                @Override public SonicConfigurationService configuration() { return configuration; }
                @Override public GameModule gameModule() { return module; }
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public GameStateManager gameState() { return state; }
                @Override public AudioManager audioManager() { return audio; }
                @Override public int romZoneId() { return 2; }
                @Override public int currentAct() { return 1; }
                @Override public ObjectPlayerQuery playerQuery() {
                    return new ObjectPlayerQuery(() -> null, List::of);
                }
            };
            objects = new ObjectManager(placements, new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objects;
            events = new Sonic3kMGZEvents() {
                @Override protected Camera camera() { return camera; }
                @Override protected GameStateManager gameState() { return state; }
                @Override protected GameModule module() { return module; }
                @Override protected AudioManager audio() { return audio; }
                @Override protected <T extends ObjectInstance> T spawnObject(Supplier<T> factory) {
                    return objects.createDynamicObject(factory);
                }
            };
            events.init(1);
        }
    }
}
