package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Tidal Tempest boss, object $4C ({@code EggmanObject}, objects/r4/boss.asm:3-515; ID from r4/object_index_3.asm),
 * placed by the act 3 layout at ($AB0, $520), under water. Both futures run the same code; only the music after
 * the escape differs ({@code good_future}). Every frame, before its routine, it clears {@code var_2c} bit 6 (one
 * bubble pops per frame), times out its pose ({@code var_2b}) or, with none running, laughs (pose 1 for 120 frames)
 * while the player is hurt or dead.
 *
 * <ul>
 *   <li>Routine 0 sets two hits ({@code collide_status} 2, type still 0) and allocates the Egg Mobile; routine 2
 *       pins the right boundary at $9B0 and drags the left one to player X - $A0; at player X $A50 the boss music
 *       starts and both lock at $9B0 (routine 6).</li>
 *   <li>Routine 6 allocates a decorative bubble, then sixteen ring bubbles 20 frames apart (FM $B6 each); routine 8
 *       waits 150 frames, routine A 150 more, then the fight starts on an ellipse of radii $60 and $40 about
 *       ($A50, $520).</li>
 *   <li>Routines C and E sweep half the ellipse ($100 a frame), firing four missiles at $800 (or $7800); routine $10
 *       pauses 96 frames, turning him at frame 48, then sweeps the other way. With the bubbles left at or under
 *       $E, $C, $A, 8, 6, 4 he instead dashes one pixel a frame across the arena (routine $16): laughing at 20 frames
 *       (the ring regroups), firing at 45, and resuming the ellipse on the other side.</li>
 *   <li>In routines C, E and $10 the {@code $3C} enemy type is restored when cleared; a clear type with one hit
 *       left is the defeat: routine $12 explodes for 60 frames (missiles deleted), turns him right and gives 1000
 *       points; routine $14 pops every bubble and, after 60 frames, flies right at $1.C until X $B30, deleting the
 *       Egg Mobile; routine 4 widens the right boundary 6 a frame to the stage's, then clears
 *       {@code boss_started}, plays the zone's future music, loads the capsule palette and deletes itself.</li>
 * </ul>
 *
 * <p>Touch: $3C is an enemy type; {@code Player_TouchEnemy} rebounds a rolling player (the shared bounce, halved),
 * clears the type and counts {@code collide_status} down, or hurts anyone else (r4/player_object_collide.asm:125-145).
 * The bubble ring is what keeps the player out. Routine $16 does not restore the type, so a hit there is taken on
 * the return to routine $10 ({@code FixBugs = 0}).
 */
public final class TtzBossEggman extends TtzBossPart implements TouchResponseAttackable {
    /** The body mirrors by {@code oFlags} bit 0 ({@link TtzBossPart#facingByFlags}). */
    @Override
    public com.openggf.level.objects.ObjectVisualFacing.Basis nativeFacing() {
        return facingByFlags();
    }

    /** Object $4C: EggmanObject is index entry $4B (r4/object_index_3.asm:3-77). */
    public static final int ID = 0x4C;
    /** EggmanObject_0_Routine2: move.w #$67,d0 (SCMD_BOSSMUS); jsr SubCpuCommand (objects/r4/boss.asm:73-74). */
    static final int SCMD_BOSSMUS = 0x67;
    /** loc_20C77A: $17 (SCMD_R4DMUS), $16 (SCMD_R4CMUS) in the good future (objects/r4/boss.asm:389-395). */
    static final int SCMD_R4CMUS = 0x16, SCMD_R4DMUS = 0x17;
    /** loc_20C4E6: move.w #$B6,d0; jsr PlayFmSound (objects/r4/boss.asm:104-105), each ring bubble. */
    static final int FM_BUBBLE = 0xB6;
    /** loc_20C7EE: move.b #$3C,obj.collide_type (objects/r4/boss.asm:435). */
    static final int COLLISION = 0x3C;
    /** EggmanObject_0_Routine2: move.w #$9B0,d0 (objects/r4/boss.asm:63, 76-79): the arena's camera X. */
    static final int ARENA_X = 0x9B0;
    /** sub_20CEAE: move.w #$5B8,obj.y(a1) (objects/r4/boss.asm:1116): where the bubbles start. */
    static final int BUBBLE_START_Y = 0x5B8;
    /** var_2c bits (objects/r4/boss.asm:4, 190, 249, 316, 445, 1028). */
    private static final int REVERSED = 0x08, TURNING = 0x10, REGROUP = 0x20, POPPED = 0x40, DEFEATED = 0x80;

