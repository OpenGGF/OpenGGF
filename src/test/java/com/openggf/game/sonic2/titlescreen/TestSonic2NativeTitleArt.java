package com.openggf.game.sonic2.titlescreen;

import com.openggf.data.Rom;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2NativeTitleArt {
    @Test
    void obj0fUsesActualRomMappingWordsAndResidentPlayerArrowAndFontPixels() throws Exception {
        var data = new TitleScreenDataLoader();
        assertTrue(data.loadData());
        Rom rom = TestEnvironment.currentRom();
        List<SpriteMappingFrame> frames = data.getMenuMappingFrames();
        assertEquals(3, frames.size());
        for (int frame = 0; frame < 3; frame++) {
            int address = 0x13B70 + rom.read16BitAddr(0x13B70 + frame * 2);
            assertEquals(8, rom.read16BitAddr(address));
            assertEquals(8, frames.get(frame).pieces().size());
            for (int piece = 0; piece < 8; piece++) {
                int offset = address + 2 + piece * 8;
                SpriteMappingPiece actual = frames.get(frame).pieces().get(piece);
                int word = rom.read16BitAddr(offset + 2);
                assertEquals(rom.readByte(offset), actual.yOffset());
                assertEquals((short) rom.read16BitAddr(offset + 6), actual.xOffset());
                assertEquals(word & 0x7FF, actual.tileIndex());
                assertEquals((word >>> 13) & 3, actual.paletteIndex());
                for (int tile = actual.tileIndex(); tile < actual.tileIndex()
                        + actual.widthTiles() * actual.heightTiles(); tile++) {
                    assertNotNull(data.getMenuPattern(tile), "Every referenced title menu tile is ROM-backed");
                }
            }
        }
        assertTrue(hasPixels(data.getMenuPattern(0x402)), "ONE PLAYER numeral must have pixels");
        assertTrue(hasPixels(data.getMenuPattern(0x3FA)), "ROM selection arrow must have pixels");
        assertTrue(hasPixels(data.getMenuPattern(0x69C)), "ROM OPTIONS O must have pixels");
    }

    @Test
    void gatedMenuUsesRomGlyphsWithoutAdvertisingTwoPlayerAndFullMenuUsesUnmodifiedFrames() throws Exception {
        var title = new TitleScreenManager();
        Field field = TitleScreenManager.class.getDeclaredField("dataLoader");
        field.setAccessible(true);
        var data = (TitleScreenDataLoader) field.get(title);
        assertTrue(data.loadData());
        for (int selection : new int[] {0, 2}) {
            TestSonic2NativeTitleControls.set(title, "titleSelection", selection);
            var pieces = title.nativeMenuPieces();
            assertFalse(pieces.stream().anyMatch(piece -> piece.tileIndex() == 0x409 || piece.tileIndex() == 0x40A),
                    "Unavailable competition must have no TWO PLAYER/VS text");
            assertEquals(1, pieces.stream().filter(piece -> piece.tileIndex() == 0x3FA).count());
            assertEquals(selection == 0 ? -12 : 4,
                    pieces.stream().filter(piece -> piece.tileIndex() == 0x3FA).findFirst().orElseThrow().yOffset());
            assertTrue(pieces.stream().anyMatch(piece -> piece.tileIndex() == 0x69C && piece.yOffset() == 4));
        }
        title.setNativeMenuAvailability(true);
        for (int selection = 0; selection < 3; selection++) {
            TestSonic2NativeTitleControls.set(title, "titleSelection", selection);
            assertEquals(data.getMenuMappingFrames().get(selection).pieces(), title.nativeMenuPieces());
        }
    }

    @Test
    void productionMenuDrawSubmitsNativeAbsoluteTilesPositionsAndSelectedPalettes() throws Exception {
        var title = new TitleScreenManager();
        title.setNativeMenuAvailability(true);
        Field field = TitleScreenManager.class.getDeclaredField("dataLoader");
        field.setAccessible(true);
        assertTrue(((TitleScreenDataLoader) field.get(title)).loadData());
        var calls = new ArrayList<Tile>();
        GraphicsManager graphics = mock(GraphicsManager.class);
        doAnswer(call -> {
            calls.add(new Tile(call.getArgument(0), ((PatternDesc) call.getArgument(1)).get(),
                    call.getArgument(2), call.getArgument(3)));
            return null;
        }).when(graphics).renderPatternWithId(anyInt(), any(PatternDesc.class), anyInt(), anyInt());
        title.drawNativeMenu(graphics);
        assertTrue(calls.contains(new Tile(TitleScreenDataLoader.PATTERN_BASE + 0x402, 0x6402, 124, 192)));
        assertTrue(calls.contains(new Tile(TitleScreenDataLoader.PATTERN_BASE + 0x409, 0x409, 124, 208)));
        assertTrue(calls.contains(new Tile(TitleScreenDataLoader.PATTERN_BASE + 0x3FA, 0x63FA, 100, 192)));
        assertEquals(18, calls.size(), "Native frame has 7/9-tile player rows and a 2-tile arrow");
        TestSonic2NativeTitleControls.set(title, "titleSelection", 2);
        calls.clear();
        title.drawNativeMenu(graphics);
        assertTrue(calls.contains(new Tile(TitleScreenDataLoader.PATTERN_BASE + 0x69C, 0x669C, 132, 196)));
        assertEquals(9, calls.size(), "OPTIONS has seven font tiles and a two-tile arrow");
    }

    private record Tile(int pattern, int word, int x, int y) { }

    private static boolean hasPixels(Pattern pattern) {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            if (pattern.getPixel(x, y) != 0) return true;
        }
        return false;
    }
}
