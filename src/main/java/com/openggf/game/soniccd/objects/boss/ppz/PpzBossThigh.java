package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A leg's thigh, object $2C ({@code BossThighObject}, objects/r1/boss.asm:1782-2190): width 8, height 8,
 * tile $2359, hung from the body at ($C, $14) plus the hip angle's cosine and sine over 16 ({@link #angle},
 * stepped by {@link #angleStep()}). While its foot is planted ({@link #PLANTED}) and the leg carries
 * ({@link #CARRY}) the angle change moves the body and Eggman instead. Its routine is the leg's step: lift,
 * lower, plant, swing, the descent copy (10) and the fall apart (18).
 */
final class PpzBossThigh extends PpzBossPiece {
    PpzBossBody body;
    PpzBossCalf calf;
    /**
     * The hip angle's step (2 slow, 8 fast) is {@code var_3c}'s first byte ({@code move.b #8,obj.var_3c(a1)}), the
     * high byte of the fall's X step long ({@link #stepX}). When the defeat finds {@link #STARTED} already set by the
     * carry, routine $18 skips its {@code move.l #0,obj.var_3c} and the thigh flies off at the step byte times
     * $10000 pixels a frame ({@code FixBugs = 0}).
     */
    int angleStep() { return (int) ((stepX >>> 24) & 0xFF); }

    void setAngleStep(int step) { stepX = (stepX & 0x00FFFFFFL) | ((long) (step & 0xFF) << 24); }
    /** var_34 / var_38 words: the last cosine and sine, for the carry delta. */
    private int lastCos;
    private int lastSin;

    PpzBossThigh(int x, int y, PpzBossBody body) {
        super(0x2C, x, y, "PpzBossThigh");
        this.body = body;
    }

    private PpzBossThigh(ObjectSpawn spawn) {
        super(spawn, "PpzBossThigh");
    }

    @Override String sheet() { return PpzBossArt.THIGH; }

    /** sub_20C8D0 / sub_20C918 (objects/r1/boss.asm:1277-1309): sets the bit on the thigh, its calf and its foot. */
    void setChain(int bit) {
        set(bit);
        calf.set(bit);
        calf.foot.set(bit);
    }

    /** sub_20C8F4 / sub_20C93C (objects/r1/boss.asm:1289-1321). */
    void clearChain(int bit) {
        clear(bit);
        calf.clear(bit);
        calf.foot.clear(bit);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> lower();
            case 4 -> raise();
            case 6 -> waitForFoot();
            case 8 -> lift();
            case 0xA -> settleDown();
            case 0xC -> settleUp();
            case 0xE -> centre();
            case 0x10 -> copyFront();
            case 0x12 -> swingDown();
            case 0x14 -> swingUp();
            case 0x16 -> reach();
            case 0x18 -> fallApart();
            default -> { }
        }
    }

    /** BossThighObject_0_Routine0 (objects/r1/boss.asm:1808-1818): no position this frame. */
    private void init() {
        width = 8;
        yRadius = 8;
        tileWord = PpzBossArt.PART_TILE;
        routine = 0xC;
        angle = 0x58;
        setAngleStep(2);
    }

    private int stepped(int delta) { return (angle + delta) & 0xFF; }

    /** BossThighObject_0_Routine2 (objects/r1/boss.asm:1822-1851). */
    private void lower() {
        PpzBossFoot foot = calf.foot;
        if (foot.routine() != 6 && foot.routine() != 8) {
            foot.clear(DONE);
            foot.setRoutine(6);
        }
        if ((byte) angle > 0) {
            angle = stepped(-angleStep());
            if ((byte) angle > 0) {
                position();
                return;
            }
        }
        angle = 0;
        if (foot.routine() == 8 || foot.clear(DONE)) routine = 4;
        position();
    }

    /** BossThighObject_0_Routine4 (objects/r1/boss.asm:1855-1867). */
    private void raise() {
        angle = stepped(angleStep());
        if (angle >= 0x58) {
            angle = 0x58;
            routine = 6;
            calf.foot.setRoutine(2);
        }
        position();
    }

    /** BossThighObject_0_Routine6 (objects/r1/boss.asm:1871-1879). */
    private void waitForFoot() {
        if (calf.foot.has(PLANTED)) set(DONE);
        position();
    }

    /** BossThighObject_0_Routine8 (objects/r1/boss.asm:1883-1894). */
    private void lift() {
        if (!has(DONE)) {
            angle = stepped(-angleStep());
            if (angle < 0x18) {
                angle = 0x18;
                set(DONE);
            }
        }
        position();
    }

    /** BossThighObject_0_RoutineA (objects/r1/boss.asm:1898-1929). */
    private void settleDown() {
        PpzBossFoot foot = calf.foot;
        if (foot.routine() != 8) {
            foot.setRoutine(6);
        } else if (angle == 0) {
            if (foot.clear(DONE)) set(DONE);
            position();
            return;
        }
        if (angle != 0) angle = stepped(-angleStep());
        position();
    }

    /** BossThighObject_0_RoutineC (objects/r1/boss.asm:1933-1964). */
    private void settleUp() {
        PpzBossFoot foot = calf.foot;
        if (foot.routine() != 4) {
            foot.setRoutine(2);
        } else if ((byte) angle >= 0x58) {
            if (foot.clear(DONE)) set(DONE);
            position();
            return;
        }
        if ((byte) angle < 0x58) angle = stepped(angleStep());
        position();
    }

    /** BossThighObject_0_RoutineE (objects/r1/boss.asm:1968-2001): to $18 from either side. */
    private void centre() {
        int value = (byte) angle;
        if (value < 0x18) {
            angle = stepped(angleStep());
            calf.foot.set(FAST);
            body.backLeg.calf.foot.set(FAST);
            settleUp();
            return;
        }
        if (value > 0x18) {
            angle = stepped(-angleStep());
            settleDown();
            return;
        }
        if (calf.foot.has(DONE)) set(DONE);
        position();
    }

    /** BossThighObject_0_Routine10 (objects/r1/boss.asm:2005-2012): the back thigh follows the front during the descent. */
    private void copyFront() {
        PpzBossThigh front = body.frontLeg;
        x = (front.getX() - 0xA) & 0xFFFF;
        y = front.getY();
        angle = front.angle;                                    // move.w obj.var_2a: the angle and the next byte
        phase = front.phase;
    }

    /** BossThighObject_0_Routine12 (objects/r1/boss.asm:2016-2042). */
    private void swingDown() {
        PpzBossFoot foot = calf.foot;
        if (foot.routine() == 6) {
            position();
            return;
        }
        if (foot.routine() != 8) {
            foot.clear(DONE);
            foot.setRoutine(6);
            position();
            return;
        }
        if (angle != 0x20) {
            angle = stepped(-angleStep());
            if ((byte) angle > 0) {
                position();
                return;
            }
        }
        angle = 0x20;
        foot.clear(DONE);
        routine = 0x14;
        position();
    }

    /** BossThighObject_0_Routine14 (objects/r1/boss.asm:2046-2058). */
    private void swingUp() {
        angle = stepped(angleStep());
        if ((byte) angle >= 0x30) {
            angle = 0x30;
            routine = 6;
            calf.foot.setRoutine(2);
        }
        position();
    }

    /** BossThighObject_0_Routine16 (objects/r1/boss.asm:2062-2086); {@code bra.w *+4} lands on the positioning. */
    private void reach() {
        if (!has(DONE)) {
            PpzBossFoot foot = calf.foot;
            if (foot.routine() == 4) {
                angle = stepped(angleStep());
                if ((byte) angle >= 0x50) set(DONE);
            } else if (foot.routine() != 2) {
                foot.setRoutine(2);
            }
        }
        position();
    }

    /**
     * loc_20D132 (objects/r1/boss.asm:2090-2142): hang from the body; or, planted and carrying, move the body and
     * Eggman by minus this frame's change of the hip offset.
     */
    private void position() {
        int sin = sine(angle) >> 4;
        int cos = cosine(angle) >> 4;
        if (has(PLANTED)) {
            if (has(CARRY)) {
                if (!testAndSet(STARTED)) {
                    lastCos = cos;
                    lastSin = sin;
                }
                int dx = cos - lastCos;
                int dy = sin - lastSin;
                lastCos = cos;
                lastSin = sin;
                body.addX(-dx);
                body.addY(-dy);
                body.eggman.addX(-dx);
                body.eggman.addY(-dy);
                return;
            }
        } else {
            clear(STARTED);
        }
        int px = body.getX() + 0xC + cos;
        if (has(BACK)) px -= 0xA;
        x = px & 0xFFFF;
        y = (body.getY() + 0x14 + sin) & 0xFFFF;
    }

    /** BossThighObject_0_Routine18 (objects/r1/boss.asm:2146-2190): the defeat throws it up and away. */
    private void fallApart() {
        body = null;                                            // links the fall no longer reads (rewind ids)
        calf = null;
        if (!testAndSet(STARTED)) {
            stepX = 0;
            stepY = -0x28000;
        } else {
            stepX += has(PLANTED) ? -0x600 : 0x600;            // bit 4 set: addi.l #-$600; clear: subi.l #-$600
            stepY += 0x1860;
        }
        fallAndCheck();
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossThigh(spawn);
    }
}
