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
                         int priorityBucket, boolean highPriority, boolean hurt) {
    public GhostFrame toGhostFrame(boolean finished) {
        return new GhostFrame(centreX, centreY, mappingFrame, hFlip, vFlip, finished,
                priorityBucket, highPriority, hurt);
    }

    /** The pose of a character that is not hurt. */
    public PlayerPose(int centreX, int centreY, int mappingFrame, boolean hFlip, boolean vFlip,
                      int priorityBucket, boolean highPriority) {
        this(centreX, centreY, mappingFrame, hFlip, vFlip, priorityBucket, highPriority, false);
    }
}
