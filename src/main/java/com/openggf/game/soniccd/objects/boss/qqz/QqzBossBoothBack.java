package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The booth's back, object $35 ({@code BossBoothBackObject}, objects/r5/boss.asm:508-543), allocated by the booth $38
 * below it: layer 5, it copies the booth's position every frame and draws unless the booth is hidden.
 */
final class QqzBossBoothBack extends QqzBossPiece {
    /** var_2e: the booth. */
    QqzBossBooth booth;

    QqzBossBoothBack(int x, int y, QqzBossBooth booth) {
        super(0x35, x, y, "QqzBossBoothBack");
        this.booth = booth;
    }

    private QqzBossBoothBack(ObjectSpawn spawn) {
        super(spawn, "QqzBossBoothBack");
    }

    @Override String sheet() { return QqzBossArt.BOOTH_BACK; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine = 2;                                           // BossBoothBackObject_0_Routine0 (:531-538)
            priority = 5;
            width = 0x10;
            yRadius = 0x10;
            tileWord = QqzBossArt.PART_TILE;
        }
        if (booth == null) return;
        setXWord(booth.xWord());                                   // BossBoothBackObject_0_Routine2 (:540-543)
        setYWord(booth.yWord());
        skipDraw = booth.has(HIDDEN);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBossBoothBack(spawn);
    }
}
