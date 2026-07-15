package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.scroll.SwScrlFbz;
import com.openggf.camera.Camera;
import com.openggf.level.ParallaxManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzBossCloudIdentity {
    @Test
    void nativeAllocationAddressOrderIsSelectorNineDownToZero() {
        for (int addressSlot = 0; addressSlot < 10; addressSlot++) {
            int selector = 9 - addressSlot;
            FbzCloudInstance cloud = new FbzCloudInstance(new ObjectSpawn(0, 0, 0, selector, 0, false, 0));
            assertEquals(selector, cloud.selector());
            assertEquals(addressSlot, cloud.addressSlot());
            assertEquals(SwScrlFbz.cloudMappingFrameForSelector(selector), cloud.mappingFrame());
        }
    }

    @Test
    void selectorNineAtAddressSlotZeroConsumesHscrollSlotZero() {
        SwScrlFbz.CloudPosition position = SwScrlFbz.computeBossCloudPosition(
                9, 0, 0x61, 0x110, 4, 3);

        assertEquals(9, position.selector());
        assertEquals(0, position.addressSlot());
        assertEquals(1, position.mappingFrame());
        assertEquals(0x213, position.x());
        assertEquals(0x0F9, position.y());

        SwScrlFbz.CloudPosition selectorZero = SwScrlFbz.computeBossCloudPosition(
                0, 9, 0x52, 0x110, 4, 3);
        assertEquals(0x1E2, selectorZero.x());
        assertEquals(0x0D9, selectorZero.y());
    }

    @Test
    void renderRefreshesCloudPositionAfterParallaxRunsLaterThanObjectUpdate() {
        SwScrlFbz handler = mock(SwScrlFbz.class);
        ParallaxManager parallax = mock(ParallaxManager.class);
        Camera camera = mock(Camera.class);
        AtomicReference<SwScrlFbz.CloudPosition> current = new AtomicReference<>(
                new SwScrlFbz.CloudPosition(0x10, 0x20, 1, 9, 0));
        when(parallax.getHandler(0)).thenReturn(handler);
        when(handler.cloudPositionAtAddressSlot(0)).thenAnswer(ignored -> current.get());
        when(camera.getX()).thenReturn((short) 0x1000);
        when(camera.getY()).thenReturn((short) 0x200);
        FbzCloudInstance cloud = new FbzCloudInstance(9);
        cloud.setServices(new StubObjectServices() {
            @Override public ParallaxManager parallaxManager() { return parallax; }
            @Override public Camera camera() { return camera; }
        });

        cloud.update(0, null);
        assertEquals(0x1010, cloud.getX());
        current.set(new SwScrlFbz.CloudPosition(0x44, 0x55, 1, 9, 0));

        cloud.appendRenderCommands(new ArrayList<>());

        assertEquals(0x1044, cloud.getX());
        assertEquals(0x255, cloud.getY());
        verify(handler, times(2)).cloudPositionAtAddressSlot(0);
    }
}
