package com.openggf.tests.route;

import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayDeque;
import java.util.StringJoiner;

/**
 * Bounded ring of the last frames' pad mask and player state, rendered only
 * when an assertion diagnostic asks for it, so a failing route names what P1
 * was doing without the passing path paying for the formatting.
 *
 * <p>Inputs: the caller's frame number, the pad mask applied on that frame
 * and the player after the frame. The frame number is the route's own count
 * of frames stepped so far, recorded after the step and the increment, so the
 * first executed frame is {@code f1} in every route that shares this log.
 *
 * <p>Origin: extracted from the FBZ2 route controller
 * ({@code TestFbzAct2TraversalPreboss}) in commit 610464952; the compact
 * sample replaced the per-frame {@code String.format} after the 2026-09-13
 * review of that extraction.
 */
public final class RecentFrameLog {
    private record Sample(int frame, int mask, int x, int y, int xSpeed, int ySpeed,
                          int gSpeed, int angle, Object groundMode, int layer,
                          int topSolid, int lrbSolid, boolean air, boolean rolling,
                          boolean objectControlled, boolean dead) {
        @Override
        public String toString() {
            return String.format(
                    " f%d:i%02X p=%04X,%04X v=%04X,%04X g=%04X a=%02X mode=%s layer=%d solid=%02X/%02X air=%s roll=%s obj=%s dead=%s",
                    frame, mask, x, y, xSpeed, ySpeed, gSpeed, angle, groundMode, layer,
                    topSolid, lrbSolid, air, rolling, objectControlled, dead);
        }
    }

    private final ArrayDeque<Sample> recentFrames = new ArrayDeque<>();
    private final int capacity;

    public RecentFrameLog(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Records the mask applied on {@code frame} and the player's post-frame
     * state. {@code frame} is the count of frames stepped so far including
     * this one (first executed frame = 1).
     */
    public void record(int frame, int mask, AbstractPlayableSprite player) {
        if (recentFrames.size() == capacity) recentFrames.removeFirst();
        recentFrames.addLast(new Sample(
                frame, mask, player.getCentreX() & 0xFFFF,
                player.getCentreY() & 0xFFFF, player.getXSpeed() & 0xFFFF,
                player.getYSpeed() & 0xFFFF, player.getGSpeed() & 0xFFFF,
                player.getAngle() & 0xFF, player.getGroundMode(), player.getLayer() & 0xFF,
                player.getTopSolidBit() & 0xFF, player.getLrbSolidBit() & 0xFF,
                player.getAir(), player.getRolling(), player.isObjectControlled(),
                player.getDead()));
    }

    /** Renders the retained frames in the same {@code [ f1:..., f2:...]} shape the FBZ2 diagnostics used. */
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (Sample sample : recentFrames) joiner.add(sample.toString());
        return joiner.toString();
    }
}
