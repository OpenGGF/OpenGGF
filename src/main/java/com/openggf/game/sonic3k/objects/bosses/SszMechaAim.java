package com.openggf.game.sonic3k.objects.bosses;

/** sub_861D0: dominant-axis speed and truncated unsigned 16.16 slope, not a unit vector. */
final class SszMechaAim {
    record Velocity(int x, int y) { }
    private SszMechaAim() { }

    static Velocity toward(int x, int y, int targetX, int targetY, int exponent) {
        int dx = (short) (targetX - x), dy = (short) (targetY - y);
        int ax = Math.abs(dx), ay = Math.abs(dy), speed = 0x100 << exponent;
        int vx, vy;
        if (ax == ay) {
            // loc_8621A sends coincident coordinates through loc_861F2: X still moves.
            vx = speed; vy = ax == 0 ? 0 : speed;
        } else if (ax > ay) {
            vx = speed; vy = (int) (((long) ay << 16) / ax) >>> (8 - exponent);
        } else {
            vx = (int) (((long) ax << 16) / ay) >>> (8 - exponent); vy = speed;
        }
        return new Velocity((short) (dx < 0 ? -vx : vx), (short) (dy < 0 ? -vy : vy));
    }
}
