package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSonic1SpinConveyorTeamDetach {
    @Test
    void spinningFrameDetachesEveryRiderWithoutTouchingNonRiders() throws Exception {
        Sonic1SpinConveyorObjectInstance conveyor = new Sonic1SpinConveyorObjectInstance(
                new ObjectSpawn(0x1000, 0x0400, 0x6F, 0, 0, false, 0));
        TestPlayableSprite main = player(false);
        TestPlayableSprite p2 = player(true);
        TestPlayableSprite p3 = player(true);
        TestPlayableSprite p4 = player(true);
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.getRidingObject(main)).thenReturn(conveyor);
        when(objectManager.getRidingObject(p2)).thenReturn(conveyor);
        when(objectManager.getRidingObject(p3)).thenReturn(null);
        when(objectManager.getRidingObject(p4)).thenReturn(conveyor);
        TestObjectServices services = new TestObjectServices() {
            @Override public ObjectManager objectManager() { return objectManager; }
        };
        services.withSidekicks(List.of(p2, p3, p4));
        conveyor.setServices(services);

        Method detach = Sonic1SpinConveyorObjectInstance.class.getDeclaredMethod(
                "detachRidingPlayer", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        detach.setAccessible(true);
        detach.invoke(conveyor, main);

        verify(objectManager).clearRidingObject(main);
        verify(objectManager).clearRidingObject(p2);
        verify(objectManager).clearRidingObject(p4);
    }

    private static TestPlayableSprite player(boolean cpu) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCpuControlled(cpu);
        return player;
    }
}
