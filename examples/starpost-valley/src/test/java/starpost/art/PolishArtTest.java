package starpost.art;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import starpost.farm.BeltRunner;

/**
 * The ROM-art polish rules (design doc §20), engine-free. The game reads its animation tables
 * from the ROM; these tests carry copies of them, transcribed from skdisasm's listing, so the
 * rules can be checked without one.
 */
class PolishArtTest {
    /** Ani_DashSplashDrown ($18DC0, 52 bytes). */
    private static byte[] dustScripts() {
        return bytes(0x00, 0x0A, 0x00, 0x0D, 0x00, 0x19, 0x00, 0x22, 0x00, 0x28,
                0x1F, 0x00, 0xFF,
                0x03, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0xFD, 0x00,
                0x01, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0xFF,
                0x03, 0x11, 0x12, 0x13, 0x14, 0xFC,
                0x05, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0xFD, 0x00, 0x00);
    }

    /** Obj_Tails_Tail_AniSelection ($16164, 50 bytes). */
    private static byte[] tailsSelection() {
        int[] table = new int[50];
        table[0x02] = 3;
        table[0x03] = 3;
        table[0x04] = 9;
        table[0x05] = 1;
        table[0x07] = 2;
        table[0x08] = 1;
        table[0x09] = 7;
        table[0x0D] = 8;
        table[0x14] = 0x0A;
        table[0x20] = 0x0B;
        table[0x21] = 0x0C;
        table[0x22] = 0x0B;
        table[0x23] = 0x0C;
        table[0x24] = 0x0B;
        return bytes(table);
    }

    /** AniTails_Tail ($16196, 100 bytes). */
    private static byte[] tailsScripts() {
        return bytes(0x00, 0x1A, 0x00, 0x1D, 0x00, 0x24, 0x00, 0x2C, 0x00, 0x32, 0x00, 0x38, 0x00, 0x3E, 0x00, 0x44,
                0x00, 0x4A, 0x00, 0x50, 0x00, 0x56, 0x00, 0x5C, 0x00, 0x60,
                0x20, 0x00, 0xFF,
                0x07, 0x22, 0x23, 0x24, 0x25, 0x26, 0xFF,
                0x03, 0x22, 0x23, 0x24, 0x25, 0x26, 0xFD, 0x01,
                0xFC, 0x05, 0x06, 0x07, 0x08, 0xFF,
                0x03, 0x09, 0x0A, 0x0B, 0x0C, 0xFF,
                0x03, 0x0D, 0x0E, 0x0F, 0x10, 0xFF,
                0x03, 0x11, 0x12, 0x13, 0x14, 0xFF,
                0x02, 0x01, 0x02, 0x03, 0x04, 0xFF,
                0x02, 0x1A, 0x1B, 0x1C, 0x1D, 0xFF,
                0x09, 0x1E, 0x1F, 0x20, 0x21, 0xFF,
                0x09, 0x29, 0x2A, 0x2B, 0x2C, 0xFF,
                0x01, 0x27, 0x28, 0xFF,
                0x00, 0x27, 0x28, 0xFF);
    }

    private static TailsTails tails() {
        return new TailsTails(tailsSelection(), tailsScripts());
    }

    @Test
    void tailsFollowTheRomsSelectionForEveryBodyAnimation() {
        int[][] cases = {
            {Anim.WALK, 0}, {Anim.RUN, 0}, {Anim.ROLL, 3}, {Anim.PUSH, 9}, {Anim.WAIT, 1}, {Anim.DUCK, 1},
            {Anim.LOOK_UP, 2}, {Anim.SPINDASH, 7}, {Anim.SKID, 8}, {Anim.SPRING, 0}, {Anim.HURT, 0},
            {Anim.TAILS_FLY, 0x0B}, {Anim.TAILS_FLY_UP, 0x0C}, {Anim.TAILS_TIRED, 0x0B},
        };
        for (int[] c : cases) {
            TailsTails t = tails();
            t.update(c[0], false, 1, 0);
            assertEquals(c[1], t.script(), "body animation $" + Integer.toHexString(c[0]));
        }
        TailsTails walking = tails();
        walking.update(Anim.WALK, false, 3, 0);
        assertEquals(0, walking.frame(), "walking frames carry their own tails: blank");
    }

    @Test
    void flyingTailsFlapAndTiredOnesStillShow() {
        // AniTails_Tail0B: delay 1, frames $27 $28; 0C: delay 0 (rising flaps twice as fast).
        assertArrayEquals(new int[] {0x27, 0x27, 0x28, 0x28, 0x27}, frames(Anim.TAILS_FLY, 5));
        assertArrayEquals(new int[] {0x27, 0x28, 0x27, 0x28}, frames(Anim.TAILS_FLY_UP, 4));
        assertArrayEquals(new int[] {0x27, 0x27, 0x28, 0x28}, frames(Anim.TAILS_TIRED, 4));
        assertArrayEquals(new int[] {1, 1, 1, 2, 2, 2, 3}, frames(Anim.SPINDASH, 7));
        assertEquals(Anim.TAILS_FLY_UP, Anim.flying(false, -1));
        assertEquals(Anim.TAILS_FLY, Anim.flying(false, 0.5f));
        assertEquals(Anim.TAILS_TIRED, Anim.flying(true, -1));
    }

