package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;

import java.nio.file.Path;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Finds where a level's layout draws given 8x8 tiles or palette colours, so a demo
 * capture or visual check can be pointed at an animated-tile range (AniPLC destination)
 * or an AnPal-cycled colour that is not visible from the level start.
 *
 * <p>Inputs: {@code --game}, {@code --rom}, {@code --zone}, {@code --act} (one-based, as
 * in {@link GameplayCaptureTool}), and at least one of {@code --tiles first-last} (hex
 * pattern indices) or {@code --colors line:c1,c2} (palette line 0-3 and colour indices).
 * Output: one line per 128px layout cell with the number of matching 8x8 placements,
 * for foreground (layer 0) and background (layer 1), in world pixel coordinates.
 *
 * <p>Originating task: HPZ bring-up demo captures (feature/ai-hpz-bring-up, 2026-09-16),
 * where AnPal_HPZ and AniPLC_HPZ were correct in tests but not visible near the start.
 *
 * <pre>
 * python3 tools/testing/maven_queue.py exec:java "-Dexec.mainClass=com.openggf.tools.LevelTileUsageLocatorTool" \
 *   "-Dexec.args=--game s3k --rom /abs/s3k.gen --zone hpz --act 2 --tiles 0x2D0-0x2DB --colors 3:1,2"
 * </pre>
 */
public final class LevelTileUsageLocatorTool {
    private static final int CELL = 128;
    private static final int CHUNK = 16;

    private LevelTileUsageLocatorTool() {
    }

    public static void main(String[] args) throws Exception {
        String game = "s3k";
        Path rom = null;
        String zone = null;
        Integer act = null;
        int firstTile = -1;
        int lastTile = -1;
        int colorLine = -1;
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--game" -> game = args[++i];
                case "--rom" -> rom = Path.of(args[++i]);
                case "--zone" -> zone = args[++i];
                case "--act" -> act = Integer.parseInt(args[++i]);
                case "--tiles" -> {
                    String[] range = args[++i].split("-");
                    firstTile = Integer.decode(range[0]);
                    lastTile = range.length > 1 ? Integer.decode(range[1]) : firstTile;
                }
                case "--colors" -> {
                    String[] parts = args[++i].split(":");
                    colorLine = Integer.parseInt(parts[0]);
                    for (String value : parts[1].split(",")) {
                        colors.add(Integer.decode(value));
                    }
                }
                default -> throw new IllegalArgumentException("Unknown flag: " + args[i]);
            }
        }
        if (zone == null || act == null || (firstTile < 0 && colorLine < 0)) {
            throw new IllegalArgumentException("--zone, --act and one of --tiles/--colors are required");
        }
        if (rom == null) {
            rom = TraceToolRomLocations.resolve(game, GameServices.configuration(), Path.of(""));
        }
        GameplayCaptureSession.Settings settings = new GameplayCaptureSession.Settings(
                320, "sonic", "", "off", null, null, null);
        try (GameplayCaptureSession session = new GameplayCaptureSession(settings)) {
            session.boot(rom, GameplayCaptureTool.ZoneIds.resolve(game, zone), act - 1, settings);
            Level level = GameServices.level().getCurrentLevel();
            for (int layer = 0; layer < 2; layer++) {
                report(level, layer, firstTile, lastTile, colorLine, colors);
            }
        }
    }

    private static void report(Level level, int layer, int firstTile, int lastTile,
                               int colorLine, java.util.Set<Integer> colors) {
        Map map = level.getMap();
        TreeMap<String, int[]> hits = new TreeMap<>();
        for (int cy = 0; cy < map.getHeight(); cy++) {
            for (int cx = 0; cx < map.getWidth(); cx++) {
                int blockIndex = Byte.toUnsignedInt(map.getValue(layer, cx, cy));
                if (blockIndex == 0 || blockIndex >= level.getBlockCount()) {
                    continue;
                }
                Block block = level.getBlock(blockIndex);
                int tileHits = 0;
                int colorHits = 0;
                for (int by = 0; by < block.getGridSide(); by++) {
                    for (int bx = 0; bx < block.getGridSide(); bx++) {
                        ChunkDesc chunkDesc = block.getChunkDesc(bx, by);
                        int chunkIndex = chunkDesc.getChunkIndex();
                        if (chunkIndex <= 0 || chunkIndex >= level.getChunkCount()) {
                            continue;
                        }
                        Chunk chunk = level.getChunk(chunkIndex);
                        for (int py = 0; py < 2; py++) {
                            for (int px = 0; px < 2; px++) {
                                PatternDesc desc = chunk.getPatternDesc(px, py);
                                int tile = desc.getPatternIndex();
                                if (firstTile >= 0 && tile >= firstTile && tile <= lastTile) {
                                    tileHits++;
                                }
                                if (colorLine >= 0 && desc.getPaletteIndex() == colorLine
                                        && tile < level.getPatternCount()
                                        && usesColor(level.getPattern(tile), colors)) {
                                    colorHits++;
                                }
                            }
                        }
                    }
                }
                if (tileHits > 0 || colorHits > 0) {
                    hits.put(String.format(Locale.ROOT, "%04X,%04X", cx * CELL, cy * CELL),
                            new int[]{tileHits, colorHits});
                }
            }
        }
        System.out.println("layer " + layer + (layer == 0 ? " (foreground)" : " (background)")
                + ": " + hits.size() + " cells");
        hits.forEach((cell, counts) -> System.out.println("  x,y=$" + cell.replace(",", ",$")
                + " tiles=" + counts[0] + " colors=" + counts[1]));
    }

    private static boolean usesColor(Pattern pattern, java.util.Set<Integer> colors) {
        if (pattern == null) {
            return false;
        }
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                if (colors.contains(pattern.getPixel(x, y) & 0xF)) {
                    return true;
                }
            }
        }
        return false;
    }
}
