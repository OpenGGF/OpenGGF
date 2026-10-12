package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.mutators.GameplayMutatorPolicySource;

/**
 * The badnik-kill player rebound shared by all three ROMs.
 *
 * <p>S3K {@code EnemyDefeated} ({@code docs/skdisasm/sonic3k.asm:179752-179771}):
 * <pre>
 *   movea.w $44(a0),a1        ; the player recorded by the badnik's own touch check
 *   tst.w   y_vel(a1)
 *   bmi.s   loc_85750         ; rising  -> addi.w #$100,y_vel(a1)
 *   move.w  y_pos(a1),d0
 *   cmp.w   y_pos(a0),d0
 *   bhs.s   loc_85758         ; at or below the badnik -> subi.w #$100,y_vel(a1)
 *   neg.w   y_vel(a1)         ; above the badnik -> negate
 * </pre>
 * The S1 ({@code React_Enemy}) and S2 ({@code Touch_KillEnemy}) forms are identical.
 *
 * <p>Two callers reach this: {@link ObjectTouchResponseController} for the ENEMY touch
 * category, which performs the ROM's {@code Touch_KillEnemy} tail itself, and objects
 * whose ObjDat flags select the ROM's {@code Touch_Special} route and therefore call
 * {@code EnemyDefeated} from their own code (for example {@code Obj_MegaChopper} at
 * {@code sonic3k.asm:184243}). Neither path may apply the bounce twice.
 */
public final class EnemyDefeatBounce {

    private EnemyDefeatBounce() {
    }

    /**
     * Applies the {@code EnemyDefeated} y-velocity rebound to {@code player}.
     *
     * <p>Only {@code y_vel} is touched; the ROM does not set the air flag here, so the
     * collision system keeps resolving air state naturally (this is what preserves a
     * ground roll through a badnik bounce).
     *
     * @param player  the player the defeated object recorded as its toucher
     * @param enemyY  the defeated object's ROM {@code y_pos} (centre Y), resolved at the
     *                same moment as the overlap that produced the kill
     */
    public static void apply(PlayableEntity player, int enemyY) {
        player.setYSpeed(resolve(player, enemyY));
    }

    private static short resolve(PlayableEntity player, int enemyY) {
        short ySpeed = player.getYSpeed();
        if (ySpeed < 0) {
            // bmi loc_85750: addi.w #$100,y_vel(a1)
            return (short) (ySpeed + 0x100);
        }
        // Use the native centre already matched by the touch owner, not a later projection.
        return player.getCentreY() < enemyY ? (short) -ySpeed : (short) (ySpeed - 0x100);
    }

    /** Actual native defeat producer with its injected world, after matching touch coordinates. */
    public static void apply(PlayableEntity player, int enemyY, WorldSession world) {
        short resolved = resolve(player, enemyY);
        GameplayMutatorPolicySource source = WorldSessionPolicyAccess.getService(world, GameplayMutatorPolicySource.class);
        if (source != null && source.policy().defeatReboundPercent() != 100) {
            var policy = source.policy();
            // Amplify the resolved signed ROM word once; horizontal/ground/air state stays native.
            int amplified = resolved * policy.defeatReboundPercent() / 100;
            int cap = policy.defeatVerticalCap();
            resolved = (short) Math.clamp(amplified, -cap, cap);
        }
        player.setYSpeed(resolved);
    }
}