    @Test
    void lookingUpFlicksOnceThenSwishes() {
        TailsTails t = tails();
        for (int i = 0; i < 21; i++) {
            t.update(Anim.LOOK_UP, false, 0, 0);
        }
        assertEquals(1, t.script(), "Flick's $FD hands over to the Swish");
    }

    @Test
    void rollingTailsTrailAlongHisPath() {
        // Facing right and rolling right: frames 5-8, unflipped.
        TailsTails right = tails();
        right.update(Anim.ROLL, false, 6, 0);
        assertEquals(5, right.frame());
        // Rising straight up: the third group ($D-$10).
        TailsTails up = tails();
        up.update(Anim.ROLL, false, 0, -6);
        assertEquals(0x0D, up.frame());
        // Diagonally up and forward: the second group.
        TailsTails diagonal = tails();
        diagonal.update(Anim.ROLL, false, 4, -4);
        assertEquals(0x09, diagonal.frame());
        // Every fourth frame the next of the script's four.
        for (int i = 0; i < 4; i++) {
            right.update(Anim.ROLL, false, 6, 0);
        }
        assertEquals(6, right.frame());
        assertEquals(0x40, TailsTails.arcTan(0, 0), "GetArcTan_Zero");
        assertEquals(0xC0, TailsTails.arcTan(0, -1));
    }

    @Test
    void theSpinDashCloudLoopsItsSevenFramesTwoTicksEach() {
        Dust dust = new Dust(dustScripts());
        int[] seen = new int[16];
        for (int i = 0; i < seen.length; i++) {
            dust.update(true, false, false, false, 100, 100);
            seen[i] = dust.dashFrame();
        }
        assertArrayEquals(new int[] {0x0A, 0x0A, 0x0B, 0x0B, 0x0C, 0x0C, 0x0D, 0x0D, 0x0E, 0x0E, 0x0F, 0x0F, 0x10, 0x10,
                0x0A, 0x0A}, seen);
        dust.update(false, false, false, false, 100, 100);
        assertEquals(0, dust.dashFrame(), "releasing the dash goes back to the blank animation");
        dust.update(true, false, false, true, 100, 100);
        assertEquals(0, dust.dashFrame(), "none while short of air (air_left below 12)");
    }

    @Test
    void aSkidDropsAPuffEveryFourthFrameWhereHeWas() {
        Dust dust = new Dust(dustScripts());
        for (int i = 0; i < 16; i++) {
            dust.update(false, true, false, false, 100 + i * 4, 50);
        }
        assertEquals(4, dust.puffCount(), "frames 0, 4, 8 and 12 of the skid");
        float[] first = dust.puff(0);
        assertEquals(100, first[0], "left where he was when it dropped");
        assertEquals(50 + 0x10, first[1], "16 pixels below his y");
        assertEquals(0x14, first[2], "15 ticks old: its last frame");
        assertEquals(0x11, dust.puff(3)[2], "the newest is on its first frame");
        dust.update(false, false, false, false, 0, 0);
        assertEquals(3, dust.puffCount(), "the script's $FC ends each puff after 16 ticks");
        Dust wet = new Dust(dustScripts());
        for (int i = 0; i < 16; i++) {
            wet.update(false, true, true, false, 100, 50);
        }
        assertEquals(0, wet.puffCount(), "no puffs under water");
    }

    @Test
    void brakingHardOnTheFarmSkidsFacingTheWayHeSlides() {
        // FixBugs off: the angle check overwrites the speed's low byte before the $400 compare.
        assertTrue(BeltRunner.skidsAt(4.0f));
        assertFalse(BeltRunner.skidsAt(3.99f), "from the right it takes $400");
        assertTrue(BeltRunner.skidsAt(-3.01f), "from the left a little over $300 is enough");
        assertFalse(BeltRunner.skidsAt(-3.0f));
        BeltRunner r = new BeltRunner(1000, 10);
        for (int i = 0; i < 200; i++) {
            r.step(0, 5000, 60, false, true, false, false, false, false);
        }
        r.step(0, 5000, 60, true, false, false, false, false, false);
        assertEquals(BeltRunner.SKID_TICKS, r.skid);
        assertFalse(r.facingLeft, "he faces his slide while braking");
        for (int i = 0; i < 40 && r.speed >= 0; i++) {
            assertTrue(r.skid > 0 || r.speed < 0.5f);
            r.step(0, 5000, 60, true, false, false, false, false, false);
        }
        assertTrue(r.facingLeft, "turned once the slide ran out");
        assertEquals(0, r.skid, "turning round ends the skid");
        BeltRunner gentle = new BeltRunner(1000, 10);
        for (int i = 0; i < 40; i++) {
            gentle.step(0, 5000, 60, false, true, false, false, false, false);
        }
        gentle.step(0, 5000, 60, true, false, false, false, false, false);
        assertEquals(0, gentle.skid, "a gentle turn does not skid");
    }

    private static int[] frames(int body, int ticks) {
        TailsTails t = tails();
        int[] out = new int[ticks];
        for (int i = 0; i < ticks; i++) {
            t.update(body, false, 0, 0);
            out[i] = t.frame();
        }
        return out;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
