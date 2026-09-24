package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLrzShootingTriggerProjectile {
    @Test void romShieldReactionDeflectsAwayAndPermanentlyClearsDamage() {
        // loc_42E00 sets shield_reaction bit3. Touch_ChkHurt_Bounce_Projectile
        // applies -$800 along the projectile-to-player angle and clears collision.
        for (int[] sample : new int[][] {
                {32, 0, -0x800, 0}, {-32, 0, 0x800, 0},
                {0, 32, 0, -0x800}, {0, -32, 0, 0x800}}) {
            var shot = new LrzShootingTriggerProjectileInstance(
                    new ObjectSpawn(0x400, 0x300, 0, 0, 0, false, 0));
            var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
            player.setCentreX((short) (0x400 + sample[0]));
            player.setCentreY((short) (0x300 + sample[1]));
            assertEquals(8, shot.getShieldReactionFlags());
            assertEquals(TouchShieldDeflectCapability.SHIELD_DEFLECT,
                    shot.getTouchResponseProfile().shieldDeflectCapability());
            assertEquals(0x98, shot.getCollisionFlags());
            assertTrue(shot.onShieldDeflect(player));
            assertEquals(sample[2], shot.xVelocity());
            assertEquals(sample[3], shot.yVelocity());
            assertEquals(0, shot.getCollisionFlags());
        }
    }
}
