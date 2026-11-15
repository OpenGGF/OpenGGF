package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;

/**
 * Metal Sonic, object $2F ({@code MetalSonicObject}, objects/r7/boss.asm:3-745), which Eggman allocates at ($C30,
 * $1CD). Every frame, before its routine, it checks the finish line ({@code sub_20BFAC}) and, while racing, whether
 * the laser has caught up with it ({@code sub_20BF54}).
 *
 * <ul>
 *   <li>Routine 2 stands, and hops (pose 1) while the player is in the air in a ball.</li>
 *   <li>Routine 4, started by Eggman's script, is the intro: poses 2 to 4 with 120- and 150-frame holds, then the
 *       race start: Eggman's bounds follow him ({@code var_38} $82), {@code bossFlags} bit 7 moves the gate blocks,
 *       the zone's music, and after 60 frames the race.</li>
 *   <li>Routines 6 and 8 run at $280 or $400 (or the boosted $500/$800, $1AA/$2AA), switching every 30 frames by a
 *       frame count; the floor below is followed, with the lift table's steps at its gaps. Staying within $10 of a
 *       player within $A0 for 50 frames starts an attack: before X $3500 the charge (routine A), after it a
 *       240-frame boost toward the player. Falling $78 frames' worth away does the same when the player is ahead.</li>
 *   <li>Routine A poses for 60 frames, then attacks forward (C, the dash: $600 and a hurting $BC from frame 40 to
 *       180) or backward (E, the electric shield: slowing to $200 and a hurting $BD from frame 10 to 150).</li>
 *   <li>At the finish ($3E90) the first to arrive closes the door ({@code bossFlags} bit 7). If it is Metal Sonic
 *       (routine $10) he stands at $3E90, the player is held left of $3E48, and Eggman, still lethal, arrives. If it
 *       is the player, Metal Sonic runs on into the door at $3E48 and is destroyed (routine $12): ten pieces, an
 *       explosion, 1000 points, and he falls off screen.</li>
 * </ul>
 * Both futures run the same code; the race music ({@code SCMD_R7CMUS} $1F in the good future, else
 * {@code SCMD_R7DMUS} $20) is the only difference.
 */
public final class SszMetalSonic extends SszBossPiece {
    /** MetalSonicObject is index entry $2E (r7/object_index_3.asm:3-50). */
    public static final int ID = 0x2F;
    /** var_2c bits. */
    static final int RACING = 0x20, PLAYER_AHEAD = 0x40;
    /** FM IDs: the finish (sub_20BFAC $BB), the pose (loc_20C24E $C9), the dash (loc_20C2A4 $CA), the shield (loc_20C31A $D0). */
    static final int FM_FINISH = 0xBB, FM_POSE = 0xC9, FM_DASH = 0xCA, FM_SHIELD = 0xD0;
    /** The finish line and the door (objects/r7/boss.asm:178, 205, 242). */
    static final int FINISH_X = 0x3E90, DOOR_X = 0x3E48;
    /**
     * {@code CheckBlockDown} leaves its {@code moveq #$D,d5} (common/object_block_collide.asm:158) in d5, which
     * loc_20C14A tests when the boost path skipped computing the player's distance.
     */
    private static final int STALE_D5 = 0xD;

    /** var_2e: Eggman (sub_20CF98 links both ways). */
    SszBossEggman eggmanLink;
    /** var_36: 0, or the boost (1 toward a player ahead, 2 toward one behind). */
    private int boost;
    /** var_38: frames raced (word). */
    private int raceFrames;
    /** var_3a: frames near (+) or far (-) from the player; var_3c: frames close; var_3e: boost frames left. */
    private int closeness;
    private int closeFrames;
    private int boostFrames;
    /** {@code obj.subtype} as a word: the fall speed over a gap. */
    private int fallSpeed;
    /**
     * Where sub_20CA8E left a2 (its lift-table pointer) this frame, as a byte offset into word_20CABA, or -1 when a2
     * still holds Eggman: routine 6 then tests {@code $39(a2)} (FixBugs = 0, objects/r7/boss.asm:253-257), a byte
     * of the stage file instead of Eggman's finish byte.
     */
    private int clobberedA2 = -1;

    SszMetalSonic(SszBossEggman eggman) {
        super(ID, 0xC30, 0x1CD, "SszMetalSonic");
        this.eggmanLink = eggman;
    }

    private SszMetalSonic(ObjectSpawn spawn) {
        super(spawn, "SszMetalSonic");
    }

    @Override String sheet() { return SszBossArt.METAL; }

