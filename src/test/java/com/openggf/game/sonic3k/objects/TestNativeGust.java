package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestNativeGust {
    @Test void nativeWordRelativePressureRemainsLocalAcrossCoordinateWrap() {
        var gust = new NativeGustObjectInstance(new ObjectSpawn(0xFFC0, 0xFFF0, 0, 0, 1, false, 0));
        gust.setServices(new TestObjectServices());
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        NativePositionOps.writeXPosPreserveSubpixel(player, 32);
        NativePositionOps.writeYPosPreserveSubpixel(player, 16);
        player.setSubpixelRaw(0x5A00, 0x6B00);
        gust.update(0, player);
        assertEquals(42, player.getCentreX());
        assertEquals(16, player.getCentreY());
        assertEquals(0x5A00, player.getXSubpixelRaw());
        assertEquals(0x6B00, player.getYSubpixelRaw());
    }
    @Test void burstRunsForExactlyTheNativeSeventyDispatchPressurePhase() {
        var gust = new NativeGustObjectInstance(new ObjectSpawn(100, 100, 0, 0, 1, false, 0));
        gust.setServices(new TestObjectServices());
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setXSpeed((short) 0x345);
        player.setYSpeed((short) -0x456);
        player.setSubpixelRaw(0x5A00, 0x6B00);
        for (int tick = 0; tick < 70; tick++) {
            NativePositionOps.writeXPosPreserveSubpixel(player, 228);
            NativePositionOps.writeYPosPreserveSubpixel(player, 100);
            gust.update(tick, player);
            assertEquals(236, player.getCentreX());
            assertFalse(gust.isDestroyed());
        }
        gust.update(70, player);
        assertEquals(236, player.getCentreX());
        assertTrue(gust.isDestroyed());
        assertEquals(0x345, player.getXSpeed());
        assertEquals(-0x456, player.getYSpeed());
        assertEquals(0x5A00, player.getXSubpixelRaw());
        assertEquals(0x6B00, player.getYSubpixelRaw());
    }

    @Test void nativeConsumerRetainsDuckingAndFacingRangeGates() {
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        NativePositionOps.writeXPosPreserveSubpixel(player, 228);
        NativePositionOps.writeYPosPreserveSubpixel(player, 100);
        player.setAnimationId(8);
        CluckoidWindPressure.apply(100, 100, false, player);
        assertEquals(228, player.getCentreX());
        player.setAnimationId(0);
        CluckoidWindPressure.apply(100, 100, true, player);
        assertEquals(228, player.getCentreX());
        CluckoidWindPressure.apply(100, 164, false, player);
        assertEquals(228, player.getCentreX());
        CluckoidWindPressure.apply(100, 100, false, player);
        assertEquals(236, player.getCentreX());
    }
}
