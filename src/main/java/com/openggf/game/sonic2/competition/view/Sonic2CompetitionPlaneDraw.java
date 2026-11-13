package com.openggf.game.sonic2.competition.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlanes.PlaneId.*;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlanes.PlaneId;

/**
 * Pure S2 native name-table producer. Inputs are the actual copied camera words and
 * scroll flags; this class advances no camera, deformation, clock, or VRAM owner.
 * The supplied work RAM includes the shipped animated-map writes beyond Block_Table.
 */
public final class Sonic2CompetitionPlaneDraw {
    public record CellPosition(PlaneId plane, int column, int row) { }
    public record CellWrite(CellPosition position, List<Integer> words) {
        public CellWrite { words = List.copyOf(words); }
    }

    /** The layout byte rows interleave foreground then background, each 128 bytes wide. */
    public enum Layout {
        FOREGROUND(0x8000), BACKGROUND(0x8080);
        private final int address;
        Layout(int address) { this.address = address; }
    }

    /** Native camera integer words, not top-left sprite coordinates or a derived parallax ratio. */
    public record CameraCopy(int x, int y) {
        public CameraCopy { x &= 0xFFFF; y &= 0xFFFF; }
    }

    /** GetBlock's source addresses and the two native 8x16 name words after chunk flips. */
    public record Block(int chunkAddress, int chunkWord, int blockAddress, List<Integer> words) {
        public Block { words = List.copyOf(words); }
    }

    private final byte[] workRam;

    public Sonic2CompetitionPlaneDraw(byte[] workRam) {
        Objects.requireNonNull(workRam, "workRam");
        if (workRam.length != 0x10000) throw new IllegalArgumentException("Native work RAM must contain 65536 bytes");
        this.workRam = workRam.clone();
    }

    /**
     * GetBlock (docs/s2disasm/s2.asm:19802-19828) / GetAddressOfBlockInChunk:
     * 128x128 chunk lookup, then the selected 16x16 block. The ten-bit block
     * index can intentionally address the deformation spill after the $C00 words.
     */
    public Block getBlock(Layout layout, int absoluteX, int absoluteY) {
        Objects.requireNonNull(layout, "layout");
        int x = absoluteX & 0xFFFF, y = absoluteY & 0xFFFF;
        int layoutOffset = ((y << 1) & 0xF00) + ((x >>> 7) & 0x7F);
        int chunk = workRam[layout.address + layoutOffset] & 0xFF;
        int chunkAddress = (chunk << 7) + (y & 0x70) + ((x >>> 3) & 0xE);
        int chunkWord = word(chunkAddress);
        int blockAddress = 0x9000 + ((chunkWord & 0x3FF) << 3);
        int left = word(blockAddress), right = word(blockAddress + 2);
        // ProcessAndWriteBlock_DoubleResolution_Horizontal (docs/s2disasm/s2.asm:19648-19683)
        // takes ONLY the first longword. V-flip toggles 16-row hardware cells;
        // it never selects the ordinary block's lower two words.
        int flip = ((chunkWord & 0x400) != 0 ? 0x800 : 0)
                | ((chunkWord & 0x800) != 0 ? 0x1000 : 0);
        if ((chunkWord & 0x400) != 0) {
            int first = left; left = right; right = first;
        }
        return new Block(chunkAddress, chunkWord, blockAddress, List.of(left ^ flip, right ^ flip));
    }

    /** CalculateVRAMAddressOfBlockForPlayer1/2 (s2.asm:19834-19890), semantic cell position. */
    public static CellPosition cellPosition(PlaneId plane, int absoluteX, int absoluteY) {
        return new CellPosition(Objects.requireNonNull(plane), (absoluteX & 0x1F0) >>> 3,
                (absoluteY & 0x1F0) >>> 4);
    }
    public CellWrite blockWrite(PlaneId plane, Layout layout, int absoluteX, int absoluteY) {
        return new CellWrite(cellPosition(plane, absoluteX, absoluteY),
                getBlock(layout, absoluteX, absoluteY).words());
    }

    /**
     * DrawLevelTitleCard loc_15714/loc_15758 (docs/s2disasm/s2.asm:29127-29168): two full-width
     * foreground rows for P2 first, then P1, at an explicit title-card-owned offset.
     * Zero is the routine's no-write sentinel; this method does not move that owner.
     */
    public List<CellWrite> titleCardForegroundRows(CameraCopy player1, CameraCopy player2, int firstRelativeY) {
        Objects.requireNonNull(player1, "player1"); Objects.requireNonNull(player2, "player2");
        if ((firstRelativeY & 0xFFFF) == 0) return List.of();
        var writes = new ArrayList<CellWrite>(128);
        for (int slot = 1; slot >= 0; slot--) {
            CameraCopy camera = slot == 0 ? player1 : player2;
            for (int row = 0; row < 2; row++)
                row(writes, foregroundTable(slot), Layout.FOREGROUND, camera,
                        -16, firstRelativeY + row * 16, 32, false, false);
        }
        return List.copyOf(writes);
    }

    /**
     * Completed native title-card leave writes for the supplied unchanged camera words. Obj34_BackgroundOut
     * (docs/s2disasm/s2.asm:27587-27604) subtracts $20 from $F0 before publishing each offset:
     * $D0,$B0,$90,$70,$50,$30,$10,-$10. The next subtraction deletes the object.
     * This is an explicit load helper, never a draw-time full redraw. A live leave
     * with changing cameras calls {@link #titleCardForegroundRows} at each real boundary.
     */
    public List<CellWrite> initialForeground(CameraCopy player1, CameraCopy player2) {
        var writes = new ArrayList<CellWrite>(1024);
        for (int y = 0xD0; y >= -0x10; y -= 0x20)
            writes.addAll(titleCardForegroundRows(player1, player2, y));
        return List.copyOf(writes);
    }

