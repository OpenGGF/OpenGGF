package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-free engineering fallback; this does not qualify native parent/escape retirement. */
class TestS1EggmanVisualFallback {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ownerlessProbeDisposesWithoutWorldReadsOrChangingRespawnAndSlotState(boolean respawnable) {
        var spawn = new ObjectSpawn(0x100, 0x200, 0x3D, S1EggmanVisualComponent.FACE, 0, false, 0);
        var component = new S1EggmanVisualComponent(spawn);
        component.setServices(new StubObjectServices() {
            @Override public RomByteReader romReader() {
                throw new AssertionError("Ownerless fallback must not load animation data");
            }
            @Override public Camera camera() {
                throw new AssertionError("Ownerless fallback must not query a scene");
            }
            @Override public com.openggf.game.session.WorldSession worldSession() {
                throw new AssertionError("Ownerless fallback must not resolve a world");
            }
        });
        component.setSlotIndex(73);
        if (respawnable) component.setDestroyedByOffscreen();
        assertEquals(respawnable, component.isDestroyedRespawnable());

        component.update(0, null);
        component.update(1, null);

        assertTrue(component.isDestroyed());
        assertEquals(respawnable, component.isDestroyedRespawnable(),
                "Raw true writes retain prior respawn state; false-then-true helpers would erase it");
        assertEquals(73, component.getSlotIndex(), "The manager retains responsibility for slot retirement");
        assertEquals(spawn, component.getSpawn());
    }

    @org.junit.jupiter.api.Test
    void alreadyDestroyedBoundComponentDoesNotConsultItsParentOrResetRespawnState() {
        var ship = mock(AbstractS1EggmanBossInstance.class);
        when(ship.getSpawn()).thenReturn(new ObjectSpawn(0x100, 0x200, 0x3D, 0, 0, false, 0));
        var component = new S1EggmanVisualComponent(ship, S1EggmanVisualComponent.FACE);
        clearInvocations(ship);
        component.setSlotIndex(73);
        component.setDestroyedByOffscreen();

        component.update(0, null);

        assertTrue(component.isDestroyed());
        assertTrue(component.isDestroyedRespawnable());
        assertEquals(73, component.getSlotIndex());
        verifyNoInteractions(ship);
    }
}
