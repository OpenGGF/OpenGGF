package com.openggf.mods.run;

import com.openggf.game.ModApi;

/**
 * The resolved render state of a playable character for one frame: ROM centre position,
 * mapping frame, flips and sprite priority. It is presentation data, never physics; a host
 * may record it to draw a ghost later.
 */
@ModApi
public record PlayerPose(int centreX, int centreY, int mappingFrame, boolean hFlip, boolean vFlip,
                         int priorityBucket, boolean highPriority) {
}
