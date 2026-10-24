package com.openggf.level.objects;

import com.openggf.game.sonic1.objects.Sonic1JunctionObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1TeleporterObjectInstance;
import com.openggf.game.sonic3k.objects.Aiz2EndEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.HPZSSEntryControlObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayNativeStateRelease {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(100, 100, 1, 0, 0, false, 100);
    private static Field field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }

    @Test void playerReleaseDoesNotCancelS1JunctionOrTransportProgress() throws Exception {
        for (var object : List.of(new Sonic1JunctionObjectInstance(SPAWN), new Sonic1TeleporterObjectInstance(SPAWN))) {
            var owner = mock(AbstractPlayableSprite.class);
            field(object, "controlledPlayer").set(object, owner);
            var routine = field(object, "routine");
            Object running = routine.getType().getEnumConstants()[1]; routine.set(object, running);
            object.releasePlayersForVisualDirection();
            assertSame(running, routine.get(object));
            assertNull(field(object, "controlledPlayer").get(object));
            verify(owner).setControlLocked(false);
        }
    }

    @Test void cannonAndHpzControllerReleaseOwnershipWithoutRewindingTheirNativePhase() throws Exception {
        var owner = mock(AbstractPlayableSprite.class);
        var services = mock(ObjectServices.class, RETURNS_DEEP_STUBS);
        var cannon = new CnzCannonInstance(SPAWN); cannon.setServices(services);
        field(cannon, "capturedPlayer").set(cannon, owner);
        field(cannon, "state").set(cannon, 2);
        cannon.releasePlayersForVisualDirection();
        assertEquals(2, field(cannon, "state").get(cannon));
        var hpz = new HPZSSEntryControlObjectInstance(SPAWN); hpz.setServices(mock(ObjectServices.class));
        field(hpz, "playersLocked").set(hpz, true);
        ((PlayerControlOwners) field(hpz, "directedControlPlayers").get(hpz)).add(owner);
        hpz.releasePlayersForVisualDirection();
        assertEquals(true, field(hpz, "playersLocked").get(hpz));
    }

    @Test void capsulePreservesEndingProgressWhileDroppingOnlyItsOwnership() throws Exception {
        var owner = mock(AbstractPlayableSprite.class);
        var capsule = new Aiz2EndEggCapsuleInstance(100, 100);
        capsule.setServices(mock(ObjectServices.class));
        field(capsule, "nativeP2EndingPoseOwner").set(capsule, owner);
        field(capsule, "tailsEndingPoseApplied").set(capsule, true);
        field(capsule, "tailsEndingPoseObjectControlLocked").set(capsule, true);
        field(capsule, "endingPoseControlsReleased").set(capsule, false);
        capsule.releasePlayersForVisualDirection();
        assertEquals(true, field(capsule, "tailsEndingPoseApplied").get(capsule));
        assertEquals(false, field(capsule, "endingPoseControlsReleased").get(capsule));
        assertNull(field(capsule, "nativeP2EndingPoseOwner").get(capsule));
    }
}
