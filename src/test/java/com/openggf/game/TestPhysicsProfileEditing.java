package com.openggf.game;

import com.openggf.game.rules.GameRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestPhysicsProfileEditing {
    @Test void editsRetainEveryUnchangedComponent() {
        var original = PhysicsProfile.SONIC_2_TAILS;
        var edited = original.withMax(0x480);
        assertEquals(original, edited.withMax(original.max()));
        assertEquals(0x600, original.max());
        assertEquals(original, original.toBuilder().build());
    }
    @Test void groupedBuilderRejectsOverflowWithoutTruncation() {
        assertThrows(IllegalArgumentException.class, () -> PhysicsProfile.builder().max(32768));
        assertThrows(IllegalArgumentException.class, () -> PhysicsProfile.builder().movement(-1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> PhysicsProfile.builder().shape(1, 128, 1, 1));
        var p = PhysicsProfile.builder().shape(8, 16, 6, 12).build();
        assertEquals(32, p.runHeight());
        assertEquals(24, p.rollHeight());
    }
    @Test void scopedTransformPreservesRulesAndOptionalInitSemantics() {
        PhysicsProvider stock = new PhysicsProvider() {
            public PhysicsProfile getProfile(String c) { return PhysicsProfile.SONIC_2_SONIC; }
            public PhysicsProfile getInitProfile(String c) { return c.equals("tails") ? null : PhysicsProfile.SONIC_3K_SONIC_INIT; }
            public PhysicsModifiers getModifiers() { return PhysicsModifiers.STANDARD; }
            public GameRules getRules() { return GameRules.SONIC_2; }
        };
        var edited = stock.transform("sonic"::equals, p -> p.withMax(0x480));
        assertEquals(0x480, edited.getProfile("sonic").max());
        assertEquals(0x480, edited.getInitProfile("sonic").max());
        assertSame(stock.getProfile("tails"), edited.getProfile("tails"));
        assertNull(edited.getInitProfile("tails"));
        assertSame(stock.getRules(), edited.getRules());
        assertSame(stock.getModifiers(), edited.getModifiers());
    }
}
