package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * Buzzsaw, object $27 in the R81x and R83x indexes ({@code BuzzsawObject}, objects/r8/buzzsaw.asm:3-173): a $50 hurting saw
 * (collision $A9) that slides along a diagonal rail (the subtype's bits 0 and 1 reverse X and Y) for nine steps of
 * nine frames, waits, returns, waits and repeats, humming every $20 on-screen frames. A negative subtype starts at the
 * far end of the rail. Its handle ({@link Handle}) is a second object that stays where the saw started.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzBuzzsaw extends MmzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private final int initialX;                  // oVar36
    private boolean initialised;
    private int routine;
    private int timer;                           // oVar2A
    private int steps;                           // oVar34
    private int humCounter;                      // oVar38
    private int xSpeed;                          // oVar2C
    private int ySpeed;                          // oVar30
    private int anim;
    private int statusFlip;
    private int spriteFlip;
    private int mapFrame;
    private com.openggf.game.soniccd.objects.common.CdAnimator animator;
    private Handle handle;

    public MmzBuzzsaw(ObjectSpawn spawn) {
        super(spawn, "Buzzsaw");
        this.initialX = spawn.x();
        this.statusFlip = spawn.renderFlags() & 3;
        this.spriteFlip = this.statusFlip;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); routine2(); }
            case 2 -> routine2();
            case 4 -> routine4();
            case 6 -> routine6();
            case 8 -> routine8();
            default -> { }
        }
        if (animator == null) animator = mmzAnimator("buzzsawAnimations", anim);
        if (animator != null) {
            animator.anim = anim;
            animator.step();
            mapFrame = animator.mapFrame();
            spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
        }
    }

    /** BuzzsawObject_0_Routine0 (objects/r8/buzzsaw.asm:27-89). */
    private void init() {
        initialised = true;
        priority = 3;
        tileWord = MmzObjectArt.BUZZSAW_TILE;
        width = 0x50;
        yRadius = 0x50;
        xSpeed = 0x10000;
        ySpeed = 0x10000;
        if ((spawn.subtype() & 1) != 0) {
            xSpeed = -xSpeed;
            statusFlip |= 1;
            spriteFlip |= 1;
        }
        if ((spawn.subtype() & 2) != 0) {
            ySpeed = -ySpeed;
            statusFlip |= 2;
            spriteFlip |= 2;
        }
        timer = 0x3C;
        routine = 8;
        boolean far = (spawn.subtype() & 0x80) != 0;
        if (far) {
            anim = 9;
            routine = 4;
        }
        var objects = services().objectManager();
        if (objects == null) return;
        int dx = 0;
        int dy = 0;
        if (far) {                                   // tst.b oSubtype(a0); bpl (loc_20CAA8, :59-89)
            dx = (spawn.subtype() & 1) != 0 ? -0x50 : 0x50;
            dy = (spawn.subtype() & 2) != 0 ? -0x50 : 0x50;
        }
        handle = new Handle(new ObjectSpawn(x - dx, y - dy, spawn.objectId(), spawn.subtype(), 0, false, 0), this);
        handle.flips = spriteFlip & 3;
        objects.addDynamicObject(handle);                              // jsr SpawnObject
    }

    /** BuzzsawObject_0_Routine2 (objects/r8/buzzsaw.asm:90-108): out along the rail. */
    private void routine2() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            anim++;
            timer = 9;
            steps = (steps - 1) & 0xFFFF;
            if (steps == 0) {
                routine += 2;
                timer = 0x3C;
            }
        }
        addToX(xSpeed);
        addToY(ySpeed);
        hum();
    }

    /** BuzzsawObject_0_Routine4 (objects/r8/buzzsaw.asm:109-120): wait at the far end. */
    private void routine4() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            routine += 2;
            steps = 9;
            timer = 1;
        }
        hum();
    }

    /** BuzzsawObject_0_Routine6 (objects/r8/buzzsaw.asm:121-139): back along the rail. */
    private void routine6() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            anim--;
            timer = 9;
            steps = (steps - 1) & 0xFFFF;
            if (steps == 0) {
                routine += 2;
                timer = 0x3C;
            }
        }
        addToX(-xSpeed);
        addToY(-ySpeed);
        hum();
    }

    /** BuzzsawObject_0_Routine8 (objects/r8/buzzsaw.asm:140-146): wait at the near end. */
    private void routine8() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            routine -= 6;
            steps = 9;
            timer = 1;
        }
        hum();
    }

    /** loc_20CBBC (objects/r8/buzzsaw.asm:147-161): FM $B1 every $20 on-screen frames. */
    private void hum() {
        if (!renderFlagOnScreen()) return;
        humCounter = (humCounter + 1) & 0xFFFF;
        if ((humCounter & 0x1F) == 0) services().playSfx(0xB1);
    }

    /** move.b #$A9,oColType(a0) (BuzzsawObject_0_Routine0, objects/r8/buzzsaw.asm:27-43). */
    @Override public int getCollisionFlags() { return routine == 0 ? 0 : 0xA9; }
    @Override public int getCollisionProperty() { return 0; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.BUZZSAW, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    /**
     * The saw's handle ({@code BuzzsawObject} with {@code oSubtype2 = $FF}, objects/r8/buzzsaw.asm:162-183): drawn one priority
     * behind the saw at the point the rail starts, deleted when the saw is gone.
     */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    static final class Handle extends MmzObject implements ObjectVisualFacing.NonDirectional {
        private final MmzBuzzsaw parent;
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$27,obj.id(a1)}). */
    private int parentSlot = -1;
        private boolean initialised;
        int flips;

        Handle(ObjectSpawn spawn, MmzBuzzsaw parent) {
            super(spawn, "BuzzsawHandle");
            this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
            this.priority = 4;                       // oPriority + 1 of the parent's 3 (loc_20CAA8, :59-80)
            this.tileWord = MmzObjectArt.BUZZSAW_TILE;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!slotHoldsId(parentSlot, 0x27)) {              // cmpi.b #$27,oID(a1); bne DeleteObject
                setDestroyed(true);
                return;
            }
            if (!initialised) {                      // loc_20CBDC (:162-178)
                initialised = true;
                width = 0x20;
                yRadius = 0x20;
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (!initialised) return;
            drawSheet(MmzObjectArt.BUZZSAW_HANDLE, 0, (flips & 1) != 0, (flips & 2) != 0);
        }
    }
}
