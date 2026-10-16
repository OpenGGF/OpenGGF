package com.openggf.tools;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trace capture/benchmark rows run the production bonus-frame tail. The trace bootstrap
 * enters a bonus stage without the render-only player-priority write that
 * GameLoop.prepareBonusStageForTitleCard makes; GameLoop then restores it after every
 * physics frame (Obj_GumballMachine: bset #7,(Player_1+art_tile).w, sonic3k.asm:127416). Without the
 * shared hook a Gumball capture draws Knuckles behind the machine glass.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestTraceReplayDriveBonusLifecycle {

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void gameplayRowRestoresTheGumballPlayerPriority() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_GUMBALL, 0)
                .build();
        BonusStageProvider provider = GameServices.module().getBonusStageProvider();
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        gameplayMode.setActiveBonusStageProvider(provider);
        provider.onEnter(BonusStageType.GUMBALL, new BonusStageState(
                0, 0, 25, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0x0C, (byte) 0x0D, 0, 0L));
        gameplayMode.registerBonusStageAdapter(provider);
        provider.onDeferredSetupComplete();
        fixture.stepIdleFrames(1);
        AbstractPlayableSprite player = fixture.sprite();
        player.setHighPriority(false);
        assertFalse(player.isHighPriority());

        TraceReplayDrive.afterGameplayStep(gameplayMode);

        assertTrue(player.isHighPriority(), "the bonus player regains VDP high priority after physics");
    }
}
