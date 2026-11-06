package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.mockito.Mockito.*;

class TestAizForegroundPlantRendering {
    @Test
    void parallaxPlantsOutsideTheViewportDoNotConsumeSpriteCapacity() {
        var renderer = rendererAt(6023, 944, 320);
        draw(renderer, 5984, 1112, 0x30); // parallax X = 5934, left of the camera
        draw(renderer, 6424, 1136, 0x31); // parallax X = 6484, right of the camera
        draw(renderer, 6576, 1104, 0x51);
        draw(renderer, 6624, 1120, 0x30);
        verify(renderer.renderer, never()).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void partiallyVisiblePlantsKeepAllTheirNativeMappingPieces() {
        var renderer = rendererAt(6023, 944, 320);
        draw(renderer, 6064, 1104, 0x41);
        draw(renderer, 6272, 1144, 0x20);
        verify(renderer.renderer).drawFrameIndex(1, 6004, 1128, false, false);
        verify(renderer.renderer).drawFrameIndex(0, 6283, 1155, false, false);
    }

    @Test
    void cullingUsesTheSelectedViewportAndVariantHeight() {
        var wide = rendererAt(6023, 944, 528);
        draw(wide, 6424, 1136, 0x31);
        verify(wide.renderer).drawFrameIndex(1, 6484, 1156, false, false);

        var nativeView = rendererAt(0, 0, 320);
        draw(nativeView, 160, 283, 1); // variant 1's 60px half-height still overlaps
        draw(nativeView, 160, 284, 1); // bottom edge excluded
        draw(nativeView, 160, 272, 0); // variant 0 has only 48px half-height
        verify(nativeView.renderer).drawFrameIndex(1, 160, 283, false, false);
        verifyNoMoreInteractions(ignoreStubs(nativeView.renderer));
    }

    private record Fixture(Camera camera, LevelManager level, PatternSpriteRenderer renderer) { }

    private Fixture rendererAt(int x, int y, int width) {
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) x);
        when(camera.getY()).thenReturn((short) y);
        when(camera.getWidth()).thenReturn((short) width);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.screenYWrapValue()).thenReturn(0xFFFF);
        var level = mock(LevelManager.class);
        var manager = mock(ObjectRenderManager.class);
        var renderer = mock(PatternSpriteRenderer.class);
        when(level.getObjectRenderManager()).thenReturn(manager);
        when(manager.getRenderer(Sonic3kObjectArtKeys.AIZ_FOREGROUND_PLANT)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);
        return new Fixture(camera, level, renderer);
    }

    private void draw(Fixture fixture, int x, int y, int subtype) {
        var plant = new AizForegroundPlantInstance(new ObjectSpawn(x, y, 0x35, subtype, 0, false, y));
        plant.setServices(new TestObjectServices().withLevelManager(fixture.level).withCamera(fixture.camera));
        plant.appendRenderCommands(new ArrayList<>());
    }
}
