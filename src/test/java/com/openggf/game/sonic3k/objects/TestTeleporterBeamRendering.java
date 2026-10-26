package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestTeleporterBeamRendering {
    @ParameterizedTest(name = "{0} contraction terminal frame / pending-delete recreation")
    @ValueSource(strings = {"hpz", "ssz", "cnz", "icz"})
    void contractionUnderflowDrawsMainNineThenDeletesOnItsNextOwnDispatch(String profile) {
        var camera = mock(Camera.class);
        when(camera.getY()).thenReturn((short) 0xF49);
        var level = mock(LevelManager.class);
        var renders = mock(ObjectRenderManager.class);
        var renderer = mock(PatternSpriteRenderer.class);
        when(level.getObjectRenderManager()).thenReturn(renders);
        String key = switch (profile) {
            case "ssz" -> Sonic3kObjectArtKeys.SSZ_TELEPORTER;
            case "cnz" -> Sonic3kObjectArtKeys.CNZ_TELEPORTER;
            case "icz" -> Sonic3kObjectArtKeys.ICZ_TELEPORTER;
            default -> Sonic3kObjectArtKeys.HPZ_ENTRY_TELEPORTER;
        };
        when(renders.getRenderer(key)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);
        var services = new TestObjectServices().withCamera(camera).withLevelManager(level);
        var parent = mock(TeleporterBeamOwner.class);
        // This component fixture owns one parent identity. Capture and every
        // restore resolve that same non-null owner, as a real world table does.
        var identities = new RewindIdentityTable();
        identities.registerObject(parent, ObjectRefId.dynamic(4, 0, 1));
        var rewindContext = RewindCaptureContext.withIdentityTable(identities);
        var spawn = new ObjectSpawn(0x100, 0x1000, 0, 0, 0, false, 0);
        var beam = switch (profile) {
            case "ssz" -> TeleporterBeamObjectInstance.sszArrivalBeam(spawn, parent);
            case "cnz" -> TeleporterBeamObjectInstance.cnzTransportBeam(spawn, parent);
            case "icz" -> TeleporterBeamObjectInstance.iczArrivalBeam(spawn, parent);
            default -> new TeleporterBeamObjectInstance(spawn, parent);
        };
        beam.setServices(services);
        int lf = 0;
        // Ordinary HPZ/CNZ entry reaches Expand through its real stack/wait
        // routines; arrival entries already own progress $18. No phase is seeded.
        while (beam.phaseForTest() != 2 || beam.progress() != 0x18) {
            assertTrue(++lf < 256, "native build reaches full expansion");
            when(level.getFrameCounter()).thenReturn(lf);
            beam.update(0, null);
        }
        beam.startContracting();
        while (beam.progress() > 0) {
            when(level.getFrameCounter()).thenReturn(++lf);
            beam.update(0, null);
        }
        assertEquals(1, lf & 1, "last contraction step is an odd Level_frame_counter");
        var missingContext = assertThrows(IllegalStateException.class, beam::captureRewindState,
                "a live parent reference cannot be captured without its identity table");
        assertTrue(missingContext.getMessage().contains("RewindIdentityTable is required"));
        var beforeUnderflow = beam.captureRewindState(rewindContext);
        clearInvocations(renderer, parent);
        when(level.getFrameCounter()).thenReturn(++lf);
        beam.update(0, null);
        beam.appendRenderCommands(new ArrayList<>());
        assertEquals(0, beam.progress());
        assertFalse(beam.isDestroyed());
        verifyNoInteractions(renderer, parent);

        when(level.getFrameCounter()).thenReturn(++lf);
        beam.update(0, null);
        assertEquals(-1, beam.progress());
        assertEquals(0, beam.segmentCountForTest());
        assertFalse(beam.isDestroyed(), "the replacement Delete code pointer has not run");
        verify(parent).onBeamFinished();
        int finalY = profile.equals("icz") ? 0xF80 : 0xF78;
        int palette = profile.equals("cnz") || profile.equals("icz") ? 1 : 3;
        beam.appendRenderCommands(new ArrayList<>());
        verify(renderer).drawFrameIndex(9, 0x100, finalY, false, false, palette);
        verify(renderer).isReady();
        verifyNoMoreInteractions(renderer, parent);
        assertEquals(1, beam.getPriorityBucket());
        assertEquals(profile.equals("ssz"), beam.isHighPriority());

        var pendingDelete = beam.captureRewindState(rewindContext);
        for (int cycle = 0; cycle < 2; cycle++) {
            var restored = new TeleporterBeamObjectInstance(spawn, null).recreateForRewind(
                    new com.openggf.level.objects.RewindRecreateContext(spawn, pendingDelete, services));
            restored.setServices(services);
            restored.restoreRewindState(pendingDelete, rewindContext);
            clearInvocations(renderer);
            restored.appendRenderCommands(new ArrayList<>());
            verify(renderer).drawFrameIndex(9, 0x100, finalY, false, false, palette);
            clearInvocations(renderer);
            when(level.getFrameCounter()).thenReturn(lf + 1); // Even LF does not defer Delete.
            restored.update(0, null);
            assertTrue(restored.isDestroyed());
            restored.appendRenderCommands(new ArrayList<>());
            verifyNoInteractions(renderer);
            verify(parent, times(1)).onBeamFinished();
        }
        // Restoring the real pre-underflow state must publish completion once
        // again at underflow, and never at the following Delete dispatch.
        beam.restoreRewindState(beforeUnderflow, rewindContext);
        clearInvocations(parent);
        when(level.getFrameCounter()).thenReturn(lf);
        beam.update(0, null);
        verify(parent).onBeamFinished();
        when(level.getFrameCounter()).thenReturn(lf + 1);
        beam.update(0, null);
        assertTrue(beam.isDestroyed());
        verifyNoMoreInteractions(parent);
    }

    @Test
    void nativeArtWordPriorityKeepsSszAndHpzBranchesSeparate() {
        var spawn=new ObjectSpawn(0x100,0x1000,0,0,0,false,0);
        org.junit.jupiter.api.Assertions.assertTrue(TeleporterBeamObjectInstance.sszArrivalBeam(spawn,null).isHighPriority());
        org.junit.jupiter.api.Assertions.assertTrue(TeleporterBeamObjectInstance.sszTransportBeam(spawn,null).isHighPriority());
        org.junit.jupiter.api.Assertions.assertFalse(new TeleporterBeamObjectInstance(spawn,null).isHighPriority());
        assertFalse(TeleporterBeamObjectInstance.cnzTransportBeam(spawn, null).isHighPriority());
        assertFalse(TeleporterBeamObjectInstance.iczArrivalBeam(spawn, null).isHighPriority());
    }

    @Test
    void iczRecreationPreservesNativeBaseAndContractingFlatPaletteOneColumns() {
        var camera = mock(Camera.class);
        when(camera.getY()).thenReturn((short) 0x5FB);
        var level = mock(LevelManager.class);
        var renders = mock(ObjectRenderManager.class);
        var icz = mock(PatternSpriteRenderer.class);
        var hpz = mock(PatternSpriteRenderer.class);
        when(level.getObjectRenderManager()).thenReturn(renders);
        when(renders.getRenderer(Sonic3kObjectArtKeys.ICZ_TELEPORTER)).thenReturn(icz);
        when(renders.getRenderer(Sonic3kObjectArtKeys.HPZ_ENTRY_TELEPORTER)).thenReturn(hpz);
        when(icz.isReady()).thenReturn(true);
        var services = new TestObjectServices().withCamera(camera).withLevelManager(level);
        var spawn = new ObjectSpawn(0x3640, 0x670, 0, 0, 0, false, 0);
        var beam = TeleporterBeamObjectInstance.iczArrivalBeam(spawn, null);
        beam.setServices(services);
        assertEquals(0x18, beam.progress());
        assertEquals(2, beam.segmentCountForTest());
        when(level.getFrameCounter()).thenReturn(1);
        beam.update(0, null);
        assertEquals(0x17, beam.progress());
        var snapshot = beam.captureRewindState();
        var restored = new TeleporterBeamObjectInstance(spawn, null).recreateForRewind(
                new com.openggf.level.objects.RewindRecreateContext(spawn, snapshot, services));
        restored.setServices(services);
        restored.restoreRewindState(snapshot);
        restored.appendRenderCommands(new ArrayList<>());
        verify(icz).drawFrameIndex(7, 0x3628, 0x5F0, false, false, 1);
        verify(icz).drawFrameIndex(8, 0x3658, 0x5F0, false, false, 1);
        verifyNoInteractions(hpz);
        // Continue into word_467FC's early rows, which are flat Y unlike SSZ/HPZ.
        for (int lf = 3; lf <= 41; lf += 2) {
            when(level.getFrameCounter()).thenReturn(lf);
            restored.update(0, null);
        }
        clearInvocations(icz);
        when(level.getFrameCounter()).thenReturn(43);
        restored.update(0, null);
        restored.appendRenderCommands(new ArrayList<>());
        verify(icz).drawFrameIndex(3, 0x3637, 0x5F0, false, false, 1);
        verify(icz).drawFrameIndex(6, 0x3649, 0x5F0, false, false, 1);
        assertEquals(2, restored.progress());
    }

    @Test
    void cnzNativeBuildAndRecreatedFlatColumnsUsePaletteOne() {
        var camera = mock(Camera.class);
        when(camera.getY()).thenReturn((short) 0xF49);
        var level = mock(LevelManager.class);
        var renders = mock(ObjectRenderManager.class);
        var cnz = mock(PatternSpriteRenderer.class);
        var hpz = mock(PatternSpriteRenderer.class);
        when(level.getObjectRenderManager()).thenReturn(renders);
        when(renders.getRenderer(Sonic3kObjectArtKeys.CNZ_TELEPORTER)).thenReturn(cnz);
        when(renders.getRenderer(Sonic3kObjectArtKeys.HPZ_ENTRY_TELEPORTER)).thenReturn(hpz);
        when(cnz.isReady()).thenReturn(true);
        var services = new TestObjectServices().withCamera(camera).withLevelManager(level);
        var spawn = new ObjectSpawn(0x100, 0x1000, 0, 0, 0, false, 0);
        var beam = TeleporterBeamObjectInstance.cnzTransportBeam(spawn, null);
        beam.setServices(services);
        for (int lf = 1; lf <= 16; lf++) {
            when(level.getFrameCounter()).thenReturn(lf);
            beam.update(0x80 + lf, null);
            assertEquals(0, beam.progress());
            beam.appendRenderCommands(new ArrayList<>());
        }
        verifyNoInteractions(cnz);
        when(level.getFrameCounter()).thenReturn(17);
        beam.update(0, null);
        assertEquals(-1, beam.progress(), "the signed first build toggle is not progress one");
        beam.appendRenderCommands(new ArrayList<>());
        verify(cnz).drawFrameIndex(1, 0x100, 0x1000, false, false, 1);
        clearInvocations(cnz);
        for (int lf = 18; lf <= 55; lf++) {
            when(level.getFrameCounter()).thenReturn(lf);
            beam.update(0, null);
        }
        assertEquals(1, beam.progress());
        var snapshot = beam.captureRewindState();
        var probe = new TeleporterBeamObjectInstance(spawn, null);
        var restored = probe.recreateForRewind(
                new com.openggf.level.objects.RewindRecreateContext(spawn, snapshot, services));
        restored.setServices(services);
        restored.restoreRewindState(snapshot);
        restored.appendRenderCommands(new ArrayList<>());
        // word_467FC starts at Y0 instead of word_46734's Y-6. Art $24EE
        // selects palette1 and low plane priority; native priority $80 = bucket1.
        verify(cnz).drawFrameIndex(3, 0xFA, 0xF78, false, false, 1);
        verify(cnz).drawFrameIndex(4, 0x106, 0xF78, false, false, 1);
        assertFalse(restored.isHighPriority());
        assertEquals(1, restored.getPriorityBucket());
        verifyNoInteractions(hpz);
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
