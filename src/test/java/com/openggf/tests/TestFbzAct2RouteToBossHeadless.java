package com.openggf.tests;

import com.openggf.game.sonic3k.objects.TestFbzAct2TraversalPreboss;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

/**
 * Behavioral Task-14 route-wave owner.
 *
 * <p>These deterministic, production-loop waves deliberately make no claim
 * that P1 has crossed the final vertical section or entered the arena. Complete
 * start-to-arena controller validation belongs to the Task-20 BK2 trace polish
 * gate; the focused subboss suite independently owns placed-$AB behavior.</p>
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzAct2RouteToBossHeadless {
    @Test
    void nativeStartWaveExecutesEveryMechanicThroughThe11900FrameFrontier() {
        TestFbzAct2TraversalPreboss
                .assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones();
    }

    @Test
    void starpost5WaveExecutesTheLowerMagneticPlatformAndChain() {
        TestFbzAct2TraversalPreboss
                .assertLateNativeStarpostRestartMaterializesAndExecutesLowerMagneticSection();
    }
}
