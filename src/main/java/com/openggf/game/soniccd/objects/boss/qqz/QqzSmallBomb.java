package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A small bomb, object $39 ({@code SmallBombObject}, objects/r5/boss.asm:861-939), four of which burst from the large
 * bomb with subtypes 3 to 0. The subtype picks its start offset, 16.16 X and Y speed (word_20BCA8) and acceleration
 * (dword_20BCB4, every $14 bytes). It waits 4 frames, then flies, accelerating, until Y $2C8 or its touch type is
 * cleared. Touch: see {@link QqzBomb}.
 */
final class QqzSmallBomb extends QqzBomb {
    /** var_3a / x_speed (the long spans the X and Y speed words): 16.16 X and Y speed. */
    private int xSpeed;
    private int ySpeed;
    private int subtype;

    QqzSmallBomb(long xFixed, long yFixed, int subtype, QqzBombDropper dropper) {
        super(0x39, (int) (xFixed >>> 16), (int) (yFixed >>> 16), dropper, "QqzSmallBomb");
        setXLong(xFixed);
        setYLong(yFixed);
        this.subtype = subtype;
    }

    private QqzSmallBomb(ObjectSpawn spawn) {
        super(spawn, "QqzSmallBomb");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                init();
                countdown();
            }
            case 2 -> countdown();
            case 4 -> fly();
            default -> { }
        }
    }

    /** SmallBombObject_0_Routine0 (objects/r5/boss.asm:877-900). */
    private void init() {
        routine = 2;
        priority = 1;
        width = 8;
        yRadius = 8;
        tileWord = QqzBossArt.PART_TILE;
        setCollision(TOUCH_TYPE, 2);
        mapFrame = 1;
        var image = stageImage();
        var payload = payload();
        if (image == null || payload == null) return;
        int[] start = QqzBossCatalogue.smallBombStart(image, payload, subtype);
        addX(start[0]);
        addY(start[1]);
        xSpeed = start[2];
        ySpeed = start[3];
    }

    /** SmallBombObject_0_Routine2 (objects/r5/boss.asm:902-914). */
    private void countdown() {
        if (touchedPlayer()) {
            becomeBossExplosion();
            return;
        }
        timer = (timer + 1) & 0xFF;
        if (timer != 4) return;
        timer = 0;
        routine = 4;
        fly();
    }

    /** SmallBombObject_0_Routine4 (objects/r5/boss.asm:916-939). */
    private void fly() {
        if (touchedPlayer()) {
            becomeBossExplosion();
            return;
        }
        if (dropperBeaten()) {
            deleteAndSkip();
            return;
        }
        addXLong(xSpeed);
        addYLong(ySpeed);
        var image = stageImage();
        var payload = payload();
        if (image != null && payload != null) {
            int[] accel = QqzBossCatalogue.smallBombAccel(image, payload, subtype);
            xSpeed += accel[0];
            ySpeed += accel[1];
        }
        if (yWord() >= 0x2C8 || colType() == 0) deleteAndSkip();
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzSmallBomb(spawn);
    }
}
