package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Palmtree Panic boss, object $2A ({@code EggmanObject}, objects/r1/boss.asm:3-698; IDs from Palmtree
 * Panic/Object Index (Act 3).asm:7-71, the ID being the entry's position plus one), placed by the act 3
 * layout at ($C10, $68). Both futures run the same code; only the music after the escape differs
 * ({@code goodFuture}). Every frame, before its routine, it times out its pose, holds the bottom boundary
 * ($310, or $100 with a one-pixel shake while {@code bossFlags} is set), laughs when the player is hurt
 * or dead, and runs the hit checks.
 *
 * <ul>
 *   <li>Routine 0 queues PLC 4 (boss and capsule art), sets {@code bossFight}, moves to ($C52, $78) and loads
 *       palette entry 5 (line 1).</li>
 *   <li>Routine 2 pins the right boundary at $AC0 and, from player X $A6A, drags the left boundary to player X - $A0;
 *       at player X $B60 it locks both at $AC0.</li>
 *   <li>Routine C raises {@code camYCenter} by 6 a frame to $C8, then starts the boss music and sets
 *       {@code bossFlags}; routine E waits 60 frames, moves to ($BD2, $78) and allocates the machine:
 *       body, two legs (thigh, calf, foot), shoulders, two arms (arm, forearm, bumper).</li>
 *   <li>Routine 4 steps the body through its walk table and starts the fight on the body's second
 *       handshake: three hits, each stripping an arm.</li>
 *   <li>Routine 6 explodes for 120 frames (the body is deleted at 94), routine 8 flies up and away, and
 *       at X $C80 the stage music returns, the capsule palette loads, {@code bossFlags} and {@code bossFight}
 *       clear; routine A widens the right boundary 6 a frame to the stage's and deletes itself.</li>
 * </ul>
 *
 * <p>Touch: Eggman ($FC) and the bumpers ($FF) are {@code $C0} types of sizes $3C-$3F, which the zone's
 * {@code Player_TouchSpecial} sends through {@code Player_TouchEnemy} while {@code bossFight} is 1
 * (Palmtree Panic/Player Object Collision.asm:299-322): a rolling player rebounds (the shared boss
 * bounce, halved), anyone else is hurt, and the type is cleared either way. A cleared Eggman type is a
 * hit even when the toucher was hurt ({@code FixBugs = 0}: no attack test). The calves ($BD) and feet
 * ($BE) are hazards.
 */
public final class PpzBossEggman extends PpzBossPiece implements TouchResponseListener {
    /** Object ID $2A: EggmanObject is index entry $29 (Palmtree Panic/Object Index (Act 3).asm:7-49). */
    public static final int ID = 0x2A;
    /** sub_20BAA8: move.w #$98,d0 (FM_SPRING); jsr PlayFmSound (objects/r1/boss.asm:135-140). */
    static final int FM_BUMPER = 0x98;
    /** EggmanObject_0_RoutineC: move.w #$67,d0 (SCMD_BOSSMUS); jsr SubCpuCommand (objects/r1/boss.asm:510-512). */
    static final int SCMD_BOSSMUS = 0x67;
    /** loc_20C16C: $11 (SCMD_R1DMUS), $10 (SCMD_R1CMUS) in the good future (objects/r1/boss.asm:666-674). */
    static final int SCMD_R1CMUS = 0x10, SCMD_R1DMUS = 0x11;
    /** EggmanObject_0_Routine0: moveq #4,d0; jsr AddGfxQueue (objects/r1/boss.asm:307-309). */
    static final int BOSS_PLC = 4;
    /** EggmanObject_0_Routine0: move.w #5,d0; jsr LoadPalette (objects/r1/boss.asm:326-329). */
    static final int BOSS_PALETTE = 5;

    // var_30 / var_32: the body and the shoulders.
    PpzBossBody body;
    PpzBossShoulder shoulders;
    /** var_2a: frames left in the current pose. */
    private int poseTimer;
    /** var_34: frames of hit immunity; var_35: frames of floor shake. */
    private int immunity;
    int shake;
    /** var_3c and x_speed words: the defeat position the escape explodes around. */
    private int savedX;
    private int savedY;
    /** var_38: the escape's last sine offset. */
    private int bob;
    private int routine2;
    /** Level-wide {@code bossFight} and {@code bossFlags}, which this object owns. */
    private boolean bossFight;
    private boolean bossFlags;

    public PpzBossEggman(ObjectSpawn spawn) {
        super(spawn, "PpzBossEggman");
    }

    /** {@code bossFight} (Level/_Definitions.inc:316): the touch gate and the level's boss state. */
    public boolean bossFight() { return bossFight; }

    /** {@code bossFlags} (Level/_Definitions.inc:314). */
    public boolean bossFlags() { return bossFlags; }

    /** var_2b: hits left during the fight. */
    public int hitsLeft() { return phase; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = player instanceof AbstractPlayableSprite s ? s : null;
        poseTimeout();
        holdBottomBound();
        laughCheck(sonic);
        if (hitChecks(sonic)) {
            // A path that ended in the hit routines still animates and draws.
        }
        boolean skipDraw = switch (routine) {
            case 0 -> { init(); yield false; }
            case 2 -> { waitForPlayer(sonic); yield false; }
            case 4 -> { fight(); yield false; }
            case 6 -> { exploding(); yield false; }
            case 8 -> escape();
            case 0xA -> widenRightBound();
            case 0xC -> { raiseCamera(); yield false; }
            case 0xE -> { countdownToMachine(); yield false; }
            default -> false;
        };
        if (skipDraw || isDestroyed()) {
            hidden = true;                                   // addq.l #4,sp: no AnimateObject, no DrawObject
            return;
        }
        hidden = false;
        animate(PpzBossCatalogue.offset(cdLevel() == null ? null : cdLevel().payload(), "eggmanAnimations"));   // AnimateObject
    }

    private boolean hidden;

    @Override protected boolean drawn() { return !hidden && routine != 0; }

    @Override String sheet() { return PpzBossArt.EGGMAN; }

    /** sub_20B9A6 (objects/r1/boss.asm:30-42): the pose ends; anim 0. */
    private void poseTimeout() {
        if (poseTimer == 0) return;
        poseTimer = (poseTimer - 1) & 0xFF;
        if (poseTimer == 0) restartAnimation(0);
    }

    /** sub_20B9CA (objects/r1/boss.asm:46-65): bottomBound and destBottomBound. */
    private void holdBottomBound() {
        int bottom = 0x310;
        if (bossFlags) {
            bottom = 0x100;
            if (shake != 0) {
                shake = (shake - 1) & 0xFF;
                bottom += (shake & 1) != 0 ? -1 : 1;
            }
        }
        Camera camera = services().camera();
        if (camera != null) camera.setMaxY((short) bottom);
    }

    /** sub_20BAC0 (objects/r1/boss.asm:144-165): pose 1 for 60 frames while the player is hurt or dead. */
    private void laughCheck(AbstractPlayableSprite player) {
        if (anim != 0 || player == null) return;
        // tst.w obj.var_30(a1) (oPlayerHurt); cmpi.b #6,obj.routine(a1) (dead).
        if (player.getInvulnerableFrames() == 0 && !player.getDead()) return;
        restartAnimation(1);
        poseTimer = 0x3C;
    }

    /** sub_20B9FC (objects/r1/boss.asm:69-84). */
    private boolean hitChecks(AbstractPlayableSprite player) {
        if (immunity != 0) {
            immunity = (immunity - 1) & 0xFF;
            if (immunity == 0) enableCollision();
            return false;
        }
        if (!has(FIGHT)) return false;
        // loc2_20BA1A (objects/r1/boss.asm:88-112): Eggman, then the front leg's calf and foot, then the back's.
        PpzBossPiece hit = null;
        PpzBossCalf frontCalf = body.frontLeg.calf, backCalf = body.backLeg.calf;
        if (colType() == 0) hit = this;
        else if (frontCalf.colType() == 0) hit = frontCalf;
        else if (frontCalf.foot.colType() == 0) hit = frontCalf.foot;
        else if (backCalf.colType() == 0) hit = backCalf;
        else if (backCalf.foot.colType() == 0) hit = backCalf.foot;
        if (hit != null) {
            takeHit(player, hit);
            return true;
        }
        // Bumpers: with three hits left the back arm's first (loc_20BA76), then the front's (loc_20BA8E).
        PpzBossBumper bumper = null;
        if (phase == 3 && shoulders.backArm.forearm.bumper.colType() == 0) bumper = shoulders.backArm.forearm.bumper;
        else if ((phase == 3 || phase == 2) && shoulders.frontArm.forearm.bumper.colType() == 0) {
            bumper = shoulders.frontArm.forearm.bumper;
        }
        if (bumper == null) return false;
        // loc_20BAA8 (objects/r1/boss.asm:135-140), falling into sub_20BAC0.
        bumper.set(PLANTED);
        pushPlayer(player, bumper);
        enableCollision();
        fmSound(FM_BUMPER);
        laughCheck(player);
        return true;
    }

    /** loc_20BAF8 (objects/r1/boss.asm:169-191) and the defeat, loc_20BB4C (:195-228). */
    private void takeHit(AbstractPlayableSprite player, PpzBossPiece hit) {
        immunity = 0x14;
        pushPlayer(player, hit);
        restartAnimation(2);
        poseTimer = 0x78;
        phase = (phase - 1) & 0xFF;
        if (phase == 0) {
            defeat();
            return;
        }
        if (phase == 2) {
            shoulders.set(CARRY);                                // bset #5: drop the back arm
            enableCollisionFrom(2);                             // bra.w loc_20BC2C
        } else {
            shoulders.set(SWAP);                                 // bset #6: drop the front arm
            enableCollisionFrom(1);                             // bra.w loc_20BC38
        }
    }

    private void defeat() {
        restartAnimation(2);
        poseTimer = 0;
        clear(FIGHT);
        routine2 = 0;
        routine = 6;
        setCollision(0, 0);
        shoulders.setRoutine(4);
        body.setRoutine(0xE);
        breakLeg(body.frontLeg);
        breakLeg(body.backLeg);
        shoulders = null;                                        // not read again; the shoulders flies off and deletes
    }

    /** sub_20BBA2 (objects/r1/boss.asm:218-228): the leg falls apart and stops hurting. */
    private static void breakLeg(PpzBossThigh thigh) {
        thigh.setRoutine(0x18);
        thigh.calf.setRoutine(0xE);
        thigh.calf.setCollision(0, 0);
        thigh.calf.foot.setRoutine(0xC);
        thigh.calf.foot.setCollision(0, 0);
    }

    /**
     * sub_20BBCE (objects/r1/boss.asm:232-251): the player is thrown away from {@code from} at $400; on the
     * ground the Y speed is zero and the angle byte is flipped. The ground speed is set to +$400 whichever
     * way the throw goes ({@code FixBugs = 0}: the sign is never applied).
     */
    private static void pushPlayer(AbstractPlayableSprite player, CdBossPart from) {
        if (player == null) return;
        int xSpeed = 0x400, ySpeed = -0x400;
        player.setGSpeed((short) 0x400);
        if (!player.getAir()) {
            player.setAngle((byte) (player.getAngle() ^ 0x80));
            ySpeed = 0;
        }
        if ((short) player.getCentreX() < from.xWord()) xSpeed = -xSpeed;   // cmp.w 8(a1),d0; bcc.s
        player.setXSpeed((short) xSpeed);
        player.setYSpeed((short) ySpeed);
    }

    /** sub_20BC06 (objects/r1/boss.asm:255-262): collision for the parts the remaining hit count has. */
    private void enableCollision() {
        enableCollisionFrom(phase);
    }

    /** loc_20BC20 / loc_20BC2C / loc_20BC38 (objects/r1/boss.asm:266-294): three, two or one hit left. */
    private void enableCollisionFrom(int hits) {
        if (hits == 3) enableBumper(shoulders.backArm);
        if (hits == 3 || hits == 2) enableBumper(shoulders.frontArm);
        if (hits < 1 || hits > 3) return;
        setCollision(0xFC, 2);
        for (PpzBossThigh thigh : new PpzBossThigh[] {body.frontLeg, body.backLeg}) {
            thigh.calf.setCollision(0xBD, 2);
            thigh.calf.foot.setCollision(0xBE, 2);
        }
    }

    /** sub_20BC92 (objects/r1/boss.asm:298-303): the arm's forearm's bumper. */
    private static void enableBumper(PpzBossArm arm) {
        arm.forearm.bumper.setCollision(0xFF, 2);
    }

    /** EggmanObject_0_Routine0 (objects/r1/boss.asm:307-330). */
    private void init() {
        // AddGfxQueue #4 (PLC 4): the sheets are registered at the stage load (PpzBossArt).
        bossFight = true;
        lockScreen(true);
        routine = 2;
        priority = 6;
        width = 0x14;
        yRadius = 8;
        tileWord = 0x3FD;
        restartAnimation(1);
        x = 0xC52;
        y = 0x78;
        loadPalette(BOSS_PALETTE);
    }

    /**
     * {@code bossFight} gates the player's right boundary: {@code ObjSonic_LevelBound} drops its 56-pixel
     * extension while it is set (Level/_Objects/Sonic/Main.asm:1673-1691). The shared boundary reads that gate from
     * the screen-lock flag ({@code PlayerLevelBoundaryRules}, Sonic 1's {@code f_lockscreen} rule that Sonic CD
     * inherits), so the boss drives it with {@code bossFight}.
     */
    private void lockScreen(boolean locked) {
        var state = services().gameState();
        if (state != null) state.setScreenLocked(locked);
    }

    /** EggmanObject_0_Routine2 (objects/r1/boss.asm:474-498). */
    private void waitForPlayer(AbstractPlayableSprite player) {
        Camera camera = services().camera();
        if (camera == null || player == null) return;
        camera.setMaxX((short) 0xAC0);
        int px = (short) player.getCentreX();
        if (px < 0xA6A) return;
        int left = (short) (px - 0xA0);
        if (left < camera.getMinX()) return;
        if (px >= 0xB60) {
            routine = 0xC;
            camera.setMaxX((short) 0xAC0);
            left = 0xAC0;
        }
        camera.setMinX((short) left);
    }

    /** EggmanObject_0_RoutineC (objects/r1/boss.asm:502-515). */
    private void raiseCamera() {
        Camera camera = services().camera();
        if (camera == null) return;
        int centre = (short) (camera.getYPosBias() + 6);
        camera.setYPosBias((short) centre);
        if (centre < 0xC8) return;
        subCpu(SCMD_BOSSMUS);
        bossFlags = true;
        routine = 0xE;
    }

    /** EggmanObject_0_RoutineE (objects/r1/boss.asm:519-530). */
    private void countdownToMachine() {
        phase = (phase + 1) & 0xFF;
        if (phase != 0x3C) return;
        phase = 0;
        routine = 4;
        x = 0xBD2;
        y = 0x78;
        buildMachine();
    }

    /**
     * sub_20BD36 (objects/r1/boss.asm:346-470): fourteen FindObjSlot allocations in this order, each at
     * Eggman's position; an allocation failure ends the build there (no hit count, no collision).
     */
    private void buildMachine() {
        PpzBossBody newBody = new PpzBossBody(x, y, this);
        if (!spawn(newBody)) return;
        body = newBody;
        body.setLayer(6);
        PpzBossThigh front = buildLeg(false, 6, 3, 2);
        if (front == null) return;
        PpzBossThigh back = buildLeg(true, 7, 6, 5);
        if (back == null) return;
        PpzBossShoulder newShoulder = new PpzBossShoulder(x, y, body);
        if (!spawn(newShoulder)) return;
        shoulders = newShoulder;
        shoulders.setLayer(3);
        if (buildArm(false, 5, 4, 3) == null) return;
        if (buildArm(true, 7, 7, 6) == null) return;
        phase = 3;
        enableCollision();
    }

    private PpzBossThigh buildLeg(boolean back, int thighLayer, int calfLayer, int footLayer) {
        PpzBossThigh thigh = new PpzBossThigh(x, y, body);
        if (!spawn(thigh)) return null;
        if (back) body.backLeg = thigh; else body.frontLeg = thigh;
        thigh.setLayer(thighLayer);
        PpzBossCalf calf = new PpzBossCalf(x, y, thigh);
        if (!spawn(calf)) return null;
        thigh.calf = calf;
        calf.setLayer(calfLayer);
        PpzBossFoot foot = new PpzBossFoot(x, y, calf);
        if (!spawn(foot)) return null;
        calf.foot = foot;
        foot.setLayer(footLayer);
        if (back) {
            thigh.set(BACK);
            calf.set(BACK);
            foot.set(BACK);
        }
        return thigh;
    }

    private PpzBossArm buildArm(boolean back, int armLayer, int forearmLayer, int bumperLayer) {
        PpzBossArm arm = new PpzBossArm(x, y, shoulders);
        if (!spawn(arm)) return null;
        if (back) shoulders.backArm = arm; else shoulders.frontArm = arm;
        arm.setLayer(armLayer);
        if (!back) arm.angle = 0x80;                             // move.b #$80,obj.var_2a(a1), front arm only
        PpzBossForearm forearm = new PpzBossForearm(x, y, arm, back);
        if (!spawn(forearm)) return null;
        arm.forearm = forearm;
        forearm.setLayer(forearmLayer);
        PpzBossBumper bumper = new PpzBossBumper(x, y, forearm);
        if (!spawn(bumper)) return null;
        forearm.bumper = bumper;
        bumper.setLayer(bumperLayer);
        if (back) {
            arm.set(BACK);
            forearm.set(BACK);
            bumper.set(BACK);
        }
        return arm;
    }

    /** EggmanObject_0_Routine4 (objects/r1/boss.asm:534-585): the body's walk table. */
    private void fight() {
        if (!body.clear(DONE)) return;
        if (routine2 == 2) {
            restartAnimation(0);
            prevAnim = 0;                                       // move.w #0,obj.anim_id: anim and prevAnim
            phase = 3;
            set(FIGHT);
            enableCollision();
        }
        routine2 += 2;
        body.clear(STARTED);
        CdStageImage image = stageImage();
        var payload = cdLevel() == null ? null : cdLevel().payload();
        if (image == null || payload == null) return;
        while (true) {
            int next = PpzBossCatalogue.walkStep(image, payload, routine2);
            if (next >= 0) {
                body.setRoutine(next);                          // move.b (a2)+,obj.routine(a1)
                body.steps = PpzBossCatalogue.walkCount(image, payload, routine2);   // move.b (a2),obj.var_2d(a1)
                return;
            }
            routine2 = 6;                                       // tst.b (a2); bge; move.b #6,obj.routine_2
        }
    }

    /** EggmanObject_0_Routine6 (objects/r1/boss.asm:589-615). */
    private void exploding() {
        phase = (phase + 1) & 0xFF;
        explosionCycle();
        if (phase == 0x5E) {
            savedX = x;
            savedY = y;
            restartAnimation(3);
            body.set(DONE);                                     // the body deletes itself next frame
            body = null;                                        // not read again
        }
        if (phase < 0x78) return;
        phase = 0;
        routine = 8;
        width = 0x20;
        yRadius = 0x20;
        var game = services().gameState();
        if (game != null) game.addScore(100 * 10);              // moveq #100,d0; jsr AddPoints (units of 10)
    }

    /** EggmanObject_0_Routine8 (objects/r1/boss.asm:619-662); true when it ends in routine A (no draw). */
    private boolean escape() {
        if (routine2 == 0) {
            phase = (phase + 1) & 0xFF;
            int curX = x, curY = y;
            x = savedX;
            y = savedY;
            explosionCycle();
            x = curX;
            y = curY;
            addXLong(0x8000);
            addYLong(-0x20000);
            if (yWord() > 0x158) return false;
            routine2 = 1;
            restartAnimation(4);
            phase = 0x40;
            bob = 8;
            return false;
        }
        addY(-bob);
        phase = (phase + 3) & 0xFF;
        bob = sine(phase) >> 5;
        addY(bob);
        addXLong(0x28000);
        if (xWord() < 0xC80) return false;
        // loc_20C16C (objects/r1/boss.asm:666-678).
        phase = 0;
        boolean goodFuture = flags().goodFuture;
        subCpu(goodFuture ? SCMD_R1CMUS : SCMD_R1DMUS);
        loadCapsulePalette();
        bossFlags = false;
        bossFight = false;
        lockScreen(false);
        routine = 0xA;
        return widenRightBound();
    }

    /** EggmanObject_0_RoutineA (objects/r1/boss.asm:680-697): always ends in addq.l #4,sp (no draw). */
    private boolean widenRightBound() {
        Camera camera = services().camera();
        var level = cdLevel();
        if (camera == null || level == null) return true;
        CdStageImage image = stageImage();
        if (image == null) return true;
        // lea CamBounds+2,a1; move.w (a1)+,d0; move.w (a1)+,d1: d1 is the stage's right boundary
        // (CamBounds: unused, left, right, top, bottom; LevelSizeLoad, Palmtree Panic/Scroll (Act 2 Past).asm:21-27).
        int stageRight = (short) image.u16(CdStageCatalogue.entry(level.payload()).camBounds() + 4);
        int right = (short) (camera.getMaxX() + 6);
        camera.setMaxX((short) right);
        if (stageRight > right) return true;
        camera.setMaxX((short) stageRight);
        deleteObject();
        return true;
    }

    /** sub_20D72A (objects/r1/boss.asm:2689-2732): every fourth count, an explosion at one of ten offsets. */
    private void explosionCycle() {
        if ((phase & 3) != 0) return;
        CdStageImage image = stageImage();
        var payload = cdLevel() == null ? null : cdLevel().payload();
        if (image == null || payload == null) return;
        int[] offset = PpzBossCatalogue.explosionOffset(image, payload, (phase >> 2) % 10);
        explode(x + offset[0], y + offset[1]);
    }

    // ---- touch: Player_TouchSpecial's boss branch ---------------------------------------------------------

    /**
     * {@code Player_TouchSpecial} sizes $3C-$3F (Palmtree Panic/Player Object Collision.asm:299-322): after
     * {@code Player_TouchEnemy}, a still-set type adds nothing, a cleared one 3; the type is cleared and
     * {@code .FlagCollision} adds 1. The shared boss branch applies the rebound or the hurt.
     */
    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        touched(this, player);
    }

    static void touched(CdBossPart part, PlayableEntity player) {
        // Player_TouchEnemy .DamageEnemy: colStatus 2 -> 1 and the type cleared when the player attacks.
        boolean attacks = player != null && (player.getAnimationId() == 2 || player.getInvincibleFrames() > 0);
        int status = part.getCollisionProperty();
        if (attacks) status = ((status - 1) & 0xFF) + 3;
        part.setCollision(0, (status + 1) & 0xFF);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossEggman(spawn);
    }
}
