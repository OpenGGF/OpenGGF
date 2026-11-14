package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The large bomb, object $38 ({@code LargeBombObject}, objects/r5/boss.asm:759-857), allocated by the dropper $18
 * below it. It follows the dropper's X; once the dropper seeks the player it bobs (a sine of three pixels, its angle
 * advancing $480 a frame from $C000); on the dropper's release it falls $22000 a frame to Y $2B8 (FM $B4), sits 8
 * frames and bursts into four small bombs. Touch: see {@link QqzBomb}.
 */
final class QqzLargeBomb extends QqzBomb {
    /** var_38: the last bob offset; var_32: the bob angle (its high byte is the SineCosine angle). */
    private int bob;
    private int bobAngle;

    QqzLargeBomb(int x, int y, QqzBombDropper dropper) {
        super(0x38, x, y, dropper, "QqzLargeBomb");
    }

    private QqzLargeBomb(ObjectSpawn spawn) {
        super(spawn, "QqzLargeBomb");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                init();
                follow();
            }
            case 2 -> follow();
            case 4 -> bobOrFall();
            case 6 -> fall();
            case 8 -> settle();
            default -> { }
        }
    }

    /** LargeBombObject_0_Routine0 (objects/r5/boss.asm:777-788). */
    private void init() {
        routine = 2;
        priority = 4;
        width = 0xC;
        yRadius = 0xC;
        tileWord = QqzBossArt.PART_TILE;
        setCollision(TOUCH_TYPE, 2);
        bob = -3;
        bobAngle = 0xC000;
    }

    /** LargeBombObject_0_Routine2 (objects/r5/boss.asm:790-800). */
    private void follow() {
        if (dropper == null) return;
        setXWord(dropper.xWord());
        if (dropper.routine() != 0xA) return;
        routine = 4;
        bobOrFall();
    }

    /** LargeBombObject_0_Routine4 (objects/r5/boss.asm:802-822). */
    private void bobOrFall() {
        if (dropper == null) return;
        setXWord(dropper.xWord());
        if (dropper.clear(RELEASE)) {
            routine = 6;
            fall();
            return;
        }
        addY(-bob);
        bobAngle = (bobAngle + 0x480) & 0xFFFF;
        // andi.l #$FFFF,d0; muls.w #3,d0; asr.w #8,d0.
        bob = (short) (sine(bobAngle >> 8) * 3) >> 8;
        addY(bob);
    }

    /** LargeBombObject_0_Routine6 (objects/r5/boss.asm:824-840). */
    private void fall() {
        if (touchedPlayer()) {
            becomeBossExplosion();
            return;
        }
        if (dropperBeaten()) {
            deleteAndSkip();
            return;
        }
        addYLong(0x22000);
        if (yWord() < 0x2B8) return;
        fmSound(QqzBossBooth.FM_LAND);
        routine = 8;
        settle();
    }

    /** LargeBombObject_0_Routine8 and loc_20BB7A (objects/r5/boss.asm:842-857). */
    private void settle() {
        if (touchedPlayer()) {
            becomeBossExplosion();
            return;
        }
        if (dropperBeaten()) {
            deleteAndSkip();
            return;
        }
        timer = (timer + 1) & 0xFF;
        if (timer != 8) return;
        // sub_20C3B0 (objects/r5/boss.asm:1732-1747): subtypes 3 to 0, at this bomb's 16.16 position, var_2e the
        // dropper; the first failed allocation ends it.
        for (int subtype = 3; subtype >= 0; subtype--) {
            if (!spawn(new QqzSmallBomb(xLong(), yLong(), subtype, dropper))) break;
        }
        deleteAndSkip();
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzLargeBomb(spawn);
    }
}
