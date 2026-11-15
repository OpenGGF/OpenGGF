package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;

/**
 * The arena's entry barrier, object $30 ({@code EntryBarrierObject}, objects/r3/boss.asm:38-156; index entry $2F,
 * r3/object_index_3.asm:51), placed by the act 3 layout at ($430, $540) with a gear $30 px to its left. It is an
 * enemy type $3E with two hits: an attacking player's touch clears the type (the shared enemy branch rebounds him),
 * the hit sound plays and the barrier retracts $30 px at 2.5 px a frame (its gear turns; after 16 frames it animates).
 * Raised, it explodes for 120 frames like the defeated machine, then stops drawing. From the hit on, the first frame
 * the player is at X $480 or more and Y $51C or less starts the boss music ({@code SCMD_BOSSMUS}) and allocates a
 * partial exit barrier $3F at ($420, $540) behind him; the raised barrier and its gear are then deleted.
 */
final class CczBossEntryBarrier extends CczBossPiece implements TouchResponseAttackable {
    /** EntryBarrierObject is index entry $2F (r3/object_index_3.asm:3-51). */
    static final int ID = 0x30;
    /** EntryBarrier_CheckBossTrigger: move.w #$67,d0 (SCMD_BOSSMUS); jsr SubCpuCommand (objects/r3/boss.asm:146-147). */
    static final int SCMD_BOSSMUS = 0x67;

    /** {@code entry_barrier.retract_timer} (var_2a). */
    int retractTimer;
    /** {@code entry_barrier.target_y} (var_2e). */
    int targetY;
    /** {@code entry_barrier.y_speed} (var_30): 16.16. */
    int ySpeed;

    CczBossEntryBarrier(ObjectSpawn spawn) {
        super(spawn, "CczBossEntryBarrier");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        hidden = false;
        switch (routine) {
            case 0 -> {
                init();
                main();
            }
            case 2 -> main();
            case 4 -> retract(player);
            case 6 -> bossActive(player);
            default -> { }
        }
    }

    private boolean hidden;

    @Override protected boolean drawn() { return !hidden && routine != 0; }

    /** EntryBarrier_Init (objects/r3/boss.asm:54-74), falling into EntryBarrier_Main. */
    private void init() {
        routine = 2;
        priority = 4;
        width = 0x30;
        yRadius = 0x10;
        tileWord = CczBossArt.BOSS_TILE;
        ySpeed = -0x28000;
        targetY = (short) (yWord() - 0x30);
        setCollision(0x3E, 2);
        CczBossGear newGear = new CczBossGear(x - 0x30, y, this);
        if (spawnFirst(newGear)) gear = newGear;                    // bsr.w SpawnObject; bne.s EntryBarrier_Main
    }

    /** EntryBarrier_Main (objects/r3/boss.asm:76-87). */
    private void main() {
        if (colType() != 0) return;
        fmSound(FM_HIT);
        routine = 4;
        testAndSet(MOVING);
        anim = 1;
        colStatus = 0;
    }

    /** EntryBarrier_Retract (objects/r3/boss.asm:91-111). */
    private void retract(PlayableEntity player) {
        checkBossTrigger(player);
        retractTimer = (retractTimer + 1) & 0xFF;
        if ((byte) retractTimer >= 0x10) {                          // cmpi.b #$10; blt.s .MoveUp (signed)
            anim = 0;
            animate(CczBossCatalogue.offset(payload(), "entryBarrierAnimations"));
        }
        addYLong(ySpeed);
        if (targetY < yWord()) return;                              // cmp.w obj.y(a0),d0; blt.s .Draw
        clear(MOVING);
        setYWord(targetY);
        routine = 6;
    }

    /** EntryBarrier_BossActive (objects/r3/boss.asm:115-133). */
    private void bossActive(PlayableEntity player) {
        checkBossTrigger(player);
        if ((short) sequenceTimer < SEQUENCE_END) {                 // cmpi.w #$78; bge.s .CheckRemoval
            updateDefeatExplosions();                               // then jsr DrawObject
        } else {
            hidden = true;
        }
        if (!has(TRIGGERED)) return;                                // btst #1; beq.s .End
        CczBossGear oldGear = gear;
        gear = null;                                                // not read again
        deleteObject();
        deleteOther(oldGear);
    }

    /** EntryBarrier_CheckBossTrigger (objects/r3/boss.asm:137-156). */
    private void checkBossTrigger(PlayableEntity player) {
        if (has(TRIGGERED) || player == null) return;
        if ((short) player.getCentreX() < 0x480) return;
        if ((short) player.getCentreY() > 0x51C) return;
        testAndSet(TRIGGERED);
        subCpu(SCMD_BOSSMUS);
        spawnFirst(new CczBossExitBarrier(0x420, 0x540, true));
    }

    /**
     * {@code sub_207292} (r3/player_object_collide.asm:129-150): an attacking player clears the collision type and
     * takes one from {@code collide_status}; the shared enemy branch applies the halved rebound.
     */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        setCollision(0, colStatus - 1);
    }

    @Override String sheet() { return CczBossArt.ENTRY; }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new CczBossEntryBarrier(spawn);
    }
}
