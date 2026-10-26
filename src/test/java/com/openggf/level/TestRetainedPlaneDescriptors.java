package com.openggf.level;

import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.internal.RetainedPlaneDescriptorSource;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.level.scroll.BgTilemapUpdateMode;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-free checks of the production tilemap cache, including native word packing. */
@Isolated
class TestRetainedPlaneDescriptors {
    private GraphicsManager graphics;
    private LevelTilemapManager manager;
    private int ordinaryReads;
    private final LevelTilemapManager.BlockLookup ordinary = (layer, x, y) -> {
        ordinaryReads++;
        return null;
    };

    @BeforeEach void setUp() {
        GraphicsManager.destroyForReinit();
        TestEnvironment.resetAll();
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        Level level = mock(Level.class);
        when(level.getMap()).thenReturn(new Map(2, 1, 1));
        when(level.getPatternCount()).thenReturn(8);
        // Deliberately much smaller than either retained plane, including BG data extent.
        manager = new LevelTilemapManager(new LevelGeometry(level, 16, 16, 16, 16, 16, 16, 1), graphics, null);
    }

    @AfterEach void tearDown() {
        GraphicsManager.destroyForReinit();
        TestEnvironment.resetAll();
    }

    @ParameterizedTest @CsvSource({"128,32", "64,64"})
    void exactGeometryAndAllNativeDescriptorBitsSurviveBothPlaneBuilds(int width, int height) {
        var plane = new MutablePlane(width, height);
        // Known native words span pattern high bits, every palette, both flips and priority.
        int[] nativeWords = {0x0123, 0x2ABC, 0x54DE, 0x7FFF, 0x8001, 0xA802, 0xD003, 0xF804};
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            plane.words[y * width + x] = nativeWords[(x + y * 3) % nativeWords.length];
        }
        var provider = features(plane, plane);
        ensure(provider);
        assertEquals(width, manager.getForegroundTilemapWidthTiles());
        assertEquals(height, manager.getForegroundTilemapHeightTiles());
        assertEquals(width, manager.getBackgroundTilemapWidthTiles());
        assertEquals(height, manager.getBackgroundTilemapHeightTiles());
        assertEquals(height, manager.getBackgroundVdpWrapHeightTiles(), "no actual-art height heuristic");
        assertEquals(0, ordinaryReads, "authoritative build cannot read the ordinary layout");
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int expected = nativeWords[(x + y * 3) % nativeWords.length];
            assertEquals(expected, word(manager.getForegroundTilemapData(), width, x, y));
            assertEquals(expected, word(manager.getBackgroundTilemapData(), width, x, y));
        }
        assertEquals(plane.words[width * height - 1], plane.descriptorAt(-1, -1));
        assertEquals(plane.words[0], plane.descriptorAt(width * 8, height * 8));
    }

    @Test void cameraWindowAndResidencyChangesCannotRewriteOwnerBuffers() {
        var fg = new MutablePlane(128, 32);
        var bg = new MutablePlane(64, 64);
        var provider = features(fg, bg);
        when(provider.foregroundWrapsHorizontally()).thenReturn(true);
        when(provider.bgWrapsHorizontally()).thenReturn(true);
        ensure(provider);
        byte[] oldFg = manager.getForegroundTilemapData(), oldBg = manager.getBackgroundTilemapData();
        byte[] expectedFg = oldFg.clone(), expectedBg = oldBg.clone();
        int fgReads = fg.reads, bgReads = bg.reads;
        for (int camera : new int[]{16, 512, -32, 1040}) {
            manager.setForegroundRingCamera(camera, 640);
            manager.requestBgWindowBaseX(camera);
            manager.setBgLoopBandBaseY(512);
            manager.setCurrentBgPeriodWidth(2048);
            manager.ensureForegroundTilemapData(ordinary, provider, 0, null, true);
            manager.ensureBackgroundTilemapData(ordinary, provider, 0, null,
                    BgTilemapUpdateMode.PERSISTENT_NAMETABLE_64X32, camera, camera, true);
            assertSame(oldFg, manager.getForegroundTilemapData());
            assertSame(oldBg, manager.getBackgroundTilemapData());
            assertArrayEquals(expectedFg, oldFg);
            assertArrayEquals(expectedBg, oldBg);
            assertEquals(0, manager.getBackgroundTilemapSourceX());
            assertEquals(0, manager.getBackgroundTilemapSourceY());
        }
        assertEquals(fgReads, fg.reads);
        assertEquals(bgReads, bg.reads);
        assertEquals(0, ordinaryReads);
    }

    @Test void revisionsPlaneAndSourceSwitchesAndDeactivationRebuildCorrectAuthority() {
        var fg = new MutablePlane(128, 32);
        var bg = new MutablePlane(64, 64);
        var provider = features(fg, bg);
        ensure(null);
        byte[] ordinaryFg = manager.getForegroundTilemapData().clone();
        byte[] ordinaryBg = manager.getBackgroundTilemapData().clone();
        ensure(provider);
        byte[] first = manager.getForegroundTilemapData();
        fg.words[0] = 0xF123;
        fg.revision++;
        ensure(provider);
        assertNotSame(first, manager.getForegroundTilemapData());
        assertEquals(0xF123, word(manager.getForegroundTilemapData(), 128, 0, 0));

        var replacement = new MutablePlane(64, 64);
        replacement.words[0] = 0xC456;
        when(((RetainedPlaneDescriptorSource) provider).foregroundPlane()).thenReturn(replacement);
        ensure(provider);
        assertEquals(64, manager.getForegroundTilemapWidthTiles());
        assertEquals(0xC456, word(manager.getForegroundTilemapData(), 64, 0, 0));
        // Same plane and revision under a different source is a new authority.
        var nextProvider = features(replacement, bg);
        byte[] beforeSwitch = manager.getBackgroundTilemapData();
        bg.words[0] = 0xB321;
        ensure(nextProvider);
        assertNotSame(beforeSwitch, manager.getBackgroundTilemapData());
        assertEquals(0xB321, word(manager.getBackgroundTilemapData(), 64, 0, 0));

        replacement.revision = 0;
        when(((RetainedPlaneDescriptorSource) nextProvider).backgroundPlane()).thenReturn(null);
        ensure(nextProvider);
        assertEquals(2, manager.getForegroundTilemapWidthTiles());
        assertEquals(2, manager.getBackgroundTilemapHeightTiles());
        assertArrayEquals(ordinaryFg, manager.getForegroundTilemapData());
        assertArrayEquals(ordinaryBg, manager.getBackgroundTilemapData());
        assertTrue(ordinaryReads > 0);
    }

    @Test void rewindDerivesImagesAgainFromRestoredOwnerEvenWhenRevisionIsReused() {
        var plane = new MutablePlane(64, 64);
        var provider = features(plane, plane);
        ensure(provider);
        byte[] before = manager.getBackgroundTilemapData();
        plane.words[0] = 0xF765;
        // A restored branch may reuse revision 1 with a different descriptor image.
        manager.resetTilemapsForRewindRestore();
        ensure(provider);
        assertNotSame(before, manager.getBackgroundTilemapData());
        assertEquals(0xF765, word(manager.getBackgroundTilemapData(), 64, 0, 0));
        assertEquals(0xF765, word(manager.getForegroundTilemapData(), 64, 0, 0));
        assertFalse(manager.capturePersistentBgNametableSnapshot().baselineValid(), "no second retained authority");
    }

    @Test void retainedBackgroundRewindAlsoInvalidatesLegacyForegroundOwner() {
        var provider = mock(ZoneFeatureProvider.class, withSettings().extraInterfaces(
                RetainedPlaneDescriptorSource.class,
                com.openggf.game.internal.ForegroundDescriptorOverride.class));
        var source = (RetainedPlaneDescriptorSource) provider;
        var legacy = (com.openggf.game.internal.ForegroundDescriptorOverride) provider;
        when(source.backgroundPlane()).thenReturn(new MutablePlane(64, 64));
        when(legacy.foregroundDescriptorRevision()).thenReturn(1L);
        when(legacy.foregroundDescriptorAt(anyInt(), anyInt())).thenReturn(0x8123);
        ensure(provider);
        assertEquals(0x8123, word(manager.getForegroundTilemapData(), 2, 0, 0));
        when(legacy.foregroundDescriptorAt(anyInt(), anyInt())).thenReturn(0xF456);
        manager.resetTilemapsForRewindRestore();
        ensure(provider);
        assertEquals(0xF456, word(manager.getForegroundTilemapData(), 2, 0, 0));
    }

    @Test void artLookupRefreshPublishesWithoutRewritingUnchangedRetainedPlanes() {
        var gpu = spy(new TilemapGpuRenderer());
        var graphics = mock(GraphicsManager.class);
        when(graphics.getTilemapGpuRenderer()).thenReturn(gpu);
        Level level = mock(Level.class);
        when(level.getPatternCount()).thenReturn(8);
        manager = new LevelTilemapManager(new LevelGeometry(level, 16, 16, 16, 16, 16, 16, 1), graphics, null);
        var plane = new MutablePlane(64, 64);
        var provider = features(plane, plane);
        ensure(provider);
        byte[] foreground = manager.getForegroundTilemapData(), background = manager.getBackgroundTilemapData();
        byte[] oldLookup = manager.getPatternLookupData();
        int reads = plane.reads;
        clearInvocations(gpu);
        when(graphics.getPatternAtlasEntry(1)).thenReturn(new PatternAtlas.Entry(1, 0, 5, 7, 9, 0, 0, 1, 1));
        manager.invalidateAllTilemaps();
        ensure(provider);
        byte[] lookup = manager.getPatternLookupData();
        assertNotSame(oldLookup, lookup);
        assertEquals(7, lookup[4]);
        assertEquals(9, lookup[5]);
        verify(gpu).setPatternLookupData(same(lookup), eq(8));
        verify(gpu, never()).setTilemapData(any(), any(), anyInt(), anyInt());
        assertSame(foreground, manager.getForegroundTilemapData());
        assertSame(background, manager.getBackgroundTilemapData());
        assertEquals(reads, plane.reads);
        clearInvocations(gpu);
        ensure(provider);
        verify(gpu, never()).setPatternLookupData(any(), anyInt());
    }

    @ParameterizedTest @CsvSource({"0,32", "32,-1", "3,32", "32,129", "256,16"})
    void invalidGeometryFailsBeforeDescriptorReadOrPartialCacheReplacement(int width, int height) {
        var plane = new MutablePlane(width, height);
        var provider = features(plane, plane);
        assertThrows(IllegalArgumentException.class, () -> ensure(provider));
        assertEquals(0, plane.reads);
        assertNull(manager.getForegroundTilemapData());
        assertNull(manager.getBackgroundTilemapData());
    }

    @Test void cinematicCaptureIgnoresLaterMutationReplacementAndDeactivationWithoutLiveReads() {
        var foreground = new MutablePlane(128, 32);
        var background = new MutablePlane(64, 64);
        foreground.words[0] = 0xf123;
        background.words[0] = 0xa456;
        var provider = features(foreground, background);
        var frozen = capture(provider);
        ensure(provider);
        byte[] nativeForeground = manager.getForegroundTilemapData();
        byte[] nativeBackground = manager.getBackgroundTilemapData();
        var privateTiles = manager.cinematicBackgroundCopy();
        privateTiles.setCinematicBackgroundParameters(frozen);
        foreground.words[0] = 0x8888;
        background.revision = 0;
        var replacement = new MutablePlane(32, 32);
        when(((RetainedPlaneDescriptorSource) provider).foregroundPlane()).thenReturn(replacement);
        clearInvocations(provider);
        int fgReads = foreground.reads, bgReads = background.reads;
        privateTiles.publishCinematicBackgroundBaseline();
        for (int camera : new int[]{-1040, -16, 512, 2048}) {
            privateTiles.setForegroundRingCamera(camera, 640);
            privateTiles.requestBgWindowBaseX(camera);
            privateTiles.ensureForegroundTilemapData(ordinary, provider, 0, null, true);
            privateTiles.ensureBackgroundTilemapData(ordinary, provider, 0, null,
                    BgTilemapUpdateMode.PERSISTENT_NAMETABLE_64X32, camera, camera, true);
            assertEquals(0xf123, word(privateTiles.getForegroundTilemapData(), 128, 0, 0));
            assertEquals(0xa456, word(privateTiles.getBackgroundTilemapData(), 64, 0, 0));
            assertEquals(128, privateTiles.getForegroundTilemapWidthTiles());
            assertEquals(64, privateTiles.getBackgroundTilemapHeightTiles());
            assertEquals(64, privateTiles.getBackgroundVdpWrapHeightTiles());
            assertEquals(0, privateTiles.getBackgroundTilemapSourceX());
            assertEquals(0, privateTiles.getBackgroundTilemapSourceY());
        }
        assertSame(nativeForeground, manager.getForegroundTilemapData());
        assertSame(nativeBackground, manager.getBackgroundTilemapData());
        assertEquals(fgReads, foreground.reads);
        assertEquals(bgReads, background.reads);
        assertEquals(0, replacement.reads);
        assertEquals(0, ordinaryReads);
        verifyNoInteractions(provider);
    }

    @ParameterizedTest @CsvSource({"true,false", "false,true", "false,false"})
    void cinematicInactivePlaneCannotAcquireANewLiveAuthority(boolean foregroundActive, boolean backgroundActive) {
        var foreground = new MutablePlane(128, 32);
        var background = new MutablePlane(64, 64);
        foreground.revision = foregroundActive ? 1 : 0;
        background.revision = backgroundActive ? 1 : 0;
        var provider = features(foreground, background);
        var frozen = capture(provider);
        // The live renderer acquires different active bytes after publication.
        foreground.revision = background.revision = 2;
        ensure(provider);
        var privateTiles = manager.cinematicBackgroundCopy();
        privateTiles.setCinematicBackgroundParameters(frozen);
        clearInvocations(provider);
        int fgReads = foreground.reads, bgReads = background.reads;
        privateTiles.publishCinematicBackgroundBaseline();
        privateTiles.ensureForegroundTilemapData(ordinary, provider, 0, null, false);
        privateTiles.ensureBackgroundTilemapData(ordinary, provider, 0, null, false);
        assertEquals(foregroundActive ? 128 : 2, privateTiles.getForegroundTilemapWidthTiles());
        assertEquals(foregroundActive ? 32 : 2, privateTiles.getForegroundTilemapHeightTiles());
        assertEquals(backgroundActive ? 64 : 2, privateTiles.getBackgroundTilemapWidthTiles());
        assertEquals(backgroundActive ? 64 : 2, privateTiles.getBackgroundTilemapHeightTiles());
        assertEquals(fgReads, foreground.reads);
        assertEquals(bgReads, background.reads);
        verifyNoInteractions(provider);
    }

    @Test void cinematicReusedRevisionReplacesAndRestoresImagesByFrozenGenerationIdentity() {
        var plane = new MutablePlane(64, 64);
        var provider = features(plane, plane);
        var first = capture(provider);
        plane.words[0] = 0xf765;
        var second = capture(provider);
        var privateTiles = manager.cinematicBackgroundCopy();
        for (var generation : new BackgroundBuildParameters[]{first, second, first}) {
            privateTiles.setCinematicBackgroundParameters(generation);
            privateTiles.publishCinematicBackgroundBaseline();
            int expected = generation == second ? 0xf765 : 1;
            assertEquals(expected, word(privateTiles.getForegroundTilemapData(), 64, 0, 0));
            assertEquals(expected, word(privateTiles.getBackgroundTilemapData(), 64, 0, 0));
        }
        assertEquals(first.retainedForeground().revision(), second.retainedForeground().revision());
        assertFalse(privateTiles.capturePersistentBgNametableSnapshot().baselineValid());
    }

    @Test void coldPrivateBaselinePublishesBothCompleteImagesBeforeAnyLegacyBuildAndRestoresNativeGpu() {
        var gpu = spy(new TilemapGpuRenderer());
        var host = mock(GraphicsManager.class);
        when(host.getTilemapGpuRenderer()).thenReturn(gpu);
        var level = mock(Level.class);
        when(level.getPatternCount()).thenReturn(8);
        var cold = new LevelTilemapManager(new LevelGeometry(level, 16, 16, 16, 16, 16, 16, 1), host, null);
        byte[] nativeForeground = new byte[64 * 32 * 4], nativeBackground = nativeForeground.clone();
        gpu.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, nativeForeground, 64, 32);
        gpu.setBackgroundTilemapDataPhysical(nativeBackground, 64, 32, 12, 8);
        var nativeState = gpu.captureBackgroundRenderState();
        var foreground = new MutablePlane(128, 32);
        var background = new MutablePlane(64, 64);
        foreground.words[0] = 0xf123;
        background.words[0] = 0xa456;
        var privateTiles = cold.cinematicBackgroundCopy();
        privateTiles.setCinematicBackgroundParameters(capture(features(foreground, background)));
        clearInvocations(gpu);
        com.openggf.graphics.TilemapGpuRendererInternalAccess.withCinematicBackground(gpu,
                new com.openggf.graphics.CinematicBackgroundResidency(), () -> {
                    privateTiles.publishCinematicBackgroundBaseline();
                    verify(gpu, atLeastOnce()).setTilemapData(eq(TilemapGpuRenderer.Layer.FOREGROUND),
                            same(privateTiles.getForegroundTilemapData()), eq(128), eq(32));
                    assertTrue(gpu.hasBackgroundBaseline(privateTiles.getBackgroundTilemapData(), 64, 64));
                    assertEquals(0, gpu.captureBackgroundRenderState().ringBaseXTiles());
                    assertEquals(0, gpu.captureBackgroundRenderState().ringBaseYTiles());
                    assertEquals(64f, gpu.getBgVdpWrapHeight());
                    assertEquals(0xf123, word(privateTiles.getForegroundTilemapData(), 128, 0, 0));
                    assertEquals(0xa456, word(privateTiles.getBackgroundTilemapData(), 64, 0, 0));
                });
        assertTrue(gpu.hasBackgroundBaseline(nativeBackground, 64, 32));
        assertEquals(nativeState, gpu.captureBackgroundRenderState());
        assertNull(cold.getForegroundTilemapData());
        assertNull(cold.getBackgroundTilemapData());
    }

    @ParameterizedTest @CsvSource({
            "false,false,false,false,false", "true,false,false,false,true",
            "false,true,false,false,false", "false,false,true,false,true",
            "false,true,true,false,false", "false,true,false,true,true"})
    void cinematicDeferredWrapUsesFrozenLayoutFlagAndTheSelectedPhysicalRetainedPlane(
            boolean nativeWrap, boolean reversed, boolean foregroundActive, boolean backgroundActive,
            boolean expected) {
        var level = com.openggf.game.GameServices.level();
        var focusedSprite = mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        level.camera.setFocusedSprite(focusedSprite);
        level.gameModule = new com.openggf.game.patch.DelegatingGameModule(
                new com.openggf.game.sonic3k.Sonic3kGameModule(), "wrap-test") {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == com.openggf.level.scroll.DirectedScrollPresentation.class)
                    return type.cast(com.openggf.level.scroll.DirectedScrollPresentation.ENABLED);
                return super.getGameService(type);
            }
        };
        var foreground = new MutablePlane(32, 32);
        var background = new MutablePlane(32, 32);
        foreground.revision = foregroundActive ? 1 : 0;
        background.revision = backgroundActive ? 1 : 0;
        var provider = features(foreground, background);
        level.zoneFeatureProvider = provider;
        level.verticalWrapEnabled = nativeWrap;
        level.parallaxManager.installContributedHandler(37, new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
        level.parallaxManager.update(37, 0, level.camera, 8);
        level.verticalWrapEnabled = !nativeWrap;
        foreground.revision = foregroundActive ? 0 : 1;
        background.revision = backgroundActive ? 0 : 1;
        level.zoneFeatureProvider = null;
        level.tilemapManager = null;
        level.graphicsManager = mock(GraphicsManager.class);
        var contributor = mock(com.openggf.game.render.AdvancedRenderMode.class);
        doAnswer(invocation -> {
            if (reversed) invocation.getArgument(1,
                    com.openggf.game.render.AdvancedRenderFrameState.Builder.class).reversePlaneAssignment();
            return null;
        }).when(contributor).contribute(any(), any());
        var controller = com.openggf.game.GameServices.advancedRenderModeController();
        controller.clear(); controller.register(contributor);
        var renderer = level.spritePresentationRenderer();
        var pool = new LevelRenderer.FrameCommandPool();
        clearInvocations(provider);
        renderer.withCinematicCamera(80, 20, () -> {
            var command = pool.obtain(renderer, LevelRenderer.FrameCommand.Kind.TEST);
            try { assertEquals(expected, command.verticalWrapForTesting()); }
            finally { command.discard(); }
        });
        assertSame(focusedSprite, level.camera.getFocusedSprite());
        verifyNoInteractions(provider);
        verify(contributor, times(1)).contribute(any(), any());
    }

    private static BackgroundBuildParameters capture(ZoneFeatureProvider provider) {
        var level = mock(LevelManager.class);
        level.zoneFeatureProvider = provider;
        level.camera = mock(com.openggf.camera.Camera.class);
        return BackgroundBuildParameters.capture(level);
    }

    private void ensure(ZoneFeatureProvider provider) {
        manager.ensureForegroundTilemapData(ordinary, provider, 0, null, false);
        manager.ensureBackgroundTilemapData(ordinary, provider, 0, null, false);
    }

    static ZoneFeatureProvider features(RetainedPlaneDescriptorSource.Plane foreground,
                                        RetainedPlaneDescriptorSource.Plane background) {
        var provider = mock(ZoneFeatureProvider.class, withSettings().extraInterfaces(RetainedPlaneDescriptorSource.class));
        var source = (RetainedPlaneDescriptorSource) provider;
        when(source.foregroundPlane()).thenReturn(foreground);
        when(source.backgroundPlane()).thenReturn(background);
        return provider;
    }

    static int word(byte[] bytes, int width, int x, int y) {
        int offset = (y * width + x) * 4;
        int flags = bytes[offset + 1] & 255;
        assertEquals(0, bytes[offset + 2]);
        assertEquals(255, bytes[offset + 3] & 255);
        return (bytes[offset] & 255) | ((flags & 7) << 8) | ((flags & 0x18) << 10)
                | ((flags & 0x60) << 6) | ((flags & 0x80) << 8);
    }

    static final class MutablePlane implements RetainedPlaneDescriptorSource.Plane {
        final int width, height;
        final int[] words;
        long revision = 1;
        int reads;
        MutablePlane(int width, int height) {
            this.width = width;
            this.height = height;
            words = new int[Math.max(1, width * height)];
            Arrays.fill(words, 1);
        }
        @Override public int widthTiles() { return width; }
        @Override public int heightTiles() { return height; }
        @Override public long revision() { return revision; }
        @Override public int descriptorAt(int pixelX, int pixelY) {
            reads++;
            return words[Math.floorMod(Math.floorDiv(pixelY, 8), height) * width
                    + Math.floorMod(Math.floorDiv(pixelX, 8), width)];
        }
    }
}
