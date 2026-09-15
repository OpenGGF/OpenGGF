package com.openggf.game.sonic3k;

import com.openggf.game.internal.SidekickCpuInitializationPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic3kSidekickCpuInitializationPolicy {
    @Test void routineZeroDirectBranchesAndCheckpointOverrideMatchNative() {
        var policy = new Sonic3kGameModule().getGameService(SidekickCpuInitializationPolicy.class);
        assertNotNull(policy);
        assertTrue(policy.preservesSpawnState(8, 0, -1));
        assertTrue(policy.preservesSpawnState(8, 0, 0));
        assertTrue(policy.preservesSpawnState(0x17, 0, 0));
        assertTrue(policy.preservesSpawnState(0x17, 1, 0));
        assertFalse(policy.preservesSpawnState(8, 1, 0));
        assertFalse(policy.preservesSpawnState(8, 0, 1));
        assertFalse(policy.preservesSpawnState(0x17, 0, 1));
        for (int zone : new int[]{0, 1, 2, 3, 5, 6, 7, 9, 10, 0x16}) {
            assertFalse(policy.preservesSpawnState(zone, 0, 0));
        }
    }
}