    /** {@code var_2d}: the current routine's step (the script entry next due). */
    public int scriptStep() { return step; }

    /** loc_20C688 (objects/r7/boss.asm:936-941): Eggman's script starts the intro. */
    void startIntro() {
        routine = 4;
        timer = 0;
        step = 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = sonic(player);
        skipDraw = false;
        clobberedA2 = -1;
        finishLine(sonic);
        laserCatchUp();
        switch (routine) {
            case 0 -> {                                         // MetalSonicObject_0_Routine0 (:32-39)
                priority = 5;
                width = 0x14;
                yRadius = 0x18;
                tileWord = 0x37C;
                routine = 2;
                standing(sonic);
            }
            case 2 -> standing(sonic);
            case 4 -> intro();
            case 6, 8 -> race(sonic);
            case 0xA -> attackPose(sonic);
            case 0xC -> dash(sonic);
            case 0xE -> shield();
            case 0x10 -> won(sonic);
            case 0x12 -> {
                if (destroyedAtTheDoor()) return;
            }
            default -> { }
        }
        animateObject(table("metalAnimations"));
    }

    /** MetalSonicObject_0_Routine2 (objects/r7/boss.asm:41-65): a hop while the player is airborne in a ball. */
    private void standing(AbstractPlayableSprite player) {
        if (anim == 1) {
            if (animFrame != 7) return;
            anim = 0;
        }
        if (player != null && player.getAir() && player.getRolling()) {
            if (step != 0) return;
            step = 1;
            anim = 1;
            return;
        }
        step = 0;
    }

    /** MetalSonicObject_0_Routine4 (objects/r7/boss.asm:69-136). */
    private void intro() {
        switch (step) {
            case 0, 1 -> {
                if (step == 0) {                                // MetalSonicObject_1_Routine0
                    anim = 2;
                    step = 1;
                    mapFrame = 0;
                }
                if (mapFrame != 6) return;                      // MetalSonicObject_1_Routine2
                anim = 3;
                step++;
                timer = 0x78;
            }
            case 2, 4, 6 -> waitWord();
            case 3 -> {                                         // MetalSonicObject_1_Routine6
                step++;
                anim = 4;
                timer = 0x96;
            }
            case 5 -> {                                         // MetalSonicObject_1_RoutineA (:115-130)
                if (eggmanLink != null) {
                    eggmanLink.boundMode = 0x82;
                    eggmanLink.setBossFlags(eggmanLink.bossFlags() | 0x80);
                }
                step++;
                timer = 0x3C;
                // USA R7 variants 9 and 10: $1F when goodFuture ($FF156A) is set, else $20.
                subCpu(flags().goodFuture ? SszBossEggman.SCMD_R7CMUS : SszBossEggman.SCMD_R7DMUS);
            }
            case 7 -> {                                         // MetalSonicObject_1_RoutineE
                set(RACING);
                runSlow();
            }
            default -> { }
        }
    }

    /**
     * sub_20BFAC (objects/r7/boss.asm:174-235). While Eggman's finish byte is clear: Metal Sonic at $3E90 wins
     * (loc_20C032); the player at $3E90 wins (loc_20BFCC): the door closes, Metal Sonic is held at $3E48 and sped up
     * ($800, +$10), and on reaching $3E48 he is destroyed.
     */
    private void finishLine(AbstractPlayableSprite player) {
        SszBossEggman eggman = eggmanLink;
        if (eggman == null) return;
        int finish = eggman.finish;
        if ((finish & 0x80) != 0) return;                       // bmi.s locret_20BFCA
        if (finish == 0) {
            if (FINISH_X <= xWord()) {                          // cmp.w obj.x(a0),d0; ble.w loc_20C032
                metalWins(eggman, player);
                return;
            }
            if (player == null || FINISH_X > px(player)) return;
            eggman.finish = 1;                                  // move.b #1,$39(a2)
            eggman.setBossFlags(eggman.bossFlags() | 0x80);
            fmSound(FM_FINISH);
            if (xWord() > DOOR_X) setXWord(DOOR_X);             // sub_20C072 with a3 = this object
            speedCap = 0x800;
            accelX = 0x10;
        }
        if (xWord() < DOOR_X) return;                           // loc_20C002
        eggman.finish |= 0x80;
        clear(RACING);
        routine = 0x12;
        breakApart();
        timer = 0;
        step = 0;
    }

