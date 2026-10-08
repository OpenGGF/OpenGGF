package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZSmashingSpikePlatform} (sonic3k.asm:88538-88651, ROM {@code $433E8}).
 *
 * <p>Every expectation is the routine's own arithmetic, read from the disassembly: the fall is
 * {@code y_vel += $40} a frame with the PREVIOUS velocity applied, so after {@code n} updates the
 * whole-pixel displacement is {@code floor(n(n-1)/8)} -- the closed form of
 * {@code sum(k * $40 << 8) >> 16} for {@code k} in {@code 0..n-1}. Nothing here comes from a trace
 * or from the class under test.
 */
class TestLrzSmashingSpikePlatformObjectInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.LBZ_GATE_LASER;
    private static final int BASE_X = 0x0B20;
    private static final int BASE_Y = 0x0640;
    /** The smallest of Lava Reef's ten act 1 subtypes; {@code $09 << 3} is 72 pixels. */
    private static final int SUBTYPE = 0x09;

    /** {@code moveq #0,d0 / move.b subtype(a0),d0 / lsl.w #3,d0} (sonic3k.asm:88546-88549). */
    @Test
    void initLatchesThePlacedYAndTheSubtypeShiftedLeftThree() {
        LrzSmashingSpikePlatformObjectInstance block = block(SUBTYPE);
        assertEquals(BASE_Y, block.baseY(), "$46(a0)");
        assertEquals(0x48, block.fallDistance(), "$38(a0) = subtype << 3");
        assertEquals(0, block.offsetPixels());
        assertEquals(BASE_Y, block.getCentreY(), "the block starts on its placed Y");
        assertFalse(block.smashed(), "$32(a0) starts clear");
    }

    /** Lava Reef's widest act 1 subtype, {@code $1D}, drops {@code $E8} pixels. */
    @Test
    void theWidestPlacedSubtypeDropsTwoHundredAndThirtyTwoPixels() {
        assertEquals(0xE8, block(0x1D).fallDistance());
    }

    /**
     * {@code loc_43128} (:88555-88562). The displacement is the motion, not the velocity field:
     * this asserts {@code y_pos} itself against the closed form of the ROM's accumulation.
     */
    @Test
    void theFallIsTheRomsAccumulatedVelocityAndNotAConstantSpeed() {
        LrzSmashingSpikePlatformObjectInstance block = block(0x1D);
        block.setServices(services());
        for (int frame = 1; frame <= 20; frame++) {
            block.update(frame, null);
            int expected = frame * (frame - 1) / 8;
            assertEquals(expected, block.offsetPixels(), "pixels fallen after " + frame + " frames");
            assertEquals(BASE_Y + expected, block.getCentreY(), "y_pos after " + frame + " frames");
            assertEquals(frame * 0x40, block.yVel(), "y_vel after " + frame + " frames");
        }
    }

    /**
     * {@code cmp.w $38(a0),d2 / blo} with {@code $38 = $48}: {@code n(n-1)/8} first reaches 72 at
     * {@code n = 25} (600/8 = 75), and frame 24 is still short (552/8 = 69). Landing clears
     * {@code y_vel}, pins {@code $34}'s high word to {@code $38} and loads the thirty-frame hold
     * (:88567-88571).
     */
    @Test
    void landingHappensOnTheFirstFrameThePixelCountReachesTheTarget() {
        LrzSmashingSpikePlatformObjectInstance block = block(SUBTYPE);
        block.setServices(services());
        for (int frame = 1; frame <= 24; frame++) {
            block.update(frame, null);
        }
        assertEquals(69, block.offsetPixels(), "frame 24 is still 3 pixels short");
        assertFalse(block.smashed());

        block.update(25, null);
        assertTrue(block.smashed(), "$32(a0)");
        assertEquals(0x48, block.offsetPixels(), "move.w $38(a0),$34(a0) pins the overshoot");
        assertEquals(BASE_Y + 0x48, block.getCentreY());
        assertEquals(0, block.yVel());
        assertEquals(30, block.holdTimer(), "move.w #30,$3A(a0)");
    }

    /**
     * {@code RawAni_43196} (:88589) and the {@code beq} that follows the {@code move.b} (:88584):
     * the twenty-entry table is stepped once a frame and the trailing zero freezes
     * {@code anim_frame}, so the block sits on frame 0 for the last eleven of the thirty hold
     * frames.
     */
    @Test
    void theSquashAnimationWalksTheRomTableAndStopsOnItsTrailingZero() {
        int[] table = {2, 4, 6, 7, 7, 7, 7, 6, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0};
        LrzSmashingSpikePlatformObjectInstance block = landed();

        for (int i = 0; i < table.length; i++) {
            block.update(100 + i, null);
            assertEquals(table[i], block.mappingFrame(), "RawAni_43196 entry " + i);
            assertEquals(29 - i, block.holdTimer());
        }
        for (int i = table.length; i < 30; i++) {
            block.update(100 + i, null);
            assertEquals(0, block.mappingFrame(), "the table's zero entry freezes anim_frame");
        }
        assertEquals(0, block.holdTimer());
        assertEquals(0x48, block.offsetPixels(), "the hold does not move the block");
    }

    /**
     * {@code loc_431AA} (:88592-88599): one whole pixel a frame off {@code $34}'s high word, with
     * {@code addq.b #8 / andi.b #8} flickering {@code mapping_frame} between 8 and 0. At zero
     * {@code loc_431D4} clears {@code $32} and the next update starts a new fall (:88605-88607).
     */
    @Test
    void theRiseIsOnePixelAFrameAndEndsByRestartingTheFall() {
        LrzSmashingSpikePlatformObjectInstance block = landed();
        for (int i = 0; i < 30; i++) {
            block.update(200 + i, null);
        }
        assertEquals(0, block.holdTimer());

        for (int i = 1; i <= 0x48; i++) {
            block.update(300 + i, null);
            assertEquals(0x48 - i, block.offsetPixels(), "pixels remaining after rise frame " + i);
            assertEquals(BASE_Y + 0x48 - i, block.getCentreY());
            assertEquals((i % 2) == 1 ? 8 : 0, block.mappingFrame(), "the 8/0 flicker");
        }
        assertTrue(block.smashed(), "the rise is still the smashed phase at zero offset");

        block.update(400, null);
        assertFalse(block.smashed(), "loc_431D4 clears $32(a0)");
        assertEquals(0, block.mappingFrame());
        assertEquals(0, block.offsetPixels());

        block.update(401, null);
        assertEquals(0x40, block.yVel(), "the next fall starts from y_vel 0 again");
    }

    /**
     * {@code d1 = width_pixels + $B}, {@code d2 = height_pixels - (mapping_frame & 7)},
     * {@code d3 = d2 + 1} (:88612-88621). The squash frames shrink the solid box with the art, and
     * the rise's frame 8 masks back to zero.
     */
    @Test
    void theSolidBoxShrinksWithTheSquashFrame() {
        LrzSmashingSpikePlatformObjectInstance block = block(SUBTYPE);
        block.setServices(services());
        assertEquals(0x4B, block.getSolidParams().halfWidth(), "d1 = $40 + $B");
        assertEquals(0x20, block.getSolidParams().airHalfHeight(), "mapping_frame 0");
        assertEquals(0x21, block.getSolidParams().groundHalfHeight());

        LrzSmashingSpikePlatformObjectInstance smashing = landed();
        smashing.update(500, null);
        assertEquals(2, smashing.mappingFrame());
        assertEquals(0x1E, smashing.getSolidParams().airHalfHeight(), "$20 - 2");
        assertEquals(0x1F, smashing.getSolidParams().groundHalfHeight());
        for (int i = 0; i < 3; i++) {
            smashing.update(501 + i, null);
        }
        assertEquals(7, smashing.mappingFrame());
        assertEquals(0x19, smashing.getSolidParams().airHalfHeight(), "$20 - 7");
    }

    /**
     * {@code swap d6 / andi.w #4|8,d6 / sub_24280} (:88626-88640). Bits 18 and 19 are set only by
     * {@code loc_1E10E}'s {@code d4 = d6 + $F} (sonic3k.asm:41578-41583), the branch that drives an
     * overlapping player downward. {@code sub_24280} then rewinds the complete 16.16 {@code y_pos}
     * by this frame's 8.8 {@code y_vel} before {@code HurtCharacter} (sonic3k.asm:49213-49220).
     */
    @Test
    void aPlayerDrivenDownwardIsHurtWithTheFixedPointRewind() {
        LrzSmashingSpikePlatformObjectInstance block = block(SUBTYPE);
        block.setServices(services());
        RecordingPlayer player = new RecordingPlayer();
        player.setCentreY((short) 0x0600);
        player.setSubpixelRaw(0, 0x8000);
        player.setYSpeed((short) -0x0200);

        block.onSolidContact(player, new SolidContact(false, false, true, false, false), 12);

        assertEquals(0x0602, player.hurtY, "y_pos rewound by y_vel << 8");
        assertEquals(0x8000, player.hurtYSub);
        assertEquals(BASE_X, player.hurtSourceX, "HurtCharacter's source is the block's x_pos");
    }

    /** The same contact shapes the {@code andi.w #4|8} rejects must not hurt anybody. */
    @Test
    void standingAndSidePushContactsDoNotHurt() {
        LrzSmashingSpikePlatformObjectInstance block = block(SUBTYPE);
        block.setServices(services());
        RecordingPlayer standing = new RecordingPlayer();
        block.onSolidContact(standing, new SolidContact(true, false, false, true, false), 12);
        assertEquals(-1, standing.hurtY, "a player standing on the block is not crushed");

        RecordingPlayer pushed = new RecordingPlayer();
        block.onSolidContact(pushed, new SolidContact(false, true, false, false, true), 12);
        assertEquals(-1, pushed.hurtY, "a side push is not the loc_1E10E branch");
    }

    /** {@code move.w #$280,priority(a0)} and {@code make_art_tile(ArtTile_LRZMisc,2,0)} (:88540). */
    @Test
    void renderStateIsTheInitWrites() {
        LrzSmashingSpikePlatformObjectInstance block = block(SUBTYPE);
        assertEquals(0x40, block.getOnScreenHalfWidth(), "width_pixels");
        assertEquals(0x20, block.getOnScreenHalfHeight(), "height_pixels");
        assertFalse(block.isHighPriority(), "palette 2, priority bit clear");
    }

    private static LrzSmashingSpikePlatformObjectInstance landed() {
        LrzSmashingSpikePlatformObjectInstance block = block(SUBTYPE);
        block.setServices(services());
        for (int frame = 1; frame <= 25; frame++) {
            block.update(frame, null);
        }
        return block;
    }

    private static LrzSmashingSpikePlatformObjectInstance block(int subtype) {
        return new LrzSmashingSpikePlatformObjectInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, subtype, 0, false, 0));
    }

    private static TestObjectServices services() {
        return new TestObjectServices().withIsolatedObjectManager();
    }

    private static final class RecordingPlayer extends TestPlayableSprite {
        private int hurtY = -1;
        private int hurtYSub = -1;
        private int hurtSourceX = -1;

        @Override
        public boolean applyHurtOrDeath(int sourceX, DamageCause cause, boolean hadRings) {
            hurtY = getCentreY() & 0xFFFF;
            hurtYSub = getYSubpixelRaw();
            hurtSourceX = sourceX;
            return true;
        }
    }
}
