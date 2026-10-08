package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the S3K {@code Reverse_gravity_flag} ($FFFFF7C6) survives and when it does not.
 *
 * <p>Nothing in the ROM clears the byte by name except {@code Obj_DEZGravitySwap}
 * ({@code sub_49228}, sonic3k.asm:95511 and :95536) and the persistent clearer object
 * the act 2 boss spawns at defeat ({@code loc_7FC3E}, :170746). A <em>level load</em>
 * clears it only as a side effect of the RAM wipe: {@code clearRAM Tails_CPU_interact,$100}
 * (:7621) covers $F700-$F7FF, and that wipe runs for a normal load, a death restart and
 * {@code StartNewLevel} (and again in {@code Title_Screen}, :5415).
 *
 * <p>The seamless act change does <em>not</em> run it. {@code loc_593EC} (:118724) calls
 * {@code Load_Level} and {@code LoadSolids} only, so gravity carries from Death Egg act 1
 * into act 2 unchanged. The engine reaches both paths through
 * {@code GameStateManager.resetForLevel()}, so the act-transition owner has to put the
 * flag back.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityFlagLifecycle {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /** The level-load RAM wipe: {@code clearRAM Tails_CPU_interact,$100} covers $F7C6. */
    @Test
    void aLevelLoadClearsTheFlag() {
        GameStateManager gameState = new GameStateManager();
        gameState.setReverseGravityActive(true);

        gameState.resetForLevel();

        assertFalse(gameState.isReverseGravityActive(),
                "clearRAM Tails_CPU_interact,$100 (sonic3k.asm:7621) zeroes $F7C6 on every load");
    }

    /** A fresh session starts upright too — the same wipe runs from Title_Screen. */
    @Test
    void aSessionResetClearsTheFlag() {
        GameStateManager gameState = new GameStateManager();
        gameState.setReverseGravityActive(true);

        gameState.resetSession();

        assertFalse(gameState.isReverseGravityActive());
    }

    /**
     * {@code loc_593EC} runs no RAM wipe, so an inverted Death Egg act 1 hands an
     * inverted act 2 to the player.
     */
    @Test
    void theSeamlessActChangeKeepsTheFlag() throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
        GameServices.gameState().setReverseGravityActive(true);

        GameServices.level().executeActTransition(
                SeamlessLevelTransitionRequest.builder(
                                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                        .targetZoneAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                        .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.TITLE_OWNER)
                        .objectSurvivalPolicy(
                                SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.PERSISTENT_EXACT_SST)
                        .preserveLevelGamestate(true)
                        .build());

        assertTrue(GameServices.gameState().isReverseGravityActive(),
                "loc_593EC calls Load_Level/LoadSolids only: no clearRAM, so $F7C6 survives");
    }
}
