package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;

/**
 * Shared ROM helpers for the Hidden Palace Knuckles fight, the Master Emerald theft and the
 * altar teleporter ending ({@code CutsceneKnux_HPZ}, sonic3k.asm:131259-133700).
 *
 * <p>The ROM links these objects through RAM words: {@code _unkFAA4} (the Knuckles object),
 * {@code _unkFAAE} (the Robotnik ship) and {@code _unkFABA} (the Master Emerald). Each has at
 * most one live instance in {@code $1601}, so the engine resolves the word by looking the
 * instance up rather than storing a pointer that rewind would have to re-link.
 */
final class HpzKnucklesCutsceneSupport {
    /** {@code _unkFAB8} bit numbers. */
    static final int FLAG_CAMERA_READY = 0;
    static final int FLAG_RELEASE_PLAYER = 1;
    static final int FLAG_SPARKS_LEFT = 2;
    static final int FLAG_SPARKS_RIGHT = 3;
    static final int FLAG_ZAP = 4;
    static final int FLAG_KNUCKLES_LANDED = 5;
    static final int FLAG_ENDING_JUMP = 6;
    static final int FLAG_BEAM_READY = 7;

    /** {@code AniRaw_RobotnikHead}: {@code dc.b 5,0,1,$FC}. */
    static final int ANI_RAW_ROBOTNIK_HEAD = 0x681CC;
    private static final int ANI_RAW_ROBOTNIK_HEAD_SIZE = 4;


    private HpzKnucklesCutsceneSupport() {
    }

    static HpzZoneRuntimeState hpz(ObjectServices services) {
        var registry = services.zoneRuntimeRegistry();
        return registry == null ? null : S3kRuntimeStates.currentHpz(registry).orElse(null);
    }

    /** Raw animation scripts {@code byte_6669A}-{@code byte_668C7}, sliced from the ROM. */
    static S3kRawAnimation scripts(ObjectServices services) {
        return load(services, Sonic3kConstants.CUTSCENE_KNUX_RAW_SCRIPTS_ADDR,
                Sonic3kConstants.CUTSCENE_KNUX_RAW_SCRIPTS_SIZE);
    }

    /** {@code AniRaw_RobotnikHead}. */
    static S3kRawAnimation robotnikHeadScript(ObjectServices services) {
        return load(services, ANI_RAW_ROBOTNIK_HEAD, ANI_RAW_ROBOTNIK_HEAD_SIZE);
    }

    private static S3kRawAnimation load(ObjectServices services, int address, int size) {
        try {
            return S3kRawAnimation.load(services.romReader(), address, size);
        } catch (IOException ex) {
            throw new IllegalStateException("raw animation script at 0x" + Integer.toHexString(address), ex);
        }
    }

    static <T> T findActive(ObjectServices services, Class<T> type) {
        var manager = services.objectManager();
        if (manager == null) {
            return null;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                return type.cast(instance);
            }
        }
        return null;
    }

    /** {@code _unkFABA}. */
    static HPZMasterEmeraldObjectInstance masterEmerald(ObjectServices services) {
        return findActive(services, HPZMasterEmeraldObjectInstance.class);
    }

    /** {@code _unkFAAE}. */
    static HpzRobotnikShipObjectInstance ship(ObjectServices services) {
        return findActive(services, HpzRobotnikShipObjectInstance.class);
    }

    /** {@code _unkFAA4} while {@code CutsceneKnux_HPZ} lives. */
    static CutsceneKnucklesHpzInstance knuckles(ObjectServices services) {
        return findActive(services, CutsceneKnucklesHpzInstance.class);
    }

    static AbstractPlayableSprite player1(ObjectServices services) {
        PlayableEntity main = services.playerQuery().mainPlayerOrNull();
        return main instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    /** {@code Player_2}, or {@code null} for an empty slot (all SST bytes zero). */
    static AbstractPlayableSprite player2(ObjectServices services) {
        PlayableEntity p2 = services.playerQuery().nativeP2OrNull();
        return p2 instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    static int x(AbstractPlayableSprite sprite) {
        return sprite == null ? 0 : sprite.getCentreX() & 0xFFFF;
    }

    static int y(AbstractPlayableSprite sprite) {
        return sprite == null ? 0 : sprite.getCentreY() & 0xFFFF;
    }

    /** {@code btst #Status_Invincible,status_secondary(a1)}. */
    static boolean statusInvincible(AbstractPlayableSprite sprite) {
        return sprite != null && (sprite.getInvincibleFrames() > 0 || sprite.isSuperSonic());
    }

    /** {@code anim(a1)}. */
    static int anim(AbstractPlayableSprite sprite) {
        return sprite == null ? 0 : sprite.getAnimationId() & 0xFF;
    }

    /** {@code invulnerability_timer(a1)} ({@code $34}). */
    static int invulnerabilityTimer(AbstractPlayableSprite sprite) {
        return sprite == null ? 0 : sprite.getInvulnerableFrames() & 0xFF;
    }

    /**
     * {@code HurtCharacter_Directly}: {@code HurtCharacter} with no invulnerability test.
     *
     * <p>{@code HurtCharacter} (sonic3k.asm:21580) reads {@code Ring_count} only for
     * {@code Player_1}; outside competition mode any other player branches straight to
     * {@code loc_102E0}, so a sidekick is always knocked back and never killed or stripped of
     * rings. {@code Player_1} with rings and no shield allocates {@code Obj_Bouncing_Ring};
     * with no rings and no shield it dies ({@code loc_10350}).
     */
    static void hurtDirectly(ObjectServices services, AbstractPlayableSprite sprite, int sourceX,
                             int vIntRunCount) {
        if (sprite == null || sprite.getDead()) {
            return;
        }
        if (sprite != player1(services)) {
            sprite.applyHurtOrDeathIgnoringIFrames(sourceX, false, true);
            return;
        }
        boolean hadRings = sprite.getRingCount() > 0;
        if (hadRings && !sprite.hasShield()) {
            services.spawnLostRings(sprite, vIntRunCount);
        }
        sprite.applyHurtOrDeathIgnoringIFrames(sourceX, false, hadRings);
    }

    /** {@code Check_InTheirRange}: signed {@code x - left} and {@code y - top} windows. */
    static boolean inTheirRange(int x, int y, int targetX, int targetY, int[] table) {
        int left = (short) (targetX + table[0]);
        int right = (short) (left + table[1]);
        int sx = (short) x;
        if (sx < left || sx >= right) {
            return false;
        }
        int top = (short) (targetY + table[2]);
        int bottom = (short) (top + table[3]);
        int sy = (short) y;
        return sy >= top && sy < bottom;
    }
}
