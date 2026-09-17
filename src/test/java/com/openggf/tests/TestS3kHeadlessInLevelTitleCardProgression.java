package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHeadlessInLevelTitleCardProgression {

    @Test
    void headlessRunnerAdvancesInLevelTitleCardOverlay() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();

        TitleCardProvider provider = GameServices.module().getTitleCardProvider();
        provider.reset();
        provider.initializeInLevel(0, 0);

        assertTrue(provider.isOverlayActive(), "In-level title card should start active.");

        fixture.stepIdleFrames(200);

        assertFalse(provider.isOverlayActive(),
                "Headless stepping should advance the in-level S3K title card until it completes.");
    }

    @Test
    void nativeWaitGateResetClearsRingsOnlyAfterChildrenStopMoving() {
        // SOZ2's in-level card resets on Obj_TitleCardWait's pass after the children stop
        // publishing movement; recorded s3k-tails-full-chain-all-emeralds SOZ keeps 88 rings
        // until row 18198 (engine countdown cleared them at 18192).
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
        var player = GameServices.camera().getFocusedSprite();
        var manager = (com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager)
                GameServices.module().getTitleCardProvider();
        manager.reset();
        manager.initializeInLevel(8, 1);
        manager.requestLevelGamestateResetAtInLevelDisplay(TitleCardProvider.RESET_AT_NATIVE_WAIT_GATE, 0);
        player.setRingCount(88);

        boolean reset = false;
        for (int frame = 0; frame < 200 && !reset; frame++) {
            boolean gateOpenBeforeStep = manager.isExternalInLevelWaitReady();
            fixture.stepIdleFrames(1);
            reset = player.getRingCount() == 0;
            assertEquals(gateOpenBeforeStep, reset,
                    "rings clear exactly on the first title update that observes the open wait gate");
        }
        assertTrue(reset, "the native wait gate must eventually reset the level gamestate");

        // Obj_TitleCardWait2's 90-pass $2E hold counts from the gate, not from the first
        // stationary child pass (soz_completerun LoadEnemyArt row 29428).
        int displayPassesAfterGate = 0;
        while (manager.isExternalInLevelWaitReady() && displayPassesAfterGate < 200) {
            fixture.stepIdleFrames(1);
            displayPassesAfterGate++;
        }
        assertEquals(89, displayPassesAfterGate);
    }

    @Test
    void inLevelGamestateResetReplenishesNativePlayerAir() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();

        var player = GameServices.camera().getFocusedSprite();
        var sidekick = GameServices.sprites().getRegisteredSidekicks().stream()
                .findFirst()
                .orElseThrow();
        player.getDrowningController().setRemainingAirFromFixedCountdown(3);
        sidekick.getDrowningController().setRemainingAirFromFixedCountdown(4);

        TitleCardProvider provider = GameServices.module().getTitleCardProvider();
        provider.reset();
        provider.initializeInLevel(0, 0);
        provider.requestLevelGamestateResetAtInLevelDisplay();
        fixture.stepIdleFrames(40);

        assertEquals(30, player.getDrowningController().getRemainingAir());
        assertEquals(30, sidekick.getDrowningController().getRemainingAir());
    }
}
