package com.openggf.level.objects;

import com.openggf.game.ModApi;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** Reads the bounded, engine-neutral baked object-sheet v1 container. */
@ModApi
public final class BakedSheetReader {
    private static final byte[] MAGIC = {'G', 'G', 'F', 'S'};
    private static final int VERSION = 1;
    private static final int KNOWN_FLAGS = 0x07;

    private BakedSheetReader() {
    }

    public static BakedSheet read(byte[] bytes) throws IOException {
        return read(bytes, ModInputLimits.production());
    }

    public static BakedSheet read(byte[] bytes, ModInputLimits limits) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        return read(new java.io.ByteArrayInputStream(bytes), Limits.from(limits));
    }

    public static BakedSheet read(InputStream input) throws IOException {
        return read(input, ModInputLimits.production());
    }

    public static BakedSheet read(InputStream input, ModInputLimits limits) throws IOException {
        return read(input, Limits.from(limits));
    }

    public static BakedSheet read(InputStream input, Limits limits) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(limits, "limits");
        LimitedInputStream limited = new LimitedInputStream(input, limits.maxAssetBytes());
        DataInputStream data = new DataInputStream(limited);
        try {
            byte[] magic = new byte[MAGIC.length];
            data.readFully(magic);
            if (!Arrays.equals(MAGIC, magic)) {
                throw invalid("bad magic");
            }
            int version = data.readUnsignedShort();
            if (version != VERSION) {
                throw invalid("unsupported version " + version);
            }

            long rawPatternCount = Integer.toUnsignedLong(data.readInt());
            int patternCount = boundedCount(rawPatternCount, limits.maxSheetPatterns(), "pattern count");
            requireRemaining(limited, (long) patternCount * Pattern.PATTERN_SIZE_IN_ROM + 3,
                    "declared patterns cannot fit within byte limit");
            Pattern[] patterns = new Pattern[patternCount];
            byte[] encodedPattern = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            for (int i = 0; i < patternCount; i++) {
                data.readFully(encodedPattern);
                Pattern pattern = new Pattern();
                pattern.fromSegaFormat(encodedPattern);
                patterns[i] = pattern;
            }

            int frameCount = data.readUnsignedShort();
            if (frameCount > limits.maxSheetFrames()) {
                throw invalid("frame count exceeds limit");
            }
            requireRemaining(limited, (long) frameCount * 4 + 1,
                    "declared frames cannot fit within byte limit");
            List<Frame> frames = new ArrayList<>(frameCount);
            long totalPieces = 0;
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                int delay = data.readUnsignedShort();
                int pieceCount = data.readUnsignedShort();
                totalPieces += pieceCount;
                if (totalPieces > limits.maxSheetPieces()) {
                    throw invalid("piece count exceeds limit");
                }
                requireRemaining(limited, (long) pieceCount * 12 + 1,
                        "declared pieces cannot fit within byte limit");
                List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);
                for (int pieceIndex = 0; pieceIndex < pieceCount; pieceIndex++) {
                    pieces.add(readPiece(data, patternCount));
                }
                frames.add(new Frame(delay, new SpriteMappingFrame(List.copyOf(pieces))));
            }

            int hasPalette = data.readUnsignedByte();
            Optional<Palette> palette;
            if (hasPalette == 0) {
                palette = Optional.empty();
            } else if (hasPalette == 1) {
                int paletteLine = data.readUnsignedByte();
                if (paletteLine > 3) {
                    throw invalid("palette line must be in 0..3");
                }
                int[] colors = new int[16];
                for (int i = 0; i < colors.length; i++) {
                    colors[i] = data.readUnsignedShort();
                }
                palette = Optional.of(new Palette(paletteLine, colors));
            } else {
                throw invalid("hasPalette must be 0 or 1");
            }
            if (data.read() != -1) {
                throw invalid("trailing bytes");
            }
            return new BakedSheet(patterns, frames, palette);
        } catch (EOFException e) {
            throw invalid("truncated container", e);
        }
    }

    private static SpriteMappingPiece readPiece(DataInputStream data, int patternCount) throws IOException {
        int x = data.readShort();
        int y = data.readShort();
        int width = data.readUnsignedByte();
        int height = data.readUnsignedByte();
        long rawTileIndex = Integer.toUnsignedLong(data.readInt());
        int flags = data.readUnsignedByte();
        int paletteIndex = data.readUnsignedByte();
        if (width == 0 || height == 0) {
            throw invalid("piece dimensions must be nonzero");
        }
        if ((flags & ~KNOWN_FLAGS) != 0) {
            throw invalid("piece uses reserved flag bits");
        }
        if (rawTileIndex > Integer.MAX_VALUE) {
            throw invalid("tile index exceeds engine range");
        }
        long endExclusive = rawTileIndex + (long) width * height;
        if (endExclusive > patternCount) {
            throw invalid("piece tile span exceeds pattern count");
        }
        return new SpriteMappingPiece(x, y, width, height, (int) rawTileIndex,
                (flags & 1) != 0, (flags & 2) != 0, paletteIndex, (flags & 4) != 0);
    }

    private static int boundedCount(long value, int limit, String name) throws IOException {
        if (value > limit || value > Integer.MAX_VALUE) {
            throw invalid(name + " exceeds limit");
        }
        return (int) value;
    }

    private static void requireRemaining(LimitedInputStream input, long required, String message)
            throws IOException {
        if (required < 0 || required > input.remaining()) {
            throw invalid(message);
        }
    }

    private static IOException invalid(String message) {
        return new IOException("Invalid baked sheet: " + message);
    }

    private static IOException invalid(String message, Throwable cause) {
        return new IOException("Invalid baked sheet: " + message, cause);
    }

    @ModApi
    public record Frame(int delay, SpriteMappingFrame mapping) {
        public Frame {
            Objects.requireNonNull(mapping, "mapping");
            mapping = new SpriteMappingFrame(List.copyOf(
                    Objects.requireNonNull(mapping.pieces(), "mapping pieces")));
        }
    }

    @ModApi
    public record BakedSheet(Pattern[] patterns, List<Frame> frames, Optional<Palette> palette) {
        public BakedSheet {
            Pattern[] sourcePatterns = Objects.requireNonNull(patterns, "patterns");
            patterns = new Pattern[sourcePatterns.length];
            for (int i = 0; i < sourcePatterns.length; i++) {
                patterns[i] = copyPattern(Objects.requireNonNull(sourcePatterns[i], "pattern"));
            }
            frames = Objects.requireNonNull(frames, "frames").stream()
                    .map(frame -> new Frame(Objects.requireNonNull(frame, "frame").delay(), frame.mapping()))
                    .toList();
            palette = Objects.requireNonNull(palette, "palette");
        }

        @Override
        public Pattern[] patterns() {
            Pattern[] result = new Pattern[patterns.length];
            for (int i = 0; i < patterns.length; i++) {
                result[i] = copyPattern(patterns[i]);
            }
            return result;
        }

        /**
         * Builds the existing runtime sheet view. Per-frame delays remain available through
         * {@link #frames()}; the legacy sheet's single delay is zero to avoid silently choosing one.
         */
        public ObjectSpriteSheet toObjectSpriteSheet() {
            List<SpriteMappingFrame> mappings = frames.stream().map(Frame::mapping).toList();
            int paletteIndex = palette.map(Palette::line).orElse(0);
            return new ObjectSpriteSheet(patterns(), mappings, paletteIndex, 0);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BakedSheet that)
                    || patterns.length != that.patterns.length
                    || !frames.equals(that.frames)
                    || !palette.equals(that.palette)) {
                return false;
            }
            for (int i = 0; i < patterns.length; i++) {
                if (!patternEquals(patterns[i], that.patterns[i])) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = 1;
            for (Pattern pattern : patterns) result = 31 * result + patternHash(pattern);
            result = 31 * result + frames.hashCode();
            return 31 * result + palette.hashCode();
        }

        private static Pattern copyPattern(Pattern source) {
            Pattern copy = new Pattern();
            copy.copyFrom(source);
            return copy;
        }

        private static boolean patternEquals(Pattern first, Pattern second) {
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    if (first.getPixel(x, y) != second.getPixel(x, y)) return false;
                }
            }
            return true;
        }

        private static int patternHash(Pattern pattern) {
            int result = 1;
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    result = 31 * result + pattern.getPixel(x, y);
                }
            }
            return result;
        }
    }

    @ModApi
    public record Palette(int line, int[] colors) {
        public Palette {
            colors = Objects.requireNonNull(colors, "colors").clone();
        }

        @Override
        public int[] colors() {
            return colors.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Palette that && line == that.line && Arrays.equals(colors, that.colors);
        }

        @Override
        public int hashCode() {
            return 31 * line + Arrays.hashCode(colors);
        }
    }

    /** JDK-neutral immutable sheet limits shared with SDK-side writers. */
    @ModApi
    public record Limits(long maxAssetBytes, int maxSheetPatterns, int maxSheetFrames,
                         int maxSheetPieces) {
        public Limits {
            if (maxAssetBytes <= 0 || maxAssetBytes > ModInputLimits.DEFAULT_MAX_ASSET_BYTES
                    || maxSheetPatterns <= 0 || maxSheetPatterns > ModInputLimits.DEFAULT_MAX_SHEET_PATTERNS
                    || maxSheetFrames <= 0 || maxSheetFrames > ModInputLimits.DEFAULT_MAX_SHEET_FRAMES
                    || maxSheetPieces <= 0 || maxSheetPieces > ModInputLimits.DEFAULT_MAX_SHEET_PIECES) {
                throw new IllegalArgumentException("Baked-sheet limits must not exceed production limits");
            }
        }

        public static Limits production() {
            return from(ModInputLimits.production());
        }

        public static Limits from(ModInputLimits limits) {
            Objects.requireNonNull(limits, "limits");
            return new Limits(limits.maxAssetBytes(), limits.maxSheetPatterns(),
                    limits.maxSheetFrames(), limits.maxSheetPieces());
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        private long remaining() {
            return limit - count;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0 && ++count > limit) {
                throw invalid("container exceeds byte limit");
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0 && (count += read) > limit) {
                throw invalid("container exceeds byte limit");
            }
            return read;
        }
    }
}
