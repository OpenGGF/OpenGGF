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
    void nativeArtWordPriorityKeepsSszAndHpzBranchesSeparate() {
        var spawn=new ObjectSpawn(0x100,0x1000,0,0,0,false,0);
        org.junit.jupiter.api.Assertions.assertTrue(TeleporterBeamObjectInstance.sszArrivalBeam(spawn,null).isHighPriority());
        org.junit.jupiter.api.Assertions.assertTrue(TeleporterBeamObjectInstance.sszTransportBeam(spawn,null).isHighPriority());
        org.junit.jupiter.api.Assertions.assertFalse(new TeleporterBeamObjectInstance(spawn,null).isHighPriority());
    }

    @Test
    void arrivalRecreationUsesCapturedArtRatherThanTheHarmlessHpzProbe() {
        var camera=mock(Camera.class);
        when(camera.getY()).thenReturn((short)0xF49);
        var level=mock(LevelManager.class);
        when(level.getFrameCounter()).thenReturn(1);
        var renders=mock(ObjectRenderManager.class);
        var ssz=mock(PatternSpriteRenderer.class);
        var hpz=mock(PatternSpriteRenderer.class);
        when(level.getObjectRenderManager()).thenReturn(renders);
        when(renders.getRenderer(Sonic3kObjectArtKeys.SSZ_TELEPORTER)).thenReturn(ssz);
        when(renders.getRenderer(Sonic3kObjectArtKeys.HPZ_ENTRY_TELEPORTER)).thenReturn(hpz);
        when(ssz.isReady()).thenReturn(true);
        when(hpz.isReady()).thenReturn(true);
        var services=new TestObjectServices().withCamera(camera).withLevelManager(level);
        var spawn=new ObjectSpawn(0x100,0x1000,0,0,0,false,0);
        var original=TeleporterBeamObjectInstance.sszArrivalBeam(spawn,null);
        original.setServices(services);original.update(1,null);
        var state=original.captureRewindState();
        // ObjectRewindDynamicCodecs constructs this harmless parent-null HPZ probe.
        var probe=new TeleporterBeamObjectInstance(spawn,null);
        var restored=probe.recreateForRewind(new com.openggf.level.objects.RewindRecreateContext(spawn,state,services));
        restored.setServices(services);restored.restoreRewindState(state);
        restored.appendRenderCommands(new ArrayList<>());
        verify(ssz).drawFrameIndex(7,0xE8,0xF78,false,false,3);
        verify(ssz).drawFrameIndex(8,0x118,0xF78,false,false,3);
        verifyNoInteractions(hpz);
    }

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
