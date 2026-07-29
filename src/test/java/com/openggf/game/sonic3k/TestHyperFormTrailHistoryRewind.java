package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestHyperFormTrailHistoryRewind {
    private Sonic sonic;

    @BeforeEach
    void setUp() {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.level().resetLevelGamestate(
                GameModuleRegistry.getCurrent().createLevelState());
        sonic = new Sonic("sonic", (short) 0, (short) 0);
    }

    @Test
    void recordsArtPrioritySeparatelyFromGameplayStatusAndRewindsIt() {
        sonic.setPreventTailsRespawn(false);
        sonic.setHighPriority(true);
        sonic.recordFollowerHistoryForTick();

        assertEquals((byte) 0, sonic.getStatusHistory(0));
        assertEquals((byte) 0x80, sonic.getArtTileAttributeHistory(0));
        PerObjectRewindSnapshot snapshot = sonic.captureRewindState();

        sonic.clearFollowerHistoryRecordedFlag();
        sonic.setHighPriority(false);
        sonic.recordFollowerHistoryForTick();
        assertEquals((byte) 0, sonic.getArtTileAttributeHistory(0));

        sonic.restoreRewindState(snapshot);
        assertEquals((byte) 0x80, sonic.getArtTileAttributeHistory(0));
        assertEquals((byte) 0, sonic.getStatusHistory(0),
                "rewind must keep gameplay status and art priority independent");
    }
}
