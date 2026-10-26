package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.nativemenu.NativeTitleModeProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercised native-mode limitations. Presentation is replaced at its action
 * boundary only; actual S3K gameplay capability resolution and GameLoop run. */
class TestTasS3kTitleCapabilities {
    @AfterEach void cleanup() { SessionManager.clear(); }

    @Test void currentLimitationCompetitionSelectionReturnsToTitleWithoutACompetitionHost() throws Exception {
        var stock=new Sonic3kGameModule();
        var title=mock(TitleScreenProvider.class);
        when(title.consumeExitAction()).thenReturn(TitleScreenProvider.TitleScreenAction.TWO_PLAYER);
        var module=new DelegatingGameModule(stock,"test:s3k-competition-capability") {
            @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
        };
        TestEnvironment.configureGameModuleFixture(module);
        assertNull(module.getGameService(NativeTitleModeProvider.class));
        var input=mock(InputHandler.class);when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        var loop=new GameLoop(input);loop.setGameplayMode(TestEnvironment.activeGameplayMode());
        loop.setGameMode(GameMode.TITLE_SCREEN);
        try {
            var exit=GameLoop.class.getDeclaredMethod("doExitTitleScreen");exit.setAccessible(true);exit.invoke(loop);
            verify(title).consumeExitAction();verify(title).initialize();
            assertEquals(GameMode.TITLE_SCREEN,loop.getCurrentGameMode());
            assertNull(loop.getNativeMenuScreen());
            assertNull(GameServices.level().getCurrentLevel());
        } finally { loop.closePresence(); }
    }

    @Test void currentLimitationStandaloneKnucklesTitleStartStillRoutesToLockedOnSaveMenu() throws Exception {
        var rom=mock(Rom.class);when(rom.isOpen()).thenReturn(true);
        when(rom.readDomesticName()).thenReturn("SONIC & KNUCKLES");
        var detector=new Sonic3kRomDetector();assertTrue(detector.canHandle(rom));
        var module=detector.createModule();TestEnvironment.configureGameModuleFixture(module);
        var selected=mock(TitleScreenProvider.class);
        when(selected.consumeExitAction()).thenReturn(TitleScreenProvider.TitleScreenAction.ONE_PLAYER);
        var input=mock(InputHandler.class);when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        var loop=new GameLoop(input);loop.setGameplayMode(TestEnvironment.activeGameplayMode());
        try {
            assertNotNull(module.getDataSelectProvider(),"standalone header resolved a real stock module");
            var route=GameLoop.class.getDeclaredMethod("resolveTitleActionRoute",TitleScreenProvider.class);
            route.setAccessible(true);
            assertEquals("DATA_SELECT",route.invoke(loop,selected).toString(),
                    "confirmed standalone start misroutes to locked-on save menu; native SK title launches MHZ");
            verify(selected).consumeExitAction();
            when(selected.consumeExitAction()).thenReturn(TitleScreenProvider.TitleScreenAction.TWO_PLAYER);
            assertEquals("TWO_PLAYER",route.invoke(loop,selected).toString(),"different requested action is routed distinctly");
        } finally {loop.closePresence();}
    }
}
