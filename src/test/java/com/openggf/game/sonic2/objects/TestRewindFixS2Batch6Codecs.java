package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.bosses.CPZBossContainerExtend;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every batch-6 S2 object that was previously dropped on a
 * held-rewind restore keeps a generic recreate path.
 *
 * <p>Batch-6 adds parent/sibling relink for the HTZ seesaw ball
 * (relinked to its placed parent seesaw) and the CPZ boss container extend
 * (relinked to its live boss + container parents). The CNZ slot-machine ring
 * prize and MTZ steam puff now restore through generic recreate.
 *
 * <p>The two cosmetic transient children evaluated in this batch —
 * {@code SuperSonicStarsObjectInstance} (Super Sonic sparkle, re-emitted each frame
 * from the live player) and {@code com.openggf.level.objects.SplashObjectInstance}
 * (water splash, re-emitted on water entry/exit) — are intentionally accept-drop and
 * are documented in {@code docs/KNOWN_DISCREPANCIES.md}; they are deliberately
 * not required to have a recreate path here.
 */
class TestRewindFixS2Batch6Codecs {

    @Test
    void batch6S2ObjectsKeepGenericRecreateCoverage() {
        List<Class<?>> covered = List.of(
                CPZBossContainerExtend.class,
                SeesawBallObjectInstance.class,
                SteamPuffObjectInstance.class,
                RingPrizeObjectInstance.class);

        for (Class<?> type : covered) {
            assertTrue(RewindRecreatable.class.isAssignableFrom(type),
                    type.getName() + " must restore through RewindRecreatable generic recreate");
        }
    }
}
