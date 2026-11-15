package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.program.CdProgramScreenRenderer;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * The picture state of a non-level CD program, in the shape {@link CdProgramScreenRenderer.Frame} draws: a tile store
 * indexed by tile, the cells of plane A and plane B, 64 palette entries, each plane's horizontal and vertical scroll,
 * display on or off, the backdrop entry and the screen width. Programs change it through typed operations only; where
 * a program's native code names a video-memory address or a register value, the call site translates it to these
 * operations and cites it. Sprites are not part of it (none of the programs using it draws any).
 */
public final class CdProgramScreenState {
    public enum Plane { A, B }

    public static final int TILES = 0x800, TILE_BYTES = 32;

    private final byte[] tiles = new byte[TILES * TILE_BYTES];
    private final BitSet dirtyTiles = new BitSet(TILES);
    private int width = 320;
    private int columns = 64, rows = 32;
    private int[] planeA = new int[columns * rows], planeB = new int[columns * rows];
    private final int[] palette = new int[64];
    private int hscrollA, hscrollB, vscrollA, vscrollB;
    private boolean display;
    private int backdrop;

    /** Screen width (320 or 256) and plane size in cells; the planes are cleared. */
    public void setLayout(int screenWidth, int planeColumns, int planeRows) {
        if (screenWidth != 320 && screenWidth != 256) throw new IllegalArgumentException("Screen width " + screenWidth);
        width = screenWidth;
        columns = planeColumns;
        rows = planeRows;
        planeA = new int[columns * rows];
        planeB = new int[columns * rows];
    }

    /** Every tile and every cell of both planes to zero (a program's clear of all video memory). */
    public void clearTilesAndPlanes() {
        Arrays.fill(tiles, (byte) 0);
        dirtyTiles.set(0, TILES);
        Arrays.fill(planeA, 0);
        Arrays.fill(planeB, 0);
    }

    /** {@code bytes} bytes of 4bpp pattern data from {@code source} into the store from tile {@code firstTile}. */
    public void writeTiles(int firstTile, byte[] source, int offset, int bytes) {
        int at = firstTile * TILE_BYTES;
        for (int i = 0; i < bytes; i++) {
            int s = offset + i;
            tiles[(at + i) % tiles.length] = s < source.length ? source[s] : 0;
        }
        dirtyTiles.set(firstTile, Math.min(TILES, firstTile + (bytes + TILE_BYTES - 1) / TILE_BYTES));
    }

    public void clearTiles(int firstTile, int count) {
        Arrays.fill(tiles, firstTile * TILE_BYTES, (firstTile + count) * TILE_BYTES, (byte) 0);
        dirtyTiles.set(firstTile, firstTile + count);
    }

    public void setPlaneCell(Plane plane, int column, int row, int word) {
        cells(plane)[(row % rows) * columns + (column % columns)] = word & 0xFFFF;
    }

    public int planeCell(Plane plane, int column, int row) {
        return cells(plane)[(row % rows) * columns + (column % columns)];
    }

    /** Every cell of a plane to one word. */
    public void fillPlane(Plane plane, int word) { Arrays.fill(cells(plane), word & 0xFFFF); }

    private int[] cells(Plane plane) { return plane == Plane.A ? planeA : planeB; }

    public void setPalette(int index, int color) { palette[index & 63] = color & 0x0EEE; }

    public int palette(int index) { return palette[index & 63]; }

    public void setHScroll(Plane plane, int value) {
        if (plane == Plane.A) hscrollA = value & 0xFFFF; else hscrollB = value & 0xFFFF;
    }

    public void setVScroll(Plane plane, int value) {
        if (plane == Plane.A) vscrollA = value & 0xFFFF; else vscrollB = value & 0xFFFF;
    }

    public int hScroll(Plane plane) { return plane == Plane.A ? hscrollA : hscrollB; }

    public int vScroll(Plane plane) { return plane == Plane.A ? vscrollA : vscrollB; }

    public void setDisplay(boolean on) { display = on; }

    public boolean display() { return display; }

    public void setBackdrop(int entry) { backdrop = entry & 63; }

    public int backdrop() { return backdrop; }

    public int width() { return width; }

    public int planeColumns() { return columns; }

    public int planeRows() { return rows; }

    /** The tile store (tile {@code t} at {@code t * 32}); callers read it. */
    public byte[] tiles() { return tiles; }

    public int[] paletteEntries() { return palette.clone(); }

    /** An independent copy, for comparisons at two clocks. */
    public CdProgramScreenState copy() {
        CdProgramScreenState c = new CdProgramScreenState();
        System.arraycopy(tiles, 0, c.tiles, 0, tiles.length);
        c.setLayout(width, columns, rows);
        System.arraycopy(planeA, 0, c.planeA, 0, planeA.length);
        System.arraycopy(planeB, 0, c.planeB, 0, planeB.length);
        System.arraycopy(palette, 0, c.palette, 0, palette.length);
        c.hscrollA = hscrollA; c.hscrollB = hscrollB; c.vscrollA = vscrollA; c.vscrollB = vscrollB;
        c.display = display; c.backdrop = backdrop;
        c.dirtyTiles.set(0, TILES);
        return c;
    }

    /** One plane's cells and vertical scroll from {@code other} (a layer that moves on its own clock). */
    public void takePlane(Plane plane, CdProgramScreenState other) {
        Objects.requireNonNull(other, "other");
        System.arraycopy(other.cells(plane), 0, cells(plane), 0, cells(plane).length);
        setVScroll(plane, other.vScroll(plane));
    }

    /**
     * The picture: each plane folded to the 64 x 32 cells its full-screen scroll shows (columns by the horizontal
     * scroll, rows by the vertical scroll), so planes of any size draw through the shared renderer.
     */
    public CdProgramScreenRenderer.Frame frame() {
        int[] a = new int[64 * 32], b = new int[64 * 32];
        int foldA = fold(planeA, hscrollA, vscrollA, a), foldB = fold(planeB, hscrollB, vscrollB, b);
        return new CdProgramScreenRenderer.Frame(tiles, dirtyTiles, a, b, palette.clone(),
                line -> foldA, line -> foldB, List.of(), width, display, vscrollA & 7, vscrollB & 7);
    }

    /**
     * Copy the plane's visible cells into a 64 x 32 view and return the horizontal scroll that places the view.
     * Screen x shows plane x {@code (x - scroll) mod width}; screen line y shows plane line {@code (y + vscroll) mod
     * height}. The view starts at the first column and row on screen; the renderer applies {@code vscroll & 7}.
     */
    private int fold(int[] cells, int scroll, int vscroll, int[] view) {
        int start = (-scroll) & (columns * 8 - 1);
        int column0 = start >> 3;
        int row0 = (vscroll & (rows * 8 - 1)) >> 3;
        for (int row = 0; row < 32; row++) {
            int sourceRow = (row0 + row) % rows;
            for (int column = 0; column < 64; column++) {
                view[row * 64 + column] = cells[sourceRow * columns + (column0 + column) % columns];
            }
        }
        return (-(start & 7)) & 0x1FF;
    }
}
