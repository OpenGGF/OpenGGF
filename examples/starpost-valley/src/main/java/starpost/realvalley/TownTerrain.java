package starpost.realvalley;

import java.util.List;
import java.util.stream.IntStream;
import com.openggf.level.objects.ObjectSpawn;
import starpost.realtown.TownContent;
import starpost.realtown.TownLayout;
import starpost.valley.Ground;
import starpost.valley.Valley;

/** The single mapping seam between town destinations and the real valley's geometry. */
public final class TownTerrain {
    private TownTerrain() {}

    /** Scene coordinates share the block columns; the act adds its empty sky rows. */
    public static TownLayout layout(Valley valley) {
        Ground ground = new Ground() {
            public int left() { return valley.left(); }
            public int originY() { return RealValley.terrainTop(); }
            public int right() { return RealValley.blocks().length * RealValley.S1_BLOCK; }
            public boolean solid(int x, int y) { return valley.solid(x, y - RealValley.terrainTop()); }
            public int floorBelow(int x, int fromY) {
                return valley.floorBelow(x, Math.max(0, fromY - RealValley.terrainTop())) + RealValley.terrainTop();
            }
        };
        return new TownLayout(ground,
            RealValley.columnOf(RealValley.SPRING_BLOCK) * RealValley.S1_BLOCK + 20,
            RealValley.columnOf(RealValley.LOOP_BLOCK) * RealValley.S1_BLOCK, valley.places);
    }

    /** Native camera-local admission must cover the gate and every menu-return position. */
    public static List<ObjectSpawn> admission(S1Terrain terrain) {
        return IntStream.range(0, RealValley.blocks().length).mapToObj(i -> {
            int x = RealValley.FARM_GATE_X + i * RealValley.S1_BLOCK;
            return TownContent.spawn(TownContent.CONTROLLER, i, x, ValleyLevel.floorAt(terrain, x));
        }).toList();
    }

    public static int entryX() { return RealValley.FARM_GATE_X + 40; }
    public static int entryY(TownLayout layout, String farmer) {
        // S3K standing radii: Tails is four pixels shorter than Sonic and Knuckles.
        return layout.ground.floorBelow(entryX(), 0) - (farmer.equals("tails") ? 15 : 19) - 1;
    }
}
