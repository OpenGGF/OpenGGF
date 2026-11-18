package com.openggf.game.soniccd.fmv;

import com.openggf.data.compression.EnigmaReader;
import com.openggf.game.MenuChrome;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdNemesisAsset;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.release.CdRetailReleases;
import com.openggf.graphics.PixelFont;
import com.openggf.level.PatternDesc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.BitSet;

/** A static archive contact sheet sampled from the authenticated USA disc. */
final class CdThankYouPreview {
    // ThankYou_LoadFirstGraphics (Thank You/Main.asm:265-271) loads this bank starting at tile 1.
    private static final int ART_OFFSET = 0x452, FIRST_TILE = 1, ART_TILES = 154;
    // ThankYou_EnigmaStreamTable (Thank You/Main.asm:1425-1428) gives the first map and its tile base.
    private static final int MAP_OFFSET = 0xA1C, MAP_BASE = 0xC001, MAP_BYTES = 40 * 28 * Short.BYTES;
    // ThankYou_LoadSecondGraphics (Thank You/Main.asm:277-283) starts the next bank here.
    private static final int MAP_END = 0xAAA;
    // ThankYou_InitialPaletteState (Thank You/Main.asm:684-693), 64 words copied at startup.
    private static final int PALETTE_OFFSET = 0x19CE, PALETTE_WORDS = 64;

    private static final int COLUMNS = 8, ROWS = 3;
    private static final int LEFT_CELL = 12, TOP_CELL = 10, CELL_STEP = 2;
    private static final int CENTRING_PIXELS = 4;

    private CdThankYouPreview() { }

    static CdProgramScreenRenderer.Frame load(CdProgramAssets disc) {
        if (CdRetailReleases.find(disc.identity()).orElse(null) != CdRetailReleases.USA) {
            throw new IllegalArgumentException("Thank You preview requires the verified Sonic CD USA disc");
        }
        try {
            // NemesisReader peeks eight bits for the last code and needs the real byte at MAP_OFFSET.
            byte[] bank = disc.read(CdNativeFile.THANKSDATA, ART_OFFSET, MAP_OFFSET + 1 - ART_OFFSET);
            int artBytes = ART_TILES * CdProgramScreenState.TILE_BYTES;
            var art = CdNemesisAsset.decode(bank, artBytes, artBytes).bytes().copy();
            byte[] map = disc.read(CdNativeFile.THANKSDATA, MAP_OFFSET, MAP_END - MAP_OFFSET);
            var cells = ByteBuffer.wrap(EnigmaReader.decompress(
                    Channels.newChannel(new ByteArrayInputStream(map)), MAP_BASE));
            if (cells.remaining() != MAP_BYTES) {
                throw new IOException("Unexpected Thank You map dimensions");
            }
            var palette = ByteBuffer.wrap(disc.read(CdNativeFile.THANKSMAIN,
                    PALETTE_OFFSET, PALETTE_WORDS * Short.BYTES));
            var state = new CdProgramScreenState();
            for (int i = 0; i < PALETTE_WORDS; i++) state.setPalette(i, palette.getShort());
            // Use the first distinct references to this bank, in map order. Their disc palette and
            // orientation carry over into equally spaced archive cells.
            var seen = new BitSet(ART_TILES + FIRST_TILE);
            var descriptor = new PatternDesc();
            int sample = 0;
            while (cells.hasRemaining() && sample < COLUMNS * ROWS) {
                int word = Short.toUnsignedInt(cells.getShort());
                descriptor.set(word);
                int tile = descriptor.getPatternIndex();
                if (tile < FIRST_TILE || tile >= FIRST_TILE + ART_TILES || seen.get(tile)) continue;
                seen.set(tile);
                int tileBytes = CdProgramScreenState.TILE_BYTES;
                state.writeTiles(tile, art, (tile - FIRST_TILE) * tileBytes, tileBytes);
                state.setPlaneCell(CdProgramScreenState.Plane.A,
                        LEFT_CELL + sample % COLUMNS * CELL_STEP, TOP_CELL + sample / COLUMNS * CELL_STEP, word);
                sample++;
            }
            if (sample != COLUMNS * ROWS) {
                throw new IOException("Incomplete Thank You tile sample");
            }
            state.setHScroll(CdProgramScreenState.Plane.A, CENTRING_PIXELS);
            state.setDisplay(true);
            return state.frame();
        } catch (IOException failure) {
            throw new UncheckedIOException("Reading the Thank You disc preview", failure);
        }
    }

    static void drawPanel(PixelFont font, int width) {
        MenuChrome.panel(font, (width - 144) / 2, 64, 144, 72);
        int left = (width - 320) / 2 + LEFT_CELL * 8 + CENTRING_PIXELS;
        int top = TOP_CELL * 8;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                MenuChrome.fill(font, left + column * CELL_STEP * 8 - 2,
                        top + row * CELL_STEP * 8 - 2, 12, 12, MenuChrome.NAVY);
            }
        }
    }
}
