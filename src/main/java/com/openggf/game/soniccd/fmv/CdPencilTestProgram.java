package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailReleases;
import com.openggf.game.timing.HardwareTimingService;

/** Presents the silent Pencil Test stream as a clip in OpenGGF's Visual Mode. */
final class CdPencilTestProgram implements CdVideoProgram {
    private static final int COLUMNS = 21, ROWS = 14;
    private static final int LEFT = 10, TOP = 7;  // tile coordinates inside the playback viewport
    private static final int FIRST_TILE = 1;    // renderer reserves tile zero for empty cells
    private static final int PALETTE_OFFSET = 0x10, PALETTE_COLORS = 16, TILE_OFFSET = 0x30;
    private static final int BANKS = 840;
    private static final int EXIT_HOLD_VINTS = 61;

    private final CdFmvStreamer stream;
    private final CdProgramScreenState screen = new CdProgramScreenState();
    private boolean picturePending;
    private int exitHold = EXIT_HOLD_VINTS;

    CdPencilTestProgram(CdProgramAssets disc, HardwareTimingService timing) {
        if (CdRetailReleases.find(disc.identity()).orElse(null) != CdRetailReleases.USA) {
            throw new IllegalArgumentException("Pencil Test requires the verified Sonic CD USA disc");
        }
        var file = CdNativeFile.PENCILSTM;
        var entry = CdFmvScreen.entry(disc, file);
        var layout = CdFmvStream.Layout.MUTE;
        if (entry.directory() || entry.size() != (long) BANKS * layout.packetSectors * CdFmvStream.SECTOR_BYTES) {
            throw new IllegalArgumentException("Unexpected Pencil Test stream extent");
        }
        stream = new CdFmvStreamer(new CdFmvStream(disc, file), layout, null, null,
                CdFmvStreamer.seekFor(timing, entry));

        // DrawFMVTilemaps (FMV/Main (Pencil Test).asm:355-377) walks tiles left to right, then top to bottom.
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                screen.setPlaneCell(CdProgramScreenState.Plane.A, LEFT + column, TOP + row,
                        FIRST_TILE + row * COLUMNS + column);
            }
        }
        screen.setBackdrop(PALETTE_COLORS);  // black surround in the browser's playback view
    }

    @Override public void frame(int pad) {
        if (done()) return;
        stream.advanceVint();
        if (picturePending) {
            showPicture(stream.shownBank());
            picturePending = false;
        } else if (stream.bankReady()) {
            // FrameRead_Start and FrameRead_Sect1 (FMV/Main (Pencil Test).asm:181-225) stage a picture over two
            // V-ints, as do FrameRead_Sect3_1 and FrameRead_Sect3_2 (:231-274) for the alternating image.
            // Publish the complete picture and palette on the second.
            stream.takeBank();
            picturePending = true;
        } else if (stream.finished()) {
            // Exit (FMV/Main (Pencil Test).asm:147-155) waits 61 V-syncs before returning.
            exitHold--;
        }
        stream.playVint();
    }

    private void showPicture(byte[] bank) {
        // FrameRead_Start (FMV/Main (Pencil Test).asm:181-214) reads the header and palette,
        // then 0x12c0 bytes of tiles. FrameRead_Sect1 (:216-225) appends the other 0x1200 bytes.
        if (word(bank, 0) != 0x3135) {
            throw new IllegalArgumentException("Invalid Pencil Test frame header in bank " + stream.banksTaken());
        }
        for (int color = 0; color < PALETTE_COLORS; color++) {
            screen.setPalette(color, word(bank, PALETTE_OFFSET + color * 2));
        }
        screen.writeTiles(FIRST_TILE, bank, TILE_OFFSET, COLUMNS * ROWS * CdProgramScreenState.TILE_BYTES);
        screen.setDisplay(true);
    }

    private static int word(byte[] bank, int offset) {
        return (bank[offset] & 0xff) << 8 | (bank[offset + 1] & 0xff);
    }

    @Override public boolean done() { return exitHold == 0; }
    @Override public CdProgramScreenState screen() { return screen; }
}