    /** loc_20C032 (objects/r7/boss.asm:221-235). */
    private void metalWins(SszBossEggman eggman, AbstractPlayableSprite player) {
        setXWord(FINISH_X);
        eggman.finish = 0x82;
        eggman.setBossFlags(eggman.bossFlags() | 0x80);
        fmSound(FM_FINISH);
        holdPlayer(player);
        clear(RACING);
        routine = 0x10;
        timer = 0;
        step = 0;
    }

    /** sub_20C072 (objects/r7/boss.asm:239-248) with a3 = the player: X at most $3E48. */
    private static void holdPlayer(AbstractPlayableSprite player) {
        if (player == null || DOOR_X >= px(player)) return;
        NativePositionOps.writeXPosPreserveSubpixel(player, DOOR_X);
    }

    /**
     * sub_20BF54 (objects/r7/boss.asm:140-170): racing within $58 of Eggman, Metal Sonic takes Eggman's speed (and
     * +8 to $600); before X $3500 that is also the dash (routine C), restarted every such frame.
     */
    private void laserCatchUp() {
        SszBossEggman eggman = eggmanLink;
        if (!has(RACING) || eggman == null) return;
        if ((short) (xWord() - eggman.xWord()) > 0x58) return;
        if (0x3500 <= xWord()) {                                // cmpi.w #$3500,obj.x(a0); blt.s loc_20BF8A
            xSpeed = eggman.xSpeed;
            accelX = 8;
            speedCap = 0x600;
            return;
        }
        if ((short) xSpeed < (short) eggman.xSpeed) {           // cmp.w obj.x_speed(a2),d0; bge.s loc_20BFA6
            xSpeed = eggman.xSpeed;
            accelX = 8;
            speedCap = 0x600;
        }
        enterRoutine(0xC);                                      // loc_20BFA6: bra.w loc_20C212
    }

    /** MetalSonicObject_0_Routine6 (objects/r7/boss.asm:252-466), routines 6 and 8. */
    private void race(AbstractPlayableSprite player) {
        accelerateCappedAndMove();
        floorFollow();
        if (finishByte() != 0) return;                          // tst.b $39(a2); beq.s loc_20C096
        raceFrames = (raceFrames + 1) & 0xFFFF;
        int d5;
        boolean toAttack;
        if (boost != 0) {                                       // loc_20C13C
            d5 = STALE_D5;
            boostFrames = (boostFrames - 1) & 0xFFFF;
            if ((short) boostFrames <= 0) {
                boost = 0;
                boostFrames = 0;
            }
            toAttack = false;
        } else {
            d5 = player == null ? 0 : (short) (px(player) - xWord());
            int d4 = d5;
            if (d5 >= 0) set(PLAYER_AHEAD);
            else {
                clear(PLAYER_AHEAD);
                d4 = -d5;
            }
            if (d4 < 0xA0) {
                if ((short) closeness < 0) closeness = 0;
                closeness = (closeness + 1) & 0xFFFF;
                if (d4 < 0x10) closeFrames = 0;
                else closeFrames = (closeFrames + 1) & 0xFFFF;
            } else {
                if ((short) closeness >= 0) closeness = 0;
                closeness = (closeness - 1) & 0xFFFF;
            }
            toAttack = (short) closeFrames >= 0x32;             // loc_20C0F2
        }
        if (toAttack && !attackOrBoost()) return;
        // loc_20C14A
        if (boostFrames == 0 && (short) closeness <= -0x78) {
            if (d5 < 0) {
                runSlowUnlessSlow();
                return;
            }
            if (!attackOrBoost()) return;                       // bra.w loc_20C0FE
        }
        // loc_20C160: divu.w #$3C; swap: the frame count's remainder.
        if ((raceFrames % 0x3C) >= 0x1E) {
            runSlowUnlessSlow();
            return;
        }
        if (routine != 8) runFast();
    }

    /**
     * loc_20C0FE (objects/r7/boss.asm:315-336): before X $3500 the attack (routine A); returns false when it ends the
     * routine. After it, a 240-frame boost toward the player.
     */
    private boolean attackOrBoost() {
        if (0x3500 > xWord()) {
            enterRoutine(0xA);                                  // loc_20C208
            return false;
        }
        closeFrames = 0;                                        // sub_20C226
        step = 0;
        timer = 0;
        boost = has(PLAYER_AHEAD) ? 1 : 2;
        boostFrames = 0xF0;
        return true;
    }

    /** loc_20C17C (objects/r7/boss.asm:369-372). */
    private void runSlowUnlessSlow() {
        if (routine != 6) runSlow();
    }

