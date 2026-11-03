package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestS3kExplosionAllocationAndOffsets {
    @Test
    void sharedOffsetRoutineKeepsNativeRangeEdgesAndIndependentWords() {
        assertEquals(-0x20, S3kExplosionOffsets.xOffset(0x003F0000, 0x20));
        assertEquals(0x1F, S3kExplosionOffsets.yOffset(0x003F0000, 0x20));
        assertEquals(0x1F, S3kExplosionOffsets.xOffset(0x0000003F, 0x20));
        assertEquals(-0x20, S3kExplosionOffsets.yOffset(0x0000003F, 0x20));
        assertEquals(-0x80, S3kExplosionOffsets.xOffset(0xFFFFFF00, 0x80));
        assertEquals(0x7F, S3kExplosionOffsets.yOffset(0xFFFFFF00, 0x80));
    }

    @Test
    void genericHelperUsesHighWordForYAndPreservesDispatchCadence() {
        var rng = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
        var reference = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
        var controller = new S3kBossExplosionController(300, 200, 8, rng);
        controller.tick();
        assertTrue(controller.drainPendingExplosions().isEmpty());
        controller.tick();
        assertTrue(controller.drainPendingExplosions().isEmpty());
        controller.tick();
        int random = reference.nextRaw();
        assertNotEquals((random >>> 8) & 0x3F, (random >>> 16) & 0x3F,
                "control seed must distinguish the historical wrong byte");
        var explosion = controller.drainPendingExplosions().getFirst();
        assertEquals(300 + (random & 0x3F) - 0x20, explosion.x());
        assertEquals(200 + ((random >>> 16) & 0x3F) - 0x20, explosion.y());
        assertEquals(reference.capture(), rng.capture());
    }

    @Test
    void exhaustedIczSlotsConsumeAttemptsWithoutRandomOrSoundAndStillFinish() {
        var parent = mock(IczMinibossInstance.class);
        var manager = mock(ObjectManager.class);
        var services = mock(ObjectServices.class);
        var rng = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
        when(services.objectManager()).thenReturn(manager);
        when(services.rng()).thenReturn(rng);
        when(manager.allocateSlotAfter(10)).thenReturn(-1);
        var controller = new IczMinibossExplosionControllerChild(parent, 300, 200);
        controller.setServices(services);
        controller.setSlotIndex(10);
        var before = rng.capture();
        for (int frame = 0; frame < 93; frame++) controller.update(frame, null);
        assertFalse(controller.isDestroyed());
        verify(parent, never()).onDefeatExplosionControllerFinished();
        controller.update(93, null);
        assertTrue(controller.isDestroyed());
        verify(parent).onDefeatExplosionControllerFinished();
        verify(manager, times(31)).allocateSlotAfter(10);
        verify(services, never()).rng();
        verify(services, never()).playSfx(anyInt());
        verify(manager, never()).addDynamicObjectAtSlot(any(), anyInt());
        assertEquals(before, rng.capture());
    }

    @Test
    void failedIczAttemptWaitsThreeFramesThenAllocatesBeforeRandomAndSound() {
        var parent = mock(IczMinibossInstance.class);
        var manager = mock(ObjectManager.class);
        var services = mock(ObjectServices.class);
        var rng = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
        var reference = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
        when(services.objectManager()).thenReturn(manager);
        when(services.rng()).thenReturn(rng);
        when(manager.allocateSlotAfter(10)).thenReturn(-1, 11);
        var controller = new IczMinibossExplosionControllerChild(parent, 300, 200);
        controller.setServices(services);
        controller.setSlotIndex(10);
        controller.update(0, null);
        controller.update(1, null);
        controller.update(2, null);
        verify(manager).allocateSlotAfter(10);
        verify(services, never()).rng();
        clearInvocations(manager, services);
        controller.update(3, null);
        var child = ArgumentCaptor.forClass(ObjectInstance.class);
        var order = inOrder(manager, services);
        order.verify(services).objectManager();
        order.verify(manager).allocateSlotAfter(10);
        order.verify(services).rng();
        order.verify(manager).addDynamicObjectAtSlot(child.capture(), eq(11));
        order.verify(services).playSfx(Sonic3kSfx.EXPLODE.id);
        int random = reference.nextRaw();
        assertInstanceOf(S3kBossExplosionChild.class, child.getValue());
        assertEquals(300 + (random & 0x3F) - 0x20, child.getValue().getX());
        assertEquals(200 + ((random >>> 16) & 0x3F) - 0x20, child.getValue().getY());
        assertEquals(reference.capture(), rng.capture());
    }
}
