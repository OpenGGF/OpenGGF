package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.TestFbzAct2TraversalPreboss;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

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
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        String previousMain = configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        String previousSidekicks = configuration.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        try {
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();

            TestFbzAct2TraversalPreboss
                    .assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(fixture -> {
                        assertInstanceOf(Sonic.class, fixture.sprite());
                        assertSame(fixture.sprite(), GameServices.sprites().getMainPlayable(),
                                "the strict route's fixed input must retain P1 authority on Sonic");
                        assertEquals(1, GameServices.sprites().getSidekicks().size());
                        Tails p2 = assertInstanceOf(
                                Tails.class, GameServices.sprites().getSidekicks().getFirst());
                        assertEquals("tails_p2", p2.getCode());
                        FbzZoneRuntimeState runtime = assertInstanceOf(
                                FbzZoneRuntimeState.class,
                                GameServices.zoneRuntimeRegistry().current());
                        assertEquals(PlayerCharacter.SONIC_AND_TAILS, runtime.playerCharacter());
                    });
        } finally {
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    previousMain == null ? "sonic" : previousMain);
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    previousSidekicks == null ? "tails" : previousSidekicks);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }

    @Test
    void starpost5WaveExecutesTheLowerMagneticPlatformAndChain() {
        TestFbzAct2TraversalPreboss
                .assertLateNativeStarpostRestartMaterializesAndExecutesLowerMagneticSection();
    }
}