    /**
     * DrawInitialBG (docs/s2disasm/s2.asm:19919-20024), FixBugs=0. EHZ's 16 camera-relative rows
     * start at -16; CNZ's shipped workaround starts at zero in BOTH acts. Only 2P
     * MCZ fills all 512x512 pixels, using absolute coordinates independent of camera.
     * There is one shared plane B; the dead P2-background initializer is not invoked.
     */
    public List<CellWrite> initialBackground(int romZone, CameraCopy background) {
        Objects.requireNonNull(background, "background");
        if (romZone != 0 && romZone != 11 && romZone != 12)
            throw new IllegalArgumentException("Not a native competition zone: " + romZone);
        var writes = new ArrayList<CellWrite>(romZone == 11 ? 1024 : 512);
        if (romZone == 11) {
            for (int y = 0; y < 512; y += 16)
                row(writes, BACKGROUND, Layout.BACKGROUND, background, 0, y, 32, true, true);
        } else {
            int first = romZone == 12 ? 0 : -16;
            for (int y = first; y < first + 256; y += 16)
                row(writes, BACKGROUND, Layout.BACKGROUND, background, 0, y, 32, false, false);
        }
        return List.copyOf(writes);
    }

    /** Draw_FG/Draw_FG_P2 and explicit P1 Screen_redraw_flag work (docs/s2disasm/s2.asm:18588-18703). */
    public List<CellWrite> foregroundUpdates(int slot, CameraCopy camera, int scrollFlags, boolean screenRedraw) {
        PlaneId table = foregroundTable(slot);
        Objects.requireNonNull(camera, "camera");
        if (slot == 1 && screenRedraw) throw new IllegalArgumentException("Screen_redraw_flag belongs to P1");
        var writes = new ArrayList<CellWrite>();
        if (screenRedraw) {
            for (int y = -16; y <= 224; y += 16)
                row(writes, table, Layout.FOREGROUND, camera, -16, y, 22, false, false);
        } else {
            edges(writes, table, Layout.FOREGROUND, camera, scrollFlags);
        }
        return List.copyOf(writes);
    }

    /**
     * Draw_BG1 / Draw_BG2 (docs/s2disasm/s2.asm:18708-18831) and Draw_BG3, for the three
     * competition zones. Copies/flags are supplied by their actual native owner.
     * CPZ's advanced camera-section path is deliberately outside this producer.
     */
    public List<CellWrite> backgroundUpdates(CameraCopy background, int flags,
            CameraCopy background2, int flags2, CameraCopy background3, int flags3) {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(background2, "background2"); Objects.requireNonNull(background3, "background3");
        var writes = new ArrayList<CellWrite>();
        edges(writes, BACKGROUND, Layout.BACKGROUND, background, flags);
        if ((flags & 0x10) != 0) row(writes, BACKGROUND, Layout.BACKGROUND, background, 0, -16, 32, true, false);
        if ((flags & 0x20) != 0) row(writes, BACKGROUND, Layout.BACKGROUND, background, 0, 224, 32, true, false);
        if ((flags & 0x40) != 0) row(writes, BACKGROUND, Layout.BACKGROUND, background, -16, -16, 32, false, false);
        if ((flags & 0x80) != 0) row(writes, BACKGROUND, Layout.BACKGROUND, background, -16, 224, 32, false, false);
        if ((flags2 & 1) != 0) column(writes, BACKGROUND, Layout.BACKGROUND, background2, -16, 112, 3);
        if ((flags2 & 2) != 0) column(writes, BACKGROUND, Layout.BACKGROUND, background2, 320, 112, 3);
        if ((flags3 & 1) != 0) column(writes, BACKGROUND, Layout.BACKGROUND, background3, -16, 64, 3);
        if ((flags3 & 2) != 0) column(writes, BACKGROUND, Layout.BACKGROUND, background3, 320, 64, 3);
        return List.copyOf(writes);
    }

    private void edges(List<CellWrite> writes, PlaneId table, Layout layout, CameraCopy camera, int flags) {
        if ((flags & 1) != 0) row(writes, table, layout, camera, -16, -16, 22, false, false);
        if ((flags & 2) != 0) row(writes, table, layout, camera, -16, 224, 22, false, false);
        if ((flags & 4) != 0) column(writes, table, layout, camera, -16, -16, 16);
        if ((flags & 8) != 0) column(writes, table, layout, camera, 320, -16, 16);
    }

    private void row(List<CellWrite> writes, PlaneId table, Layout layout, CameraCopy camera,
            int x, int y, int blocks, boolean absoluteX, boolean absoluteY) {
        int worldX = (x + (absoluteX ? 0 : camera.x())) & 0xFFFF;
        int worldY = (y + (absoluteY ? 0 : camera.y())) & 0xFFFF;
        for (int i = 0; i < blocks; i++) writes.add(blockWrite(table, layout, worldX + i * 16, worldY));
    }

    private void column(List<CellWrite> writes, PlaneId table, Layout layout, CameraCopy camera,
            int x, int y, int blocks) {
        for (int i = 0; i < blocks; i++) writes.add(blockWrite(table, layout, camera.x() + x, camera.y() + y + i * 16));
    }

    private int word(int address) {
        return (workRam[address] & 0xFF) << 8 | workRam[address + 1] & 0xFF;
    }

    private static PlaneId foregroundTable(int slot) {
        return switch (slot) {
            case 0 -> FOREGROUND_P1;
            case 1 -> FOREGROUND_P2;
            default -> throw new IllegalArgumentException("Native foreground slot must be 0 or 1");
        };
    }

}
