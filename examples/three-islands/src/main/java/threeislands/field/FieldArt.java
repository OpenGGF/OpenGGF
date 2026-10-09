package threeislands.field;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import java.util.Arrays;
import threeislands.core.Zone;

/** Cached ROM fragments assembled as a freely walkable oblique field, never stock platform physics. */
public final class FieldArt {
    private final SceneImage ground, cliff, lip, palm, flowers, doorway;
    private final SceneLevelKit kit;
    private final SceneImage[] waterFrames;
    private final boolean greenHill;
    private final int grassColour;

    public FieldArt(Zone zone, SceneLevelKit kit, SceneRomArt rom) {
        this.kit = kit;
        int darkest = java.util.Arrays.stream(kit.palette()).boxed().min(java.util.Comparator.comparingInt(
                colour -> (colour >>> 16 & 255) + (colour >>> 8 & 255) + (colour & 255))).orElse(0);
        doorway = new SceneImage(1, 1, new int[] {darkest | 0xFF000000});
        greenHill = zone == Zone.GREEN_HILL;
        if (greenHill) {
            // GHZ act-1 decoded chunk atlas: 1 = palm/grass bank, 6 = checker cliff.
            // Coordinates address decoded kit art,
            // not external images; the kit owns ROM parsing, palette and tile flips.
            ground = crop(kit.blockImage(1), 0, 128, 32, 8);
            cliff = crop(kit.blockImage(6), 32, 32, 32, 32);
            lip = crop(kit.blockImage(1), 0, 128, 32, 16);
            waterFrames = greenHillWater(rom, kit.palette());
            palm = crop(kit.blockImage(1), 96, 0, 80, 128);
            flowers = crop(kit.blockImage(1), 0, 80, 32, 48);
        } else {
            var textures = DungeonArt.textures(kit);
            SceneImage tile = textures.isEmpty() ? floorTile(kit) : textures.get(Math.min(3, textures.size() - 1));
            ground = zone == Zone.EMERALD_HILL ? floorTile(kit) : tile;
            cliff = textures.isEmpty() ? tile : textures.get(0);
            lip = zone == Zone.EMERALD_HILL ? cliff : textures.isEmpty() ? tile : textures.get(Math.min(7, textures.size() - 1));
            SceneImage source = kit.backdrop() == null ? tile : kit.backdrop().image();
            SceneImage water = crop(source, 0, Math.max(0, source.height() - 32), 32, 32);
            waterFrames = new SceneImage[] {water};
            palm = null;
            flowers = null;
        }
        int selected = kit.palette()[0];
        int difference = Integer.MAX_VALUE;
        for (int colour : kit.palette()) {
            int r = colour >>> 16 & 255, g = colour >>> 8 & 255, b = colour & 255;
            if (g > r * 2 && g > b * 2 && Math.abs(g - 120) < difference) {
                selected = colour;
                difference = Math.abs(g - 120);
            }
        }
        grassColour = selected | 0xFF000000;
    }

    /** GHZ Blk16 $61: $40F6,$40F7,$40F8,$40F9 (line 2, row-major, no flips).
     * PalCycle_GHZ animates these reflections, not AniArt_GHZ_Waterfall's vertical art.
     * ROM offsets are Sonic 1 World REV01: Nem_GHZ_1st and Pal_GHZCyc_Water.
     */
    static SceneImage[] greenHillWater(SceneRomArt rom, int[] zonePalette) {
        SceneImage[] frames = new SceneImage[4];
        for (int step = 0; step < frames.length; step++) {
            int[] palette = Arrays.copyOfRange(zonePalette, 32, 48);
            System.arraycopy(rom.palette(0x1B7E + step * 8, 4), 0, palette, 8, 4);
            frames[step] = rom.tiles(0x3CB3C, Compression.NEMESIS, 0xF6, 2, 2, false, palette);
        }
        return frames;
    }

    SceneImage waterFrame(long ticks) {
        // PCycGHZ_Go reloads 5 then decrements through zero: six scene ticks per step.
        return waterFrames[(int) Math.floorMod(ticks / 6, waterFrames.length)];
    }

