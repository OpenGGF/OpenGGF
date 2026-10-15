package com.openggf.mods.code;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.difficulty.AizBombingRunRecipe;
import com.openggf.game.sonic3k.difficulty.AizEndBossRecipe;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Actual registration/fault publication composed with allocated native attack owners.
 * Commit callbacks isolate the ownership boundary; this does not certify a cold
 * arena route, object-execution timing, ROM art or visible warning duration.
 */
class TestOwnedNativeAttackLifetime {
    private static final String OWNER = "attack-owner";

    @BeforeEach
    void headless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void reset() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void realFaultWithdrawsFireBreathPolicyAndRetiresItsProducedFlames() throws Exception {
        var h = new Harness();
        var boss = h.objects.createDynamicObject(() -> new AizMinibossInstance(
                new ObjectSpawn(0x180, 0x180, 0x91, 0, 0, false, 0)));
        boss.getState().hitCount = 3;
        invoke(boss, "onInitialDelayComplete");
        invoke(boss, "prepareDifficultyFollowup");
        assertEquals(3, h.live(AizMinibossBarrelShotFlareChild.class).size(),
                "the real native warning graph must admit the follow-up");
        for (int tick = 1; tick <= 24; tick++) {
            invoke(boss, "updateDifficultyFollowup");
            for (var warning : h.live(AizMinibossBarrelShotFlareChild.class)) warning.update(tick, null);
        }
        var flames = h.live(AizMinibossFlameChild.class);
        assertEquals(4, flames.size(), "the native producer creates its whole committed wave");
        flames.forEach(flame -> flame.update(25, null));
        assertTrue(flames.stream().anyMatch(flame -> flame.getCollisionFlags() != 0),
                "withdrawal must be tested against a genuinely harmful native flame");

        h.fault();
        assertNull(h.module.getGameService(AizMinibossAttackPolicy.class));
        for (var flame : flames) {
            assertEquals(0, flame.getCollisionFlags());
            flame.update(26, null);
            assertTrue(flame.isDestroyed());
        }
        assertFalse(boss.isDestroyed(), "quarantine withdraws the optional attack, not the native boss");
    }

    @Test
    void restoredWaterfallAttackCannotReviveAQuarantinedRecipe() throws Exception {
        var h = new Harness();
        var boss = h.objects.createDynamicObject(() -> new AizEndBossInstance(
                new ObjectSpawn(0x180, 0x180, 0x92, 0, 0, false, 0)));
        invoke(boss, "spawnArmChildren");
        boss.getState().lastUpdatedVIntRunCount = 0;
        for (var arm : h.live(AizEndBossArmChild.class)) arm.update(0, null);
        for (var propeller : h.live(AizEndBossPropellerChild.class)) propeller.update(0, null);
        assertEquals(2, h.live(AizEndBossPropellerChild.class).size(),
                "native arm initialization requires the parent current update clock");
        invoke(boss, "onHoverComplete");
        for (int tick = 1; tick <= 160 && h.live(AizEndBossFlameChild.class).isEmpty(); tick++) {
            for (var propeller : h.live(AizEndBossPropellerChild.class)) propeller.update(tick, null);
        }
        var originalFlame = h.live(AizEndBossFlameChild.class).getFirst();
        assertNotEquals(0, originalFlame.getCollisionFlags());
        RewindRegistry registry = new RewindRegistry();
        registry.register(h.objects.rewindSnapshottable());
        var snapshot = registry.capture();

        h.fault();
        assertNull(h.module.getGameService(AizEndBossRecipe.class));
        List.copyOf(h.objects.getActiveObjects()).forEach(h.objects::removeDynamicObject);
        registry.restore(snapshot);
        var restoredFlame = h.live(AizEndBossFlameChild.class).getFirst();
        assertNotSame(originalFlame, restoredFlame);
        assertEquals(0, restoredFlame.getCollisionFlags(),
                "historical attack state cannot revive failed creator authority");
        for (var flame : h.live(AizEndBossFlameChild.class)) {
            flame.update(161, null);
            assertTrue(flame.isDestroyed());
        }
        assertFalse(h.live(AizEndBossInstance.class).isEmpty());
    }

    @Test
    void realFaultRetiresACommittedShipBombBeforeNativeImpact() {
        var h = new Harness();
        var ship = h.objects.createDynamicObject(() -> new AizBattleshipInstance(
                new ObjectSpawn(0x100, 0x8F0, 0, 0, 0, false, 0), 0x8F0));
        for (int tick = 0; tick <= 421; tick++) ship.update(tick, null);
        var bomb = h.live(AizShipBombInstance.class).getFirst();
        assertFalse(bomb.isDestroyed());

        h.fault();
        assertNull(h.module.getGameService(AizBombingRunRecipe.class));
        bomb.update(422, null);
        assertTrue(bomb.isDestroyed());
        assertFalse(ship.isDestroyed(), "native ship script/exit remains the original owner's job");
    }

    private static final class Harness {
        final ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        final GameModule module;
        final ObjectManager objects;

        Harness() {
            var context = new ModContext(OWNER, "s3k", ModAssetRoot.forTests(OWNER));
            context.registerService("waterfall", AizEndBossRecipe.class, AizEndBossRecipe.ALTERNATING_ARMS);
            context.registerService("bombing", AizBombingRunRecipe.class, AizBombingRunRecipe.STAGGERED_PORTS);
            context.registerService("fire-breath", AizMinibossAttackPolicy.class, hits -> 24);
            module = OwnedModServices.decorate(mock(GameModule.class, CALLS_REAL_METHODS), context.freeze(), boundary);
            var config = SonicConfigurationService.createStandalone();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            var camera = new Camera(config);
            var renderer = mock(PatternSpriteRenderer.class);
            when(renderer.isReady()).thenReturn(true);
            var renders = mock(ObjectRenderManager.class);
            when(renders.getRenderer(anyString())).thenReturn(renderer);
            ObjectManager[] holder = new ObjectManager[1];
            var state = new GameStateManager();
            var services = new StubObjectServices() {
                @Override public Camera camera() { return camera; }
                @Override public SonicConfigurationService configuration() { return config; }
                @Override public GameModule gameModule() { return module; }
                @Override public ObjectRenderManager renderManager() { return renders; }
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public GameStateManager gameState() { return state; }
            };
            objects = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objects;
        }

        <T> List<T> live(Class<T> type) {
            return objects.getActiveObjects().stream().filter(type::isInstance)
                    .filter(object -> !object.isDestroyed()).map(type::cast).toList();
        }

        void fault() {
            assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> boundary.run(OWNER, () -> { throw new IllegalStateException("creator failure"); }));
            assertFalse(boundary.isOwnerAvailable(OWNER));
        }
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
