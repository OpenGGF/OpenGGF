package com.openggf.graphics;

/**
 * Transient SAT envelope: immutable geometry plus native admission, exact scanline
 * clipping and presentation state, consumed synchronously during replay.
 */
public record SpriteSatEntry(
        int priorityBucket,
        SpritePiece piece,
        int visibleTopY,
        int visibleBottomY,
        SpritePresentation.Subject presentationSubject,
        Ghost ghost) {
    /**
     * A gameplay ghost's draw style. Ghosts are not ROM sprites: a sprite mask clips them like
     * any later SAT entry, but they never arm one (see {@link SpriteSatMaskPostProcessor}).
     *
     * @param alpha          ghost opacity
     * @param preserveColour keep palette colours (a player's look) instead of desaturating
     */
    public record Ghost(float alpha, boolean preserveColour) { }

    /** Visible-scanline bound meaning "no sprite-mask clip on this side". */
    public static final int UNCLIPPED_TOP = Integer.MIN_VALUE;
    public static final int UNCLIPPED_BOTTOM = Integer.MAX_VALUE;

    public SpriteSatEntry {
        java.util.Objects.requireNonNull(presentationSubject, "presentation subject");
        if (priorityBucket < RenderPriority.MIN || priorityBucket > RenderPriority.MAX) {
            throw new IllegalArgumentException("Priority bucket out of range");
        }
        java.util.Objects.requireNonNull(piece, "sprite piece");
        if (visibleTopY > visibleBottomY) {
            throw new IllegalArgumentException("Visible scanlines must not be inverted");
        }
    }

    /** A real (ROM) sprite entry without a scanline clip or presentation overlay. */
    public SpriteSatEntry(int priorityBucket, SpritePiece piece) {
        this(priorityBucket, piece, UNCLIPPED_TOP, UNCLIPPED_BOTTOM, SpritePresentation.Subject.WORLD, null);
    }

    public boolean isGhost() {
        return ghost != null;
    }

    public SpriteSatEntry withGhost(Ghost style) {
        return new SpriteSatEntry(priorityBucket, piece, visibleTopY, visibleBottomY, presentationSubject, style);
    }

    public SpriteSatEntry withPresentationSubject(SpritePresentation.Subject subject) {
        return new SpriteSatEntry(priorityBucket, piece, visibleTopY, visibleBottomY, subject, ghost);
    }

    public static SpriteSatEntry of(int x, int y, int widthTiles, int heightTiles,
            int firstPatternIndex, int paletteIndex, boolean hFlip, boolean vFlip,
            boolean piecePriority, boolean globalHighPriority) {
        return of(x, y, widthTiles, heightTiles, firstPatternIndex, firstPatternIndex & 0x7FF,
                paletteIndex, hFlip, vFlip, piecePriority, globalHighPriority);
    }

    public static SpriteSatEntry of(int x, int y, int widthTiles, int heightTiles,
            int firstPatternIndex, int rawTileWordLow11, int paletteIndex, boolean hFlip, boolean vFlip,
            boolean piecePriority, boolean globalHighPriority) {
        return new SpriteSatEntry(RenderPriority.MIN,
                new SpritePiece(x, y, widthTiles, heightTiles, firstPatternIndex, rawTileWordLow11, paletteIndex,
                        hFlip, vFlip, piecePriority, globalHighPriority, SpriteMaskReplayRole.NORMAL,
                        0, widthTiles, 0, heightTiles, null));
    }

    public SpriteSatEntry clipRect(int col, int cols, int row, int rows) {
        return new SpriteSatEntry(priorityBucket, piece.clipRect(col, cols, row, rows),
                visibleTopY, visibleBottomY, presentationSubject, ghost);
    }

    public SpriteSatEntry clipRows(int row, int rows) {
        return clipRect(piece.startColTile(), piece.colCountTiles(), row, rows);
    }

    public SpriteSatEntry withMaskReplayRole(SpriteMaskReplayRole role) {
        return new SpriteSatEntry(priorityBucket, piece.withMaskReplayRole(role),
                visibleTopY, visibleBottomY, presentationSubject, ghost);
    }

    /** Copy restricted to screen scanlines {@code [top, bottom)}; tile rows are left whole. */
    public SpriteSatEntry withVisibleScanlines(int top, int bottom) {
        return new SpriteSatEntry(priorityBucket, piece, top, bottom, presentationSubject, ghost);
    }

    /**
     * First visible pixel row (0-8) of an 8x8 tile drawn with its top at {@code drawY}.
     * Returns 8 when the tile is entirely hidden by a sprite-mask clip.
     */
    public int visibleTileRowStart(int drawY) {
        long start = (long) visibleTopY - drawY;
        return (int) Math.max(0, Math.min(8, start));
    }

    /** Exclusive last visible pixel row (0-8) of an 8x8 tile drawn with its top at {@code drawY}. */
    public int visibleTileRowEnd(int drawY) {
        long end = (long) visibleBottomY - drawY;
        return (int) Math.max(0, Math.min(8, end));
    }
}
