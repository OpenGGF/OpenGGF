package survivors;

import com.openggf.audio.GameSound;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Rings are Sonic's health. Hits charge the ring-bank toll after Armor, with no
 * knockback, spilling half the payment as pickups he can grab back; a shield
 * absorbs the hit instead; a hit with no rings left is lethal unless a revive remains.
 * Called from touch listeners, which the engine runs before its own hurt pass: leaving
 * Sonic flashing makes that pass return without the stock knockback and ring loss.
 */
final class Guard {
    private Guard() { }

    // One second of readable recovery; dense crowds still charge only one hit per window.
    static final int INVULNERABLE_FRAMES = 60;
    static final int REVIVE_INVULNERABLE_FRAMES = 180;
    // S2 Sonic anim ids the touch pass counts as an attack: Roll (with rolling status) and Spindash.
    private static final int ANIM_ROLL = 0x02;
    private static final int ANIM_SPINDASH = 0x09;

    static boolean attacking(AbstractPlayableSprite player) {
        int animation = player.getAnimationId();
        return player.getInvincibleFrames() > 0 || player.isSuperSonic()
                || animation == ANIM_SPINDASH || animation == ANIM_ROLL && player.getRolling();
    }

    /** True when the touch would hurt Sonic (a hazard, or an enemy he is not attacking). */
    static boolean harmful(AbstractPlayableSprite player, TouchResponseResult result) {
        return result.category() == TouchCategory.HURT
                || (result.category() == TouchCategory.ENEMY || result.category() == TouchCategory.BOSS)
                && !attacking(player);
    }

    /**
     * Absorbs a harmful touch on the main player. Returns true when the hit was absorbed (the
     * engine's hurt pass will then do nothing); false lets the stock hurt or death apply.
     */
    static boolean absorb(ObjectServices services, AbstractPlayableSprite player, TouchResponseResult result) {
        if (player.getDead() || player.isDebugMode() || player.isCpuControlled() || player.getInvulnerable()) {
            return false;
        }
        if (!harmful(player, result)) return false;
        return takeHit(services, player);
    }

    /** Applies one hit to Sonic: shield, ring toll, revive, or (returning false) a lethal hit. */
    static boolean takeHit(ObjectServices services, AbstractPlayableSprite player) {
        // Super Sonic shrugs off hazards too: his rings already drain by the second.
        if (player.getInvulnerable() || player.getInvincibleFrames() > 0 || player.isSuperSonic()) return true;
        var run = services.gameService(RunState.class);
        Stage stage = Stage.find(services);
        if (player.hasShield()) {
            player.setInvulnerableFrames(INVULNERABLE_FRAMES);
            player.removeShield();
            services.audioManager().playSfx(GameSound.HURT);
            if (stage != null) stage.onPlayerHit(0);
            return true;
        }
        int rings = player.getRingCount();
        if (rings <= 0) {
            if (run != null && run.revives > 0) {
                run.revives--;
                player.setInvulnerableFrames(REVIVE_INVULNERABLE_FRAMES);
                player.addRings(20);
                services.playSfx(0xBF); // S2 sfx_ContinueJingle.
                if (stage != null) stage.onRevive();
                return true;
            }
            return false;
        }
        int toll = run == null ? RunState.BASE_TOLL : run.toll(rings);
        int lost = Math.min(toll, rings);
        player.setInvulnerableFrames(INVULNERABLE_FRAMES);
        player.setRingCount(rings - lost);
        services.audioManager().playSfx(GameSound.RING_SPILL);
        if (stage != null) {
            stage.spillRings(player.getCentreX(), player.getCentreY(), Math.max(1, lost / 2));
            stage.onPlayerHit(lost);
        }
        return true;
    }
}
