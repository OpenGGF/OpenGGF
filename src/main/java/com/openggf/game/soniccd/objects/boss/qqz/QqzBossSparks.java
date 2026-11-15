package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A spark, object $20 ({@code BossSparksObject}, objects/r5/boss.asm:984-1043). Both kinds start at speed -$20000 in
 * X and Y (16.16). Kind 0 (sub_20C196, from the worn booth) rolls left along the belt, its X speed falling $1000 a
 * frame, and is deleted at X $D78 or less or off screen; kind 1 (the landing and give-out bursts, {@code var_2d} = 1)
 * falls with gravity $2000 and the same X drag until off screen.
 */
final class QqzBossSparks extends QqzBossPiece {
    /** var_3a / x_speed: 16.16 X and Y speed. */
    private int xSpeed;
    private int ySpeed;

    QqzBossSparks(int x, int y, int kind) {
        super(0x20, x, y, "QqzBossSparks");
        step = kind;                                               // move.b #1,obj.var_2d(a1) for a burst spark
    }

    private QqzBossSparks(ObjectSpawn spawn) {
        super(spawn, "QqzBossSparks");
    }

    @Override String sheet() { return QqzBossArt.SPARKS; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                init();
                if (step == 0) roll();
                else {
                    routine = 4;
                    fall();
                }
            }
            case 2 -> roll();
            case 4 -> fall();
            default -> { }
        }
        if (skipDraw) return;
        animate(QqzBossCatalogue.offset(payload(), "sparksAnimations"));
    }

    /** BossSparksObject_0_Routine0 (objects/r5/boss.asm:1002-1015). */
    private void init() {
        routine = 2;
        priority = 2;
        width = 8;
        yRadius = 4;
        tileWord = QqzBossArt.PART_TILE;
        xSpeed = -0x20000;
        ySpeed = -0x20000;
    }

    /** BossSparksObject_0_Routine2 (objects/r5/boss.asm:1019-1025). */
    private void roll() {
        xSpeed += -0x1000;
        addXLong(xSpeed);
        if (xWord() <= 0xD78) {
            deleteAndSkip();
            return;
        }
        if (offScreen()) deleteAndSkip();                          // loc_20BDA8
    }

    /** BossSparksObject_0_Routine4 (objects/r5/boss.asm:1029-1043). */
    private void fall() {
        xSpeed += -0x1000;
        ySpeed += 0x2000;
        addXLong(xSpeed);
        addYLong(ySpeed);
        if (offScreen()) deleteAndSkip();
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBossSparks(spawn);
    }
}
