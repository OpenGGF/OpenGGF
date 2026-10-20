package com.openggf.game.sonic3k.objects;

import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.Arrays;

/** Packed-byte translation of {@code sub_246DA} for MGZ's single 128x64 source bank. */
final class MgzEndBossArtScaler {
    static final int SOURCE_WIDTH = 0x80;
    static final int SOURCE_HEIGHT = 0x40;
    private static final int SOURCE_ROW_BYTES = SOURCE_WIDTH / 2;

    private MgzEndBossArtScaler() { }

    static void scale(byte[] source, int scaleStep, SpriteMappingFrame mapping, Pattern[] target) {
        int modifiedScale = (scaleStep & 0x7F) + 4;
        Extents extents = extents(mapping);
        for (Pattern pattern : target) if (pattern != null) pattern.clear();
        byte[] row = new byte[SOURCE_ROW_BYTES];
        int sourceRow = 0;
        int verticalFraction = 0;
        int integerStep = modifiedScale >>> 3;
        int fractionalStep = (modifiedScale & 7) << 13;
        for (int outputY = 0; sourceRow < SOURCE_HEIGHT; outputY++) {
            Arrays.fill(row, (byte) 0);
            scaleRow(source, sourceRow * SOURCE_ROW_BYTES, modifiedScale, row);
            copyRow(row, outputY, mapping, extents, target);

            // loc_24778 and the specialized row loops: the vertical d3 word
            // survives the horizontal accumulator reset through SWAPs.
            int sum = verticalFraction + fractionalStep;
            verticalFraction = sum & 0xFFFF;
            sourceRow += integerStep + (sum >>> 16);
        }
    }

    private static void scaleRow(byte[] source, int start, int scale, byte[] row) {
        int input = start;
        int output = 0;
        switch (scale) {
            case 4 -> // loc_24B6A: sixteen longword copies.
                    System.arraycopy(source, start, row, 0, SOURCE_ROW_BYTES);
            case 5 -> {
                // loc_24B4A / loc_24B32: six pairs of five-byte groups.
                for (int group = 0; group < 12; group++) {
                    row[output++] = source[input++];
                    row[output++] = source[input++];
                    row[output++] = merge(source, input, input + 1);
                    input += 2;
                    row[output++] = source[input++];
                }
                row[output++] = source[input++];
                row[output++] = source[input++];
                row[output] = merge(source, input, input + 1);
            }
            case 6 -> {
                // loc_24AA4: ten copy/merge/copy/merge groups, then the
                // loc_24A56 copy/merge/copy tail (its last byte is unmasked).
                for (int group = 0; group < 10; group++) {
                    for (int pair = 0; pair < 2; pair++) {
                        row[output++] = source[input++];
                        row[output++] = merge(source, input, input + 1);
                        input += 2;
                    }
                }
                row[output++] = source[input++];
                row[output++] = merge(source, input, input + 1);
                row[output] = source[input + 2];
            }
            case 7 -> {
                // loc_24A10: copy one byte, then merge three pairs.
                for (int group = 0; group < 9; group++) {
                    row[output++] = source[input++];
                    for (int pair = 0; pair < 3; pair++) {
                        row[output++] = merge(source, input, input + 1);
                        input += 2;
                    }
                }
                row[output] = source[input]; // loc_249D4 copies byte63 whole.
            }
            case 8, 9 -> {
                // loc_24984 / loc_24906 take HIGH(first)|LOW(second),
                // not the high nibbles of both source bytes.
                int groups = scale == 8 ? 8 : 7;
                for (int group = 0; group < groups; group++) {
                    for (int pair = 0; pair < 4; pair++) {
                        row[output++] = merge(source, input, input + 1);
                        input += 2;
                    }
                    if (scale == 9) input++;
                }
                if (scale == 9) row[output] = (byte) (source[input] & 0xF0);
            }
            default -> scaleGenericRow(source, start, scale, row);
        }
    }

    private static byte merge(byte[] source, int highIndex, int lowIndex) {
        return (byte) ((source[highIndex] & 0xF0) | (source[lowIndex] & 0x0F));
    }

    private static void scaleGenericRow(byte[] source, int start, int scale, byte[] row) {
        // loc_2472A's DIVU/LSR sequence gives floor(64/scale) full
        // eight-pixel blocks; a remaining partial block uses sub_24802.
        int fullBlocks = SOURCE_ROW_BYTES / scale;
        int fullSamples = fullBlocks * 8;
        int samples = fullSamples + (fullBlocks * scale < SOURCE_ROW_BYTES ? 8 : 0);
        int index = 0;
        int fraction = 0;
        int integerStep = scale >>> 3;
        int fractionalStep = (scale & 7) << 13;
        for (int sample = 0; sample < samples; sample++) {
            int packed = index < SOURCE_ROW_BYTES ? Byte.toUnsignedInt(source[start + index]) : 0;
            int sum = fraction + fractionalStep;
            fraction = sum & 0xFFFF;
            index = (index + integerStep + (sum >>> 16)) & 0xFFFF;

            // Only sub_24802 clears the JUST READ sample when the NEW d2
            // reaches $40. loc_2479C's full blocks retain their last sample.
            // Reads beyond this bank's row cannot survive that tail check.
            if (sample >= fullSamples && index >= SOURCE_ROW_BYTES) packed = 0;
            int mask = (sample & 1) == 0 ? 0xF0 : 0x0F;
            row[sample >>> 1] |= (byte) (packed & mask);
        }
    }

    private static void copyRow(byte[] row, int outputY, SpriteMappingFrame mapping,
                                Extents extents, Pattern[] target) {
        // Native output advances $80 bytes per tile column and starts a new
        // bank every32 rows. Map_ScaledArt expresses those banks as pieces
        // with four tiles per column; preserve the supplied tile indices.
        for (SpriteMappingPiece piece : mapping.pieces()) {
            int localY = outputY - (piece.yOffset() - extents.minY);
            if (localY < 0 || localY >= piece.heightTiles() * 8) continue;
            for (int tileX = 0; tileX < piece.widthTiles(); tileX++) {
                int tile = piece.tileIndex() + tileX * piece.heightTiles() + (localY >>> 3);
                if (tile < 0 || tile >= target.length || target[tile] == null) continue;
                int outputX = piece.xOffset() - extents.minX + tileX * 8;
                for (int x = 0; x < 8; x++) {
                    int pixelX = outputX + x;
                    int packed = pixelX < SOURCE_WIDTH ? Byte.toUnsignedInt(row[pixelX >>> 1]) : 0;
                    int pixel = (pixelX & 1) == 0 ? packed >>> 4 : packed & 0x0F;
                    target[tile].setPixel(x, localY & 7, (byte) pixel);
                }
            }
        }
    }

    private static Extents extents(SpriteMappingFrame frame) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (SpriteMappingPiece piece : frame.pieces()) {
            minX = Math.min(minX, piece.xOffset());
            minY = Math.min(minY, piece.yOffset());
        }
        return new Extents(minX == Integer.MAX_VALUE ? 0 : minX, minY == Integer.MAX_VALUE ? 0 : minY);
    }

    private record Extents(int minX, int minY) { }
}
