package com.openggf.tools.modsdk;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Deterministic offline generator for the {@code sample-platformer} gallery mod's original
 * assets: the TMX level + GPAL palette (fed to {@code ggfmod convert level --from-tmx --palette}),
 * the four PNG sprite sheets + YAML manifests (fed to {@code ggfmod convert art} /
 * {@code convert art --playable} in a later task), and the WAV/OGG audio.
 *
 * <p>This is a plain {@code main()} utility, run manually and checked in as data (not executed by
 * the build). It contains no timestamps, randomness, or environment-dependent input: every pixel,
 * XML byte, and PCM sample is computed from fixed constants, so re-running it reproduces the same
 * output every time.
 *
 * <p><b>OGG encode step (the one non-hermetic step):</b> this generator writes
 * {@code audio/zone-theme.wav} (a ~8s deterministic integer-cycle chiptune arpeggio). The checked-in
 * {@code audio/zone-theme.ogg} was produced exactly once, by hand, with:
 * <pre>ffmpeg -i zone-theme.wav -c:a libvorbis -q:a 3 zone-theme.ogg</pre>
 * after which the intermediate {@code zone-theme.wav} was deleted. If you regenerate assets, rerun
 * that exact command and delete the WAV again — do not check in the WAV as the shipped music track.
 */
public final class SamplePlatformerAssetGenerator {
    private static final Path MOD_DIR = Path.of(
            "src/test/resources/mods/sample-platformer-src/project/src/main/mod");
    private static final Path AUDIO_DIR = Path.of(
            "src/test/resources/mods/sample-platformer-src/project/src/main/resources/audio");

    // ---- Level geometry (tiles; 16px/tile) ----
    private static final int MAP_WIDTH_TILES = 128;
    private static final int MAP_HEIGHT_TILES = 16;
    private static final int GROUND_ROW_TOP = 13; // grass row
    private static final int GROUND_ROW_BOTTOM = 15; // last dirt row (inclusive)
    private static final int PIT_START_COL = 60;
    private static final int PIT_END_COL = 63; // inclusive

    // Tileset GIDs (1-based; index 0 in the image is GID 1).
    private static final int GID_BLANK = 1;
    private static final int GID_GRASS = 2;
    private static final int GID_DIRT = 3;
    private static final int GID_PLATFORM = 4;
    private static final int GID_DECORATION = 5;
    private static final int GID_CLOUD = 6;
    private static final int GID_DECORATION2 = 7;
    private static final int GID_CLOUD2 = 8;
    private static final int TILE_COUNT = 8;

    /** Palette line 1 (tile/object colors): (r,g,b) as 3-bit Genesis channel codes, index 0..15. */
    private static final int[][] LINE1 = {
            {0, 0, 0}, // 0: reserved/transparent slot (never matched -- alpha-0 pixels always map here)
            {0, 0, 0}, // 1: outline black
            {2, 6, 2}, // 2: grass light
            {1, 4, 1}, // 3: grass dark
            {5, 3, 1}, // 4: dirt light
            {3, 2, 0}, // 5: dirt dark
            {6, 5, 3}, // 6: platform tan
            {4, 3, 2}, // 7: platform dark
            {7, 6, 4}, // 8: platform highlight
            {2, 7, 2}, // 9: decoration leaf green
            {7, 3, 5}, // 10: decoration flower pink
            {7, 7, 2}, // 11: decoration flower yellow
            {4, 6, 7}, // 12: sky cyan
            {7, 7, 7}, // 13: sky white
            {6, 4, 2}, // 14: decoration2 rock brown
            {2, 4, 7}, // 15: sky detail2 dark blue
    };

