package com.openggf.game.preview;

import com.openggf.data.Rom;
import com.openggf.data.compression.EnigmaReader;
import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePieceRenderer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;

/** CPU raster and ROM decoding primitives shared by game-owned title previews. */
public final class TitlePreviewRaster {
    private TitlePreviewRaster() { }

    @FunctionalInterface
    public interface PixelClip {
        boolean visible(int x, int y);
    }

    public static Image composeTilemapImage(Pattern[] patterns,
                                     Palette[] palettes,
                                     int[] map,
                                     int widthTiles,
                                     int heightTiles) {
        return composeTilemapImage(patterns, palettes, map, widthTiles, heightTiles, 0);
    }

    public static Image composeTilemapImage(Pattern[] patterns,
                                     Palette[] palettes,
                                     int[] map,
                                     int widthTiles,
                                     int heightTiles,
                                     int tileIndexBase) {
        int width = widthTiles * Pattern.PATTERN_WIDTH;
        int height = heightTiles * Pattern.PATTERN_HEIGHT;
        byte[] rgba = new byte[width * height * 4];

        if (patterns == null || palettes == null || map == null) {
            return new Image(width, height, rgba);
        }

        int tileCount = Math.min(map.length, widthTiles * heightTiles);
        for (int tile = 0; tile < tileCount; tile++) {
            int word = map[tile];
            int patternIndex = (word & 0x7FF) - tileIndexBase;
            if (patternIndex < 0 || patternIndex >= patterns.length) {
                continue;
            }
            Pattern pattern = patterns[patternIndex];
            if (pattern == null) {
                continue;
            }

            int paletteIndex = (word >> 13) & 0x03;
            Palette palette = paletteIndex < palettes.length ? palettes[paletteIndex] : null;
            if (palette == null) {
                continue;
            }

            boolean hFlip = (word & 0x0800) != 0;
            boolean vFlip = (word & 0x1000) != 0;
            int tileX = tile % widthTiles;
            int tileY = tile / widthTiles;
            blitPattern(rgba, width, tileX * 8, tileY * 8, pattern, palette, hFlip, vFlip);
        }

        return new Image(width, height, rgba);
    }

    public static void overlaySpriteFrame(Image image,
                                   Pattern[] patterns,
                                   Palette[] palettes,
                                   SpriteMappingFrame frame,
                                   int originX,
                                   int originY,
                                   int tileIndexBase) {
        overlaySpriteFrameClipped(image, patterns, palettes, frame, originX, originY, tileIndexBase,
                (x, y) -> true);
    }

    public static void overlaySpriteFrameClipped(Image image,
                                          Pattern[] patterns,
                                          Palette[] palettes,
                                          SpriteMappingFrame frame,
                                          int originX,
                                          int originY,
                                          int tileIndexBase,
                                          PixelClip clip) {
        if (image == null || patterns == null || palettes == null || frame == null || frame.pieces() == null) {
            return;
        }
        for (int i = frame.pieces().size() - 1; i >= 0; i--) {
            overlaySpritePiece(image, patterns, palettes, frame.pieces().get(i), originX, originY, tileIndexBase, clip);
        }
    }

    public static Image copyImage(Image image) {
        return new Image(image.width(), image.height(), Arrays.copyOf(image.rgba(), image.rgba().length));
    }

    public static Image shiftImage(Image source, int deltaX, int deltaY) {
        byte[] shifted = new byte[source.rgba().length];
        for (int y = 0; y < source.height(); y++) {
            int destY = y + deltaY;
            if (destY < 0 || destY >= source.height()) {
                continue;
            }
            for (int x = 0; x < source.width(); x++) {
                int destX = x + deltaX;
                if (destX < 0 || destX >= source.width()) {
                    continue;
                }
                int sourceOffset = ((y * source.width()) + x) * 4;
                int destOffset = ((destY * source.width()) + destX) * 4;
                shifted[destOffset] = source.rgba()[sourceOffset];
                shifted[destOffset + 1] = source.rgba()[sourceOffset + 1];
                shifted[destOffset + 2] = source.rgba()[sourceOffset + 2];
                shifted[destOffset + 3] = source.rgba()[sourceOffset + 3];
            }
        }
        return new Image(source.width(), source.height(), shifted);
    }

