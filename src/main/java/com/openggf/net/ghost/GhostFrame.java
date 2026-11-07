package com.openggf.net.ghost;

/**
 * One resolved render-state ghost frame (spec §7: final render state, never physics).
 * {@code hurt} marks a frame in which the player was in their hurt state (knocked back), so
 * other players' HUDs can show it; it is presentation only.
 */
@com.openggf.game.ModApi
public record GhostFrame(int x, int y, int mappingFrame, boolean hFlip, boolean vFlip,
                         boolean finished, int priorityBucket, boolean highPriority, boolean hurt, int progress, boolean secondaryPath, int shoeKind) {
    public GhostFrame(int x, int y, int mappingFrame, boolean hFlip, boolean vFlip, boolean finished, int priorityBucket, boolean highPriority, boolean hurt) {
        this(x, y, mappingFrame, hFlip, vFlip, finished, priorityBucket, highPriority, hurt, 0, false, 0);
    }
    public GhostFrame {
        if (progress < 0 || progress > 15 || shoeKind < 0 || shoeKind > 2) throw new IllegalArgumentException("invalid ghost progress or shoe kind");
    }
    /** A frame of a player who is not hurt. */
    public GhostFrame(int x, int y, int mappingFrame, boolean hFlip, boolean vFlip,
                      boolean finished, int priorityBucket, boolean highPriority) {
        this(x, y, mappingFrame, hFlip, vFlip, finished, priorityBucket, highPriority, false);
    }
}
