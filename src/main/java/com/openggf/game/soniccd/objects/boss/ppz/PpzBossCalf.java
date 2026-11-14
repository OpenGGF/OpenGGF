package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A leg's calf, object $2D ({@code BossCalfObject}, objects/r1/boss.asm:2194-2443): width 8, height $14,
 * tile $2359; a hazard ($BD) during the fight. It hangs at (4, $10) from its thigh plus a 16.16 knee offset
 * ({@link #offsetX}, {@link #offsetY}) stepped by {@link #stepX}/{@link #stepY}. While its leg is planted and
 * carrying, a step moves the thigh, the body and Eggman instead of the calf.
 */
final class PpzBossCalf extends PpzBossPiece {
    PpzBossThigh thigh;
    PpzBossFoot foot;

    PpzBossCalf(int x, int y, PpzBossThigh thigh) {
        super(0x2D, x, y, "PpzBossCalf");
        this.thigh = thigh;
    }

    private PpzBossCalf(ObjectSpawn spawn) {
        super(spawn, "PpzBossCalf");
    }

    @Override String sheet() { return PpzBossArt.CALF; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> hang();
            case 4 -> copyFront();
            case 6 -> kneeForward();
            case 8 -> kneeBack();
            case 0xA -> kneeBackUp();
            case 0xC -> kneeBackDown();
            case 0xE -> fallApart();
            default -> { }
        }
    }

    /** BossCalfObject_0_Routine0 (objects/r1/boss.asm:2215-2225). */
    private void init() {
        routine = 2;
        width = 8;
        yRadius = 0x14;
        tileWord = PpzBossArt.PART_TILE;
        stepX = 0x8000;
        stepY = 0x4000;
    }

    /** BossCalfObject_0_Routine2 (objects/r1/boss.asm:2229-2237). */
    private void hang() {
        x = (thigh.getX() + 4) & 0xFFFF;
        y = (thigh.getY() + 0x10) & 0xFFFF;
    }

    /** BossCalfObject_0_Routine4 (objects/r1/boss.asm:2241-2249): the back calf follows the front during the descent. */
    private void copyFront() {
        PpzBossCalf front = thigh.body.frontLeg.calf;
        x = (front.getX() - 0xA) & 0xFFFF;
        y = front.getY();
    }

    /** BossCalfObject_0_Routine6 (objects/r1/boss.asm:2253-2267). */
    private void kneeForward() {
        if (word(offsetX) < 8) {
            offsetX += stepX;
            if (has(PLANTED)) {
                carry(-stepX, 0);
                return;
            }
        }
        place();
    }

    /** BossCalfObject_0_Routine8 (objects/r1/boss.asm:2271-2285): the Y word is zeroed, its fraction kept. */
    private void kneeBack() {
        offsetY = withWord(offsetY, 0);
        if (word(offsetX) > -8) {
            offsetX -= stepX;
            if (has(PLANTED)) {
                carry(stepX, 0);
                return;
            }
        }
        place();
    }

    /** BossCalfObject_0_RoutineA (objects/r1/boss.asm:2289-2321). */
    private void kneeBackUp() {
        long dx = 0, dy = 0;
        if (word(offsetX) > -8) {
            offsetX -= stepX;
            dx = stepX;
        }
        if (word(offsetY) > -4) {
            dy = stepY;
            offsetY -= dy;
        }
        if (has(PLANTED)) carry(dx, dy);
        else place();
    }

    /** BossCalfObject_0_RoutineC (objects/r1/boss.asm:2325-2358). */
    private void kneeBackDown() {
        long dx = 0, dy = 0;
        if (word(offsetX) > -8) {
            offsetX -= stepX;
            dx = stepX;
        }
        if (word(offsetY) < 4) {
            dy = stepY;
            offsetY += dy;
        }
        if (has(PLANTED)) carry(dx, -dy);
        else place();
    }

    /** loc_20D3AC (objects/r1/boss.asm:2362-2379): carrying, the thigh, body and Eggman move; otherwise nothing. */
    private void carry(long dx, long dy) {
        if (!has(CARRY)) return;
        for (CdBossPart part : new CdBossPart[] {thigh, thigh.body, thigh.body.eggman}) {
            part.addXLong(dx);
            part.addYLong(dy);
        }
    }

    /** loc_20D3DE (objects/r1/boss.asm:2383-2395). */
    private void place() {
        x = (thigh.getX() + 4 + word(offsetX)) & 0xFFFF;
        y = (thigh.getY() + 0x10 + word(offsetY)) & 0xFFFF;
    }

    /** BossCalfObject_0_RoutineE (objects/r1/boss.asm:2399-2443). */
    private void fallApart() {
        thigh = null;                                           // links the fall no longer reads (rewind ids)
        foot = null;
        if (!testAndSet(STARTED)) {
            stepX = 0;
            stepY = -0x20000;
        } else {
            stepX += has(PLANTED) ? 0x600 : -0x600;            // bit 4 set: subi.l #-$600; clear: addi.l #-$600
            stepY += 0x1A60;
        }
        fallAndCheck();
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossCalf(spawn);
    }
}
