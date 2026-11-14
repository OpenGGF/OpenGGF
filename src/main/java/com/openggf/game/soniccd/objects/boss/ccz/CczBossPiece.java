package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

/**
 * State the Collision Chaos boss's slots share (objects/r3/boss.asm:3-34). The routines name their scratch bytes by
 * offset; these fields name them by use:
 * <ul>
 *   <li>{@link #flags} ({@code var_2c}: {@code entry_barrier.flags}, {@code boss.flags}, {@code gear.flags}): bit 0
 *       {@link #DONE} (the explosion sequence ended: the slot stops drawing), bit 1 {@link #TRIGGERED} (entry
 *       barrier: the arena was entered), bit 2 {@link #MOVING} (the entry barrier retracts or the machine moves: its
 *       gear turns).</li>
 *   <li>{@link #sequenceTimer} ({@code var_3e}: {@code encounter.sequence_timer}, {@code boss.timer}): frames
 *       counted by the explosion patterns.</li>
 *   <li>{@link #gear} ({@code var_34}: {@code entry_barrier.link}, {@code boss.gear_link}): the arena actor's gear,
 *       whose {@link #DONE} bit the end of its explosions sets.</li>
 * </ul>
 */
abstract class CczBossPiece extends CdBossPart {
    static final int DONE = 0x01, TRIGGERED = 0x02, MOVING = 0x04;
    /** move.w #$9E,d0 (FM_EXPLODE); jsr PlayFmSound (objects/r3/boss.asm:712-713). */
    static final int FM_EXPLODE = 0x9E;
    /** move.w #$AC,d0 (FM_AC); jsr PlayFmSound: a barrier or the machine is struck (objects/r3/boss.asm:79-80, 446-447). */
    static final int FM_HIT = 0xAC;
    /** The bomb's ID; the arena's explosions are bombs started in routine $A (objects/r3/boss.asm:710-711). */
    static final int BOMB_ID = 0x3E;
    /** cmpi.w #$78,encounter.sequence_timer(a0) (objects/r3/boss.asm:582-584). */
    static final int SEQUENCE_END = 0x78;

    int flags;
    int sequenceTimer;
    CczBossGear gear;

    CczBossPiece(int id, int x, int y, String name) {
        super(id, x, y, name);
    }

    CczBossPiece(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    final boolean has(int bit) { return (flags & bit) != 0; }

    /** {@code bset}: sets the bit and returns whether it was already set. */
    final boolean testAndSet(int bit) {
        boolean was = (flags & bit) != 0;
        flags |= bit;
        return was;
    }

    /** {@code bclr}: clears the bit and returns whether it was set. */
    final boolean clear(int bit) {
        boolean was = (flags & bit) != 0;
        flags &= ~bit;
        return was;
    }

    /** {@code bchg}: toggles the bit and returns whether it was set. */
    final boolean toggle(int bit) {
        boolean was = (flags & bit) != 0;
        flags ^= bit;
        return was;
    }

    final CdStagePayload payload() {
        var level = cdLevel();
        return level == null ? null : level.payload();
    }

    /** {@code SpawnObjectAfter} ({@code FindNextObjSlot}): the first free slot after this one; false when none is. */
    final boolean spawnAfter(AbstractObjectInstance child) {
        var objects = services().objectManager();
        if (objects == null) return false;
        objects.addDynamicObjectAfterCurrent(child);
        return !child.isDestroyed();
    }

    /** {@code SpawnObject} ({@code FindObjSlot}): the first free dynamic slot; false when none is ({@code bne}). */
    final boolean spawnFirst(AbstractObjectInstance child) {
        return spawn(child);
    }

    /**
     * {@code BossArena_UpdateDefeatExplosions} (objects/r3/boss.asm:581-597): the sequence timer counts; at $78 the
     * slot and its gear are {@link #DONE}; before it, the defeat pattern.
     */
    final void updateDefeatExplosions() {
        sequenceTimer = (sequenceTimer + 1) & 0xFFFF;
        if (sequenceTimer == SEQUENCE_END) {
            testAndSet(DONE);
            if (gear != null) gear.testAndSet(DONE);              // movea.w encounter.link(a0),a1; bset #0
            return;
        }
        if (sequenceTimer > SEQUENCE_END) return;                  // bcc.s .End (unsigned)
        spawnTimedExplosion("defeatExplosions");
    }

    /**
     * {@code BossArena_SpawnTimedExplosion} (objects/r3/boss.asm:684-716): pattern {@code column} starts with an
     * interval and a count; when the sequence timer is a multiple of the interval, a bomb in its silent-explosion
     * routine ($A) is allocated ({@code SpawnObject}) at this slot's position plus offset (timer / interval) mod
     * count, and {@code FM_EXPLODE} plays.
     */
    final void spawnTimedExplosion(String column) {
        CdStageImage image = stageImage();
        CdStagePayload payload = payload();
        if (image == null || payload == null) return;
        int table = CczBossCatalogue.offset(payload, column);
        if (table < 0) return;
        int timer = sequenceTimer & 0xFFFF;
        int interval = image.u16(table);
        if (timer % interval != 0) return;                         // divu.w; swap; tst.w d0; bne.s .End
        int count = image.u16(table + 2);
        int index = (timer / interval) % count;                    // divu.w d0,d2; swap d2
        int entry = table + 4 + index * 4;
        int ex = x + (short) image.u16(entry), ey = y + (short) image.u16(entry + 2);
        CczBossBomb explosion = CczBossBomb.silentExplosion(ex & 0xFFFF, ey & 0xFFFF);
        if (!spawnFirst(explosion)) return;                        // bsr.w SpawnObject; bne.s .End
        fmSound(FM_EXPLODE);
    }

    /** {@code jmp DeleteOtherObject}: another slot is cleared. */
    static void deleteOther(CdBossPart other) {
        if (other != null && !other.isDestroyed()) other.setDestroyed(true);
    }

    @Override
    protected String sheetKey() {
        return artProvider() == null ? null : CczBossArt.key(sheet());
    }

    /** The part's sheet kind ({@link CczBossArt}). */
    abstract String sheet();
}
