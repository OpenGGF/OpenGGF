package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.objects.boss.BossChildComponent;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The Lava Reef miniboss's firing hand: subtype {@code $16}, the last child of each ring
 * ({@code loc_78922}, sonic3k.asm:160381-160389 and {@code loc_78946}/{@code loc_7897A},
 * sonic3k.asm:160391-160446).
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
 *
 * <p>Two things about the idle routine are easy to get wrong. {@code loc_78946} does <b>no</b>
 * positional work and ends {@code bra.w sub_78B46}, with no {@code Draw_Sprite} after it: an idle
 * hand neither moves nor draws, and simply sits wherever the last volley left it. Only
 * {@code loc_7897A} positions it, and it does so on {@code parent3} -- the tenth arm link, subtype
 * {@code $14} -- through {@code MoveSprite_AtAngleLookup} over {@code AngleLookup_2}
 * (sonic3k.asm:178504-178522), not on the boss body. Syncing it to the parent instead is what
 * makes shots appear to leave the drill rather than the end of the arm.
 */
final class LrzMinibossHandChild extends AbstractBossChild implements RewindRecreatable, LrzMinibossRingChild {

    /** {@code word_78D6C}: priority 0, {@code $10 $10} size, mapping frame 6, collision 6. */
    private static final int BASE_MAPPING_FRAME = 6;
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
    /** {@code loc_787FE}'s {@code move.w #$10,$2E(a0)}, which only the mirrored ring runs. */
    private static final int MIRRORED_STAGGER_BASE = 0x10;
    /** {@code sub_78BD6}: {@code move.b #$80,$3C(a0)}. The hand never steps it. */
    private static final int FIXED_ANGLE = 0x80;

    /**
     * {@code byte_78E05}: {@code dc.b 1,6,7,$B,$FC}, read by {@code Animate_Raw}
     * (sonic3k.asm:177333-177362). Byte 0 is the shared frame delay; the walk starts at
     * {@code anim_frame = 1} and reads {@code 1(a1,d0.w)}, so the frames are {@code 7}, {@code $B}
     * and then {@code $FC} -> {@code AnimateRaw_Restart}, which emits {@code 1(a1)} = {@code 6}.
     * Frame {@code 6} is therefore the restart target, not the first frame.
     */
    private static final int[] FIRE_ANIMATION = {1, 6, 7, 0x0B, 0xFC};

    /**
     * {@code AngleLookup_2} (sonic3k.asm:201856-201859), 64 bytes.
     * {@code MoveSprite_AtAngleLookup} loads {@code a3 = a2 + $40} and indexes it with
     * {@code not.w d0}, i.e. {@code -(lo + 1)}, which reads this same table backwards from its
     * end -- {@code AngleLookup_2[$3F - lo]}.
     */
    private static final int[] ANGLE_LOOKUP_2 = {
        0x00, 0x01, 0x01, 0x02, 0x02, 0x03, 0x04, 0x04, 0x05, 0x05, 0x06, 0x06, 0x07, 0x08, 0x08, 0x09,
        0x09, 0x0A, 0x0A, 0x0B, 0x0B, 0x0C, 0x0C, 0x0D, 0x0D, 0x0E, 0x0E, 0x0F, 0x0F, 0x10, 0x10, 0x11,
        0x11, 0x11, 0x12, 0x12, 0x13, 0x13, 0x13, 0x14, 0x14, 0x14, 0x15, 0x15, 0x15, 0x15, 0x16, 0x16,
        0x16, 0x16, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
    };

    private static final int ROUTINE_IDLE = 0;      // loc_78946
    private static final int ROUTINE_FIRING = 1;    // loc_7897A

    private boolean mirrored;
    private int childSubtype;
    private int routine = ROUTINE_IDLE;
    private int waitTimer;
    private int shotCounter;
    private int hitsRemaining = HIT_COUNT;
    /** {@code anim_frame(a0)}: {@code Animate_Raw} steps it by one and reads {@code 1(a1,d0.w)}. */
    private int animFrame;
    /** {@code anim_frame_timer(a0)}. */
    private int animTimer;
    private int mappingFrame = BASE_MAPPING_FRAME;
    /** loc_78922 runs on the creation frame and returns; the wait starts the frame after. */
    private boolean setupFrameDone;
    /** False until {@code Obj_Wait} has run {@code sub_78BD6}'s {@code $34(a0)}. */
    private boolean staggerElapsed;

