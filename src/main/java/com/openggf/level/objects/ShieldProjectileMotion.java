package com.openggf.level.objects;

import com.openggf.game.ModApi;
import com.openggf.physics.TrigLookupTable;

/** Pure S3K Touch_ChkHurt_Bounce_Projectile velocity; collision/lifetime stay with the owner. */
@ModApi
public final class ShieldProjectileMotion {
    private ShieldProjectileMotion() { }

    @ModApi
    public record Velocity(int x, int y) { }

    /** ROM shield reflection at $800 speed using signed-short displacement and native angle math. */
    public static Velocity awayFrom(int projectileX, int projectileY, int actorCentreX, int actorCentreY) {
        int angle = TrigLookupTable.calcAngle(saturateToShort(actorCentreX - projectileX),
                saturateToShort(actorCentreY - projectileY));
        return new Velocity(-((TrigLookupTable.cosHex(angle) * 0x800) >> 8),
                -((TrigLookupTable.sinHex(angle) * 0x800) >> 8));
    }

    private static short saturateToShort(int value) {
        return (short) Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE);
    }
}
