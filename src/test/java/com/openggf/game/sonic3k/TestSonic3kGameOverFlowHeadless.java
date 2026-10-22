package com.openggf.game.sonic3k;

import com.openggf.game.DamageCause;
import com.openggf.game.GameOverExit;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kGameOverCardObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractGameOverCardObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Sonic 3&amp;K game over and time over from a live AIZ1: {@code loc_12432}
 * (docs/skdisasm/sonic3k.asm:24628-24656) loads {@code Obj_GameOver} into
 * {@code Reserved_object_3} and the first dynamic slot, they slide in, and
 * {@code loc_2D666} sends the game on (docs/skdisasm/sonic3k.asm:62129-62141).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kGameOverFlowHeadless {
    private static final int ZONE_AIZ_REGISTRY_INDEX = 0;
    private static final int ACT_1 = 0;
    private static final int MAX_FRAMES_TO_CROSS = 1200;
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private AbstractPlayableSprite sprite;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ_REGISTRY_INDEX, ACT_1);
    }

    @AfterAll
    static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        sprite = fixture.sprite();
    }

    @Test
    void gameOverArtIsResidentFromTheRom() {
        assertNotNull(GameServices.level().getObjectManager().getObjectServices()
                .renderManager().getRenderer(ObjectArtKeys.GAME_OVER),
                "ArtNem_GameOver + Map_GameOver must be loaded for the card");
    }

    @Test
    void lastLifeLoadsGameOverPairAndLeavesForTheTitleScreen() {
        GameStateManager gameState = GameServices.gameState();
        while (gameState.getLives() > 1) gameState.loseLife();
        LevelManager level = GameServices.level();

        killAndCross();

        assertEquals(0, gameState.getLives());
        assertEquals(0, sprite.getDeathCountdown(), "restartime is rewritten to zero");
        S3kGameOverCardObjectInstance word = cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD);
        S3kGameOverCardObjectInstance over = cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_OVER);
        assertEquals(AbstractGameOverCardObjectInstance.FRAME_GAME, word.getMappingFrame());
        assertEquals(AbstractGameOverCardObjectInstance.FRAME_OVER_GAME, over.getMappingFrame());

        assertNativeArtGate(word, over);
        stepUntilConjoined(word, over);
        assertEquals(S3kGameOverCardObjectInstance.WAIT_FRAMES, word.getWaitTimer());
        assertNull(level.getGameOverExitRequested());
        assertFalse(level.isRespawnRequestedForRewind(), "a game over never restarts the level");

        // A/B/C/Start on either controller (loc_2D638 :62109-62112)
        fixture.stepFrame(false, false, false, false, true);
        assertEquals(GameOverExit.TITLE_SCREEN, level.getGameOverExitRequested(),
                "no continues: Game_mode 0 (Sega screen)");
        assertFalse(level.isRespawnRequestedForRewind());
    }

    @Test
    void continueInHandRoutesToTheContinueScreen() {
        GameStateManager gameState = GameServices.gameState();
        while (gameState.getLives() > 1) gameState.loseLife();
        gameState.addContinue();
        LevelManager level = GameServices.level();

        killAndCross();
        S3kGameOverCardObjectInstance word = cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD);
        stepUntilConjoined(word, cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_OVER));
        fixture.stepFrame(false, false, false, false, true);
        assertEquals(GameOverExit.CONTINUE_SCREEN, level.getGameOverExitRequested());
    }

    @Test
    void timeOverLoadsTimeOverPairAndRestartsTheLevel() {
        GameStateManager gameState = GameServices.gameState();
        LevelManager level = GameServices.level();
        int livesBefore = gameState.getLives();
        // The AIZ1 intro holds the player under object control, and a
        // controlled corpse never falls; a time over only happens in play.
        waitForPlayerControl();
        // TimeOver fires at 9:59; the HUD kills the player and raises Time_over_flag.
        level.getLevelGamestate().setTimerFrames(10L * 60 * 60);

        crossDeathRow();

        assertEquals(livesBefore - 1, gameState.getLives(), "a time over still costs a life");
        assertEquals(0, sprite.getDeathCountdown());
        S3kGameOverCardObjectInstance word = cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD);
        S3kGameOverCardObjectInstance over = cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_OVER);
        assertEquals(AbstractGameOverCardObjectInstance.FRAME_TIME, word.getMappingFrame());
        assertEquals(AbstractGameOverCardObjectInstance.FRAME_OVER_TIME, over.getMappingFrame());

        assertNativeArtGate(word, over);
        stepUntilConjoined(word, over);
        fixture.stepFrame(false, false, false, false, true);
        assertTrue(level.isRespawnRequestedForRewind(), "Time_over_flag set: loc_2D680 restarts");
        assertNull(level.getGameOverExitRequested());
    }

    @Test
    void pendingQueueAndCardReconstructionPreserveGameSaveOrdering() {
        GameStateManager gameState = GameServices.gameState();
        while (gameState.getLives() > 1) gameState.loseLife();
        var plc = GameServices.level().getGameModule().getGameService(Sonic3kGameOverPlcService.class);
        var queueOwner = GameServices.level().getGameModule().getGameService(Sonic3kLevelTitlePlcService.class);
        assertNotNull(plc);
        assertNotNull(queueOwner);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saves = new ArrayList<SavePoint>();
        try (var requests = mockStatic(SessionSaveRequests.class)) {
            requests.when(() -> SessionSaveRequests.requestCurrentSessionSave(SaveReason.LIVES_CONTINUES_SAVE))
                    .thenAnswer(invocation -> {
                        var word = cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD);
                        saves.add(new SavePoint(word.getRoutine(), word.getVdpX(),
                                plc.isBusy(), gameState.getLives()));
                        return null;
                    });
            killAndCross();
            fixture.stepIdleFrames(5);
            assertEquals(19, queueOwner.capture().activeEntry().remainingPatterns());
            var pendingQueue = queueOwner.capture();
            var pending = registry.capture();
            assertEquals(pendingQueue, pending.get(queueOwner.key()));
            assertEquals(1, pending.entries().values().stream()
                    .filter(NemesisPlcQueueSnapshot.class::isInstance).count(),
                    "the production rewind registry captures the shared Nemesis FIFO once");
            assertTrue(saves.isEmpty());

            fixture.stepIdleFrames(6);
            assertEquals(1, queueOwner.capture().activeEntry().remainingPatterns());
            assertEquals(0, cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD).getRoutine());
            assertTrue(saves.isEmpty());
            fixture.stepIdleFrames(1);
            var released = registry.capture();
            assertEquals(List.of(new SavePoint(0, 0, false, 0)), saves,
                    "GAME saves zero lives only after the native queue drains, before setup");
            assertEquals(0x60, cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD).getVdpX());

            registry.restore(pending);
            assertEquals(pendingQueue, queueOwner.capture());
            assertEquals(0, cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD).getRoutine());
            assertFalse(cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD).isDisplayedThisFrame());
            assertEquals(1, saves.size(), "reconstruction must not request another save");
            fixture.stepIdleFrames(7);
            assertEquals(List.of(new SavePoint(0, 0, false, 0), new SavePoint(0, 0, false, 0)), saves);
            assertEquals(0x60, cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD).getVdpX());
            assertEquals(0x1E0, cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_OVER).getVdpX());

            registry.restore(released);
            assertFalse(plc.isBusy());
            assertEquals(2, cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD).getRoutine());
            fixture.stepIdleFrames(1);
            assertEquals(0x70, cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD).getVdpX());
            assertEquals(2, saves.size(), "restoring an initialized card must not repeat its save");
        }
    }

    private record SavePoint(int routine, int x, boolean pending, int lives) { }

    private void assertNativeArtGate(S3kGameOverCardObjectInstance word,
                                     S3kGameOverCardObjectInstance over) {
        var plc = GameServices.level().getGameModule().getGameService(Sonic3kGameOverPlcService.class);
        var queueOwner = GameServices.level().getGameModule().getGameService(Sonic3kLevelTitlePlcService.class);
        assertNotNull(plc);
        assertNotNull(queueOwner);
        assertEquals(new NemesisPlcQueueSnapshot.Entry(0x191DE4, 0x79C, 34, 34),
                queueOwner.capture().activeEntry(), "the death producer submitted and prepared ROM PLC3");
        assertTrue(queueOwner.capture().queuedEntries().isEmpty());
        for (int frame = 1; frame <= 12; frame++) {
            assertTrue(plc.isBusy());
            assertEquals(0, word.getRoutine());
            assertEquals(0, over.getRoutine());
            assertFalse(word.isDisplayedThisFrame());
            assertFalse(over.isDisplayedThisFrame());
            fixture.stepIdleFrames(1);
            if (frame < 12) {
                assertEquals(34 - frame * 3, queueOwner.capture().activeEntry().remainingPatterns());
            }
        }
        assertFalse(plc.isBusy());
        assertEquals(2, word.getRoutine());
        assertEquals(2, over.getRoutine());
        assertEquals(0x60, word.getVdpX());
        assertEquals(0x1E0, over.getVdpX());
        assertTrue(word.isDisplayedThisFrame());
        assertTrue(over.isDisplayedThisFrame());
    }

    private void waitForPlayerControl() {
        for (int frame = 0; frame < 3000; frame++) {
            fixture.stepIdleFrames(1);
            if (!sprite.isObjectControlled()) {
                return;
            }
        }
        throw new AssertionError("AIZ1 intro never released the player");
    }

    private void killAndCross() {
        assertTrue(sprite.applyHurtOrDeath(sprite.getCentreX(), DamageCause.SPIKE, false));
        assertTrue(sprite.getDead());
        crossDeathRow();
    }

    private void crossDeathRow() {
        for (int frame = 0; frame < MAX_FRAMES_TO_CROSS; frame++) {
            if (sprite.isInDeathRestartRoutine()) {
                return;
            }
            fixture.stepIdleFrames(1);
        }
        throw new AssertionError("corpse never crossed Camera_Y_pos + $100");
    }

    private void stepUntilConjoined(AbstractGameOverCardObjectInstance word,
                                    AbstractGameOverCardObjectInstance over) {
        for (int frame = 0; frame < 600; frame++) {
            if (word.getRoutine() == 4 && over.getRoutine() == 4) {
                assertEquals(0x120, word.getVdpX());
                assertEquals(0x120, over.getVdpX());
                return;
            }
            fixture.stepIdleFrames(1);
        }
        throw new AssertionError("card never conjoined: word routine " + word.getRoutine()
                + " x=" + word.getVdpX() + ", over routine " + over.getRoutine());
    }

    private S3kGameOverCardObjectInstance cardAt(int slot) {
        for (ObjectInstance instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kGameOverCardObjectInstance card && card.getSlotIndex() == slot) {
                return card;
            }
        }
        throw new AssertionError("no Obj_GameOver at Object_RAM slot " + slot);
    }
}
