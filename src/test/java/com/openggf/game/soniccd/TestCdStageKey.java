package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestCdStageKey {
    @Test
    void allSeventyNativeIdsRoundTripThroughTypedKeys() {
        Set<String> ids = new HashSet<>();
        for (int round : new int[]{1, 3, 4, 5, 6, 7, 8}) {
            for (int act = 1; act <= 3; act++) {
                for (char period : (act == 3 ? "CD" : "ABCD").toCharArray()) {
                    String id = "R" + round + act + period;
                    CdStageKey key = CdStageKey.parse(id);
                    assertEquals(id, key.nativeId());
                    assertEquals(round, key.zone().nativeRound());
                    assertEquals(act, key.actNumber());
                    if (act == 3) {
                        assertInstanceOf(CdStageKey.Boss.class, key);
                    } else {
                        assertInstanceOf(CdStageKey.Traversal.class, key);
                    }
                    assertTrue(ids.add(id));
                }
            }
        }
        assertEquals(70, ids.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "R21A", "R01A", "R91A", "R10A", "R14A", "R13A", "R13B",
            "R33A", "R83B", "R11E", "r11a", " R11A", "R11A\n", "R11A__", "R11ADEMO",
            "R43CDEMO", "SPECSTAGE", "R111A"})
    void rejectsNonexistentOrNonstageIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> CdStageKey.parse(id));
    }

    @Test
    void constructorsEnforceTheirRequiredDomainValues() {
        assertEquals("R12B", new CdStageKey.Traversal(CdZone.PALMTREE_PANIC,
                CdStageKey.TraversalAct.ACT_2, CdPeriod.PAST).nativeId());
        assertEquals("R83D", new CdStageKey.Boss(CdZone.METALLIC_MADNESS,
                CdStageKey.FutureOutcome.BAD).nativeId());
        assertThrows(NullPointerException.class, () -> CdStageKey.parse(null));
        assertThrows(NullPointerException.class, () -> new CdStageKey.Traversal(null,
                CdStageKey.TraversalAct.ACT_1, CdPeriod.PRESENT));
        assertThrows(NullPointerException.class, () -> new CdStageKey.Traversal(CdZone.PALMTREE_PANIC,
                null, CdPeriod.PRESENT));
        assertThrows(NullPointerException.class, () -> new CdStageKey.Traversal(CdZone.PALMTREE_PANIC,
                CdStageKey.TraversalAct.ACT_1, null));
        assertThrows(NullPointerException.class, () -> new CdStageKey.Boss(null, CdStageKey.FutureOutcome.GOOD));
        assertThrows(NullPointerException.class, () -> new CdStageKey.Boss(CdZone.PALMTREE_PANIC, null));
    }

    @Test
    void sourceRoundAndPeriodParsersRejectUnknownValues() {
        assertEquals(CdZone.COLLISION_CHAOS, CdZone.fromNativeRound(3));
        for (int round : new int[]{-1, 0, 2, 9}) {
            assertThrows(IllegalArgumentException.class, () -> CdZone.fromNativeRound(round));
        }
        assertEquals(CdPeriod.GOOD_FUTURE, CdPeriod.fromPayloadSuffix('C'));
        assertThrows(IllegalArgumentException.class, () -> CdPeriod.fromPayloadSuffix('c'));
        assertThrows(IllegalArgumentException.class, () -> CdPeriod.fromPayloadSuffix('E'));
    }
}
