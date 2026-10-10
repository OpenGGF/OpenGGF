package threeislands.field;

import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import threeislands.core.Zone;

/**
 * Draws a {@link TileMap} from its zone's own ROM art, a 32-pixel cell at a time, plus props
 * (palms, rocks, lamps, cones) that the field depth-sorts with its walkers.
 *
 * <p>Green Hill: grass clearings and dirt trails cut through a jungle plateau whose south faces
 * are the checkerboard cliff with its grass fringe, animated sea and river water, log bridges
 * from {@code Nem_Bridge} and purple rocks from {@code Nem_PplRock} (Sonic 1 REV01 offsets).
 * Star Light: plated highways and catwalks on girders over the starfield background, edged by
 * the zone's rail with its red lights and lined with its street lamps, beside plated rooftops.
 * Crop rectangles address decoded level-kit blocks (256-pixel Sonic 1 chunks), inspected natively.
 */
final class MapArt {
    /** A depth-sorted decoration standing with its base at {@code y}. */
    record Prop(int x, int y, int type) {}

    static final int PALM = 0, ROCK = 1, LAMP = 2, LAMP_SHORT = 3, CONE = 4, BARRIER = 5, PLANT = 6;

    private final boolean greenHill;
    private final SceneLevelKit kit;
    private final SceneImage[] water;
    private final SceneImage cliff, lip, palm, plant, log, rock;
    private final SceneImage plate, rail, lattice, lamp, lampShort, cone, barrier, building, city;
    private final int grass, plateau, path, pathDark;
    private final List<Prop> props = new ArrayList<>();

    MapArt(Zone zone, SceneLevelKit kit, SceneRomArt rom, TileMap map, SceneImage[] water, SceneImage cliff,
            SceneImage lip, SceneImage palm, SceneImage plant, int grassColour) {
        this.kit = kit;
        greenHill = zone == Zone.GREEN_HILL;
        this.water = water;
        int[] palette = kit.palette();
        if (greenHill) {
            this.cliff = cliff;
            this.lip = lip;
            this.palm = palm;
            this.plant = plant;
            // Obj11 bridge logs use palette line 2 (Tile_Pal3); Obj3B rocks line 3 (Tile_Pal4).
            log = rom.tiles(0x2FA2C, Compression.NEMESIS, 0, 2, 2, true, Arrays.copyOfRange(palette, 32, 48));
            rock = rom.tiles(0x300BA, Compression.NEMESIS, 0, 6, 4, true, Arrays.copyOfRange(palette, 48, 64));
            plate = rail = lattice = lamp = lampShort = cone = barrier = building = city = null;
            // Palette line 0/2 browns for trails; the bright and deep greens for clearings and jungle.
            grass = mix(nearest(palette, 0x49B600), nearest(palette, 0x006D00), 0.55);
            plateau = nearest(palette, 0x004900);
            path = nearest(palette, 0xB66D49);
            pathDark = nearest(palette, 0x924900);
        } else {
            this.cliff = this.lip = this.palm = this.plant = log = rock = null;
            SceneImage chunk1 = kit.blockImage(1), chunk2 = kit.blockImage(2), chunk20 = kit.blockImage(20),
                    chunk24 = kit.blockImage(24);
            plate = crop(chunk1, 0, 240, 32, 16);
            rail = crop(chunk1, 0, 224, 32, 32);
            lamp = crop(chunk1, 16, 92, 32, 132);
            lampShort = crop(chunk1, 80, 108, 32, 116);
            lattice = crop(chunk20, 0, 100, 64, 80);
            cone = crop(chunk20, 48, 186, 16, 34);
            barrier = crop(chunk24, 16, 60, 64, 36);
            building = crop(chunk2, 0, 0, 128, 128);
            // Chunk 17 holds the distant city's lit windows on a transparent band.
            city = crop(kit.blockImage(17), 0, 112, 256, 144);
            grass = plateau = 0;
            path = 0xFF2E3A2E;
            pathDark = 0xFF1C241C;
        }
        for (int row = 0; row < map.rowCount(); row++) {
            for (int col = 0; col < map.columns; col++) {
                char c = map.at(col, row);
                int x = col * 32 + 16, y = row * 32 + 31;
                int hash = Math.floorMod(col * 73 + row * 151 + col * row * 7, 23);
                if (greenHill) {
                    if (c == 'T') props.add(new Prop(x, y, PALM));
                    else if (c == 'r') props.add(new Prop(x, y, ROCK));
                    else if (c == '#' && hash < 3 && canopy(map, col, row)) props.add(new Prop(x + hash * 4 - 4, y, PALM));
                    else if (c == '#' && hash > 19) props.add(new Prop(x, y, PLANT));
                } else {
                    if (c == 'r') props.add(new Prop(x, y, hash % 2 == 0 ? CONE : BARRIER));
                    else if (walkway(c) && map.at(col, row - 1) == '~' && Math.floorMod(col, 4) == 1) {
                        props.add(new Prop(x, row * 32 + 6, Math.floorMod(col, 8) == 1 ? LAMP : LAMP_SHORT));
                    }
                }
            }
        }
    }

