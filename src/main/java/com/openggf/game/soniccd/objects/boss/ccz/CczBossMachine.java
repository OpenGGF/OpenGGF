package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;

/**
 * The Collision Chaos boss machine, object $31 ({@code BossMachineObject}, objects/r3/boss.asm:160-716; index entry
 * $30, r3/object_index_3.asm:52), placed by the act 3 layout at ($380, $1C0). Both futures run the same code; only
 * the stage music after the defeat differs ({@code SCMD_R3CMUS} $13 in the good future, {@code SCMD_R3DMUS} $14 in
 * the bad).
 *
 * <ul>
 *   <li>Routine 0 loads palette entry 6 and allocates ({@code SpawnObjectAfter}) Eggman $30 px above, a gear $40 px
 *       to the left and the bomb launcher.</li>
 *   <li>Routine 2 follows the player's Y between $DC and $1C0 (its gear turns while it moves); once the player is at
 *       or below $1C0 the fight starts: Y $1C0, collision $3F with four hits ({@code collide_status}).</li>
 *   <li>Routine 4: while the player stands in the arena column (X $340-$3C0, Y $80 up to the next of four
 *       thresholds) an exit barrier $3F appears at ($380, the next of four heights) as a step up; a hit (the
 *       player's touch cleared the collision type) starts the hit routine: art for the escape (first hit), Eggman
 *       thrown up (first two) or escaping (third), the hit sound, 16 frames of palette flash, three explosions,
 *       and the machine rises $48 px at 2.5 px a frame before it can be hit again.</li>
 *   <li>The third hit ({@code collide_status} 1) scores 1000, plays the stage music and rises $70 px; the capsule
 *       palette loads, it rises $10 px more (routine 8) and explodes until its sequence ends (routine $A); once the
 *       fourth barrier stands it deletes itself and its gear.</li>
 * </ul>
 *
 * <p>From the first hit, every frame raises {@code camYCenter} by 6 to $B2. The fourth barrier spawned by a hit
 * routine sets {@code bossFlags} to 3, which turns the act's level events to the boss bounds. Touch: $3F is an enemy
 * type of size $3F; {@code Player_TouchEnemy} rebounds a rolling or invincible player (halved) and decrements the
 * hit count, anyone else is hurt (r3/player_object_collide.asm:129-150, 212-230).
 */
final class CczBossMachine extends CczBossPiece implements TouchResponseAttackable {
    /** BossMachineObject is index entry $30 (r3/object_index_3.asm:3-52). */
    static final int ID = 0x31;
    /** BossMachine_Init loads palette 6 (objects/r3/boss.asm:231-235); BossMachine_UpdateDamageFlash flashes palette 5 (objects/r3/boss.asm:160-185). */
    static final int PALETTE = 6, FLASH_PALETTE = 5;
    /** BossMachine_Defeated: $14 (SCMD_R3DMUS), $13 (SCMD_R3CMUS) in the good future (objects/r3/boss.asm:463-469). */
    static final int SCMD_R3CMUS = 0x13, SCMD_R3DMUS = 0x14;
    /** cmpi.b #4,boss.attack_count(a0): four barriers (objects/r3/boss.asm:322-323). */
    static final int BARRIERS = 4;

    CczBossEggman eggman;
    /** {@code boss.target_y} (var_2e). */
    int targetY;
    /** {@code boss.y_speed} (var_30): 16.16. */
    int ySpeed;
    /** {@code boss.attack_count} (var_3a): barriers spawned. */
    int attackCount;
    /** {@code boss.phase} (var_3b): hits taken. */
    int phase;
    /** {@code boss.flash_timer} (var_2b): bit 7 is the flash phase. */
    int flashTimer;
    /** {@code boss.rise_counter} (var_2a). */
    int riseCounter;
    /**
     * {@code boss_flags} (r3/boss.asm:479; read by R33Events, common/events.asm:153-163), a level byte nothing clears
     * on this stage. This object writes it; the barrier it spawns with the write carries the value too, because
     * exit barriers are never deleted and the machine deletes itself once that barrier stands.
     */
    private int bossFlags;
    private boolean exited;

    CczBossMachine(ObjectSpawn spawn) {
        super(spawn, "CczBossMachine");
    }

    /** {@code boss_flags}: 3 once the fourth barrier came from a hit routine. */
    int bossFlags() { return bossFlags; }

    /** {@code collide_status}: hits left before the defeat counts it as 1. */
    int hitsLeft() { return colStatus & 0xFF; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (phase != 0) raiseCamera();
        switch (routine) {
            case 0 -> init();
            case 2 -> trackPlayer(player);
            case 4 -> combat(player);
            case 6 -> hitReaction(player);
            case 8 -> defeatedRise(player);
            case 0xA -> waitForExit(player);
            default -> { }
        }
        if (exited) return;                                         // addq.l #4,sp; jmp DeleteOtherObject
        hidden = has(DONE);                                         // btst #0,boss.flags(a0); beq.s .Draw; rts
        if (!hidden) updateDamageFlash();
    }

