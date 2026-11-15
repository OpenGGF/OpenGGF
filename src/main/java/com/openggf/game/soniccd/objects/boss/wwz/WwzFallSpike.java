package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A falling spike, object $38 ({@code FallSpikeObject}, objects/r6/boss.asm:953-1011), six of which Eggman drops from
 * the top floor's ceiling (BossSpawnProjectileWave). It falls 3.5 pixels a frame as a hazard ($BF) until the floor is
 * within 3 pixels, lands there harmless, then blinks: drawn 5 frames of every 10, seven times, and is deleted.
 */
final class WwzFallSpike extends WwzBossPiece {
    static final int ID = 0x38;
    /** move.b #$BF,obj.collide_type(a0); move.b #2,obj.collide_status(a0) (objects/r6/boss.asm:969-970). */
    static final int COL_TYPE = 0xBF, COL_STATUS = 2;
    /** addi.l #$38000,obj.y(a0) (objects/r6/boss.asm:973). */
    static final long FALL_STEP = 0x38000L;
    // var_2e: Eggman (written, never read).
    WwzBossEggman eggman;
    /** var_2b: blink cycles. */
    private int cycles;
    private boolean hidden;

    WwzFallSpike(WwzBossEggman eggman) {
        super(ID, 0, 0, "WwzFallSpike");
        this.eggman = eggman;
    }

    private WwzFallSpike(ObjectSpawn spawn) {
        super(spawn, "WwzFallSpike");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        hidden = false;
        if (routine == 0) {                                  // FallSpikeInit (objects/r6/boss.asm:972-995)
            routine = 2;
            priority = 3;
            width = 4;
            yRadius = 0xC;
            tileWord = WwzBossArt.PART_TILE;
            setCollision(COL_TYPE, COL_STATUS);
        }
        if (routine == 2) {                                  // FallSpikeDrop (:972-983)
            addYLong(FALL_STEP);
            int distance = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance() - 3;
            if (distance > 0) return;
            addY(-distance);
            routine = 4;
            setCollision(0, 0);
            return;
        }
        timer = (timer + 1) & 0xFF;                          // FallSpikeWait (:987-1006)
        if (timer < 5) return;
        hidden = true;
        if (timer != 0xA) return;
        cycles = (cycles + 1) & 0xFF;
        if (cycles == 7) {
            deleteObject();
            return;
        }
        timer = 0;
    }

    @Override protected boolean drawn() { return !hidden && routine != 0; }

    @Override String sheet() { return WwzBossArt.FALL_SPIKE; }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzFallSpike(spawn);
    }
}
