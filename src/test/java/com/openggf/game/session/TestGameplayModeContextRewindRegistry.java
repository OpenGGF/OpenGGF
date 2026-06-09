package com.openggf.game.session;

import com.openggf.camera.Camera;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.animation.AnimatedTileCachePolicy;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.animation.DestinationPlan;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.NoOpBonusStageProvider;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.render.AdvancedRenderMode;
import com.openggf.game.render.AdvancedRenderModeContext;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.zone.NoOpZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the {@link RewindRegistry} integration on
 * {@link GameplayModeContext}.
 *
 * <p>Tests verify that the seven always-available atomic adapters are
 * registered automatically when {@link GameplayModeContext#attachGameplayManagers}
 * is called, without requiring a full level load or ROM access.
 */
class TestGameplayModeContextRewindRegistry {

    @BeforeEach
    void configureServices() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    private static GameplayModeContext buildAttachedContext() {
        WorldSession world = new WorldSession(new Sonic2GameModule());
        GameplayModeContext ctx = new GameplayModeContext(world);

        Camera camera = new Camera();
        TimerManager timers = new TimerManager();
        GameStateManager gameState = new GameStateManager();
        FadeManager fade = new FadeManager();
        GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
        DefaultSolidExecutionRegistry solid = new DefaultSolidExecutionRegistry();

        ctx.attachGameplayManagers(camera, timers, gameState, fade, rng, solid);
        return ctx;
    }

    @Test
    void registryIsNonNullAfterAttach() {
        GameplayModeContext ctx = buildAttachedContext();
        assertNotNull(ctx.getRewindRegistry());
    }

    @Test
    void registryHasAtomicAdaptersAfterAttach() {
        GameplayModeContext ctx = buildAttachedContext();
        RewindRegistry registry = ctx.getRewindRegistry();

        CompositeSnapshot snapshot = registry.capture();

        Set<String> expectedKeys = Set.of(
                "camera",
                "gamestate",
                "gamerng",
                "timermanager",
                "fademanager",
                "oscillation",
                "solid-execution");
        assertTrue(snapshot.entries().keySet().containsAll(expectedKeys),
                "Expected all atomic adapter keys to be present, got: " + snapshot.entries().keySet());
    }

    @Test
    void exactlySevenAtomicKeysAfterAttach() {
        GameplayModeContext ctx = buildAttachedContext();
        RewindRegistry registry = ctx.getRewindRegistry();
        CompositeSnapshot snapshot = registry.capture();
        assertEquals(7, snapshot.entries().keySet().size(),
                "Expected exactly 7 atomic adapters, got: " + snapshot.entries().keySet());
    }

    @Test
    void registryIsNullBeforeAttach() {
        WorldSession world = new WorldSession(new Sonic2GameModule());
        GameplayModeContext ctx = new GameplayModeContext(world);
        assertNull(ctx.getRewindRegistry(),
                "Registry should be null until attachGameplayManagers is called");
    }

    @Test
    void tearDownClearsRegistry() {
        GameplayModeContext ctx = buildAttachedContext();
        assertNotNull(ctx.getRewindRegistry());
        ctx.tearDownManagers();
        assertFalse(ctx.isGameplayRuntimeReady(),
                "A torn-down gameplay context must not be advertised as runtime-ready");
        assertNull(ctx.getRewindRegistry(),
                "Registry should be null after tearDownManagers");
    }

    @Test
    void tearDownClearsActiveBonusStageProvider() {
        GameplayModeContext ctx = buildAttachedContext();
        BonusStageProvider provider = new StubBonusStageProvider();
        ctx.setActiveBonusStageProvider(provider);
        assertSame(provider, ctx.getActiveBonusStageProvider());

        ctx.tearDownManagers();

        assertSame(NoOpBonusStageProvider.INSTANCE, ctx.getActiveBonusStageProvider(),
                "A torn-down gameplay context must not retain a session-scoped bonus stage provider");
    }

    @Test
    void reattachRebuildsRegistry() {
        GameplayModeContext ctx = buildAttachedContext();
        RewindRegistry first = ctx.getRewindRegistry();

        // Tear down and re-attach (simulates a resume-parked path)
        ctx.tearDownManagers();
        Camera camera2 = new Camera();
        TimerManager timers2 = new TimerManager();
        GameStateManager gameState2 = new GameStateManager();
        FadeManager fade2 = new FadeManager();
        GameRng rng2 = new GameRng(GameRng.Flavour.S1_S2);
        DefaultSolidExecutionRegistry solid2 = new DefaultSolidExecutionRegistry();
        ctx.attachGameplayManagers(camera2, timers2, gameState2, fade2, rng2, solid2);

        RewindRegistry second = ctx.getRewindRegistry();
        assertNotNull(second);
        assertNotSame(first, second, "Re-attach should produce a new RewindRegistry instance");
        // New registry should have the same 7 keys
        assertEquals(7, second.capture().entries().keySet().size());
    }

    @Test
    void partialCoreReattachAfterTeardownDoesNotReportRuntimeReady() {
        GameplayModeContext ctx = buildAttachedContext();
        attachLevelManagers(ctx);
        attachSharedRegistries(ctx);
        assertTrue(ctx.isGameplayRuntimeReady(), "Fully attached context should be runtime-ready");

        ctx.tearDownManagers();
        ctx.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2), new DefaultSolidExecutionRegistry());

        assertFalse(ctx.isGameplayRuntimeReady(),
                "Core-manager reattach must not expose stale level managers or registries as runtime-ready");
    }

    @Test
    void sharedRuntimeRegistriesAreRewindRegisteredAfterAttach() {
        GameplayModeContext ctx = buildAttachedContext();
        attachSharedRegistries(ctx);

        CompositeSnapshot snapshot = ctx.getRewindRegistry().capture();

        assertTrue(snapshot.entries().keySet().containsAll(Set.of(
                "zone-runtime",
                "palette-ownership",
                "animated-tile-channels",
                "special-render",
                "advanced-render-mode",
                "mutation-pipeline")),
                "Expected all runtime-owned registry adapters, got: " + snapshot.entries().keySet());
    }

    @Test
    void tearDownClearsAllAttachedSharedRegistryState() {
        GameplayModeContext ctx = buildAttachedContext();
        ZoneRuntimeRegistry zoneRuntime = new ZoneRuntimeRegistry();
        PaletteOwnershipRegistry paletteOwnership = new PaletteOwnershipRegistry();
        AnimatedTileChannelGraph animatedTiles = new AnimatedTileChannelGraph();
        SpecialRenderEffectRegistry specialRender = new SpecialRenderEffectRegistry();
        AdvancedRenderModeController advancedRender = new AdvancedRenderModeController();
        ZoneLayoutMutationPipeline mutationPipeline = new ZoneLayoutMutationPipeline();

        ctx.attachSharedRegistries(zoneRuntime, paletteOwnership, animatedTiles,
                specialRender, advancedRender, mutationPipeline);

        zoneRuntime.install(new MutableZoneRuntimeState());
        paletteOwnership.setPaletteRotationDisabled(true);
        animatedTiles.install(List.of(dummyChannel()));
        specialRender.register(new StatefulEffect());
        advancedRender.register(new StatefulMode());
        mutationPipeline.queue(context -> MutationEffects.redrawAllTilemaps());

        ctx.tearDownManagers();

        assertSame(NoOpZoneRuntimeState.INSTANCE, zoneRuntime.current());
        assertFalse(paletteOwnership.isPaletteRotationDisabled(),
                "palette rotation disable state must not leak after gameplay teardown");
        assertTrue(animatedTiles.channels().isEmpty());
        assertTrue(specialRender.isEmpty());
        assertTrue(advancedRender.isEmpty());
        assertTrue(mutationPipeline.isEmpty());
    }

    @Test
    void specialRenderRegistryRestoresStatefulEffectSnapshots() {
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();
        StatefulEffect effect = new StatefulEffect();
        registry.register(effect);
        effect.value = 7;

        var snapshot = registry.capture();
        effect.value = 11;

        registry.restore(snapshot);

        assertEquals(7, effect.value);
    }

    @Test
    void advancedRenderControllerRestoresStatefulModeSnapshots() {
        AdvancedRenderModeController controller = new AdvancedRenderModeController();
        StatefulMode mode = new StatefulMode();
        controller.register(mode);
        mode.value = 5;

        var snapshot = controller.capture();
        mode.value = 9;

        controller.restore(snapshot);

        assertEquals(5, mode.value);
    }

    private static void attachSharedRegistries(GameplayModeContext ctx) {
        ctx.attachSharedRegistries(
                new ZoneRuntimeRegistry(),
                new PaletteOwnershipRegistry(),
                new AnimatedTileChannelGraph(),
                new SpecialRenderEffectRegistry(),
                new AdvancedRenderModeController(),
                new ZoneLayoutMutationPipeline());
    }

    private static void attachLevelManagers(GameplayModeContext ctx) {
        WaterSystem water = new WaterSystem();
        ParallaxManager parallax = new ParallaxManager();
        TerrainCollisionManager terrain = new TerrainCollisionManager();
        CollisionSystem collision = new CollisionSystem(terrain);
        SpriteManager sprites = new SpriteManager();
        LevelManager level = mock(LevelManager.class);
        ctx.attachLevelManagers(water, parallax, terrain, collision, sprites, level);
    }

    private static AnimatedTileChannel dummyChannel() {
        return new AnimatedTileChannel(
                "dummy",
                () -> true,
                context -> 0,
                DestinationPlan.single(0),
                AnimatedTileCachePolicy.ALWAYS,
                context -> {
                });
    }

    private static final class MutableZoneRuntimeState implements ZoneRuntimeState {
        @Override
        public String gameId() {
            return "s2";
        }

        @Override
        public int zoneIndex() {
            return 0;
        }

        @Override
        public int actIndex() {
            return 0;
        }
    }

    private static final class StatefulEffect
            implements SpecialRenderEffect, RewindSnapshottable<Integer> {
        private int value;

        @Override
        public SpecialRenderEffectStage stage() {
            return SpecialRenderEffectStage.AFTER_BACKGROUND;
        }

        @Override
        public void render(SpecialRenderEffectContext context) {
        }

        @Override
        public String key() {
            return "stateful-effect";
        }

        @Override
        public Integer capture() {
            return value;
        }

        @Override
        public void restore(Integer snapshot) {
            value = snapshot;
        }
    }

    private static final class StatefulMode
            implements AdvancedRenderMode, RewindSnapshottable<Integer> {
        private int value;

        @Override
        public String id() {
            return "stateful-mode";
        }

        @Override
        public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
        }

        @Override
        public String key() {
            return "stateful-mode";
        }

        @Override
        public Integer capture() {
            return value;
        }

        @Override
        public void restore(Integer snapshot) {
            value = snapshot;
        }
    }

    private static final class StubBonusStageProvider implements BonusStageProvider {
        @Override
        public boolean hasBonusStages() {
            return true;
        }

        @Override
        public BonusStageType selectBonusStage(int ringCount) {
            return BonusStageType.SLOT_MACHINE;
        }

        @Override
        public void onEnter(BonusStageType type, BonusStageState savedState) {
        }

        @Override
        public void onExit() {
        }

        @Override
        public void onFrameUpdate() {
        }

        @Override
        public boolean isStageComplete() {
            return false;
        }

        @Override
        public void requestExit() {
        }

        @Override
        public BonusStageRewards getRewards() {
            return BonusStageRewards.none();
        }

        @Override
        public int getZoneId(BonusStageType type) {
            return -1;
        }

        @Override
        public int getMusicId(BonusStageType type) {
            return -1;
        }

        @Override
        public BonusStageState getSavedState() {
            return null;
        }
    }
}