    private static SceneImage crop(SceneImage image, int x, int y, int width, int height) {
        int[] pixels = new int[width * height];
        for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) {
            pixels[py * width + px] = image.pixel(Math.min(image.width() - 1, x + px), Math.min(image.height() - 1, y + py));
        }
        return new SceneImage(width, height, pixels);
    }

    /** Find an opaque solid ROM tile; each region retains its own terrain palette and texture. */
    private static SceneImage floorTile(SceneLevelKit kit) {
        int size = kit.blockSize();
        for (int id = 1; id < Math.min(96, kit.blockCount()); id++) {
            byte[] solid = kit.blockSolidity(id);
            SceneImage image = kit.blockImage(id);
            for (int y = 16; y < size - 16; y += 16) for (int x = 0; x < size - 16; x += 16) {
                if (solid[y * size + x] == 0) continue;
                boolean opaque = true;
                for (int py = 0; py < 16 && opaque; py++) for (int px = 0; px < 16; px++) {
                    if ((image.pixel(x + px, y + py) >>> 24) == 0) { opaque = false; break; }
                }
                if (opaque) return crop(image, x, y, 16, 16);
            }
        }
        // Still ROM art when a decorative region has no collision-marked opaque tile.
        return crop(kit.blockImage(1), 0, 0, 16, 16);
    }

    public void draw(SceneCanvas c, Field field, double cameraX, double cameraY, long ticks) {
        c.clear(kit.palette()[0] & 0xFFFFFF);
        // The strip of distant scenery above the traversable area is the ROM backdrop.
        if (kit.backdrop() != null) c.drawBackdrop(kit.backdrop(), 0, -(int) cameraY,
                field.width(), 88, 0, cameraX, ticks);
        for (int y = (int) cameraY / 16 * 16; y < cameraY + c.height() + 16; y += 16) {
            for (int x = (int) cameraX / 16 * 16; x < cameraX + c.width() + 16; x += 16) {
                if (y < 16) continue;
                boolean wet = field.water(x + 8, y + 8);
                boolean edge = ((x < 48 || x >= field.width() - 48) && (y < 304 || y >= 368)) || y >= field.height() - 48;
                boolean crossing = field.orchardCrossing(x + 8, y + 8);
                SceneImage tile = wet ? waterFrame(ticks) : edge || crossing ? cliff : ground;
                // Grass fragments retain original pixels, repeated in two short rows for ground depth.
                if (greenHill && !wet && !edge && !crossing) {
                    c.fill((int) (x - cameraX), (int) (y - cameraY), 16, 16, grassColour);
                    // A side-facing grass fringe is not a top-down floor. Use the palette for
                    // the ground plane and keep the real grass art as sparse, un-stretched tufts.
                    if (Math.floorMod(x / 16 * 7 + y / 16 * 11, 13) == 0) {
                        c.draw(ground, (float) (x - cameraX), (float) (y - cameraY), SceneDraw.plain().withAlpha(0.25f));
                    }
                } else c.draw(tile, (float) (x - cameraX), (float) (y - cameraY),
                        SceneDraw.plain().withScale(16f / tile.width(), 16f / tile.height()).withAlpha(greenHill ? 1f : wet ? 0.3f : 0.6f));
            }
        }
        if (field.layout != null) {
            for (var passage : field.layout.passages) {
                int gx = (passage.from().x() + passage.to().x()) / 2;
                int gy = (passage.from().y() + passage.to().y()) / 2;
                if (!field.sealed(gx, gy)) continue;
                boolean vertical = passage.from().x() == passage.to().x();
                int w = vertical ? 64 : 16, h = vertical ? 16 : 64;
                bank(c, gx - w / 2, gy - h / 2, w, h, cameraX, cameraY);
                c.fill((int) (gx - w / 2 - cameraX), (int) (gy - h / 2 - cameraY), w, h, 0x606ECFFF);
            }
        }
        // Raised banks use the original face and grass fringe. Their rectangles are collision owners.
        for (int[] bank : field.banks()) bank(c, bank[0], bank[1], bank[2], bank[3], cameraX, cameraY);
        // Natural bridge: the same grass and checkerboard bank, not a generated wooden texture.
        for (int x = 400; field.layout == null && x < 560; x += 32) {
            c.draw(lip, (float) (x - cameraX), (float) (350 - cameraY));
        }
        if (greenHill) {
            for (int[] tree : new int[][] {{64,296},{164,208},{344,448},{608,288},{876,232},{624,568},{64,536},{184,512},{288,720},{160,912},{688,752},{832,928},{944,592},{1040,240},{1280,400},{1440,704},{1264,864},{1392,960}}) {
                c.draw(palm, (float) (tree[0] - 40 - cameraX), (float) (tree[1] - 128 - cameraY));
            }
            for (int[] flower : new int[][] {{96,288},{264,240},{360,432},{656,272},{672,568},{880,560},{1120,624},{1280,640},{1216,880},{704,880},{256,816},{1408,224}}) {
                c.draw(flowers, (float) (flower[0] - cameraX), (float) (flower[1] - 48 - cameraY));
            }
        } else {
            // ROM architecture marks the northern skyline without covering the walkable plane.
            int block = kit.block(Math.max(0, kit.columns() / 3), 0);
            if (block != 0) c.draw(kit.blockImage(block), (float) (512 - cameraX), (float) (80 - kit.blockSize() - cameraY));
        }
        // A ROM-textured facade has the same solid footprint as Field.walkable.
        // Towers, buttresses and the recessed door make each entrance part of its landscape.
        Field.Spot door = field.entrance();
        int dx = (int) door.homeX, dy = (int) door.homeY;
        bank(c, dx - 72, dy - 112, 144, 96, cameraX, cameraY);
        bank(c, dx - 80, dy - 120, 32, 104, cameraX, cameraY);
        bank(c, dx + 48, dy - 120, 32, 104, cameraX, cameraY);
        int sx = (int) (dx - cameraX), sy = (int) (dy - cameraY);
        c.draw(doorway, sx - 24, sy - 56, SceneDraw.plain().withScale(48, 44));
        for (int px = dx - 32; px < dx + 32; px += 16) c.draw(cliff, (float) (px - cameraX),
                (float) (dy - 64 - cameraY), SceneDraw.plain().withScale(16f / cliff.width(), 16f / cliff.height()));
        for (int step = 0; step < 3; step++) {
            c.draw(lip, sx - 32, sy - 12 + step * 4, SceneDraw.plain().withScale(64f / lip.width(), 4f / lip.height()));
        }
    }

    private void bank(SceneCanvas c, int x, int y, int width, int height, double cameraX, double cameraY) {
        for (int py = y; py < y + height; py += 16) for (int px = x; px < x + width; px += 16) {
            c.draw(cliff, (float) (px - cameraX), (float) (py - cameraY), SceneDraw.plain().withScale(16f / cliff.width(), 16f / cliff.height()));
        }
        for (int px = x; px < x + width; px += 32) c.draw(lip, (float) (px - cameraX), (float) (y - cameraY));
    }
}
