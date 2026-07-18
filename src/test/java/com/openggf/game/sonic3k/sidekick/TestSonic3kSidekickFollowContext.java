package com.openggf.game.sonic3k.sidekick;

import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.game.sonic3k.objects.MhzTwistedVineObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSonic3kSidekickFollowContext {

    @Test
    void sklSlot03TwistedVineDoesNotActivateAizHollowTreeBridge() {
        AbstractPlayableSprite sidekick = sidekickWithActiveObject(
                new MhzTwistedVineObjectInstance(new ObjectSpawn(
                        0x1000, 0x0500, 0x03, 0, 0, false, 0)));
        assertFalse(Sonic3kSidekickFollowContext.isObjectOrderFollowSteeringContext(sidekick, sidekick),
                "zone-set aliases sharing slot $03 must not inherit AIZ hollow-tree CPU grace");
    }

    @Test
    void actualAizHollowTreeActivatesBridge() {
        AbstractPlayableSprite sidekick = sidekickWithActiveObject(
                new AizHollowTreeObjectInstance(new ObjectSpawn(
                        0x1000, 0x0500, 0x03, 0, 0, false, 0)));
        assertTrue(Sonic3kSidekickFollowContext.isObjectOrderFollowSteeringContext(sidekick, sidekick));
    }

    private static AbstractPlayableSprite sidekickWithActiveObject(com.openggf.level.objects.ObjectInstance object) {
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.getActiveObjects()).thenReturn(List.of(object));
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getObjectManager()).thenReturn(objectManager);
        AbstractPlayableSprite sidekick = mock(AbstractPlayableSprite.class);
        when(sidekick.currentLevelManager()).thenReturn(levelManager);
        when(sidekick.getCentreX()).thenReturn((short) 0x1000);
        when(sidekick.getCentreY()).thenReturn((short) 0x0500);
        return sidekick;
    }
}
