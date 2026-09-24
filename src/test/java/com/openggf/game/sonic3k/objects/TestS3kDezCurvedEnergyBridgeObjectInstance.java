package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.NativePositionOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kDezCurvedEnergyBridgeObjectInstance {
    @Test
    void subtypeSevenStartsWithTheRomOneHundredSixtyPassWindow() {
        S3kDezCurvedEnergyBridgeObjectInstance bridge = bridge();
        bridge.setServices(new TestObjectServices());

        bridge.update(0, null);

        assertTrue(bridge.visibleForTest());
        assertEquals(159, bridge.onFramesLeftForTest());
        assertEquals(4, bridge.romObjectCodePointerHighWord());
    }

    @Test
    void activeFieldSelectsTheCurvedCollisionIndexesInsideItsBox() {
        S3kDezCurvedEnergyBridgeObjectInstance bridge = bridge();
        TestPlayableSprite player = player(0x100, 0x100);

        bridge.applyPlayer(player, false, true);

        assertEquals(0x0E, player.getTopSolidBit());
        assertEquals(0x0F, player.getLrbSolidBit());
        assertFalse(player.getAir());
    }

    @Test
    void fieldSwitchOffRestoresIndexesAndDropsAPlayerItOwned() {
        S3kDezCurvedEnergyBridgeObjectInstance bridge = bridge();
        TestPlayableSprite player = player(0x100, 0x100);
        player.setOnObject(true);
        bridge.applyPlayer(player, false, true);

        bridge.applyPlayer(player, false, false);

        assertEquals(0x0C, player.getTopSolidBit());
        assertEquals(0x0D, player.getLrbSolidBit());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
    }

    @Test
    void leavingTheBoxWhileFieldRemainsOnRestoresIndexesWithoutForcingAir() {
        S3kDezCurvedEnergyBridgeObjectInstance bridge = bridge();
        TestPlayableSprite player = player(0x100, 0x100);
        bridge.applyPlayer(player, false, true);
        NativePositionOps.writeXPosPreserveSubpixel(player, 0x300);

        bridge.applyPlayer(player, false, true);

        assertEquals(0x0C, player.getTopSolidBit());
        assertEquals(0x0D, player.getLrbSolidBit());
        assertFalse(player.getAir());
    }

    private static S3kDezCurvedEnergyBridgeObjectInstance bridge() {
        return new S3kDezCurvedEnergyBridgeObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x56, 0x07, 0, false, 0));
    }

    private static TestPlayableSprite player(int x, int y) {
        TestPlayableSprite player = new TestPlayableSprite();
        NativePositionOps.writeXPosPreserveSubpixel(player, x);
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        return player;
    }
}
