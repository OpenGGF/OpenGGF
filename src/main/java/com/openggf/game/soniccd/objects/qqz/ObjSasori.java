package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Sasori, object $21 ({@code SasoriObject}, objects/r5/sasori.asm:3-8; main body loc_20D212, :11-22): a
 * scorpion badnik with a gun child ({@link ObjSasoriGun}, secondary subtype 1) and, for subtype 0, a
 * missile ({@link ObjSasoriMissile}). Collision {@code $31} (enemy). {@code DestroyInGoodFuture} runs
 * first, AnimateObject every frame, CheckObjectDespawn2 at the start X (:18-21).
 *
 * <p>Routines (off_20D240, :25-37): 0 init (returns); 2 drop to the floor and make the gun; 4 patrol
 * {@code $50} pixels either side of the start at 1 (subtype 0) or 0.5 pixel a frame, turning at the limit
 * or at an edge, and, when the player is within {@code [-$28, $28)} vertically and {@code [-$50, $50)}
 * horizontally, stopping to shoot (turning first if the player is behind); 6 to 14 the firing sequence:
 * 30 frames, gun up 5 and back 5, 10 frames, the missile (subtype 0 only), 30 frames, gun down, 30 frames,
 * then back to 4. The stalled gun moves with the body each frame. Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSasori extends QqzBadnik {
    private int originX;           // oVar2A
    private int velocity;          // oVar2C, 16.16
    private ObjSasoriGun gun;      // oVar30
    private int gunOffset;         // oVar32
    private int timer;             // oVar34

    public ObjSasori(ObjectSpawn spawn) {
        super(spawn, "Sasori");
    }

    @Override protected int despawnX() { return originX; }

    @Override
    protected String sheetKind() {
        return spawn.subtype() == 0 ? QqzObjectArt.SASORI1 : QqzObjectArt.SASORI2;
    }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        if (destroyInGoodFuture()) return;
        switch (routine) {
            case 0 -> init();
            case 2 -> drop();
            case 4 -> patrol(player);
            case 6 -> aim();
            case 8 -> recoilUp();
            case 0xA -> { routine += 2; timer = 0xA; }
            case 0xC -> fire();
            case 0xE -> { routine += 2; timer = 0x1E; }
            case 0x10 -> recoilDown();
            case 0x12 -> { routine += 2; timer = 0x1E; }
            case 0x14 -> { if (--timer == 0) routine = 4; }
            default -> { }
        }
        animColumn = "sasoriAnimations";
        animate();
    }

    /** SasoriObject_0_Routine0 (objects/r5/sasori.asm:40-76). */
    private void init() {
        routine += 2;
        priority = 3;
        xRadius = 0x18;
        width = 0x18;
        yRadius = 0xC;
        colType = 0x31;
        originX = x;
        if (spawn.subtype() == 0) {
            velocity = -0x10000;
            gunOffset = 3;
        } else {
            velocity = -0x8000;
            gunOffset = 7;
        }
        tileWord = QqzObjectArt.SASORI_TILE;
        initialised = true;
    }

    /** SasoriObject_0_Routine2 (objects/r5/sasori.asm:80-100). */
    private void drop() {
        y = (y + 1) & 0xFFFF;
        int d1 = floorDistance();
        if (d1 >= 0) return;
        y = (y + d1) & 0xFFFF;
        routine += 2;
        var objects = services().objectManager();
        ObjSasoriGun child = new ObjSasoriGun(this);
        if (objects != null) objects.addDynamicObjectAfterCurrent(child);
        if (objects == null || child.isDestroyed()) {
            deleteObject();                                  // bne.w loc_20D4FC
            drawThisFrame = false;
            return;
        }
        gun = child;
    }

    /** SasoriObject_0_Routine4 (objects/r5/sasori.asm:104-173). */
    private void patrol(PlayableEntity player) {
        addToX(velocity);
        int offset = Math.abs((short) (x - originX));
        if (offset >= 0x50) {                                // cmpi.w #$50,d0; bge.s loc_20D396
            turn();
            return;
        }
        int d1 = floorDistance();
        if (((d1 + 7) & 0xFFFF) >= 0xE) {                    // addq.w #7; subi.w #$E; bcc.s loc_20D396
            turn();
            return;
        }
        y = (y + d1) & 0xFFFF;
        gun.place(x + gunOffset, y - 0x10);
        if (player == null) return;
        // loc_20D3C4 (objects/r5/sasori.asm:160-173): carry set when the player is inside the shooting window.
        if (!inWindow(centreY(player) - y, 0x28, 0x50)) return;
        int dx = (centreX(player) - x) & 0xFFFF;
        if (!inWindow(dx, 0x50, 0xA0)) return;
        routine += 2;                                        // loc_20D382
        boolean playerLeft = (short) dx < 0;                 // tst.w d0; smi d0
        boolean movingLeft = velocity < 0;                   // tst.b obj.var_2c(a0); smi d1
        if (playerLeft != movingLeft) turn();                // eor.b d1,d0; bne.s loc_20D396
    }

    /** loc_20D396 (objects/r5/sasori.asm:146-157): undo this frame's step and walk the other way. */
    private void turn() {
        setXLong(xLong() - velocity);
        velocity = -velocity;
        gunOffset = -gunOffset;
        flipHorizontally();
        gun.flip();
    }

    /** SasoriObject_0_Routine6 (objects/r5/sasori.asm:177-185): reset the gun on the body. */
    private void aim() {
        routine += 2;
        timer = 0x1E;
        gun.place(x + gunOffset, y - 0x10);
    }

    /** SasoriObject_0_Routine8 (objects/r5/sasori.asm:187-202): the gun jerks up and back. */
    private void recoilUp() {
        if (--timer != 0) return;
        routine += 2;
        gun.nudge(velocity < 0 ? 5 : -5, -5);
    }

    /** SasoriObject_0_RoutineC (objects/r5/sasori.asm:210-242): fire the missile (subtype 0). */
    private void fire() {
        if (--timer != 0) return;
        routine += 2;
        if (spawn.subtype() != 0) return;
        var objects = services().objectManager();
        if (objects == null) return;
        int dx = velocity < 0 ? -4 : 4;
        int xv = velocity < 0 ? -0x30000 : 0x30000;
        ObjSasoriMissile missile = new ObjSasoriMissile(this, (x + dx) & 0xFFFF, (y - 0x18) & 0xFFFF, xv);
        objects.addDynamicObject(missile);                   // jsr SpawnObject
        if (missile.isDestroyed()) return;
        if (onScreen()) services().playSfx(0xA0);            // tst.b obj.sprite_flags(a0); bpl.s; move.w #$A0,d0
    }

    /** SasoriObject_0_Routine10 (objects/r5/sasori.asm:250-265): the gun comes back down. */
    private void recoilDown() {
        if (--timer != 0) return;
        routine += 2;
        gun.nudge(velocity < 0 ? -5 : 5, 5);
    }
}
