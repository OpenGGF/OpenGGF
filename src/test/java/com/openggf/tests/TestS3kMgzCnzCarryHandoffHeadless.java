package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMgzCnzCarryHandoffHeadless {
    @ParameterizedTest
    @ValueSource(strings = {"tails", ""})
    void rescuedTailsCarriesSonicIntoCnzUntilLanding(String followers) throws Exception {
        var config = SonicConfigurationService.getInstance();
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, followers);
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(2, 1).build();
            var level = GameServices.level();
            var sonic = fixture.sprite();
            fixture.camera().setX((short) 0x3C80);
            fixture.camera().setY((short) 0x600);
            fixture.camera().setFrozen(true);
            sonic.setCentreX((short) 0x3CC0);
            sonic.setCentreY((short) 0x710);
            sonic.setAir(true);
            for (var sidekick : GameServices.sprites().getSidekicks()) {
                sidekick.setCentreX((short) 0x3CC0);
                sidekick.setCentreY((short) 0x800);
                sidekick.setRenderFlagOnScreen(false);
            }
            ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                    .getMgzEvents().triggerBossCollapseHandoff();
            boolean rescued = false;
            for (int frame = 0; frame < 500; frame++) {
                fixture.stepIdleFrames(1);
                if (GameServices.sprites().getSidekicks().stream()
                        .anyMatch(sidekick -> sidekick.getCpuController().isFlyingCarrying())) {
                    rescued = true;
                    break;
                }
            }
            assertTrue(rescued, "MGZ2 must engage its real rescue carry before the zone handoff");
            level.requestZoneAndAct(3, 0, true);
            boolean arrivedCarried = false;
            for (int frame = 0; frame < 400; frame++) {
                fixture.stepIdleFrames(1);
                if (level.getCurrentZone() == 3
                        && !level.hasPendingFreshLevelTransitionBoundary()
                        && sonic.isObjectControlled()) {
                    arrivedCarried = true;
                    break;
                }
            }
            assertTrue(arrivedCarried, "CNZ1 must attach Sonic to Tails after the MGZ2 rescue");
            assertEquals(0x100, sonic.getXSpeed(), "CNZ1 carry must use its own flight velocity");
            var tails = GameServices.sprites().getSidekicks().getFirst();
            assertEquals(SidekickCpuController.State.CARRYING, tails.getCpuController().getState());
            var registry = fixture.gameplayMode().getRewindRegistry();
            var carried = registry.capture();
            fixture.stepIdleFrames(8);
            var forward = registry.capture();
            registry.restore(carried);
            fixture.stepIdleFrames(8);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(forward, registry.capture(),
                    "CNZ carry after MGZ handoff replays", 8);
            boolean landed = false;
            for (int frame = 0; frame < 240; frame++) {
                fixture.stepIdleFrames(1);
                if (!sonic.getAir()) {
                    landed = true;
                    break;
                }
                assertTrue(sonic.isObjectControlled(), "Sonic must stay carried until landing");
            }
            assertTrue(landed, "Tails must bring Sonic down onto the CNZ1 terrain");
            fixture.stepIdleFrames(2);
            assertFalse(sonic.isObjectControlled(), "Landing must release Sonic for ordinary play");
        } finally {
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, oldSidekick);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldMain);
        }
    }
}