    /** var_30: the Egg Mobile. */
    TtzEggMobile mobile;
    /** var_2a: the routine's frame counter. */
    private int timer;
    /** var_2b: frames left in the current pose. */
    private int poseTimer;
    private int bits;
    /** var_2d: ring bubbles allocated, then left. */
    int bubbles;
    /** var_32: the regrouping ring's next place; var_33: the next attack threshold. */
    int regroupCount;
    private int attacks;
    /** var_34: the ellipse angle; var_38/var_3a: the last offset (one long for {@code neg.l}); var_3c, x_speed: radii. */
    private int angle;
    private int offsetX;
    private int offsetY;
    private int radiusX;
    private int radiusY;

    public TtzBossEggman(ObjectSpawn spawn) {
        super(spawn, "TtzBossEggman");
    }

    /** {@code collide_status}: hits left (2, then 1 once hit). */
    public int hitsLeft() { return colStatus & 0xFF; }

    boolean reversed() { return (bits & REVERSED) != 0; }

    boolean turning() { return (bits & TURNING) != 0; }

    boolean regrouping() { return (bits & REGROUP) != 0; }

    boolean defeated() { return (bits & DEFEATED) != 0; }

    /** {@code bset #6,obj.var_2c(a1)}: returns whether a bubble already popped this frame. */
    boolean markPopped() {
        boolean was = (bits & POPPED) != 0;
        bits |= POPPED;
        return was;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = player instanceof AbstractPlayableSprite s ? s : null;
        hidden = false;
        bits &= ~POPPED;                                                // bclr #6,obj.var_2c(a0)
        if (poseTimer != 0) {
            poseTimer = (poseTimer - 1) & 0xFF;
            if (poseTimer == 0) restartAnimation(0);
        } else {
            laughCheck(sonic);                                          // loc_20C3F8: bsr.w sub_20C7AA
        }
        switch (routine) {
            case 0 -> { init(); waitForPlayer(sonic); }                 // Routine0 falls into Routine2
            case 2 -> waitForPlayer(sonic);
            case 4 -> { widenRightBound(); return; }                    // always addq.l #4,sp
            case 6 -> spawnBubbles();
            case 8 -> settle();
            case 0xA -> countdown();
            case 0xC -> sweep(0x100, 0x800, 0x8000);
            case 0xE -> sweep(-0x100, 0x7800, 0);
            case 0x10 -> pause();
            case 0x12 -> exploding();
            case 0x14 -> escape();
            case 0x16 -> dash();
            default -> { }                                              // Routines $18 and $1A: rts
        }
        if (hidden || isDestroyed()) return;
        animateObject("eggmanAnimations");
    }

    /** EggmanObject_0_Routine0 (objects/r4/boss.asm:51-60). */
    private void init() {
        routine = 2;
        priority = 4;
        width = 0x20;
        yRadius = 0x2C;
        tileWord = TtzBossArt.BOSS_TILE;
        colStatus = 2;                                                  // move.b #2,obj.collide_status
        // sub_20CED2 (objects/r4/boss.asm:1123-1134): FindObjSlot the Egg Mobile at this position.
        TtzEggMobile newMobile = new TtzEggMobile(x, y, this);
        if (spawn(newMobile)) mobile = newMobile;
    }

