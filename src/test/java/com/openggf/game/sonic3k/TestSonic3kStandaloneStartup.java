package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomFingerprint;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomIdentityTable;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic3k.titlescreen.Sonic3kTitleScreenManager;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real cartridge windows, with no Sonic 3 bytes available to asset readers. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kStandaloneStartup {
    @Test
    void originalCartridgeWindowHasStandaloneIdentityAndBounds() throws Exception {
        try (Rom standalone = standaloneCartridge()) {
            assertEquals(0x200000, standalone.getSize());
            assertEquals("SONIC & KNUCKLES", standalone.readDomesticName().trim());
            assertTrue(RomIdentityTable.verify(RomIdentity.SK,
                    RomFingerprint.of(standalone.readAllBytes())).isPresent());
            assertThrows(java.io.IOException.class, () -> standalone.readByte(0x200000),
                    "Sonic 3 data must remain inaccessible, even though the physical file also contains it");
        }
    }

    @Test
    void standaloneCartridgeCanConstructNativeGameplayLoader() throws Exception {
        try (Rom standalone = standaloneCartridge()) {
            TestEnvironment.configureRomFixture(standalone);
            assertDoesNotThrow(() -> new Sonic3k(standalone),
                    "valid standalone cartridge tables must not be rejected because AIZ is absent");
        }
    }

    @Test
    void standaloneNativeTitleReachesMenuWithoutSonic3ResourceFallback() throws Exception {
        try (Rom standalone = standaloneCartridge()) {
            TestEnvironment.configureRomFixture(standalone);
            GameServices.graphics().initHeadless();
            GameServices.audio().setBackend(new com.openggf.audio.NullAudioBackend());
            var title = (Sonic3kTitleScreenManager) GameServices.module().getTitleScreenProvider();
            var input = mock(InputHandler.class);
            when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
            title.initialize();
            for (int i = 0; i < 1000 && title.getState() != TitleScreenProvider.State.ACTIVE; i++) title.update(input);
            assertEquals(TitleScreenProvider.State.ACTIVE, title.getState());
            assertEquals(7, title.startZoneIndex());
            when(input.logical()).thenReturn(LogicalInputSnapshot.ofPlayers(
                    new com.openggf.control.PlayerInputState(2, 2, 0, 0, false, false),
                    com.openggf.control.PlayerInputState.neutral()));
            title.update(input);
            assertEquals(1, title.nativeTitleState().option(), "native Down selects Knuckles");
            var exit = title.standaloneSinglePlayerExit();
            assertEquals(com.openggf.game.CharacterKey.KNUCKLES, exit.team().main());
            assertEquals(7, exit.zone());
            assertEquals(0, exit.act());
            assertTrue(exit.resetProgress());
            var warm = title.nativeTitleState().capture();
            title.reset();
            assertEquals(1, title.nativeTitleState().option());
            title.nativeTitleState().apply(new com.openggf.game.sonic3k.titlescreen.Sonic3kTitleState.Snapshot(0));
            assertEquals(0, title.nativeTitleState().option());
            title.nativeTitleState().apply(warm);
            assertEquals(1, title.nativeTitleState().option());
        }
    }

    @Test
    void originalSonic3TitleLoadsItsOwnPaletteAndRealTwoPlayerSelection() throws Exception {
        try (Rom image = new Rom()) {
            assertTrue(image.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            try (Rom s3 = Rom.fromReader(RomByteReader.fromRom(image).window(0x200000, 0x200000),
                    "original Sonic 3 cartridge window")) {
                assertTrue(RomIdentityTable.verify(RomIdentity.S3, RomFingerprint.of(s3.readAllBytes())).isPresent());
                assertTrue(Sonic3kCartridgePolicy.isStandaloneS3(s3));
                assertFalse(Sonic3kCartridgePolicy.isStandaloneSk(s3));
                TestEnvironment.configureRomFixture(s3);
                GameServices.graphics().initHeadless();
                GameServices.audio().setBackend(new com.openggf.audio.NullAudioBackend());
                var data = new com.openggf.game.sonic3k.titlescreen.Sonic3kTitleScreenDataLoader();
                assertTrue(data.loadData());
                assertNotNull(data.getFrameDPatterns());
                assertTrue(data.getFrameDPatterns().length > 300);
                assertArrayEquals(s3.readBytes(0x3E88, 128), data.getFrameDPaletteData(),
                        "Pal_TitleSonicD's original-S3 address differs from the SK code-section address");
                var title = (Sonic3kTitleScreenManager) GameServices.module().getTitleScreenProvider();
                var input = mock(InputHandler.class);
                when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
                title.initialize();
                for (int i = 0; i < 1000 && title.getState() != TitleScreenProvider.State.ACTIVE; i++) title.update(input);
                assertEquals(TitleScreenProvider.State.ACTIVE, title.getState());
                assertEquals(TitleScreenProvider.TitleScreenAction.ONE_PLAYER, title.consumeExitAction());
                when(input.logical()).thenReturn(LogicalInputSnapshot.ofPlayers(
                        new com.openggf.control.PlayerInputState(2, 2, 0, 0, false, false),
                        com.openggf.control.PlayerInputState.neutral()));
                title.update(input);
                assertEquals(TitleScreenProvider.TitleScreenAction.TWO_PLAYER, title.consumeExitAction());
                assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Exit.NONE,
                        title.standaloneSinglePlayerExit(), "Sonic 3 must not take the standalone SK launch policy");
            }
        }
    }

    private static Rom standaloneCartridge() throws Exception {
        try (Rom image = new Rom()) {
            assertTrue(image.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            return Rom.fromReader(RomByteReader.fromRom(image).window(0, 0x200000),
                    "verified original SK cartridge window, physical s3k input");
        }
    }
}