    public static Image scaleImageIntoNativeCanvas(Image source, int scaledWidth, int scaledHeight) {
        int width = source.width();
        int height = source.height();
        byte[] scaled = new byte[source.rgba().length];
        int offsetX = (width - scaledWidth) / 2;
        int offsetY = (height - scaledHeight) / 2;
        double scaleX = width / (double) scaledWidth;
        double scaleY = height / (double) scaledHeight;
        for (int y = 0; y < scaledHeight; y++) {
            double sourceY0 = y * scaleY;
            double sourceY1 = (y + 1) * scaleY;
            for (int x = 0; x < scaledWidth; x++) {
                double sourceX0 = x * scaleX;
                double sourceX1 = (x + 1) * scaleX;
                int destOffset = (((offsetY + y) * width) + offsetX + x) * 4;
                writeAreaSample(source, sourceX0, sourceX1, sourceY0, sourceY1, scaled, destOffset);
            }
        }
        return new Image(width, height, scaled);
    }

    public static int[] loadEnigmaMap(Rom rom, int address, int startingArtTile, int readSize) throws IOException {
        byte[] compressed = rom.readBytes(address, readSize);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             ReadableByteChannel channel = Channels.newChannel(bais)) {
            byte[] decompressed = EnigmaReader.decompress(channel, startingArtTile);
            int[] words = new int[decompressed.length / 2];
            ByteBuffer buf = ByteBuffer.wrap(decompressed);
            for (int i = 0; i < words.length; i++) {
                words[i] = buf.getShort() & 0xFFFF;
            }
            return words;
        }
    }

    public static Palette[] loadPaletteLines(byte[] data) {
        int lines = data.length / Palette.PALETTE_SIZE_IN_ROM;
        Palette[] palettes = new Palette[lines];
        for (int line = 0; line < lines; line++) {
            palettes[line] = loadPalette(Arrays.copyOfRange(data,
                    line * Palette.PALETTE_SIZE_IN_ROM,
                    (line + 1) * Palette.PALETTE_SIZE_IN_ROM));
        }
        return palettes;
    }

    public static Palette loadPalette(byte[] data) {
        Palette palette = new Palette();
        palette.fromSegaFormat(data);
        return palette;
    }

    public static Pattern[] appendPatternsAt(Pattern[] base, Pattern[] addition, int offset) {
        if (addition == null || addition.length == 0) {
            return base != null ? base : new Pattern[0];
        }
        int baseLength = base != null ? base.length : 0;
        int total = Math.max(baseLength, offset + addition.length);
        Pattern[] combined = new Pattern[total];
        if (base != null) {
            System.arraycopy(base, 0, combined, 0, base.length);
        }
        System.arraycopy(addition, 0, combined, offset, addition.length);
        return combined;
    }

    public static Image emptyTilemapImage(int widthTiles, int heightTiles) {
        return new Image(widthTiles * Pattern.PATTERN_WIDTH,
                heightTiles * Pattern.PATTERN_HEIGHT,
                new byte[widthTiles * Pattern.PATTERN_WIDTH * heightTiles * Pattern.PATTERN_HEIGHT * 4]);
    }

    public static void overlayTilemapRows(Image image,
                                           Pattern[] patterns,
                                           Palette[] palettes,
                                           int[] map,
                                           int widthTiles,
                                           int startTileRow,
                                           int endTileRow,
                                           int tileIndexBase) {
        overlayTilemapRows(image, patterns, palettes, map, widthTiles, startTileRow, endTileRow,
                tileIndexBase, 0, 0);
    }

    public static void overlayTilemapRows(Image image,
                                           Pattern[] patterns,
                                           Palette[] palettes,
                                           int[] map,
                                           int widthTiles,
                                           int startTileRow,
                                           int endTileRow,
                                           int tileIndexBase,
                                           int pixelOffsetX,
                                           int pixelOffsetY) {
        if (patterns == null || palettes == null || map == null) {
            return;
        }
        int tileCount = Math.min(map.length, widthTiles * endTileRow);
        for (int tile = Math.max(0, startTileRow) * widthTiles; tile < tileCount; tile++) {
            int word = map[tile];
            int patternIndex = (word & 0x7FF) - tileIndexBase;
            if (patternIndex < 0 || patternIndex >= patterns.length || patterns[patternIndex] == null) {
                continue;
            }
            int paletteIndex = (word >> 13) & 0x03;
            Palette palette = paletteIndex < palettes.length ? palettes[paletteIndex] : null;
            if (palette == null) {
                continue;
            }
            blitPattern(image.rgba(), image.width(),
                    pixelOffsetX + (tile % widthTiles) * 8,
                    pixelOffsetY + (tile / widthTiles) * 8,
                    patterns[patternIndex],
                    palette,
                    (word & 0x0800) != 0,
                    (word & 0x1000) != 0);
        }
    }

    public static int[] readWords(Rom rom, int address, int count) throws IOException {
        byte[] bytes = rom.readBytes(address, count * 2);
        int[] words = new int[count];
        for (int i = 0; i < count; i++) {
            words[i] = ((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF);
        }
        return words;
    }

    private static void writeAreaSample(Image source,
                                        double sourceX0,
                                        double sourceX1,
                                        double sourceY0,
                                        double sourceY1,
                                        byte[] dest,
                                        int destOffset) {
        int width = source.width();
        int height = source.height();
        byte[] rgba = source.rgba();
        int startX = Math.max(0, (int) Math.floor(sourceX0));
        int endX = Math.min(width, (int) Math.ceil(sourceX1));
        int startY = Math.max(0, (int) Math.floor(sourceY0));
        int endY = Math.min(height, (int) Math.ceil(sourceY1));
        double red = 0;
        double green = 0;
        double blue = 0;
        double alpha = 0;
        double coverage = 0;

        for (int sy = startY; sy < endY; sy++) {
            double overlapY = Math.min(sy + 1.0, sourceY1) - Math.max(sy, sourceY0);
            if (overlapY <= 0) {
                continue;
            }
            for (int sx = startX; sx < endX; sx++) {
                double overlapX = Math.min(sx + 1.0, sourceX1) - Math.max(sx, sourceX0);
                if (overlapX <= 0) {
                    continue;
                }
                double weight = overlapX * overlapY;
                int sourceOffset = ((sy * width) + sx) * 4;
                double sourceAlpha = (rgba[sourceOffset + 3] & 0xFF) / 255.0;
                red += (rgba[sourceOffset] & 0xFF) * sourceAlpha * weight;
                green += (rgba[sourceOffset + 1] & 0xFF) * sourceAlpha * weight;
                blue += (rgba[sourceOffset + 2] & 0xFF) * sourceAlpha * weight;
                alpha += sourceAlpha * weight;
                coverage += weight;
            }
        }

        if (coverage <= 0 || alpha <= 0) {
            return;
        }
        dest[destOffset] = roundedByte(red / alpha);
        dest[destOffset + 1] = roundedByte(green / alpha);
        dest[destOffset + 2] = roundedByte(blue / alpha);
        dest[destOffset + 3] = roundedByte((alpha / coverage) * 255.0);
    }

    private static byte roundedByte(double value) {
        int rounded = Math.max(0, Math.min(255, (int) Math.round(value)));
        return (byte) rounded;
    }

    private static void overlaySpritePiece(Image image,
                                           Pattern[] patterns,
                                           Palette[] palettes,
                                           SpriteMappingPiece piece,
                                           int originX,
                                           int originY,
                                           int tileIndexBase,
                                           PixelClip clip) {
        SpritePieceRenderer.renderPiece(piece, originX, originY, -tileIndexBase, -1, false, false,
                (patternIndex, hFlip, vFlip, paletteIndex, drawX, drawY) -> {
                    Palette palette = paletteIndex < palettes.length ? palettes[paletteIndex] : null;
                    if (palette == null || patternIndex < 0 || patternIndex >= patterns.length
                            || patterns[patternIndex] == null) {
                        return;
                    }
                    blitPattern(image.rgba(), image.width(), drawX, drawY,
                            patterns[patternIndex], palette, hFlip, vFlip, clip);
                });
    }

    private static void blitPattern(byte[] rgba,
                                    int imageWidth,
                                    int dstX,
                                    int dstY,
                                    Pattern pattern,
                                    Palette palette,
                                    boolean hFlip,
                                    boolean vFlip) {
        blitPattern(rgba, imageWidth, dstX, dstY, pattern, palette, hFlip, vFlip, (x, y) -> true);
    }

    private static void blitPattern(byte[] rgba,
                                    int imageWidth,
                                    int dstX,
                                    int dstY,
                                    Pattern pattern,
                                    Palette palette,
                                    boolean hFlip,
                                    boolean vFlip,
                                    PixelClip clip) {
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            int sourceY = vFlip ? Pattern.PATTERN_HEIGHT - 1 - y : y;
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                int sourceX = hFlip ? Pattern.PATTERN_WIDTH - 1 - x : x;
                int colorIndex = pattern.getPixel(sourceX, sourceY) & 0x0F;
                if (colorIndex == 0) {
                    continue;
                }
                int outX = dstX + x;
                int outY = dstY + y;
                if (outX < 0 || outY < 0 || outX >= imageWidth || outY >= rgba.length / 4 / imageWidth) {
                    continue;
                }
                if (clip != null && !clip.visible(outX, outY)) {
                    continue;
                }
                Palette.Color color = palette.getColor(colorIndex);
                int offset = ((outY * imageWidth) + outX) * 4;
                rgba[offset] = color.r;
                rgba[offset + 1] = color.g;
                rgba[offset + 2] = color.b;
                rgba[offset + 3] = (byte) 0xFF;
            }
        }
    }
}
