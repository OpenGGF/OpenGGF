package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * An upper arm, object $30 ({@code BossArmObject}, objects/r1/boss.asm:1428-1539): width 8, height 8, tile $2359,
 * swung about the shoulders at the cosine and sine of {@link #angle} + $40 over 16 (X - $A on the back arm).
 * Routine 2 swings it up 2 a frame to 0, 6 down to $30 (each sets {@link #DONE}), 4 and 8 hold, A drops it. The
 * back arm ({@link #BACK}) is never drawn.
 */
final class PpzBossArm extends PpzBossPiece {
    PpzBossShoulder shoulders;
    PpzBossForearm forearm;

    PpzBossArm(int x, int y, PpzBossShoulder shoulders) {
        super(0x30, x, y, "PpzBossArm");
        this.shoulders = shoulders;
    }

    private PpzBossArm(ObjectSpawn spawn) {
        super(spawn, "PpzBossArm");
    }

    @Override String sheet() { return PpzBossArt.ARM; }

    /** BossArmObject: btst #2,obj.var_2c(a0); bne.s locret (objects/r1/boss.asm:1433-1435). */
    @Override protected boolean drawn() { return routine != 0 && !has(BACK); }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                // BossArmObject_0_Routine0 (objects/r1/boss.asm:1454-1461) sets routine 6 and falls into routine 2.
                width = 8;
                yRadius = 8;
                tileWord = PpzBossArt.PART_TILE;
                routine = 6;
                swingUp();
            }
            case 2 -> swingUp();
            case 4, 8 -> place();                               // off_20CA96 lists Routine4 twice
            case 6 -> swingDown();
            case 0xA -> drop();
            default -> { }
        }
    }

    /** BossArmObject_0_Routine2 (objects/r1/boss.asm:1463-1469): {@code subq.b #2; bhi}. */
    private void swingUp() {
        int before = angle;
        angle = (angle - 2) & 0xFF;
        if (before <= 2) {
            routine = 4;
            angle = 0;
            set(DONE);
        }
        place();
    }

    /** BossArmObject_0_Routine6 (objects/r1/boss.asm:1473-1480): {@code addq.b #2; cmpi.b #$30; bcs}. */
    private void swingDown() {
        angle = (angle + 2) & 0xFF;
        if (angle >= 0x30) {
            routine = 8;
            angle = 0x30;
            set(DONE);
        }
        place();
    }

    /** BossArmObject_0_RoutineA (objects/r1/boss.asm:1489-1518). */
    private void drop() {
        if (!testAndSet(STARTED)) {
            phase = 0;
            stepX = 0x10000;
            stepY = -0x20000;
            forearm.setRoutine(4);
        } else {
            shoulders = null;                                    // links the fall no longer reads (rewind ids)
            forearm = null;
            stepX += 0x600;
            stepY += 0x1F00;
        }
        fallAndCheck();
    }

    /** loc_20CB6E (objects/r1/boss.asm:1522-1539). */
    private void place() {
        int a = (angle + 0x40) & 0xFF;
        int px = shoulders.getX() + (cosine(a) >> 4);
        if (has(BACK)) px -= 0xA;
        x = px & 0xFFFF;
        y = (shoulders.getY() + (sine(a) >> 4)) & 0xFFFF;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossArm(spawn);
    }
}
