package com.openggf.tests.route;

import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayDeque;

/**
 * Bounded ring of the last frames' pad mask and player state, formatted for
 * assertion diagnostics so a failing route names what P1 was doing.
 */
public final class RecentFrameLog {
    private final ArrayDeque<String> recentFrames = new ArrayDeque<>();
    private final int capacity;

    public RecentFrameLog(int capacity) {
        this.capacity = capacity;
    }

    /** Records the mask applied on {@code frame} and the player's post-frame state. */
    public void record(int frame, int mask, AbstractPlayableSprite player) {
        if (recentFrames.size() == capacity) recentFrames.removeFirst();
        recentFrames.addLast(String.format(
                " f%d:i%02X p=%04X,%04X v=%04X,%04X g=%04X a=%02X mode=%s layer=%d solid=%02X/%02X air=%s roll=%s obj=%s dead=%s",
                frame, mask, player.getCentreX() & 0xFFFF,
                player.getCentreY() & 0xFFFF, player.getXSpeed() & 0xFFFF,
                player.getYSpeed() & 0xFFFF, player.getGSpeed() & 0xFFFF,
                player.getAngle() & 0xFF, player.getGroundMode(), player.getLayer() & 0xFF,
                player.getTopSolidBit() & 0xFF, player.getLrbSolidBit() & 0xFF,
                player.getAir(), player.getRolling(), player.isObjectControlled(),
                player.getDead()));
    }

    @Override
    public String toString() {
        return recentFrames.toString();
    }
}