    private boolean hidden;

    @Override protected boolean drawn() { return !hidden && routine != 0; }

    /** BossMachineObject (objects/r3/boss.asm:189-202): {@code scroll_focus_y} (camYCenter) up 6 a frame to $B2. */
    private void raiseCamera() {
        Camera camera = services().camera();
        if (camera == null) return;
        int centre = camera.getYPosBias();
        if (centre < 0xB0) {
            centre += 6;                                            // addq.w #6,scroll_focus_y
            if (centre < 0xB2) {
                camera.setYPosBias((short) centre);
                return;
            }
        }
        camera.setYPosBias((short) 0xB2);                           // .LockCamera
    }

    /** BossMachine_UpdateDamageFlash (objects/r3/boss.asm:160-185). */
    private void updateDamageFlash() {
        if (flashTimer == 0) return;
        boolean wasSet = (flashTimer & 0x80) != 0;
        flashTimer ^= 0x80;                                         // bchg #7,boss.flash_timer(a0)
        int palette;
        if (wasSet) {
            palette = PALETTE;
            mapFrame &= 0xFE;
        } else {
            palette = FLASH_PALETTE;
            mapFrame |= 1;
        }
        flashTimer = (flashTimer - 1) & 0xFF;
        loadPalette(palette);
    }

    /** BossMachine_Init (objects/r3/boss.asm:231-266). */
    private void init() {
        loadPalette(PALETTE);
        routine = 2;
        priority = 4;
        width = 0x50;
        yRadius = 0x10;
        tileWord = CczBossArt.BOSS_TILE;
        CczBossEggman newEggman = new CczBossEggman(x, y - 0x30, this);
        if (!spawnAfter(newEggman)) return;
        eggman = newEggman;
        CczBossGear newGear = new CczBossGear(x - 0x40, y, this);
        if (!spawnAfter(newGear)) return;
        gear = newGear;
        spawnAfter(new CczBossBombLauncher(x, this));
    }

    /** BossMachine_TrackPlayer (objects/r3/boss.asm:270-317). */
    private void trackPlayer(PlayableEntity player) {
        if (gear != null) gear.setAnim(1);                          // move.b #1,$1C(a1)
        if (player == null) return;
        int playerY = (short) player.getCentreY();
        if (playerY >= 0x1C0) {                                     // cmp.w d0,d1; ble.s .StartFight
            startFight();
            return;
        }
        if (playerY < 0xDC) playerY = 0xDC;                         // cmp.w d0,d2; bgt.s .ClampTop
        if (playerY == targetY) clear(MOVING);
        else testAndSet(MOVING);
        targetY = playerY;
        setYWord(playerY);
    }

    private void startFight() {
        setYWord(0x1C0);
        clear(MOVING);
        routine = 4;
        ySpeed = -0x28000;
        targetY = (short) (yWord() - 0x48);
        setCollision(0x3F, 4);
        if (gear != null) gear.setAnim(0);                          // move.b #0,$1C(a1)
    }

    /** BossMachine_CanSpawnExitBarrier (objects/r3/boss.asm:321-345): true for d0 = 0. */
    private boolean canSpawnExitBarrier(PlayableEntity player) {
        if (attackCount >= BARRIERS || player == null) return false;
        CdStageImage image = stageImage();
        CdStagePayload payload = payload();
        if (image == null || payload == null) return false;
        int threshold = CczBossCatalogue.word(image, payload, "playerYThresholds", attackCount);
        int playerY = (short) player.getCentreY();
        if (playerY > threshold || playerY < 0x80) return false;
        int playerX = (short) player.getCentreX();
        return playerX >= 0x340 && playerX <= 0x3C0;
    }

    /** BossMachine_Combat (objects/r3/boss.asm:358-371). */
    private void combat(PlayableEntity player) {
        if (canSpawnExitBarrier(player)) spawnExitBarrier();
        if (colType() == 0) handleHit(player);
    }

    /** BossMachine_SpawnExitBarrier (objects/r3/boss.asm:375-388): a full barrier at ($380, the next height). */
    private CczBossExitBarrier spawnExitBarrier() {
        CdStageImage image = stageImage();
        CdStagePayload payload = payload();
        if (image == null || payload == null) return null;
        int barrierY = CczBossCatalogue.word(image, payload, "exitBarrierY", attackCount);
        CczBossExitBarrier barrier = new CczBossExitBarrier(0x380, barrierY & 0xFFFF, false);
        if (!spawnFirst(barrier)) return null;
        attackCount++;
        return barrier;
    }

