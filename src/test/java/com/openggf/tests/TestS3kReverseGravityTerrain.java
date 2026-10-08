package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.physics.ReverseGravity;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Step 2a-2 of the S3K Death Egg reverse-gravity slice: which probe is the floor.
 *
 * <p>{@code sub_11FD6} (sonic3k.asm:24127-24137) is the wrapper every "check the floor"
 * site in {@code SonicKnux_DoLevelCollision} calls. With {@code Reverse_gravity_flag}
 * ($FFFFF7C6) set it calls {@code Sonic_CheckCeiling} instead, and mirrors the angle the
 * probe returned. {@code sub_11FEE} (:24141-24151) is its opposite: the "check the
 * ceiling" wrapper becomes {@code Sonic_CheckFloor}, mirrored the same way. The ten and
 * nine callers of those two wrappers are what spread the swap across the whole airborne
 * collision routine, and Tails' and Knuckles' copies
 * ({@code Tails_DoLevelCollision}, {@code Knux_DoLevelCollision}) do the same.
 *
 * <p>The quadrant dispatch that picks between them is <em>not</em> mirrored:
 * {@code loc_11F00} (:24040-24048) feeds the raw {@code x_vel}/{@code y_vel} to
 * {@code GetArcTan}. Velocity is never inverted, so "falling" still selects the floor
 * branch — which now probes the ceiling.
 *
 * <p>The selector itself is asserted by {@code TestS3kReverseGravityProbeSelection}.
 * This class owns the angle mirror those wrappers apply to the angle they return.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityTerrain {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * The angle mirror {@code sub_11FD6}, {@code sub_11FEE} and
     * {@code Call_Player_AnglePos} all apply: {@code addi.b #$40,d0 / neg.b d0 /
     * subi.b #$40,d0}. It exchanges floor and ceiling and leaves both walls alone.
     */
    @Test
    void theAngleMirrorExchangesFloorAndCeilingAndFixesBothWalls() {
        assertEquals(0x80, ReverseGravity.mirrorAngle(0x00), "flat floor becomes flat ceiling");
        assertEquals(0x00, ReverseGravity.mirrorAngle(0x80), "and back");
        assertEquals(0x40, ReverseGravity.mirrorAngle(0x40), "the walls are the mirror's fixed points");
        assertEquals(0xC0, ReverseGravity.mirrorAngle(0xC0));
        assertEquals(0x60, ReverseGravity.mirrorAngle(0x20), "a $20 floor slope becomes a $60 ceiling slope");
        assertEquals(0x20, ReverseGravity.mirrorAngle(0x60));
        assertEquals(0xA0, ReverseGravity.mirrorAngle(0xE0));
        for (int angle = 0; angle < 0x100; angle++) {
            assertEquals(angle, ReverseGravity.mirrorAngle(ReverseGravity.mirrorAngle(angle)),
                    "the mirror is its own inverse at angle " + angle);
        }
    }

}
