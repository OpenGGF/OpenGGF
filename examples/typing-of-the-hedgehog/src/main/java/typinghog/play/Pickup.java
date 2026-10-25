package typinghog.play;

import typinghog.core.Prompt;

/**
 * A stand's non-badnik target: a power monitor (type its word for the power), an Eggman decoy
 * monitor (typing it hurts, like Typing of the Dead's civilians), or a fleeing animal (typing it
 * rescues it). Monitors sit on the ground and expire; animals run off screen.
 */
final class Pickup {
    enum Kind {
        SHIELD("shield", "monitor_shield"),
        SPEED("speed shoes", "monitor_shoes"),
        INVINCIBLE("invincible", "monitor_invincible"),
        SUPER_RING("super ring", "monitor_rings"),
        EXTRA_LIFE("extra life", "monitor_life"),
        DECOY("eggman", "monitor_eggman"),
        RESCUE("", "animal");

        final String word;
        final String prop;
        Kind(String word, String prop) { this.word = word; this.prop = prop; }
        boolean monitor() { return this != RESCUE; }
    }

    static final int MONITOR_ROWS = 420;

    final Kind kind;
    final Prompt prompt;
    final Stagehand.Visual visual;
    final int targetId;
    int rows;
    int x;
    int y;
    int vx;
    int animTick;

    Pickup(Kind kind, Prompt prompt, Stagehand.Visual visual, int targetId, int x, int y, int vx) {
        this.kind = kind;
        this.prompt = prompt;
        this.visual = visual;
        this.targetId = targetId;
        this.x = x << 8;
        this.y = y;
        this.vx = vx;
    }

    int px() { return x >> 8; }
}
