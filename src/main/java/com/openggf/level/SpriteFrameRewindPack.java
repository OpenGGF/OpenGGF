package com.openggf.level;

import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatEntry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Rewind storage for one {@link SpritePresentation.Frame}. A checkpoint keeps
 * about 180 tiles per frame; as records that is roughly 80 bytes a tile plus the
 * boxed pattern-version keys. Packed, a tile is four ints and its repeated drawing
 * state is one row of a per-frame style table, so the live {@code Frame}/{@code Tile}
 * types stay unchanged and only the snapshot changes shape.
 *
 * <p>Tiles: {@code patternId, x bits, y bits, style} per tile. Styles: {@link #STYLE_INTS}
 * ints (palette, occlusion mask, width/height/ghost-alpha bits, row range, subject index,
 * enum ordinals and flags). Subjects keep their identity through a per-frame table.
 * Pattern versions are a sorted id array and four longs per id. Arrays are never
 * exposed or mutated after packing, so identical pieces are shared between checkpoints.
 */
final class SpriteFrameRewindPack {
    static final int TILE_INTS = 4;
    static final int STYLE_INTS = 9;
    private static final SpritePresentation.Layer[] LAYERS = SpritePresentation.Layer.values();
    private static final SpriteSatEntry.PixelOperation[] OPERATIONS = SpriteSatEntry.PixelOperation.values();
    private static final int[] NO_INTS = new int[0];
    private static final long[] NO_LONGS = new long[0];
    private static final SpritePresentation.Subject[] NO_SUBJECTS = new SpritePresentation.Subject[0];

    /** Immutable packed frame; equality is value equality of the frame it decodes to. */
    static final class Packed {
        static final Packed EMPTY = new Packed(NO_INTS, NO_INTS, NO_SUBJECTS, List.of(), NO_INTS, NO_LONGS);
        private final int[] tiles, styles;
        private final SpritePresentation.Subject[] subjects;
        private final List<SpritePresentation.Primitive> primitives;
        private final int[] patternIds;
        private final long[] patternWords;

        private Packed(int[] tiles, int[] styles, SpritePresentation.Subject[] subjects,
                       List<SpritePresentation.Primitive> primitives, int[] patternIds, long[] patternWords) {
            this.tiles = tiles; this.styles = styles; this.subjects = subjects;
            this.primitives = primitives; this.patternIds = patternIds; this.patternWords = patternWords;
        }

        int tileCount() { return tiles.length / TILE_INTS; }

        SpritePresentation.Frame decode() {
            if (this == EMPTY) return SpritePresentation.Frame.empty();
            int count = tiles.length / TILE_INTS;
            var decodedStyles = new SpritePresentation.Tile[styles.length / STYLE_INTS];
            var out = new SpritePresentation.Tile[count];
            for (int i = 0, t = 0; i < count; i++, t += TILE_INTS) {
                int style = tiles[t + 3];
                SpritePresentation.Tile shape = decodedStyles[style];
                if (shape == null) shape = decodedStyles[style] = styleTile(style);
                out[i] = new SpritePresentation.Tile(shape.layer(), tiles[t], shape.palette(), shape.hFlip(),
                        shape.vFlip(), shape.priority(), Float.intBitsToFloat(tiles[t + 1]),
                        Float.intBitsToFloat(tiles[t + 2]), shape.width(), shape.height(), shape.priorityShader(),
                        shape.occlusionMask(), shape.ghost(), shape.ghostAlpha(), shape.rowStart(), shape.rowEnd(),
                        shape.subject(), shape.ghostColour(), shape.pixelOperation());
            }
            var versions = new HashMap<Integer, SpritePresentation.PatternVersion>(patternIds.length * 2);
            for (int i = 0, w = 0; i < patternIds.length; i++, w += 4) {
                versions.put(patternIds[i], new SpritePresentation.PatternVersion(
                        patternWords[w], patternWords[w + 1], patternWords[w + 2], patternWords[w + 3]));
            }
            return new SpritePresentation.Frame(Arrays.asList(out), primitives, versions);
        }

        /** Every style field, with placeholder pattern and position. */
        private SpritePresentation.Tile styleTile(int style) {
            int s = style * STYLE_INTS;
            int flags = styles[s + 8];
            return new SpritePresentation.Tile(LAYERS[flags & 0xF], 0, styles[s],
                    (flags & 1 << 4) != 0, (flags & 1 << 5) != 0, (flags & 1 << 6) != 0, 0, 0,
                    Float.intBitsToFloat(styles[s + 2]), Float.intBitsToFloat(styles[s + 3]),
                    (flags & 1 << 7) != 0, styles[s + 1], (flags & 1 << 8) != 0,
                    Float.intBitsToFloat(styles[s + 4]), styles[s + 5], styles[s + 6],
                    subjects[styles[s + 7]], (flags & 1 << 9) != 0, OPERATIONS[flags >>> 10]);
        }

        boolean sharesStorageWith(Packed other) {
            return tiles == other.tiles && styles == other.styles && subjects == other.subjects
                    && patternIds == other.patternIds && patternWords == other.patternWords;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            return other instanceof Packed p && Arrays.equals(tiles, p.tiles) && Arrays.equals(styles, p.styles)
                    && Arrays.equals(subjects, p.subjects) && primitives.equals(p.primitives)
                    && Arrays.equals(patternIds, p.patternIds) && Arrays.equals(patternWords, p.patternWords);
        }

        @Override public int hashCode() {
            return 31 * Arrays.hashCode(tiles) + Arrays.hashCode(patternIds);
        }
    }

    // Reusable working storage; capture runs on the gameplay thread, one frame at a time.
    private int[] tileScratch = new int[256 * TILE_INTS];
    private int[] styleScratch = new int[32 * STYLE_INTS];
    private SpritePresentation.Subject[] subjectScratch = new SpritePresentation.Subject[16];
    private int[] styleTable = new int[64];
    private int[] idScratch = new int[64];
    private SpritePresentation.PatternVersion[] versionScratch = new SpritePresentation.PatternVersion[64];
    private long[] wordScratch = new long[256];
    private final int[] style = new int[STYLE_INTS];

    /**
     * Packs {@code frame}. Arrays equal to one held by a {@code previous} pack are
     * reused rather than copied, so an unchanged piece costs one reference per checkpoint.
     */
    Packed pack(SpritePresentation.Frame frame, Packed... previous) {
        List<SpritePresentation.Tile> tiles = frame.tiles();
        var versions = frame.patternVersions();
        if (tiles.isEmpty() && frame.primitives().isEmpty() && versions.isEmpty()) return Packed.EMPTY;
        int count = tiles.size();
        if (tileScratch.length < count * TILE_INTS) tileScratch = new int[count * TILE_INTS * 2];
        int styleCount = 0, subjectCount = 0;
        Arrays.fill(styleTable, 0);
        SpritePresentation.Subject lastSubject = null;
        int lastSubjectIndex = -1;
        int lastStyle = -1;
        for (int i = 0, t = 0; i < count; i++, t += TILE_INTS) {
            SpritePresentation.Tile tile = tiles.get(i);
            SpritePresentation.Subject subject = tile.subject();
            if (subject != lastSubject) {
                lastSubjectIndex = -1;
                for (int k = 0; k < subjectCount; k++) {
                    if (subjectScratch[k] == subject) { lastSubjectIndex = k; break; }
                }
                if (lastSubjectIndex < 0) {
                    if (subjectCount == subjectScratch.length)
                        subjectScratch = Arrays.copyOf(subjectScratch, subjectCount * 2);
                    subjectScratch[subjectCount] = subject;
                    lastSubjectIndex = subjectCount++;
                }
                lastSubject = subject;
            }
            style[0] = tile.palette();
            style[1] = tile.occlusionMask();
            style[2] = Float.floatToRawIntBits(tile.width());
            style[3] = Float.floatToRawIntBits(tile.height());
            style[4] = Float.floatToRawIntBits(tile.ghostAlpha());
            style[5] = tile.rowStart();
            style[6] = tile.rowEnd();
            style[7] = lastSubjectIndex;
            style[8] = tile.layer().ordinal() | (tile.hFlip() ? 1 << 4 : 0) | (tile.vFlip() ? 1 << 5 : 0)
                    | (tile.priority() ? 1 << 6 : 0) | (tile.priorityShader() ? 1 << 7 : 0)
                    | (tile.ghost() ? 1 << 8 : 0) | (tile.ghostColour() ? 1 << 9 : 0)
                    | tile.pixelOperation().ordinal() << 10;
            if (lastStyle < 0 || !sameStyle(lastStyle)) {
                lastStyle = findOrAddStyle(styleCount);
                if (lastStyle == styleCount) styleCount++;
            }
            tileScratch[t] = tile.patternId();
            tileScratch[t + 1] = Float.floatToRawIntBits(tile.x());
            tileScratch[t + 2] = Float.floatToRawIntBits(tile.y());
            tileScratch[t + 3] = lastStyle;
        }
        int versionCount = sortedVersions(versions);
        if (wordScratch.length < versionCount * 4) wordScratch = new long[versionCount * 8];
        for (int i = 0, w = 0; i < versionCount; i++, w += 4) {
            var version = versionScratch[i];
            wordScratch[w] = version.a(); wordScratch[w + 1] = version.b();
            wordScratch[w + 2] = version.c(); wordScratch[w + 3] = version.d();
        }
        Arrays.fill(versionScratch, 0, versionCount, null);
        int[] tileArray = null, styleArray = null, idArray = null;
        SpritePresentation.Subject[] subjectArray = null;
        long[] wordArray = null;
        for (Packed p : previous) {
            if (p == null) continue;
            if (tileArray == null && Arrays.equals(p.tiles, 0, p.tiles.length, tileScratch, 0, count * TILE_INTS))
                tileArray = p.tiles;
            if (styleArray == null
                    && Arrays.equals(p.styles, 0, p.styles.length, styleScratch, 0, styleCount * STYLE_INTS))
                styleArray = p.styles;
            if (subjectArray == null && sameSubjects(p.subjects, subjectCount)) subjectArray = p.subjects;
            if (idArray == null && Arrays.equals(p.patternIds, 0, p.patternIds.length, idScratch, 0, versionCount))
                idArray = p.patternIds;
            if (wordArray == null
                    && Arrays.equals(p.patternWords, 0, p.patternWords.length, wordScratch, 0, versionCount * 4))
                wordArray = p.patternWords;
        }
        if (tileArray == null) tileArray = Arrays.copyOf(tileScratch, count * TILE_INTS);
        if (styleArray == null) styleArray = Arrays.copyOf(styleScratch, styleCount * STYLE_INTS);
        if (subjectArray == null) subjectArray = Arrays.copyOf(subjectScratch, subjectCount);
        if (idArray == null) idArray = Arrays.copyOf(idScratch, versionCount);
        if (wordArray == null) wordArray = Arrays.copyOf(wordScratch, versionCount * 4);
        Arrays.fill(subjectScratch, 0, subjectCount, null);
        return new Packed(tileArray, styleArray, subjectArray, frame.primitives(), idArray, wordArray);
    }

    private boolean sameStyle(int index) {
        return Arrays.equals(styleScratch, index * STYLE_INTS, index * STYLE_INTS + STYLE_INTS, style, 0, STYLE_INTS);
    }

    /** Open-addressed lookup of {@link #style}; returns {@code styleCount} after appending it. */
    private int findOrAddStyle(int styleCount) {
        if (styleCount * 2 >= styleTable.length) {
            styleTable = new int[styleTable.length * 2];
            for (int i = 0; i < styleCount; i++) insert(i, hash(styleScratch, i * STYLE_INTS));
        }
        int mask = styleTable.length - 1;
        int slot = hash(style, 0) & mask;
        for (int entry; (entry = styleTable[slot]) != 0; slot = (slot + 1) & mask) {
            if (sameStyle(entry - 1)) return entry - 1;
        }
        if (styleScratch.length < (styleCount + 1) * STYLE_INTS)
            styleScratch = Arrays.copyOf(styleScratch, styleScratch.length * 2);
        System.arraycopy(style, 0, styleScratch, styleCount * STYLE_INTS, STYLE_INTS);
        styleTable[slot] = styleCount + 1;
        return styleCount;
    }

    private void insert(int index, int hash) {
        int mask = styleTable.length - 1;
        int slot = hash & mask;
        while (styleTable[slot] != 0) slot = (slot + 1) & mask;
        styleTable[slot] = index + 1;
    }

    private static int hash(int[] values, int from) {
        int h = 1;
        for (int i = from; i < from + STYLE_INTS; i++) h = 31 * h + values[i];
        return h ^ (h >>> 16);
    }

    private boolean sameSubjects(SpritePresentation.Subject[] held, int subjectCount) {
        if (held.length != subjectCount) return false;
        for (int i = 0; i < subjectCount; i++) if (held[i] != subjectScratch[i]) return false;
        return true;
    }

    /** Fills {@link #idScratch}/{@link #versionScratch} in ascending id order; returns the count. */
    private int sortedVersions(java.util.Map<Integer, SpritePresentation.PatternVersion> versions) {
        int n = versions.size();
        if (idScratch.length < n) {
            idScratch = new int[n * 2];
            versionScratch = new SpritePresentation.PatternVersion[n * 2];
        }
        int size = 0;
        for (var entry : versions.entrySet()) {
            int id = entry.getKey();
            var version = entry.getValue();
            int at = size++;
            // Insertion sort: a frame references a few dozen mutable art slots.
            while (at > 0 && idScratch[at - 1] > id) {
                idScratch[at] = idScratch[at - 1];
                versionScratch[at] = versionScratch[at - 1];
                at--;
            }
            idScratch[at] = id;
            versionScratch[at] = version;
        }
        return size;
    }
}
