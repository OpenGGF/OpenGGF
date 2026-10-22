package com.openggf.game.sonic2.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Independent REV01 asset and native menu-layout contracts; no renderer or host session. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionAssets {
    @Test
    void assetsComeFromTheRetailRevisionAndNativePointerTables() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        assertEquals("8bca5dcef1af3e00098666fd892dc1c2a76333f9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(rom.readAllBytes())));
        RomByteReader reader = RomByteReader.fromRom(rom);
        int[] resultPointers = {0x8804, 0x88CE, 0x8960, 0x8AA4, 0x8B30};
        for (int i = 0; i < resultPointers.length; i++) {
            assertEquals(resultPointers[i], reader.readU32BE(0x87DC + i * 8));
        }
        assertEquals(0x874A, reader.readU32BE(0x8F7E));
        assertEquals(0x00FF0330, reader.readU32BE(0x8F8A));

        var assets = Sonic2CompetitionAssets.load(rom);
        assertEquals(0x58, assets.fontPatterns().length);
        assertEquals(0x15, assets.menuBoxPatterns().length);
        assertEquals(0xAA, assets.previewPatterns().length);
        assertEquals(0x26, assets.winsPatterns().length);
        assertEquals(40, assets.backgroundPatterns().length);
        assertEquals(0x13A, assets.selectPatterns().length);
        assertEquals(0x96, assets.resultsPatterns().length);
        assertTrue(hasOpaquePixels(assets.fontPatterns()));
        assertTrue(hasOpaquePixels(assets.previewPatterns()));
        assertTrue(hasOpaquePixels(assets.winsPatterns()));
    }

    @Test
    void planeMapsMatchIndependentlyDecodedRetailData() throws Exception {
        var assets = Sonic2CompetitionAssets.load(TestEnvironment.currentRom());
        assertEquals(17, assets.selectPanel(false).width());
        assertEquals(12, assets.selectPanel(false).height());
        assertEquals("0d512e5d2c1546683275c070b0c741f1de05d975bbd4c68faf9ba5d8ed83ebcc",
                digest(assets.selectPanel(false)));
        assertEquals("5e90ee86a6af0372afba4db83606131805eacc64a99aea7440adc4e801eedc5a",
                digest(assets.backgroundMap()));

        // Golden decoded plane-map words, independently read from the REV01 Enigma streams.
        String[] hashes = {
                "062322dd00e5360d87bd36ba2981b144d8ec256d3d70656673b729d9e8f6ff9d",
                "9a592f8590c4331b463f50a7eebc555d7dae54d7b82c250975eefc04f0325235",
                "2a10a0e6aba96239aea167623f94adbea6ee7b60bc850ae1d28a5d7b18aeada8",
                "a1a49e6126b86c437bc2a1f8b6203c60a0a38657fa66c17481faafea1d926045",
                "93d218d2b7e2fe0dec4e67c94e41126091a084c02ccbcefe6a0220412a1585ea"
        };
        int[] nonblank = {109, 70, 176, 71, 77};
        var screens = Sonic2CompetitionAssets.ResultScreen.values();
        for (int i = 0; i < screens.length; i++) {
            var map = assets.resultMap(screens[i]);
            assertEquals(40, map.width());
            assertEquals(28, map.height());
            assertEquals(hashes[i], digest(map), screens[i].name());
            assertEquals(nonblank[i], map.words().stream().filter(word -> word != 0).count());
        }
    }

    @Test
    void selectionUsesNativeCardsTextPreviewPalettesAndCompletionCrosses() throws Exception {
        var assets = Sonic2CompetitionAssets.load(TestEnvironment.currentRom());
        var map = assets.selectMap(0, new boolean[4]);
        assertEquals(40, map.width());
        assertEquals(28, map.height());
        assertEquals(0x71, map.wordAt(2, 2));
        assertEquals(0x2071, map.wordAt(22, 2));
        assertEquals(0x6022, map.wordAt(4, 4)); // Highlighted EMERALD HILL starts with E.
        assertEquals(0x002A, map.wordAt(25, 4)); // Unhighlighted " MYSTIC CAVE".
        assertEquals(0x4090, map.wordAt(8, 8));
        assertEquals(0x207B, map.wordAt(28, 8)); // Unselected preview is hidden.
        assertEquals(0x122, assets.selectMap(0, new boolean[]{false, true, false, false}).wordAt(28, 8));
        assertEquals(0x411C, assets.selectMap(3, new boolean[4]).wordAt(28, 21));
        assertEquals(0x491D, assets.selectMap(3, new boolean[4]).wordAt(30, 21));
        assertEquals(4, assets.menuPalettes().length);
        var selected = assets.selectionPalettes(1);
        assertTrue(selected[2].dataEquals(assets.iconPalette(1)));
        assertTrue(selected[0].dataEquals(assets.menuPalettes()[0]));
        assertFalse(assets.iconPalette(0).dataEquals(assets.iconPalette(1)));
    }

    @Test
    void nativeNumberFormattingPreservesLeadingCellsAndUsesFontTiles() throws Exception {
        var assets = Sonic2CompetitionAssets.load(TestEnvironment.currentRom());
        var original = assets.resultMap(Sonic2CompetitionAssets.ResultScreen.ACT);
        var count = assets.withCount(original, 0x486, 42, 0);
        assertEquals(original.words().get(0x486 / 2), count.words().get(0x486 / 2));
        assertEquals(0x14, count.words().get(0x488 / 2));
        assertEquals(0x12, count.words().get(0x48A / 2));
        assertEquals(0x6010, assets.withCount(original, 0x486, 0, 0x6000).words().get(0x48A / 2));
        assertEquals(original, assets.withScore(original, 0x33E, 0, 0));
        var score = assets.withScore(original, 0x33E, 12340, 0);
        assertEquals(0x11, score.words().get(0x340 / 2));
        assertEquals(0x10, score.words().get(0x348 / 2));
        assertEquals(0, original.words().get(0x340 / 2));
        assertEquals(0x6031, assets.withText(original, 0x364,
                Sonic2CompetitionAssets.Text.TIED, 0x6000).words().get(0x364 / 2));
    }

    @Test
    void decodedSnapshotsCannotBeMutatedByPresentationConsumers() throws Exception {
        var assets = Sonic2CompetitionAssets.load(TestEnvironment.currentRom());
        assertThrows(UnsupportedOperationException.class,
                () -> assets.resultMap(Sonic2CompetitionAssets.ResultScreen.ACT).words().set(0, 123));
        var palettes = assets.menuPalettes();
        byte before = assets.menuPalettes()[0].red(1);
        palettes[0].colors[1].r ^= 0x7F;
        assertEquals(before, assets.menuPalettes()[0].red(1));
        var patterns = assets.selectPatterns();
        byte pixel = assets.selectPatterns()[0x10].getPixel(0, 0);
        patterns[0x10].setPixel(0, 0, (byte) (pixel ^ 0xF));
        assertEquals(pixel, assets.selectPatterns()[0x10].getPixel(0, 0));
    }

    private static String digest(Sonic2CompetitionAssets.TileMap map) throws Exception {
        var words = ByteBuffer.allocate(map.words().size() * 2);
        map.words().forEach(word -> words.putShort((short) (int) word));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(words.array()));
    }

    private static boolean hasOpaquePixels(com.openggf.level.Pattern[] patterns) {
        for (var pattern : patterns) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (pattern.getPixel(x, y) != 0) return true;
                }
            }
        }
        return false;
    }
}
