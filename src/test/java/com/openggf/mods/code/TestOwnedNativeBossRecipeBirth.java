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
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
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
import com.openggf.level.objects.StubObjectServices;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
        SessionManager.clear();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameServices.configuration().resetToDefaults();
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void reset() {
        SessionManager.clear();
        GameServices.configuration().resetToDefaults();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sonic", "tails", "knuckles"})
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
    @ValueSource(strings = {"sonic", "tails", "knuckles"})
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
        boolean knuckles = character.equals("knuckles");
        var boss = knuckles
                ? assertInstanceOf(MgzEndBossKnuxInstance.class, objects.getFirst())
                : assertInstanceOf(MgzEndBossInstance.class, objects.getFirst());
        int expectedId = knuckles ? Sonic3kObjectIds.MGZ_END_BOSS_KNUX : Sonic3kObjectIds.MGZ_END_BOSS;
        assertEquals(expectedId, world.state.getCurrentBossId());
        assertEquals(expectedId, boss.getSpawn().objectId());
        assertEquals(0x3D20, boss.getSpawn().x());
        assertEquals(knuckles ? 0x68 : 0x668, boss.getSpawn().y());
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

    private static World approach(String character) {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var world = new World();
        world.camera.setX((short) 0x3A00);
        world.camera.setY((short) (character.equals("knuckles") ? 0 : 0x600));
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
            };
            objects = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
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
