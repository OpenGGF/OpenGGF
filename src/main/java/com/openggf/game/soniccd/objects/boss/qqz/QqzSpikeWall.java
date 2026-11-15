package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;

/**
 * The spike wall that closes the arena behind the player, object $21 ({@code SpikeWallObject},
 * objects/r5/boss.asm:1053-1130), placed by the act 3 layout below the floor at ($C10, $2E0). Touch $BF (a hazard).
 * Once the player passes X $C50 it queues the boss art (PLC 4), rises 2 pixels a frame to Y $2A0 and slides right 2 a
 * frame to X $CD0, and stays. While it moves and after, it is solid with half-width $C and Y radius $1E (restored to $20
 * after the call).
 */
public final class QqzSpikeWall extends QqzBossPiece implements ReevaluatingSolidObject {
    /** SpikeWallObject is index entry $20 (r5/object_index_3_act3.asm:3-36). */
    public static final int ID = 0x21;

    public QqzSpikeWall(ObjectSpawn spawn) {
        super(spawn, "QqzSpikeWall");
    }

    @Override String sheet() { return QqzBossArt.SPIKE_WALL; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                init();
                waitForPlayer(player);
            }
            case 2 -> waitForPlayer(player);
            case 4 -> rise();
            case 6 -> slide();
            case 8 -> solid();
            default -> { }
        }
    }

    /** SpikeWallObject_0_Routine0 (objects/r5/boss.asm:1071-1080). */
    private void init() {
        routine = 2;
        priority = 4;
        width = 0x20;
        yRadius = 0x20;
        tileWord = QqzBossArt.SPIKE_WALL_TILE;
        setCollision(0xBF, 2);
    }

    /** SpikeWallObject_0_Routine2 (objects/r5/boss.asm:1082-1093). */
    private void waitForPlayer(PlayableEntity player) {
        if (player == null || (short) player.getCentreX() < 0xC50) return;
        // moveq #4,d0; jsr AddGfxQueue: PLC 4, the boss art; the sheets exist from the stage load (QqzBossArt).
        routine = 4;
        rise();
    }

    /** SpikeWallObject_0_Routine4 (objects/r5/boss.asm:1095-1105). */
    private void rise() {
        addYLong(-0x20000);
        if (yWord() > 0x2A0) {
            solid();
            return;
        }
        setYWord(0x2A0);
        routine = 6;
        slide();
    }

    /** SpikeWallObject_0_Routine6 (objects/r5/boss.asm:1107-1117). */
    private void slide() {
        addXLong(0x20000);
        if (xWord() < 0xCD0) {
            solid();
            return;
        }
        routine = 8;
        setXWord(0xCD0);
        solid();
    }

    /** loc_20BE66 (objects/r5/boss.asm:1124-1130). */
    private void solid() {
        width = 0xC;
        yRadius = 0x1E;
        solidObject(Entry.SOLID);
        width = 0x20;
        yRadius = 0x20;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzSpikeWall(spawn);
    }
}
