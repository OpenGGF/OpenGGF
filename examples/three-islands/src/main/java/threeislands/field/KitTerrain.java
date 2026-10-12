package threeislands.field;

import com.openggf.mods.scene.SceneLevelKit;
import java.util.HashMap;
import java.util.Map;

/** A level kit's primary-path collision as {@link Terrain}; block masks are cached on first use. */
public final class KitTerrain implements Terrain {
    private final SceneLevelKit kit;
    private final int size;
    private final int[] area;
    private final Map<Integer, byte[]> masks = new HashMap<>();

    public KitTerrain(SceneLevelKit kit) {
        this.kit = kit;
        this.size = kit.blockSize();
        this.area = extent(kit);
    }

    /**
     * The playable area's horizontal span over the layout's full height. The kit's playable area
     * is the act's starting camera boundary; several acts (Sonic 1's Marble Zone, for one) lower
     * it with level events as the player goes deeper, so it would cut the route off at the top.
     */
    public static int[] extent(SceneLevelKit kit) {
        int[] area = kit.playableArea();
        return new int[] {area[0], 0, area[2], kit.rows() * kit.blockSize()};
    }

    @Override
    public boolean solid(int x, int y) {
        if (x < 0 || y < 0) return false;
        int block = kit.block(x / size, y / size);
        if (block == 0) return false;
        byte[] mask = masks.computeIfAbsent(block, kit::blockSolidity);
        return mask[(y % size) * size + (x % size)] != SceneLevelKit.EMPTY;
    }

    @Override
    public int[] area() {
        return area.clone();
    }
}
