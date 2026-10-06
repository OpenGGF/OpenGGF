package slaytherobotnik.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The ported movement routines step exactly as the ROM's do, in its own fixed-point units. */
class RomBodyTest {
    @Test
    void moveSpriteMovesByTheOldSpeedThenAddsGravity() {
        RomBody b = new RomBody(0, 0, 0x100, -0x400);
        b.moveSprite();
        assertEquals(-0x400, b.y, "MoveSprite adds the speed it had before gravity");
        assertEquals(-0x400 + 0x38, b.yVel);
        assertEquals(0x100, b.x);
    }

    @Test
    void theRabbitHopsThirtyEightFramesAndAboutThirtyEightPixelsHigh() {
        // Obj_Animal type 0: hops at -$400 under MoveSprite's $38 gravity.
        RomBody rabbit = new RomBody(0, 0, 0, -0x400);
        int frames = 0;
        int highest = 0;
        int rising = 0;
        while (frames < 200) {
            int frame = rabbit.hop(0, -0x400);
            frames++;
            highest = Math.min(highest, rabbit.y);
            rising += frame;
            if (rabbit.yVel == -0x400) {
                break;
            }
        }
        assertEquals(38, frames, "frames from take-off to landing");
        assertEquals(-9880, highest, "peak, in 1/256 pixels");
        assertEquals(18, rising, "frames shown rising (mapping frame 1)");
        assertEquals(0, rabbit.y, "lands on the floor");
    }

    @Test
    void aSpilledRingBouncesWithThreeQuartersOfItsSpeedOnlyWhenTheFloorIsTested() {
        RomBody ring = new RomBody(0, 99, 0, 0x400);
        assertTrue(ring.fallRing(true, 100));
        assertEquals(100 * 256, ring.y, "put back on the floor");
        assertEquals(-(0x418 - (0x418 >> 2)), ring.yVel, "MoveSprite2, +$18, then three quarters back up");

        RomBody sinking = new RomBody(0, 99, 0, 0x400);
        assertFalse(sinking.fallRing(false, 100), "no floor test this frame");
        assertEquals(99 * 256 + 0x400, sinking.y, "it sinks until the next test");
        assertEquals(0x418, sinking.yVel);
    }

    @Test
    void anAttractedRingPullsHarderWhileMovingAway() {
        RomBody away = new RomBody(0, 0, -0x100, 0);
        away.attractTo(50, 0);
        assertEquals(-0x100 + 0xC0, away.xVel, "$C0 while moving away from the target");
        RomBody towards = new RomBody(0, 0, 0x100, 0);
        towards.attractTo(50, 0);
        assertEquals(0x100 + 0x30, towards.xVel, "$30 while already heading for it");
        assertEquals(0x130, towards.x, "then MoveSprite2");
    }

    @Test
    void aMonitorIconRisesForThirtyTwoFrames() {
        RomBody icon = new RomBody(0, 0, 0, -0x300);
        int frames = 0;
        while (icon.riseIcon()) {
            frames++;
        }
        assertEquals(32, frames, "$300 / $18");
        assertEquals(-(0x300 * 32 - 0x18 * 32 * 31 / 2), icon.y);
    }

    @Test
    void detailsSplitIntoAVerbAndAnArgument() {
        assertEquals("rematch", TimedPicture.verb("rematch:aiz:fire_breath"));
        assertEquals("aiz:fire_breath", TimedPicture.argument("rematch:aiz:fire_breath"));
        assertEquals("heal", TimedPicture.verb("heal"));
        assertEquals("", TimedPicture.argument("heal"));
    }
}
