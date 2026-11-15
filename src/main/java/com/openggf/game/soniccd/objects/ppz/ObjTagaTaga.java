package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Taga-Taga, object $40 ({@code ObjTagaTaga}, Palmtree Panic/Objects/Taga-Taga/Main.asm:7-109): a
 * fish that waits under its start point, jumps with an initial velocity of -$3C000 (-$30000 damaged)
 * and gravity $1000 (16.16 per frame), and falls back. It is harmless (collision 0) while waiting
 * and $2E in the air. Its position stays fixed in X.
 *
 * <p>Routines (Main.asm:19-23): 0 init, 2 wait (collision off, animation 0, Y back to the start),
 * 4 animate until the script's {@code $FC}, 6 jump (animation 1, collision $2E), 8 flight; landing
 * sets routine 2 again. Init, wait and jump have no {@code rts}: init runs the wait and animate code in
 * the same frame (Main.asm:58-74), wait runs animate, jump runs the flight (Main.asm:84-92). The jump and landing sounds {@code FM_A2} are not hosted.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjTagaTaga extends PpzBadnik {
    private int originX;
    private int baseY;
    /** oVar30: jump velocity, oVar34: current vertical velocity, oVar38: gravity (all 16.16). */
    private int jumpVelocity;
    private int velocity;
    private int gravity;
    private boolean damaged;

    public ObjTagaTaga(ObjectSpawn spawn) {
        super(spawn, "Taga-Taga");
    }

    @Override protected int tileIdIndex() { return PpzObjectArt.TILE_ID_TAGA_TAGA; }

    @Override protected String sheetKind() {
        return damaged ? PpzObjectArt.PPZ_TAGA_TAGA2 : PpzObjectArt.PPZ_TAGA_TAGA1;
    }

    @Override protected int despawnX() { return originX; }

    @Override
    protected void run(PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); waitUnderwater(); animate(); }   // each falls through into the next (no rts)
            case 2 -> { waitUnderwater(); animate(); }
            case 4 -> animate();                        // ObjTagaTaga_Animate (Taga-Taga/Main.asm:71-74)
            case 6 -> jump();
            case 8 -> fly();
            default -> { }
        }
    }

    /** ObTagaTaga_Init (Taga-Taga/Main.asm:26-58). */
    private void init() {
        routine += 2;
        priority = 3;
        xRadius = 0x10;
        width = 0x10;
        yRadius = 0x16;
        originX = x;
        baseY = y;
        damaged = spawn.subtype() != 0;
        jumpVelocity = damaged ? -0x30000 : -0x3C000;
        gravity = 0x1000;
        animator = PpzObjectArt.animator(artProvider(), e -> damaged ? e.tagaTagaAnimations2() : e.tagaTagaAnimations1(), 0);
        initialised = true;
    }

    /** ObjTagaTaga_Init2 (Taga-Taga/Main.asm:62-67): {@code move.w #$FF,oAnim} sets animation 0 and forces a restart. */
    private void waitUnderwater() {
        routine += 2;
        animator.anim = 0;
        animator.restart();
        colType = 0;
        setY((baseY & 0xFFFF) << 16);                   // move.l oVar2C(a0),oY(a0); oVar2E is 0
    }

    /** ObjTagaTaga_Jump (Taga-Taga/Main.asm:78-88), then falls into ObjTagaTaga_Main. */
    private void jump() {
        routine += 2;
        animator.anim = 1;                              // move.w #$1FF,oAnim(a0)
        animator.restart();
        colType = 0x2E;
        setY((baseY & 0xFFFF) << 16);
        velocity = jumpVelocity;
        // tst.b oSprFlags; bpl; FM_A2 (Main.asm:84-87): not hosted.
        fly();
    }

    /** ObjTagaTaga_Main (Taga-Taga/Main.asm:92-109). */
    private void fly() {
        addToY(velocity);
        velocity += gravity;
        if ((short) y > (short) baseY) {                // move.w oY,d0; cmp.w oVar2C,d0; ble.s .DoAnim
            routine = 2;
            // tst.b oSprFlags; bpl; FM_A2 (Main.asm:101-104): not hosted.
        }
        animate();
    }
}