    /** EggmanObject_0_Routine2 (objects/r4/boss.asm:62-86). */
    private void waitForPlayer(AbstractPlayableSprite player) {
        Camera camera = services().camera();
        if (camera == null || player == null) return;
        camera.setMaxX((short) ARENA_X);                                // right_bound, target_right_bound
        int left = (short) (player.getCentreX() - 0xA0);
        if (left < camera.getMinX()) return;                            // cmp.w left_bound,d0; blt.s
        if ((short) player.getCentreX() >= 0xA50) {
            subCpu(SCMD_BOSSMUS);
            routine = 6;
            camera.setMaxX((short) ARENA_X);
            left = ARENA_X;
        }
        camera.setMinX((short) left);                                   // left_bound, target_left_bound
    }

    /** EggmanObject_0_Routine6 (objects/r4/boss.asm:90-115). */
    private void spawnBubbles() {
        holdPosition();
        timer = (timer + 1) & 0xFF;
        if (timer == 0xA) spawnBubble(true);                            // jsr sub_20CEA2: the decorative bubble
        if (timer != 0x14 && timer < 0x1E) return;
        fmSound(FM_BUBBLE);                                             // loc_20C4E6
        timer = 0;
        TtzBossBubble bubble = spawnBubble(false);
        if (bubble != null) bubble.index = bubbles;                     // move.b obj.var_2d(a0),obj.var_2d(a1)
        bubbles = (bubbles + 1) & 0xFF;
        if (bubbles == 0x10) routine = 8;
    }

    /** sub_20CEAE and sub_20CEA2 (objects/r4/boss.asm:1102-1119): a bubble at X, Y $5B8. */
    private TtzBossBubble spawnBubble(boolean decorative) {
        TtzBossBubble bubble = new TtzBossBubble(x, BUBBLE_START_Y, this);
        if (!spawn(bubble)) return null;
        if (decorative) bubble.routine2 = 1;                            // move.b #1,obj.routine_2(a1)
        return bubble;
    }

    /** sub_20C514 (objects/r4/boss.asm:119-134): sink $0.4650 a frame to Y $520, then hold ($AB0, $520). */
    private void holdPosition() {
        if (yWord() < 0x520) {
            addYLong(0x4650);
            return;
        }
        setXLong(0xAB00000L);
        setYLong(0x5200000L);
    }

    /** EggmanObject_0_Routine8 (objects/r4/boss.asm:138-147). */
    private void settle() {
        holdPosition();
        timer = (timer + 1) & 0xFF;
        if (timer != 0x96) return;
        timer = 0;
        routine = 0xA;
    }

    /** EggmanObject_0_RoutineA (objects/r4/boss.asm:151-164). */
    private void countdown() {
        timer = (timer + 1) & 0xFF;
        if (timer != 0x96) return;
        routine = 0xC;
        radiusX = 0x60;
        radiusY = 0x40;
        angle = 0;
        offsetX = 0x60;
        offsetY = 0;
        timer = 0;
    }

    /**
     * EggmanObject_0_RoutineC / _RoutineE (objects/r4/boss.asm:168-218): half the ellipse, {@code step} a frame;
     * missiles at {@code fireAt}; at {@code endAt} routine $10 with {@code var_2c} bit 4 (the ring turns).
     */
    private void sweep(int step, int fireAt, int endAt) {
        hitCheck();
        angle = (angle + step) & 0xFFFF;
        if (angle == fireAt) fireMissiles();
        if (angle == endAt) {
            timer = 0;
            routine = 0x10;
            bits |= TURNING;
            return;
        }
        addX(-offsetX);                                                 // move.w obj.var_38,d0; sub.w d0,obj.x
        addY(-offsetY);
        int[] offset = orbit(angle, radiusX, radiusY);
        offsetX = offset[0];
        offsetY = offset[1];
    }

