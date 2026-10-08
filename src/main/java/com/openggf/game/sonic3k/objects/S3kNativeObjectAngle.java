package com.openggf.game.sonic3k.objects;

/** Native sub_8622C octant-ratio angle shared by LRZ and DDZ. */
final class S3kNativeObjectAngle {
    private S3kNativeObjectAngle() { }
    /**
     * {@code sub_8622C} (sonic3k.asm:181154-181215): the byte angle from ({@code fromX},{@code fromY})
     * towards ({@code toX},{@code toY}), 0 pointing down and {@code $40} right, built from
     * {@code (min << 5) / max} and the octant. Returns the low byte used by the native callers.
     */
    static int angleTowards(int fromX, int fromY, int toX, int toY) {
        int dx = (toX - fromX) & 0xFFFF;
        int dy = (toY - fromY) & 0xFFFF;
        int octant = 0;
        if ((short) dx < 0) {
            dx = (-dx) & 0xFFFF;
            octant += 8;
        }
        if ((short) dy < 0) {
            dy = (-dy) & 0xFFFF;
            octant += 4;
        }
        int d0 = dx;
        int d1 = dy;
        if (d1 < d0) {
            d0 = dy;
            d1 = dx;
            octant += 2;
        }
        if (d1 == 0) {
            return d0 & 0xFF;
        }
        int quotient = ((d0 << 5) & 0xFFFF) / d1;
        int angle = switch (octant) {
            case 0 -> quotient;
            case 2 -> 0x40 - quotient;
            case 4 -> 0x80 - quotient;
            case 6 -> quotient + 0x40;
            case 8 -> -quotient;
            case 10 -> quotient + 0xC0;
            case 12 -> quotient + 0x80;
            default -> 0xC0 - quotient;
        };
        return angle & 0xFF;
    }

}
