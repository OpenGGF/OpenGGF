package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Boss smoke, object $3F ({@code BossSmokeObject}, objects/r6/boss.asm:877-951). The defeat allocates one with
 * animation 1 attached to Eggman ($40 above it, $1A4 frames): while its frame is below 7 it draws and throws a puff,
 * a detached smoke with a random speed ({@code BossSpawnExplosionDebris}, :1883-1912; {@code FM_EXPLODE}). A detached
 * puff (animation 0) drifts for $18 frames.
 */
final class WwzBossSmoke extends WwzBossPiece {
    static final int ID = 0x3F;
    // var_2e: Eggman (attached smoke only).
    WwzBossEggman eggman;
    private boolean hidden;

    /** BossSpawnExplosion: animation 1, attached to Eggman. */
    WwzBossSmoke(WwzBossEggman eggman) {
        super(ID, 0, 0, "WwzBossSmoke");
        this.eggman = eggman;
        anim = 1;
    }

    /** BossSpawnExplosionDebris: a puff at {@code (px, py)} with speeds {@code (vx, vy)}. */
    private WwzBossSmoke(int px, int py, int vx, int vy) {
        super(ID, px, py, "WwzBossSmoke");
        x = px & 0xFFFF;
        y = py & 0xFFFF;
        xVel = vx;
        yVel = vy;
    }

    private WwzBossSmoke(ObjectSpawn spawn) {
        super(spawn, "WwzBossSmoke");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        hidden = false;
        if (routine == 0) {                                  // BossSmokeInit (objects/r6/boss.asm:889-911)
            tileWord = WwzBossArt.PART_TILE;
            if (anim != 0) {
                priority = 3;
                width = 0xC;
                yRadius = 0x10;
                routine = 2;
            } else {
                priority = 2;
                width = 0x10;
                yRadius = 0x10;
                routine = 4;
            }
        }
        if (routine == 2) followParent();
        else standalone();
    }

    /** BossSmokeFollowParent (objects/r6/boss.asm:915-934). */
    private void followParent() {
        timer = (timer + 1) & 0xFFFF;
        if (timer == 0x1A4) {
            deleteObject();
            return;
        }
        if (eggman != null) {
            setXWord(eggman.xWord());
            setYWord(eggman.yWord() - 0x40);
        }
        animate(WwzBossCatalogue.offset(payload(), "smokeAnimations"));
        if (mapFrame >= 7) {                                 // BossSmokeStopDrawing
            hidden = true;
            return;
        }
        throwPuff();
    }

    /** BossSmokeStandalone (objects/r6/boss.asm:943-951). */
    private void standalone() {
        timer = (timer + 1) & 0xFFFF;
        if (timer == 0x18) {
            deleteObject();
            return;
        }
        integratePosition();
        animate(WwzBossCatalogue.offset(payload(), "smokeAnimations"));
    }

    /**
     * BossSpawnExplosionDebris (objects/r6/boss.asm:1883-1912): FindObjSlot, FM_EXPLODE, a puff 8 pixels above with
     * {@code Random}'s low word mod $400 as the X speed and minus its high word (15 bits) mod $400 as the Y speed.
     */
    private void throwPuff() {
        WwzBossSmoke puff = new WwzBossSmoke(x, y - 8, 0, 0);
        if (!spawn(puff)) return;
        fmSound(FM_EXPLODE);
        int raw = services().rng().nextRaw();
        int low = (short) raw;                               // andi.l #$FFFF,d0; ext.l d0
        int high = -((raw >>> 16) & 0x7FFF);                 // swap d1; andi.l #$7FFF,d1; neg.l d1
        puff.xVel = (short) (low % 0x400);                   // divs.w #$400,d0; swap d0
        puff.yVel = (short) (high % 0x400);                  // divs.w #$400,d1; swap d1
    }

    private com.openggf.game.soniccd.CdStagePayload payload() {
        var level = cdLevel();
        return level == null ? null : level.payload();
    }

    @Override protected boolean drawn() { return !hidden && routine != 0; }

    @Override String sheet() { return WwzBossArt.SMOKE; }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzBossSmoke(spawn);
    }
}