    /** EggmanObject_0_Routine10 (objects/r4/boss.asm:222-291). */
    private void pause() {
        bits &= ~(TURNING | REGROUP);
        hitCheck();
        timer = (timer + 1) & 0xFF;
        if (timer == 0x30) {                                            // loc_20C642: both turn
            if (mobile != null) {
                mobile.flipX = !mobile.flipX;
                mobile.facing = !mobile.facing;
            }
            flipX = !flipX;
            facing = !facing;
            return;
        }
        if (timer != 0x60) return;
        timer = 0;                                                      // loc_20C660
        if (facing) {
            bits |= REVERSED;
            routine = 0xE;
        } else {
            bits &= ~REVERSED;
            routine = 0xC;
        }
        // loc_20C686: an attack for each threshold the bubbles left have reached.
        CdStageImage image = stageImage();
        if (image == null) return;
        while (true) {
            int threshold = TtzBossCatalogue.bubbleThreshold(image, payload(), attacks);
            if (threshold < 0 || threshold < (byte) bubbles) return;   // bmi.s; cmp.b obj.var_2d,d0; blt.w
            attacks = (attacks + 1) & 0xFF;                             // sub_20C6A0
            routine = 0x16;
        }
    }

    /** EggmanObject_0_Routine16 (objects/r4/boss.asm:295-356): the dash. */
    private void dash() {
        timer = (timer + 1) & 0xFF;
        if (timer == 0x14) {                                            // loc_20C6DC
            laugh();
            regroupCount = 0;
            bits |= REGROUP;
        } else if (timer == 0x15) {
            bits &= ~REGROUP;                                           // loc_20C6EC
        } else if (timer == 0x2D) {
            fireMissiles();                                             // loc_20C6D4
        }
        if (!reversed()) {                                              // loc_20C6F2
            addXLong(-0x10000);
            if (xWord() > 0x9F0) return;
        } else {
            addXLong(0x10000);                                          // loc_20C70C
            if (xWord() < 0xAB0) return;
        }
        // loc_20C71E: neg.l of var_38/var_3a as one long; the ellipse resumes on the far side.
        long negated = (-(((long) (offsetX & 0xFFFF) << 16) | (offsetY & 0xFFFF))) & 0xFFFFFFFFL;
        offsetX = (int) (negated >>> 16);
        offsetY = (int) (negated & 0xFFFF);
        angle = (angle - 0x8000) & 0xFFFF;                              // addi.w #-$8000,obj.var_34
        setXLong(0xA500000L);
        addXLong(negated);                                              // move.l obj.var_38,d0; add.l d0,obj.x
        setYLong(0x5200000L);
        timer = 0;
        routine = 0x10;
        bits |= TURNING;
    }

    /** sub_20CEFA (objects/r4/boss.asm:1138-1176): four missiles, mirrored when he faces right. */
    private void fireMissiles() {
        CdStageImage image = stageImage();
        if (image == null) return;
        for (int i = 0; i < 4; i++) {
            int[] launch = TtzBossCatalogue.missileLaunch(image, payload(), i);
            int dx = launch[0];
            long speedX = launch[2];
            if (facing) {                                               // btst #0,obj.flags(a0); neg.w d1; neg.l d3
                dx = -dx;
                speedX = -speedX;
            }
            TtzBossMissile missile = new TtzBossMissile(x, y, this, dx, launch[1], speedX, launch[3]);
            if (!spawn(missile)) return;                                // bne.s loc_20CF56
        }
    }

    /** sub_20C7E6 (objects/r4/boss.asm:427-451): restore the type; a hit with one left is the defeat. */
    private void hitCheck() {
        if (colType != 0) return;
        colType = COLLISION;
        if (colStatus != 1) return;
        timer = 0;                                                      // loc_20C7FE
        routine = 0x12;
        bits |= DEFEATED;
        restartAnimation(2);
        // bra.w *+4 (objects/r4/boss.asm:451): the branch lands on the next instruction, EggmanObject_0_Routine12,
        // so its first count runs on the hit frame (native R43D-fight: routine $14 and the escape a frame earlier).
        exploding();
    }

