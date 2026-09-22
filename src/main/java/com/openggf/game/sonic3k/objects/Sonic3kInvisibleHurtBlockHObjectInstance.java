package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;

/**
 * Object 0x6A - InvisibleHurtBlockH (Sonic 3 & Knuckles).
 * <p>
 * Uses the same invisible solid collision as object 0x28, but applies normal
 * hurt handling on one face selected by the placement flip bits:
 * <ul>
 *   <li>no flips: top face only (standing contact)</li>
 *   <li>x-flip: side face only (swapped d6 bits 0/1 in the ROM)</li>
 *   <li>y-flip: bottom face only (swapped d6 bits 2/3 in the ROM)</li>
 * </ul>
 * <p>
 * <p>
 * {@code sub_1F58C} gates the hurt on {@code shield_reaction(a0) & $73 & shield_reaction(a1)}.
 * For a player slot that second byte is {@code status_secondary} (both are offset {@code $2B}), so
 * the mask names which power-up makes the block harmless. The plain block sets no bit and is never
 * skipped; {@code Obj_InvisibleLavaBlock} sets bit 4 (fire shield) and
 * {@code Obj_InvisibleShockBlock} bit 5 (lightning shield) before falling into this routine.
 * <p>
 * ROM: Obj_InvisibleHurtBlockHorizontal / sub_1F58C (sonic3k.asm:43273, 43427-43438)
 */
public class Sonic3kInvisibleHurtBlockHObjectInstance extends Sonic3kInvisibleBlockObjectInstance implements SolidObjectListener {

    /** {@code Status_FireShield}: {@code bset #4,shield_reaction(a0)} in {@code Obj_InvisibleLavaBlock}. */
    public static final int REACTION_FIRE_SHIELD = 1 << 4;

    /** Obj_InvisibleShockBlock: bset #5,shield_reaction(a0) (sonic3k.asm:43265). */
    public static final int REACTION_LIGHTNING_SHIELD = 1 << 5;

    /** The reaction bits this block sets, already masked by {@code sub_1F58C}'s {@code andi.b #$73}. */
    // Not final: the rewind coverage guard captures scalar object fields generically.
    private int shieldReactionBits;

    public Sonic3kInvisibleHurtBlockHObjectInstance(ObjectSpawn spawn) {
        this(spawn, "InvisibleHurtBlockH", 0);
    }

    protected Sonic3kInvisibleHurtBlockHObjectInstance(ObjectSpawn spawn, String name,
                                                       int shieldReactionBits) {
        super(spawn, name);
        this.shieldReactionBits = shieldReactionBits & SHIELD_REACTION_MASK;
    }

    /** {@code andi.b #$73,d0}: any shield or invincibility bit of {@code status_secondary}. */
    private static final int SHIELD_REACTION_MASK = 0x73;

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (playerEntity == null || !isActiveHurtFace(contact) || playerEntity.getInvulnerable()) {
            return;
        }

        // sub_1F58C: skip the whole hurt when the player's own reaction byte answers this block's.
        if (isImmune(playerEntity)) {
            return;
        }

        int sourceX = getX();
        DamageCause cause = damageCause();
        rewindPlayerYBeforeHurt(playerEntity);
        if (playerEntity.isCpuControlled()) {
            playerEntity.applyHurt(sourceX, cause);
            return;
        }

        boolean hadRings = playerEntity.getRingCount() > 0;
        if (hadRings && !playerEntity.hasShield()) {
            // HurtCharacter reserves the Obj37 owner; the shared slot scheduler
            // derives whether its initializer clears Ring_count this pass or
            // the next. This object has no separate ring-clear rule: it reaches
            // the same HurtCharacter path as ordinary touch damage.
            // ROM: sonic3k.asm:21065-21077, 35549-35616.
            services().spawnLostRingsAfterCurrentFrame(playerEntity, frameCounter);
        }
        playerEntity.applyHurtOrDeath(sourceX, cause, hadRings);
    }

    /**
     * {@code move.b shield_reaction(a0),d0 / andi.b #$73,d0 / and.b shield_reaction(a1),d0 / bne}.
     * Note the object's bit alone decides: an invincible player without the matching shield still
     * takes the hit, because this block's reaction byte carries only the shield bit.
     */
    private boolean isImmune(PlayableEntity player) {
        if (shieldReactionBits == 0) {
            return false;
        }
        return (shieldReactionBits & playerShieldReactionBits(player)) != 0;
    }

    /** The player's {@code status_secondary} shield bits, which share offset {@code $2B}. */
    private static int playerShieldReactionBits(PlayableEntity player) {
        if (!player.hasShield() || player.getShieldType() == null) {
            return 0;
        }
        return switch (player.getShieldType()) {
            case FIRE -> REACTION_FIRE_SHIELD;
            case LIGHTNING -> REACTION_LIGHTNING_SHIELD;
            default -> 0;
        };
    }

    /**
     * The engine's damage-cause equivalent of the reaction bit, so the shared hurt path applies the
     * same elemental-shield rules a donor roster may narrow.
     */
    private DamageCause damageCause() {
        return (shieldReactionBits & REACTION_FIRE_SHIELD) != 0 ? DamageCause.FIRE : DamageCause.NORMAL;
    }

    private void rewindPlayerYBeforeHurt(PlayableEntity player) {
        short ySpeed = player.getYSpeed();
        if (ySpeed != 0) {
            // ROM sub_1F58C routes to sub_24280, which subtracts y_vel<<8 from
            // y_pos before HurtCharacter (docs/skdisasm/sonic3k.asm:43422-43431,
            // 49200-49220).
            player.move((short) 0, (short) -ySpeed);
        }
    }

    private boolean isActiveHurtFace(SolidContact contact) {
        int renderFlags = spawn.renderFlags() & 0x03;
        if ((renderFlags & 0x01) != 0) {
            return contact.touchSide();
        }
        if ((renderFlags & 0x02) != 0) {
            return contact.touchBottom();
        }
        return contact.standing();
    }
}
