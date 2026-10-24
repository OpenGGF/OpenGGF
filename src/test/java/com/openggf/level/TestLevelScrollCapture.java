package com.openggf.level;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.*;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.render.AdvancedRenderMode;
import com.openggf.game.render.AdvancedRenderModeContext;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.*;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-free real owner/capture behavior; no renderer or production constructor substitutes. */
class TestLevelScrollCapture {
    @Test void failedLevelConstructionReleasesStaticMockAndNextFixtureUsesRealBootstrapDependencies() {
        var previousConfig = GameServices.configuration();
        var previousGraphics = GameServices.graphics();
        var failure = new IllegalStateException("GLCommand bootstrap failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new ScrollFixture(fixture -> {
            doThrow(failure).when(fixture.config).getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        })));
        assertSame(previousConfig, GameServices.configuration());
        assertSame(previousGraphics, GameServices.graphics());
        try (var recovered = new ScrollFixture()) {
            assertSame(recovered.config, GameServices.configuration());
            assertSame(recovered.graphics, GameServices.graphics());
            assertNotNull(LevelSpritePresentation.captureScroll(recovered.level));
        }
        assertSame(previousConfig, GameServices.configuration());
        assertSame(previousGraphics, GameServices.graphics());
    }

    @Test void validationIsPureAndRejectsForeignLoadAndResetBeforeCompositeRestore() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var camera = f.camera.capture();
            var tables = f.renderer.spriteTables.capture();
            var mode = f.renderer.getCurrentAdvancedRenderFrameState();
            clearInvocations(f.graphics, f.parallax, f.sprites);
            LevelSpritePresentation.validateScroll(f.level, frame);
            assertSame(mode, f.renderer.getCurrentAdvancedRenderFrameState());
            assertEquals(camera, f.camera.capture());
            assertEquals(tables, f.renderer.spriteTables.capture());
            assertEquals(1, f.resolutions.get());
            verifyNoInteractions(f.parallax, f.sprites);
            verify(f.graphics, never()).flush();
            verify(f.graphics, never()).flushPatternBatch();
            verify(f.graphics, never()).discardQueuedCommands();
            verify(f.graphics, never()).executeCapturedCommands(any(), anyInt(), anyInt(), anyInt(), anyInt());
            var foreign = f.newLevel(new WorldSession(f.module, f.module, mock(GameDataSource.class), null));
            assertThrows(IllegalArgumentException.class, () -> LevelSpritePresentation.validateScroll(foreign, frame));
            f.level.generation++;
            assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.validateScroll(f.level, frame));
            f.level.generation--;
            f.renderer.resetState();
            assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.validateScroll(f.level, frame));
        }
    }

    @Test void repeatedResolutionCannotOverwriteThePriorDrawsControllerBuffer() {
        try (var f = new ScrollFixture()) {
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            f.overrideColumns[0] = 123;
            for (int i = 0; i < 3; i++) LevelSpritePresentation.captureScroll(f.level);
            assertArrayEquals(new short[] {-4, 7},
                    f.renderer.getCurrentAdvancedRenderFrameState().foregroundPerColumnVScrollOverride());
        }
    }

    @Test void captureCopiesEachLiveBufferOnceWithoutDrawingPreparingOrAdvancingOwners() {
        try (var f = new ScrollFixture()) {
            var camera = f.camera.capture();
            var tables = f.renderer.spriteTables.capture();
            var mask = f.renderer.boundsMask.capture();
            clearInvocations(f.parallax, f.graphics, f.sprites);
            var first = LevelSpritePresentation.captureScroll(f.level);
            assertEquals(100, first.cameraX());
            assertEquals(200, first.cameraY());
            assertEquals(1, f.resolutions.get());
            verify(f.parallax).getHScrollForShader();
            verify(f.parallax).getVScrollPerLineBGForShader();
            verify(f.parallax).getVScrollPerColumnBGForShader();
            verify(f.parallax).getVScrollPerColumnFGForShader();
            verify(f.parallax).getVscrollFactorFG();
            verify(f.parallax).getVscrollFactorBG();
            verify(f.parallax).getBgCameraX();
            verify(f.parallax).getBgPeriodWidth();
            verifyNoMoreInteractions(f.parallax);
            verify(f.graphics, never()).flush();
            verify(f.graphics, never()).executeCapturedCommands(any(), anyInt(), anyInt(), anyInt(), anyInt());
            verifyNoInteractions(f.sprites);
            assertEquals(camera, f.camera.capture());
            assertEquals(tables, f.renderer.spriteTables.capture());
            assertEquals(mask, f.renderer.boundsMask.capture());
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            var equal = LevelSpritePresentation.captureScroll(f.level);
            assertEquals(first, equal);
            assertEquals(first.hashCode(), equal.hashCode());
            f.horizontal[0] = 999; f.lines[0] = 999; f.columns[0] = 999;
            f.foregroundColumns[0] = 999; f.overrideColumns[0] = 999;
            assertNotEquals(first, LevelSpritePresentation.captureScroll(f.level));
            LevelSpritePresentation.withScroll(f.level, first, () -> {
                var bg = LevelSpritePresentation.backgroundScrollForDraw(f.level);
                assertArrayEquals(new int[] {0xff9cfff9, 0xffa2000f}, bg.horizontal());
                assertArrayEquals(new short[] {-3, 12}, bg.columns());
                bg.horizontal()[0] = 123; bg.columns()[0] = 123;
                assertEquals(0xff9cfff9, LevelSpritePresentation.backgroundScrollForDraw(f.level).horizontal()[0]);
                assertEquals(-3, LevelSpritePresentation.backgroundScrollForDraw(f.level).columns()[0]);
                var mode = f.renderer.getCurrentAdvancedRenderFrameState();
                assertArrayEquals(new short[] {-4, 7}, mode.foregroundPerColumnVScrollOverride());
                mode.foregroundPerColumnVScrollOverride()[0] = 123;
            });
            f.restoreBuffers();
            assertEquals(first, LevelSpritePresentation.captureScroll(f.level));
        }
    }

    @Test void foreignOwnerReplacementAndLifetimeRejectBeforeCallbackOrQueueDrain() {
        try (var f = new ScrollFixture()) {
            var first = LevelSpritePresentation.captureScroll(f.level);
            var foreign = f.newLevel(new WorldSession(f.module, f.module, mock(GameDataSource.class), null));
            assertThrows(IllegalArgumentException.class, () -> LevelSpritePresentation.withScroll(foreign, first, () -> fail("foreign")));
            assertNotEquals(first, LevelSpritePresentation.captureScroll(foreign));
            f.world.setCurrentLevel(mock(Level.class));
            reject(f, first);
            f.world.setCurrentLevel(f.terrain);
            var atlas = f.graphics.getPatternAtlas();
            doReturn(new PatternAtlas(512, 512)).when(f.graphics).getPatternAtlas();
            reject(f, first);
            doReturn(atlas).when(f.graphics).getPatternAtlas();
            f.level.generation++;
            reject(f, first);
            f.level.generation--;
            var rewind = f.renderer.spriteTables.capture();
            f.renderer.spriteTables.invalidateScrollForActReload();
            f.renderer.spriteTables.restore(rewind);
            reject(f, first); // rewind never resurrects a prior load's lifetime
            var fresh = LevelSpritePresentation.captureScroll(f.level);
            f.renderer.resetState();
            reject(f, fresh);
            LevelSpritePresentation.withScroll(f.level, LevelSpritePresentation.captureScroll(f.level), () -> { });
        }
    }

    @Test void captureRejectsIncompleteAttachmentAndReleasesGuardAfterResolverFailure() {
        try (var f = new ScrollFixture()) {
            f.world.setCurrentLevel(null);
            assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level));
            f.world.setCurrentLevel(f.terrain);
            f.level.parallaxManager = null;
            assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level));
            f.level.parallaxManager = f.parallax;
            var failure = new IllegalStateException("contributor failed");
            f.controller.register(new AdvancedRenderMode() {
                public String id() { return "failure"; }
                public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) { throw failure; }
            });
            assertSame(failure, assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level)));
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            f.controller.clear();
            assertNotNull(LevelSpritePresentation.captureScroll(f.level));
        }
    }

    @Test void resolverCannotReenterCaptureScopeValidationOrRendererDrawing() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.controller.register(new AdvancedRenderMode() {
                public String id() { return "reentry"; }
                public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
                    assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level));
                    assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.validateScroll(f.level, frame));
                    assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> fail("reentry")));
                    assertThrows(IllegalStateException.class, () -> f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly()));
                }
            });
            assertEquals(frame, LevelSpritePresentation.captureScroll(f.level));
            verify(f.graphics, never()).executeCapturedCommands(any(), anyInt(), anyInt(), anyInt(), anyInt());
        }
    }

    private static void reject(ScrollFixture f, LevelSpritePresentation.ScrollFrame frame) {
        clearInvocations(f.graphics);
        assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> fail("stale frame")));
        verify(f.graphics, never()).flush();
        verify(f.graphics, never()).executeCapturedCommands(any(), anyInt(), anyInt(), anyInt(), anyInt());
    }
}

