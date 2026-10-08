package eggsky.world;

import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import eggsky.core.Recolor;
import java.util.HashMap;

/**
 * A planet's surface: a loop of layout blocks remixed from one act ({@link TerrainGen}), with
 * collision read from the blocks' masks and art recoloured into the planet's palette. X wraps
 * around the planet; above the top row is open sky, below the bottom row is bedrock. Cells the
 * player has carved keep their own copy of mask and art.
 */
public final class Terrain {
    private final SceneLevelKit kit;
    private final int size;
    private final int columns;
    private final int rows;
    private final int[] grid;
    private final byte[][] masks;
    private final SceneImage[] images;
    private final Recolor recolor;
    /** Carved cells (column + row * columns) with their own mask and art. */
    private final HashMap<Integer, byte[]> carvedMasks = new HashMap<>();
    private final HashMap<Integer, int[]> carvedPixels = new HashMap<>();
    private final HashMap<Integer, SceneImage> carvedImages = new HashMap<>();
    /** Per wrapped pixel column sample (every {@link #STEP} px): the top floor row, or -1. */
    private final int[] topFloor;
    public static final int STEP = 4;

    public Terrain(SceneLevelKit kit, TerrainGen.Layout layout, Recolor recolor) {
        this.kit = kit;
        this.size = kit.blockSize();
        this.columns = layout.columns();
        this.rows = layout.rows();
        this.grid = layout.cells().clone();
        this.masks = new byte[Math.max(1, kit.blockCount())][];
        this.images = new SceneImage[masks.length];
        this.recolor = recolor;
        this.topFloor = new int[width() / STEP];
        for (int i = 0; i < topFloor.length; i++) {
            topFloor[i] = scanTopFloor(i * STEP, 24);
        }
    }

