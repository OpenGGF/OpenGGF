package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestCoconutsInitialization {
    private CoconutsBadnikInstance create() {
        return new CoconutsBadnikInstance(new ObjectSpawn(0x300, 0x200, 0x9D, 0, 0, false, 0));
    }

    private PerObjectRewindSnapshot.CoconutsRewindExtra state(CoconutsBadnikInstance object) {
        return (PerObjectRewindSnapshot.CoconutsRewindExtra)
                object.captureRewindState().badnikSubclassExtra();
    }

    @Test
    void initReturnsBeforeIdleAndRewindPreservesThatPass() {
        var object = create();
        var beforeInit = object.captureRewindState();
        object.update(0, null);
        assertEquals(0x10, state(object).timer(), "Obj9D_Init writes $10 and returns");
        var afterInit = object.captureRewindState();
        object.update(1, null);
        assertEquals(0x0F, state(object).timer());

        var restored = create();
        restored.restoreRewindState(beforeInit);
        restored.update(0, null);
        assertEquals(afterInit, restored.captureRewindState());
        restored.restoreRewindState(afterInit);
        restored.update(1, null);
        assertEquals(object.captureRewindState(), restored.captureRewindState());
    }

    @Test
    void enteringThrowRangeOnIdleExpiryThrowsBeforeStartingClimb() {
        var object = create();
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) (0x300 - 0x60));
        object.update(0, player); // Obj9D_Init only.
        for (int i = 0; i < 0x10; i++) {
            object.update(i + 1, player);
        }
        when(player.getCentreX()).thenReturn((short) (0x300 - 0x5F));
        object.update(17, player);
        assertEquals(0x20, state(object).attackTimer(), "Obj9D_Idle checks range before timer expiry");
        assertEquals(8, state(object).timer());
        assertEquals(0, state(object).climbTableIndex());
        assertEquals(0x200, object.getY());
    }
    private void wirePlayers(CoconutsBadnikInstance object, AbstractPlayableSprite main,
                             AbstractPlayableSprite sidekick) {
        var camera = mock(com.openggf.camera.Camera.class);
        when(camera.getFocusedSprite()).thenReturn(main);
        object.setServices(new com.openggf.level.objects.TestObjectServices()
                .withCamera(camera).withSidekicks(java.util.List.of(sidekick)));
    }

    @Test
    void idleFacesNearerSidekickAndRewindRetainsTheDecision() {
        var object = create();
        var main = mock(AbstractPlayableSprite.class);
        var tails = mock(AbstractPlayableSprite.class);
        when(main.getCentreX()).thenReturn((short) 0x280);
        when(tails.getCentreX()).thenReturn((short) 0x310);
        wirePlayers(object, main, tails);
        object.update(0, main);
        object.update(1, main);
        org.junit.jupiter.api.Assertions.assertFalse(object.captureRewindState().badnikExtra().facingLeft());
        assertEquals(0x20, state(object).attackTimer());
        var snapshot = object.captureRewindState();
        var restored = create();
        wirePlayers(restored, main, tails);
        restored.restoreRewindState(snapshot);
        object.update(2, main);
        restored.update(2, main);
        assertEquals(object.captureRewindState(), restored.captureRewindState());
    }

    @Test
    void orientationTieKeepsMainPlayerAndRangeUsesTheRomWordWindow() {
        for (int offset : new int[]{-0x60, -0x5F, 0, 0x60, 0x61}) {
            var object = create();
            var main = mock(AbstractPlayableSprite.class);
            var tails = mock(AbstractPlayableSprite.class);
            when(main.getCentreX()).thenReturn((short) (0x300 + offset));
            when(tails.getCentreX()).thenReturn((short) (0x300 - offset));
            wirePlayers(object, main, tails);
            object.update(0, main);
            object.update(1, main);
            assertEquals(offset >= -0x5F && offset <= 0x60 ? 0x20 : 0,
                    state(object).attackTimer(), "main offset=" + offset);
            assertEquals(offset <= 0, object.captureRewindState().badnikExtra().facingLeft(),
                    "tie and equality select MainCharacter");
        }
    }


    @Test
    void coconutChildOwnsObj98SpawnAndKeepsTheLoadSubObjectMovementDelay() {
        var object=create();
        var player=mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short)0x2B0);
        var camera=mock(com.openggf.camera.Camera.class);
        when(camera.getFocusedSprite()).thenReturn(player);
        var manager=mock(com.openggf.level.objects.ObjectManager.class);
        object.setServices(new com.openggf.level.objects.TestObjectServices()
                .withCamera(camera).withDirectObjectManager(manager));
        object.update(0,player);
        object.update(1,player);
        for(int i=2;i<=10;i++) object.update(i,player);
        var captured=org.mockito.ArgumentCaptor.forClass(com.openggf.level.objects.ObjectInstance.class);
        org.mockito.Mockito.verify(manager).addDynamicObject(captured.capture());
        var child=org.junit.jupiter.api.Assertions.assertInstanceOf(
                Sonic2CoconutProjectileInstance.class,captured.getValue());
        // Obj9D_CreateCoconut (s2.asm:75366-75386): native $98, subobject row $20, x+11,y-13.
        assertEquals(0x98,child.getSpawn().objectId());
        assertEquals(0x20,child.getSpawn().subtype());
        assertEquals(0x30B,child.getX());assertEquals(0x1F3,child.getY());
        child.update(11,player);
        assertEquals(0x30B,child.getX());assertEquals(0x1F3,child.getY());
        var nativeDisplay=child.nativeDisplaySlot();
        assertEquals(0x84,nativeDisplay.renderFlags());assertEquals(3,nativeDisplay.mapping().frame());
        assertEquals(0x37D96,nativeDisplay.mapping().address());assertEquals(0x1F7,nativeDisplay.artTile());
        var snapshot=child.captureRewindState();
        var restored=child.recreateForRewind(new com.openggf.level.objects.RewindRecreateContext(
                child.getSpawn(),snapshot,new com.openggf.level.objects.StubObjectServices()));
        restored.restoreRewindState(snapshot);
        assertEquals(snapshot,restored.captureRewindState());
        assertEquals(child.getSpawn(),restored.getSpawn());
        assertEquals(nativeDisplay,restored.nativeDisplaySlot());
        child.update(12,player);
        restored.update(12,player);
        assertEquals(child.nativeDisplaySlot(),restored.nativeDisplaySlot());
        assertEquals(0x30A,child.getX());assertEquals(0x1F2,child.getY());
    }
}
