package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Amenbo, the water-strider badnik, object $2C ({@code AmenboObject}, objects/r4/amenbo.asm:3-218): a collision-$31
 * badnik (tile $23B0, priority 3, $28 wide, $18 tall) that skates on the water line ({@code water_y - 8}). Its
 * speed {@code oVar2C} (16.16) ramps by {@code oVar30} ({@code -$600}; subtype 1 uses {@code -$200}) to a limit
 * of {@code -1.5} ({@code -0.75}) pixels a frame, then back to the other limit (routine 2), brakes by
 * {@code oVar34} ($100 or $C0) until it crosses zero (routine 4), waits $3C frames (6), turns round (8) and
 * waits $3C more before starting again; the animation and sprites differ by subtype. Subtype 0 also shoots: every
 * $78 frames, if the player is within $60 of it in X and Y and on the side it faces, it spawns a bullet
 * ({@link ObjAmenboBullet}, collision $B2, $20000 a frame) and plays {@code FM $A0} when on screen
 * (sub_20D8D8, objects/r4/amenbo.asm:164-201; sub_20D97E, :206-224). It ends in CheckObjectDespawn.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjAmenbo extends TtzBadnik {
    private int originX;
    private int shootTimer;     // oVar3C
    private int speed;          // oVar2C
    private int accel;          // oVar30
    private int brake;          // oVar34
    private int waitTimer;      // oVar2A
    private boolean second;
    private int xFixed;

    public ObjAmenbo(ObjectSpawn spawn) {
        super(spawn, "Amenbo");
    }

    @Override protected String sheetKind() { return second ? TtzObjectArt.AMENBO2 : TtzObjectArt.AMENBO1; }
    @Override protected int despawnX() { return originX; }

    @Override
    protected void run(PlayableEntity player) {
        if (routine == 0) init();
        switch (routine) {
            case 2 -> accelerate();
            case 4 -> brake();
            case 6 -> turn();
            case 8 -> pause();
            default -> { }
        }
    }

    /** AmenboObject_0_Routine0 (objects/r4/amenbo.asm:25-52). */
    private void init() {
        routine = 2;
        initialised = true;
        priority = 3;
        tileWord = TtzObjectArt.AMENBO_TILE;
        width = 0x14;
        yRadius = 0xC;
        colType = 0x31;
        shootTimer = 0x78;
        originX = x;
        xFixed = x << 16 | xSub;
        second = spawn.subtype() != 0;
        accel = second ? -0x200 : -0x600;
        brake = second ? 0xC0 : 0x100;
        animator = TtzObjectArt.animator(artProvider(), second ? "amenboAnimations2" : "amenboAnimations1", 0);
    }

    /** AmenboObject_0_Routine2 (objects/r4/amenbo.asm:53-84). */
    private void accelerate() {
        int d6 = speed + accel;
        int limit = second ? -0xC000 : -0x18000;
        if (d6 <= limit) {
            d6 = limit;
            routine += 2;
            accel = -accel;
        } else {
            limit = -limit;
            if (d6 >= limit) {
                d6 = limit;
                routine += 2;
                accel = -accel;
            }
        }
        speed = d6;
        finish();
    }

    /** AmenboObject_0_Routine4 (objects/r4/amenbo.asm:92-115). */
    private void brake() {
        int d6 = speed;
        boolean before = d6 < 0;
        d6 += brake;
        boolean after = d6 < 0;
        if (before != after) {
            routine += 2;
            brake = -brake;
            d6 = 0;
            waitTimer = 0x3C;
        }
        speed = d6;
        shootIfDue();
        move();
    }

    /** AmenboObject_0_Routine6 (objects/r4/amenbo.asm:121-139). */
    private void turn() {
        waitTimer = (waitTimer - 1) & 0xFFFF;
        if ((waitTimer & 0x8000) != 0) {
            routine += 2;
            flipHorizontally();
            waitTimer = 0x3C;
        }
        shootIfDue();
        move();
    }

    /** AmenboObject_0_Routine8 (objects/r4/amenbo.asm:145-154). */
    private void pause() {
        waitTimer = (waitTimer - 1) & 0xFFFF;
        if ((waitTimer & 0x8000) != 0) routine = 2;
        move();
    }

    private void finish() {
        shootIfDue();
        if (animator != null) animate();
        move();
    }

    /** loc_20D8C4 (objects/r4/amenbo.asm:155-163): x += speed (16.16), y = water_y - 8. */
    private void move() {
        xFixed += speed;
        x = xFixed >>> 16;
        xSub = xFixed & 0xFFFF;
        y = (waterY() - 8) & 0xFFFF;
    }

    /** sub_20D8D8 and sub_20D97E (objects/r4/amenbo.asm:164-224): subtype 0 only. */
    private void shootIfDue() {
        if (second) return;
        shootTimer = (shootTimer - 1) & 0xFFFF;
        if ((shootTimer & 0x8000) == 0) return;
        shootTimer = 0x78;
        var p = mainPlayer();
        if (p == null) return;
        int dy = (p.getCentreY() - y + 0x60) & 0xFFFF;
        if (dy >= 0xC0) return;
        int rawDx = (short) (p.getCentreX() - x);
        boolean playerLeft = rawDx < 0;
        int dx = (rawDx + 0x60) & 0xFFFF;
        if (dx >= 0xC0) return;
        boolean facingLeft = (spriteFlip & 1) == 0;               // btst #0,obj.sprite_flags(a0); seq d0
        if (facingLeft != playerLeft) return;                      // eor.b d1,d0; bne
        int speedOfBullet = (spriteFlip & 1) != 0 ? 0x20000 : -0x20000;
        ObjAmenboBullet bullet = new ObjAmenboBullet(x, y, speedOfBullet, spriteFlip, priority + 1, tileWord,
                second ? TtzObjectArt.AMENBO2 : TtzObjectArt.AMENBO1);
        if (!spawnChild(bullet)) return;
        if (renderFlagOnScreen()) services().playSfx(0xA0);
    }
}