    /** loc_20C188 (objects/r7/boss.asm:376-405): routine 8 at $400 ($800 boosted ahead, $2AA behind), pose 6. */
    private void runFast() {
        setRun(boost == 1 ? 0x800 : boost == 2 ? 0x2AA : 0x400);
        routine = 8;
        anim = 6;
        step = 0;                                               // loc_20C22A
        timer = 0;
    }

    /** loc_20C1C8 (objects/r7/boss.asm:409-438): routine 6 at $280 ($500, $1AA), pose 5. */
    private void runSlow() {
        setRun(boost == 1 ? 0x500 : boost == 2 ? 0x1AA : 0x280);
        routine = 6;
        anim = 5;
        step = 0;
        timer = 0;
    }

    /**
     * {@code move.w d0,var_34; cmp.w x_speed,d0; blt.s; neg.w d0; move.w d0,var_30}: the cap, and the acceleration
     * positive below the current speed, negative from it up (either way the next capped step lands on the cap).
     */
    private void setRun(int speed) {
        speedCap = speed;
        accelX = speed < (short) xSpeed ? speed : -speed;
    }

    /** loc_20C208 / loc_20C212 / loc_20C21C with sub_20C226 (objects/r7/boss.asm:442-466). */
    private void enterRoutine(int next) {
        routine = next;
        closeFrames = 0;
        step = 0;
        timer = 0;
    }

    /** MetalSonicObject_0_RoutineA (objects/r7/boss.asm:470-507). */
    private void attackPose(AbstractPlayableSprite player) {
        accelerateCappedAndMove();
        floorFollow();
        switch (runScript("metalScriptA")) {
            case 0 -> {                                         // loc_20C24E
                anim = 4;
                fmSound(FM_POSE);
            }
            case 1 -> {                                         // sub_20C268
                if (player != null && (short) (px(player) - xWord()) >= 0) {
                    set(PLAYER_AHEAD);
                    enterRoutine(0xC);
                } else {
                    clear(PLAYER_AHEAD);
                    enterRoutine(0xE);
                }
            }
            default -> { }
        }
    }

    /** MetalSonicObject_0_RoutineC (objects/r7/boss.asm:511-567): the dash. */
    private void dash(AbstractPlayableSprite player) {
        accelerateCappedAndMove();
        floorFollow();
        switch (runScript("metalScriptC")) {
            case 0 -> {                                         // loc_20C2A4
                fmSound(FM_DASH);
                accelX = 8;
                speedCap = 0x600;
                anim = 8;
            }
            case 1 -> anim = 9;                                 // sub_20C2CA
            case 2 -> setCollision(0xBC, 2);                    // sub_20C2D2
            case 3 -> {                                         // sub_20C2E0
                setCollision(0, 0);
                if (player != null && (short) px(player) > xWord()) runSlow();
                else runFast();
            }
            default -> { }
        }
    }

    /** MetalSonicObject_0_RoutineE (objects/r7/boss.asm:571-613): the electric shield. */
    private void shield() {
        accelerateCappedAndMove();
        floorFollow();
        switch (runScript("metalScriptE")) {
            case 0 -> {                                         // loc_20C31A
                accelX = -8;
                speedCap = 0x200;
                spawnAfter(new SszElectricShield(this));        // sub_20CF84
                anim = 0xA;
                fmSound(FM_SHIELD);
            }
            case 1 -> setCollision(0xBD, 2);                    // sub_20C344
            case 2 -> {                                         // sub_20C352
                setCollision(0, 0);
                runFast();
            }
            default -> { }
        }
    }

    /** MetalSonicObject_0_Routine10 (objects/r7/boss.asm:670-716): Metal Sonic won; the player stays left of the door. */
    private void won(AbstractPlayableSprite player) {
        holdPlayer(player);
        switch (step) {
            case 0 -> {
                anim = 0xB;
                setXWord(FINISH_X);
                setYWord(0x1CC);
                step++;
            }
            case 1 -> {
                if (player == null || px(player) < 0x3DD0) return;
                step++;
                timer = (0x1E << 8) | (timer & 0xFF);           // move.b #$1E,obj.var_2a(a0)
            }
            case 2 -> waitHighByte();
            case 3 -> anim = 0xC;
            default -> { }
        }
    }

    /** MetalSonicObject_2_Routine4_0 on the byte at var_2a (the timer word's high byte). */
    private void waitHighByte() {
        int value = ((timer >> 8) - 1) & 0xFF;
        timer = (value << 8) | (timer & 0xFF);
        if (value == 0) step++;
    }

