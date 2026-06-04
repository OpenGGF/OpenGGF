package com.openggf.tests.objects;

import static org.junit.jupiter.api.Assertions.*;
import com.openggf.game.sonic2.objects.S2ObjectWindowing;
import org.junit.jupiter.api.Test;

class TestS2ObjectWindowing {

    @Test
    void loadBaseIsCameraMaskedWithoutSubtract() {
        // camRounded = Camera_X_pos & $FF80 (NO -$80 on the load base) — s2.asm:33026
        assertEquals(0x1500, S2ObjectWindowing.loadCoarse(0x1540));
        assertEquals(0x1500, S2ObjectWindowing.loadCoarse(0x157F));
    }

    @Test
    void unloadCoarseSubtracts80FirstThenMasks() {
        // Camera_X_pos_coarse = (Camera_X_pos - $80) & $FF80 — s2.asm MarkObjGone base
        assertEquals(0x1480, S2ObjectWindowing.unloadCoarse(0x1540)); // (0x1540-0x80)&0xFF80 = 0x14C0&0xFF80=0x1480
        assertEquals(0x1500, S2ObjectWindowing.unloadCoarse(0x1580)); // (0x1580-0x80)&0xFF80 = 0x1500
    }

    @Test
    void forwardLoadEdgeIsCoarsePlus280_trimEdgeIsCoarseMinus80() {
        int cam = 0x1500; // already chunk-aligned
        assertEquals(0x1500 + 0x280, S2ObjectWindowing.forwardLoadEdge(cam));
        assertEquals(0x1500 - 0x80,  S2ObjectWindowing.leftTrimEdge(cam));
        // NOT camRounded - 0x300:
        assertNotEquals(0x1500 - 0x300, S2ObjectWindowing.leftTrimEdge(cam));
    }

    @Test
    void backwardLoadEdgeIsCoarseMinus80_rightTrimEdgeIsCoarsePlus280() {
        int cam = 0x1500;
        assertEquals(0x1500 - 0x80,  S2ObjectWindowing.backwardLoadEdge(cam));
        assertEquals(0x1500 + 0x280, S2ObjectWindowing.rightTrimEdge(cam));
        assertNotEquals(0x1500 + 0x300, S2ObjectWindowing.rightTrimEdge(cam));
    }

    @Test
    void markObjGone_firstDeleteBucketIs300_native() {
        // (x & $FF80) - Camera_X_pos_coarse > $280  ⇒ first deleting bucket = $300 (value is $80-coarse)
        int cam = 0x1500;            // unloadCoarse(0x1500) = (0x1500-0x80)&0xFF80 = 0x1480
        int base = S2ObjectWindowing.unloadCoarse(cam); // 0x1480
        // object exactly at base + $280 → compare == $280 → NOT > $280 → stays
        assertFalse(S2ObjectWindowing.markObjGone(base + 0x280, cam));
        // object at base + $300 (next $80 bucket) → > $280 → delete
        assertTrue(S2ObjectWindowing.markObjGone(base + 0x300, cam));
    }

    @Test
    void chkLoadObj_alreadyLoadedContinuesScan_onlyFullStops() {
        // respawn bit already set (object already loaded) → CONTINUE (advance list ptr by 6), success
        assertEquals(S2ObjectWindowing.LoadOutcome.ALREADY_LOADED_CONTINUE,
                S2ObjectWindowing.chkLoadObj(/*respawnBitAlreadySet*/ true, /*slotAllocatable*/ false));
        // not yet loaded + a free slot → LOADED
        assertEquals(S2ObjectWindowing.LoadOutcome.LOADED,
                S2ObjectWindowing.chkLoadObj(false, true));
        // not loaded + SST full → STOP (only allocation failure halts the scan)
        assertEquals(S2ObjectWindowing.LoadOutcome.SST_FULL_STOP,
                S2ObjectWindowing.chkLoadObj(false, false));
    }
}
