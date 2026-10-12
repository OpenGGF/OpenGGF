package starpost.ruins;

import java.util.ArrayList;
import java.util.List;
import starpost.valley.Ground;

/**
 * One room of the Ruins: a small grid of a zone's level blocks with their own collision, a water
 * line in the Labyrinth, and what is placed in it (rings, rocks, badniks, springs, spikes, air
 * bubbles, monitors). Engine-free: it is the {@link Ground} for the
 * traversal check that places the exit ({@link Reach}).
 *
 * <p>Coordinates are chamber pixels from the top-left corner. Above the top is a ceiling; left
 * and right the runner is held inside; below the bottom is a shaft down to the next chamber.
 * Top-solid floors (platforms) hold from above only: walls and ceilings are fully solid pixels.
 */
public final class Chamber implements Ground {
    public static final int NO_WATER = Integer.MAX_VALUE;
    public static final byte EMPTY = 0;
    public static final byte TOP_SOLID = 1;
    public static final byte SOLID = 2;

    // Thing types.
    public static final int RING = 0;
    public static final int ROCK = 1;
    public static final int SPRING = 2;
    public static final int SPIKES = 3;
    public static final int BADNIK = 4;
    public static final int BUBBLES = 5;
    public static final int MONITOR = 6;

    /** S3K's yellow spring launches at $A00 (10 pixels per frame). */
    public static final float SPRING_POWER = 10f;
    /** Half the width of the spikes (Map_Spike's upright row of three is 32 pixels wide). */
    public static final int SPIKES_HALF = 16;

    /**
     * Something placed in the chamber: {@code x} and {@code y} are the floor point it stands on
     * (rings and flyers: their centre). {@code param} is the badnik kind, the monitor's content
     * index, or 0.
     */
    public record Thing(int type, int x, int y, int param) {
    }

    /** A rectangle of lava (Marble Zone): touching it hurts unless lava is no threat. */
    public record Lava(int x, int y, int w, int h) {
        public boolean contains(float px, float py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public final int number;
    public final int band;
    public final int zone;
    public final int act;
    public final Kit kit;
    public final int cols;
    public final int rows;
    public final int size;
    public final int width;
    public final int height;
    /** Block ids, {@code cells[column + row * cols]}; 0 is open air. */
    public final int[] cells;
    public int waterY = NO_WATER;
    public final List<Thing> things = new ArrayList<>();
    public final List<Lava> lava = new ArrayList<>();
    public int entryX;
    public int entryY;
    /** The shaft down; {@code exitX < 0} when the chamber has none (the last one). */
    public int exitX = -1;
    public int exitY;
    /** The Star Post elevator (landmarks only); {@code elevatorX < 0} otherwise. */
    public int elevatorX = -1;
    public int elevatorY;
    /** A landmark's name for the title card, or null. */
    public String landmark;
    /** What item monitors hold ({@link Thing#param} indexes it; -1 is ten rings). */
    public final List<String> prizes = new ArrayList<>();
    private final short[][] floorCache;

    public Chamber(int number, int zone, int act, Kit kit, int cols, int rows, int[] cells) {
        this.number = number;
        this.band = RuinsRules.band(number);
        this.zone = zone;
        this.act = act;
        this.kit = kit;
        this.cols = cols;
        this.rows = rows;
        this.size = kit.size();
        this.width = cols * size;
        this.height = rows * size;
        this.cells = cells;
        this.floorCache = new short[width][];
    }

    public int block(int column, int row) {
        return column < 0 || row < 0 || column >= cols || row >= rows ? 0 : cells[column + row * cols];
    }

    /** The collision at a pixel; outside the chamber, open air (the edges are handled elsewhere). */
    public byte type(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return EMPTY;
        }
        int id = cells[x / size + y / size * cols];
        if (id <= 0) {
            return EMPTY;
        }
        byte[] mask = kit.solidity(id);
        return mask == null ? EMPTY : mask[(y % size) * size + x % size];
    }

    /** Whether the chamber's picture is drawn at a pixel (false where the background shows). */
    public boolean opaque(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        int id = cells[x / size + y / size * cols];
        return id > 0 && kit.opaque(id, x % size, y % size);
    }

