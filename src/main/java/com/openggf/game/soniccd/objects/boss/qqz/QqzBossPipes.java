package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The booth's pipes, object $36 ({@code BossPipesObject}, objects/r5/boss.asm:547-585), allocated by the booth at its
 * start: they stay where they were made and show one more length of pipe ({@code (booth Y - Y) / $20}) as the booth
 * sinks; drawn unless the booth is hidden.
 */
final class QqzBossPipes extends QqzBossPiece {
    /** var_2e: the booth. */
    QqzBossBooth booth;

    QqzBossPipes(int x, int y, QqzBossBooth booth) {
        super(0x36, x, y, "QqzBossPipes");
        this.booth = booth;
    }

    private QqzBossPipes(ObjectSpawn spawn) {
        super(spawn, "QqzBossPipes");
    }

    @Override String sheet() { return QqzBossArt.PIPES; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine = 2;                                           // BossPipesObject_0_Routine0 (:570-577)
            priority = 4;
            width = 8;
            yRadius = 0x30;
            tileWord = QqzBossArt.PART_TILE;
        }
        if (booth == null) return;
        // moveq #0,d0; move.w $C(a2),d0; sub.w obj.y(a0),d0; divu.w #$20,d0; move.b d0,obj.sprite_frame(a0).
        mapFrame = (((booth.yWord() - yWord()) & 0xFFFF) / 0x20) & 0xFF;
        skipDraw = booth.has(HIDDEN);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBossPipes(spawn);
    }
}