    LrzMinibossHandChild(AbstractBossInstance parent, int childSubtype, boolean mirrored) {
        super(parent, "LRZMinibossHand", PRIORITY_BUCKET, 0x9D);
        this.mirrored = mirrored;
        this.childSubtype = childSubtype;
        // loc_787FE / loc_7880A, as for the links: subtype $16 is the last of the ring, so the
        // hand is the last thing to come alive.
        this.waitTimer = (mirrored ? MIRRORED_STAGGER_BASE : 0) + childSubtype * 2;
        // CreateChild8_TreeListRepeated copies the parent's x_pos/y_pos into every child
        // (sonic3k.asm:177196-177197). syncPositionWithParent() is a no-op for the hand because
        // loc_7897A anchors on parent3 instead, so without this the hand would sit at (0,0) until
        // its first volley -- which is a real position, off in the corner of the level, not a
        // harmless placeholder.
        if (parent != null) {
            this.currentX = parent.getX();
            this.currentY = parent.getY();
        }
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
        if (!setupFrameDone) {
            // loc_78922: SetUp_ObjAttributes3, sub_78BD6, then the $34 body is not yet live.
            setupFrameDone = true;
            return;
        }
        if (!staggerElapsed) {
            // Wait_Draw -> Obj_Wait, then $34(a0) = the tail of loc_78922 that installs
            // loc_78946 and collision_property 4. loc_78946 first runs the frame after.
            waitTimer--;
            if (waitTimer < 0) {
                staggerElapsed = true;
            }
            return;
        }
        if (routine == ROUTINE_IDLE) {
            // loc_78946: arm when the boss extends. No positional work, and no Draw_Sprite.
            if (bossFlagSet(1 << 3)) {
                routine = ROUTINE_FIRING;
                waitTimer = mirrored ? ARM_DELAY_MIRRORED : ARM_DELAY;
                shotCounter = 0;                        // clr.b $39(a0)
            }
            updateDynamicSpawn();
            return;
        }
        // loc_7897A. The bit-2 test only reinstalls loc_78946 for the NEXT frame; this frame
        // still falls through to loc_7898C and animates, moves, waits and fires.
        if (bossFlagSet(1 << 2)) {
            routine = ROUTINE_IDLE;
        }
        advanceAnimation();
        moveAtAngleLookup();
        waitTimer--;
        if (waitTimer < 0) {
            fire();
        }
        updateDynamicSpawn();
    }

    /**
     * {@code MoveSprite_AtAngleLookup} (sonic3k.asm:178504-178522) with {@code a2 = AngleLookup_2}
     * and {@code $3C = $80}: the hand hangs off {@code parent3}, the tenth arm link.
     */
    private void moveAtAngleLookup() {
        int anchorX;
        int anchorY;
        BossChildComponent anchor = LrzMinibossRingChild.predecessorOf(parent, this);
        if (anchor != null && !anchor.isDestroyed()) {
            anchorX = anchor.getX();
            anchorY = anchor.getY();
        } else if (parent != null && !parent.isDestroyed()) {
            anchorX = parent.getX();
            anchorY = parent.getY();
        } else {
            return;
        }
        int low = FIXED_ANGLE & 0x3F;
        int quadrant = (FIXED_ANGLE >> 5) & 6;
        int along = ANGLE_LOOKUP_2[low];
        int across = ANGLE_LOOKUP_2[0x3F - low];
        int dx;
        int dy;
        switch (quadrant) {
            case 0 -> { dx = along;  dy = across; }     // AtAngle_00_3F
            case 2 -> { dx = across; dy = -along; }     // AtAngle_40_7F
            case 4 -> { dx = -along; dy = -across; }    // AtAngle_80_BF
            default -> { dx = -across; dy = along; }    // AtAngle_C0_FF
        }
        // move.w d2,x_pos(a0) / move.w d3,y_pos(a0): words, so no sub-pixel carry here.
        currentX = anchorX + dx;
        currentY = anchorY + dy;
    }

    /** {@code loc_789CA} (sonic3k.asm:160448-160462). */
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

    /**
     * {@code Animate_Raw} / {@code Animate_RawNoSST} (sonic3k.asm:177333-177362) over
     * {@code byte_78E05}. {@code anim_frame} steps by <b>one</b> and the frame read is
     * {@code 1(a1,d0.w)}; the delay for every frame is byte 0.
     */
    private void advanceAnimation() {
        animTimer--;
        if (animTimer >= 0) {
            return;
        }
        animFrame++;
        int value = animFrame + 1 < FIRE_ANIMATION.length ? FIRE_ANIMATION[animFrame + 1] : 0xFC;
        if (value >= 0x80) {
            // AnimateRaw_Restart: mapping_frame = 1(a1), anim_frame_timer = (a1), anim_frame = 0.
            mappingFrame = FIRE_ANIMATION[1];
            animTimer = FIRE_ANIMATION[0];
            animFrame = 0;
            return;
        }
        mappingFrame = value;
        animTimer = FIRE_ANIMATION[0];
    }

    /**
     * {@code sub_78CF4} / {@code loc_78D2C} (sonic3k.asm:160694-160730): four hits, then the
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

    int getMappingFrame() {
        return mappingFrame;
    }

    /** {@code $2E(a0)}: frames still to wait before {@code loc_78946} takes over. */
    int getStaggerRemaining() {
        return staggerElapsed ? -1 : waitTimer;
    }

    boolean isFiring() {
        return staggerElapsed && routine == ROUTINE_FIRING;
    }

    @Override public int ringSubtype() { return childSubtype; }
    @Override public boolean ringMirrored() { return mirrored; }
    @Override public int ringXFixed() { return currentX << 16; }
    @Override public int ringYFixed() { return currentY << 16; }

    private boolean bossFlagSet(int mask) {
        return parent instanceof LrzMinibossInstance boss && (boss.getFlags38() & mask) != 0;
    }

    /** {@code loc_78946} has no {@code Draw_Sprite}: an idle hand is not drawn at all. */
    @Override public void syncPositionWithParent() { /* loc_7897A anchors on parent3, not $44 */ }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != ROUTINE_FIRING) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, mirrored, false);
    }
}
