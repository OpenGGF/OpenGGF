package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.player.CdSpecialChunks.SpecialLoopRule;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ObjSonic_SpecialChunks' body (scddisasm src/Level/_Objects/Sonic/Main.asm:2510-2637)
 * on Palmtree Panic act 1 Past's IDs ($8C loop, $7F special loop, $1E both roll tunnels,
 * Scroll (Act 1 Past).asm SpecChunks) with the Palmtree Panic special-roll Y test.
 */
@com.openggf.tests.FullReset
@org.junit.jupiter.api.extension.ExtendWith({
        com.openggf.tests.RuntimeStateContaminationExtension.class,
        com.openggf.tests.SingletonResetExtension.class
})
class TestCdSpecialChunks {
    private static final CdSpecialChunks PPZ_PAST = new CdSpecialChunks(0x8C, 0x7F, 0x1E, 0x1E, 0x90,
            SpecialLoopRule.AIRBORNE_LOWER_PATH);

    private static Sonic at(int x, int y) {
        Sonic sonic = new Sonic("sonic", (short) 0, (short) 0);
        sonic.setCentreX((short) x);
        sonic.setCentreY((short) y);
        return sonic;
    }

    @Test
    void specialRollTunnelRollsOnlyFromInChunkYNinetyHex() {
        assertFalse(PPZ_PAST.apply(at(0x480, 0x28F), 0x1E), "y & $FF = $8F stays out of .RollTunnel");
        assertTrue(PPZ_PAST.apply(at(0x480, 0x290), 0x1E), "y & $FF = $90 reaches .RollTunnel");
        CdSpecialChunks otherZone = new CdSpecialChunks(0x8C, 0x7F, 0x1E, 0x1E, -1,
                SpecialLoopRule.AIRBORNE_LOWER_PATH);
        assertTrue(otherZone.apply(at(0x480, 0x210), 0x1E), "other zones branch straight to .RollTunnel");
    }

    @Test
    void loopChunkSwitchesPathByPositionThenAngle() {
        Sonic sonic = at(0x52B, 0x300);                    // x & $FF = $2B < $2C
        sonic.setLoopLowPlane(true);
        PPZ_PAST.apply(sonic, 0x8C);
        assertFalse(sonic.isLoopLowPlane(), ".CheckIfLeft clears bit 6");
        sonic.setCentreX((short) 0x5E0);                   // >= $E0
        PPZ_PAST.apply(sonic, 0x8C);
        assertTrue(sonic.isLoopLowPlane(), ".CheckIfRight sets bit 6");
        sonic.setCentreX((short) 0x580);
        sonic.setAngle((byte) 0x81);
        PPZ_PAST.apply(sonic, 0x8C);
        assertFalse(sonic.isLoopLowPlane(), ".HighPath clears bit 6 above $80");
        sonic.setAngle((byte) 0x80);
        PPZ_PAST.apply(sonic, 0x8C);
        assertTrue(sonic.isLoopLowPlane(), ".CheckAngle sets bit 6 at 1..$80");
        sonic.setAngle((byte) 0);
        sonic.setLoopLowPlane(false);
        PPZ_PAST.apply(sonic, 0x8C);
        assertFalse(sonic.isLoopLowPlane(), "a flat angle keeps the path");
    }

    @Test
    void specialLoopInTheAirAndAnyOtherChunkTakeTheLowerPath() {
        Sonic sonic = at(0x5E0, 0x300);
        sonic.setAir(true);
        sonic.setLoopLowPlane(true);
        PPZ_PAST.apply(sonic, 0x7F);
        assertFalse(sonic.isLoopLowPlane(), ".CheckIfInAir: airborne clears bit 6");
        sonic.setAir(false);
        PPZ_PAST.apply(sonic, 0x7F);
        assertTrue(sonic.isLoopLowPlane(), "grounded runs the loop position checks");
        PPZ_PAST.apply(sonic, 0x12);
        assertFalse(sonic.isLoopLowPlane(), "any other chunk clears bit 6");
    }
}
