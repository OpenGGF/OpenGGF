package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kDezConveyorBeltObjectInstance {
    @Test
    void subtypeSetsHalfWidthAndXFlipReversesTheTwoPixelStep() {
        assertEquals(0x90, belt(0x12, 0).halfWidthForTest());
        assertEquals(2, belt(0x12, 0).stepForTest());
        assertEquals(-2, belt(0x12, 1).stepForTest());
    }

    @Test
    void groundedPlayersAboveAndBelowMoveInOppositeDirections() {
        S3kDezConveyorBeltObjectInstance belt = belt(0x12, 0);
        TestPlayableSprite above = player(0x100, 0x0F0);
        TestPlayableSprite below = player(0x100, 0x110);

        belt.apply(above);
        belt.apply(below);

        assertEquals(0x102, above.getCentreX());
        assertEquals(0x0FE, below.getCentreX());
    }

    @Test
    void airborneAndOutOfWindowPlayersAreUntouched() {
        S3kDezConveyorBeltObjectInstance belt = belt(0x10, 0);
        TestPlayableSprite airborne = player(0x100, 0x0F0);
        airborne.setAir(true);
        TestPlayableSprite outside = player(0x200, 0x100);

        belt.apply(airborne);
        belt.apply(outside);

        assertEquals(0x100, airborne.getCentreX());
        assertEquals(0x200, outside.getCentreX());
    }

    private static S3kDezConveyorBeltObjectInstance belt(int subtype, int flags) {
        return new S3kDezConveyorBeltObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x50, subtype, flags, false, 0));
    }

    private static TestPlayableSprite player(int x, int y) {
        TestPlayableSprite player = new TestPlayableSprite();
        NativePositionOps.writeXPosPreserveSubpixel(player, x);
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        return player;
    }
}
