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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native-mode title consumer regressions. Historical negative evidence remains
 * in the dated TAS reports; real startup presentation is qualified separately. */
class TestTasS3kTitleCapabilities {
    @AfterEach void cleanup() { SessionManager.clear(); }

    @TempDir Path competitionSaves;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @RequiresRom(SonicGame.SONIC_3K)
    void competitionSelectionOpensNativeHostThroughStockProvider(boolean originalS3) throws Exception {
        // Only the selected title action is replaced at this consumer seam. The
        // stock provider, cartridge data, menu host and drawing are production.
        // Natural title input/presentation is qualified by the startup stream.
        var source = com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom());
        var reader = originalS3 ? source.window(0x200000, 0x200000) : source;
        String previousSaves = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", competitionSaves.toString());
        try (var rom = Rom.fromReader(reader, "native Competition capability cartridge")) {
            TestEnvironment.configureRomFixture(rom);
            try (var boot = new com.openggf.tools.HeadlessGameBoot(320, 224)) {
                var services = com.openggf.game.session.EngineServices.current();
                services.audio().setBackend(new com.openggf.audio.NullAudioBackend());
                var stock = new Sonic3kGameModule();
                var title = mock(TitleScreenProvider.class);
                when(title.consumeExitAction()).thenReturn(TitleScreenProvider.TitleScreenAction.TWO_PLAYER);
                var module = new DelegatingGameModule(stock, "test:s3k-competition-title-consumer") {
                    @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
                };
                var context = com.openggf.tools.HeadlessGameBoot.openResolvedSessionForBoot(services, module);
                com.openggf.game.session.GameplaySessionFactory.attachManagers(context, services);
                var input = new InputHandler();
                input.setLogicalOverride(LogicalInputSnapshot.neutral());
                var loop = new GameLoop(services, input);
                loop.setGameplayMode(context);
                loop.setGameMode(GameMode.TITLE_SCREEN);
                try {
                    assertNotNull(module.getGameService(NativeTitleModeProvider.class));
                    var exit = GameLoop.class.getDeclaredMethod("doExitTitleScreen");
                    exit.setAccessible(true); exit.invoke(loop);
                    verify(title).consumeExitAction();
                    assertEquals(GameMode.COMPETITION_MENU, loop.getCurrentGameMode());
                    var visit = assertInstanceOf(
                            com.openggf.game.sonic3k.competition.S3kNativeTitleModeProvider.Visit.class,
                            loop.getNativeMenuScreen());
                    assertEquals(originalS3
                                    ? com.openggf.game.sonic3k.competition.S3kCompetitionResources.ORIGINAL_S3
                                    : com.openggf.game.sonic3k.competition.S3kCompetitionResources.LOCKED_ON,
                            visit.host().resources());
                    assertNull(visit.host().world(), "mode selection has not started a race");
                    assertNull(context.getLevelManager().getCurrentLevel(), "no ordinary coop world substituted");
                    visit.draw(); services.graphics().flushScreenSpace();
                    var image = com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320, 224);
                    var colors = new java.util.HashSet<Integer>();
                    for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) colors.add(image.argb(x, y));
                    assertTrue(colors.size() > 8, "native cartridge menu is visibly drawn");
                } finally {
                    loop.setGameMode(GameMode.TITLE_SCREEN);
                    loop.closePresence();
                    services.graphics().cleanup();
                    com.openggf.graphics.RenderContext.reset();
                    SessionManager.clear();
                }
            }
        } finally {
            if (previousSaves == null) System.clearProperty("openggf.saveRoot");
            else System.setProperty("openggf.saveRoot", previousSaves);
        }
    }

    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void nativeStandaloneKnucklesTitleUsesMhzWhileLockedOnKeepsSaveGrid() throws Exception {
        // The historical mocked-header negative is retained in the dated validation record.
        // The stock module, title/input and actual host router run against verified
        // original cartridge bytes; no test-supplied data-select policy is installed.
        try (var image = new Rom()) {
            assertTrue(image.open(com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            try (var standalone = Rom.fromReader(com.openggf.data.RomByteReader.fromRom(image).window(0, 0x200000),
                    "original SK cartridge window")) {
                assertTrue(com.openggf.data.RomIdentityTable.verify(com.openggf.data.RomIdentity.SK,
                        com.openggf.data.RomFingerprint.of(standalone.readAllBytes())).isPresent());
                TestEnvironment.configureRomFixture(standalone);
                var module = GameServices.module();
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
