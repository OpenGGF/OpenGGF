package infinite;

import com.openggf.audio.GameSound;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Mod design: a shield, or else a 20-ring toll that spills from Sonic, absorbs an enemy hit
 * with no knockback.
 * Sonic keeps running and blinks for the stock post-hit time. With fewer than 20 rings and
 * no shield the stock hurt (losing every ring) or death applies.
 */
final class CourseGuard {
    static final int RING_TOLL = 20;
    // Stock post-hit invulnerability ($78), so one contact cannot be charged twice.
    static final int GUARD_INVULNERABLE_FRAMES = 0x78;
    // S1 Sonic anim ids the engine's touch pass counts as an attack: Roll (with the rolling
    // status) and 9 (the shared spindash id).
    private static final int ANIM_ROLL = 0x02;
    private static final int ANIM_SPINDASH = 0x09;

    /**
     * Called from a badnik's touch listener, which the engine runs before it applies damage.
     * Leaving Sonic flashing makes the engine's hurt pass return without knockback or ring loss.
     */
    static boolean absorb(ObjectServices services, AbstractPlayableSprite player, TouchResponseResult result,
            int frameCounter) {
        if (player.getDead() || player.isDebugMode() || player.isCpuControlled() || player.getInvulnerable()) {
            return false;
        }
        boolean harmful = result.category() == TouchCategory.HURT
                || result.category() == TouchCategory.ENEMY && !attacking(player);
        if (!harmful) return false;
        if (!player.hasShield() && player.getRingCount() < RING_TOLL) return false;
        // Flashing starts on the contact frame, before the spilled rings exist, so they cannot be
        // re-collected until the stock threshold (flash time below 90) as after an ordinary hit.
        player.setInvulnerableFrames(GUARD_INVULNERABLE_FRAMES);
        if (player.hasShield()) {
            player.removeShield();
            services.audioManager().playSfx(GameSound.HURT);
            return true;
        }
        int remaining = player.getRingCount() - RING_TOLL;
        var rings = services.ringManager();
        if (rings != null) {
            // The stock spill (bouncing rings and its sound) for just the toll. It empties the
            // counter as a stock hit does, so the rest is restored afterwards.
            rings.spawnLostRings(player, RING_TOLL, frameCounter);
        } else {
            services.audioManager().playSfx(GameSound.RING_SPILL);
        }
        player.setRingCount(remaining);
        return true;
    }

    /** Mirrors the engine's attack test for solo S1 Sonic (invincibility is already excluded). */
    private static boolean attacking(AbstractPlayableSprite player) {
        int animation = player.getAnimationId();
        return animation == ANIM_SPINDASH || animation == ANIM_ROLL && player.getRolling();
    }

    private CourseGuard() { }
}
