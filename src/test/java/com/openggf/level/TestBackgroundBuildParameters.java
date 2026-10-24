package com.openggf.level;

import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.internal.BackgroundBuildProjection;
import com.openggf.game.internal.BackgroundBuildProjectionSource;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestBackgroundBuildParameters {
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
}
