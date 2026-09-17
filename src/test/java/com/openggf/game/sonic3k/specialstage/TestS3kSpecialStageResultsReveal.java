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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the post-tally reveal branch of {@link S3kSpecialStageResultsScreen}.
 *
 * <p>ROM reference: {@code loc_2E512} diverts a completed Super Emerald stage to the HPZ
 * sanctuary reveal before the Chaos Emerald check at {@code loc_2E540} runs; that check then
 * picks ObjDat2_2E918 ("NOW SONIC CAN / BE SUPER SONIC") on the Sonic 3 side and
 * ObjDat2_2E960 ("SONIC CAN GO TO / HIDDEN PALACE") once the Big Ring's zone is on the S&amp;K
 * side. {@code sub_2ECA8} points every completeness test at Super_emerald_count on a Super
 * Emerald stage, and {@code loc_2EB88} swaps in the "SUPER EMERALD" word. Routines $E-$12
 * ({@code loc_2E616}-{@code loc_2E7DA}) pan the sanctuary, close the star ring and, with seven
 * Super Emeralds, show ObjDat2_2E984. These tests run without a loaded level, so the star
 * groups are updated by the screen itself with their ROM lifetimes.
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
    /** ObjDat2_2E984 frames: "NOW" name "CAN" / "BE" HYPER name, on palette line 1. */
    private static final List<Integer> HYPER_MESSAGE_FRAMES =
            List.of(0x2C, 0x13, 0x2D, 0x35, 0x12, 0x13);

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
    void superEmeraldStageWithAllChaosEmeraldsShowsNoChaosReveal() {
        collectAllChaosEmeralds();

        var screen = advanceToReveal(screen(true, true, PlayerCharacter.SONIC_ALONE));

        assertEquals(List.of(), screen.revealFramesForTest(),
                "A Super Emerald stage is handed to the sanctuary reveal before loc_2E540");
        assertEquals(0x10, screen.sanctuaryRoutineForTest(),
                "loc_2E512 switched the cleared Super Emerald stage into routines $E/$10");
    }

    @Test
    void failedSuperEmeraldStageSkipsTheSanctuaryReveal() {
        collectAllChaosEmeralds();
        var screen = new S3kSpecialStageResultsScreen(30, false, 0, gameState.getEmeraldCount(),
                PlayerCharacter.SONIC_ALONE, true, true);

        for (int frame = 1; frame <= FRAMES_TO_REVEAL && !screen.isComplete(); frame++) {
            screen.update(frame, null);
            assertEquals(-1, screen.sanctuaryRoutineForTest());
        }
        assertTrue(screen.isComplete(),
                "Special_stage_spheres_left is non-zero, so loc_2E534/loc_2E540 exit normally");
        assertEquals(0x240, screen.sanctuaryCameraYForTest());
    }

    // ---- loc_2EAA6: only state-1 emeralds keep their indicator ----

    @Test
    void emeraldIndicatorsFollowTheStateOneGateNotCollection() {
        gameState.restoreS3kEmeraldProgress(List.of(1, 2, 3, 0, 1, 3, 2), true);

        var screen = screen(true, true, PlayerCharacter.SONIC_ALONE);

        assertEquals(List.of(true, false, false, false, true, false, false),
                java.util.stream.IntStream.range(0, 7)
                        .mapToObj(screen::emeraldIndicatorVisibleForTest).toList(),
                "cmpi.b #1,(Collected_emeralds_array,d0.w) / bne loc_2EC7A");
    }

    // ---- Routines $E, $10, $A: the sanctuary pan and converging stars ----

    /**
     * Update numbers are 1-based loop iterations. With 30 rings the tally ends on update
     * 862 (routine 0 on 1, the $2E wait on 2-361, 500 time-bonus steps on 362-861), and
     * loc_2E4D6 falls straight through loc_2E512 into routine $E on that update.
     */
    private static final int TALLY_END_UNDER_50_RINGS = 862;

    private static void runTo(S3kSpecialStageResultsScreen screen, int fromUpdate, int toUpdate) {
        for (int frame = fromUpdate; frame <= toUpdate; frame++) {
            screen.update(frame, null);
        }
    }

    @Test
    void sanctuaryPanWaitsSixtyFramesThenMovesTheCameraOnePixelPerFrameToThePedestals() {
        collectAllChaosEmeraldsAndSuperEmeraldsExcept(6);
        var screen = new S3kSpecialStageResultsScreen(30, true, 0, gameState.getEmeraldCount(),
                PlayerCharacter.SONIC_ALONE, true, true);
        int e = TALLY_END_UNDER_50_RINGS;

        runTo(screen, 1, e - 1);
        assertEquals(-1, screen.sanctuaryRoutineForTest());
        runTo(screen, e, e);
        assertEquals(0xE, screen.sanctuaryRoutineForTest());
        runTo(screen, e + 1, e + 59);
        assertEquals(0x240, screen.sanctuaryCameraYForTest(), "loc_2E616 counts $2E down first");
        runTo(screen, e + 60, e + 60);
        assertEquals(0x241, screen.sanctuaryCameraYForTest());

        runTo(screen, e + 61, e + 154);
        assertFalse(screen.phase1SlidingOutForTest(0));
        runTo(screen, e + 155, e + 155);
        assertEquals(0x2A0, screen.sanctuaryCameraYForTest());
        assertTrue(screen.phase1SlidingOutForTest(0), "loc_2E622 retires the tally rows at $2A0");
        assertTrue(screen.phase1SlidingOutForTest(14), "and the GOT A SUPER EMERALD rows");
        assertFalse(screen.phase1SlidingOutForTest(17),
                "the plural rows only exist with seven Super Emeralds");
        int scoreRowX = screen.phase1XForTest(0);
        runTo(screen, e + 156, e + 162);
        assertEquals(scoreRowX, screen.phase1XForTest(0), "slot 30 holds for $2E = 8 frames");
        runTo(screen, e + 163, e + 163);
        assertEquals(scoreRowX + 0x20, screen.phase1XForTest(0));

        runTo(screen, e + 164, e + 283);
        assertEquals(0x320, screen.sanctuaryCameraYForTest());
        assertNull(screen.convergingStarsForTest());
        runTo(screen, e + 284, e + 284);
        assertEquals(0x10, screen.sanctuaryRoutineForTest(), "loc_2E6EA once Y is $320");
        assertEquals(0x320, screen.sanctuaryCameraYForTest());
        assertNotNull(screen.convergingStarsForTest());
        assertEquals(0x15A0, screen.sanctuaryCameraXForTest());
    }

    @Test
    void sixSuperEmeraldsExitSixtyFramesAfterTheStarsCloseWithoutAHyperMessage() {
        collectAllChaosEmeraldsAndSuperEmeraldsExcept(6);
        var screen = new S3kSpecialStageResultsScreen(30, true, 2, gameState.getEmeraldCount(),
                PlayerCharacter.SONIC_ALONE, true, true);
        int e = TALLY_END_UNDER_50_RINGS;

        assertEquals(0x1600, screen.sanctuaryCameraXForTest(), "word_2E398[2]");
        runTo(screen, 1, e + 508);
        assertFalse(screen.convergingStarsForTest().hasCollapsed());
        runTo(screen, e + 509, e + 509);
        assertTrue(screen.convergingStarsForTest().hasCollapsed(),
                "224 drawn frames from the first star pass on e+285");
        assertEquals(0xA, screen.sanctuaryRoutineForTest(), "loc_2E75C: fewer than seven");
        runTo(screen, e + 510, e + 569);
        assertFalse(screen.isComplete());
        runTo(screen, e + 570, e + 570);
        assertTrue(screen.isComplete());
        assertEquals(List.of(), screen.revealFramesForTest());
        assertNull(screen.expandingStarsForTest());
    }

    @Test
    void sevenSuperEmeraldsPanToTheMasterEmeraldAndAnnounceTheHyperForm() {
        collectAllChaosEmeraldsAndSuperEmeraldsExcept(6);
        gameState.markSuperEmeraldCollected(6);
        // Stage 3 starts the camera at $1500, $A0 left of the Hyper pan target.
        var screen = new S3kSpecialStageResultsScreen(30, true, 3, gameState.getEmeraldCount(),
                PlayerCharacter.SONIC_ALONE, true, true);
        int e = TALLY_END_UNDER_50_RINGS;

        runTo(screen, 1, e + 155);
        assertTrue(screen.phase1SlidingOutForTest(17), "seven Super Emeralds retire the plural rows");
        runTo(screen, e + 156, e + 538);
        assertEquals(0x10, screen.sanctuaryRoutineForTest());
        assertEquals(0x1500, screen.sanctuaryCameraXForTest(), "loc_2E772 holds for $2E = 30");
        runTo(screen, e + 539, e + 539);
        assertEquals(0x1501, screen.sanctuaryCameraXForTest());
        runTo(screen, e + 540, e + 698);
        assertEquals(0x15A0, screen.sanctuaryCameraXForTest());
        assertNull(screen.expandingStarsForTest());
        int s = e + 699;
        runTo(screen, s, s);
        assertNotNull(screen.expandingStarsForTest(), "loc_2E792 once the camera is at $15A0");
        assertEquals(0x12, screen.sanctuaryRoutineForTest());

        runTo(screen, s + 1, s + 120);
        assertEquals(List.of(), screen.revealFramesForTest());
        runTo(screen, s + 121, s + 121);
        assertEquals(HYPER_MESSAGE_FRAMES, screen.revealFramesForTest(),
                "ObjDat2_2E984: NOW SONIC CAN / BE HYPER SONIC");
        runTo(screen, s + 122, s + 175);
        assertFalse(screen.hyperMessageArrivedForTest());
        runTo(screen, s + 176, s + 176);
        assertTrue(screen.hyperMessageArrivedForTest(),
                "loc_2E9D8 fires once slot 66 has made its 56 $10-pixel steps");
        runTo(screen, s + 177, s + 481);
        assertFalse(screen.isComplete());
        runTo(screen, s + 482, s + 482);
        assertTrue(screen.isComplete(), "routine $A counts the 6*60 message hold");
    }

    @Test
    void knucklesHyperMessageUsesHisNameFrame() {
        collectAllChaosEmeraldsAndSuperEmeraldsExcept(6);
        gameState.markSuperEmeraldCollected(6);
        var screen = new S3kSpecialStageResultsScreen(30, true, 0, gameState.getEmeraldCount(),
                PlayerCharacter.KNUCKLES, true, true);
        int s = TALLY_END_UNDER_50_RINGS + 539;

        runTo(screen, 1, s + 121);
        // sub_2EC80: Knuckles adds 3 to the name frame ($16).
        assertEquals(List.of(0x2C, 0x16, 0x2D, 0x35, 0x12, 0x16), screen.revealFramesForTest());
    }

    @Test
    void fiftyRingsHoldTheContinueWaitBeforeRoutineE() {
        collectAllChaosEmeraldsAndSuperEmeraldsExcept(6);
        var screen = new S3kSpecialStageResultsScreen(50, true, 0, gameState.getEmeraldCount(),
                PlayerCharacter.KNUCKLES, true, true);
        int r = TALLY_END_UNDER_50_RINGS;

        runTo(screen, 1, r + 119);
        assertEquals(-1, screen.sanctuaryRoutineForTest());
        assertFalse(screen.continueIconShownForTest());
        runTo(screen, r + 120, r + 120);
        assertTrue(screen.continueIconShownForTest(), "loc_2E4EA after the 2*60 wait");
        assertEquals(0xE, screen.sanctuaryRoutineForTest(),
                "loc_2E512 overwrites the 270-frame continue wait with 60");
        int e = r + 120;
        runTo(screen, e + 1, e + 155);
        assertEquals(0x2A0, screen.sanctuaryCameraYForTest());
        assertTrue(screen.phase1SlidingOutForTest(5), "slot 35 joins the slide-out with 50 rings");
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
