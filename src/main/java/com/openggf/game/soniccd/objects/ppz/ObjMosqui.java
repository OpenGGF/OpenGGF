package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;

import java.util.function.ToIntFunction;

/**
 * Mosqui, object $3D ({@code ObjMosqui}, Palmtree Panic/Objects/Mosqui/Main.asm:7-149). It patrols
 * $80 pixels either side of its start, then dives straight down when the player is within a window
 * below it and waits on the floor for the screen to leave it.
 *
 * <p>Routines (Mosqui/Main.asm:19-24): 0 init, 2 patrol, 4 animate until the dive animation's
 * {@code $FC}, 6 dive, 8 wait. Collision $2B (enemy, size $2B) stays on throughout.
 * {@code debugMode} (Main.asm:60-61) is not modelled; the dive sound {@code FM_A7}
 * (Main.asm:131-134) is not hosted. Shipped {@code FixBugs = 0}: the routines are modelled as assembled.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjMosqui extends PpzBadnik {
    private int originX;
    /** oVar2C: the patrol velocity as a 16.16 value (Mosqui/Main.asm:41,49,54). */
    private int velocity;
    private boolean damaged;

    public ObjMosqui(ObjectSpawn spawn) {
        super(spawn, "Mosqui");
    }

    @Override protected int tileIdIndex() { return PpzObjectArt.TILE_ID_MOSQUI; }

    @Override protected String sheetKind() {
        return damaged ? PpzObjectArt.PPZ_MOSQUI2 : PpzObjectArt.PPZ_MOSQUI1;
    }

    @Override protected int despawnX() { return originX; }

    @Override
    protected void run(PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); patrol(player); }       // ObjMosqui_Init falls through into _Main
            case 2 -> patrol(player);
            case 4 -> animate();                        // ObjMosqui_Animate (Mosqui/Main.asm:116-119)
            case 6 -> dive();
            case 8 -> waitOffscreen();
            default -> { }
        }
    }

    /** ObjMosqui_Init (Mosqui/Main.asm:26-55). */
    private void init() {
        routine += 2;
        priority = 3;
        xRadius = 0x10;
        width = 0x10;
        yRadius = 0x10;
        colType = 0x2B;
        originX = x;
        damaged = spawn.subtype() != 0;
        velocity = damaged ? -0x8000 : -0x10000;
        ToIntFunction<PpzObjectCatalogue.Entry> table = damaged ? e -> e.mosquiAnimations2() : e -> e.mosquiAnimations1();
        animator = PpzObjectArt.animator(artProvider(), table, 0);
        initialised = true;
    }

    /** ObjMosqui_Main (Mosqui/Main.asm:59-94). */
    private void patrol(PlayableEntity player) {
        if (player != null && inDiveRange(player)) {
            routine += 2;                               // .StartDive
            animator.anim = 1;
            return;
        }
        while (true) {
            addToX(velocity);
            int distance = Math.abs((short) (x - originX));
            if (distance < 0x80) break;                 // cmpi.w #$80,d0; blt.s .Animate
            velocity = -velocity;                       // .ChkTurn: neg.l oVar2C(a0); bchg flips
            flipHorizontally();
        }
        animate();
    }

    /**
     * ObjMosqui_CheckInRange (Mosqui/Main.asm:98-112): carry set when the player is in {@code [y-$30, y+$40)}
     * and {@code [x-$30, x+$30)} (the two {@code subi.w} borrows).
     */
    private boolean inDiveRange(PlayableEntity player) {
        int dy = (player.getCentreY() - y + 0x30) & 0xFFFF;
        if (dy >= 0x70) return false;                   // subi.w #$70,d0; bcc.s .End
        int dx = (player.getCentreX() - x + 0x30) & 0xFFFF;
        return dx < 0x60;                               // subi.w #$60,d1; the carry
    }

    /** ObjMosqui_Dive (Mosqui/Main.asm:123-138). */
    private void dive() {
        y = (y + 6) & 0xFFFF;                           // addq.w #6,oY(a0)
        int d1 = floorDistance();
        if (d1 > -8) return;                            // cmpi.w #-8,d1; bgt.s .End
        y = (y + d1 + 8) & 0xFFFF;                      // subi.w #-8,d1; add.w d1,oY(a0)
        routine += 2;
        // tst.b oSprFlags(a0); bpl.s .End; FM_A7 (Mosqui/Main.asm:131-134): not hosted.
    }

    /** ObjMosqui_Wait (Mosqui/Main.asm:142-150): off screen, DespawnObject. */
    private void waitOffscreen() {
        if (!onScreen()) ObjectLifetimeOps.destroyRespawnableOffscreen(this);
    }
}
