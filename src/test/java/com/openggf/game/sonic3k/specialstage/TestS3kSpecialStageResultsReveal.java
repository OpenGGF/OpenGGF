package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the post-tally reveal branch of {@link S3kSpecialStageResultsScreen}.
 *
 * <p>ROM reference: {@code loc_2E512} diverts a completed Super Emerald stage to the HPZ
 * sanctuary reveal before the Chaos Emerald check at {@code loc_2E540} runs; that check then
 * picks ObjDat2_2E918 ("NOW SONIC CAN / BE SUPER SONIC") on the Sonic 3 side and
 * ObjDat2_2E960 ("SONIC CAN GO TO / HIDDEN PALACE") once the Big Ring's zone is on the S&amp;K
 * side. {@code sub_2ECA8} points every completeness test at Super_emerald_count on a Super
 * Emerald stage, and {@code loc_2EB88} swaps in the "SUPER EMERALD" word.
 */
@ExtendWith(SingletonResetExtension.class)
class TestS3kSpecialStageResultsReveal {

    /** Map_Results frame $24: "CHAOS EMERALD". */
    private static final int FRAME_CHAOS_EMERALD = 0x24;
    /** Map_Results frame $30: "SUPER EMERALD". */
    private static final int FRAME_SUPER_EMERALD = 0x30;
    /** ObjDat2_2E918 frames: "NOW" name "CAN" / "BE" SUPER name. */
    private static final List<Integer> SUPER_FORM_FRAMES =
            List.of(0x27, 0x13, 0x3A, 0x28, 0x12, 0x13);
    /** ObjDat2_2E960 frames: name "CAN GO TO" / "HIDDEN PALACE". */
    private static final List<Integer> HIDDEN_PALACE_FRAMES = List.of(0x13, 0x2E, 0x2F);

    /**
     * Frames needed to reach the reveal: the 360-frame pre-tally wait, 500 frames draining
     * the 5000 time bonus, the 120-frame post-tally wait, the 270-frame continue-icon wait,
     * and the staggered slide-out of the bonus text.
     */
    private static final int FRAMES_TO_REVEAL = 360 + 500 + 120 + 270 + 60;

    private GameStateManager gameState;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        gameState = GameServices.gameState();
        gameState.resetSession();
        gameState.configureSpecialStageProgress(7, 7);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    private S3kSpecialStageResultsScreen screen(boolean superEmeraldStage, boolean skSideOrigin,
                                                PlayerCharacter character) {
        return new S3kSpecialStageResultsScreen(50, true, 0, gameState.getEmeraldCount(),
                character, superEmeraldStage, skSideOrigin);
    }

    private static S3kSpecialStageResultsScreen advanceToReveal(S3kSpecialStageResultsScreen screen) {
        for (int frame = 0; frame < FRAMES_TO_REVEAL; frame++) {
            screen.update(frame, null);
        }
        return screen;
    }

    private void collectAllChaosEmeralds() {
        for (int i = 0; i < 7; i++) {
            gameState.markEmeraldCollected(i);
        }
    }

    private void collectAllChaosEmeraldsAndSuperEmeraldsExcept(int uncollectedIndex) {
        collectAllChaosEmeralds();
        gameState.setEmeraldsConverted(true);
        for (int i = 0; i < 7; i++) {
            if (i != uncollectedIndex) {
                gameState.markSuperEmeraldCollected(i);
            }
        }
    }

    // ---- loc_2E512: a completed Super Emerald stage never shows the Chaos reveal ----

    @Test
    void superEmeraldStageWithAllChaosEmeraldsShowsNoReveal() {
        collectAllChaosEmeralds();

        var screen = advanceToReveal(screen(true, true, PlayerCharacter.SONIC_ALONE));

        assertEquals(List.of(), screen.revealFramesForTest(),
                "A Super Emerald stage is handed to the sanctuary reveal before loc_2E540");
        assertTrue(screen.isComplete(), "The screen should finish instead of revealing");
    }

    // ---- sub_2ECA8/loc_2EB88: the Super Emerald stage counts Super Emeralds ----

    @Test
    void superEmeraldStageNamesTheSuperEmeraldAndIgnoresTheChaosCount() {
        collectAllChaosEmeralds();

        var screen = screen(true, true, PlayerCharacter.SONIC_ALONE);

        assertEquals(FRAME_SUPER_EMERALD, screen.emeraldWordFrameForTest());
        assertFalse(screen.superTextVisibleForTest(),
                "A full Chaos Emerald set must not pluralise the Super Emerald line");
    }

    @Test
    void superEmeraldStageCompletingTheSuperSetPluralisesTheEmeraldWord() {
        collectAllChaosEmeraldsAndSuperEmeraldsExcept(6);
        gameState.markSuperEmeraldCollected(6);

        var screen = screen(true, true, PlayerCharacter.KNUCKLES);

        assertEquals(FRAME_SUPER_EMERALD, screen.emeraldWordFrameForTest());
        assertTrue(screen.superTextVisibleForTest());
    }

    // ---- loc_2E540: which Chaos Emerald reveal the zone selects ----

    @Test
    void chaosStageOnTheSonic3SidePromisesTheSuperForm() {
        collectAllChaosEmeralds();

        var screen = advanceToReveal(screen(false, false, PlayerCharacter.SONIC_AND_TAILS));

        assertEquals(FRAME_CHAOS_EMERALD, screen.emeraldWordFrameForTest());
        assertEquals(SUPER_FORM_FRAMES, screen.revealFramesForTest());
    }

    @Test
    void chaosStageOnTheSkSidePointsAtHiddenPalaceInstead() {
        collectAllChaosEmeralds();

        var screen = advanceToReveal(screen(false, true, PlayerCharacter.SONIC_AND_TAILS));

        assertEquals(HIDDEN_PALACE_FRAMES, screen.revealFramesForTest());
    }

    @Test
    void tailsAloneGetsNoRevealOnTheSonic3SideButDoesOnTheSkSide() {
        collectAllChaosEmeralds();

        assertEquals(List.of(),
                advanceToReveal(screen(false, false, PlayerCharacter.TAILS_ALONE))
                        .revealFramesForTest());
        // Frame $13 + 2 is the "TAILS" name variant (ROM sub_2EC80).
        assertEquals(List.of(0x15, 0x2E, 0x2F),
                advanceToReveal(screen(false, true, PlayerCharacter.TAILS_ALONE))
                        .revealFramesForTest());
    }

    @Test
    void anIncompleteChaosSetShowsNoReveal() {
        for (int i = 0; i < 6; i++) {
            gameState.markEmeraldCollected(i);
        }

        var screen = advanceToReveal(screen(false, false, PlayerCharacter.SONIC_ALONE));

        assertEquals(List.of(), screen.revealFramesForTest());
    }
}