    public int blockSize() {
        return size;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /** The planet's circumference in pixels. */
    public int width() {
        return columns * size;
    }

    public int height() {
        return rows * size;
    }

    public int wrapX(int x) {
        return Math.floorMod(x, width());
    }

    public float wrapX(float x) {
        float w = width();
        float r = x % w;
        return r < 0 ? r + w : r;
    }

    /** The shortest signed horizontal distance from {@code from} to {@code to} around the planet. */
    public float dx(float from, float to) {
        float w = width();
        float d = (to - from) % w;
        if (d > w / 2) {
            d -= w;
        } else if (d < -w / 2) {
            d += w;
        }
        return d;
    }

    public int block(int column, int row) {
        if (row < 0 || row >= rows) {
            return 0;
        }
        return grid[Math.floorMod(column, columns) + row * columns];
    }

    private byte[] mask(int id) {
        if (id <= 0 || id >= masks.length) {
            return null;
        }
        if (masks[id] == null) {
            masks[id] = kit.blockSolidity(id);
        }
        return masks[id];
    }

    /** {@link SceneLevelKit#EMPTY}, {@code TOP_SOLID} or {@code SOLID} at a world pixel. */
    public byte solidity(int x, int y) {
        if (y < 0) {
            return SceneLevelKit.EMPTY;
        }
        if (y >= height()) {
            return SceneLevelKit.SOLID;
        }
        int wx = Math.floorMod(x, width());
        int column = wx / size;
        int row = y / size;
        int cell = column + row * columns;
        byte[] m = carvedMasks.isEmpty() ? null : carvedMasks.get(cell);
        if (m == null) {
            m = mask(grid[cell]);
            if (m == null) {
                return SceneLevelKit.EMPTY;
            }
        }
        return m[(y % size) * size + wx % size];
    }

    /** Solid from every side (walls, ceilings, floors). */
    public boolean solid(int x, int y) {
        return solidity(x, y) == SceneLevelKit.SOLID;
    }

    /** Anything that holds weight from above. */
    public boolean floor(int x, int y) {
        return solidity(x, y) != SceneLevelKit.EMPTY;
    }

    /** The first floor row at or below {@code y} within {@code range} pixels, or -1. */
    public int groundBelow(int x, int y, int range) {
        for (int yy = Math.max(0, y); yy < y + range; yy++) {
            if (floor(x, yy)) {
                return yy;
            }
        }
        return -1;
    }

    /** The highest floor with {@code headroom} clear rows above it in this column, or -1. */
    public int scanTopFloor(int x, int headroom) {
        int clear = headroom;
        for (int y = 0; y < height(); y += 2) {
            if (floor(x, y)) {
                if (clear >= headroom) {
                    // Refine to the exact top row.
                    int top = y;
                    while (top > 0 && floor(x, top - 1)) {
                        top--;
                    }
                    return top;
                }
                clear = 0;
            } else {
                clear += 2;
            }
        }
        return -1;
    }

    /** The precomputed top floor near {@code x}, or -1 where the column has none. */
    public int topFloor(float x) {
        int i = Math.floorMod((int) x / STEP, topFloor.length);
        return topFloor[i];
    }

    /**
     * Every floor in a column with {@code headroom} clear rows above it, top to bottom, as a
     * packed list (up to 16).
     */
    public int[] floors(int x, int headroom) {
        int[] found = new int[16];
        int n = 0;
        int clear = headroom;
        boolean inSolid = false;
        for (int y = 0; y < height() && n < found.length; y++) {
            boolean f = floor(x, y);
            if (f && !inSolid && clear >= headroom) {
                found[n++] = y;
            }
            if (f) {
                clear = 0;
            } else {
                clear++;
            }
            inSolid = f;
        }
        return java.util.Arrays.copyOf(found, n);
    }

    /** The block's planet-coloured art (cached); null for open air. */
    public SceneImage image(int column, int row) {
        if (row < 0 || row >= rows) {
            return null;
        }
        int cell = Math.floorMod(column, columns) + row * columns;
        SceneImage carved = carvedImages.isEmpty() ? null : carvedImages.get(cell);
        if (carved != null) {
            return carved;
        }
        int id = grid[cell];
        if (id <= 0 || id >= images.length) {
            return null;
        }
        if (images[id] == null) {
            images[id] = recolor.apply(kit.blockImage(id));
        }
        return images[id];
    }

    /**
     * Carves a disc of terrain (the terrain manipulator): its collision becomes empty and its
     * art transparent, edged with a darker rim. Returns how many solid pixels were removed.
     */
    public int carve(int cx, int cy, int radius) {
        int removed = 0;
        int r2 = radius * radius;
        int rim2 = (radius + 2) * (radius + 2);
        for (int y = cy - radius - 2; y <= cy + radius + 2; y++) {
            if (y < 0 || y >= height()) {
                continue;
            }
            for (int x = cx - radius - 2; x <= cx + radius + 2; x++) {
                int d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                if (d2 > rim2) {
                    continue;
                }
                int wx = Math.floorMod(x, width());
                int cell = wx / size + (y / size) * columns;
                int index = (y % size) * size + wx % size;
                if (d2 <= r2) {
                    byte[] m = writableMask(cell);
                    if (m[index] != SceneLevelKit.EMPTY) {
                        removed++;
                    }
                    m[index] = SceneLevelKit.EMPTY;
                    writablePixels(cell)[index] = 0;
                } else {
                    int[] px = carvedPixels.get(cell);
                    if (px != null && px[index] != 0) {
                        px[index] = eggsky.core.Colour.scale(px[index], 0.6f);
                    }
                }
            }
        }
        // Rebuild the art of every touched cell.
        for (int y = cy - radius - 2; y <= cy + radius + 2; y += size / 2) {
            for (int x = cx - radius - 2; x <= cx + radius + 2 + size / 2; x += size / 2) {
                if (y < 0 || y >= height()) {
                    continue;
                }
                int cell = Math.floorMod(x, width()) / size + (Math.min(height() - 1, y) / size) * columns;
                int[] px = carvedPixels.get(cell);
                if (px != null) {
                    carvedImages.put(cell, new SceneImage(size, size, px));
                }
            }
        }
        return removed;
    }

    private byte[] writableMask(int cell) {
        byte[] m = carvedMasks.get(cell);
        if (m == null) {
            byte[] base = mask(grid[cell]);
            m = base == null ? new byte[size * size] : base.clone();
            carvedMasks.put(cell, m);
        }
        return m;
    }

    private int[] writablePixels(int cell) {
        int[] px = carvedPixels.get(cell);
        if (px == null) {
            int id = grid[cell];
            SceneImage base = id > 0 && id < images.length ? image(cell % columns, cell / columns) : null;
            px = base == null ? new int[size * size] : base.pixels();
            carvedPixels.put(cell, px);
        }
        return px;
    }

    /** How many cells have been carved (saved per planet). */
    public int carvedCells() {
        return carvedMasks.size();
    }
}
