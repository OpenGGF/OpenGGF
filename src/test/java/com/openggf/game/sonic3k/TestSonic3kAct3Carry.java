package com.openggf.game.sonic3k;

import com.openggf.game.ShieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic3kAct3Carry {
    @Test
    void carriesAndConsumesTheFourRomValuesOnce() {
        Sonic3kAct3Carry carry = new Sonic3kAct3Carry();
        carry.arm(73, 12_345, ShieldType.LIGHTNING);
        assertTrue(carry.active());
        var saved = carry.consume();
        assertNotNull(saved);
        assertEquals(73, saved.rings());
        assertEquals(12_345, saved.timerFrames());
        assertEquals(ShieldType.LIGHTNING, saved.shield());
        assertFalse(carry.active());
        assertNull(carry.consume());
    }

    @Test
    void rewindRestoresAnArmedCarry() {
        Sonic3kAct3Carry carry = new Sonic3kAct3Carry();
        carry.arm(18, 600, ShieldType.FIRE);
        var snapshot = carry.capture();
        assertNotNull(carry.consume());
        carry.restore(snapshot);
        assertEquals(snapshot, carry.consume());
    }
}
