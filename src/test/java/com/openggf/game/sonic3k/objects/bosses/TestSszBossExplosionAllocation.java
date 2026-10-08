package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.GameRng;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSszBossExplosionAllocation {
    @Test
    void failedForwardAllocationConsumesAttemptWithoutRngThenFollowsSlotOccupant() {
        var manager = mock(ObjectManager.class);
        var rng = mock(GameRng.class);
        var parent = mock(AbstractObjectInstance.class);
        when(parent.getSlotIndex()).thenReturn(5);
        when(parent.getX()).thenReturn(0x300);
        when(parent.getY()).thenReturn(0x500);
        when(manager.getActiveObjects()).thenReturn(List.of(parent));
        var controller = new SszBossExplosionController(0, 0, 5);
        controller.setServices(new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public GameRng rng() { return rng; }
        });
        controller.setSlotIndex(7);
        when(manager.allocateSlotAfter(7)).thenReturn(-1, 8);
        when(rng.nextRaw()).thenReturn(0x003F0010);
        for (int frame = 0; frame < 3; frame++) {
            controller.update(frame, null);
        }
        verify(manager, times(1)).allocateSlotAfter(7);
        verifyNoInteractions(rng);
        // Native parent3 is an address: a replacement in that slot supplies its position.
        var replacement = mock(AbstractObjectInstance.class);
        when(replacement.getSlotIndex()).thenReturn(5);
        when(replacement.getX()).thenReturn(0x400);
        when(replacement.getY()).thenReturn(0x600);
        when(manager.getActiveObjects()).thenReturn(List.of(replacement));
        controller.update(3, null);
        var child = ArgumentCaptor.forClass(S3kBossExplosionChild.class);
        verify(manager).addDynamicObjectAtSlot(child.capture(), eq(8));
        assertEquals(0x3F0, child.getValue().getX());
        assertEquals(0x61F, child.getValue().getY());
        assertTrue(child.getValue().nativeInitSfxForTest());
        assertFalse(child.getValue().nativeInitSfxPlayedForTest());
        when(manager.getActiveObjects()).thenReturn(List.of());
        controller.update(4, null);
        assertFalse(controller.isDestroyed(), "Go_Delete_Sprite defers deletion one pass");
        controller.update(5, null);
        assertTrue(controller.isDestroyed());
        verify(rng, times(1)).nextRaw();
    }

    @Test
    void nativeParentStopBitRetiresWorkerBeforeAllocationOrRng() {
        var manager = mock(ObjectManager.class);
        var rng = mock(GameRng.class);
        var parent = mock(SszMechaSonicObjectInstance.class);
        when(parent.getSlotIndex()).thenReturn(5);
        when(parent.stopsDefeatExplosions()).thenReturn(true);
        when(manager.getActiveObjects()).thenReturn(List.of(parent));
        var controller = new SszBossExplosionController(0, 0, 5);
        controller.setServices(new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public GameRng rng() { return rng; }
        });
        controller.setSlotIndex(7);
        controller.update(0, null);
        assertFalse(controller.isDestroyed(), "native stop installs next-pass deletion");
        verify(manager, never()).allocateSlotAfter(anyInt());
        verifyNoInteractions(rng);
        controller.update(1, null);
        assertTrue(controller.isDestroyed());
    }

    @Test
    void controllerAllocationIsOneForwardAttemptWithoutFallback() {
        var manager = mock(ObjectManager.class);
        var services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
        };
        when(manager.allocateSlotAfter(5)).thenReturn(-1);
        SszBossExplosionController.spawnFor(services, 5, 0x300, 0x500);
        verify(manager).allocateSlotAfter(5);
        verifyNoMoreInteractions(manager);
    }
    @Test
    void finiteBurstOutlivesDeletedCreatorAndSpendsCountsOnAllocationFailure() {
        var manager = mock(ObjectManager.class);
        var rng = mock(GameRng.class);
        var services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public GameRng rng() { return rng; }
        };
        when(manager.allocateSlotAfter(5)).thenReturn(7);
        when(manager.allocateSlotAfter(7)).thenReturn(-1);
        when(manager.getActiveObjects()).thenReturn(List.of());
        assertTrue(SszBossExplosionController.spawnFor(services, 5, 0x300, 0x500, 0xC));
        var captured = ArgumentCaptor.forClass(SszBossExplosionController.class);
        verify(manager).addDynamicObjectAtSlot(captured.capture(), eq(7));
        var controller = captured.getValue();
        // The mocked addDynamicObjectAtSlot does not perform ObjectManager's service injection.
        controller.setServices(services);
        for (int frame = 0; frame < 189; frame++) controller.update(frame, null);
        verify(manager, times(63)).allocateSlotAfter(7);
        assertFalse(controller.isDestroyed());
        controller.update(189, null);
        assertFalse(controller.isDestroyed(), "count expiry installs Go_Delete_Sprite");
        controller.update(190, null);
        assertTrue(controller.isDestroyed());
        verifyNoInteractions(rng);
    }

}
