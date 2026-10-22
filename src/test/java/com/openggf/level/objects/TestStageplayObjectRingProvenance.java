package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.objects.Sonic1RingInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kPlacedRingObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayObjectRingProvenance {
    @Test void releasedAnimalSharingNumericRingIdIsRemovedWhileNativeRingsSurvive() {
        var fixture = new Fixture(new Sonic3kObjectRegistry());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        var placed = fixture.objects.createDynamicObject(() -> new Sonic3kPlacedRingObjectInstance(spawn(0, 7)));
        var dynamic = fixture.objects.createDynamicObject(() -> new Sonic3kPlacedRingObjectInstance(spawn(0, -1)));
        var animal = fixture.objects.createDynamicObject(() -> new AnimalObjectInstance(spawn(0, -1), fixture.services));
        assertFalse(fixture.objects.isStageplayRingPlacement(animal.getSpawn()));
        assertTrue(fixture.objects.isStageplayRingPlacement(placed.getSpawn()));
        assertTrue(fixture.objects.isStageplayPlayerAttachmentOrRing(dynamic));
        assertFalse(fixture.objects.isStageplayPlayerAttachmentOrRing(animal));
        direction.clearObjects();
        assertEquals(List.of(placed, dynamic), List.copyOf(fixture.objects.getActiveObjects()));
        assertFalse(direction.allowsObject(animal), "Future native animals must remain suppressed after clearing");
    }

    @Test void nativeDynamicRingChildrenArePreservedWithoutTrustingTheirNumericIdAlone() {
        var fixture = new Fixture(new Sonic1ObjectRegistry());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        var ring = fixture.objects.createDynamicObject(() -> new Sonic1RingInstance(
                spawn(Sonic1ObjectIds.RING, -1), List.of(new RingSpawn(100, 80))));
        var unrelated = fixture.objects.createDynamicObject(() -> new AnimalObjectInstance(
                spawn(Sonic1ObjectIds.RING, -1), fixture.services));
        assertTrue(fixture.objects.isStageplayPlayerAttachmentOrRing(ring));
        assertFalse(fixture.objects.isStageplayPlayerAttachmentOrRing(unrelated));
        direction.clearObjects();
        assertEquals(List.of(ring), List.copyOf(fixture.objects.getActiveObjects()));
    }

    private static ObjectSpawn spawn(int id, int layoutIndex) {
        return new ObjectSpawn(100, 80, id, 0, 0, false, 80, layoutIndex);
    }

    private static final class Fixture {
        final ObjectServices services = mock(ObjectServices.class);
        final ObjectManager objects;
        Fixture(ObjectRegistry registry) {
            var graphics = new GraphicsManager();
            graphics.setHeadlessMode(true);
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.solidExecutionRegistry()).thenReturn(SolidExecutionRegistry.inert());
            objects = new ObjectManager(List.of(), registry, 0, null, null, graphics, mock(Camera.class), services);
            when(services.objectManager()).thenReturn(objects);
        }
    }
}
