package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameMode;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.nativemenu.*;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
class TestGameLoopNativeMenus {
    @AfterEach
    void closeOwnedRuntime() {
        Engine.clearGlobalInstance();
        SessionManager.clear();
    }

    @Test
    void nativeSessionPublicationRebindsEngineDrawingAndLoopToTheSameNewManagers() throws Exception {
        var fixture = fixture(TitleScreenProvider.TitleScreenAction.OPTIONS,
                mock(NativeTitleModeProvider.class));
        var old = fixture.loop.resolveGameplayModeContext();
        var oldLevel = old.getLevelManager();
        var engine = new Engine(EngineServices.current());
        var bind = Engine.class.getDeclaredMethod("bindGameplayMode", GameplayModeContext.class);
        bind.setAccessible(true);
        bind.invoke(engine, old);
        var replacement = SessionManager.openGameplaySession(new Sonic2GameModule());
        GameplaySessionFactory.attachManagers(replacement, EngineServices.current());

        engine.getGameLoop().publishNativeGameplayMode(replacement);

        assertSame(replacement, field(engine, "gameplayMode"));
        assertSame(replacement.getLevelManager(), field(engine, "levelManager"));
        assertSame(replacement.getSpriteManager(), field(engine, "spriteManager"));
        assertSame(replacement.getCamera(), field(engine, "camera"));
        assertSame(replacement, engine.getGameLoop().resolveGameplayModeContext());
        assertNotSame(oldLevel, replacement.getLevelManager());
    }
    @Test
    void optionsActionEntersActualNativeProviderWithoutLoadingOnePlayerWorld() throws Exception {
        var options = mock(NativeMenuScreen.class);
        when(options.mode()).thenReturn(GameMode.OPTIONS);
        var modeProvider = mock(NativeTitleModeProvider.class);
        when(modeProvider.optionsScreen()).thenReturn(options);
        var fixture = fixture(TitleScreenProvider.TitleScreenAction.OPTIONS, modeProvider);

        invoke(fixture.loop, "doExitTitleScreen");

        assertEquals(GameMode.OPTIONS, fixture.loop.getCurrentGameMode());
        assertSame(options, fixture.loop.getNativeMenuScreen());
        verify(options).initialize();
        assertNull(fixture.loop.resolveGameplayModeContext().getLevelManager().getCurrentLevel());
    }

    @Test
    void competitionTokenRequiresConnectedProviderAndCannotStartOrdinaryCoop() throws Exception {
        var fixture = fixture(TitleScreenProvider.TitleScreenAction.TWO_PLAYER,
                mock(NativeTitleModeProvider.class));

        invoke(fixture.loop, "doExitTitleScreen");

        assertEquals(GameMode.TITLE_SCREEN, fixture.loop.getCurrentGameMode());
        assertNull(fixture.loop.getNativeMenuScreen());
        verify(fixture.title).initialize();
        assertNull(fixture.loop.resolveGameplayModeContext().getLevelManager().getCurrentLevel());
    }

    @Test
    void nativeModeIsPublishedOnlyAfterSetupCompletesOutsideTheOutgoingToken() throws Exception {
        var mode = new java.util.concurrent.atomic.AtomicReference<>(GameMode.COMPETITION_RESULTS);
        var competition = mock(NativeMenuScreen.class);
        when(competition.mode()).thenAnswer(ignored -> mode.get());
        when(competition.consumeExit()).thenReturn(NativeMenuScreen.Exit.NONE);
        var provider = mock(NativeTitleModeProvider.class);
        when(provider.competitionScreen()).thenReturn(competition);
        var fixture = fixture(TitleScreenProvider.TitleScreenAction.TWO_PLAYER, provider);
        invoke(fixture.loop, "doExitTitleScreen");
        var lifecycle = fixture.loop.resolveGameplayModeContext().plcFrameLifecycle();
        doAnswer(ignored -> {
            assertEquals(GameMode.COMPETITION_RESULTS, fixture.loop.getCurrentGameMode());
            lifecycle.runLogicalIteration(() -> { }, frame -> {
                GameLoopPlcLifecycle.runPhase(frame,
                        com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD, () -> { });
                return null;
            });
            mode.set(GameMode.COMPETITION);
            return null;
        }).when(competition).afterFrame();

        fixture.loop.step();

        assertEquals(GameMode.COMPETITION, fixture.loop.getCurrentGameMode());
        verify(competition).update(fixture.input);
        verify(competition).afterFrame();
        verify(fixture.input).update();
    }

    @Test
    void providerMayChangeNativeMenuToArenaAndResultsWithoutRunningTheOrdinaryLevelLoop() throws Exception {
        var competition = mock(NativeMenuScreen.class);
        when(competition.mode()).thenReturn(GameMode.COMPETITION_MENU);
        when(competition.consumeExit()).thenReturn(NativeMenuScreen.Exit.NONE);
        var provider = mock(NativeTitleModeProvider.class);
        when(provider.competitionScreen()).thenReturn(competition);
        var fixture = fixture(TitleScreenProvider.TitleScreenAction.TWO_PLAYER, provider);
        invoke(fixture.loop, "doExitTitleScreen");
        when(competition.mode()).thenReturn(GameMode.COMPETITION);
        invoke(fixture.loop, "updateNativeMenuMode");
        assertEquals(GameMode.COMPETITION_MENU, fixture.loop.getCurrentGameMode());
        invoke(fixture.loop, "completeNativeMenuFrame");
        assertEquals(GameMode.COMPETITION, fixture.loop.getCurrentGameMode());
        when(competition.mode()).thenReturn(GameMode.COMPETITION_RESULTS);
        invoke(fixture.loop, "updateNativeMenuMode");
        assertEquals(GameMode.COMPETITION, fixture.loop.getCurrentGameMode());
        invoke(fixture.loop, "completeNativeMenuFrame");
        assertEquals(GameMode.COMPETITION_RESULTS, fixture.loop.getCurrentGameMode());
        verify(fixture.input, times(2)).update();
        verify(competition, times(2)).update(fixture.input);
    }

    private static Fixture fixture(TitleScreenProvider.TitleScreenAction action,
                                    NativeTitleModeProvider provider) {
        var title = mock(TitleScreenProvider.class);
        when(title.consumeExitAction()).thenReturn(action);
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:native-menu") {
            @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
            @Override public <T> T getGameService(Class<T> type) {
                return type == NativeTitleModeProvider.class ? type.cast(provider) : super.getGameService(type);
            }
        };
        SessionManager.openGameplaySession(module);
        var gameplay = TestEnvironment.activeGameplayMode();
        var input = mock(InputHandler.class);
        when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        var loop = new GameLoop(input);
        loop.setGameplayMode(gameplay);
        loop.setGameMode(GameMode.TITLE_SCREEN);
        return new Fixture(loop, title, input);
    }

    private static void invoke(GameLoop loop, String name) throws Exception {
        var method = GameLoop.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(loop);
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private record Fixture(GameLoop loop, TitleScreenProvider title, InputHandler input) { }
}
