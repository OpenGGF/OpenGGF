package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidRoutineKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestHCZ2WallObjectInstance {

    @Test
    void movingWallDeclaresNativeSolidObjectFull2VisibilityContract() {
        HCZ2WallObjectInstance wall = new HCZ2WallObjectInstance();

        assertEquals(SolidRoutineKind.FULL_SOLID, wall.getSolidRoutineProfile().kind());
        assertTrue(wall.getSolidRoutineProfile().bypassesOffscreenSolidGate(),
                "Obj_HCZ2Wall calls SolidObjectFull2, so its collision box remains live outside render bounds");
    }

    @Test
    void sidePushRechecksEarlierEngineSlotVerticalHurtBlock() {
        HCZ2WallObjectInstance wall = new HCZ2WallObjectInstance();
        wall.setSlotIndex(17);
        Sonic3kInvisibleHurtBlockVObjectInstance block =
                new Sonic3kInvisibleHurtBlockVObjectInstance(
                        new ObjectSpawn(0x0A98, 0x07C0, 0x6B, 0x16, 0x01, false, 0));
        block.setSlotIndex(14);
        ObjectServices services = mock(ObjectServices.class);
        ObjectManager objectManager = mock(ObjectManager.class);
        PlayableEntity player = mock(PlayableEntity.class);
        when(services.objectManager()).thenReturn(objectManager);
        when(objectManager.activeObjectsOfType(Sonic3kInvisibleHurtBlockVObjectInstance.class))
                .thenReturn(List.of(block));
        wall.setServices(services);

        wall.onSolidContact(player,
                new SolidContact(false, true, false, false, true), 0);

        verify(objectManager).processImmediateInlineSolidCheckpoint(block, player, List.of());
    }

    @Test
    void ordinaryObjectSlotLatchesTheOffsetUntilItsNextUpdate() {
        var hcz = mock(HczZoneRuntimeState.class);
        when(hcz.backgroundRoutine()).thenReturn(4);
        when(hcz.wallOffsetPixels()).thenReturn(-0x20);
        var wall = wallWith(hcz);

        wall.update(7, null);
        assertEquals(0x5DE, wall.getX());
        assertEquals(0x700, wall.getY());
        when(hcz.wallOffsetPixels()).thenReturn(-0x21);

        assertEquals(0x5DE, wall.getX(), "a later screen publication cannot push this slot's latched pose");
        wall.update(8, null);
        assertEquals(0x5DF, wall.getX());
        assertFalse(wall.isDestroyed());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, 12, 16})
    void nonChaseBackgroundStageDeletesTheWallOnItsOwnSlot(int stage) {
        var hcz = mock(HczZoneRuntimeState.class);
        when(hcz.backgroundRoutine()).thenReturn(stage);
        var wall = wallWith(hcz);

        assertFalse(wall.isDestroyed());
        wall.update(12, null);

        assertTrue(wall.isDestroyed());
        verify(hcz, never()).wallOffsetPixels();
    }

    @Test
    void absentHczOwnerDeletesTheWallWithoutAnEventCommand() {
        var services = mock(ObjectServices.class);
        when(services.zoneRuntimeRegistry()).thenReturn(new ZoneRuntimeRegistry());
        var wall = new HCZ2WallObjectInstance();
        wall.setServices(services);

        wall.update(0, null);

        assertTrue(wall.isDestroyed());
    }

    @Test
    void reconstructedWallRestoresItsLatchedPoseWithoutReadingTheFuturePublication() {
        var hcz = mock(HczZoneRuntimeState.class);
        when(hcz.backgroundRoutine()).thenReturn(4);
        when(hcz.wallOffsetPixels()).thenReturn(-0x20);
        var original = wallWith(hcz);
        original.update(7, null);
        var saved = original.captureRewindState();
        when(hcz.wallOffsetPixels()).thenReturn(-0x60);
        var reconstructed = wallWith(hcz);
        clearInvocations(hcz);

        reconstructed.restoreRewindState(saved);

        assertEquals(0x5DE, reconstructed.getX());
        assertEquals(0x700, reconstructed.getY());
        verifyNoInteractions(hcz);
        reconstructed.update(8, null);
        assertEquals(0x61E, reconstructed.getX(), "the next actual slot consumes the restored owner's value");
    }

    private static HCZ2WallObjectInstance wallWith(HczZoneRuntimeState hcz) {
        var registry = new ZoneRuntimeRegistry();
        registry.install(hcz);
        var services = mock(ObjectServices.class);
        when(services.zoneRuntimeRegistry()).thenReturn(registry);
        var wall = new HCZ2WallObjectInstance();
        wall.setServices(services);
        return wall;
    }
}
