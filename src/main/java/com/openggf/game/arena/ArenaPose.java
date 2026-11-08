package com.openggf.game.arena;



/**
 * The resolved render state of a playable character for one frame: ROM centre position,
 * mapping frame, flips, sprite priority and whether the character is in its hurt state
 * (knocked back after a hit). It is presentation data, never physics; a host may record it to
 * draw a ghost later.
 */
public record ArenaPose(int centreX, int centreY, int mappingFrame, boolean hFlip, boolean vFlip,
                         int priorityBucket, boolean highPriority, boolean hurt, boolean secondaryPath, int shoeKind) {
    public ArenaPose(int centreX, int centreY, int mappingFrame, boolean hFlip, boolean vFlip, int priorityBucket, boolean highPriority, boolean hurt) {
        this(centreX, centreY, mappingFrame, hFlip, vFlip, priorityBucket, highPriority, hurt, false, 0);
    }
}
