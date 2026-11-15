package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The boss's body, object $2B ({@code BossBodyObject}, objects/r1/boss.asm:701-1321): width $24, height $20,
 * tile $359. It sequences the two legs: Eggman sets its routine from the walk table and polls bit 0
 * ({@link #DONE}). Routine 2 descends 1.5 pixels a frame (with Eggman) until the front foot lands; 4 plants
 * the front leg and lifts the back; 6 waits for both feet; 8 walks left (stepping until X $B58 or the step
 * count), A walks right (until X $BA0), C runs the fast stride, E falls with the defeat and 10 waits.
 * {@link #frontLeg} and {@link #backLeg} are its two legs ({@code var_30}, {@code var_32}).
 */
final class PpzBossBody extends PpzBossPiece {
    PpzBossEggman eggman;
    PpzBossThigh frontLeg;
    PpzBossThigh backLeg;

    PpzBossBody(int x, int y, PpzBossEggman eggman) {
        super(0x2B, x, y, "PpzBossBody");
        this.eggman = eggman;
    }

    private PpzBossBody(ObjectSpawn spawn) {
        super(spawn, "PpzBossBody");
    }

    @Override String sheet() { return PpzBossArt.BODY; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> descend();
            case 4 -> plantFront();
            case 6 -> waitForFeet();
            case 8 -> walkLeft();
            case 0xA -> walkRight();
            case 0xC -> runStride();
            case 0xE -> fall();
            case 0x10 -> pause();
            default -> { }
        }
    }

    /** BossBodyObject_0_Routine0 (objects/r1/boss.asm:723-732); Eggman allocated every part before this first run. */
    private void init() {
        routine = 2;
        width = 0x24;
        yRadius = 0x20;
        tileWord = PpzBossArt.BODY_TILE;
        slowStride();
    }

    /** BossBodyObject_0_Routine10 (objects/r1/boss.asm:736-744). */
    private void pause() {
        steps = (steps - 1) & 0xFF;
        if (steps != 0) return;
        slowStride();
        set(DONE);
        clear(STARTED);
    }

    /** BossBodyObject_0_Routine2 (objects/r1/boss.asm:748-768): the back leg copies the front during the descent. */
    private void descend() {
        backLeg.setRoutine(0x10);
        backLeg.calf.setRoutine(4);
        backLeg.calf.foot.setRoutine(0xA);
        frontLeg.setChain(CARRY);
        if (frontLeg.has(PLANTED)) {
            set(DONE);
            return;
        }
        addYLong(0x18000);
        eggman.addYLong(0x18000);
    }

    /** BossBodyObject_0_Routine4 (objects/r1/boss.asm:772-811). */
    private void plantFront() {
        backLeg.clear(DONE);
        PpzBossThigh front = frontLeg;
        if (!front.clear(DONE)) return;
        if (front.routine() == 0xC) {
            front.setRoutine(0xA);
            return;
        }
        if (front.routine() == 0xA) {
            front.setRoutine(0xE);
            return;
        }
        front.setRoutine(2);
        front.clearChain(CARRY);
        front.clearChain(PLANTED);
        backLeg.setRoutine(8);
        backLeg.setChain(CARRY);
        backLeg.setChain(PLANTED);
        backLeg.calf.setRoutine(2);
        backLeg.calf.foot.setRoutine(4);
        set(DONE);
    }

    /** BossBodyObject_0_Routine6 (objects/r1/boss.asm:815-832). */
    private void waitForFeet() {
        if (!testAndSet(STARTED)) armsSwingB();
        if (!frontLeg.has(DONE) || !backLeg.has(DONE)) return;
        clear(STARTED);
        set(SWAP);
        set(DONE);
    }

    /** The leading and trailing legs by {@link #SWAP}: bit 6 clear leads with the front leg. */
    private PpzBossThigh lead() { return has(SWAP) ? backLeg : frontLeg; }

    private PpzBossThigh trail() { return has(SWAP) ? frontLeg : backLeg; }

    /**
     * Both legs done: clear the done bits of the lead thigh and its foot and of the trailing thigh and its
     * calf ({@code movea.w obj.var_30(a2),a4} is read twice, so the trailing leg's calf, not its foot,
     * {@code FixBugs = 0}).
     */
    private boolean bothLegsDone() {
        PpzBossThigh a1 = lead(), a2 = trail();
        if (!a1.has(DONE) || !a2.has(DONE)) return false;
        a1.clear(DONE);
        a1.calf.foot.clear(DONE);
        a2.clear(DONE);
        a2.calf.clear(DONE);
        return true;
    }

    /** BossBodyObject_0_Routine8 (objects/r1/boss.asm:836-911). */
    private void walkLeft() {
        if (!bothLegsDone()) return;
        if (xWord() <= 0xB58) steps = 1;
        steps = (steps - 1) & 0xFF;
        if (steps == 0) {
            set(DONE);
            clear(STARTED);
            return;
        }
        if (toggle(SWAP)) {
            stepLegs(frontLeg, 8, 6, backLeg, 2, 8);
            armsSwingA();
        } else {
            stepLegs(backLeg, 8, 6, frontLeg, 2, 8);
            armsSwingB();
        }
    }

    /**
     * One step: {@code plant} lifts ({@code routine}, its chain carries and is planted, its calf gets
     * {@code plantCalf}); {@code swing} swings ({@code swingRoutine}, chain released, calf {@code swingCalf}).
     */
    private static void stepLegs(PpzBossThigh plant, int plantRoutine, int plantCalf,
                                 PpzBossThigh swing, int swingRoutine, int swingCalf) {
        plant.setRoutine(plantRoutine);
        plant.setChain(CARRY);
        plant.setChain(PLANTED);
        plant.calf.setRoutine(plantCalf);
        swing.setRoutine(swingRoutine);
        swing.clearChain(CARRY);
        swing.clearChain(PLANTED);
        swing.calf.setRoutine(swingCalf);
    }

    /** BossBodyObject_0_RoutineA (objects/r1/boss.asm:1076-1159). */
    private void walkRight() {
        PpzBossThigh a1 = lead(), a2 = trail();
        if (!a1.has(DONE) || !a2.has(DONE)) return;
        if (!testAndSet(STARTED)) {
            slowStride();
        } else {
            bothLegsDone();
            if (xWord() >= 0xBA0) steps = 1;
            steps = (steps - 1) & 0xFF;
            if (steps == 0) {
                clear(STARTED);
                set(DONE);
                return;
            }
        }
        if (toggle(SWAP)) {
            stepLegs(frontLeg, 0x16, 8, backLeg, 0x12, 6);
            armsSwingB();
        } else {
            stepLegs(backLeg, 0x16, 8, frontLeg, 0x12, 6);
            armsSwingA();
        }
    }

    /** BossBodyObject_0_RoutineC (objects/r1/boss.asm:1163-1252): the fast stride. */
    private void runStride() {
        PpzBossThigh a1 = lead(), a2 = trail();
        if (!testAndSet(STARTED)) {
            // sub_20C500 restores a1 before its arm walk, then leaves a1 on the front arm's bumper (or 0 once that
            // arm has dropped) and a2 on the shoulder (objects/r1/boss.asm:1008-1029). The routine goes on with
            // those registers: neither has routine 4 or bit 0 set (a bumper never sets it; address 0 reads the BIOS
            // vector table's zero high bytes), so the first pass ends at locret_20C8A4 (FixBugs = 0). The legs move
            // a frame later, as native shows (R13C-death row 2026).
            fastStride();
            return;
        }
        if (a1.routine() == 4) {
            a1.calf.setRoutine(0xA);
            a2.calf.setRoutine(0xC);
        }
        if (!bothLegsDone()) return;
        steps = (steps - 1) & 0xFF;
        if (steps == 0) {
            set(DONE);
            clear(STARTED);
            return;
        }
        if (toggle(SWAP)) {
            frontLeg.clear(STARTED);
            frontLeg.setChain(CARRY);
            frontLeg.setChain(PLANTED);
            frontLeg.calf.setRoutine(0xA);
            frontLeg.calf.foot.setRoutine(2);
            backLeg.clear(STARTED);
            backLeg.setRoutine(2);
            backLeg.clearChain(CARRY);
            backLeg.clearChain(PLANTED);
            backLeg.calf.setRoutine(0xC);
        } else {
            frontLeg.clear(STARTED);
            frontLeg.setRoutine(2);
            frontLeg.clearChain(CARRY);
            frontLeg.clearChain(PLANTED);
            frontLeg.calf.setRoutine(0xC);
            backLeg.clear(STARTED);
            backLeg.setChain(CARRY);
            backLeg.setChain(PLANTED);
            backLeg.calf.setRoutine(0xA);
            backLeg.calf.foot.setRoutine(2);
        }
        armsReach();
    }

    /** BossBodyObject_0_RoutineE (objects/r1/boss.asm:1256-1273): falls 2 pixels a frame (with Eggman) to the floor. */
    private void fall() {
        frontLeg = null;                                      // links the fall no longer reads (rewind ids)
        backLeg = null;
        if (has(DONE)) {
            deleteObject();
            return;
        }
        if (floorDistance() <= 0) return;
        addY(2);
        eggman.addY(2);
    }

    // ---- arms (sub_20C42E, sub_20C474, sub_20C4BA) -----------------------------------------------------

    /** sub_20C42E (objects/r1/boss.asm:915-936): back arm up, front arm down. */
    private void armsSwingA() {
        swing(eggman.shoulders.backArm, 2, 8, 0);
        swing(eggman.shoulders.frontArm, 6, 6, 0);
    }

    /** sub_20C474 (objects/r1/boss.asm:940-961). */
    private void armsSwingB() {
        swing(eggman.shoulders.backArm, 6, 6, 0);
        swing(eggman.shoulders.frontArm, 2, 8, 0);
    }

    /** sub_20C4BA (objects/r1/boss.asm:965-986): both arms reach (forearm routine $A, frame 1). */
    private void armsReach() {
        swing(eggman.shoulders.backArm, 2, 0xA, 1);
        swing(eggman.shoulders.frontArm, 2, 0xA, 1);
    }

    /** An arm the shoulders still holds ({@code move.l a1,d0; beq}) gets the routine; its forearm too, with the frame. */
    private static void swing(PpzBossArm arm, int armRoutine, int forearmRoutine, int frame) {
        if (arm == null) return;
        arm.setRoutine(armRoutine);
        arm.forearm.setRoutine(forearmRoutine);
        arm.forearm.setMapFrame(frame);
    }

    /** sub_20C500 (objects/r1/boss.asm:990-1029): the fast stride's leg steps and fast arms. */
    private void fastStride() {
        for (PpzBossThigh thigh : new PpzBossThigh[] {frontLeg, backLeg}) {
            thigh.setAngleStep(8);
            thigh.calf.stepX = 0x10000;
            thigh.calf.stepY = 0x8000;
            thigh.calf.foot.stepX = 0xC000;
            thigh.calf.foot.stepY = 0x18000;
        }
        armSpeed(eggman.shoulders.backArm, true);
        armSpeed(eggman.shoulders.frontArm, true);
    }

    /** sub_20C5AE (objects/r1/boss.asm:1033-1072): the slow stride. */
    private void slowStride() {
        for (PpzBossThigh thigh : new PpzBossThigh[] {frontLeg, backLeg}) {
            thigh.setAngleStep(2);
            thigh.calf.stepX = 0x8000;
            thigh.calf.stepY = 0x4000;
            thigh.calf.foot.stepX = 0x4000;
            thigh.calf.foot.stepY = 0x8000;
        }
        armSpeed(eggman.shoulders.backArm, false);
        armSpeed(eggman.shoulders.frontArm, false);
    }

    private static void armSpeed(PpzBossArm arm, boolean fast) {
        if (arm == null) return;
        if (fast) {
            arm.forearm.set(FAST);
            arm.forearm.bumper.set(FAST);
        } else {
            arm.forearm.clear(FAST);
            arm.forearm.bumper.clear(FAST);
        }
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossBody(spawn);
    }
}