    /** MetalSonicObject_0_Routine12 (objects/r7/boss.asm:720-744); true once deleted. */
    private boolean destroyedAtTheDoor() {
        if (step == 0) {
            var game = services().gameState();
            // moveq #$64,d0; jsr AddPoints: 100 of AddPoints' units of ten (score, HUD and Points/Main.asm:157-160),
            // 1000 points; the engine's score counts points (CdScorePolicy). Native R73C-win row 4238: 0 -> 100 units.
            if (game != null) game.addScore(0x64 * 10);
            step = 1;
            anim = 0xD;
            xSpeed = 0;
            accelX = 0;
            ySpeed = -0x500;
            gravity = 0x28;
            SszBossLaser blast = SszBossLaser.metalBlast(this);   // sub_20D070
            if (spawnAfter(blast)) fmSound(FM_EXPLODE);
        }
        if (timer == 0xF0) {
            skipDraw = true;
            deleteObject();
            return true;
        }
        accelerateAndMove();
        timer = (timer + 1) & 0xFFFF;
        return false;
    }

    /**
     * sub_20CF14 (objects/r7/boss.asm:1872-1904): ten pieces ($31) at Metal Sonic, frame d3 mod 5, X speed the
     * remainder of ({@code Random} | $FFFF8000) / $280, Y speed -$200, gravity $C + (Random & $F).
     */
    private void breakApart() {
        for (int d3 = 9; d3 >= 0; d3--) {
            int d0 = (short) services().rng().nextWord();       // Random; andi.l #$FFFF,d0; ext.l d0
            int d1 = d0 | 0xFFFF8000;
            int xs = d1 % 0x280;                                // divs.w: the remainder takes the dividend's sign
            SszMetalSonicDebris piece = new SszMetalSonicDebris(this, d3 % 5, xs, 0xC + (d0 & 0xF));
            if (!spawnAfter(piece)) return;
        }
    }

    /** The byte routine 6 tests at {@code $39(a2)}: Eggman's finish, or the stage file's after a lift-table walk. */
    private int finishByte() {
        if (clobberedA2 < 0) return eggmanLink == null ? 0 : eggmanLink.finish & 0xFF;
        CdStageImage image = stageImage();
        int table = table("metalLiftTable");
        return image == null || table < 0 ? 0 : image.u8(table + clobberedA2 + 0x39);
    }

    /**
     * sub_20CA0E / sub_20CA20 (objects/r7/boss.asm:1342-1381): within 8 pixels of the floor Metal Sonic snaps to
     * it; above that a lift-table entry within $40 moves him by its step, else he falls (the fall speed + $30 a
     * frame, in 1/256 pixels).
     */
    private void floorFollow() {
        int distance = floorDistance(0);
        if (distance > 8) {
            int[] walk = liftWalk(stageImage(), payload(), xWord());
            if (walk == null) return;
            clobberedA2 = walk[3];
            if (walk[0] == 0) {                                 // not found: bne past loc_20CA4E
                fallSpeed = (fallSpeed + 0x30) & 0xFFFF;
                addYLong((long) (short) fallSpeed << 8);
                return;
            }
            addX(walk[1]);
            addY(walk[2]);
            distance = 0;
        }
        fallSpeed = 0;                                          // loc_20CA4E: clr.w obj.subtype(a0)
        addY(distance);
    }

    /**
     * sub_20CA8E (objects/r7/boss.asm:1407-1430) over word_20CABA from object X {@code x}: {found (1/0), dX, dY, a2's
     * byte offset after the walk}, or null without the table.
     */
    static int[] liftWalk(CdStageImage image, CdStagePayload payload, int x) {
        if (image == null || SszBossCatalogue.offset(payload, "metalLiftTable") < 0) return null;
        for (int i = 0; ; i++) {
            int[] entry = SszBossCatalogue.liftEntry(image, payload, i);
            if (entry == null) return new int[] {0, 0, 0, i * 6 + 2};
            int d2 = (short) (entry[0] - x);
            if (d2 <= 0) d2 = (short) -d2;
            if (d2 > 0x40) continue;
            return new int[] {1, entry[1], entry[2], (i + 1) * 6};
        }
    }

    /** sub_20CA8E's found entry near {@code x} as {X, dX, dY}, or null (Eggman's floor follow). */
    static int[] liftNear(CdStageImage image, CdStagePayload payload, int x) {
        int[] walk = liftWalk(image, payload, x);
        return walk == null || walk[0] == 0 ? null : new int[] {x, walk[1], walk[2]};
    }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszMetalSonic(spawn);
    }
}