    /**
     * Marble Zone's lava: a floor with no picture. The kit draws animated tiles at their first
     * frame, which leaves the lava's own tiles (Art_MzLava1/2, animated by AniArt_MZ) blank, while
     * its collision stays: Sonic 1 stands Sonic on the lava and a Lava Tag object hurts him. Each
     * run of invisible floor at least 16 pixels wide becomes a {@link Lava} rectangle from just
     * above the surface down 24 pixels.
     */
    public void findLava() {
        int runX = -1, runY = -1;
        for (int x = 0; x <= width; x++) {
            int surface = -1;
            if (x < width) {
                // From row 40: a window cut through the bottom of a pool above leaves its floor
                // at the very top edge, where nothing can stand.
                for (int y = 40; y < height - 2; y++) {
                    if (floor(x, y) && !floor(x, y - 1) && !opaque(x, y) && !opaque(x, y + 2) && !opaque(x, y - 2)) {
                        surface = y;
                        break;
                    }
                }
            }
            if (runX >= 0 && (surface < 0 || Math.abs(surface - runY) > 2)) {
                if (x - runX >= 16) {
                    // The pool reaches down to whatever floor is under it (the bed of a lava lake).
                    int bed = height;
                    for (int bx = runX; bx < x; bx += 4) {
                        int under = runY + 2;
                        while (under < height && floor(bx, under)) {
                            under++;
                        }
                        bed = Math.min(bed, Math.min(height, floorBelow(bx, under)));
                    }
                    lava.add(new Lava(runX, runY - 10, x - runX, Math.max(26, bed - runY + 10)));
                }
                runX = -1;
            }
            if (surface >= 0 && runX < 0) {
                runX = x;
                runY = surface;
            }
        }
    }

    /** Walls: only fully solid pixels stop the runner from the side. */
    @Override
    public boolean solid(int x, int y) {
        return type(x, y) == SOLID;
    }

    /** Anything that holds the feet: solid ground or a top-solid platform. */
    public boolean floor(int x, int y) {
        return type(x, y) != EMPTY;
    }

    @Override
    public boolean ceiling(int x, int y) {
        return y < 0 || type(x, y) == SOLID;
    }

    @Override
    public int floorBelow(int x, int fromY) {
        if (x < 0 || x >= width) {
            return height + 512;
        }
        int from = Math.max(0, fromY);
        if (from >= height) {
            return height + 512;
        }
        short[] column = floorCache[x];
        if (column == null) {
            column = new short[height + 1];
            int next = height + 512;
            column[height] = (short) Math.min(Short.MAX_VALUE, next);
            for (int y = height - 1; y >= 0; y--) {
                if (floor(x, y)) {
                    next = y;
                }
                column[y] = (short) Math.min(Short.MAX_VALUE, next);
            }
            floorCache[x] = column;
        }
        return column[from];
    }

    @Override
    public int left() {
        return 0;
    }

    @Override
    public int right() {
        return width;
    }

    /** Whether the feet at this point are under the water line (Sonic 1 compares his centre). */
    public boolean underwater(float x, float feetY, int height) {
        return waterY != NO_WATER && feetY - height / 2f > waterY;
    }

    /** Whether a body around these feet touches lava or spikes. */
    public boolean hazard(float x, float feetY) {
        for (Lava l : lava) {
            if (l.contains(x, feetY - 4) || l.contains(x, feetY)) {
                return true;
            }
        }
        for (Thing t : things) {
            if (t.type() == SPIKES && Math.abs(x - t.x()) < SPIKES_HALF + 6 && feetY > t.y() - 18 && feetY <= t.y() + 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the chamber does to the runner after its step: water physics at the line and springs
     * underfoot. Returns 1 when a spring fired, 2 when Sonic splashed through the surface, else 0.
     * Play and the traversal check share it, so the check sees what the player will.
     */
    public int afterStep(GenerationRunner r) {
        int event = 0;
        if (waterY != NO_WATER && r.setUnderwater(underwater(r.x, r.y, r.height()))) {
            event = 2;
        }
        for (Thing t : things) {
            if (t.type() == SPRING && Math.abs(r.x - t.x()) < 12 && r.y >= t.y() - 2 && r.y <= t.y() + 4
                    && (r.onGround || r.ySpeed > 0)) {
                r.y = t.y();
                r.spring(SPRING_POWER);
                return 1;
            }
        }
        return event;
    }

    public void add(int type, int x, int y, int param) {
        things.add(new Thing(type, x, y, param));
    }

    public boolean hasThingNear(int type, int x, int y, int distance) {
        for (Thing t : things) {
            if ((type < 0 || t.type() == type) && Math.abs(t.x() - x) < distance && Math.abs(t.y() - y) < distance) {
                return true;
            }
        }
        return false;
    }
}
