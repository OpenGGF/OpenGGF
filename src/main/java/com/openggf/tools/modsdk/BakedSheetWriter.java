package com.openggf.tools.modsdk;

import com.openggf.level.Pattern;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.render.SpriteMappingPiece;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/** Writes the canonical baked object-sheet v1 container used by the mod SDK. */
public final class BakedSheetWriter {
    private static final byte[] MAGIC = {'G', 'G', 'F', 'S'};
    private static final int VERSION = 1;

    private BakedSheetWriter() {
    }

    public static byte[] write(BakedSheetReader.BakedSheet sheet) throws IOException {
        return write(sheet, BakedSheetReader.Limits.production());
    }

    public static byte[] write(BakedSheetReader.BakedSheet sheet,
                               BakedSheetReader.Limits limits) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(sheet, output, limits);
        return output.toByteArray();
    }

    public static void write(BakedSheetReader.BakedSheet sheet, OutputStream output) throws IOException {
        write(sheet, output, BakedSheetReader.Limits.production());
    }

    public static void write(BakedSheetReader.BakedSheet sheet, OutputStream output,
                             BakedSheetReader.Limits limits) throws IOException {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(limits, "limits");
        Pattern[] patterns = sheet.patterns();
        List<BakedSheetReader.Frame> frames = sheet.frames();
        validate(patterns, frames, sheet.palette(), limits);

        DataOutputStream data = new DataOutputStream(output);
        data.write(MAGIC);
        data.writeShort(VERSION);
        data.writeInt(patterns.length);
        for (Pattern pattern : patterns) {
            writePattern(data, pattern);
        }
        data.writeShort(frames.size());
        for (BakedSheetReader.Frame frame : frames) {
            data.writeShort(frame.delay());
            List<SpriteMappingPiece> pieces = frame.mapping().pieces();
            data.writeShort(pieces.size());
            for (SpriteMappingPiece piece : pieces) {
                writePiece(data, piece);
            }
        }
        if (sheet.palette().isEmpty()) {
            data.writeByte(0);
        } else {
            BakedSheetReader.Palette palette = sheet.palette().orElseThrow();
            data.writeByte(1);
            data.writeByte(palette.line());
            for (int color : palette.colors()) {
                data.writeShort(color);
            }
        }
    }

    private static void validate(Pattern[] patterns, List<BakedSheetReader.Frame> frames,
                                 java.util.Optional<BakedSheetReader.Palette> palette,
                                 BakedSheetReader.Limits limits) {
        require(patterns.length <= limits.maxSheetPatterns(), "pattern count exceeds limit");
        for (Pattern pattern : patterns) {
            Objects.requireNonNull(pattern, "pattern");
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    require((pattern.getPixel(x, y) & 0xff) <= 0x0f,
                            "pattern pixels must fit 4 bits");
                }
            }
        }
        require(frames.size() <= limits.maxSheetFrames() && frames.size() <= 0xffff,
                "frame count exceeds limit");
        long byteCount = 4 + 2 + 4 + (long) patterns.length * Pattern.PATTERN_SIZE_IN_ROM + 2 + 1;
        long totalPieces = 0;
        for (BakedSheetReader.Frame frame : frames) {
            require(frame.delay() >= 0 && frame.delay() <= 0xffff, "delay must fit u16");
            List<SpriteMappingPiece> pieces = frame.mapping().pieces();
            require(pieces.size() <= 0xffff, "piece count must fit u16");
            totalPieces += pieces.size();
            require(totalPieces <= limits.maxSheetPieces(), "piece count exceeds limit");
            byteCount += 4L + 12L * pieces.size();
            for (SpriteMappingPiece piece : pieces) {
                validatePiece(piece, patterns.length);
            }
        }
        if (palette.isPresent()) {
            BakedSheetReader.Palette value = palette.orElseThrow();
            require(value.line() >= 0 && value.line() <= 3, "palette line must be in 0..3");
            int[] colors = value.colors();
            require(colors.length == 16, "palette must contain exactly 16 colors");
            for (int color : colors) {
                require(color >= 0 && color <= 0xffff, "palette color must fit u16");
            }
            byteCount += 33;
        }
        require(byteCount <= limits.maxAssetBytes(), "container exceeds byte limit");
    }

    private static void validatePiece(SpriteMappingPiece piece, int patternCount) {
        Objects.requireNonNull(piece, "piece");
        require(piece.xOffset() >= Short.MIN_VALUE && piece.xOffset() <= Short.MAX_VALUE,
                "x offset must fit s16");
        require(piece.yOffset() >= Short.MIN_VALUE && piece.yOffset() <= Short.MAX_VALUE,
                "y offset must fit s16");
        require(piece.widthTiles() > 0 && piece.widthTiles() <= 0xff,
                "width must fit nonzero u8");
        require(piece.heightTiles() > 0 && piece.heightTiles() <= 0xff,
                "height must fit nonzero u8");
        require(piece.tileIndex() >= 0, "tile index must be nonnegative");
        require(piece.paletteIndex() >= 0 && piece.paletteIndex() <= 0xff,
                "palette index must fit u8");
        long endExclusive = (long) piece.tileIndex() + (long) piece.widthTiles() * piece.heightTiles();
        require(endExclusive <= patternCount, "piece tile span exceeds pattern count");
    }

    private static void writePattern(DataOutputStream data, Pattern pattern) throws IOException {
        Objects.requireNonNull(pattern, "pattern");
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x += 2) {
                int high = pattern.getPixel(x, y) & 0xff;
                int low = pattern.getPixel(x + 1, y) & 0xff;
                require(high <= 0x0f && low <= 0x0f, "pattern pixels must fit 4 bits");
                data.writeByte((high << 4) | low);
            }
        }
    }

    private static void writePiece(DataOutputStream data, SpriteMappingPiece piece) throws IOException {
        data.writeShort(piece.xOffset());
        data.writeShort(piece.yOffset());
        data.writeByte(piece.widthTiles());
        data.writeByte(piece.heightTiles());
        data.writeInt(piece.tileIndex());
        int flags = (piece.hFlip() ? 1 : 0) | (piece.vFlip() ? 2 : 0) | (piece.priority() ? 4 : 0);
        data.writeByte(flags);
        data.writeByte(piece.paletteIndex());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("Invalid baked sheet: " + message);
        }
    }
}
