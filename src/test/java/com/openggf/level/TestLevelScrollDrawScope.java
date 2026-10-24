package com.openggf.level;

import com.openggf.game.sonic3k.Sonic3kLevelInitProfile;
import com.openggf.graphics.*;
import com.openggf.level.render.BackgroundRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelScrollDrawScope {
    @Test void backgroundShapeAndNullCallbackRejectBeforeCacheOrPriorQueueChanges() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var old = cache.getBackgroundTilemapData().clone();
            var prior = new ArrayList<BackgroundObservation>();
            f.graphics.registerCommand(observeBackground(f, prior));
            for (int size : new int[] {0, 8191, 8193}) {
                assertThrows(IllegalArgumentException.class, () -> LevelSpritePresentation.withScrollAndBackground(
                        f.level, null, new byte[size], () -> fail("bad shape")));
            }
            assertThrows(IllegalArgumentException.class, () -> LevelSpritePresentation.withScrollAndBackground(
                    f.level, null, null, () -> fail("null image")));
            assertThrows(NullPointerException.class, () -> LevelSpritePresentation.withScrollAndBackground(
                    f.level, null, backgroundImage(42), null));
            assertArrayEquals(old, cache.getBackgroundTilemapData());
            assertTrue(prior.isEmpty(), "admission failure preserves prior queued work");
            assertEquals(0, f.resolutions.get());
            f.graphics.flush();
            assertEquals(List.of(new BackgroundObservation(0xff9cfff9, 11)), prior);
        }
    }

    @Test void nullBackgroundScopeCopiesColdCurrentViewWithoutPublishingOrAdvancingOwners() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var tables = f.renderer.spriteTables.capture();
            var camera = f.camera.capture();
            var mask = f.renderer.boundsMask.capture();
            int clock = f.level.frameCounter;
            var image = backgroundImage(42);
            var expected = image.clone();
            assertNull(tables.preparedScroll()); assertNull(tables.publishedScroll());
            LevelSpritePresentation.withScrollAndBackground(f.level, null, image, () -> {
                assertEquals(0xff9cfff9, selected(f));
                assertEquals(-17, LevelSpritePresentation.backgroundScrollForDraw(f.level).y());
                assertEquals(512, LevelSpritePresentation.backgroundScrollForDraw(f.level).period());
                assertEquals(211, f.renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
                image[0] = 99;
                f.horizontal[0] = 123;
                f.columns[0] = 123;
                assertEquals(0xff9cfff9, selected(f));
                assertArrayEquals(new short[] {-3, 12}, LevelSpritePresentation.backgroundScrollForDraw(f.level).columns());
                assertArrayEquals(expected, cache.getBackgroundTilemapData());
            });
            assertArrayEquals(expected, cache.getBackgroundTilemapData());
            assertNotSame(image, cache.getBackgroundTilemapData());
            assertEquals(64, cache.getBackgroundTilemapWidthTiles());
            assertEquals(32, cache.getBackgroundTilemapHeightTiles());
            assertEquals(32, cache.getBackgroundVdpWrapHeightTiles());
            assertEquals(tables, f.renderer.spriteTables.capture());
            assertEquals(camera, f.camera.capture()); assertEquals(mask, f.renderer.boundsMask.capture());
            assertEquals(clock, f.level.frameCounter);
            assertEquals(1, f.resolutions.get(), "one render-mode resolution, no public ScrollFrame capture");
            verify(f.parallax, never()).update(anyInt(), anyInt(), any(), anyInt());
            verifyNoInteractions(f.sprites);
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            assertEquals(123, selected(f));
        }
    }

    @Test void selectedBackgroundScopeDrainsOldImageThenCallbackCommandsWithFrozenScroll() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            var observations = new ArrayList<BackgroundObservation>();
            f.graphics.registerCommand(observeBackground(f, observations));
            // Stand in only for the normal batch endpoint: commands still use real queue ownership.
            doAnswer(call -> { f.graphics.registerCommand(observeBackground(f, observations)); return null; })
                    .when(f.graphics).flushPatternBatch();
            LevelSpritePresentation.withScrollAndBackground(f.level, frame, backgroundImage(42), () -> {
                assertEquals(List.of(new BackgroundObservation(99, 11), new BackgroundObservation(99, 11)), observations);
                assertEquals(0xff9cfff9, selected(f));
                f.graphics.registerCommand(observeBackground(f, observations));
            });
            assertEquals(List.of(new BackgroundObservation(99, 11), new BackgroundObservation(99, 11),
                    new BackgroundObservation(0xff9cfff9, 42), new BackgroundObservation(0xff9cfff9, 42)), observations);
            assertEquals(99, selected(f));
            assertEquals(1, f.resolutions.get(), "nonnull selection does not resolve live contributors");
            assertArrayEquals(backgroundImage(42), cache.getBackgroundTilemapData());
            f.graphics.flush(); assertEquals(4, observations.size(), "scope work cannot escape to a later flush");
        }
    }

    @Test void backgroundScopeRejectsIncompleteDetachedAndUninitializedOwnersWithoutMutation() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            f.level.generation = 0; rejectBackground(f, null, cache);
            f.level.generation = 1;
            f.world.setCurrentLevel(null); rejectBackground(f, null, cache);
            f.world.setCurrentLevel(f.terrain);
            f.level.parallaxManager = null; rejectBackground(f, null, cache);
            f.level.parallaxManager = f.parallax;
            f.level.gameModule = mock(com.openggf.game.GameModule.class); rejectBackground(f, null, cache);
            f.level.gameModule = f.module;
            f.level.tilemapManager = null; rejectBackground(f, null, cache);
            f.level.tilemapManager = cache;
            var atlas = f.graphics.getPatternAtlas();
            doReturn(null).when(f.graphics).getPatternAtlas(); rejectBackground(f, null, cache);
            doReturn(atlas).when(f.graphics).getPatternAtlas();
            f.graphics.setHeadlessMode(false); rejectBackground(f, null, cache);
            f.graphics.setHeadlessMode(true);
            LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(42), () -> { });
        }
    }

    @Test void backgroundScopeReusesFrameOwnerLoadAtlasViewportAndResetValidation() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var foreign = f.newLevel(new com.openggf.game.session.WorldSession(
                    f.module, f.module, mock(com.openggf.game.GameDataSource.class), null));
            assertThrows(IllegalArgumentException.class, () -> LevelSpritePresentation.withScrollAndBackground(
                    foreign, frame, backgroundImage(42), () -> fail("foreign")));
            f.level.generation++; rejectBackground(f, frame, cache); f.level.generation--;
            var atlas = f.graphics.getPatternAtlas();
            doReturn(new PatternAtlas(512, 512)).when(f.graphics).getPatternAtlas(); rejectBackground(f, frame, cache);
            doReturn(atlas).when(f.graphics).getPatternAtlas();
            when(f.config.getShort(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short) 400);
            f.camera.refreshViewportDimensions(f.config); rejectBackground(f, frame, cache);
            when(f.config.getShort(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short) 320);
            f.camera.refreshViewportDimensions(f.config);
            var rewind = f.renderer.spriteTables.capture();
            f.renderer.spriteTables.invalidateScrollForActReload(); f.renderer.spriteTables.restore(rewind);
            rejectBackground(f, frame, cache);
            var fresh = LevelSpritePresentation.captureScroll(f.level);
            f.renderer.resetState(); rejectBackground(f, fresh, cache);
            LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(42), () -> { });
        }
    }

    @Test void enclosingGraphicsCaptureRejectsBothBackgroundSelectionsBeforeCacheMutation() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var order = new ArrayList<BackgroundObservation>();
            f.graphics.registerCommand(observeBackground(f, order)); // ordinary work stays outside the capture
            f.graphics.executeCapturedCommands(() -> {
                f.graphics.registerCommand(observeBackground(f, order));
                rejectBackground(f, null, cache); rejectBackground(f, frame, cache);
                assertTrue(order.isEmpty());
                f.graphics.registerCommand(observeBackground(f, order));
            }, 0, 0, 320, 224);
            assertEquals(2, order.size());
            f.graphics.flush(); assertEquals(3, order.size());
            assertTrue(order.stream().allMatch(value -> value.image() == 11));
            LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(42), () -> { });
        }
    }

    @Test void scrollCinematicAndCpuPreparationRejectBackgroundEntryBeforeMutation() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var frame = LevelSpritePresentation.captureScroll(f.level);
            Runnable reject = () -> { rejectBackground(f, null, cache); rejectBackground(f, frame, cache); };
            LevelSpritePresentation.withScroll(f.level, frame, reject);
            LevelSpritePresentation.withCinematicCamera(f.level, 20, 30, reject);
            SpritePresentation.prepare(f.graphics, 0, 0, reject, value -> null);
            LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(42), () -> { });
        }
    }

    @Test void nullBackgroundResolverAndCallbackKeepAllRendererReentryGuards() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var probes = new java.util.concurrent.atomic.AtomicInteger();
            Runnable reject = () -> {
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.validateScroll(f.level, frame));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> fail("scroll")));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScrollAndBackground(
                        f.level, null, backgroundImage(99), () -> fail("background")));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withCinematicCamera(f.level, 1, 2, () -> fail("cinematic")));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.prepare(f.level, f.sprites));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.publishPreparedScene(f.level));
            };
            f.controller.register(new com.openggf.game.render.AdvancedRenderMode() {
                public String id() { return "background-reentry"; }
                public void contribute(com.openggf.game.render.AdvancedRenderModeContext context,
                                       com.openggf.game.render.AdvancedRenderFrameState.Builder builder) {
                    if (probes.incrementAndGet() > 1) return;
                    reject.run();
                    assertThrows(IllegalStateException.class, () -> f.renderer.drawWithRenderOptions(null,
                            LevelManager.LevelRenderOptions.tilesOnly()));
                    assertEquals(11, cache.getBackgroundTilemapData()[0]);
                }
            });
            LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(42), reject);
            assertEquals(1, probes.get());
            LevelSpritePresentation.validateScroll(f.level, frame);
        }
    }

    @Test void incompatibleBackgroundSourcesRejectInsteadOfOverridingTheirCacheContract() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            when(f.parallax.getBgPeriodWidth()).thenReturn(1024); rejectBackground(f, null, cache);
            when(f.parallax.getBgPeriodWidth()).thenReturn(512);
            when(f.parallax.getBgTilemapUpdateMode()).thenReturn(com.openggf.level.scroll.BgTilemapUpdateMode.PERSISTENT_NAMETABLE_64X32);
            rejectBackground(f, null, cache);
            when(f.parallax.getBgTilemapUpdateMode()).thenReturn(com.openggf.level.scroll.BgTilemapUpdateMode.STATIC_WINDOW);
            f.level.zoneFeatureProvider = mock(com.openggf.game.ZoneFeatureProvider.class); rejectBackground(f, null, cache);
            f.level.zoneFeatureProvider = null;
            var registry = new com.openggf.game.zone.ZoneRuntimeRegistry();
            f.services.when(com.openggf.game.GameServices::zoneRuntimeRegistryOrNull).thenReturn(registry);
            var runtime = mock(com.openggf.game.zone.ZoneRuntimeState.class);
            registry.install(runtime);
            when(runtime.requiresFullWidthBgTilemap()).thenReturn(true); rejectBackground(f, null, cache);
            when(runtime.requiresFullWidthBgTilemap()).thenReturn(false);
            when(runtime.usesPersistentBackgroundVdpPlane()).thenReturn(true); rejectBackground(f, null, cache);
            registry.clear();
            f.controller.register(new com.openggf.game.render.AdvancedRenderMode() {
                public String id() { return "reversed-background"; }
                public void contribute(com.openggf.game.render.AdvancedRenderModeContext context,
                                       com.openggf.game.render.AdvancedRenderFrameState.Builder builder) { builder.reversePlaneAssignment(); }
            });
            rejectBackground(f, null, cache);
            rejectBackground(f, LevelSpritePresentation.captureScroll(f.level), cache);
        }
    }

    @Test void backgroundInstallationSettlesOldBookkeepingAndSurvivesOrdinaryEnsure() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var registry = new com.openggf.game.zone.ZoneRuntimeRegistry();
            f.services.when(com.openggf.game.GameServices::zoneRuntimeRegistryOrNull).thenReturn(registry);
            var runtime = mock(com.openggf.game.zone.ZoneRuntimeState.class);
            when(runtime.requiresFullWidthBgTilemap()).thenReturn(true);
            registry.install(runtime);
            var descriptors = mock(com.openggf.game.ZoneFeatureProvider.class,
                    withSettings().extraInterfaces(com.openggf.game.internal.BackgroundDescriptorOverride.class));
            when(((com.openggf.game.internal.BackgroundDescriptorOverride) descriptors).backgroundDescriptorRevision()).thenReturn(7L);
            cache.setBackgroundTilemapDirty(true);
            cache.ensureBackgroundTilemapData((layer, x, y) -> null, descriptors, 0, f.parallax, false);
            cache.ensureBackgroundTilemapData((layer, x, y) -> null, null, 0, f.parallax,
                    com.openggf.level.scroll.BgTilemapUpdateMode.PERSISTENT_NAMETABLE_64X32, 32, 48, false);
            registry.clear();
            cache.requestBgWindowBaseX(512); cache.setCurrentBgPeriodWidth(1024); cache.setBgLoopBandBaseY(512);
            var expected = backgroundImage(42);
            LevelSpritePresentation.withScrollAndBackground(f.level, null, expected, () -> {
                assertEquals(0, cache.getBackgroundTilemapSourceX()); assertEquals(0, cache.getBackgroundTilemapSourceY());
                assertEquals(512, cache.getCurrentBgPeriodWidth()); assertEquals(-1, cache.getBgLoopBandBaseY());
                assertFalse(cache.isBackgroundTilemapDirty()); assertFalse(cache.isPersistentBgBaselineValid());
                var retained = cache.getBackgroundTilemapData();
                cache.ensureBackgroundTilemapData((layer, x, y) -> { fail("settled image must not rebuild layout"); return null; },
                        null, 0, f.parallax, false);
                assertSame(retained, cache.getBackgroundTilemapData());
                assertArrayEquals(expected, retained);
            });
            cache.ensureBackgroundTilemapData((layer, x, y) -> { fail("later ensure must retain selected bytes"); return null; },
                    null, 0, f.parallax, false);
            assertArrayEquals(expected, cache.getBackgroundTilemapData());
            assertEquals(32, cache.getBackgroundVdpWrapHeightTiles());
        }
    }

    @Test void backgroundCallbackFailureClosesOwnedBatchAndRestoresViewModeTablesAndGuard() {
        try (var f = new ScrollFixture()) {
            backgroundCache(f);
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var tables = f.renderer.spriteTables.capture();
            f.horizontal[0] = 99;
            var observations = new ArrayList<BackgroundObservation>();
            var primary = new IllegalArgumentException("background callback");
            var cleanup = new IllegalStateException("batch close");
            assertSame(primary, assertThrows(IllegalArgumentException.class, () ->
                    LevelSpritePresentation.withScrollAndBackground(f.level, frame, backgroundImage(42), () -> {
                        f.graphics.registerCommand(observeBackground(f, observations));
                        doAnswer(call -> {
                            f.graphics.registerCommand(observeBackground(f, observations));
                            throw cleanup;
                        }).when(f.graphics).flushPatternBatch();
                        throw primary;
                    })));
            assertArrayEquals(new Throwable[] {cleanup}, primary.getSuppressed());
            assertEquals(List.of(new BackgroundObservation(0xff9cfff9, 42), new BackgroundObservation(0xff9cfff9, 42)), observations);
            doCallRealMethod().when(f.graphics).flushPatternBatch();
            assertEquals(99, selected(f)); assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            assertEquals(tables, f.renderer.spriteTables.capture());
            LevelSpritePresentation.withScrollAndBackground(f.level, frame, backgroundImage(43), () -> { });
            f.graphics.flush(); assertEquals(2, observations.size());
        }
    }

    @Test void backgroundCommandDiscardFailureVisitsTailOnceAndRestoresSelectionForRecovery() {
        try (var f = new ScrollFixture()) {
            backgroundCache(f);
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            var observations = new ArrayList<BackgroundObservation>();
            var primary = new IllegalStateException("scoped discard");
            assertSame(primary, assertThrows(IllegalStateException.class, () ->
                    LevelSpritePresentation.withScrollAndBackground(f.level, frame, backgroundImage(42), () -> {
                        f.graphics.registerCommand(new GLCommandable() {
                            public void execute(int x, int y, int w, int h) { fail("headless"); }
                            public void discard() { observations.add(new BackgroundObservation(selected(f), 42)); throw primary; }
                        });
                        f.graphics.registerCommand(observeBackground(f, observations));
                    })));
            assertEquals(List.of(new BackgroundObservation(0xff9cfff9, 42), new BackgroundObservation(0xff9cfff9, 42)), observations);
            assertEquals(99, selected(f));
            LevelSpritePresentation.withScrollAndBackground(f.level, frame, backgroundImage(43), () -> { });
            f.graphics.flush(); assertEquals(2, observations.size());
        }
    }

    @Test void backgroundUploadFailureRestoresScopeAndAllowsAnotherInstallation() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            var tilemap = mock(TilemapGpuRenderer.class);
            doReturn(tilemap).when(f.graphics).getTilemapGpuRenderer();
            var failure = new IllegalStateException("upload endpoint");
            doAnswer(call -> { if (((byte[]) call.getArgument(1))[0] == 42) throw failure; return null; })
                    .when(tilemap).setTilemapData(eq(TilemapGpuRenderer.Layer.BACKGROUND), any(byte[].class), anyInt(), anyInt());
            assertSame(failure, assertThrows(IllegalStateException.class, () ->
                    LevelSpritePresentation.withScrollAndBackground(f.level, frame, backgroundImage(42), () -> fail("failed upload"))));
            assertEquals(99, selected(f));
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            // The failed upload retains its derived image; recover the endpoint before retrying installation.
            doNothing().when(tilemap).setTilemapData(eq(TilemapGpuRenderer.Layer.BACKGROUND),
                    any(byte[].class), anyInt(), anyInt());
            LevelSpritePresentation.withScrollAndBackground(f.level, frame, backgroundImage(43), () -> { });
            assertArrayEquals(backgroundImage(43), cache.getBackgroundTilemapData());
        }
    }

    @Test void callbackAndCommandDrainLifetimeChangesRejectAndReleaseOwnedWork() {
        for (boolean selectedFrame : new boolean[] {false, true}) {
            for (int boundary = 0; boundary < 3; boundary++) {
                try (var f = new ScrollFixture()) {
                    var cache = backgroundCache(f);
                    var frame = selectedFrame ? LevelSpritePresentation.captureScroll(f.level) : null;
                    var observations = new ArrayList<BackgroundObservation>();
                    int point = boundary;
                    if (point == 0) {
                        f.graphics.registerCommand(new GLCommandable() {
                            public void execute(int x, int y, int w, int h) { fail("headless"); }
                            public void discard() { f.level.generation++; }
                        });
                    }
                    assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScrollAndBackground(
                            f.level, frame, backgroundImage(42), () -> {
                                assertNotEquals(0, point, "old work invalidated load before installation");
                                f.graphics.registerCommand(observeBackground(f, observations));
                                if (point == 1) f.renderer.spriteTables.invalidateScrollForActReload();
                                else f.graphics.registerCommand(new GLCommandable() {
                                    public void execute(int x, int y, int w, int h) { fail("headless"); }
                                    public void discard() { f.level.generation++; }
                                });
                            }));
                    assertEquals(point == 0 ? 11 : 42, cache.getBackgroundTilemapData()[0]);
                    assertEquals(point == 0 ? 0 : 1, observations.size());
                    LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(43), () -> { });
                    f.graphics.flush(); assertEquals(point == 0 ? 0 : 1, observations.size());
                }
            }
        }
    }

    @Test void nullBackgroundResolverFailurePreservesCacheAndRestoresScopeWithoutPublication() {
        try (var f = new ScrollFixture()) {
            var cache = backgroundCache(f);
            var tables = f.renderer.spriteTables.capture();
            var failure = new IllegalStateException("background resolver");
            f.controller.register(new com.openggf.game.render.AdvancedRenderMode() {
                public String id() { return "failing-background"; }
                public void contribute(com.openggf.game.render.AdvancedRenderModeContext context,
                                       com.openggf.game.render.AdvancedRenderFrameState.Builder builder) { throw failure; }
            });
            assertSame(failure, assertThrows(IllegalStateException.class, () ->
                    LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(42), () -> fail("resolver failed"))));
            assertArrayEquals(backgroundImage(11), cache.getBackgroundTilemapData());
            assertEquals(tables, f.renderer.spriteTables.capture());
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            f.controller.clear();
            LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(42), () -> { });
        }
    }

    @Test void backgroundCallbackRejectsReplacedWorldModuleAtlasViewportCacheAndGraphicsOwners() {
        for (int replacement = 0; replacement < 6; replacement++) {
            try (var f = new ScrollFixture()) {
                var cache = backgroundCache(f);
                var atlas = f.graphics.getPatternAtlas();
                var observations = new ArrayList<BackgroundObservation>();
                int kind = replacement;
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScrollAndBackground(
                        f.level, null, backgroundImage(42), () -> {
                            f.graphics.registerCommand(new GLCommandable() {
                                public void execute(int x, int y, int w, int h) { fail("headless"); }
                                public void discard() { observations.add(new BackgroundObservation(selected(f), cache.getBackgroundTilemapData()[0])); }
                            });
                            switch (kind) {
                                case 0 -> f.world.setCurrentLevel(mock(Level.class));
                                case 1 -> f.level.gameModule = mock(com.openggf.game.GameModule.class);
                                case 2 -> doReturn(new PatternAtlas(512, 512)).when(f.graphics).getPatternAtlas();
                                case 3 -> {
                                    when(f.config.getShort(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short) 400);
                                    f.camera.refreshViewportDimensions(f.config);
                                }
                                case 4 -> f.level.tilemapManager = mock(LevelTilemapManager.class);
                                case 5 -> f.level.graphicsManager = new GraphicsManager();
                                default -> fail("unknown replacement");
                            }
                        }));
                f.world.setCurrentLevel(f.terrain); f.level.gameModule = f.module;
                doReturn(atlas).when(f.graphics).getPatternAtlas();
                when(f.config.getShort(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short) 320);
                f.camera.refreshViewportDimensions(f.config); f.level.tilemapManager = cache; f.level.graphicsManager = f.graphics;
                LevelSpritePresentation.withScrollAndBackground(f.level, null, backgroundImage(43), () -> { });
                assertArrayEquals(backgroundImage(43), cache.getBackgroundTilemapData());
                f.graphics.flush();
                assertEquals(List.of(new BackgroundObservation(0xff9cfff9, 42)), observations,
                        "owned work is released in the selected view despite owner replacement");
            }
        }
    }

    private static LevelTilemapManager backgroundCache(ScrollFixture f) {
        when(f.parallax.getBgTilemapUpdateMode()).thenReturn(com.openggf.level.scroll.BgTilemapUpdateMode.STATIC_WINDOW);
        f.services.when(com.openggf.game.GameServices::zoneRuntimeRegistryOrNull)
                .thenReturn(new com.openggf.game.zone.ZoneRuntimeRegistry());
        when(f.terrain.getMap()).thenReturn(new Map(2, 4, 2));
        f.level.blockPixelSize = 128;
        f.level.cachedBgWidthPx = 512; f.level.cachedBgHeightPx = 256;
        var cache = new LevelTilemapManager(new LevelGeometry(f.terrain, 512, 256, 512, 512, 256, 128, 8), f.graphics, null);
        f.level.tilemapManager = cache;
        cache.restoreRetainedBackgroundTilemapData(backgroundImage(11));
        return cache;
    }

    private static byte[] backgroundImage(int firstPattern) {
        var image = new byte[8192];
        for (int i = 3; i < image.length; i += 4) image[i] = (byte) 255;
        image[0] = (byte) firstPattern;
        return image;
    }

    private static void rejectBackground(ScrollFixture f, LevelSpritePresentation.ScrollFrame frame, LevelTilemapManager cache) {
        var before = cache.getBackgroundTilemapData().clone();
        int base = cache.getBgTilemapBaseX(), period = cache.getCurrentBgPeriodWidth(), band = cache.getBgLoopBandBaseY();
        assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScrollAndBackground(
                f.level, frame, backgroundImage(42), () -> fail("rejected background")));
        assertArrayEquals(before, cache.getBackgroundTilemapData());
        assertEquals(base, cache.getBgTilemapBaseX()); assertEquals(period, cache.getCurrentBgPeriodWidth());
        assertEquals(band, cache.getBgLoopBandBaseY());
    }

    private record BackgroundObservation(int scroll, int image) { }

    private static GLCommandable observeBackground(ScrollFixture f, List<BackgroundObservation> observations) {
        return new GLCommandable() {
            public void execute(int x, int y, int w, int h) { fail("CPU owner test must remain headless"); }
            public void discard() { observations.add(new BackgroundObservation(selected(f), f.level.tilemapManager.getBackgroundTilemapData()[0])); }
        };
    }

    @Test void queuedTerrainKeepsItsOwnModeAcrossCaptureBufferReuse() {
        retainedTerrainSurvivesCaptures(false);
    }

    @Test void queuedTerrainKeepsItsOwnModeAfterLaterResolverFailure() {
        retainedTerrainSurvivesCaptures(true);
    }

    private static void retainedTerrainSurvivesCaptures(boolean failAfterReuse) {
        try (var f = new ScrollFixture()) {
            var reversed = new java.util.concurrent.atomic.AtomicBoolean();
            var fail = new java.util.concurrent.atomic.AtomicBoolean();
            var failure = new IllegalStateException("contributor after buffer reuse");
            f.controller.register(new com.openggf.game.render.AdvancedRenderMode() {
                public String id() { return "changing-queued-mode"; }
                public void contribute(com.openggf.game.render.AdvancedRenderModeContext context,
                                       com.openggf.game.render.AdvancedRenderFrameState.Builder builder) {
                    if (reversed.get()) builder.reversePlaneAssignment();
                    if (fail.get()) throw failure;
                }
            });
            var terrain = new RetainedTerrain(f);
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly()); // controller A
            assertFalse(terrain.commands.isEmpty());
            assertTrue(terrain.passes.isEmpty(), "actual wrappers must remain unexecuted");
            clearInvocations(f.graphics);
            reversed.set(true);
            f.overrideColumns[0] = 81;
            var first = LevelSpritePresentation.captureScroll(f.level); // controller B
            f.overrideColumns[0] = 93;
            var second = LevelSpritePresentation.captureScroll(f.level); // reuses controller A
            if (failAfterReuse) {
                fail.set(true);
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> LevelSpritePresentation.captureScroll(f.level)));
            }
            verify(f.graphics, never()).flush();
            verify(f.graphics, never()).executeCapturedCommands(any(), anyInt(), anyInt(), anyInt(), anyInt());
            assertTrue(terrain.passes.isEmpty());
            terrain.execute();
            assertEquals(List.of(new PlanePass(TilemapGpuRenderer.Layer.BACKGROUND, 0, -32, -2),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 103, 211, 0),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 103, 211, 1)), terrain.passes);
            assertEquals(List.of(List.of((short) -4, (short) 7), List.of((short) -4, (short) 7)), terrain.columns,
                    "both queued foreground priorities retain the old mode's columns");
            LevelSpritePresentation.withScroll(f.level, first, () -> {
                assertTrue(f.renderer.getCurrentAdvancedRenderFrameState().reversePlaneAssignment());
                assertArrayEquals(new short[] {81, 7}, f.renderer.getCurrentAdvancedRenderFrameState().foregroundPerColumnVScrollOverride());
            });
            LevelSpritePresentation.withScroll(f.level, second, () -> {
                assertTrue(f.renderer.getCurrentAdvancedRenderFrameState().reversePlaneAssignment());
                assertArrayEquals(new short[] {93, 7}, f.renderer.getCurrentAdvancedRenderFrameState().foregroundPerColumnVScrollOverride());
            });
        }
    }

    @Test void cinematicSetupRejectsScrollReentryButItsOwnCallbackCanDraw() {
        cinematicSetupRejectsReentry(false);
    }

    @Test void cinematicResolverFailureRestoresCameraSelectionTablesAndScopeGuard() {
        cinematicSetupRejectsReentry(true);
    }

    private static void cinematicSetupRejectsReentry(boolean failResolution) {
        try (var f = new ScrollFixture()) {
            when(f.module.getLevelInitProfile()).thenReturn(new Sonic3kLevelInitProfile(null));
            var old = LevelScrollPresentation.capture(f.level, f.renderer.getCurrentAdvancedRenderFrameState());
            f.renderer.spriteTables.prepare(SpritePresentation.Frame.empty(), old);
            f.renderer.spriteTables.publish();
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            f.horizontal[0] = 99;
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var camera = f.camera.capture();
            var tables = f.renderer.spriteTables.capture();
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            int resolutionsBefore = f.resolutions.get();
            var failure = new IllegalStateException("cinematic resolver failed");
            f.controller.register(new com.openggf.game.render.AdvancedRenderMode() {
                public String id() { return "cinematic-setup-probe"; }
                public void contribute(com.openggf.game.render.AdvancedRenderModeContext context,
                                       com.openggf.game.render.AdvancedRenderFrameState.Builder builder) {
                    if (calls.incrementAndGet() > 1) return; // bounded even on the broken nested resolver
                    assertAll(
                            () -> assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.validateScroll(f.level, frame)),
                            () -> assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level)),
                            () -> assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> fail("nested draw"))),
                            () -> assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withCinematicCamera(f.level, 1, 2, () -> fail("nested cinematic"))),
                            () -> assertThrows(IllegalStateException.class, () -> f.renderer.drawCinematicWorld(null)));
                    if (failResolution) throw failure;
                }
            });
            clearInvocations(f.graphics);
            var callback = new java.util.concurrent.atomic.AtomicBoolean();
            Runnable draw = () -> {
                callback.set(true);
                f.renderer.drawCinematicWorld(null);
                assertEquals(140, f.camera.getX());
                assertEquals(168, f.camera.getY());
                assertEquals(179, f.renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
                f.graphics.flush();
            };
            if (failResolution) {
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> LevelSpritePresentation.withCinematicCamera(f.level, 140, 168, draw)));
                assertFalse(callback.get());
                verify(f.graphics, never()).flush();
            } else {
                LevelSpritePresentation.withCinematicCamera(f.level, 140, 168, draw);
                assertTrue(callback.get());
                verify(f.graphics, times(1)).flush();
            }
            verify(f.graphics, never()).executeCapturedCommands(any(), anyInt(), anyInt(), anyInt(), anyInt());
            assertEquals(1, calls.get());
            assertEquals(resolutionsBefore + 1, f.resolutions.get());
            assertEquals(camera, f.camera.capture());
            assertEquals(tables, f.renderer.spriteTables.capture());
            assertEquals(0xff9cfff9, selected(f));
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            LevelSpritePresentation.validateScroll(f.level, frame);
        }
    }

    @Test void scopeOwnsCommandsUsesCurrentGraphicsCameraAndRestoresLiveSelection() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            f.camera.setX((short) 500); f.camera.setY((short) 600);
            var camera = f.camera.capture();
            var tables = f.renderer.spriteTables.capture();
            var discarded = new ArrayList<Integer>();
            f.graphics.registerCommand(observeDiscard(f, discarded));
            LevelSpritePresentation.withScroll(f.level, frame, () -> {
                assertEquals(0xff9cfff9, selected(f));
                assertEquals(211, f.renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
                f.graphics.registerCommand(observeDiscard(f, discarded));
            });
            assertEquals(List.of(99, 0xff9cfff9), discarded);
            verify(f.graphics).executeCapturedCommands(any(), eq(503), eq(598), eq(320), eq(224));
            assertEquals(99, selected(f));
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            assertEquals(camera, f.camera.capture());
            assertEquals(tables, f.renderer.spriteTables.capture());
            assertEquals(1, f.resolutions.get());
            f.graphics.flush();
            assertEquals(2, discarded.size(), "owned commands cannot escape to the next flush");
        }
    }

    @Test void nestedScopeCaptureCinematicAndNativePreparationRejectWithoutDamagingOuterScope() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            LevelSpritePresentation.withScroll(f.level, frame, () -> {
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> fail("nested")));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.validateScroll(f.level, frame));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withCinematicCamera(f.level, 1, 2, () -> fail("cinematic")));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.prepare(f.level, f.sprites));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.publish(f.level,
                        com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL));
                assertThrows(IllegalStateException.class, () -> f.renderer.renderEndingBackground(3));
                assertEquals(0xff9cfff9, selected(f));
            });
            assertEquals(frame, LevelSpritePresentation.captureScroll(f.level));
        }
    }

    @Test void drawFailureDiscardsOwnedCommandsBeforeRestoringPriorPublishedSelection() {
        try (var f = new ScrollFixture()) {
            when(f.module.getLevelInitProfile()).thenReturn(new Sonic3kLevelInitProfile(null));
            var old = LevelScrollPresentation.capture(f.level, f.renderer.getCurrentAdvancedRenderFrameState());
            f.renderer.spriteTables.prepare(SpritePresentation.Frame.empty(), old);
            f.renderer.spriteTables.publish();
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            f.horizontal[0] = 77;
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var before = f.renderer.spriteTables.capture();
            var discarded = new ArrayList<Integer>();
            var failure = new IllegalArgumentException("draw failed");
            assertSame(failure, assertThrows(IllegalArgumentException.class, () ->
                    LevelSpritePresentation.withScroll(f.level, frame, () -> {
                        f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
                        f.graphics.registerCommand(observeDiscard(f, discarded));
                        throw failure;
                    })));
            assertEquals(List.of(77), discarded);
            assertEquals(0xff9cfff9, selected(f));
            assertEquals(before, f.renderer.spriteTables.capture());
            LevelSpritePresentation.withScroll(f.level, frame, () -> assertEquals(77, selected(f)));
            f.graphics.flush();
            assertEquals(1, discarded.size());
        }
    }

    @Test void scopeNestedInsideGraphicsCaptureKeepsCommandsInTheirOwnGeneration() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            var discarded = new ArrayList<Integer>();
            f.graphics.executeCapturedCommands(() -> {
                f.graphics.registerCommand(observeDiscard(f, discarded));
                LevelSpritePresentation.withScroll(f.level, frame, () -> f.graphics.registerCommand(observeDiscard(f, discarded)));
                assertEquals(List.of(0xff9cfff9), discarded);
                f.graphics.registerCommand(observeDiscard(f, discarded));
            }, 0, 0, 320, 224);
            assertEquals(List.of(0xff9cfff9, 99, 99), discarded);
        }
    }

    @Test void entryDrainFailureAndExitBatchFailureReleaseSelectionAndScopeGuard() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            var discarded = new ArrayList<Integer>();
            f.graphics.registerCommand(observeDiscard(f, discarded));
            var entryFailure = new IllegalStateException("drain failure");
            doThrow(entryFailure).doCallRealMethod().when(f.graphics).flush();
            assertSame(entryFailure, assertThrows(IllegalStateException.class, () ->
                    LevelSpritePresentation.withScroll(f.level, frame, () -> fail("not entered"))));
            assertEquals(List.of(99), discarded);
            var exitFailure = new IllegalStateException("batch close failure");
            assertSame(exitFailure, assertThrows(IllegalStateException.class, () ->
                    LevelSpritePresentation.withScroll(f.level, frame, () -> {
                        f.graphics.registerCommand(observeDiscard(f, discarded));
                        doThrow(exitFailure).doCallRealMethod().when(f.graphics).flushPatternBatch();
                    })));
            assertEquals(List.of(99, 0xff9cfff9), discarded);
            assertEquals(99, selected(f));
            LevelSpritePresentation.withScroll(f.level, frame, () -> { });
            var drawFailure = new IllegalArgumentException("primary draw failure");
            var closeFailure = new IllegalStateException("secondary close failure");
            assertSame(drawFailure, assertThrows(IllegalArgumentException.class, () ->
                    LevelSpritePresentation.withScroll(f.level, frame, () -> {
                        doThrow(closeFailure).doCallRealMethod().when(f.graphics).flushPatternBatch();
                        throw drawFailure;
                    })));
            assertArrayEquals(new Throwable[] {closeFailure}, drawFailure.getSuppressed());
            LevelSpritePresentation.validateScroll(f.level, frame);
        }
    }

    @Test void lifecycleChangeInsideCallbackRejectsBeforeCommandsCanExecute() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var discarded = new ArrayList<Integer>();
            assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> {
                f.graphics.registerCommand(observeDiscard(f, discarded));
                f.renderer.spriteTables.invalidateScrollForActReload();
            }));
            assertEquals(List.of(0xff9cfff9), discarded);
            assertNotNull(LevelSpritePresentation.captureScroll(f.level));
        }
    }

    @Test void realTerrainPassesSelectSnapshotThenRestorePriorDrawAndOrdinaryLiveDrawing() {
        try (var f = new ScrollFixture()) {
            var tilemap = mock(TilemapGpuRenderer.class);
            when(tilemap.isBackgroundContentGenerationCurrent(anyInt())).thenReturn(true);
            when(tilemap.captureBackgroundRenderState()).thenReturn(new TilemapGpuRenderer.BackgroundRenderState(0, 0, 0));
            var background = mock(BackgroundRenderer.class);
            doReturn(tilemap).when(f.graphics).getTilemapGpuRenderer();
            doReturn(background).when(f.graphics).getBackgroundRenderer();
            doReturn(1).when(f.graphics).getPatternAtlasTextureId();
            doReturn(2).when(f.graphics).getCombinedPaletteTextureId();
            doReturn(null).when(f.graphics).getTilePriorityFBO();
            f.level.useShaderBackground = true;
            var passes = new ArrayList<PlanePass>();
            doAnswer(call -> {
                passes.add(new PlanePass(call.getArgument(0), call.getArgument(7), call.getArgument(8), call.getArgument(14)));
                return null;
            }).when(tilemap).render(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                    anyFloat(), anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyFloat());
            // Exercise actual frame commands against mocked GPU endpoints. Never run GL commands.
            doAnswer(call -> {
                GLCommandable command = call.getArgument(0);
                if (command instanceof LevelRenderer.FrameCommand) command.execute(503, 598, 320, 224);
                else call.callRealMethod();
                return null;
            }).when(f.graphics).registerCommand(any());
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            var beforeMode = f.renderer.getCurrentAdvancedRenderFrameState();
            int beforeY = beforeMode.foregroundVScrollOverride();
            var snapshot = LevelSpritePresentation.captureScroll(f.level);
            f.controller.clear();
            f.camera.setX((short) 500); f.camera.setY((short) 600);
            when(f.parallax.getVscrollFactorFG()).thenReturn((short) 601);
            when(f.parallax.getVscrollFactorBG()).thenReturn((short) 67);
            passes.clear();
            LevelSpritePresentation.withScroll(f.level, snapshot,
                    () -> f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly()));
            assertEquals(List.of(new PlanePass(TilemapGpuRenderer.Layer.BACKGROUND, 0, -32, -2),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 103, 211, 0),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 103, 211, 1)), passes);
            assertEquals(beforeY, f.renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
            passes.clear();
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            assertEquals(List.of(new PlanePass(TilemapGpuRenderer.Layer.BACKGROUND, 0, 64, -2),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 503, 601, 0),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 503, 601, 1)), passes);
        }
    }

    private record PlanePass(TilemapGpuRenderer.Layer source, float x, float y, int priority) { }

    /** Retain real terrain wrappers until after controller reuse; only GPU endpoints are replaced. */
    private static final class RetainedTerrain {
        final List<LevelRenderer.FrameCommand> commands = new ArrayList<>();
        final List<PlanePass> passes = new ArrayList<>();
        final List<List<Short>> columns = new ArrayList<>();
        RetainedTerrain(ScrollFixture f) {
            var tilemap = mock(TilemapGpuRenderer.class);
            when(tilemap.isBackgroundContentGenerationCurrent(anyInt())).thenReturn(true);
            when(tilemap.captureBackgroundRenderState()).thenReturn(new TilemapGpuRenderer.BackgroundRenderState(0, 0, 0));
            doReturn(tilemap).when(f.graphics).getTilemapGpuRenderer();
            doReturn(mock(BackgroundRenderer.class)).when(f.graphics).getBackgroundRenderer();
            doReturn(1).when(f.graphics).getPatternAtlasTextureId();
            doReturn(2).when(f.graphics).getCombinedPaletteTextureId();
            doReturn(null).when(f.graphics).getTilePriorityFBO();
            f.level.useShaderBackground = true;
            doAnswer(call -> {
                passes.add(new PlanePass(call.getArgument(0), call.getArgument(7), call.getArgument(8), call.getArgument(14)));
                return null;
            }).when(tilemap).render(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                    anyFloat(), anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyFloat());
            doAnswer(call -> {
                com.openggf.util.ShortIndexedView view = call.getArgument(0);
                var copy = new ArrayList<Short>();
                for (int i = 0; i < view.size(); i++) copy.add(view.get(i));
                columns.add(List.copyOf(copy));
                return null;
            }).when(tilemap).enablePerColumnVScroll(any(com.openggf.util.ShortIndexedView.class));
            doAnswer(call -> {
                GLCommandable command = call.getArgument(0);
                if (command instanceof LevelRenderer.FrameCommand frame) commands.add(frame);
                else command.discard();
                return null;
            }).when(f.graphics).registerCommand(any());
        }
        void execute() {
            try { for (var command : commands) command.execute(100, 200, 320, 224); }
            finally { commands.forEach(LevelRenderer.FrameCommand::discard); commands.clear(); }
        }
    }
    private static int selected(ScrollFixture f) { return LevelSpritePresentation.backgroundScrollForDraw(f.level).horizontal()[0]; }
    private static GLCommandable observeDiscard(ScrollFixture f, List<Integer> discarded) {
        return new GLCommandable() {
            public void execute(int x, int y, int w, int h) { fail("headless command must be discarded"); }
            public void discard() { discarded.add(selected(f)); }
        };
    }
}
