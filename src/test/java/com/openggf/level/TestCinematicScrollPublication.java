package com.openggf.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.internal.BackgroundColumnProjectionSource;
import com.openggf.game.internal.BackgroundColumnRemap;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.tests.TestEnvironment;
import com.openggf.util.ShortIndexedView;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class TestCinematicScrollPublication {
    @Test
    void tallDirectedBackgroundKeepsNativeRowsThenRepeatsItsBottomPatternBandWithoutMovingForeground() {
        TestEnvironment.resetAll();
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var level = GameServices.level();
            enableDirectedSnapshots(level);
            level.parallaxManager.installContributedHandler(37,
                    new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            level.parallaxManager.update(37, 0, level.camera, 8);
            var captured = LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            var mask = captured.registers().arenaMask();
            var nativeView = captured.forCinematicView(32, 10, 400, 224, mask);
            var tall = captured.forCinematicView(32, 10, 400, 384, mask);
            assertArrayEquals(nativeView.horizontal(), java.util.Arrays.copyOf(tall.horizontal(), 224),
                    "The complete native logical image stays untouched");
            var lower = tall.slice(0, 224, 400, 224, mask);
            assertEquals(nativeView.registers().backgroundY(), lower.registers().backgroundY(),
                    "Never advance the finite plane origin into another 256-pixel wrap");
            assertEquals(tall.registers().foregroundY() + 224, lower.registers().foregroundY(),
                    "The tall foreground remains continuous world space");
            for (int row = 0; row < 160; row++) {
                int nativeRow = 224 - Pattern.PATTERN_HEIGHT + row % Pattern.PATTERN_HEIGHT;
                assertEquals(nativeRow, row + lower.backgroundLines()[row],
                        "Extra background rows repeat all scanlines of the bottom native pattern band");
                assertEquals((short) nativeView.horizontal()[nativeRow], (short) lower.horizontal()[row]);
            }
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void columnRemapIsCapturedWithNativeScrollAndRetainedAcrossPublicationAndRewind() {
        TestEnvironment.resetAll();
        // No level assembly occurs in this ROM-free fixture, so explicitly bind
        // its native stock module before decorating the directed capability.
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var changed = new AtomicBoolean();
            var provider =
                    mock(
                            ZoneFeatureProvider.class,
                            withSettings().extraInterfaces(BackgroundColumnProjectionSource.class));
            var source = (BackgroundColumnProjectionSource) provider;
            when(source.captureBackgroundColumns())
                    .thenAnswer(
                            ignored -> {
                                int offset = changed.get() ? 1000 : 0;
                                return (com.openggf.game.internal.BackgroundColumnProjection)
                                        view ->
                                                new BackgroundColumnRemap.Columns(
                                                        values(view.width(), offset),
                                                        values(view.width(), 7),
                                                        64);
                            });
            var level = GameServices.level();
            enableDirectedSnapshots(level);
            level.zoneFeatureProvider = provider;
            var camera = level.camera;
            camera.setX((short) 0);
            camera.setY((short) 0);
            level.parallaxManager.installContributedHandler(
                    37, new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            level.parallaxManager.update(37, 0, camera, 8);
            verify(source, times(1)).captureBackgroundColumns();
            clearInvocations(provider);
            changed.set(true);
            var nativeScroll =
                    LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            var tables = new LevelSpritePresentation.Tables();
            tables.prepare(new SpritePresentation.Frame(List.of()), nativeScroll);
            tables.publish();
            var saved = tables.capture();
            var directed =
                    tables.publishedScroll()
                            .forCinematicView(0, 0, 400, 300, nativeScroll.registers().arenaMask());
            assertEquals(17, directed.backgroundColumnsRemap().sourceX().get(17));
            var tile = directed.slice(17, 32, 63, 100, directed.registers().arenaMask());
            assertEquals(17, tile.backgroundColumnsRemap().sourceX().get(0));
            assertEquals(64, tile.backgroundColumnsRemap().sourceYEndExclusive(),
                    "Background tiles retain the native source origin, so the native cutoff stays unchanged");
            assertEquals(1, tile.columnPhaseX());
            verifyNoInteractions(provider);
            level.parallaxManager.update(37, 0, camera, 9);
            var next = LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            tables.prepare(new SpritePresentation.Frame(List.of()), next);
            tables.publish();
            assertEquals(
                    1017,
                    tables.publishedScroll()
                            .forCinematicView(0, 0, 400, 300, next.registers().arenaMask())
                            .backgroundColumnsRemap()
                            .sourceX()
                            .get(17));
            tables.restore(saved);
            assertEquals(
                    17,
                    tables.publishedScroll()
                            .forCinematicView(0, 0, 400, 300, nativeScroll.registers().arenaMask())
                            .backgroundColumnsRemap()
                            .sourceX()
                            .get(17));
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void ordinaryNativeTicksNeverCaptureDirectedOwnersAndClearRetiredSnapshots() {
        TestEnvironment.resetAll();
        // No level assembly occurs in this ROM-free fixture, so explicitly bind
        // its native stock module before decorating the directed capability.
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var level = GameServices.level();
            var nativeModule = level.gameModule;
            var provider =
                    mock(
                            ZoneFeatureProvider.class,
                            withSettings()
                                    .extraInterfaces(
                                            BackgroundColumnProjectionSource.class,
                                            com.openggf.game.internal
                                                    .BackgroundBuildProjectionSource.class));
            level.zoneFeatureProvider = provider;
            var handler = spy(new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            var parallax = level.parallaxManager;
            parallax.installContributedHandler(37, handler);
            var camera = level.camera;
            camera.setX((short) 160);
            camera.setY((short) 80);
            parallax.update(37, 0, camera, 7);
            verify(handler, never()).captureProjection(any());
            verifyNoInteractions(provider);
            assertNull(parallax.projectionPhase());
            assertNull(parallax.backgroundBuildParameters());
            assertNull(parallax.backgroundColumnProjection());
            int[] ordinaryWords = parallax.getHScrollForShader().clone();

            enableDirectedSnapshots(level);
            parallax.update(37, 0, camera, 8);
            assertNotNull(parallax.projectionPhase());
            assertNotNull(parallax.backgroundBuildParameters());
            verify(handler, times(1)).captureProjection(any());
            verify((BackgroundColumnProjectionSource) provider, times(1))
                    .captureBackgroundColumns();
            verify((com.openggf.game.internal.BackgroundBuildProjectionSource) provider, times(1))
                    .captureBackgroundBuild();
            assertArrayEquals(ordinaryWords, parallax.getHScrollForShader());

            clearInvocations(handler, provider);
            // No stale derived frame survives retiring the explicit capability.
            level.gameModule = nativeModule;
            parallax.update(37, 0, camera, 9);
            verify(handler, never()).captureProjection(any());
            verifyNoInteractions(provider);
            assertNull(parallax.projectionPhase());
            assertNull(parallax.backgroundBuildParameters());
            assertNull(parallax.backgroundColumnProjection());
            assertArrayEquals(ordinaryWords, parallax.getHScrollForShader());
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void separateEndingPublicationClearsDirectedSnapshotsAndPreservesNativeOutput() {
        TestEnvironment.resetAll();
        // No level assembly occurs in this ROM-free fixture, so explicitly bind
        // its native stock module before decorating the directed capability.
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var level = GameServices.level();
            var parallax = level.parallaxManager;
            parallax.installContributedHandler(
                    37, new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            var camera = level.camera;
            camera.setX((short) 160);
            camera.setY((short) 80);
            parallax.update(37, 0, camera, 7);
            parallax.updateForEnding(37, 0, 8, 73);
            int[] ordinaryEndingWords = parallax.getHScrollForShader().clone();
            short ordinaryForegroundY = parallax.getVscrollFactorFG();
            short ordinaryBackgroundY = parallax.getVscrollFactorBG();

            var provider =
                    mock(
                            ZoneFeatureProvider.class,
                            withSettings().extraInterfaces(BackgroundColumnProjectionSource.class));
            when(((BackgroundColumnProjectionSource) provider).captureBackgroundColumns())
                    .thenReturn(
                            view ->
                                    new BackgroundColumnRemap.Columns(
                                            values(view.width(), 0), values(view.width(), 7)));
            level.zoneFeatureProvider = provider;
            enableDirectedSnapshots(level);
            parallax.update(37, 0, camera, 9);
            assertNotNull(parallax.projectionPhase());
            assertNotNull(parallax.backgroundBuildParameters());
            assertNotNull(parallax.backgroundColumnProjection());
            clearInvocations(provider);

            parallax.updateForEnding(37, 0, 10, 73);
            assertArrayEquals(ordinaryEndingWords, parallax.getHScrollForShader());
            assertEquals(ordinaryForegroundY, parallax.getVscrollFactorFG());
            assertEquals(ordinaryBackgroundY, parallax.getVscrollFactorBG());
            assertNull(parallax.projectionPhase());
            assertNull(parallax.backgroundBuildParameters());
            assertNull(parallax.backgroundColumnProjection());
            verifyNoInteractions(provider);
            var ending =
                    LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            ending.forCinematicView(
                                    160, 80, 320, 224, ending.registers().arenaMask()));
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void fullViewCameraAndSlicesReuseOneFrozenRetainedGenerationWithoutNativeOwnerCalls() {
        TestEnvironment.resetAll();
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var level = GameServices.level();
            var nativeModule = level.gameModule;
            enableDirectedSnapshots(level);
            var foreground = new TestRetainedPlaneDescriptors.MutablePlane(128, 32);
            var background = new TestRetainedPlaneDescriptors.MutablePlane(64, 64);
            foreground.words[0] = 0xf123;
            background.words[0] = 0xa456;
            var provider = TestRetainedPlaneDescriptors.features(foreground, background);
            level.zoneFeatureProvider = provider;
            var handler = spy(new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            var parallax = level.parallaxManager;
            parallax.installContributedHandler(37, handler);
            parallax.update(37, 0, level.camera, 8);
            var nativeScroll = LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            var frozen = nativeScroll.backgroundBuildParameters();
            assertTrue(frozen.retainedForeground().active());
            assertTrue(frozen.retainedBackground().active());
            var tables = new LevelSpritePresentation.Tables();
            tables.prepare(new SpritePresentation.Frame(List.of()), nativeScroll);
            tables.publish();
            var publication = tables.capture();
            foreground.words[0] = 0x8888;
            background.revision = 0;
            when(((com.openggf.game.internal.RetainedPlaneDescriptorSource) provider).foregroundPlane())
                    .thenReturn(new TestRetainedPlaneDescriptors.MutablePlane(32, 32));
            clearInvocations(handler, provider);
            int foregroundReads = foreground.reads, backgroundReads = background.reads;
            for (int repeat = 0; repeat < 3; repeat++) {
                var full = tables.publishedScroll().forCinematicView(-32, 80, 400, 300,
                        nativeScroll.registers().arenaMask());
                var camera = full.forCinematicCamera(96, -16, full.registers().arenaMask());
                var slice = full.slice(17, 32, 63, 100, full.registers().arenaMask());
                for (var sample : new LevelScrollPresentation[]{full, camera, slice}) {
                    assertSame(frozen, sample.backgroundBuildParameters());
                    assertEquals(0xf123, sample.backgroundBuildParameters().retainedForeground().descriptorAt(0, 0));
                    assertEquals(0xa456, sample.backgroundBuildParameters().retainedBackground().descriptorAt(0, 0));
                    assertEquals(nativeScroll.tilemapMode(), camera.tilemapMode());
                }
            }
            assertEquals(foregroundReads, foreground.reads);
            assertEquals(backgroundReads, background.reads);
            verifyNoInteractions(handler, provider);
            tables.restore(publication);
            assertSame(frozen, tables.publishedScroll().backgroundBuildParameters());
            level.gameModule = nativeModule;
            parallax.update(37, 0, level.camera, 9);
            assertNull(parallax.projectionPhase());
            assertNull(parallax.backgroundBuildParameters());
            assertNull(parallax.backgroundColumnProjection());
            verifyNoInteractions(provider);
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void cameraOnlyScopeInstallsColdFrozenPlanesAndRestoresNativeResidencyAfterAnAbort() {
        TestEnvironment.resetAll();
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        var level = GameServices.level();
        var focusedSprite = mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        level.camera.setFocusedSprite(focusedSprite);
        var nativeGraphics = level.graphicsManager;
        try {
            enableDirectedSnapshots(level);
            var foreground = new TestRetainedPlaneDescriptors.MutablePlane(128, 32);
            var background = new TestRetainedPlaneDescriptors.MutablePlane(64, 64);
            foreground.words[0] = 0xf123;
            background.words[0] = 0xa456;
            var provider = TestRetainedPlaneDescriptors.features(foreground, background);
            level.zoneFeatureProvider = provider;
            level.parallaxManager.installContributedHandler(37, new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            level.parallaxManager.update(37, 0, level.camera, 8);
            var gpu = new com.openggf.graphics.TilemapGpuRenderer();
            var host = mock(com.openggf.graphics.GraphicsManager.class);
            when(host.getTilemapGpuRenderer()).thenReturn(gpu);
            level.graphicsManager = host;
            var romLevel = mock(Level.class);
            when(romLevel.getPatternCount()).thenReturn(8);
            var nativeTiles = new LevelTilemapManager(new LevelGeometry(romLevel,
                    16, 16, 16, 16, 16, 16, 1), host, null);
            level.tilemapManager = nativeTiles;
            byte[] nativeBytes = new byte[64 * 32 * 4];
            gpu.setBackgroundTilemapDataPhysical(nativeBytes, 64, 32, 12, 8);
            var nativeGpu = gpu.captureBackgroundRenderState();
            var camera = level.camera.capture();
            foreground.words[0] = 0x8888;
            background.revision = 0;
            clearInvocations(provider);
            int fgReads = foreground.reads, bgReads = background.reads;
            assertThrows(IllegalStateException.class,
                    () -> LevelSpritePresentation.withCinematicCamera(level, -32, 80, () -> {
                        assertNotSame(nativeTiles, level.tilemapManager);
                        assertEquals(128, level.tilemapManager.getForegroundTilemapWidthTiles());
                        assertEquals(64, level.tilemapManager.getBackgroundTilemapHeightTiles());
                        assertEquals(0xf123, TestRetainedPlaneDescriptors.word(
                                level.tilemapManager.getForegroundTilemapData(), 128, 0, 0));
                        assertEquals(0xa456, TestRetainedPlaneDescriptors.word(
                                level.tilemapManager.getBackgroundTilemapData(), 64, 0, 0));
                        assertTrue(gpu.hasBackgroundBaseline(level.tilemapManager.getBackgroundTilemapData(), 64, 64));
                        throw new IllegalStateException("abort private camera");
                    }));
            assertSame(focusedSprite, level.camera.getFocusedSprite());
            assertSame(nativeTiles, level.tilemapManager);
            assertNull(nativeTiles.getForegroundTilemapData());
            assertNull(nativeTiles.getBackgroundTilemapData());
            assertEquals(nativeGpu, gpu.captureBackgroundRenderState());
            assertTrue(gpu.hasBackgroundBaseline(nativeBytes, 64, 32));
            assertEquals(camera, level.camera.capture());
            assertEquals(fgReads, foreground.reads);
            assertEquals(bgReads, background.reads);
            var source = (com.openggf.game.internal.RetainedPlaneDescriptorSource) provider;
            verify(source, never()).foregroundPlane();
            verify(source, never()).backgroundPlane();
        } finally {
            level.graphicsManager = nativeGraphics;
            SessionManager.clear();
        }
    }

    @Test
    void entryModeResolvesOnceUnderNativeCameraWhileArenaAndBuildRemainPublicationFrozen() throws Exception {
        TestEnvironment.resetAll();
        var level = GameServices.level();
        var nativeGraphics = level.graphicsManager;
        try {
            level.gameModule = new com.openggf.game.sonic3k.Sonic3kGameModule();
            enableDirectedSnapshots(level);
            var camera = spy(level.camera);
            level.camera = camera;
            var focusedSprite = mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
            camera.setFocusedSprite(focusedSprite);
            doReturn((short) 800).when(camera).getWidth();
            camera.setX((short) 160); camera.setY((short) 80);
            camera.setXCopy((short) 160); camera.setYCopy((short) 80);
            camera.setShakeOffsets(7, 0);
            var foreground = new TestRetainedPlaneDescriptors.MutablePlane(32, 32);
            var background = new TestRetainedPlaneDescriptors.MutablePlane(32, 32);
            foreground.words[0] = 0xe123; background.words[0] = 0xa456;
            var provider = mock(ZoneFeatureProvider.class, withSettings().extraInterfaces(
                    com.openggf.game.internal.NativeArenaCameraFraming.class,
                    com.openggf.game.internal.RetainedPlaneDescriptorSource.class));
            var retained = (com.openggf.game.internal.RetainedPlaneDescriptorSource) provider;
            when(retained.foregroundPlane()).thenReturn(foreground);
            when(retained.backgroundPlane()).thenReturn(background);
            var framing = (com.openggf.game.internal.NativeArenaCameraFraming) provider;
            when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.of(240));
            level.zoneFeatureProvider = provider;
            level.objectManager = mock(com.openggf.level.objects.ObjectManager.class);
            when(level.objectManager.getFrameCounter()).thenReturn(11);
            level.verticalWrapEnabled = true;
            var handler = spy(new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            doReturn(7).when(handler).getShakeOffsetX();
            level.parallaxManager.installContributedHandler(37, handler);
            level.parallaxManager.update(37, 0, camera, 8);
            var nativeScroll = LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            var frozen = nativeScroll.backgroundBuildParameters();
            var renderer = spy(level.spritePresentationRenderer());
            renderer.spriteTables.prepare(new SpritePresentation.Frame(List.of()), nativeScroll);
            renderer.spriteTables.publish();
            doReturn(new SpritePresentation.Frame(List.of())).when(renderer).captureSceneSpriteTable(any());
            level.graphicsManager = mock(com.openggf.graphics.GraphicsManager.class);
            when(level.graphicsManager.isHeadlessMode()).thenReturn(true);
            level.tilemapManager = null;
            level.level = mock(Level.class); // Exercise the actual draw mode-selection branch, without GL.
            level.waterSystem = mock(WaterSystem.class);
            var modeField = LevelRenderer.class.getDeclaredField("currentAdvancedRenderFrameState");
            modeField.setAccessible(true);
            var operands = new java.util.ArrayList<int[]>();
            var seeding = new AtomicBoolean(true);
            var contributor = mock(com.openggf.game.render.AdvancedRenderMode.class);
            doAnswer(invocation -> {
                var context = invocation.getArgument(0, com.openggf.game.render.AdvancedRenderModeContext.class);
                assertSame(camera, context.camera());
                operands.add(new int[]{context.frameCounter(), context.cameraX(),
                        context.camera().getX(), context.camera().getY()});
                var builder = invocation.getArgument(1, AdvancedRenderFrameState.Builder.class);
                if (seeding.get()) builder.setForegroundVScrollOverride((short) 17)
                        .setForegroundPerColumnVScrollOverride(new short[]{4, 9});
                else builder.setForegroundVScrollOverride((short) 41);
                return null;
            }).when(contributor).contribute(any(), any());
            var controller = GameServices.advancedRenderModeController();
            controller.clear(); controller.register(contributor);
            // Start with a real controller buffer. Later entry resolution reuses this
            // buffer, so restoration requires the renderer's existing owned mode copy.
            modeField.set(renderer, controller.resolve(new com.openggf.game.render.AdvancedRenderModeContext(
                    camera, 99, level, 37, 0, 160)));
            operands.clear(); seeding.set(false);
            foreground.words[0] = 0; background.revision = 0;
            when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.empty());
            when(provider.foregroundWrapsHorizontally()).thenReturn(true);
            camera.setMinX((short) 1000); camera.setMaxX((short) 900);
            level.verticalWrapEnabled = false;
            level.frameCounter = 99;
            when(level.objectManager.getFrameCounter()).thenReturn(77);
            var replacement = mock(ZoneFeatureProvider.class, withSettings().extraInterfaces(
                    com.openggf.game.internal.NativeArenaCameraFraming.class));
            when(((com.openggf.game.internal.NativeArenaCameraFraming) replacement)
                    .presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.of(900));
            when(replacement.foregroundWrapsHorizontally()).thenReturn(true);
            clearInvocations(provider, replacement, handler, level.objectManager);
            int fgReads = foreground.reads, bgReads = background.reads;
            var savedCamera = camera.capture();
            int entries = 0;
            for (var live : new ZoneFeatureProvider[]{provider, replacement, null}) {
                level.zoneFeatureProvider = live;
                int before = operands.size();
                renderer.withCinematicView(80, 20, 800, 300, () -> {
                    var full = cinematicScroll(renderer, "cinematicFullScroll");
                    assertFrozenArena(full, frozen, 153, 473);
                    assertEquals(87, full.registers().cameraXWithShake());
                    assertEquals(-19, full.registers().overriddenForegroundY());
                    renderer.withCinematicTile(17, 32, 600, 224, () -> {
                        var tile = cinematicScroll(renderer, "cinematicScroll");
                        assertFrozenArena(tile, frozen, 136, 456);
                        assertEquals(104, tile.registers().cameraXWithShake());
                        assertEquals(13, tile.registers().overriddenForegroundY());
                        if (live == null) renderer.drawCinematicWorld(GameServices.sprites());
                        assertEquals(before + 1, operands.size(), "Tiles and drawing never resolve another mode");
                    });
                    assertSame(focusedSprite, level.camera.getFocusedSprite());
                });
                assertSame(focusedSprite, level.camera.getFocusedSprite());
                assertEquals(++entries, operands.size());
                assertEquals(17, renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
                assertArrayEquals(new short[]{4, 9}, renderer.getCurrentAdvancedRenderFrameState()
                        .foregroundPerColumnVScrollOverride());
                renderer.withCinematicCamera(80, 20, () -> {
                    var directed = cinematicScroll(renderer, "cinematicScroll");
                    assertFrozenArena(directed, frozen, 153, 473);
                    assertEquals(87, directed.registers().cameraXWithShake());
                    assertEquals(-19, directed.registers().overriddenForegroundY());
                    if (live == null) renderer.drawCinematicWorld(GameServices.sprites());
                    assertEquals(before + 2, operands.size(), "Camera drawing keeps its entry-time mode");
                });
                assertSame(focusedSprite, level.camera.getFocusedSprite());
                assertEquals(++entries, operands.size());
                assertEquals(savedCamera, camera.capture());
                assertEquals(17, renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
                assertArrayEquals(new short[]{4, 9}, renderer.getCurrentAdvancedRenderFrameState()
                        .foregroundPerColumnVScrollOverride());
                assertSame(nativeScroll, renderer.spriteTables.publishedScroll(), "SAT display generation stays separate");
                assertFalse(renderer.spriteTables.publishedScroll().renderMode().hasForegroundVScrollOverride());
            }
            assertEquals(6, operands.size(), "One resolution for each full-view or camera entry, never zero");
            assertThrows(IllegalStateException.class, () -> renderer.withCinematicView(80, 20, 800, 300,
                    () -> { throw new IllegalStateException("abort full view"); }));
            assertSame(focusedSprite, level.camera.getFocusedSprite());
            assertEquals(7, operands.size());
            assertEquals(17, renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
            assertThrows(IllegalStateException.class, () -> renderer.withCinematicCamera(80, 20,
                    () -> { throw new IllegalStateException("abort camera"); }));
            assertSame(focusedSprite, level.camera.getFocusedSprite());
            assertEquals(8, operands.size());
            assertEquals(savedCamera, camera.capture());
            assertEquals(17, renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
            assertArrayEquals(new short[]{4, 9}, renderer.getCurrentAdvancedRenderFrameState()
                    .foregroundPerColumnVScrollOverride());
            verify(contributor, times(9)).contribute(any(), any()); // One explicit seed plus eight entries.
            for (int[] actual : operands) assertArrayEquals(new int[]{99, 160, 160, 80}, actual);
            assertEquals(fgReads, foreground.reads); assertEquals(bgReads, background.reads);
            verifyNoInteractions(provider, replacement, handler, level.objectManager);
        } finally {
            level.graphicsManager = nativeGraphics;
            SessionManager.clear();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "100,100,800,false,53,373", "100,100,400,false,53,373",
            "100,100,321,false,53,321", "100,100,320,false,0,320",
            "100,100,319,false,0,319", "120,100,800,false,0,800",
            "100,100,800,true,0,800"})
    void frozenArenaUsesDestinationShakeAndRasterWidthWithoutNormalizingDomains(
            int min, int max, int width, boolean wraps, int left, int right) {
        TestEnvironment.resetAll();
        try {
            var level = GameServices.level();
            level.gameModule = new com.openggf.game.sonic3k.Sonic3kGameModule();
            enableDirectedSnapshots(level);
            level.camera.setX((short) 160); level.camera.setXCopy((short) 160);
            level.camera.setShakeOffsets(7, 0);
            level.camera.setMinX((short) min); level.camera.setMaxX((short) max);
            level.frameCounter = 13; level.objectManager = null;
            var provider = mock(ZoneFeatureProvider.class);
            when(provider.foregroundWrapsHorizontally()).thenReturn(wraps);
            level.zoneFeatureProvider = provider;
            var handler = spy(new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            doReturn(7).when(handler).getShakeOffsetX();
            level.parallaxManager.installContributedHandler(37, handler);
            level.parallaxManager.update(37, 0, level.camera, 8);
            var captured = LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            level.zoneFeatureProvider = null;
            level.camera.setMinX((short) 0); level.camera.setMaxX((short) 3000);
            level.frameCounter = 88;
            clearInvocations(provider, handler);
            var view = captured.forCinematicView(40, 0, width, 224);
            var camera = captured.forCinematicCamera(40, 0, width);
            for (var sample : new LevelScrollPresentation[]{view, camera}) {
                assertEquals(left, sample.registers().arenaMask().left());
                assertEquals(right, sample.registers().arenaMask().right());
                assertEquals(13, sample.registers().arenaMask().noiseFrame());
                assertEquals(47, sample.registers().cameraXWithShake());
            }
            assertEquals(min, captured.backgroundBuildParameters().arenaBounds().minX());
            assertEquals(max, captured.backgroundBuildParameters().arenaBounds().maxX());
            verifyNoInteractions(provider, handler);
        } finally { SessionManager.clear(); }
    }

    private static LevelScrollPresentation cinematicScroll(LevelRenderer renderer, String name) {
        try {
            var field = LevelRenderer.class.getDeclaredField(name);
            field.setAccessible(true);
            return (LevelScrollPresentation) field.get(renderer);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void assertFrozenArena(LevelScrollPresentation sample, BackgroundBuildParameters frozen,
                                          int left, int right) {
        assertSame(frozen, sample.backgroundBuildParameters());
        assertEquals(left, sample.registers().arenaMask().left());
        assertEquals(right, sample.registers().arenaMask().right());
        assertEquals(11, sample.registers().arenaMask().noiseFrame());
        assertTrue(frozen.foregroundVerticalWrap());
        assertEquals(0xe123, frozen.retainedForeground().descriptorAt(0, 0));
        assertEquals(0xa456, frozen.retainedBackground().descriptorAt(0, 0));
    }

    private static void enableDirectedSnapshots(LevelManager level) {
        level.gameModule =
                new com.openggf.game.patch.DelegatingGameModule(level.gameModule, "directed-test") {
                    @Override
                    public <T> T getGameService(Class<T> type) {
                        if (type == com.openggf.level.scroll.DirectedScrollPresentation.class)
                            return type.cast(
                                    com.openggf.level.scroll.DirectedScrollPresentation.ENABLED);
                        return super.getGameService(type);
                    }
                };
    }

    private static ShortIndexedView values(int width, int offset) {
        return new ShortIndexedView() {
            @Override
            public int size() {
                return width;
            }

            @Override
            public short get(int index) {
                return (short) (index + offset);
            }
        };
    }
}
