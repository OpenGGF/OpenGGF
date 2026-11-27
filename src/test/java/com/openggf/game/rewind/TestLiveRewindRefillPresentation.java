package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.presentation.TransportHud;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Refill and unlimited-pulse presentation driven through the real manager, input and history:
 * which budget events start the refill plate, its fill/hold/hide sequence, and what ends it.
 */
class TestLiveRewindRefillPresentation {
    private SonicConfigurationService config;
    private AudioManager audio;
    private LiveRewindManager manager;
    private GameplayModeContext owner;
    private final int[] ticks = {0};
    private final InputHandler input = new InputHandler();

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, false);
        audio = EngineServices.current().audio();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        manager = new LiveRewindManager(config, audio, () -> GameMode.SPECIAL_STAGE);
        owner = new GameplayModeContext(new WorldSession(GameModuleRegistry.getCurrent()));
        GameplaySessionFactory.attachManagers(owner, EngineServices.current());
        RewindSnapshottable<Integer> adapter = new RewindSnapshottable<>() {
            @Override public String key() { return SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY; }
            @Override public Integer capture() { return ticks[0]; }
            @Override public void restore(Integer snapshot) { ticks[0] = snapshot; }
        };
        SpecialStageProvider stage = mock(SpecialStageProvider.class);
        when(stage.supportsRewind()).thenReturn(true);
        when(stage.rewindAdapter()).thenReturn(Optional.of(adapter));
        owner.registerSpecialStageAdapter(stage);
    }

    @AfterEach
    void tearDown() {
        manager.bindOwner(null, false, GameMode.MASTER_TITLE_SCREEN);
        owner.destroy();
        SessionManager.clear();
        audio.resetState();
        config.resetToDefaults();
    }

    @Test
    void lifeLossRefillFillsFromWhatWasLeftHoldsFullThenHides() {
        spendFrames(18);
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        assertFalse(manager.budgetHudState().visible(), "the refill waits for the next gameplay frame");

        idle();
        TransportHud.Message first = message();
        assertEquals(TransportHud.Status.REWIND, first.status());
        assertEquals("9.7s", first.value(), "the fill starts from the pre-refill amount");
        assertEquals(97, first.progressPercent());
        int fill = LiveRewindHudOverlay.refillFillFrames(60);
        int total = LiveRewindHudOverlay.refillAnimationFrames(60);
        int previous = first.progressPercent();
        for (int frame = 1; frame < fill; frame++) {
            idle();
            assertTrue(message().progressPercent() >= previous, "the meter only fills");
            previous = message().progressPercent();
        }
        for (int frame = fill; frame < total; frame++) {
            idle();
            assertEquals("10.0s", message().value(), "held full at frame " + frame);
            assertEquals(100, message().progressPercent());
        }
        idle();
        assertFalse(manager.budgetHudState().visible(), "the plate hides after the hold");
    }

    @Test
    void pressingRewindCancelsTheRefillAndTheRewindPlateTakesOver() {
        spendFrames(18);
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        idle();
        idle();
        assertEquals(1, manager.budgetHudState().refillFrame(), "one frame into the fill");

        holdRewind();
        assertEquals(-1, manager.budgetHudState().refillFrame());
        assertEquals("10.0s", message().value(), "the normal rewind plate counts the live allowance");
        releaseKey();
        idle();
        idle();
        assertFalse(manager.budgetHudState().visible(), "the cancelled refill does not come back");
    }

    @Test
    void refillWithNothingSpentOrWithRefillDisabledShowsNothing() {
        record(5);
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        idle();
        assertFalse(manager.budgetHudState().visible(), "an already full pool restores nothing");

        spendFrames(6);
        config.setConfigValue(SonicConfiguration.REWIND_REFILL_ON_LIFE_LOSS, false);
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        idle();
        assertFalse(manager.budgetHudState().visible());
    }

    @Test
    void levelLoadAndNewRunEndAShownRefill() {
        spendFrames(6);
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        idle();
        assertTrue(manager.budgetHudState().visible());
        owner.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
        idle();
        assertFalse(manager.budgetHudState().visible());

        spendFrames(6);
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        idle();
        assertTrue(manager.budgetHudState().visible());
        manager.startNewRun();
        idle();
        assertFalse(manager.budgetHudState().visible());
    }

    @Test
    void aDeathReloadsOwnLevelLoadAndTitleCardReportsKeepThePendingLifeLossRefill() {
        spendFrames(6);
        // FULL_DEATH_RELOAD order: the respawn refill is marked inside the load, then the
        // title card exit and the loop's level-load report arrive before gameplay resumes.
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        owner.markRewindBoundary(RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE);
        owner.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
        idle();
        assertEquals(0, manager.budgetHudState().refillFrame(), "the refill shows once the player can move");
        assertTrue(manager.budgetHudState().visible());
    }

    @Test
    void blockedFramesHoldTheRefillHiddenUntilGameplayResumes() {
        spendFrames(6);
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        for (int frame = 0; frame < 120; frame++) {
            manager.handleRealtimeRewindInput(GameMode.SPECIAL_STAGE, true, input);
            assertFalse(manager.budgetHudState().visible(), "nothing over a blocked transition");
        }
        idle();
        assertEquals(0, manager.budgetHudState().refillFrame(), "the refill starts once rewind is allowed");
    }

    @Test
    void unlimitedNeverAnimatesARefillAndPulsesOnlyWhileTheKeyIsHeld() {
        config.setConfigValue(SonicConfiguration.REWIND_BUDGET_SECONDS, "unlimited");
        spendFrames(6);
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        idle();
        assertFalse(manager.budgetHudState().visible(), "unlimited has nothing to restore");

        record(10);
        holdRewind();
        TransportHud.Message held = message();
        assertEquals(TransportHud.ValueMark.INFINITY, held.valueMark());
        assertEquals("", held.value());
        assertEquals(100, held.progressPercent());
        assertEquals(0, manager.budgetHudState().pulseFrame());
        holdRewind();
        assertEquals(1, manager.budgetHudState().pulseFrame());
        releaseKey();
        manager.handleRealtimeRewindInput(GameMode.SPECIAL_STAGE, false, input);
        assertEquals(-1, manager.budgetHudState().pulseFrame(), "release stops the pulse");
        holdRewind();
        assertEquals(0, manager.budgetHudState().pulseFrame(), "a new hold starts at the resting edge");
    }

    @Test
    void offScopeShowsNoRefill() {
        spendFrames(6);
        config.setConfigValue(SonicConfiguration.REWIND_BUDGET_SCOPE, "off");
        owner.markRewindBoundary(RewindBoundary.LIFE_LOSS_RESPAWN);
        idle();
        assertFalse(manager.budgetHudState().visible());
    }

    private TransportHud.Message message() {
        var state = manager.budgetHudState();
        assertTrue(state.visible());
        return LiveRewindHudOverlay.message(null, state);
    }

    /** Records forward history, rewinds {@code frames} ticks at normal speed and releases. */
    private void spendFrames(int frames) {
        record(frames + 4);
        for (int frame = 0; frame < frames; frame++) holdRewind();
        releaseKey();
        idle();
        assertFalse(manager.isRewindingOrReleasing());
    }

    private void record(int frames) {
        manager.bindOwner(owner, false, GameMode.SPECIAL_STAGE);
        for (int frame = 0; frame < frames; frame++) {
            ticks[0]++;
            manager.recordExternalFrame(GameMode.SPECIAL_STAGE, false, input);
        }
    }

    private void idle() {
        assertFalse(manager.handleRealtimeRewindInput(GameMode.SPECIAL_STAGE, false, input));
    }

    private void holdRewind() {
        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
        assertTrue(manager.handleRealtimeRewindInput(GameMode.SPECIAL_STAGE, false, input));
    }

    private void releaseKey() {
        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_RELEASE);
    }
}
