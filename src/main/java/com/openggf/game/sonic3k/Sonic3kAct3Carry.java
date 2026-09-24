package com.openggf.game.sonic3k;

import com.openggf.game.ShieldType;
import com.openggf.game.rewind.RewindSnapshottable;

/**
 * Session-lived owner for the S3K {@code Act3_flag}, {@code Act3_ring_count},
 * {@code Act3_timer} and {@code Saved2_status_secondary} hand-off.
 *
 * <p>The record deliberately lives above zone runtime state: LRZ2 -> {@code $1600}
 * and DEZ2 -> {@code $1700} both cross a full level load before their screen-event
 * stage consumes these words (sonic3k.asm:119406-119417, 120360-120371,
 * 131248-131253 and 169867-169872).
 */
public final class Sonic3kAct3Carry implements RewindSnapshottable<Sonic3kAct3Carry.Snapshot> {
    private boolean active;
    private int rings;
    private long timerFrames;
    private ShieldType shield;

    @Override
    public String key() {
        return "s3k.act3Carry";
    }

    public void arm(int rings, long timerFrames, ShieldType shield) {
        this.active = true;
        this.rings = Math.max(0, rings);
        this.timerFrames = Math.max(0, timerFrames);
        this.shield = shield;
    }

    public boolean active() {
        return active;
    }

    /** Consume-once, matching {@code clr.b (Act3_flag)} after level setup. */
    public Snapshot consume() {
        if (!active) {
            return null;
        }
        Snapshot result = capture();
        active = false;
        rings = 0;
        timerFrames = 0;
        shield = null;
        return result;
    }

    @Override
    public Snapshot capture() {
        return new Snapshot(active, rings, timerFrames, shield);
    }

    @Override
    public void restore(Snapshot snapshot) {
        if (snapshot == null) {
            active = false;
            rings = 0;
            timerFrames = 0;
            shield = null;
            return;
        }
        active = snapshot.active();
        rings = snapshot.rings();
        timerFrames = snapshot.timerFrames();
        shield = snapshot.shield();
    }

    public record Snapshot(boolean active, int rings, long timerFrames, ShieldType shield) { }
}