    /** A jungle palm only where its trunk and crown stay over solid plateau. */
    private static boolean canopy(TileMap map, int col, int row) {
        for (int r = row - 4; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) if (map.at(c, r) != '#') return false;
        }
        return true;
    }

    private static boolean open(char c) {
        return !TileMap.solid(c) && c != 'R';
    }

    private static boolean walkway(char c) {
        return c != '~' && c != '#' && c != 'b' && c != 'R' && c != 'H' && c != 'O';
    }

    List<Prop> props() {
        return props;
    }

    void draw(SceneCanvas c, Field field, double cameraX, double cameraY, long ticks) {
        TileMap map = field.map;
        if (greenHill) c.clear(plateau & 0xFFFFFF);
        else sky(c, cameraX, cameraY, ticks);
        int c0 = (int) Math.floor(cameraX / 32), r0 = (int) Math.floor(cameraY / 32);
        for (int row = r0; row <= r0 + c.height() / 32 + 1; row++) {
            for (int col = c0; col <= c0 + c.width() / 32 + 1; col++) {
                float sx = (float) (col * 32 - cameraX), sy = (float) (row * 32 - cameraY);
                if (greenHill) greenHill(c, field, map, col, row, sx, sy, ticks);
                else starLight(c, field, map, col, row, sx, sy, ticks);
            }
        }
    }

    // ------------------------------------------------------------------ Green Hill

    private void greenHill(SceneCanvas c, Field field, TileMap map, int col, int row, float sx, float sy, long ticks) {
        char cell = map.base(col, row);
        int x = (int) sx, y = (int) sy;
        int hash = Math.floorMod(col * 31 + row * 17 + col * row, 11);
        switch (cell) {
            case '#' -> {
                char below = map.at(col, row + 1);
                if (open(below) || below == '~' || below == 'O') {
                    // The checkerboard face of the plateau, topped by its grass fringe.
                    c.draw(cliff, sx, sy, SceneDraw.plain().withScale(32f / cliff.width(), 32f / cliff.height()));
                    c.draw(lip, sx, sy - 6, SceneDraw.plain().withScale(32f / lip.width(), 12f / lip.height()));
                    c.fill(x, y + 30, 32, 2, 0x50000000);
                } else {
                    c.fill(x, y, 32, 32, plateau);
                    if (hash < 4) c.fill(x + hash * 6, y + 8 + hash * 3, 6, 3, mix(plateau, 0xFF000000, 0.25));
                }
                if (open(map.at(col - 1, row))) c.fill(x, y, 3, 32, 0x30000000);
                if (open(map.at(col + 1, row))) c.fill(x + 29, y, 3, 32, 0x30000000);
            }
            case '~' -> {
                waterCell(c, sx, sy, ticks);
                if (open(map.at(col, row - 1))) c.draw(lip, sx, sy - 2, SceneDraw.plain().withScale(32f / lip.width(), 8f / lip.height()));
            }
            case 'b' -> bridge(c, map, col, row, sx, sy, ticks);
            case 'O' -> {
                if (field.orchardOpen()) bridge(c, map, col, row, sx, sy, ticks);
                else waterCell(c, sx, sy, ticks);
            }
            case '=' -> {
                c.fill(x, y, 32, 32, path);
                // Pebbles and a soft grass border where the trail meets a clearing.
                if (hash < 5) c.fill(x + 4 + hash * 5, y + 6 + hash * 4, 3, 2, pathDark);
                if (hash > 7) c.fill(x + 20 - hash, y + 22, 2, 2, pathDark);
                edgeGrass(c, map, col, row, x, y);
            }
            default -> {
                c.fill(x, y, 32, 32, grass);
                if (hash < 2) c.fill(x + 8 + hash * 10, y + 12, 6, 2, mix(grass, 0xFFFFFFFF, 0.2));
                if (cell == ',') c.draw(plant, sx, sy - 16, SceneDraw.plain().withScale(32f / plant.width(), 48f / plant.height()));
            }
        }
    }

    private void edgeGrass(SceneCanvas c, TileMap map, int col, int row, int x, int y) {
        if (isGrass(map.base(col, row - 1))) c.fill(x, y, 32, 3, grass);
        if (isGrass(map.base(col, row + 1))) c.fill(x, y + 29, 32, 3, grass);
        if (isGrass(map.base(col - 1, row))) c.fill(x, y, 3, 32, grass);
        if (isGrass(map.base(col + 1, row))) c.fill(x + 29, y, 3, 32, grass);
    }

    private static boolean isGrass(char c) {
        return c == '.' || c == ',' || Character.isLetterOrDigit(c) && !TileMap.solid(c) && c != 'b' && c != 'O' && c != 'R';
    }

    private void waterCell(SceneCanvas c, float sx, float sy, long ticks) {
        SceneImage frame = water[(int) Math.floorMod(ticks / 6, water.length)];
        SceneDraw fit = SceneDraw.plain().withScale(16f / frame.width(), 16f / frame.height());
        for (int dy = 0; dy < 32; dy += 16) for (int dx = 0; dx < 32; dx += 16) c.draw(frame, sx + dx, sy + dy, fit);
    }

    private void bridge(SceneCanvas c, TileMap map, int col, int row, float sx, float sy, long ticks) {
        waterCell(c, sx, sy, ticks);
        boolean across = map.at(col - 1, row) != '~' || map.at(col + 1, row) != '~';
        if (across) {
            // Obj11's logs, side by side, with the rope above and below.
            for (int dx = 0; dx < 32; dx += 16) c.draw(log, sx + dx, sy + 4, SceneDraw.plain().withScale(1f, 1.5f));
            c.fill((int) sx, (int) sy + 3, 32, 2, 0xFF603818);
            c.fill((int) sx, (int) sy + 28, 32, 2, 0xFF603818);
        } else {
            for (int dy = 0; dy < 32; dy += 16) c.draw(log, sx + 4, sy + dy, SceneDraw.plain().withScale(1.5f, 1f));
            c.fill((int) sx + 3, (int) sy, 2, 32, 0xFF603818);
            c.fill((int) sx + 28, (int) sy, 2, 32, 0xFF603818);
        }
    }

    // ------------------------------------------------------------------ Star Light

    private void sky(SceneCanvas c, double cameraX, double cameraY, long ticks) {
        c.clear(0x000008);
        if (kit.backdrop() != null) {
            int span = Math.max(0, kit.backdrop().image().height() - c.height());
            c.drawBackdrop(kit.backdrop(), Math.min(span, (int) (cameraY * 0.15)), cameraX * 0.3, ticks);
        }
        // The sleeping city drifts past far below the highways.
        int top = c.height() - 132 - (int) (cameraY * 0.08) % 24;
        int offset = (int) Math.floorMod((long) (cameraX * 0.5), (long) city.width());
        for (int x = -offset; x < c.width(); x += city.width()) c.draw(city, x, top, SceneDraw.plain().withAlpha(0.8f));
        c.fill(0, top + city.height() - 12, c.width(), c.height(), 0xFF02020C);
    }

    /** Street lamps stand on walkway cells whose north edge faces the open sky. */
    private static boolean lampAt(TileMap map, int col, int row) {
        return walkway(map.at(col, row)) && map.at(col, row - 1) == '~' && Math.floorMod(col, 4) == 1;
    }

    private void starLight(SceneCanvas c, Field field, TileMap map, int col, int row, float sx, float sy, long ticks) {
        char cell = map.base(col, row);
        int x = (int) sx, y = (int) sy;
        char above = map.at(col, row - 1);
        switch (cell) {
            case '~' -> {
                // Under a platform's rail, its girders drop away into the night.
                if (above != '~' && above != '#') {
                    c.draw(lattice, sx, sy, SceneDraw.plain().withScale(32f / lattice.width(), 40f / lattice.height()).withAlpha(0.85f));
                    c.fill(x, y + 22, 32, 10, 0x60000008);
                } else if (above == '#') {
                    roof(c, col, row - 1, sx, sy, 0.45f);
                    c.fill(x, y + 16, 32, 16, 0x70000008);
                }
            }
            case '#' -> {
                roof(c, col, row, sx, sy, above == '#' ? 0.7f : 0.8f);
                if (above != '#') c.fill(x, y, 32, 2, 0xFF98B898);
            }
            case 'b' -> catwalk(c, map, col, row, sx, sy);
            case 'R' -> {
                if (field.routeOpen()) catwalk(c, map, col, row, sx, sy);
                else {
                    catwalk(c, map, col, row, sx, sy);
                    int glow = (int) (90 + 60 * Math.sin(ticks / 8.0));
                    c.fill(x, y, 32, 32, glow << 24 | 0x40C0FF);
                    for (int bar = 3; bar < 32; bar += 7) c.fill(x + bar, y, 2, 32, 0xC0E0F8FF);
                }
            }
            default -> {
                for (int dy = 0; dy < 32; dy += 16) c.draw(plate, sx, sy + dy, SceneDraw.plain().withScale(1f, 1f));
                int hash = Math.floorMod(col * 37 + row * 11 + col * row, 13);
                if (hash == 0) roof(c, col, row, sx, sy, 0.18f);
                // Lamplight pools on the plating beneath each street lamp.
                int light = 0;
                for (int lc = col - 1; lc <= col + 1; lc++) for (int lr = row - 1; lr <= row; lr++) {
                    if (lampAt(map, lc, lr)) light = Math.max(light, lc == col && lr == row ? 0x38 : lr == row || lc == col ? 0x20 : 0x10);
                }
                if (light > 0) c.fill(x, y, 32, 32, light << 24 | 0xFFF0B0);
                if (cell == '=') {
                    c.fill(x, y, 32, 32, 0x40000000);
                    if (Math.floorMod(col, 2) == 0) c.fill(x + 6, y + 15, 20, 2, 0xC0F0E070);
                }
                // The zone's rail, with its red lights, runs along every open edge.
                if (above == '~' || above == 'b') c.draw(rail, sx, sy - 4, SceneDraw.plain().withScale(1f, 12f / rail.height()));
                if (map.at(col, row + 1) == '~') c.draw(rail, sx, sy + 24, SceneDraw.plain().withScale(1f, 12f / rail.height()));
                if (map.at(col - 1, row) == '~') c.fill(x, y, 3, 32, 0xFFC8D0C8);
                if (map.at(col + 1, row) == '~') c.fill(x + 29, y, 3, 32, 0xFFC8D0C8);
            }
        }
    }

    private void roof(SceneCanvas c, int col, int row, float sx, float sy, float alpha) {
        int ox = Math.floorMod(col * 32, building.width()), oy = Math.floorMod(row * 32, building.height());
        c.drawRegion(building, ox, oy, 32, 32, sx, sy, 32, 32, SceneDraw.plain().withAlpha(alpha));
    }

    private void catwalk(SceneCanvas c, TileMap map, int col, int row, float sx, float sy) {
        boolean across = map.at(col - 1, row) != '~' || map.at(col + 1, row) != '~';
        int x = (int) sx, y = (int) sy;
        c.draw(lattice, sx, sy, SceneDraw.plain().withScale(32f / lattice.width(), 32f / lattice.height()));
        if (across) {
            c.fill(x, y + 6, 32, 20, 0xFF4E6A4E);
            c.fill(x, y + 4, 32, 3, 0xFFD8E0D8);
            c.fill(x, y + 25, 32, 3, 0xFF889888);
            if (Math.floorMod(col, 2) == 0) c.fill(x + 12, y + 5, 6, 2, 0xFFFF3030);
        } else {
            c.fill(x + 6, y, 20, 32, 0xFF4E6A4E);
            c.fill(x + 4, y, 3, 32, 0xFFD8E0D8);
            c.fill(x + 25, y, 3, 32, 0xFF889888);
        }
    }

    // ------------------------------------------------------------------ props

    void drawProp(SceneCanvas c, Prop prop, double cameraX, double cameraY, long ticks) {
        float x = (float) (prop.x() - cameraX), y = (float) (prop.y() - cameraY);
        switch (prop.type()) {
            case PALM -> c.draw(palm, x - palm.width() / 2f, y - palm.height());
            case PLANT -> c.draw(plant, x - plant.width() / 2f, y - plant.height());
            case ROCK -> c.draw(rock, x - rock.width() / 2f, y - rock.height() + 2);
            case LAMP -> c.draw(lamp, x - lamp.width() / 2f, y - lamp.height() + 8);
            case LAMP_SHORT -> c.draw(lampShort, x - lampShort.width() / 2f, y - lampShort.height() + 8);
            case CONE -> c.draw(cone, x - cone.width() / 2f, y - cone.height());
            default -> c.draw(barrier, x - barrier.width() / 2f, y - barrier.height() + 4);
        }
    }

    /** Building facade texture for this theme's dungeon entrance. */
    SceneImage facade() {
        return greenHill ? cliff : crop(building, 0, 0, 32, 32);
    }

    private static SceneImage crop(SceneImage image, int x, int y, int width, int height) {
        int[] pixels = new int[width * height];
        for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) {
            pixels[py * width + px] = image.pixel(Math.min(image.width() - 1, x + px), Math.min(image.height() - 1, y + py));
        }
        return new SceneImage(width, height, pixels);
    }

    private static int nearest(int[] palette, int target) {
        int best = palette[0], score = Integer.MAX_VALUE;
        for (int colour : palette) {
            int dr = (colour >> 16 & 255) - (target >> 16 & 255), dg = (colour >> 8 & 255) - (target >> 8 & 255),
                    db = (colour & 255) - (target & 255);
            int d = dr * dr + dg * dg + db * db;
            if (d < score) { score = d; best = colour; }
        }
        return best | 0xFF000000;
    }

    private static int mix(int a, int b, double t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }
}
