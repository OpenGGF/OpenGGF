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

/** Reached native title capability checks, with historical limits recorded separately. */
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

    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void nativeStandaloneKnucklesTitleUsesMhzWhileLockedOnKeepsSaveGrid() throws Exception {
        // The historical mocked-header negative is retained in the dated validation record.
        // Supply only the parent's pending module-level data-select composition; title/input
        // and the actual host router run against verified original cartridge bytes.
        try (var image = new Rom()) {
            assertTrue(image.open(com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            try (var standalone = Rom.fromReader(com.openggf.data.RomByteReader.fromRom(image).window(0, 0x200000),
                    "original SK cartridge window")) {
                assertTrue(com.openggf.data.RomIdentityTable.verify(com.openggf.data.RomIdentity.SK,
                        com.openggf.data.RomFingerprint.of(standalone.readAllBytes())).isPresent());
                TestEnvironment.configureRomFixture(standalone);
                var stock = GameServices.module();
                var module = new DelegatingGameModule(stock, "test:pending-standalone-data-select-composition") {
                    @Override public DataSelectProvider getDataSelectProvider() {
                        return Sonic3kCartridgePolicy.isStandaloneSk() ? null : super.getDataSelectProvider();
                    }
                    @Override public com.openggf.game.dataselect.DataSelectPresentationProvider getDataSelectPresentationProvider() {
                        return Sonic3kCartridgePolicy.isStandaloneSk() ? null : super.getDataSelectPresentationProvider();
                    }
                };
                TestEnvironment.configureGameModuleFixture(module);
                GameServices.rom().setRom(standalone);
                GameServices.graphics().initHeadless();
                GameServices.audio().setBackend(new com.openggf.audio.NullAudioBackend());
                var title = (com.openggf.game.sonic3k.titlescreen.Sonic3kTitleScreenManager) module.getTitleScreenProvider();
                var input = mock(InputHandler.class);
                when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
                title.initialize();
                for (int i = 0; i < 1000 && title.getState() != TitleScreenProvider.State.ACTIVE; i++) title.update(input);
                assertEquals(TitleScreenProvider.State.ACTIVE, title.getState());
                when(input.logical()).thenReturn(LogicalInputSnapshot.ofPlayers(
                        new com.openggf.control.PlayerInputState(2, 2, 0, 0, false, false),
                        com.openggf.control.PlayerInputState.neutral()));
                title.update(input);
                assertEquals(CharacterKey.KNUCKLES, title.standaloneSinglePlayerExit().team().main());
                var loop = new GameLoop(input);
                loop.setGameplayMode(TestEnvironment.activeGameplayMode());
                try {
                    var route = GameLoop.class.getDeclaredMethod("resolveTitleActionRoute", TitleScreenProvider.class);
                    route.setAccessible(true);
                    assertEquals("LEVEL", route.invoke(loop, title).toString());
                    assertEquals(7, title.startZoneIndex());
                    TestEnvironment.configureRomFixture(image);
                    loop.setGameplayMode(TestEnvironment.activeGameplayMode());
                    assertEquals("DATA_SELECT", route.invoke(loop, GameServices.module().getTitleScreenProvider()).toString(),
                            "the same SK header with the actual S3 lock-on keeps its native save grid");
                } finally { loop.closePresence(); }
            }
        }
    }

}