    /**
     * Palette line 2: the unified OBJECT palette shared by zapbug, springpad, and ring
     * (flappy-precedent: the level GPAL deliberately populates the CRAM line the object
     * sheets land on). The three object PNGs are drawn strictly from these RGB values and
     * each object sheet YAML declares exactly this 16-color list, so convert-time
     * quantization is lossless AND the runtime colors (sourced from this GPAL line) match
     * the designed art.
     */
    private static final int[][] LINE2 = {
            {0, 0, 0}, // 0: transparent slot (background pixels quantize here; hidden at runtime)
            {1, 1, 1}, // 1: outline near-black
            {7, 6, 0}, // 2: ring gold light
            {5, 4, 0}, // 3: ring gold dark
            {7, 0, 0}, // 4: bright red (zapbug core / springpad accent stripe)
            {4, 0, 0}, // 5: zapbug mid red
            {1, 0, 0}, // 6: zapbug outline dark red
            {2, 2, 2}, // 7: dark gray (zapbug legs frame 0)
            {5, 5, 5}, // 8: light gray (zapbug legs frame 1 / springpad coil shade)
            {7, 7, 0}, // 9: yellow (zapbug eye / springpad coil bright)
            {4, 4, 0}, // 10: dark yellow (springpad compressed coil)
            {3, 3, 3}, // 11: mid gray (springpad base plate)
            {7, 7, 7}, // 12: white highlight
            {7, 4, 0}, // 13: orange (spare)
            {3, 2, 0}, // 14: brown (spare)
            {6, 6, 6}, // 15: bright gray (spare)
    };

    /**
     * Palette line 0 (character colors) -- a PLACEHOLDER, not Bolt's runtime colors. Bolt's
     * on-screen colors come from the {@code .ggfp} BASE palette (the 16 colors declared in
     * {@code bolt-sheet.yaml}) via {@code CharacterDefinition}'s palette supplier
     * ({@code ignored -> materialized.palette()}, per the sample-standalone-src precedent),
     * which the player-palette path loads over CRAM line 0 at runtime. This GPAL line only
     * needs to exist (GPAL requires whole 16-color lines) and to stay disjoint from LINE1 so
     * tileset patterns always quantize against line 1.
     */
    private static final int[][] LINE0 = {
            {0, 0, 0}, {1, 1, 1}, {2, 2, 2}, {3, 3, 3},
            {4, 4, 4}, {5, 5, 5}, {6, 6, 6}, {7, 7, 6},
            {1, 2, 4}, {2, 4, 6}, {3, 5, 7}, {6, 1, 1},
            {7, 3, 3}, {7, 5, 1}, {5, 3, 6}, {1, 5, 3},
    };

    /**
     * Warning header emitted into every object-sheet YAML. paletteLine is a BASE, not the final
     * CRAM line: SpritePieceRenderer.preparePiece computes the rendered line as
     * {@code (piece.paletteIndex + paletteLine) & 0x3} (Genesis art_tile-addition semantics).
     */
    private static final String OBJECT_LINE_WARNING = """
            # paletteLine is a BASE, not the final CRAM line: SpritePieceRenderer.preparePiece
            # computes the rendered line as (paletteLine + piece.paletteIndex) & 0x3 (Genesis
            # art_tile-addition semantics). Every piece below declares paletteIndex: 0, so this
            # sheet renders on CRAM line (2 + 0) & 3 = 2 -- which this mod's palette.gpal
            # DELIBERATELY populates with the shared 16-color object palette (ring golds,
            # zapbug reds, springpad grays/yellow), mirroring the sample-flappy pipe-sheet
            # approach. The declared palette list below is byte-for-byte that GPAL line, so
            # convert-time quantization and runtime colors agree. Keep the three in sync:
            # this list, the PNG's pixels, and the generator's LINE2 GPAL data. Do not bump
            # paletteIndex: (2 + 1) & 3 = 3 is zero-filled in the GPAL (solid black).
            """;

    private SamplePlatformerAssetGenerator() {}

    public static void main(String[] args) throws IOException {
        Files.createDirectories(MOD_DIR);
        Files.createDirectories(AUDIO_DIR);

        writePalette();
        writeTileset();
        writeLevelTmx();

        writeBolt();
        writeZapbug();
        writeSpringpad();
        writeRing();

        writeSfx(AUDIO_DIR.resolve("jump2.wav"), 25, 882, 80); // 882Hz, 80 cycles
        writeSfx(AUDIO_DIR.resolve("hit.wav"), 50, 441, 40); // 441Hz, 40 cycles
        writeSfx(AUDIO_DIR.resolve("spring.wav"), 15, 1470, 100); // 1470Hz, 100 cycles
        writeZoneThemeWav();
        writeAudioManifest();

        System.out.println("sample-platformer assets generated under " + MOD_DIR + " and " + AUDIO_DIR);
    }

    // ---- Palette ----

    private static void writePalette() throws IOException {
        byte[] bytes = binary(out -> {
            out.writeBytes("GPAL");
            out.writeShort(1);
            out.writeShort(3);
            out.writeShort(16);
            out.writeShort(0);
            writeLine(out, LINE0); // line 0: player placeholder (overridden by .ggfp palette)
            writeLine(out, LINE1); // line 1: tileset colors
            writeLine(out, LINE2); // line 2: shared object colors (zapbug/springpad/ring)
        });
        Files.write(MOD_DIR.resolve("palette.gpal"), bytes);
    }

