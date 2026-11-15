package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The booth's explosion, object $23 ({@code BossExplosionObject}, objects/r5/boss.asm:1493-1528): the common explosion
 * art (tile $680), rising 3 pixels a frame through its one animation (BossExplodeAnims); the animation's $FC end moves
 * it to routine 4, which deletes it the next frame.
 */
final class QqzBossExplosion extends QqzBossPiece {
    QqzBossExplosion(int x, int y) {
        super(0x23, x, y, "QqzBossExplosion");
    }

    private QqzBossExplosion(ObjectSpawn spawn) {
        super(spawn, "QqzBossExplosion");
    }

    @Override String sheet() { return QqzBossArt.EXPLOSION; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                init();
                addYLong(-0x30000);
            }
            case 2 -> addYLong(-0x30000);                          // BossExplosionObject_0_Routine2
            case 4 -> {
                deleteAndSkip();                                   // BossExplosionObject_0_Routine4
                return;
            }
            default -> { }
        }
        routine = (routine + animate(QqzBossCatalogue.offset(payload(), "explosionAnimations"))) & 0xFF;
    }

    /** BossExplosionObject_0_Routine0 (objects/r5/boss.asm:1511-1518). */
    private void init() {
        routine = 2;
        priority = 3;
        width = 0x10;
        yRadius = 0x10;
        tileWord = QqzBossArt.EXPLOSION_TILE;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBossExplosion(spawn);
    }
}
