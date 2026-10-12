package starpost.valley;

import java.util.ArrayList;
import java.util.List;
import starpost.art.Art;

/**
 * The side-view valley east of the farm, as a row of Green Hill act 1 blocks with their own
 * collision (floors at the ROM's row 192): the farm gate by the waterfall, the town on four flat
 * blocks, the totem ledge, the loop, and the slope up to the meadow plateau.
 */
public final class Valley implements Ground {
    /** A doorway or spot the player can use by pressing up (or walking past, for gates). */
    public record Place(String id, int x, int halfWidth, String label) {
    }

    public final int[] blocks = {13, 45, 60, 60, 60, 60, 45, 3, 45, 53, 38, 1, 16};
    public final List<Place> places = new ArrayList<>();
    /** The loop (block 53) and the spring at the foot of the totem ledge (block 3). */
    public final int loopX;
    public final int springX;
    private final Art art;

    public Valley(Art art) {
        this.art = art;
        loopX = indexOf(53) * Art.BLOCK;
        springX = indexOf(3) * Art.BLOCK + 20;
        places.add(new Place("farm_gate", 150, 24, "TO THE FARM"));
        places.add(new Place("seed_stall", 2 * Art.BLOCK + 112, 20, "DANDEL'S SEEDS"));
        places.add(new Place("inn", 3 * Art.BLOCK + 128, 16, "LAMPPOST INN"));
        places.add(new Place("workshop", 4 * Art.BLOCK + 128, 16, "TAILS' WORKSHOP"));
        places.add(new Place("museum", 4 * Art.BLOCK + 259, 14, "WORKSHOP MUSEUM"));   // starpost.museum's annex
        places.add(new Place("robomart", 5 * Art.BLOCK + 128, 16, "ROBOMART"));
        places.add(new Place("ruins", 11 * Art.BLOCK + 64, 18, "MARBLE RUINS"));
        places.add(new Place("capsule", 12 * Art.BLOCK + 120, 28, "THE GREAT CAPSULE"));
        places.add(new Place("lake", 12 * Art.BLOCK + 228, 20, "WATERFALL LAKE"));
    }

    private int indexOf(int block) {
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == block) {
                return i;
            }
        }
        return 0;
    }

    public int width() {
        return blocks.length * Art.BLOCK;
    }

    public int blockAt(int x) {
        int column = Math.floorDiv(x, Art.BLOCK);
        return column < 0 || column >= blocks.length ? 0 : blocks[column];
    }

    @Override
    public boolean solid(int x, int y) {
        int block = blockAt(x);
        return block > 0 && art.solid(block, Math.floorMod(x, Art.BLOCK), y);
    }

    @Override
    public int floorBelow(int x, int fromY) {
        for (int y = Math.max(0, fromY); y < Art.BLOCK; y++) {
            if (solid(x, y)) {
                return y;
            }
        }
        return Art.BLOCK * 2;
    }

    @Override
    public int left() {
        return 0;
    }

    @Override
    public int right() {
        return width();
    }

    /** The place the farmer is standing at, or null. */
    public Place placeAt(float x) {
        for (Place place : places) {
            if (Math.abs(x - place.x()) <= place.halfWidth()) {
                return place;
            }
        }
        return null;
    }
}
