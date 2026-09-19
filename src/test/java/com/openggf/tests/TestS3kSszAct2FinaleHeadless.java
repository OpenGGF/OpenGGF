package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszKnuxFinalBossCraneObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cold production ownership for SSZ2's placed crane and its boss allocation boundary. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszAct2FinaleHeadless {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void placedCraneReachesTheActTwoBossThroughItsNativeWait() {
        HeadlessTestFixture fixture = boot();
        SszKnuxFinalBossCraneObjectInstance crane = active(SszKnuxFinalBossCraneObjectInstance.class);
        assertNotNull(crane, "$B2 at ($180,$430) resolves through the production placement");

        // INIT owns move.w Camera_min_X,Camera_max_X.
        fixture.stepIdleFrames(1);
        assertEquals(GameServices.camera().getMinX() & 0xFFFF,
                GameServices.camera().getMaxX() & 0xFFFF);

        for (int frame = 0; frame < 0x240 && active(SszMechaSonicObjectInstance.class) == null; frame++) {
            var player = fixture.sprite();
            player.setDead(false);
            player.setHurt(false);
            player.setCentreX((short) 0x120);
            player.setCentreYPreserveSubpixel((short) 0x430);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setAir(true);
            fixture.stepIdleFrames(1);
        }

        SszMechaSonicObjectInstance boss = active(SszMechaSonicObjectInstance.class);
        assertNotNull(boss, "loc_7CB64's signed wait allocated Obj_SSZEndBoss");
        for (int frame = 0; frame < 4 && !boss.initExecutedForTest(); frame++) fixture.stepIdleFrames(1);
        assertTrue(boss.actTwoForTest(), "Current_act selects the act-2 init branch");
        assertTrue(S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().cutsceneFlag(4), "crane helper raised _unkFAB8 bit 4");
        assertEquals(GameServices.camera().getMinX() & 0xFFFF,
                GameServices.camera().getMaxX() & 0xFFFF,
                "the final allocation boundary preserves the crane-owned camera lock");
    }

    @Test
    void defeatHandshakeAdvancesThroughTheArenaFloorStage() {
        HeadlessTestFixture fixture = boot();
        var state = S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        state.setForegroundRoutine(4);
        state.setEventsFg4Low(0xFF);

        fixture.stepIdleFrames(1);

        assertEquals(8, state.foregroundRoutine(), "loc_58AE0 addq.w #4,Events_routine_fg");
        assertEquals(0, state.eventsFg4(), "loc_58AE0 clr.w Events_fg_4");
    }

    private static HeadlessTestFixture boot() {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 1)
                .withFreshLevelStartLifecycle()
                .startPosition((short) 0x120, (short) 0x430)
                .startPositionIsCentre()
                .build();
    }

    private static <T> T active(Class<T> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) return null;
        for (var object : manager.getActiveObjects()) {
            if (type.isInstance(object) && !object.isDestroyed()) return type.cast(object);
        }
        return null;
    }
}
