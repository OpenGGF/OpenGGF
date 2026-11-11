package com.openggf.mods.run;

import com.openggf.game.ModApi;
import com.openggf.net.ghost.GhostFrame;

/**
 * The resolved render state of a playable character for one frame: ROM centre position,
 * mapping frame, flips, sprite priority and whether the character is in its hurt state
 * (knocked back after a hit). It is presentation data, never physics; a host may record it to
 * draw a ghost later.
 */
@ModApi
public record PlayerPose(int centreX, int centreY, int mappingFrame, boolean hFlip, boolean vFlip,
                         int priorityBucket, boolean highPriority, boolean hurt, boolean secondaryPath, int shoeKind) {
    public PlayerPose(int centreX, int centreY, int mappingFrame, boolean hFlip, boolean vFlip, int priorityBucket, boolean highPriority, boolean hurt) {
        this(centreX, centreY, mappingFrame, hFlip, vFlip, priorityBucket, highPriority, hurt, false, 0);
    }
    public GhostFrame toGhostFrame(boolean finished, int progress) {
        return new GhostFrame(centreX, centreY, mappingFrame, hFlip, vFlip, finished, priorityBucket, highPriority, hurt, progress, secondaryPath, shoeKind);
    }
    public GhostFrame toGhostFrame(boolean finished) {
        return toGhostFrame(finished, 0);
    }

    /** The pose of a character that is not hurt. */
    public PlayerPose(int centreX, int centreY, int mappingFrame, boolean hFlip, boolean vFlip,
                      int priorityBucket, boolean highPriority) {
        this(centreX, centreY, mappingFrame, hFlip, vFlip, priorityBucket, highPriority, false);
    }
}
