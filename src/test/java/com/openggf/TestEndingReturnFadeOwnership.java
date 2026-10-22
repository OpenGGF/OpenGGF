package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.game.EndingPhase;
import com.openggf.game.EndingProvider;
import com.openggf.game.GameMode;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Repeated ending completion uses the real loop, palette fade and native PLC lease. */
@Isolated
class TestEndingReturnFadeOwnership {
    private GameLoop loop;

    @AfterEach
    void tearDown() {
        if (loop != null) loop.closePresence();
        loop = null;
        SessionManager.clear();
    }

    @RequiresRom(SonicGame.SONIC_1)
    @ParameterizedTest
    @EnumSource(value = EndingPhase.class, names = {"POST_CREDITS", "CUTSCENE", "FINISHED"})
    void aPersistentEndingCompletionCannotBypassItsOutgoingFade(EndingPhase phase) throws Exception {
        TitleScreenProvider title = mock(TitleScreenProvider.class);
        when(title.getState()).thenReturn(TitleScreenProvider.State.ACTIVE);
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule() {
            @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
        });
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var mode = fixture.gameplayMode();
        mode.getLevelManager().consumeTitleCardRequest();
        loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(mode);
        loop.changeGameModeWithoutRewindBoundary(phase == EndingPhase.CUTSCENE
                ? GameMode.ENDING_CUTSCENE : GameMode.TRY_AGAIN_END);
        EndingProvider ending = mock(EndingProvider.class);
        when(ending.getCurrentPhase()).thenReturn(phase);
        when(ending.isComplete()).thenReturn(true);
        Field provider = GameLoop.class.getDeclaredField("endingProvider");
        provider.setAccessible(true);
        provider.set(loop, ending);
        FadeManager fade = mode.getFadeManager();
        assertFalse(fade.isActive());

        loop.step();
        assertEquals(FadeManager.FadeState.FADING_TO_BLACK, fade.getState());
        assertTrue(fade.hasPendingCompletion());
        assertSame(ending, loop.getEndingProvider());
        verify(title, never()).initialize();

        int iterations = 0;
        while (loop.getCurrentGameMode() != GameMode.TITLE_SCREEN) {
            assertTrue(++iterations < 64, "the actual outgoing callback must eventually enter title");
            assertDoesNotThrow(loop::step, "a repeated completion cannot start a nested native fade");
            if (loop.getCurrentGameMode() != GameMode.TITLE_SCREEN) {
                assertSame(ending, loop.getEndingProvider(), "the outgoing callback still owns departure");
                assertTrue(fade.hasPendingCompletion());
                verify(title, never()).initialize();
            }
        }
        assertTrue(iterations > 1, "the return must wait for the real outgoing fade");
        verify(title, times(1)).initialize();
        assertNull(loop.getEndingProvider());
        assertEquals(FadeManager.FadeState.FADING_FROM_BLACK, fade.getState(),
                "the closed outgoing lease permits a new reveal lease in its callback");
        for (int frame = 0; frame < 64 && fade.isActive(); frame++) loop.step();
        assertFalse(fade.isActive(), "the reveal must also close normally");
        verify(title, times(1)).initialize();
    }

    @ParameterizedTest
    @EnumSource(value = FadeManager.FadeState.class, names = {"FADING_TO_BLACK", "FADING_FROM_BLACK"})
    void anExistingCallbackOwnsCompletionBeforeAnyRepeatedCleanup(FadeManager.FadeState state) {
        var sprites = mock(SpriteManager.class);
        var level = mock(LevelManager.class);
        var audio = mock(AudioManager.class);
        FadeManager fade = new FadeManager();
        AtomicInteger owner = new AtomicInteger();
        AtomicInteger replacement = new AtomicInteger();
        if (state == FadeManager.FadeState.FADING_TO_BLACK) fade.startFadeToBlack(owner::incrementAndGet);
        else fade.startFadeFromBlack(owner::incrementAndGet);
        int frame = fade.getFrameCount();

        GameLoopGameOverExit.startEndingReturn(sprites, level, "sonic", audio, fade, null,
                replacement::incrementAndGet);

        verifyNoInteractions(sprites, level, audio);
        assertEquals(state, fade.getState());
        assertEquals(frame, fade.getFrameCount());
        assertTrue(fade.hasPendingCompletion());
        assertEquals(0, replacement.get());
        for (int i = 0; i < 64 && owner.get() == 0; i++) fade.update();
        assertEquals(1, owner.get(), "the original callback survives unchanged");
        assertEquals(0, replacement.get());
    }

    @Test
    void aCompletedBlackHoldWithoutAPendingCallbackRetainsItsImmediateHandoff() {
        var sprites = mock(SpriteManager.class);
        var level = mock(LevelManager.class);
        var audio = mock(AudioManager.class);
        FadeManager fade = new FadeManager();
        AtomicInteger old = new AtomicInteger();
        AtomicInteger handoff = new AtomicInteger();
        fade.startFadeToBlack(old::incrementAndGet);
        for (int frame = 0; frame < 64 && old.get() == 0; frame++) fade.update();
        assertEquals(1, old.get());
        assertEquals(FadeManager.FadeState.HOLD_BLACK, fade.getState());
        assertFalse(fade.hasPendingCompletion());

        GameLoopGameOverExit.startEndingReturn(sprites, level, "sonic", audio, fade, null,
                handoff::incrementAndGet);

        assertEquals(1, handoff.get());
        verify(sprites).setInputSuppressed(false);
        verify(level).setForceHudSuppressed(false);
        verify(audio).fadeOutMusic();
        assertEquals(FadeManager.FadeState.HOLD_BLACK, fade.getState());
    }
}
