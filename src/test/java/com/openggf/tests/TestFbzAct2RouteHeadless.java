package com.openggf.tests;

import com.openggf.game.sonic3k.objects.TestFbzAct2TraversalPreboss;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

/**
 * Native fixed-input FBZ2 route acceptance.
 *
 * <p>The main wave starts at the act spawn and advances only through the
 * production frame loop. It traverses every placed mechanic on the route,
 * completes all seven subboss beam cycles, rides the end-boss event plane,
 * lands eight ordinary player attacks, opens the real capsule, and stops only
 * when the boss-owned exit requests Sandopolis Act 0.</p>
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzAct2RouteHeadless {
    @Test
    void nativeStartWaveCompletesFbz2AndRequestsSandopolisAct0() {
        TestFbzAct2TraversalPreboss
                .assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones();
    }

    @Test
    void starpost5WaveExecutesTheLowerMagneticPlatformAndChain() {
        TestFbzAct2TraversalPreboss
                .assertLateNativeStarpostRestartMaterializesAndExecutesLowerMagneticSection();
    }
}
