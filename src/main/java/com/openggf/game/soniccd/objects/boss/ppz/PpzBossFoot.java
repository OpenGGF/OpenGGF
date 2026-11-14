package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A leg's foot, object $2E ({@code BossFootObject}, objects/r1/boss.asm:2447-2685): width $20, height $14,
 * tile $2359; a hazard ($BE) during the fight. It sits at (-$B, $E) from its calf plus a 16.16 offset. Routine
 * 2 lowers it until {@code CheckBlockDown} finds the floor; the landing plants the leg (bit 4 on foot, calf and
 * thigh), shakes the floor for 8 frames and sends {@code SCMD_STOMPSFX}. Routine 6 lifts it; planted and
 * carrying, a lift or lower moves the calf, thigh, body and Eggman instead.
 */
final class PpzBossFoot extends PpzBossPiece {
    /** loc_20D556: move.w #$7E,d0 (SCMD_STOMPSFX); jsr SubCpuCommand (objects/r1/boss.asm:2531-2532). */
    static final int SCMD_STOMPSFX = 0x7E;

    PpzBossCalf calf;

    PpzBossFoot(int x, int y, PpzBossCalf calf) {
        super(0x2E, x, y, "PpzBossFoot");
        this.calf = calf;
    }

    private PpzBossFoot(ObjectSpawn spawn) {
        super(spawn, "PpzBossFoot");
    }

    @Override String sheet() { return PpzBossArt.FOOT; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> lower();
            case 4 -> waitForFloor();
            case 6 -> lift();
            case 8 -> place();
            case 0xA -> copyFront();
            case 0xC -> fallApart();
            default -> { }
        }
    }

    /** BossFootObject_0_Routine0 (objects/r1/boss.asm:2467-2480). */
    private void init() {
        width = 0x20;
        yRadius = 0x14;
        tileWord = PpzBossArt.PART_TILE;
        stepX = 0x4000;
        stepY = 0x8000;
        routine = 4;
        set(DONE);
        offsetX = withWord(offsetX, -8);
        offsetY = withWord(offsetY, 0x10);
        place();
    }

    /** BossFootObject_0_Routine2 (objects/r1/boss.asm:2484-2533). */
    private void lower() {
        offsetX -= stepX;
        offsetY += stepY;
        int limit = has(PLANTED) && has(FAST) ? 0xC : 0x10;
        if (word(offsetY) >= limit) {
            set(DONE);                                          // loc_20D548
            routine = 4;
            return;
        }
        follow();                                               // loc_20D536: bsr.w loc_20D620
        if (floorDistance() <= 0) land();
    }

    /** loc_20D556 (objects/r1/boss.asm:2522-2533). */
    private void land() {
        set(PLANTED);
        calf.set(PLANTED);
        calf.thigh.set(PLANTED);
        calf.thigh.body.eggman.shake = 8;
        subCpu(SCMD_STOMPSFX);
    }

    /** BossFootObject_0_Routine6 (objects/r1/boss.asm:2537-2552). */
    private void lift() {
        offsetX += stepX;
        offsetY -= stepY;
        if (word(offsetY) <= 0) {
            offsetX = 0;
            offsetY = 0;
            set(DONE);
            routine = 8;
        }
        follow();
    }

    /** BossFootObject_0_Routine4 (objects/r1/boss.asm:2556-2565). */
    private void waitForFloor() {
        if (has(PLANTED)) return;
        place();
        if (floorDistance() <= 0) land();
    }

    /** BossFootObject_0_RoutineA (objects/r1/boss.asm:2575-2587): the back foot follows the front during the descent. */
    private void copyFront() {
        PpzBossFoot front = calf.thigh.body.frontLeg.calf.foot;
        x = (front.getX() - 0xA) & 0xFFFF;
        y = front.getY();
        offsetX = withWord(offsetX, word(front.offsetX));
        offsetY = withWord(offsetY, word(front.offsetY));
    }

    /**
     * loc_20D620 (objects/r1/boss.asm:2591-2621): not planted, the foot follows its calf; planted and carrying,
     * the step moves the calf, thigh, body and Eggman (lowering: negated); planted only, nothing moves.
     */
    private void follow() {
        if (!has(PLANTED)) {
            place();
            return;
        }
        if (!has(CARRY)) return;
        long dx = stepX, dy = stepY;
        if (routine != 6) {
            dx = -dx;
            dy = -dy;
        }
        for (CdBossPart part : new CdBossPart[] {calf, calf.thigh, calf.thigh.body, calf.thigh.body.eggman}) {
            part.addXLong(-dx);
            part.addYLong(dy);
        }
    }

    /** loc_20D67A (objects/r1/boss.asm:2625-2637). */
    private void place() {
        x = (calf.getX() - 0xB + word(offsetX)) & 0xFFFF;
        y = (calf.getY() + 0xE + word(offsetY)) & 0xFFFF;
    }

    /** BossFootObject_0_RoutineC (objects/r1/boss.asm:2641-2685). */
    private void fallApart() {
        calf = null;                                            // a link the fall no longer reads (rewind ids)
        if (!testAndSet(STARTED)) {
            stepX = 0;
            stepY = -0x23000;
        } else {
            stepX += has(PLANTED) ? 0x660 : -0x660;            // bit 4 set: subi.l #-$660; clear: addi.l #-$660
            stepY += 0x1660;
        }
        fallAndCheck();
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossFoot(spawn);
    }
}
