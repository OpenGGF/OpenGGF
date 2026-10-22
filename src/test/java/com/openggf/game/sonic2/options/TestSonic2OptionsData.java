package com.openggf.game.sonic2.options;

import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.compression.EnigmaReader;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.sprites.playable.AbstractPlayableSprite.*;
import static com.openggf.game.sonic2.options.TestSonic2OptionsControls.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2OptionsData {
    @Test
    void decodesActualRomBoxesTextArtPalettesAndBackgroundAnimation() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var data = Sonic2OptionsData.load(rom, true);
        assertArrayEquals(decodeWords(rom, 0x9AB2, 44, 0x70), data.boxMaps[0]);
        assertArrayEquals(decodeWords(rom, 0x9AB2, 44, 0x2070), data.boxMaps[1]);
        assertArrayEquals(decodeWords(rom, 0x7CB80, 428, 0x6000), data.backgroundMap);
        assertEquals(176, data.boxMaps[0].length);
        assertEquals(1120, data.backgroundMap.length);
        assertArrayEquals(rom.readBytes(0x97CA + 1, 17), bytes(data.headers[0]));
        assertArrayEquals(rom.readBytes(0x97EC + 1, 15), bytes(data.teamChoices[0]));
        var domestic = Sonic2OptionsData.load(rom, false);
        assertArrayEquals(rom.readBytes(0x97DC + 1, 15), bytes(domestic.teamChoices[0]));
        assertArrayEquals(rom.readBytes(0x980C + 1, 15), bytes(domestic.teamChoices[2]));
        assertTrue(opaquePixels(data.patterns, 0x10, 88) > 500, "ROM font must contain pixels");
        assertTrue(opaquePixels(data.patterns, 0x70, 21) > 200, "ROM MenuBox must contain pixels");
        assertTrue(opaquePixels(data.backgroundArt, 0, 40) > 100, "ROM Sonic/Miles art must contain pixels");
        for (int line = 0; line < 4; line++) {
            Palette expected = new Palette();
            expected.fromSegaFormat(rom.readBytes(0x30E2 + line * 32, 32));
            for (int color = 0; color < 16; color++) {
                assertEquals(expected.colors[color].r, data.palettes[line].colors[color].r);
                assertEquals(expected.colors[color].g, data.palettes[line].colors[color].g);
                assertEquals(expected.colors[color].b, data.palettes[line].colors[color].b);
            }
        }
        int[] starts = {0, 200, 206, 212, 412, 418, 424};
        int[] sources = {0, 10, 20, 30, 20, 10, 0};
        for (int i = 0; i < starts.length; i++) {
            assertEquals(sources[i], data.backgroundSource(starts[i]));
            if (i > 0) assertEquals(sources[i - 1], data.backgroundSource(starts[i] - 1));
        }
    }

    @Test
    void selectedAndUnselectedBoxesUseNativeTextOffsetsAndPaletteLines() throws Exception {
        var data = Sonic2OptionsData.load(TestEnvironment.currentRom(), true);
        var state = new Sonic2OptionsState();
        int[] selected = data.box(0, state);
        assertEquals(0x601A, selected[24], "header star at box(2,1), palette3");
        assertEquals(0x6030, selected[91], "SONIC starts with absolute tile $30, not font-relative $40");
        int[] unselected = data.box(1, state);
        assertEquals(0x1A, unselected[24]);
        assertEquals(0x2070 | (data.boxMaps[0][0] & 0x7FF) - 0x70, unselected[0]);
        press(state, INPUT_UP | INPUT_LEFT, 0);
        int[] sound = data.box(2, state);
        assertEquals(0x6017, sound[97]);
        assertEquals(0x6023, sound[98], "hex F skips the punctuation tiles");
        assertEquals(0x1A, data.box(0, state)[24], "old row is rebuilt as unselected");
    }

    @Test
    void actualScreenDrawSubmitsEveryBackgroundAndBoxCellAtNativeAndWidePositions() throws Exception {
        var data = Sonic2OptionsData.load(TestEnvironment.currentRom(), true);
        var state = new Sonic2OptionsState();
        var tiles = new ArrayList<DrawTile>();
        GraphicsManager graphics = mock(GraphicsManager.class);
        doAnswer(call -> {
            tiles.add(new DrawTile(call.getArgument(0), ((PatternDesc) call.getArgument(1)).get(),
                    call.getArgument(2), call.getArgument(3)));
            return null;
        }).when(graphics).renderPatternWithId(anyInt(), any(PatternDesc.class), anyInt(), anyInt());
        var sounds = new ArrayList<Integer>();
        int[] musicStarts = {0};
        var screen = new Sonic2OptionsScreen(state, true, data, () -> graphics,
                () -> musicStarts[0]++, sounds::add);
        var input = new InputHandler();
        screen.initialize();
        assertEquals(1, musicStarts[0]);
        input.setLogicalOverride(sample(INPUT_UP, 0, false, 0, 0, false));
        for (int frame = 0; frame < 22; frame++) screen.update(input);
        assertEquals(0, state.selectedRow(), "blocking palette fade must not consume controls");
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
        for (int width : new int[] {320, 426}) {
            when(graphics.getProjectionWidth()).thenReturn(width);
            tiles.clear();
            screen.draw();
            int backgroundCells = ((width + 7) / 8) * 28;
            assertEquals(backgroundCells + 3 * 176, tiles.size());
            int columns = (width + 7) / 8;
            for (int i = 0; i < backgroundCells; i++) {
                DrawTile tile = tiles.get(i);
                int word = data.backgroundMap[(i / columns) * 40 + (i % columns) % 40];
                assertEquals(new DrawTile(Sonic2OptionsScreen.PATTERN_BASE + (word & 0x7FF),
                        word, (i % columns) * 8, (i / columns) * 8), tile);
            }
            for (int row = 0; row < 3; row++) {
                int[] box = data.box(row, state);
                for (int cell = 0; cell < 176; cell++) {
                    int word = box[cell];
                    assertEquals(new DrawTile(Sonic2OptionsScreen.PATTERN_BASE + (word & 0x7FF), word,
                            (width - 320) / 2 + (9 + cell % 22) * 8,
                            (3 + row * 8 + cell / 22) * 8), tiles.get(backgroundCells + row * 176 + cell));
                }
            }
        }
        verify(graphics, times(data.patterns.length)).cachePatternTexture(any(Pattern.class), anyInt());
        verify(graphics, times(10)).updatePatternTexture(any(Pattern.class), anyInt());
        verify(graphics, times(8)).cachePaletteTexture(any(Palette.class), anyInt());
        // P2 changes a choice through the real NativeMenuScreen update adapter, then Start exits once.
        input.setLogicalOverride(sample(0, 0, false, INPUT_RIGHT, 0, true));
        screen.update(input);
        assertEquals(1, state.playerOption());
        assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Destination.LEVEL,
                screen.consumeExit().destination());
        assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Exit.NONE, screen.consumeExit());
        screen.reset();
        screen.initialize();
        assertEquals(1, state.playerOption(), "screen reset/entry preserves retail option RAM");
        assertEquals(0, state.selectedRow());
    }

    private record DrawTile(int pattern, int word, int x, int y) { }

    private static int[] decodeWords(Rom rom, int address, int size, int base) throws Exception {
        try (var channel = Channels.newChannel(new ByteArrayInputStream(rom.readBytes(address, size)))) {
            ByteBuffer bytes = ByteBuffer.wrap(EnigmaReader.decompress(channel, base));
            int[] words = new int[bytes.remaining() / 2];
            for (int i = 0; i < words.length; i++) words[i] = Short.toUnsignedInt(bytes.getShort());
            return words;
        }
    }

    private static byte[] bytes(int[] words) {
        byte[] bytes = new byte[words.length];
        for (int i = 0; i < words.length; i++) bytes[i] = (byte) words[i];
        return bytes;
    }

    private static int opaquePixels(Pattern[] patterns, int first, int count) {
        int pixels = 0;
        for (int i = first; i < first + count; i++) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                if (patterns[i].getPixel(x, y) != 0) pixels++;
            }
        }
        return pixels;
    }
}
