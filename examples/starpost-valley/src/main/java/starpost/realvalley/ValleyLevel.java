package starpost.realvalley;

import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.level.objects.ObjectSpawn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the valley's format-v2 level data: the re-encoded Sonic 1 terrain plus stock Sonic 3&amp;K objects
 * (the farm gate's Star Post, the totem ledge's spring and the loop's path swappers).
 */
public final class ValleyLevel {
    /** Sonic 3&amp;K stock object ids ({@code Sonic3kObjectIds}). */
    static final int PATH_SWAP = 0x02;
    static final int SPRING = 0x07;
    static final int STAR_POST = 0x34;
    /** Obj_Spring subtype: vertical, yellow (-$A00), as the scene's spring(10). */
    static final int SPRING_YELLOW_UP = 0x02;

    private ValleyLevel() {
    }

    /** Encodes {@code terrain} with the valley's layout. */
    public static EncodedValley encode(S1Terrain terrain) {
        return ValleyEncoder.encode(terrain, new ValleyEncoder.Spec(RealValley.blocks(), RealValley.SKY_ROWS,
                RealValley.LOOP_BLOCK, RealValley.LOOP_TWIN, RealValley.PLACEHOLDER_LINE,
                RealValley.PLACEHOLDER_COLOR));
    }

    /**
     * The level data for the act: the placeholder's identity (runtime zone index, object zone set)
     * with the valley's terrain, palette and objects.
     */
    public static ModZoneLevelData build(ModZoneLevelData placeholder, S1Terrain terrain) {
        EncodedValley valley = encode(terrain);
        List<ModPaletteClaim> claims = new ArrayList<>();
        for (int[] claim : valley.claims()) {
            claims.add(new ModPaletteClaim(claim[0], claim[1], claim[2]));
        }
        int widthPixels = valley.width() * 128;
        int heightPixels = valley.height() * 128;
        return new ModZoneLevelData(2, placeholder.zoneIndex(), 8, valley.width(), valley.height(),
                0, widthPixels - 320, 0, heightPixels - 224,
                valley.patterns(), valley.chunks(), valley.blocks(), valley.foreground(), valley.background(),
                valley.heights(), valley.widths(), valley.angles(), valley.primary(), valley.secondary(),
                placeholder.paletteLines(), placeholder.hostMetadata(), claims, objects(terrain), List.of(),
                valley.patternCount(), valley.chunkCount(), valley.blockCount());
    }

    /** The stock objects, in x order. */
    public static List<ObjectSpawn> objects(S1Terrain terrain) {
        List<ObjectSpawn> objects = new ArrayList<>();
        int gate = RealValley.FARM_GATE_X;
        objects.add(spawn(objects.size(), gate, floorAt(terrain, gate) - 0x20, STAR_POST, 1));
        int springX = RealValley.columnOf(RealValley.SPRING_BLOCK) * RealValley.S1_BLOCK + 20;
        objects.add(spawn(objects.size(), springX, floorAt(terrain, springX) - 8, SPRING, SPRING_YELLOW_UP));
        objects.addAll(loopSwappers(objects.size()));
        objects.sort(Comparator.comparingInt(ObjectSpawn::x));
        return objects;
    }

    /**
     * Path swappers reproducing Sonic 1's {@code Sonic_Loops} planes for the loop block: within
     * $2C of the block's west edge the high plane (here the primary path); across the loop's top,
     * westward travel takes the low plane (secondary: the twin block's collision, where the west arc
     * is solid and the east arc open) and eastward travel the high plane; leaving the block east
     * restores the high plane. All keep the player's low sprite priority, as Sonic 1 never raises it,
     * so the loop's own high-priority tiles still cover him.
     */
    static List<ObjectSpawn> loopSwappers(int firstId) {
        int x0 = RealValley.columnOf(RealValley.LOOP_BLOCK) * RealValley.S1_BLOCK;
        int top = RealValley.terrainTop();
        List<ObjectSpawn> swaps = new ArrayList<>();
        // Subtype bits: 0-1 half span ($20 << n), 3 path east of the line, 4 path west of it.
        swaps.add(spawn(firstId, x0 + 0x2C, top + 0x80, PATH_SWAP, 0x02));          // both sides primary
        swaps.add(spawn(firstId + 1, x0 + 0x80, top + 0x40, PATH_SWAP, 0x11));      // top half: west secondary
        swaps.add(spawn(firstId + 2, x0 + 0x100, top + 0x80, PATH_SWAP, 0x12));     // east primary, west secondary
        return swaps;
    }

    private static ObjectSpawn spawn(int id, int x, int y, int objectId, int subtype) {
        return new ObjectSpawn(x, y, objectId, subtype, 0, false, y, id + 1);
    }

    /**
     * The first floor pixel at or below the terrain's top at world {@code x} on the primary path, read
     * from the Sonic 1 collision (heights rise from the cell's bottom).
     */
    public static int floorAt(S1Terrain terrain, int x) {
        int column = Math.floorDiv(x, RealValley.S1_BLOCK);
        int[] blocks = RealValley.blocks();
        if (column < 0 || column >= blocks.length) {
            return RealValley.terrainTop() + RealValley.S1_BLOCK;
        }
        int[] words = terrain.blocks()[blocks[column]];
        int inBlock = Math.floorMod(x, RealValley.S1_BLOCK);
        for (int cy = 0; cy < 16; cy++) {
            int word = words[cy * 16 + inBlock / 16];
            if ((word & 0x1000) == 0 || (word & 0x0800) != 0) {
                continue;
            }
            int profile = terrain.collision()[word & 0x3FF];
            if (profile == 0) {
                continue;
            }
            int px = inBlock % 16;
            int height = terrain.heights()[profile * 16 + ((word & 0x0400) != 0 ? 15 - px : px)];
            if (height > 0) {
                return RealValley.terrainTop() + cy * 16 + 16 - Math.min(16, height);
            }
        }
        return RealValley.terrainTop() + RealValley.S1_BLOCK;
    }
}