/** Actual level/renderer/world/camera owners with bounded buffer producers and headless graphics. */
final class ScrollFixture implements AutoCloseable {
    final MockedStatic<GameServices> services;
    final SonicConfigurationService config = mock(SonicConfigurationService.class);
    final Camera camera;
    final ParallaxManager parallax = mock(ParallaxManager.class);
    final SpriteManager sprites = mock(SpriteManager.class);
    final GraphicsManager graphics = spy(new GraphicsManager());
    final GameModule module = mock(GameModule.class);
    final Level terrain = mock(Level.class);
    final WorldSession world = new WorldSession(module, module, mock(GameDataSource.class), null);
    final AdvancedRenderModeController controller = new AdvancedRenderModeController();
    final AtomicInteger resolutions = new AtomicInteger();
    final int[] horizontal = new int[2];
    final short[] lines = new short[2], columns = new short[2], foregroundColumns = new short[2], overrideColumns = new short[2];
    final FixtureLevel level;
    final LevelRenderer renderer;

    ScrollFixture() { this(fixture -> { }); }

    ScrollFixture(java.util.function.Consumer<ScrollFixture> beforeLevelConstruction) {
        services = mockStatic(GameServices.class, CALLS_REAL_METHODS);
        try {
            // GLCommand field initializers run during LevelManager construction.
            // Preserve real fallback services; override only this fixture's explicit owners.
            services.when(GameServices::configuration).thenReturn(config);
            services.when(GameServices::graphics).thenReturn(graphics);
            services.when(GameServices::advancedRenderModeControllerOrNull).thenReturn(controller);
            services.when(GameServices::specialRenderEffectRegistryOrNull)
                    .thenReturn(new com.openggf.game.render.SpecialRenderEffectRegistry());
            services.when(GameServices::paletteOwnershipRegistryOrNull)
                    .thenReturn(new com.openggf.game.palette.PaletteOwnershipRegistry());
            services.when(GameServices::ghostRenderRegistryOrNull)
                    .thenReturn(new com.openggf.game.ghost.GhostRenderRegistry());
        when(module.getRules()).thenReturn(mock(com.openggf.game.rules.GameRules.class, RETURNS_DEEP_STUBS));
        when(config.getShort(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short) 320);
        when(config.getShort(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn((short) 224);
        when(config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn(320);
        when(config.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        camera = new Camera(config);
        camera.setFocusedSprite(mock(com.openggf.sprites.playable.AbstractPlayableSprite.class));
        camera.setX((short) 100); camera.setY((short) 200); camera.setShakeOffsets(3, -2);
        graphics.initHeadless();
        graphics.bindRuntimeManagedReferences(camera, new FadeManager());
        restoreBuffers();
        when(parallax.getHScrollForShader()).thenReturn(horizontal);
        when(parallax.getVScrollPerLineBGForShader()).thenReturn(lines);
        when(parallax.getVScrollPerColumnBGForShader()).thenReturn(columns);
        when(parallax.getVScrollPerColumnFGForShader()).thenReturn(foregroundColumns);
        when(parallax.getVscrollFactorFG()).thenReturn((short) 198);
        when(parallax.getVscrollFactorBG()).thenReturn((short) -7);
        when(parallax.getBgCameraX()).thenReturn(17);
        when(parallax.getBgPeriodWidth()).thenReturn(512);
        when(terrain.getMap()).thenReturn(new Map(2, 1, 1));
        beforeLevelConstruction.accept(this);
        level = newLevel(world);
        renderer = level.spritePresentationRenderer();
        controller.register(new AdvancedRenderMode() {
            public String id() { return "scroll-fixture"; }
            public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
                resolutions.incrementAndGet();
                builder.enablePerLineForegroundScroll();
                builder.setForegroundVScrollOverride((short) 211);
                builder.setBackgroundVScrollOverride((short) -17);
                builder.setForegroundPerColumnVScrollOverride(overrideColumns);
            }
        });
        } catch (RuntimeException | Error failure) {
            services.close();
            throw failure;
        }
    }

    FixtureLevel newLevel(WorldSession owner) {
        owner.setCurrentLevel(terrain);
        var engine = new EngineContext(config, graphics, mock(AudioManager.class), mock(RomManager.class),
                mock(PerformanceProfiler.class), mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class));
        var result = new FixtureLevel(camera, sprites, parallax, engine, owner);
        result.gameModule = module;
        result.tilemapManager = mock(LevelTilemapManager.class);
        when(result.tilemapManager.getBackgroundTilemapWidthTiles()).thenReturn(64);
        return result;
    }

    void restoreBuffers() {
        horizontal[0] = 0xff9cfff9; horizontal[1] = 0xffa2000f;
        lines[0] = -2; lines[1] = 9; columns[0] = -3; columns[1] = 12;
        foregroundColumns[0] = -5; foregroundColumns[1] = 13; overrideColumns[0] = -4; overrideColumns[1] = 7;
    }
    public void close() { services.close(); }

    static final class FixtureLevel extends LevelManager {
        long generation = 1;
        FixtureLevel(Camera camera, SpriteManager sprites, ParallaxManager parallax, EngineContext engine, WorldSession world) {
            super(camera, sprites, parallax, mock(CollisionSystem.class), mock(WaterSystem.class), new GameStateManager(), engine, world);
        }
        @Override public long getCompletedProductionLoadGeneration() { return generation; }
        @Override void ensureForegroundTilemapData() { }
        @Override void ensureBackgroundTilemapData() { }
        @Override boolean applyBackgroundTilemapWindowSelection(int bgCameraX) { return false; }
        @Override Palette.Color resolveLevelBackdropColor() { return new Palette.Color((byte) 0, (byte) 0, (byte) 0); }
    }
}
