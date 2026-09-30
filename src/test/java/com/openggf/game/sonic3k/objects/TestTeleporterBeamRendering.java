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

class TestTeleporterBeamRendering {
    @Test
    void expandedArrivalDrawsOnlyTheTwoColumnsAndFlickersOnEvenFrames() {
        var camera = mock(Camera.class);
        when(camera.getY()).thenReturn((short) 0xF49);
        var level = mock(LevelManager.class);
        var renders = mock(ObjectRenderManager.class);
        var renderer = mock(PatternSpriteRenderer.class);
        when(level.getObjectRenderManager()).thenReturn(renders);
        when(renders.getRenderer(Sonic3kObjectArtKeys.SSZ_TELEPORTER)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);
        var beam = TeleporterBeamObjectInstance.sszArrivalBeam(
                new ObjectSpawn(0x100, 0x1000, 0, 0, 0, false, 0), null);
        beam.setServices(new TestObjectServices().withCamera(camera).withLevelManager(level));

        when(level.getFrameCounter()).thenReturn(1);
        beam.update(1, null);
        beam.appendRenderCommands(new ArrayList<>());
        // Render_Sprites loc_1AEE4 skips the main sprite when mapping_frame is zero.
        verify(renderer).drawFrameIndex(7, 0xE8, 0xF78, false, false, 3);
        verify(renderer).drawFrameIndex(8, 0x118, 0xF78, false, false, 3);
        verify(renderer).isReady();
        verifyNoMoreInteractions(renderer);

        clearInvocations(renderer);
        when(level.getFrameCounter()).thenReturn(2);
        beam.update(2, null);
        beam.appendRenderCommands(new ArrayList<>());
        verifyNoInteractions(renderer);
    }
}