    /** BossMachine_HandleHit (objects/r3/boss.asm:401-470). */
    private void handleHit(PlayableEntity player) {
        int hit = phase;
        phase = (phase + 1) & 0xFF;
        switch (hit) {
            case 0, 1 -> {
                // BossMachine_HitPhase_LoadArt (first hit): AddGfxQueue #4; the sheets exist from the stage load.
                // BossMachine_HitPhase_RaiseUpperChild.
                if (eggman != null) eggman.throwUp();
            }
            default -> {
                // BossMachine_HitPhase_Defeated: frame 4, then 2.
                mapFrame = 4;
                if (eggman != null) eggman.startEscape(yWord());
                mapFrame = 2;
            }
        }
        // BossMachine_StartHitReaction.
        fmSound(FM_HIT);
        sequenceTimer = 0;
        routine = 6;
        flashTimer = 0x10;
        if ((colStatus & 0xFF) == 1) defeated();
        hitReaction(player);
    }

    /** BossMachine_Defeated (objects/r3/boss.asm:457-470). */
    private void defeated() {
        var game = services().gameState();
        if (game != null) game.addScore(100 * 10);                  // moveq #100,d0; jsr AddPoints (units of 10)
        colType = 0;
        targetY = (short) (yWord() - 0x70);
        subCpu(flags().goodFuture ? SCMD_R3CMUS : SCMD_R3DMUS);
    }

    /** BossMachine_TrySpawnFinalBarrier (objects/r3/boss.asm:474-483). */
    private void trySpawnFinalBarrier(PlayableEntity player) {
        if (attackCount != 3 || !canSpawnExitBarrier(player)) return;
        bossFlags = 3;                                              // move.b #3,boss_flags
        CczBossExitBarrier last = spawnExitBarrier();
        if (last != null) last.bossFlags = bossFlags;               // the level byte outlives this slot (see bossFlags)
    }

    /** BossMachine_HitReaction (objects/r3/boss.asm:487-494): the hit's explosions, then the move. */
    private void hitReaction(PlayableEntity player) {
        sequenceTimer = (sequenceTimer + 1) & 0xFFFF;               // addq.w #1,boss.timer(a0)
        switch (phase) {
            case 1 -> { if (sequenceTimer < 4) spawnTimedExplosion("lowerHitExplosions"); }
            case 2 -> { if (sequenceTimer < 4) spawnTimedExplosion("upperHitExplosions"); }
            default -> { if (sequenceTimer < 3) spawnTimedExplosion("defeatHitExplosions"); }
        }
        moveToTarget(player);
    }

    /** BossMachine_MoveToTarget (objects/r3/boss.asm:505-534). */
    private void moveToTarget(PlayableEntity player) {
        testAndSet(MOVING);
        trySpawnFinalBarrier(player);
        addYLong(ySpeed);
        if (targetY < yWord()) return;                              // cmp.w obj.y(a0),d0; blt.s .End
        clear(MOVING);
        setYWord(targetY);
        if ((colStatus & 0xFF) == 1) {
            // .BeginDefeatRise.
            loadCapsulePalette();
            riseCounter = 0;
            routine = 8;
            targetY = (short) (yWord() - 0x10);
            return;
        }
        riseCounter = 0;
        setCollision(0x3F, colStatus);
        routine = 4;
        targetY = (short) (yWord() - 0x48);
    }

    /** BossMachine_DefeatedRise (objects/r3/boss.asm:538-555). */
    private void defeatedRise(PlayableEntity player) {
        updateDefeatExplosions();
        trySpawnFinalBarrier(player);
        riseCounter = (riseCounter + 1) & 0xFF;
        if (riseCounter == 4) mapFrame = 6;
        addYLong(ySpeed);
        if (targetY < yWord()) return;                              // blt.s .End
        routine = 0xA;
    }

    /** BossMachine_WaitForExit and BossMachine_ExitEncounter (objects/r3/boss.asm:559-577). */
    private void waitForExit(PlayableEntity player) {
        updateDefeatExplosions();
        trySpawnFinalBarrier(player);
        if (!has(DONE) || attackCount != BARRIERS) return;
        CczBossGear oldGear = gear;
        gear = null;                                                // not read again
        eggman = null;
        exited = true;
        deleteObject();
        deleteOther(oldGear);
    }

    // ---- touch: Player_TouchEnemy -------------------------------------------------------------------------

    /**
     * {@code sub_207292} (r3/player_object_collide.asm:129-150): an attacking player clears the collision type and
     * takes one from {@code collide_status}; the shared enemy branch applies the halved rebound.
     */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        setCollision(0, colStatus - 1);
    }

    @Override String sheet() { return CczBossArt.MACHINE; }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new CczBossMachine(spawn);
    }
}
