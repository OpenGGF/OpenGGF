package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The Lava Reef miniboss's firing hand: subtype {@code $16}, the last child of each ring
 * ({@code loc_78922}, sonic3k.asm:160438-160500).
 *
 * <p>It is the only child with its own {@code collision_property} ({@code 4}, so four hits), and
 * killing both hands is what shortens the parent's hover from {@code $15F} to {@code $1F}
 * ({@code loc_78D2C} -> {@code loc_787D8}).
 *
 * <p>Firing runs off the parent's {@code $38} bit 3. When the boss extends, the hand arms with
 * {@code $2E = $7F}, or {@code $DF} on the mirrored ring so the two sides alternate, and
 * {@code loc_789CA} fires: a projectile from {@code ChildObjDat_78D90} with the shot counter
 * {@code $39} as its subtype, the next reload at {@code $13} frames, and after the third shot
 * {@code $FFF} -- which is not a long reload so much as a stop, because the parent's bit 2
 * (set when the arm finishes retracting) returns the hand to its idle routine first.
 */
final class LrzMinibossHandChild extends AbstractBossChild implements RewindRecreatable, LrzMinibossRingChild {

    /** {@code word_78D6C}: priority 0, {@code $10 $10} size, mapping frame 6, collision 6. */
    private static final int MAPPING_FRAME = 6;
    private static final int PRIORITY_BUCKET = 0;
    /** {@code move.b #4,collision_property(a0)} at {@code loc_78922}. */
    private static final int HIT_COUNT = 4;
    /** {@code loc_78946}: {@code moveq #$7F,d0}, or {@code $DF} on the mirrored ring. */
    private static final int ARM_DELAY = 0x7F;
    private static final int ARM_DELAY_MIRRORED = 0xDF;
    /** {@code loc_789CA}: {@code move.w #$13,$2E(a0)} between shots. */
    private static final int RELOAD_FRAMES = 0x13;
    /** {@code loc_789CA}: after the third shot the reload becomes {@code $FFF}. */
    private static final int RELOAD_AFTER_LAST_SHOT = 0xFFF;
    private static final int SHOTS_PER_VOLLEY = 3;
    /** {@code byte_78E05}: {@code dc.b 1,6,7,$B,$FC}, the firing animation. */
    private static final int[] FIRE_ANIMATION = {1, 6, 7, 0x0B, 0xFC};

    private static final int ROUTINE_IDLE = 0;      // loc_78946
    private static final int ROUTINE_FIRING = 1;    // loc_7897A

    private boolean mirrored;
    private int childSubtype;
    private int routine = ROUTINE_IDLE;
    private int waitTimer;
    private int shotCounter;
    private int hitsRemaining = HIT_COUNT;
    private int animationIndex;

    LrzMinibossHandChild(AbstractBossInstance parent, int childSubtype, boolean mirrored) {
        super(parent, "LRZMinibossHand", PRIORITY_BUCKET, 0x9D);
        this.mirrored = mirrored;
        this.childSubtype = childSubtype;
    }

    @Override
    public LrzMinibossHandChild recreateForRewind(RewindRecreateContext ctx) {
        return parent == null ? null : new LrzMinibossHandChild(parent, childSubtype, mirrored);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!shouldUpdate(vIntRunCount)) {
            return;
        }
        if (routine == ROUTINE_IDLE) {
            // loc_78946: arm when the boss extends.
            if (bossFlagSet(1 << 3)) {
                routine = ROUTINE_FIRING;
                waitTimer = mirrored ? ARM_DELAY_MIRRORED : ARM_DELAY;
                shotCounter = 0;
            }
        } else {
            // loc_7897A: the parent's bit 2 sends the hand back to idle.
            if (bossFlagSet(1 << 2)) {
                routine = ROUTINE_IDLE;
            } else {
                advanceAnimation();
                waitTimer--;
                if (waitTimer < 0) {
                    fire();
                }
            }
        }
        syncPositionWithParent();
        updateDynamicSpawn();
    }

    /** {@code loc_789CA} (sonic3k.asm:160483-160500). */
    private void fire() {
        shotCounter++;
        waitTimer = shotCounter >= SHOTS_PER_VOLLEY ? RELOAD_AFTER_LAST_SHOT : RELOAD_FRAMES;
        var objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(Sonic3kSfx.BOSS_PROJECTILE.id);
        }
        // ChildObjDat_78D90 -> loc_78A02, with the shot counter as the projectile's subtype,
        // which sub_78BAA turns into one of word_78BCA's three velocity pairs.
        final int shot = shotCounter;
        spawnProjectile(shot);
    }

    private void spawnProjectile(int shot) {
        spawnChild(() -> new LrzMinibossProjectileChild(currentX, currentY, shot, mirrored));
    }

    /** {@code Animate_Raw} over {@code byte_78E05}. */
    private void advanceAnimation() {
        animationIndex++;
        if (animationIndex >= FIRE_ANIMATION.length
                || FIRE_ANIMATION[animationIndex] == 0xFC) {
            animationIndex = 0;
        }
    }

    /**
     * {@code sub_78CF4} / {@code loc_78D2C} (sonic3k.asm:160752-160791): four hits, then the
     * parent learns which side died.
     */
    void takeHit() {
        if (hitsRemaining <= 0) {
            return;
        }
        hitsRemaining--;
        if (hitsRemaining == 0 && parent instanceof LrzMinibossInstance boss) {
            boss.onHandDestroyed(mirrored);
        }
    }

    int getHitsRemaining() {
        return hitsRemaining;
    }

    int getShotCounter() {
        return shotCounter;
    }

    @Override public int ringSubtype() { return childSubtype; }
    @Override public boolean ringMirrored() { return mirrored; }

    private boolean bossFlagSet(int mask) {
        return parent instanceof LrzMinibossInstance boss && (boss.getFlags38() & mask) != 0;
    }

    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(MAPPING_FRAME, currentX, currentY, mirrored, false);
    }
}
