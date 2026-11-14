package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * Propeller, object $28 in the R81x index ({@code PropellerObject}, objects/r8/propeller.asm:3-254): a base that drops to the
 * floor and crawls $80 frames each way along it, carrying two blades above and below ({@link Blade}). Each blade idles,
 * winds up, spins for $49 frames as a hurting object (collision $A8) and stops; the two run half a cycle apart.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzPropeller extends MmzObject implements ObjectVisualFacing.NonDirectional {
    private final int initialX;                  // oVar30
    private int routine;
    private int timer;                           // oVar2A
    private int speed = 0x10000;                 // oVar2C
    private boolean initialised;
    private CdAnimator animator;
    private int mapFrame;

    public MmzPropeller(ObjectSpawn spawn) {
        super(spawn, "Propeller");
        this.initialX = spawn.x();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> settle();
            case 4 -> crawl();
            default -> { }
        }
        if (animator == null) animator = mmzAnimator("propellerBaseAnimations", 0);
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    /** PropellerObject_0_Routine0 (objects/r8/propeller.asm:30-85). */
    private void init() {
        routine = 2;
        initialised = true;
        priority = 4;
        tileWord = MmzObjectArt.PROPELLER_TILE;
        timer = 0x80;
        speed = 0x10000;
        width = 8;
        yRadius = 0x2E;
        var objects = services().objectManager();
        if (objects == null) return;
        Blade lower = new Blade(new ObjectSpawn(x, y, spawn.objectId(), 1, 0, false, 0), this, 0x14, true);
        Blade upper = new Blade(new ObjectSpawn(x, y, spawn.objectId(), 0xFF, 0, false, 0), this, -0x2C, false);
        objects.addDynamicObjectAfterCurrent(lower);                   // jsr SpawnObjectAfter (subtype 1, flipped)
        objects.addDynamicObjectAfterCurrent(upper);                   // jsr SpawnObjectAfter (subtype $FF)
    }

    /** PropellerObject_0_Routine2 (objects/r8/propeller.asm:86-93): one pixel down until the floor. */
    private void settle() {
        y = (y + 1) & 0xFFFF;
        int d1 = floorDistance();
        if (d1 < 0) {
            y = (y + d1) & 0xFFFF;
            routine += 2;
        }
    }

    /** PropellerObject_0_Routine4 (objects/r8/propeller.asm:99-109). */
    private void crawl() {
        addToX(speed);
        y = (y + floorDistance()) & 0xFFFF;
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            speed = -speed;
            timer = 0x80;
        }
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.PROPELLER_BASE, mapFrame, false, false);
    }

    /** A blade ({@code PropellerObject} with a nonzero subtype, objects/r8/propeller.asm:124-246). */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    static final class Blade extends MmzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
        private final MmzPropeller base;
        /** oVar3E: the base's slot, which the blade tests each turn (objects/r8/propeller.asm:125-126). */
        private final int baseSlot;
        private final int offsetY;               // oVar3C
        private final boolean flipped;
        private int routine;
        private int timer;                       // oVar2A
        private int collision;
        private int mapFrame;
        private int anim;
        private CdAnimator animator;
        private boolean initialised;

        Blade(ObjectSpawn spawn, MmzPropeller base, int offsetY, boolean flipped) {
            super(spawn, "PropellerBlade");
            this.base = base;
            this.baseSlot = base.getSlotIndex();
            this.offsetY = offsetY;
            this.flipped = flipped;
            this.priority = 3;
            this.tileWord = MmzObjectArt.PROPELLER_TILE;
            this.width = 0x20;
            this.yRadius = 0x1C;
            this.initialised = true;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!slotHoldsId(baseSlot, 0x28)) {             // cmpi.b #$28,oID(a1); beq / jmp DeleteObject
                setDestroyed(true);
                return;
            }
            run();
            x = base.getX();                                 // loc_20CE22 tail: follow the base
            y = (base.getY() + offsetY) & 0xFFFF;
        }

        /**
         * PropellerObject_1_Routine0..Routine10 (objects/r8/propeller.asm:150-246): every routine that sets up a wait ends by
         * falling into the wait routine that follows it in the same call ({@code Routine2} into {@code Routine4},
         * {@code Routine6} into {@code Routine8}, {@code RoutineA} into {@code RoutineC}, {@code RoutineE} into
         * {@code Routine10}), and a wait that runs out branches or falls into the next setup in the same call.
         */
        private void run() {
            switch (routine) {
                case 0 -> {
                    if ((spawn.subtype() & 0x80) != 0) { routine = 0xA; routineA(); }
                    else { routine = 2; routine2(); }                       // bra.w *+4
                }
                case 2 -> routine2();
                case 4 -> routine4();
                case 6 -> routine6();
                case 8 -> routine8();
                case 0xA -> routineA();
                case 0xC -> routineC();
                case 0xE -> routineE();
                case 0x10 -> routine10();
                default -> { }
            }
        }

        private void routine2() { mapFrame = 6; timer = 5; collision = 0; routine = 4; routine4(); }
        private void routine4() { timer = (timer - 1) & 0xFFFF; if (timer == 0) { routine += 2; routine6(); } }
        private void routine6() { mapFrame = 7; timer = 0x49; routine = 8; routine8(); }
        private void routine8() { timer = (timer - 1) & 0xFFFF; if (timer == 0) { routine += 2; routineA(); } }
        private void routineA() { mapFrame = 6; timer = 5; routine = 0xC; routineC(); }
        private void routineC() { timer = (timer - 1) & 0xFFFF; if (timer == 0) { routine += 2; routineE(); } }
        private void routineE() {
            anim = 0;
            if (animator != null) animator.restart();
            timer = 0x49;
            collision = 0xA8;
            routine = 0x10;
            routine10();
        }
        private void routine10() {
            timer = (timer - 1) & 0xFFFF;
            if (timer == 0) { routine = (routine - 0xE) & 0xFF; routine2(); }   // addi.b #-$E,oRoutine; bra.w Routine2
            else animate();
        }

        private void animate() {
            if (animator == null) animator = mmzAnimator("propellerBladeAnimations", anim);
            if (animator == null) return;
            animator.step();
            mapFrame = animator.mapFrame();
        }

        @Override public int getCollisionFlags() { return collision; }
        @Override public int getCollisionProperty() { return 0; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(MmzObjectArt.PROPELLER_BLADE, mapFrame, false, flipped);
        }
    }
}
