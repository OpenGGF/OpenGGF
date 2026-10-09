package starpost.ruins;

/**
 * Which mapping frame a badnik shows, from Sonic 1's animation scripts (s1disasm _anim/*.asm;
 * a script's delay byte holds each frame for delay + 1 frames).
 */
final class BadnikFrames {
    /** Map_Orb: the spike ball. */
    static final int ORBINAUT_BALL = 3;
    /** Map_Hog: the cannonball. */
    static final int CANNONBALL = 4;

    private BadnikFrames() {
    }

    static int frame(Badnik b, long ticks) {
        return switch (b.kind) {
            // Map_Cat: the head shuts (0) while it crawls and opens (16) between pulls.
            case Badnik.CATERKILLER -> b.state == 1 ? 0 : 16;
            // Ani_Bas: still 0, falling 1, flying 1 2 3 2 (delay 3).
            case Badnik.BATBRAIN -> b.state == 0 ? 0 : b.state == 1 ? 1 : seq(ticks, 4, 1, 2, 3, 2);
            // Ani_Buzz: flying 0 1 (delay 1); firing 4 5.
            case Badnik.BUZZ_BOMBER -> b.state == 1 ? seq(ticks, 2, 4, 5) : seq(ticks, 2, 0, 1);
            // Ani_Yad: walking 0 3 1 4 0 3 2 5 (delay 7); standing 0.
            case Badnik.YADRIN -> b.vx != 0 ? seq(ticks, 8, 0, 3, 1, 4, 0, 3, 2, 5) : 0;
            // Ani_Jaws: 0 1 2 3 (delay 7).
            case Badnik.JAWS -> seq(ticks, 8, 0, 1, 2, 3);
            // Ani_Burro: still 0 6, moving 0 1, falling 4 (delay 3).
            case Badnik.BURROBOT -> b.state == 1 ? 4 : b.vx != 0 ? seq(ticks, 4, 0, 1) : seq(ticks, 4, 0, 6);
            // Ani_Orb: normal 0.
            case Badnik.ORBINAUT -> 0;
            // Ani_Bomb: standing 1 0, walking 5 4 3 2 (delay 19), activated 7 6.
            case Badnik.BOMB -> b.state == 2 ? seq(ticks, 20, 7, 6) : b.state == 1 ? seq(ticks, 20, 5, 4, 3, 2)
                    : seq(ticks, 20, 1, 0);
            // Ani_Hog: 0 0 2 2 3 2 (delay 9); 1 as it hops.
            default -> b.state == 1 ? 1 : seq(ticks, 10, 0, 0, 2, 2, 3, 2);
        };
    }

    /** A Caterkiller body segment's frame. */
    static int caterkillerBody(Badnik b, int segment) {
        return 8;
    }

    private static int seq(long ticks, int hold, int... frames) {
        return frames[(int) (ticks / hold % frames.length)];
    }
}
