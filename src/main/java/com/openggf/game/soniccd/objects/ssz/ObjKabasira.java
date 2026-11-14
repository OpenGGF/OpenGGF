package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Kabasira, object $22 in acts 1 and 2 ({@code KabasiraObject}, objects/r7/kabasira.asm:3-235): a swarm of
 * mosquito-like badniks. One placed head (collision $31) spawns three followers with
 * {@code SpawnObjectAfter}; all four trace the same Lissajous figure about the head's placement
 * (X: {@code sin(phaseX) >> 4}, Y: {@code cos(phaseY) >> 2}, with the followers' phases staggered by
 * {@code -$1000} and {@code -$800} per step, :104-181). A subtype-0 head also shoots: when the player is within
 * {@code [-$78,$78)} horizontally and {@code [-$60,$60)} vertically (sub_20D646, :185-199) and its cooldown
 * ({@code oVar3A}, reloaded to $F0) is over, it spawns a shot ({@code SpawnObject}, secondary subtype
 * {@code $FE}, collision {@code $F1}, anim 8) aimed with Atan2 at {@code 2 px} per frame, and plays
 * {@code FM_A0} when on screen (:117-160). Subtype 1 heads do not shoot.
 *
 * <p>The three kinds are the same ID told apart by the secondary subtype (:4-8): head (>= 0), follower
 * ({@code $FF}, deleted when its head's slot is no longer ID $22, :12-18) and shot ({@code $FE}, :203-235). The
 * shot flies on its velocity, becomes explosion $18 with {@code oRoutine2 = 1} (no points, no animal) and plays
 * {@code FM_9E} when the player touches it, and is deleted when the player is more than $E0 above or below it.
 * Heads and followers end in {@code CheckObjectDespawn}; heads begin with {@code DestroyInGoodFuture}.
 *
 * <p>The sine terms are {@code asr.l} of {@code sine << 16} added to origins whose fraction is $8000; the
 * unspecified register bits that fill the low word cannot change the integer position (see {@link ObjKanabun}).
 * The shot's velocity low word is the unspecified upper word of {@code d1} at entry, modelled as zero.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjKabasira extends SszBadnik {
    /** The kinds named by the secondary subtype. */
    private enum Kind { HEAD, FOLLOWER, SHOT }

    /** The shot's touch: the enemy attack path with no bounce (loc_20703E -> loc_206FE8 in r7/player_object_collide.asm). */
    private static final TouchResponseProfile SHOT_TOUCH = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL, false, true, false, TouchShieldDeflectCapability.NONE, 0, false,
            TouchAttackBouncePolicy.NONE, TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    private static final int HEAD_COLLISION = 0x31;
    private static final int SHOT_COLLISION = 0xF1;
    private static final int SHOT_ANIM = 8;
    private static final int SHOOT_COOLDOWN = 0xF0;
    /** {@code FM_A0} (:159) and {@code FM_9E} (:217). */
    private static final int FM_SHOT = 0xA0;
    private static final int FM_BURST = 0x9E;

    private final Kind kind;
    private ObjKabasira head;
    /** A follower's obj.var_3e: the head's slot ({@code move.w a0,obj.var_3e(a1)}, objects/r7/kabasira.asm:80). */
    private final int headSlot;
    private SszAnimator animator;
    /** oVar2A:oVar2C and oVar2E:oVar30 as 16.16 longs: the figure's origin, or the shot's velocity. */
    private int originX;
    private int originY;
    private int velocityX;
    private int velocityY;
    /** oVar32/oVar34 (phases) and oVar36/oVar38 (steps). */
    private int phaseX;
    private int phaseY;
    private int stepX;
    private int stepY;
    /** oVar3A: the shot cooldown. */
    private int cooldown;
    private int anim;
    private boolean burst;

    /** A head placed in the layout. */
    public ObjKabasira(ObjectSpawn spawn) {
        this(spawn, Kind.HEAD, null);
    }

    private ObjKabasira(ObjectSpawn spawn, Kind kind, ObjKabasira head) {
        super(spawn, "Kabasira");
        this.kind = kind;
        this.head = head;
        this.headSlot = head == null ? -1 : head.getSlotIndex();
    }

    @Override protected String sheetKind() { return SszObjectArt.KABASIRA; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (kind) {
            case SHOT -> updateShot(player);
            case FOLLOWER -> {
                if (!slotHoldsId(headSlot, 0x22)) {                    // loc_20D42E: cmpi.b #$22,obj.id(a1) (:12-17)
                    setDestroyed(true);
                    return;
                }
                run(player);
            }
            case HEAD -> {
                if (destroyInGoodFuture()) return;                    // loc_20D440
                run(player);
            }
        }
    }

    /** loc_20D446 (:26-33). */
    private void run(PlayableEntity player) {
        if (routine == 0) {
            if (artProvider() == null) return;
            init();
        } else if (routine == 2) {
            main(player);
        }
        animate();
    }

    private void animate() {
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
        if (animator.showedFrame()) spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
    }

    /** sub_20D532 (:103-112) for this object: routine 2, priority 3, radii and tile. */
    private void commonInit() {
        routine = 2;
        priority = 3;
        width = 0xC;
        yRadius = 8;
        tileWord = SszObjectArt.KABASIRA_TILE;
        initialised = true;
    }

    /** KabasiraObject_0_Routine0 (:43-99), the head only. */
    private void init() {
        var provider = artProvider();
        colType = HEAD_COLLISION;
        originX = (x << 16) | 0x8000;
        originY = (y << 16) | 0x8000;
        boolean slow = spawn.subtype() != 0;
        stepX = slow ? 0x100 : 0x200;
        stepY = slow ? 0x80 : 0x100;
        anim = slow ? 0 : 4;
        animator = SszObjectArt.animator(provider, SszObjectCatalogue.Entry::kabasiraAnimations, anim);
        commonInit();
        int offsetX = slow ? 0x2000 : 0x1000;
        int offsetY = slow ? 0x1000 : 0x800;
        int phaseOffsetX = 0;
        int phaseOffsetY = 0;
        var objects = services().objectManager();
        for (int n = 1; n <= 3; n++) {
            phaseOffsetX -= offsetX;
            phaseOffsetY -= offsetY;
            ObjKabasira follower = new ObjKabasira(new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), 0, false, 0),
                    Kind.FOLLOWER, this);
            follower.anim = n + anim;                                // add.b obj.anim_id(a0),d0
            follower.phaseX = phaseOffsetX & 0xFFFF;
            follower.phaseY = phaseOffsetY & 0xFFFF;
            follower.originX = originX;
            follower.originY = originY;
            follower.stepX = stepX;
            follower.stepY = stepY;
            follower.animator = SszObjectArt.animator(provider, SszObjectCatalogue.Entry::kabasiraAnimations, follower.anim);
            follower.commonInit();
            if (objects == null) continue;
            objects.addDynamicObjectAfterCurrent(follower);          // jsr SpawnObjectAfter
            if (follower.isDestroyed()) {                            // bne.w loc_20D43A: jmp DeleteObject (the head)
                setDestroyed(true);
                return;
            }
        }
    }

    /** KabasiraObject_0_Routine2 (:116-181). */
    private void main(PlayableEntity entity) {
        int highWord = 0;                                            // the upper word of d0 here (see the class comment)
        if (kind == Kind.HEAD && spawn.subtype() == 0) {
            if (cooldown != 0) {
                cooldown--;
            } else if (entity instanceof AbstractPlayableSprite player) {
                shoot(player);
            }
        }
        phaseX = (phaseX + stepX) & 0xFFFF;
        int sine = TrigLookupTable.sinHex((phaseX >> 8) & 0xFF);
        int xLong = originX + (((sine << 16) | highWord) >> 4);
        x = (xLong >>> 16) & 0xFFFF;
        xSub = xLong & 0xFFFF;
        phaseY = (phaseY + stepY) & 0xFFFF;
        int cosine = TrigLookupTable.cosHex((phaseY >> 8) & 0xFF);
        int yLong = originY + ((cosine << 16) >> 2);
        y = (yLong >>> 16) & 0xFFFF;
        ySub = yLong & 0xFFFF;
    }

    /** loc_20D584 (:128-160) with sub_20D646 (:185-199). */
    private void shoot(AbstractPlayableSprite player) {
        int dy = ((player.getCentreY() & 0xFFFF) - y) & 0xFFFF;
        if (((dy + 0x60) & 0xFFFF) >= 0xC0) return;                  // subi.w #-$60,d0; subi.w #$C0,d0; bcc.s
        int dx = ((player.getCentreX() & 0xFFFF) - x) & 0xFFFF;
        if (((dx + 0x78) & 0xFFFF) >= 0xF0) return;                  // subi.w #-$78,d0; subi.w #$F0,d0
        cooldown = SHOOT_COOLDOWN;
        var objects = services().objectManager();
        if (objects == null) return;
        ObjKabasira shot = new ObjKabasira(new ObjectSpawn(x, y, spawn.objectId(), 0, 0, false, 0), Kind.SHOT, this);
        shot.anim = SHOT_ANIM;
        shot.colType = SHOT_COLLISION;
        shot.commonInit();
        shot.animator = SszObjectArt.animator(artProvider(), SszObjectCatalogue.Entry::kabasiraAnimations, SHOT_ANIM);
        int angle = TrigLookupTable.calcAngle((short) dx, (short) dy);            // jsr Atan2
        shot.velocityX = (TrigLookupTable.cosHex(angle & 0xFF) << 16) >> 7;
        shot.velocityY = (TrigLookupTable.sinHex(angle & 0xFF) << 16) >> 7;
        if (shot.velocityX < 0) shot.flipHorizontally();             // bchg #0,obj.sprite_flags(a1); bchg #0,obj.flags(a1)
        objects.addDynamicObject(shot);                              // jsr SpawnObject
        if (shot.isDestroyed()) return;
        if (renderFlagOnScreen()) services().playSfx(FM_SHOT);       // tst.b obj.sprite_flags(a0); bpl.s; move.w #$A0
    }

    /** loc_20D66E (:203-235). */
    private void updateShot(PlayableEntity entity) {
        long px = ((long) (x & 0xFFFF) << 16 | xSub) + velocityX;
        x = (int) (px >>> 16) & 0xFFFF;
        xSub = (int) px & 0xFFFF;
        long py = ((long) (y & 0xFFFF) << 16 | ySub) + velocityY;
        y = (int) (py >>> 16) & 0xFFFF;
        ySub = (int) py & 0xFFFF;
        if (burst) {                                                  // tst.b obj.collide_status(a0)
            services().playSfx(FM_BURST);
            becomeExplosion(0, 0, 0, false);                          // DeleteObject; id $18; oRoutine2 = 1
            return;
        }
        int dy = entity == null ? 0 : (((AbstractPlayableSprite) entity).getCentreY() & 0xFFFF) - y;
        if (Math.abs((short) dy) > 0xE0) {                            // cmpi.w #$E0,d0; bgt.w loc_20D43A
            setDestroyed(true);
            return;
        }
        animate();
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (kind == Kind.SHOT) {
            burst = true;                                             // loc_206FE8: addq.b #1,obj.collide_status(a1)
            return;
        }
        super.onPlayerAttack(player, result);
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return getTouchResponseProfile(false);
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return kind == Kind.SHOT ? SHOT_TOUCH : TouchResponseProfile.fromProvider(this, multiRegionSource);
    }

    @Override
    public boolean usesEnemyTouchCategoryOverride() {
        return kind == Kind.SHOT;
    }
}
