package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Falling debris, object $39 ({@code FloorDebrisObject}, objects/r6/boss.asm:1023-1065), which Eggman drops from the
 * floor he hovers under (BossSpawnFallingProjectile, :1754-1789: X $AC0 + {@code Random} mod $50, X speed
 * {@code Random} mod $280, Y speed $100, gravity 8 + ({@code Random} & $F)). It tumbles until it is within a pixel of
 * the floor, then bursts (BossSpawnFloorDebrisBurst, :1792-1822): it becomes piece 3 and allocates pieces 2, 1 and 0,
 * copies of itself, each with a speed and gravity from {@code BossFloorDebrisMotionTable}. Pieces only move; every
 * debris object is deleted below Y $6F0 (BossDeleteFloorDebrisBelowStage, :1933-1941).
 */
final class WwzFloorDebris extends WwzBossPiece {
    static final int ID = 0x39;
    /** cmpi.w #$6F0,obj.y(a0) (objects/r6/boss.asm:1926). */
    static final int DELETE_Y = 0x6F0;
    // var_2e: Eggman (written, never read).
    WwzBossEggman eggman;
    /** DebrisSprites2 after the burst. */
    private boolean piece;

    WwzFloorDebris(WwzBossEggman eggman) {
        super(ID, 0, 0, "WwzFloorDebris");
        this.eggman = eggman;
    }

    /** A burst copy: routine, flags, layer, sizes, tile and position copied from {@code from}. */
    private WwzFloorDebris(WwzFloorDebris from) {
        super(ID, from.x, from.y, "WwzFloorDebris");
        routine = from.routine;
        priority = from.priority;
        width = from.width;
        yRadius = from.yRadius;
        tileWord = from.tileWord;
        setXWord(from.xWord());
        setYWord(from.yWord());
    }

    private WwzFloorDebris(ObjectSpawn spawn) {
        super(spawn, "WwzFloorDebris");
    }

    /** BossSpawnFallingProjectile's writes after the allocation. */
    void launch(int px, int py, int xSpeed, int gravity) {
        setXWord(px);
        setYWord(py);
        xVel = xSpeed;
        yVel = 0x100;
        accelY = gravity;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {                                      // FloorDebrisInit (objects/r6/boss.asm:1044-1053)
                routine = 2;
                priority = 3;
                width = 8;
                yRadius = 8;
                tileWord = WwzBossArt.DEBRIS_TILE;
            }
            case 2 -> fall();
            default -> integrateVelocity();                  // FloorDebrisSettled (:1072-1075)
        }
        if (!isDestroyed() && (short) y > DELETE_Y) deleteObject();   // after DrawObject
    }

    /** FloorDebrisFall (objects/r6/boss.asm:1045-1058). */
    private void fall() {
        var level = cdLevel();
        animate(WwzBossCatalogue.offset(level == null ? null : level.payload(), "debrisAnimations"));
        integrateVelocity();
        int distance = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance() - 1;
        if (distance > 0) return;
        addY(-distance);
        routine = 4;
        burst();
    }

    /** BossSpawnFloorDebrisBurst / BossSpawnFloorDebrisPiece / BossInitFloorDebrisPiece (objects/r6/boss.asm:1792-1822). */
    private void burst() {
        var image = stageImage();
        var level = cdLevel();
        int table = WwzBossCatalogue.offset(level == null ? null : level.payload(), "debrisMotion");
        if (image == null || table < 0) return;
        WwzFloorDebris target = this;
        for (int d2 = 3, entry = 0; d2 >= 0; d2--, entry++) {
            if (target != this) {
                if (!spawn(target)) return;
            }
            int at = table + entry * 8;
            target.piece = true;
            target.mapFrame = d2;
            target.xVel = (short) image.u16(at);
            target.yVel = (short) image.u16(at + 2);
            target.accelX = (short) image.u16(at + 4);
            target.accelY = (short) image.u16(at + 6);
            if (d2 > 0) target = new WwzFloorDebris(this);
        }
    }

    @Override String sheet() { return piece ? WwzBossArt.DEBRIS2 : WwzBossArt.DEBRIS1; }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzFloorDebris(spawn);
    }
}