    private static void writeLine(DataOutputStream out, int[][] tuples) throws IOException {
        for (int[] t : tuples) out.writeShort(word(t[0], t[1], t[2]));
    }

    private static int word(int r3, int g3, int b3) {
        return (b3 << 9) | (g3 << 5) | (r3 << 1);
    }

    private static int channel8(int n3) {
        return (n3 * 255 + 3) / 7;
    }

    private static int rgb8(int r3, int g3, int b3) {
        return (channel8(r3) << 16) | (channel8(g3) << 8) | channel8(b3);
    }

    // ---- Tileset ----

    private static void writeTileset() throws IOException {
        BufferedImage image = new BufferedImage(TILE_COUNT * 16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int tile = 0; tile < TILE_COUNT; tile++) {
            int ox = tile * 16;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                image.setRGB(ox + x, y, tilePixel(tile, x, y));
            }
        }
        Path out = MOD_DIR.resolve("tileset.png");
        Files.deleteIfExists(out);
        if (!ImageIO.write(image, "png", out.toFile())) throw new IOException("No PNG writer for tileset.png");
    }

    private static int tilePixel(int tile, int x, int y) {
        boolean edge = x == 0 || x == 15 || y == 0 || y == 15;
        return switch (tile) {
            case 0 -> transparent(); // blank
            case 1 -> edge ? opaque(1) : (y < 4 ? opaque(2) : (y == 4 ? opaque(3) : opaque(4))); // grass
            case 2 -> edge ? opaque(1) : (((x + y) % 4 == 0) ? opaque(4) : opaque(5)); // dirt
            case 3 -> edge ? opaque(1) : (y < 2 ? opaque(8) : (y >= 14 ? opaque(7) : opaque(6))); // platform
            case 4 -> decorationPixel(x, y); // decoration (flower)
            case 5 -> cloudPixel(x, y, 13, 12, 3); // sky detail
            case 6 -> decoration2Pixel(x, y); // decoration2 (rock/tuft)
            case 7 -> cloudPixel(x, y, 15, 13, 5); // sky detail2 (larger)
            default -> transparent();
        };
    }

    private static int decorationPixel(int x, int y) {
        int dx = x - 8, dy = y - 11;
        if (dx * dx + dy * dy <= 4) return opaque(9); // stem/leaf blob
        int px = x - 8, py = y - 6;
        if (px * px + py * py <= 9) {
            return ((x + y) % 2 == 0) ? opaque(10) : opaque(11); // petals
        }
        return transparent();
    }

    private static int cloudPixel(int x, int y, int fillIndex, int outlineIndex, int radius) {
        int dx = x - 8, dy = y - 4;
        int r2 = dx * dx + dy * dy;
        if (r2 <= (radius - 1) * (radius - 1)) return opaque(fillIndex);
        if (r2 <= radius * radius) return opaque(outlineIndex);
        return transparent();
    }

    private static int decoration2Pixel(int x, int y) {
        int dx = x - 8, dy = y - 12;
        if (dx * dx + dy * dy <= 9) return opaque(14);
        return transparent();
    }

    private static int transparent() {
        return 0; // alpha=0; RGB is irrelevant per TmxLevelImporter.encodePattern
    }

    private static int opaque(int line1Index) {
        int[] t = LINE1[line1Index];
        return 0xFF000000 | rgb8(t[0], t[1], t[2]);
    }

    // ---- Level TMX ----

    private static void writeLevelTmx() throws IOException {
        int[] fg = new int[MAP_WIDTH_TILES * MAP_HEIGHT_TILES];
        int[] collision = new int[fg.length];

        for (int col = 0; col < MAP_WIDTH_TILES; col++) {
            boolean pit = col >= PIT_START_COL && col <= PIT_END_COL;
            if (pit) continue;
            fg[GROUND_ROW_TOP * MAP_WIDTH_TILES + col] = GID_GRASS;
            for (int row = GROUND_ROW_TOP + 1; row <= GROUND_ROW_BOTTOM; row++) {
                fg[row * MAP_WIDTH_TILES + col] = GID_DIRT;
            }
        }
        placePlatform(fg, 15, 18, 11);
        placePlatform(fg, 35, 38, 9);
        placePlatform(fg, 68, 71, 11);

        for (int col : new int[] {5, 25, 45, 85, 105}) {
            fg[12 * MAP_WIDTH_TILES + col] = GID_DECORATION;
        }
        for (int col : new int[] {65, 115}) {
            fg[12 * MAP_WIDTH_TILES + col] = GID_DECORATION2;
        }
        for (int col : new int[] {10, 50, 90, 120}) {
            fg[2 * MAP_WIDTH_TILES + col] = GID_CLOUD;
        }
        for (int col : new int[] {30, 100}) {
            fg[3 * MAP_WIDTH_TILES + col] = GID_CLOUD2;
        }

        for (int i = 0; i < fg.length; i++) {
            collision[i] = (fg[i] == GID_GRASS || fg[i] == GID_DIRT || fg[i] == GID_PLATFORM) ? 1 : 0;
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<map version=\"1.10\" tiledversion=\"1.11.2\" orientation=\"orthogonal\" ")
                .append("renderorder=\"right-down\" width=\"").append(MAP_WIDTH_TILES)
                .append("\" height=\"").append(MAP_HEIGHT_TILES)
                .append("\" tilewidth=\"16\" tileheight=\"16\" infinite=\"0\" nextlayerid=\"4\" nextobjectid=\"1\">\n");
        xml.append("  <tileset firstgid=\"1\" name=\"tileset\" tilewidth=\"16\" tileheight=\"16\" ")
                .append("tilecount=\"").append(TILE_COUNT).append("\" columns=\"").append(TILE_COUNT)
                .append("\" margin=\"0\" spacing=\"0\">\n")
                .append("    <image source=\"tileset.png\" width=\"").append(TILE_COUNT * 16)
                .append("\" height=\"16\"/>\n  </tileset>\n");
        xml.append(layer("FG", fg));
        xml.append(layer("COLLISION", collision));
        xml.append(objects());
        xml.append("</map>\n");

        Files.writeString(MOD_DIR.resolve("level.tmx"), xml.toString(), StandardCharsets.UTF_8);
    }

    private static void placePlatform(int[] fg, int startCol, int endCol, int row) {
        for (int col = startCol; col <= endCol; col++) fg[row * MAP_WIDTH_TILES + col] = GID_PLATFORM;
    }

    private static String layer(String name, int[] gids) {
        StringBuilder sb = new StringBuilder();
        sb.append("  <layer id=\"").append(name.equals("FG") ? 2 : 3).append("\" name=\"").append(name)
                .append("\" width=\"").append(MAP_WIDTH_TILES).append("\" height=\"").append(MAP_HEIGHT_TILES)
                .append("\">\n    <data encoding=\"csv\">");
        for (int i = 0; i < gids.length; i++) {
            if (i > 0) sb.append(',');
            if (i % MAP_WIDTH_TILES == 0 && i > 0) sb.append('\n');
            sb.append(gids[i]);
        }
        sb.append("</data>\n  </layer>\n");
        return sb.toString();
    }

    private static String objects() {
        StringBuilder sb = new StringBuilder();
        sb.append("  <objectgroup id=\"1\" name=\"OBJECTS\">\n");
        int id = 1;
        sb.append("    <object id=\"").append(id++).append("\" class=\"start\" x=\"64\" y=\"160\"><point/></object>\n");
        sb.append("    <object id=\"").append(id++).append("\" class=\"object\" x=\"900\" y=\"192\">")
                .append("<properties><property name=\"objectKey\" value=\"sample-platformer:zapbug\"/></properties>")
                .append("<point/></object>\n");
        sb.append("    <object id=\"").append(id++).append("\" class=\"object\" x=\"944\" y=\"192\">")
                .append("<properties><property name=\"objectKey\" value=\"sample-platformer:springpad\"/></properties>")
                .append("<point/></object>\n");
        for (int i = 0; i < 20; i++) {
            int x = 80 + i * 95;
            int y = 100 + ((i * 37) % 90);
            sb.append("    <object id=\"").append(id++).append("\" class=\"ring\" x=\"").append(x)
                    .append("\" y=\"").append(y).append("\"><point/></object>\n");
        }
        sb.append("  </objectgroup>\n");
        return sb.toString();
    }

    // ---- Sprite sheets ----

    private static void writeBolt() throws IOException {
        int[][] palette = {
                {0, 0, 0}, {1, 1, 1}, {2, 2, 2}, {4, 4, 4},
                {6, 6, 6}, {0, 3, 6}, {2, 5, 7}, {7, 0, 0},
                {3, 0, 0}, {7, 7, 0}, {0, 7, 0}, {7, 4, 0},
                {4, 0, 7}, {0, 7, 7}, {5, 3, 1}, {7, 7, 7},
        };
        BufferedImage image = new BufferedImage(24, 64, BufferedImage.TYPE_INT_RGB);
        for (int frame = 0; frame < 2; frame++) {
            int oy = frame * 32;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 24; x++) {
                image.setRGB(x, oy + y, rgb8Of(palette, boltPixel(x, y, frame)));
            }
        }
        writePng(image, "bolt.png");
        writeSheetYaml("bolt-sheet.yaml",
                """
                # Character sheet: paletteLine 0 + piece paletteIndex 0 => rendered CRAM line
                # (0 + 0) & 3 = 0. Bolt's runtime colors come from this sheet's own 16-color
                # palette below, carried into the .ggfp BASE palette and loaded over CRAM
                # line 0 by CharacterDefinition's palette supplier -- NOT from the level
                # GPAL's line 0 (which is placeholder data).
                """,
                0, palette, List.of(
                frameYaml(30, pieceYaml(0, 0, 24, 32, -12, -16, 0)),
                frameYaml(30, pieceYaml(0, 32, 24, 32, -12, -16, 0))));
    }

    private static int boltPixel(int x, int y, int frame) {
        int cx = 12, cy = 20, r = 9;
        int dx = x - cx, dy = y - cy;
        int d2 = dx * dx + dy * dy;
        if (d2 <= r * r) {
            if (d2 >= (r - 1) * (r - 1)) return 1; // outline
            if (dy < -3) return 4; // highlight
            if (dx > 2) return 2; // shadow
            return 3; // mid
        }
        if (x >= cx - 1 && x <= cx && y >= 2 && y < cy - r) return 1; // antenna rod
        int tipDx = x - cx, tipDy = y - 2;
        if (tipDx * tipDx + tipDy * tipDy <= 4) return frame == 0 ? 7 : 8; // antenna tip blink
        int eyeLDx = x - (cx - 3), eyeDy = y - (cy - 1);
        if (eyeLDx * eyeLDx + eyeDy * eyeDy <= 1) return 9;
        int eyeRDx = x - (cx + 3);
        if (eyeRDx * eyeRDx + eyeDy * eyeDy <= 1) return 9;
        return 0; // background
    }

    private static void writeZapbug() throws IOException {
        BufferedImage image = new BufferedImage(24, 32, BufferedImage.TYPE_INT_RGB);
        for (int frame = 0; frame < 2; frame++) {
            int oy = frame * 16;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 24; x++) {
                image.setRGB(x, oy + y, rgb8Of(LINE2, zapbugPixel(x, y, frame)));
            }
        }
        writePng(image, "zapbug.png");
        writeSheetYaml("zapbug-sheet.yaml", OBJECT_LINE_WARNING, 2, LINE2, List.of(
                frameYaml(16, pieceYaml(0, 0, 24, 16, -12, -8, 0)),
                frameYaml(16, pieceYaml(0, 16, 24, 16, -12, -8, 0))));
    }

    /** Returns LINE2 indices. */
    private static int zapbugPixel(int x, int y, int frame) {
        int cx = 12, cy = 8;
        double dx = (x - cx) / 9.0, dy = (y - cy) / 6.0;
        double d2 = dx * dx + dy * dy;
        if (d2 <= 1.0) {
            if (d2 >= 0.75) return 6; // dark red outline
            return d2 <= 0.35 ? 4 : 5; // bright core / mid red
        }
        // Legs: alternate diagonals per frame for a simple 2-frame walk cycle.
        int legPhase = frame == 0 ? 1 : -1;
        for (int leg = -1; leg <= 1; leg += 2) {
            int lx = cx + leg * 10;
            int ly = cy + legPhase * leg;
            if (Math.abs(x - lx) <= 1 && Math.abs(y - ly) <= 6) return frame == 0 ? 7 : 8; // gray legs
        }
        int eyeDx = x - cx, eyeDy = y - (cy - 2);
        if (eyeDx * eyeDx + eyeDy * eyeDy <= 1) return 9; // yellow eye
        return 0;
    }

    private static void writeSpringpad() throws IOException {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int frame = 0; frame < 2; frame++) {
            int oy = frame * 16;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 32; x++) {
                image.setRGB(x, oy + y, rgb8Of(LINE2, springpadPixel(x, y, frame)));
            }
        }
        writePng(image, "springpad.png");
        writeSheetYaml("springpad-sheet.yaml", OBJECT_LINE_WARNING, 2, LINE2, List.of(
                frameYaml(4, pieceYaml(0, 0, 32, 16, -16, -8, 0)),
                frameYaml(8, pieceYaml(0, 16, 32, 16, -16, -8, 0))));
    }

    /** Returns LINE2 indices. */
    private static int springpadPixel(int x, int y, int frame) {
        boolean edge = x == 0 || x == 31 || y == 0 || y == 15;
        if (edge) return 1; // near-black outline
        // Base plate: bottom 4 rows always solid.
        if (y >= 12) return (y == 12) ? 4 : 11; // red accent stripe over mid-gray plate
        // Coil: compressed (frame 0) fills fewer rows than extended (frame 1).
        int coilTop = frame == 0 ? 8 : 2;
        if (y >= coilTop && y < 12) {
            boolean stripe = ((x / 2) + y) % 2 == 0;
            return stripe ? (frame == 0 ? 10 : 9) : 8; // yellow coil stripes over light gray
        }
        return 0;
    }

    private static void writeRing() throws IOException {
        BufferedImage image = new BufferedImage(16, 64, BufferedImage.TYPE_INT_RGB);
        for (int frame = 0; frame < 4; frame++) {
            int oy = frame * 16;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                image.setRGB(x, oy + y, rgb8Of(LINE2, ringPixel(x, y, frame)));
            }
        }
        writePng(image, "ring.png");
        writeSheetYaml("ring-sheet.yaml", OBJECT_LINE_WARNING, 2, LINE2, List.of(
                frameYaml(6, pieceYaml(0, 0, 16, 16, -8, -8, 0)),
                frameYaml(6, pieceYaml(0, 16, 16, 16, -8, -8, 0)),
                frameYaml(6, pieceYaml(0, 32, 16, 16, -8, -8, 0)),
                frameYaml(6, pieceYaml(0, 48, 16, 16, -8, -8, 0))));
    }

    /** Returns LINE2 indices. */
    private static int ringPixel(int x, int y, int frame) {
        // 4-frame spin illusion: ellipse width narrows toward a thin edge-on line then back out.
        double[] halfWidths = {6.0, 3.5, 1.2, 3.5};
        double halfWidth = halfWidths[frame];
        double cx = 8, cy = 8, halfHeight = 6.0;
        double dx = (x - cx) / halfWidth, dy = (y - cy) / halfHeight;
        double d2 = dx * dx + dy * dy;
        if (d2 > 1.0) return 0;
        double innerScale = halfWidth <= 2.0 ? 0.4 : 0.6;
        double idx = (x - cx) / (halfWidth * innerScale), idy = (y - cy) / (halfHeight * innerScale);
        if (idx * idx + idy * idy <= 1.0) return 0; // hollow center
        return (x + y) % 2 == 0 ? 2 : 3; // light/dark gold dither
    }

    private static int rgb8Of(int[][] palette, int index) {
        int[] t = palette[index];
        return rgb8(t[0], t[1], t[2]);
    }

    private static void writePng(BufferedImage image, String name) throws IOException {
        Path out = MOD_DIR.resolve(name);
        Files.deleteIfExists(out);
        if (!ImageIO.write(image, "png", out.toFile())) throw new IOException("No PNG writer for " + name);
    }

    private static String pieceYaml(int sourceX, int sourceY, int w, int h, int xOff, int yOff, int paletteIndex) {
        return "{ sourceX: %d, sourceY: %d, widthPixels: %d, heightPixels: %d, xOffset: %d, yOffset: %d, "
                .formatted(sourceX, sourceY, w, h, xOff, yOff)
                + "hFlip: false, vFlip: false, paletteIndex: %d, priority: false }".formatted(paletteIndex);
    }

    private static String frameYaml(int delay, String piece) {
        return "  - delay: %d\n    pieces:\n      - %s\n".formatted(delay, piece);
    }

    private static void writeSheetYaml(String fileName, String headerComment, int paletteLine,
                                       int[][] palette, List<String> frames) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(headerComment);
        sb.append("formatVersion: 1\n");
        sb.append("paletteLine: ").append(paletteLine).append('\n');
        sb.append("palette: [");
        for (int i = 0; i < palette.length; i++) {
            if (i > 0) sb.append(", ");
            int[] t = palette[i];
            sb.append('"').append(hex(rgb8(t[0], t[1], t[2]))).append('"');
        }
        sb.append("]\n");
        sb.append("frames:\n");
        for (String frame : frames) sb.append(frame);
        Files.writeString(MOD_DIR.resolve(fileName), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String hex(int rgb) {
        return "#%06X".formatted(rgb);
    }

    // ---- Audio ----

    /** Short 16-bit mono 22050Hz square-wave blip: {@code cycles} full periods of {@code samplesPerCycle}. */
    private static void writeSfx(Path out, int samplesPerCycle, int approxFrequencyHz, int cycles) throws IOException {
        int frames = samplesPerCycle * cycles;
        int amplitude = (int) (0.5 * 32767);
        writeWav(out, 22050, frames, n -> {
            int phase = n % samplesPerCycle;
            return phase < samplesPerCycle / 2 ? amplitude : -amplitude;
        });
    }

    /**
     * ~8s deterministic looping chiptune arpeggio: 8 notes/pattern x 2205 frames each (0.1s/note)
     * x 10 pattern repeats = 176400 frames = exactly 8.000s at 22050Hz. Each note's sample count
     * (2205) is an exact multiple of its own samplesPerCycle, so every note is an integer number
     * of sine cycles (starts and ends near a zero crossing) -- the same style of transition
     * recurs at the loop point (end of track back to start), so the loop reads as just another
     * note change, not a special-case discontinuity.
     */
    private static void writeZoneThemeWav() throws IOException {
        int[] samplesPerCycle = {147, 105, 63, 49, 35, 21, 15, 9}; // 2205 / n; ascending arpeggio
        int frameLength = 2205;
        int repeats = 10;
        int totalFrames = frameLength * samplesPerCycle.length * repeats;
        double amplitude = 0.3 * 32767;
        writeWav(AUDIO_DIR.resolve("zone-theme.wav"), 22050, totalFrames, n -> {
            int withinPattern = n % (frameLength * samplesPerCycle.length);
            int noteIndex = withinPattern / frameLength;
            int withinNote = withinPattern % frameLength;
            int spc = samplesPerCycle[noteIndex];
            return (int) Math.round(amplitude * Math.sin(2.0 * Math.PI * withinNote / spc));
        });
    }

    @FunctionalInterface
    private interface SampleFn {
        int sample(int frameIndex);
    }

    private static void writeWav(Path out, int sampleRate, int frames, SampleFn fn) throws IOException {
        int dataSize = frames * 2;
        try (var stream = new BufferedOutputStream(Files.newOutputStream(out))) {
            writeAscii(stream, "RIFF");
            writeLE32(stream, 36 + dataSize);
            writeAscii(stream, "WAVE");
            writeAscii(stream, "fmt ");
            writeLE32(stream, 16);
            writeLE16(stream, 1); // PCM
            writeLE16(stream, 1); // mono
            writeLE32(stream, sampleRate);
            writeLE32(stream, sampleRate * 2); // byte rate
            writeLE16(stream, 2); // block align
            writeLE16(stream, 16); // bits per sample
            writeAscii(stream, "data");
            writeLE32(stream, dataSize);
            for (int i = 0; i < frames; i++) {
                int sample = Math.max(-32768, Math.min(32767, fn.sample(i)));
                writeLE16(stream, sample & 0xFFFF);
            }
        }
    }

    private static void writeAscii(java.io.OutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeLE16(java.io.OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void writeLE32(java.io.OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeAudioManifest() throws IOException {
        String yaml = """
                formatVersion: 1
                tracks:
                  - id: zone-theme
                    assetPath: audio/zone-theme.ogg
                    loop: true
                    loopStartFrame: 0
                    gain: 1.0
                    tempoEffects: false
                sfx:
                  - id: jump2
                    assetPath: audio/jump2.wav
                    gain: 1.0
                  - id: hit
                    assetPath: audio/hit.wav
                    gain: 1.0
                  - id: spring
                    assetPath: audio/spring.wav
                    gain: 1.0
                """;
        Files.writeString(AUDIO_DIR.resolve("audio-manifest.yaml"), yaml, StandardCharsets.UTF_8);
    }

    private static byte[] binary(IoWriter writer) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
