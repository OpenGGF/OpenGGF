package com.openggf.level;

import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.internal.BackgroundBuildProjection;
import com.openggf.game.internal.BackgroundBuildProjectionSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestBackgroundBuildParameters {
    @ParameterizedTest @CsvSource({"128,32", "64,64"})
    void completeRetainedImagesOwnEveryNativeWordAndWrapNegativeCoordinates(int width, int height) {
        var foreground = new TestRetainedPlaneDescriptors.MutablePlane(width, height);
        var background = new TestRetainedPlaneDescriptors.MutablePlane(height, width);
        for (int i = 0; i < foreground.words.length; i++) {
            foreground.words[i] = (i * 257 + 0x8123) & 0xffff;
            background.words[i] = (i * 511 + 0xf456) & 0xffff;
        }
        var provider = TestRetainedPlaneDescriptors.features(foreground, background);
        var frozen = capture(provider);
        var fg = frozen.retainedForeground();
        var bg = frozen.retainedBackground();
        assertTrue(fg.active());
        assertTrue(bg.active());
        assertEquals(width, fg.widthTiles());
        assertEquals(height, fg.heightTiles());
        assertEquals(height, bg.widthTiles());
        assertEquals(width, bg.heightTiles());
        assertArrayEquals(foreground.words, fg.descriptors());
        assertArrayEquals(background.words, bg.descriptors());
        assertEquals(foreground.words[foreground.words.length - 1], fg.descriptorAt(-1, -1));
        assertEquals(background.words[background.words.length - 1], bg.descriptorAt(-8, -8));
        assertEquals(foreground.words[0], fg.descriptorAt(width * 8, height * 8));
        assertEquals(background.words[0], bg.descriptorAt(height * 8, width * 8));
        int expected = foreground.words[0];
        foreground.words[0] ^= 0xffff;
        int[] exported = fg.descriptors();
        exported[0] = 0;
        assertEquals(expected, fg.descriptorAt(0, 0));
        int[] input = new int[32 * 32];
        input[0] = 0xfedc;
        var owned = new BackgroundBuildParameters.RetainedImage(32, 32, 1, input);
        input[0] = 0;
        assertEquals(0xfedc, owned.descriptorAt(0, 0));
    }

    @ParameterizedTest @CsvSource({"0,32", "32,-1", "3,32", "32,129", "256,16"})
    void invalidEitherPlaneIsRejectedBeforeReadingAnyRetainedDescriptors(int width, int height) {
        var foreground = new TestRetainedPlaneDescriptors.MutablePlane(64, 32);
        var background = new TestRetainedPlaneDescriptors.MutablePlane(width, height);
        assertThrows(IllegalArgumentException.class,
                () -> capture(TestRetainedPlaneDescriptors.features(foreground, background)));
        assertEquals(0, foreground.reads);
        assertEquals(0, background.reads);
    }

    @Test void capturesInactivePlanesIndependentlyAndDoesNotRetainLiveOwners() {
        var foreground = new TestRetainedPlaneDescriptors.MutablePlane(128, 32);
        var background = new TestRetainedPlaneDescriptors.MutablePlane(64, 64);
        background.revision = 0;
        var provider = TestRetainedPlaneDescriptors.features(foreground, background);
        var frozen = capture(provider);
        clearInvocations(provider);
        foreground.words[0] = 0xfedc;
        foreground.revision = 0;
        background.revision = 1;
        when(((com.openggf.game.internal.RetainedPlaneDescriptorSource) provider).foregroundPlane())
                .thenReturn(new TestRetainedPlaneDescriptors.MutablePlane(32, 32));
        clearInvocations(provider);
        assertTrue(frozen.retainedForeground().active());
        assertEquals(1, frozen.retainedForeground().revision());
        assertEquals(1, frozen.retainedForeground().descriptorAt(0, 0));
        assertFalse(frozen.retainedBackground().active());
        assertEquals(0, frozen.retainedBackground().revision());
        assertThrows(IllegalStateException.class, () -> frozen.retainedBackground().descriptorAt(0, 0));
        assertEquals(0, background.reads);
        verifyNoInteractions(provider);
    }

    @Test void sameRevisionWithNewBytesHasANewFrozenGenerationAndWindowAccessorsOwnTheirArrays() {
        var plane = new TestRetainedPlaneDescriptors.MutablePlane(64, 32);
        var provider = TestRetainedPlaneDescriptors.features(plane, plane);
        int[] cells = {0xf123};
        when(provider.foregroundWindow()).thenReturn(new com.openggf.graphics.ForegroundWindow(1, 1, 0, cells));
        var first = capture(provider);
        plane.words[0] = 0xe456;
        cells[0] = 0;
        var second = capture(provider);
        assertEquals(first.retainedForeground().revision(), second.retainedForeground().revision());
        assertNotSame(first.retainedForeground(), second.retainedForeground());
        assertEquals(1, first.retainedForeground().descriptorAt(0, 0));
        assertEquals(0xe456, second.retainedForeground().descriptorAt(0, 0));
        first.foregroundWindow().descriptors()[0] = 0;
        assertEquals(0xf123, first.foregroundWindow().descriptors()[0]);
    }

    private static BackgroundBuildParameters capture(ZoneFeatureProvider provider) {
        var level = mock(LevelManager.class);
        level.zoneFeatureProvider = provider;
        level.camera = mock(com.openggf.camera.Camera.class);
        return BackgroundBuildParameters.capture(level);
    }

    @Test void eventForegroundSnapshotRejectsOversizedImageBeforeReadingDescriptors() {
        var provider = mock(ZoneFeatureProvider.class, withSettings().extraInterfaces(
                BackgroundBuildProjectionSource.class, com.openggf.game.internal.ForegroundDescriptorOverride.class));
        when(((BackgroundBuildProjectionSource) provider).captureBackgroundBuild())
                .thenReturn(new BackgroundBuildProjection(0, null, ignored -> 0));
        var foreground = (com.openggf.game.internal.ForegroundDescriptorOverride) provider;
        when(foreground.foregroundDescriptorRevision()).thenReturn(1L);
        var level = mock(LevelManager.class);
        level.zoneFeatureProvider = provider;
        level.cachedFgWidthPx = 65536; level.cachedFgHeightPx = 65536;
        var failure = assertThrows(IllegalStateException.class, () -> BackgroundBuildParameters.capture(level));
        assertTrue(failure.getMessage().contains("Directed foreground descriptor image exceeds bounded snapshot"));
        verify(foreground, never()).foregroundDescriptorAt(anyInt(), anyInt());
    }

    @Test void nativeCaptureFreezesEventGatesAndDescriptorInputsBeforeASeparateViewSamplesThem() {
        var changed = new AtomicBoolean();
        var provider = mock(ZoneFeatureProvider.class, withSettings().extraInterfaces(BackgroundBuildProjectionSource.class,
                com.openggf.game.internal.ForegroundDescriptorOverride.class));
        var source = (BackgroundBuildProjectionSource) provider;
        var foreground = (com.openggf.game.internal.ForegroundDescriptorOverride) provider;
        when(foreground.foregroundDescriptorRevision()).thenReturn(1L);
        when(foreground.foregroundDescriptorRingRows()).thenReturn(32);
        when(foreground.foregroundDescriptorAt(anyInt(), anyInt())).thenAnswer(ignored -> changed.get() ? 0x8123 : 0xe456);
        when(provider.bgWrapsHorizontally()).thenAnswer(ignored -> !changed.get());
        when(provider.backgroundLoopBandBaseY(0, 0)).thenAnswer(ignored -> changed.get() ? 512 : -1);
        when(source.captureBackgroundBuild()).thenAnswer(ignored -> {
            boolean state = changed.get();
            return new BackgroundBuildProjection(state ? 2 : 1, (x, y) -> state ? 0x8123 : 0xe456,
                    cameraX -> cameraX + (state ? 1000 : 2000));
        });
        var level = mock(LevelManager.class);
        level.zoneFeatureProvider = provider;
        level.camera = mock(com.openggf.camera.Camera.class);
        var frozen = BackgroundBuildParameters.capture(level);
        clearInvocations(provider);
        changed.set(true);
        assertTrue(frozen.wraps());
        assertEquals(1, frozen.foregroundRevision());
        assertEquals(32, frozen.foregroundRingRows());
        assertEquals(0xe456, frozen.foregroundDescriptors().applyAsInt(0, 16));
        assertEquals(-1, frozen.loopBandY());
        assertEquals(1, frozen.projection().descriptorRevision());
        assertEquals(0xe456, frozen.projection().descriptors().applyAsInt(10, 20));
        assertEquals(2032, frozen.projection().nametableBase().applyAsDouble(32));
        verifyNoInteractions(provider);
    }

    @Test void nativeArenaCaptureCarriesTheSingleResolvedWrapValueAndFreezesFramingAndClock() {
        var level = mock(LevelManager.class);
        level.camera = mock(com.openggf.camera.Camera.class);
        level.verticalWrapEnabled = true;
        level.frameCounter = 99;
        level.objectManager = mock(com.openggf.level.objects.ObjectManager.class);
        when(level.objectManager.getFrameCounter()).thenReturn(11);
        var provider = mock(ZoneFeatureProvider.class, withSettings().extraInterfaces(
                BackgroundBuildProjectionSource.class, com.openggf.game.internal.NativeArenaCameraFraming.class));
        when(((BackgroundBuildProjectionSource) provider).captureBackgroundBuild())
                .thenReturn(new BackgroundBuildProjection(0, null, ignored -> 0));
        var framing = (com.openggf.game.internal.NativeArenaCameraFraming) provider;
        when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.of(240));
        when(provider.foregroundWrapsHorizontally()).thenReturn(true, false);
        level.zoneFeatureProvider = provider;
        when(level.getZoneFeatureProvider()).thenReturn(provider);
        var frozen = BackgroundBuildParameters.capture(level);
        assertTrue(frozen.foregroundWraps());
        assertTrue(frozen.foregroundVerticalWrap());
        assertEquals(new BackgroundBuildParameters.ArenaBounds(240, 240, 11, true), frozen.arenaBounds());
        verify(provider, times(1)).foregroundWrapsHorizontally();
        verify(framing, times(1)).presentationNativeHorizontalOrigin();
        verify(level.objectManager, times(1)).getFrameCounter();
        var order = inOrder(provider);
        order.verify(provider).bgWrapsHorizontally();
        order.verify(provider).foregroundWrapsHorizontally();
        order.verify(provider).useLinearBackgroundLayoutOverflow(0);
        order.verify(provider).useFullWidthBackgroundTilemapWindow(0, 0, Integer.MIN_VALUE, 0);
        order.verify(provider).backgroundLoopBandBaseY(0, 0);
        order.verify(provider).isIntroOceanPhaseActive(0, 0);
        order.verify(framing).presentationNativeHorizontalOrigin();
        when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.empty());
        level.zoneFeatureProvider = null;
        level.verticalWrapEnabled = false;
        level.frameCounter = 123;
        clearInvocations(provider, level.camera, level.objectManager);
        assertEquals(new BackgroundBuildParameters.ArenaBounds(240, 240, 11, true), frozen.arenaBounds());
        assertTrue(frozen.foregroundVerticalWrap());
        verifyNoInteractions(provider, level.camera, level.objectManager);
    }

    @Test void ordinaryArenaCaptureKeepsArithmeticClockAndCallbackEvaluationOrder() {
        var level = mock(LevelManager.class);
        var camera = mock(com.openggf.camera.Camera.class);
        level.camera = camera;
        level.gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        var state = mock(com.openggf.game.GameStateManager.class);
        when(level.boundaryGameState()).thenReturn(state);
        when(camera.getMaxX()).thenReturn((short) 100);
        when(camera.getMinX()).thenReturn((short) 20);
        var x = new java.util.concurrent.atomic.AtomicInteger(40);
        var width = new java.util.concurrent.atomic.AtomicInteger(800);
        when(camera.getXWithShake()).thenAnswer(ignored -> (short) x.get());
        when(camera.getWidth()).thenAnswer(ignored -> (short) width.get());
        var provider = mock(ZoneFeatureProvider.class, withSettings().extraInterfaces(
                com.openggf.game.internal.NativeArenaCameraFraming.class));
        var framing = (com.openggf.game.internal.NativeArenaCameraFraming) provider;
        when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.empty());
        when(level.getZoneFeatureProvider()).thenReturn(provider);
        level.zoneFeatureProvider = provider;
        level.objectManager = mock(com.openggf.level.objects.ObjectManager.class);
        when(level.objectManager.getFrameCounter()).thenAnswer(ignored -> {
            x.set(999); width.set(320); return 77;
        });
        level.frameCounter = 5;
        clearInvocations(level, camera, state, provider, level.objectManager);
        var mask = LevelScrollPresentation.captureArenaMask(level);
        assertEquals(0, mask.left());
        assertEquals(444, mask.right(), "100 + native $40 allowance + 320 - the already sampled 40");
        assertEquals(77, mask.noiseFrame(), "The object clock wins over LevelManager's frame");
        var order = inOrder(level, camera, state, provider, level.objectManager);
        order.verify(camera).getMaxX();
        order.verify(level).boundaryGameState();
        order.verify(state).isBossFightActive();
        order.verify(state).isEndOfLevelActive();
        order.verify(level).getZoneFeatureProvider();
        order.verify(framing).presentationNativeHorizontalOrigin();
        order.verify(camera).getMinX();
        order.verify(camera).getXWithShake();
        order.verify(camera).getWidth();
        order.verify(level.objectManager).getFrameCounter();
        order.verify(provider).foregroundWrapsHorizontally();
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest @CsvSource({
            "S1,false,false,164", "S1,true,false,100", "S1,false,true,100",
            "S2,false,false,164", "S2,true,false,100", "S2,false,true,100",
            "S3K,false,false,100"})
    void nativeArenaMetadataKeepsTheOrdinaryBoundaryAllowanceAndLockBranches(
            String game, boolean locked, boolean ending, int expectedMax) {
        var level = mock(LevelManager.class);
        level.camera = mock(com.openggf.camera.Camera.class);
        level.gameModule = switch (game) {
            case "S1" -> new com.openggf.game.sonic1.Sonic1GameModule();
            case "S2" -> new com.openggf.game.sonic2.Sonic2GameModule();
            default -> new com.openggf.game.sonic3k.Sonic3kGameModule();
        };
        var state = mock(com.openggf.game.GameStateManager.class);
        when(level.boundaryGameState()).thenReturn(state);
        when(state.isScreenLocked()).thenReturn(locked);
        when(state.isBossFightActive()).thenReturn(locked);
        when(state.isEndOfLevelActive()).thenReturn(ending);
        when(level.camera.getMinX()).thenReturn((short) 20);
        when(level.camera.getMaxX()).thenReturn((short) 100);
        when(level.camera.getXWithShake()).thenReturn((short) 40);
        when(level.camera.getWidth()).thenReturn((short) 800);
        level.frameCounter = 5;
        var bounds = BackgroundBuildParameters.capture(level).arenaBounds();
        assertEquals(20, bounds.minX()); assertEquals(expectedMax, bounds.maxX());
        assertEquals(5, bounds.noiseFrame()); assertFalse(bounds.wraps());
        var ordinary = LevelScrollPresentation.captureArenaMask(level);
        assertEquals(0, ordinary.left());
        assertEquals(expectedMax == 164 ? 444 : 380, ordinary.right());
        assertEquals(5, ordinary.noiseFrame());
    }

    @Test void compatibilityPolicyDeclaresMissingArenaAndCinematicSamplingRejectsIt() {
        var frozen = new BackgroundBuildParameters(false, false, false, false, false, false,
                -1, false, new BackgroundBuildProjection(0, null, ignored -> 0));
        assertNull(frozen.arenaBounds(), "Tile-build-only construction does not invent arena geometry");
        assertFalse(frozen.foregroundVerticalWrap());
        assertFalse(frozen.retainedForeground().active());
        assertFalse(frozen.retainedBackground().active());
        var level = mock(LevelManager.class);
        level.camera = mock(com.openggf.camera.Camera.class);
        level.parallaxManager = mock(ParallaxManager.class);
        when(level.parallaxManager.getHScrollForShader()).thenReturn(new int[224]);
        when(level.parallaxManager.backgroundBuildParameters()).thenReturn(frozen);
        when(level.spritePresentationRenderer()).thenReturn(new LevelRenderer(level));
        var sample = LevelScrollPresentation.capture(level,
                com.openggf.game.render.AdvancedRenderFrameState.disabled());
        assertThrows(IllegalStateException.class, () -> sample.forCinematicCamera(10, 20, 800));
    }
}
