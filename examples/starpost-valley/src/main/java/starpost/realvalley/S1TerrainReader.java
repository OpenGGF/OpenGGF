package starpost.realvalley;

import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;

/** Copies the arrays the encoder needs out of a decoded Sonic 1 level (any engine {@link Level}). */
public final class S1TerrainReader {
    private S1TerrainReader() {
    }

    public static S1Terrain read(Level level) {
        byte[][] pixels = new byte[level.getPatternCount()][64];
        for (int p = 0; p < pixels.length; p++) {
            Pattern pattern = level.getPattern(p);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    pixels[p][y * 8 + x] = pattern == null ? 0 : (byte) (pattern.getPixel(x, y) & 0xF);
                }
            }
        }
        int[][] chunks = new int[level.getChunkCount()][4];
        int[] collision = new int[chunks.length];
        for (int c = 0; c < chunks.length; c++) {
            Chunk chunk = level.getChunk(c);
            for (int i = 0; i < 4; i++) {
                chunks[c][i] = chunk.getPatternDesc(i % 2, i / 2).get() & 0xFFFF;
            }
            collision[c] = chunk.getSolidTileIndex();
        }
        int[][] blocks = new int[level.getBlockCount()][256];
        for (int b = 0; b < blocks.length; b++) {
            Block block = level.getBlock(b);
            for (int i = 0; i < 256; i++) {
                blocks[b][i] = block.getChunkDesc(i % 16, i / 16).get() & 0xFFFF;
            }
        }
        int profiles = level.getSolidTileCount();
        byte[] heights = new byte[profiles * 16];
        byte[] widths = new byte[profiles * 16];
        byte[] angles = new byte[profiles];
        for (int t = 0; t < profiles; t++) {
            SolidTile tile = level.getSolidTile(t);
            System.arraycopy(tile.heights, 0, heights, t * 16, 16);
            System.arraycopy(tile.widths, 0, widths, t * 16, 16);
            angles[t] = tile.getAngle();
        }
        int[] palette = new int[64];
        for (int line = 0; line < 4; line++) {
            Palette source = level.getPalette(line);
            for (int c = 0; c < 16; c++) {
                palette[line * 16 + c] = segaWord(source.getColor(c));
            }
        }
        int bgWidth = level.getLayerWidthBlocks(1);
        int bgHeight = level.getLayerHeightBlocks(1);
        int[] bgLayout = new int[bgWidth * bgHeight];
        for (int y = 0; y < bgHeight; y++) {
            for (int x = 0; x < bgWidth; x++) {
                bgLayout[y * bgWidth + x] = level.getMap().getValue(1, x, y) & 0xFF;
            }
        }
        return new S1Terrain(pixels, chunks, collision, blocks, heights, widths, angles, palette,
                bgWidth, bgHeight, bgLayout);
    }

    /** Inverts the engine's 3-bit-per-channel colour expansion back to a Genesis colour word. */
    static int segaWord(Palette.Color color) {
        int r = ((color.r & 0xFF) * 7 + 127) / 255;
        int g = ((color.g & 0xFF) * 7 + 127) / 255;
        int b = ((color.b & 0xFF) * 7 + 127) / 255;
        return (b << 9) | (g << 5) | (r << 1);
    }
}
