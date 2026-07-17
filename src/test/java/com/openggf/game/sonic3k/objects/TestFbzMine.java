package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzMine {
    @Test
    void proximityArmsThirtyOneUpdatesBeforeSameSlotExplosion() {
        PlayableEntity player = player(0x1000 - 0x10, 0x700 - 0x18, false);
        ObjectManager manager = org.mockito.Mockito.mock(ObjectManager.class);
        ObjectServices services = services(player, manager);
        FbzMineObjectInstance mine = mine(services);
        mine.setSlotIndex(42);

        assertEquals(0x1E, mine.armCountdownReload());
        assertFalse(mine.isArmed());
        assertEquals(1, mine.getPriorityBucket());

        mine.update(100, null);
        assertEquals(0, mine.mappingFrame(), "detection frame does not run the blink routine");
        assertEquals(0, mine.getCollisionFlags());
        for (int armedUpdate = 1; armedUpdate <= 30; armedUpdate++) {
            mine.update(100 + armedUpdate, null);
            assertFalse(mine.isArmed(), "mine remains blinking through armed update " + armedUpdate);
        }
        mine.update(131, null);
        assertTrue(mine.isArmed());
        assertEquals(0x8B, mine.getCollisionFlags(), "armed collision exists for exactly this frame");

        mine.update(132, null);
        assertTrue(mine.isDestroyed());
        assertEquals(-1, mine.getSlotIndex());
        var replacement = org.mockito.ArgumentCaptor.forClass(ObjectInstance.class);
        org.mockito.Mockito.verify(manager).addDynamicObjectAtSlot(replacement.capture(), org.mockito.Mockito.eq(42));
        assertNotSame(mine, replacement.getValue());
        org.mockito.Mockito.verify(services).playSfx(Sonic3kSfx.EXPLODE.id);
    }

    @Test
    void proximityUsesExactUnsignedBoundsAndSuppressesDebugPlayers() {
        assertTriggered(player(0x1000 + 0x0F, 0x700 + 7, false), true);
        assertTriggered(player(0x1000 - 0x10, 0x700 - 0x18, false), true);
        assertTriggered(player(0x1000 + 0x10, 0x700, false), false);
        assertTriggered(player(0x1000, 0x700 + 8, false), false);
        assertTriggered(player(0x1000, 0x700, true), false);
    }

    private static void assertTriggered(PlayableEntity player, boolean expected) {
        FbzMineObjectInstance mine = mine(services(player, org.mockito.Mockito.mock(ObjectManager.class)));
        mine.update(0, null);
        mine.update(1, null);
        assertEquals(expected ? 1 : 0, mine.mappingFrame());
    }

    private static FbzMineObjectInstance mine(ObjectServices services) {
        FbzMineObjectInstance mine = new FbzMineObjectInstance(
                new ObjectSpawn(0x1000, 0x700, 0xE1, 0, 0, false, 1));
        mine.setServices(services);
        return mine;
    }

    private static ObjectServices services(PlayableEntity player, ObjectManager manager) {
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.playerQuery())
                .thenReturn(new ObjectPlayerQuery(() -> player, List::of));
        org.mockito.Mockito.when(services.objectManager()).thenReturn(manager);
        return services;
    }

    private static PlayableEntity player(int x, int y, boolean debug) {
        PlayableEntity player = org.mockito.Mockito.mock(PlayableEntity.class);
        org.mockito.Mockito.when(player.getCentreX()).thenReturn((short) x);
        org.mockito.Mockito.when(player.getCentreY()).thenReturn((short) y);
        org.mockito.Mockito.when(player.isDebugMode()).thenReturn(debug);
        return player;
    }
}
