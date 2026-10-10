package threeislands.field;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ROM masonry assembled around the same room boundaries used by movement and gates. */
public final class DungeonArt {
    private final SceneImage[] floor, wall;
    private final SceneImage trim;
    private final int light;

    public DungeonArt(SceneLevelKit kit, Dungeon dungeon) {
        List<SceneImage> tiles = textures(kit);
        if (tiles.isEmpty()) throw new IllegalArgumentException("Interior kit has no opaque terrain art");
        SceneImage ground = tiles.get(Math.min(tiles.size() - 1, 3));
        SceneImage masonry = tiles.get(0);
        SceneImage border = tiles.get(Math.min(tiles.size() - 1, 7));
        // Curated decoded-kit fragments, inspected in the native renderer. Keep the whole
        // 32-pixel masonry repeat so alternating brick courses and rock faces remain intact.
        if (dungeon.zone().game.equals("s1") && dungeon.artZone() == 1) {
            ground = crop(kit.blockImage(4), 0, 0, 32);
            masonry = ground;
        } else if (dungeon.zone().game.equals("s2") && dungeon.artZone() == 5) {
            ground = crop(kit.blockImage(1), 32, 64, 32);
            masonry = crop(kit.blockImage(12), 0, 16, 32);
        } else if (dungeon.zone().game.equals("s3k") && dungeon.artZone() == 9) {
            ground = crop(kit.blockImage(4), 64, 32, 32);
            masonry = crop(kit.blockImage(2), 64, 64, 32);
        } else if (dungeon.zone().game.equals("s3k") && dungeon.artZone() == 1) {
            ground = crop(kit.blockImage(3), 0, 32, 32);
            masonry = crop(kit.blockImage(6), 16, 16, 32);
            border = crop(kit.blockImage(6), 16, 16, 16);
        }
        floor = repeat(ground);
        wall = repeat(masonry);
        trim = border;
        int brightest = 0xFF888888, luminance = 0;
        for (int colour : kit.palette()) {
            int value = (colour >>> 16 & 255) + (colour >>> 8 & 255) + (colour & 255);
            if (value > luminance && value < 700) { brightest = colour | 0xFF000000; luminance = value; }
        }
        light = brightest;
    }

    private static SceneImage crop(SceneImage source, int x, int y, int size) {
        int[] pixels = new int[size * size];
        for (int py = 0; py < size; py++) for (int px = 0; px < size; px++) pixels[py * size + px] = source.pixel(x + px, y + py);
        return new SceneImage(size, size, pixels);
    }

    private static SceneImage[] repeat(SceneImage image) {
        if (image.width() == 16) return new SceneImage[] {image, image, image, image};
        return new SceneImage[] {crop(image, 0, 0, 16), crop(image, 16, 0, 16),
                crop(image, 0, 16, 16), crop(image, 16, 16, 16)};
    }

    /** Read art from decoded blocks, independent of the stock act's camera/route bounds. */
    static List<SceneImage> textures(SceneLevelKit kit) {
        List<SceneImage> result = new ArrayList<>();
        Set<Integer> hashes = new HashSet<>();
        for (int block = 1; block < kit.blockCount() && result.size() < 12; block++) {
            SceneImage image = kit.blockImage(block);
            for (int y = 0; y + 16 <= image.height() && result.size() < 12; y += 16) {
                for (int x = 0; x + 16 <= image.width() && result.size() < 12; x += 16) {
                    int[] pixels = new int[256];
                    Set<Integer> colours = new HashSet<>();
                    boolean opaque = true;
                    for (int py = 0; py < 16; py++) for (int px = 0; px < 16; px++) {
                        int colour = image.pixel(x + px, y + py);
                        pixels[py * 16 + px] = colour;
                        colours.add(colour);
                        if ((colour >>> 24) < 255) opaque = false;
                    }
                    if (opaque && colours.size() >= 3 && hashes.add(java.util.Arrays.hashCode(pixels))) {
                        result.add(new SceneImage(16, 16, pixels));
                    }
                }
            }
        }
        return result;
    }

    public void draw(SceneCanvas c, Field field, double cameraX, double cameraY, long ticks) {
        c.clear(0x090B16);
        Dungeon dungeon = field.dungeon;
        for (int y = (int) cameraY / 16 * 16; y < cameraY + c.height() + 16; y += 16) {
            for (int x = (int) cameraX / 16 * 16; x < cameraX + c.width() + 16; x += 16) {
                int sx = (int) Math.round(x - cameraX), sy = (int) Math.round(y - cameraY);
                int tile = Math.floorMod(x / 16, 2) + 2 * Math.floorMod(y / 16, 2);
                if (dungeon.floor(x + 8, y + 8)) {
                    c.draw(floor[tile], sx, sy, SceneDraw.plain().withAlpha(0.48f));
                    if (!dungeon.floor(x + 8, y - 8)) c.fill(sx, sy, 16, 3, 0x90000000);
                } else {
                    boolean beside = dungeon.floor(x - 8, y + 8) || dungeon.floor(x + 24, y + 8);
                    boolean above = dungeon.floor(x + 8, y + 24);
                    boolean below = dungeon.floor(x + 8, y - 8);
                    if (beside || above || below) {
                        c.draw(above || below ? trim : wall[tile], sx, sy);
                        if (above) c.fill(sx, sy + 14, 16, 2, light);
                    } else if (dungeon.floor(x + 8, y + 40)) {
                        c.draw(wall[tile], sx, sy, SceneDraw.plain().withAlpha(0.7f));
                    }
                }
                if (field.sealed(x + 8, y + 8)) {
                    c.fill(sx, sy, 16, 16, 0x801E3D61);
                    for (int bar = 2; bar < 16; bar += 5) c.fill(sx + bar, sy, 2, 16, light);
                }
            }
        }
        // The doorway and inner plinth are part of the world, so they scroll with the party.
        int doorX = (int) (48 - cameraX), doorY = (int) (320 - cameraY);
        c.fill(doorX, doorY, 8, 32, 0xFFB5DBFF);
        var goal = dungeon.layout().route.getLast();
        int altarX = (int) (goal.x() - 16 - cameraX), altarY = (int) (goal.y() - cameraY);
        c.draw(wall[0], altarX, altarY);
        c.draw(wall[1], altarX + 16, altarY);
        c.fill(altarX, altarY, 32, 2, light);
    }
}
