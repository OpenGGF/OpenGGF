package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kSuperStateController;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real manager hydration, including the generic probe and captured PLAYER_REF owner. */
class TestSuperTailsFlockManagerRewind {
    @BeforeEach void setUp() { TestEnvironment.resetAll(); }
    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    @Test
    void recreatedFlockRetainsOwnerSlotsIdentityAndContinuesNativeMotion() {
        Tails owner = new Tails("tails", (short) 0x180, (short) 0x200);
        var controller = mock(Sonic3kSuperStateController.class);
        when(controller.isSuperTailsFormActive()).thenReturn(true);
        owner.setSuperStateController(controller);
        Camera camera = mock(Camera.class);
        when(camera.getFocusedSprite()).thenReturn(owner);
        when(camera.screenYWrapValue()).thenReturn(0xFFFF);
        var services = new TestObjectServices().withCamera(camera);
        var manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                null, camera, services);
        services.withDirectObjectManager(manager);
        var original = manager.createDynamicObject(() -> new SuperTailsFlickyFlockObjectInstance(owner));
        assertNotNull(original);
        original.update(1, owner);
        var captured = manager.rewindSnapshottable().capture();
        assertEquals(1, captured.dynamicObjects().size());
        assertNull(captured.dynamicObjects().getFirst().playerOwner(),
                "the ordinary generic field codec owns the flock's playable reference");
        var identity = captured.dynamicObjects().getFirst().objectId();
        int slot = original.getSlotIndex();
        original.update(2, owner);
        var expected = original.birdRuntimeState(0);
        original.setDestroyed(true);

        manager.rewindSnapshottable().restore(captured);

        var restored = manager.activeObjectsOfType(SuperTailsFlickyFlockObjectInstance.class).getFirst();
        assertNotSame(original, restored, "restore must construct the missing dynamic object");
        assertTrue(restored.isBoundTo(owner), "phase-two hydration resolves the captured player reference");
        assertEquals(slot, restored.getSlotIndex());
        assertSame(restored, manager.captureIdentityContext().requireIdentityTable().resolve(identity));
        var restoredSnapshot = manager.rewindSnapshottable().capture();
        assertArrayEquals(captured.usedSlotsBits(), restoredSnapshot.usedSlotsBits());
        assertEquals(captured.dynamicObjectIdCounter(), restoredSnapshot.dynamicObjectIdCounter());
        assertEquals(identity, restoredSnapshot.dynamicObjects().getFirst().objectId());
        restored.update(2, owner);
        assertEquals(expected, restored.birdRuntimeState(0),
                "restored native motion resumes from the captured cursor without a new allocation");
    }
}
