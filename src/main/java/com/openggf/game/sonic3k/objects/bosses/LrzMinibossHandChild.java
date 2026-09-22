package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The Lava Reef miniboss's firing hand: subtype {@code $16}, the last child of each ring
 * ({@code loc_78922}, sonic3k.asm:160378-160386, and {@code loc_78946}/{@code loc_7897A},
 * sonic3k.asm:160388-160432).
 *
 * <p>It is the only child with its own {@code collision_property} ({@code 4}, so four hits), and
 * killing both hands is what shortens the parent's hover from {@code $15F} to {@code $1F}
 * ({@code loc_78D2C}, sonic3k.asm:160778-160791, via {@code loc_787D8}).
 *
 * <p>Firing runs off the parent's {@code $38} bit 3. When the boss extends, the hand arms with
 * {@code $2E = $7F}, or {@code $DF} on the mirrored ring so the two sides alternate, and
 * {@code loc_789CA} (sonic3k.asm:160434-160453) fires: a projectile from
 * {@code ChildObjDat_78D90} with the shot counter {@code $39} as its subtype, the next reload at
 * {@code $13} frames, and after the third shot {@code $FFF} -- which is not a long reload so much
 * as a stop, because the parent's bit 2 (set when the arm finishes retracting) returns the hand to
 * its idle routine first.
 *
 * <p><b>What draws, and when.</b> {@code loc_78946} does no positional work and ends
 * {@code bra.w sub_78B46}: there is no {@code Draw_Sprite} after it, so an idle hand neither moves
 * nor draws. {@code loc_7897A} does draw -- and it draws on the frame it hands back to the idle
 * routine as well, because the {@code btst #2} test only rewrites {@code (a0)} for the next frame
 * and this frame still falls through {@code loc_7898C} to {@code loc_789C4}. The two routine-switch
 * frames therefore break the obvious rule in opposite directions, which is why the draw is a
 * per-frame decision here rather than a function of the routine.
 *
 * <p>The draw is also skipped on the odd frames of the hit window: {@code loc_7897A} ends on
 * {@code move.b $20(a0),d0 / beq -> Add_SpriteToCollisionResponseList + draw}, else
 * {@code btst #0,d0 / bne -> rts}. A flashing hand blinks, and only an unflashing hand is on the
 * collision-response list.
 *
 * <p>Position comes from {@code loc_7897A} alone, and it anchors on {@code parent3} -- the tenth
 * arm link, subtype {@code $14} -- through {@code MoveSprite_AtAngleLookup} over
 * {@code AngleLookup_2} (sonic3k.asm:178504-178523), not on the boss body. Syncing it to the parent
 * instead is what makes shots appear to leave the drill rather than the end of the arm.
 */
final class LrzMinibossHandChild extends LrzMinibossRingChildBase
        implements RewindRecreatable, TouchResponseProvider, TouchResponseAttackable {

    /** {@code word_78D6C}: priority 0, {@code $10 $10} size, mapping frame 6, collision 6. */
    private static final int BASE_MAPPING_FRAME = 6;
    private static final int PRIORITY_BUCKET = 0;
    /** {@code word_78D6C}'s last byte, which {@code SetUp_ObjAttributes3} puts in {@code collision_flags}. */
    private static final int ACTIVE_COLLISION_FLAGS = 6;
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
    /** {@code sub_78CF4}: {@code move.b #$20,$20(a0)}. */
    private static final int HIT_FLASH_FRAMES = 0x20;

    /**
     * {@code byte_78E05}: {@code dc.b 1,6,7,$B,$FC}, read by {@code Animate_Raw}
     * (sonic3k.asm:177333-177362). Byte 0 is the shared frame delay; the walk starts at
     * {@code anim_frame = 1} and reads {@code 1(a1,d0.w)}, so the frames are {@code 7}, {@code $B}
     * and then {@code $FC} -> {@code AnimateRaw_Restart}, which emits {@code 1(a1)} = {@code 6}.
     * Frame {@code 6} is therefore the restart target, not the first frame.
     */
    private static final int[] FIRE_ANIMATION = {1, 6, 7, 0x0B, 0xFC};

    /**
     * {@code AngleLookup_2} (sonic3k.asm:201852-201859), 64 bytes.
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
    /** {@code collision_flags(a0)}: {@code 6} until a hit zeroes it, then {@code 6} again. */
    private int collisionFlagsByte = ACTIVE_COLLISION_FLAGS;
    /** {@code $25(a0)}: where the touch pass stows {@code collision_flags}. */
    private int savedCollisionFlags;
    /** {@code $20(a0)}: the hit window {@code sub_78CF4} counts out. */
    private int hitWindowTimer;
    /** {@code $1C(a0)}: which player object address last hit this one. */
    private int attackerMarker;
    /** {@code status(a0)} bit 7, which the touch pass sets on the killing blow. */
    private boolean statusBit7;
    /** {@code anim_frame(a0)}: {@code Animate_Raw} steps it by one and reads {@code 1(a1,d0.w)}. */
    private int animFrame;
    /** {@code anim_frame_timer(a0)}. */
    private int animTimer;
    private int mappingFrame = BASE_MAPPING_FRAME;
    /** loc_78922 runs on the creation frame and returns; the wait starts the frame after. */
    private boolean setupFrameDone;
    /** False until {@code Obj_Wait} has run {@code sub_78BD6}'s {@code $34(a0)}. */
    private boolean staggerElapsed;
    /** Whether this frame's routine reached a {@code Draw_Sprite}. See the class comment. */
    private boolean drawnThisFrame;

    /** Restore construction uses the live concrete boss; snapshot fields restore the phase. */
    private LrzMinibossHandChild(LrzMinibossInstance parent) {
        this(parent, 0x16, false);
    }

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
        if (retirementTick()) {
            // Wait_Draw: Obj_Wait then Draw_Sprite. A parked hand is drawn even though an idle
            // one is not, because it is no longer running loc_78946.
            drawnThisFrame = true;
            updateDynamicSpawn();
            return;
        }
        drawnThisFrame = false;
        if (!setupFrameDone) {
            // loc_78922: SetUp_ObjAttributes3, sub_78BD6, then the $34 body is not yet live.
            setupFrameDone = true;
            return;
        }
        if (!staggerElapsed) {
            // Wait_Draw -> Obj_Wait, then $34(a0) = the tail of loc_78922 that installs
            // loc_78946 and collision_property 4. loc_78946 first runs the frame after.
            drawnThisFrame = true;
            waitTimer--;
            if (waitTimer < 0) {
                staggerElapsed = true;
            }
            return;
        }
        if (routine == ROUTINE_IDLE) {
            // loc_78946: arm when the boss extends. No positional work, and -- because it ends
            // bra.w sub_78B46 rather than falling into a draw -- no Draw_Sprite either, not even
            // on the frame it switches to loc_7897A.
            if (bossFlagSet(1 << 3)) {
                routine = ROUTINE_FIRING;
                waitTimer = mirrored ? ARM_DELAY_MIRRORED : ARM_DELAY;
                shotCounter = 0;                        // clr.b $39(a0)
            }
            sub78B46();
            updateDynamicSpawn();
            return;
        }
        // loc_7897A. The bit-2 test only reinstalls loc_78946 for the NEXT frame; this frame
        // still falls through to loc_7898C and animates, moves, waits, fires and draws.
        if (bossFlagSet(1 << 2)) {
            routine = ROUTINE_IDLE;
        }
        advanceAnimation();
        moveAtAngleLookup();
        waitTimer--;
        if (waitTimer < 0) {
            fire();
        }
        sub78B46();
        sub78CF4();
        // move.b $20(a0),d0 / beq -> collision list + draw; btst #0,d0 / bne -> rts.
        drawnThisFrame = hitWindowTimer == 0 || (hitWindowTimer & 1) == 0;
        updateDynamicSpawn();
    }

    /**
     * {@code MoveSprite_AtAngleLookup} (sonic3k.asm:178504-178523) with {@code a2 = AngleLookup_2}
     * and {@code $3C = $80}: the hand hangs off {@code parent3}, the tenth arm link.
     *
     * <p>{@code move.w x_pos(a1),d2} / {@code move.w y_pos(a1),d3} read the anchor's ROM position
     * words, which for these children are {@link LrzMinibossRingChild#ringXFixed} and
     * {@code ringYFixed}'s integer halves, and the sum is written back as words too: no sub-pixel
     * carry survives this hop. When the link is gone there is no anchor at all -- the ROM's
     * {@code parent3} would point at a dead slot -- so the hand stays where it is rather than
     * snapping back to the drill.
     */
    private void moveAtAngleLookup() {
        var anchor = LrzMinibossRingChild.predecessorOf(parent, this);
        if (!(anchor instanceof LrzMinibossRingChild link) || anchor.isDestroyed()) {
            return;
        }
        int anchorX = link.ringXFixed() >> 16;
        int anchorY = link.ringYFixed() >> 16;
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

    /** {@code loc_789CA} (sonic3k.asm:160434-160453). */
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
     * {@code sub_78CF4} (sonic3k.asm:160756-160776), the hand's own half of the hit.
     *
     * <p>Like the drill's {@code sub_78C14} it is gated on {@code collision_flags(a0)} being
     * <b>zero</b>: the shared touch pass zeroes the byte (stowing it in {@code $25}) and
     * decrements {@code collision_property}, so a non-zero byte here means nothing has landed.
     * Unlike the drill's, it has no palette flash -- there is no {@code sub_78C98} call -- and it
     * announces the hit by creating {@code ChildObjDat_78D98}'s ring instead. The window is
     * {@code $20} frames, and the byte is restored only when it counts out.
     */
    private void sub78CF4() {
        if (collisionFlagsByte != 0) {
            return;
        }
        if (hitsRemaining == 0) {
            loc78D2C();
            return;
        }
        if (hitWindowTimer == 0) {
            hitWindowTimer = HIT_FLASH_FRAMES;
            var objectServices = tryServices();
            if (objectServices != null) {
                objectServices.playSfx(Sonic3kSfx.BOSS_HIT.id);
            }
            final boolean ring = mirrored;
            spawnChild(() -> new LrzMinibossHitSparkChild(parent, ring));
        }
        hitWindowTimer = (hitWindowTimer - 1) & 0xFF;
        if (hitWindowTimer != 0) {
            return;
        }
        collisionFlagsByte = savedCollisionFlags;   // move.b $25(a0),collision_flags(a0)
    }

    /**
     * {@code loc_78D2C} (sonic3k.asm:160778-160791): set bit 6 (unmirrored) or bit 7 (mirrored) of
     * the parent's {@code $38}, and if both are now set drop the parent's {@code $2E} to
     * {@code $1F}. Setting the bit is also what {@code sub_78B46} reads, so this is the step that
     * peels the dead hand's arm away.
     */
    private void loc78D2C() {
        if (parent instanceof LrzMinibossInstance boss) {
            boss.onHandDestroyed(mirrored);
        }
    }

    /**
     * The shared touch pass's boss bookkeeping (sonic3k.asm:20916-20923): stow
     * {@code collision_flags} in {@code $25}, record the attacking player's object address in
     * {@code $1C}, zero {@code collision_flags}, decrement {@code collision_property}, and on the
     * blow that takes it to zero set {@code status} bit 7.
     */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (collisionFlagsByte == 0) {
            return;
        }
        savedCollisionFlags = collisionFlagsByte;
        attackerMarker = LrzMinibossInstance.attackerMarkerFor(player);
        collisionFlagsByte = 0;
        if (hitsRemaining > 0) {
            hitsRemaining--;
            if (hitsRemaining == 0) {
                statusBit7 = true;
            }
        }
    }

    @Override
    public int getCollisionFlags() {
        return collisionFlagsByte;
    }

    @Override
    public int getCollisionProperty() {
        return hitsRemaining;
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

    /** {@code $20(a0)}. */
    int getHitWindowTimer() {
        return hitWindowTimer;
    }

    /** {@code $1C(a0)}. */
    int getAttackerMarker() {
        return attackerMarker;
    }

    /** {@code status(a0)} bit 7. */
    boolean isStatusBit7Set() {
        return statusBit7;
    }

    /** Whether this frame reached a {@code Draw_Sprite}. */
    boolean wasDrawnThisFrame() {
        return drawnThisFrame;
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
        if (!drawnThisFrame) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, mirrored, false);
    }
}