    /** EggmanObject_0_Routine12 (objects/r4/boss.asm:455-494). */
    private void exploding() {
        timer = (timer + 1) & 0xFF;
        if (timer < 0x3C) explosionCycle();                             // bcc.s loc_20C83A; bsr.w sub_20C928
        if (timer == 0x3C) {                                            // loc_20C84C
            poseTimer = 0xFF;
            restartAnimation(3);
            mapFrame = 1;                                               // move.b #1,obj.sprite_frame
            return;
        }
        if (timer != 0x3D) return;
        timer = 0;                                                      // loc_20C874
        routine = 0x14;
        flipX = true;
        facing = true;
        if (mobile != null) {
            mobile.flipX = true;
            mobile.facing = true;
        }
        var game = services().gameState();
        if (game != null) game.addScore(100 * 10);                      // moveq #100,d0; jsr AddPoints (units of 10)
    }

    /** sub_20C928 (objects/r4/boss.asm:551-580): every fourth count, an explosion at one of ten offsets. */
    private void explosionCycle() {
        if ((timer & 3) != 0) return;
        CdStageImage image = stageImage();
        if (image == null) return;
        int[] offset = TtzBossCatalogue.explosionOffset(image, payload(), (timer >> 2) % 10);
        explode(x + offset[0], y + offset[1]);
    }

    /** EggmanObject_0_Routine14 (objects/r4/boss.asm:498-515). */
    private void escape() {
        timer = (timer + 1) & 0xFF;
        if (timer < 0x3C) return;
        addXLong(0x1C000);
        if (xWord() < 0xB30) return;
        routine = 4;
        deleteOther(mobile);                                            // jsr DeleteOtherObject
        mobile = null;
    }

    /** EggmanObject_0_Routine4 (objects/r4/boss.asm:370-398): every path ends in addq.l #4,sp (no draw). */
    private void widenRightBound() {
        hidden = true;
        Camera camera = services().camera();
        CdStageImage image = stageImage();
        if (camera == null || image == null) return;
        int stageRight = TtzBossCatalogue.stageRightBound(image, payload());
        int right = (short) (camera.getMaxX() + 6);
        camera.setMaxX((short) right);                                  // addq.w #6,right_bound / target_right_bound
        if (stageRight > right) return;                                 // cmp.w right_bound,d1; ble.s loc_20C77A
        var state = services().gameState();
        if (state != null) state.setScreenLocked(false);                // clr.b boss_started
        camera.setMaxX((short) stageRight);
        subCpu(flags().goodFuture ? SCMD_R4CMUS : SCMD_R4DMUS);
        loadCapsulePalette();
        deleteObject();
    }

    /** sub_20C7AA (objects/r4/boss.asm:402-412): pose 1 while the player is hurt or dead. */
    private void laughCheck(AbstractPlayableSprite player) {
        if (anim != 0 || player == null) return;
        // tst.w obj.var_30(a1) (oPlayerHurt); cmpi.b #6,$24(a1) (dead).
        if (player.getInvulnerableFrames() == 0 && !player.getDead()) return;
        laugh();
    }

    /** loc_20C7C8 (objects/r4/boss.asm:416-423). */
    private void laugh() {
        poseTimer = 0x78;
        restartAnimation(1);
    }

    /**
     * {@code Player_TouchEnemy} (r4/player_object_collide.asm:125-145) with a non-zero {@code collide_status}: the
     * type is cleared and the status counted down; the shared touch owner applies the halved rebound.
     */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        colType = 0;
        colStatus = (colStatus - 1) & 0xFF;
    }

    @Override String sheet() { return TtzBossArt.EGGMAN; }

    @Override
    protected TtzBossEggman shell(ObjectSpawn spawn) {
        return new TtzBossEggman(spawn);
    }
}
