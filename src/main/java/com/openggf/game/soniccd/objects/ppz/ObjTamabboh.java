package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Tamabboh, object $22 with any subtype but 1 ({@code ObjTamabboh}, Palmtree Panic/Objects/Tamabboh/
 * Main.asm:7-199): a ground badnik that patrols $80 pixels either side of its start, stops when the
 * player is within $50 pixels, and fires two missiles ({@link ObjTamabbohMissile}, the same object ID
 * with subtype 1) before patrolling again. A damaged variant (any other non-zero subtype) never fires.
 * Collision $2C. The fire sound {@code FM_A0} is not hosted.
 *
 * <p>Routines (Main.asm:21-27): 0 init, 2 settle on the floor, 4 patrol, 6 start the wait animation,
 * 8 animate until the script's {@code $FC}, 10 fire. Init has no {@code rts} and runs the floor
 * settle in the same frame (Main.asm:58-72); the wait routine runs its animate (Main.asm:148-158);
 * the patrol re-runs from its top after a turn (Main.asm:115-119), which decrements the cooldown
 * and tests the range again.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjTamabboh extends PpzBadnik {
    /** ObjTamabboh_Fire: move.w #$78,oVar34(a0) (Tamabboh/Main.asm:165). */
    private static final int COOLDOWN = 0x78;

    private int originX;
    /** oVar2C: patrol velocity, 16.16 (Main.asm:44,52,57). */
    private int velocity;
    /** oVar34: frames before the range test starts again. */
    private int cooldown;
    private boolean damaged;

    public ObjTamabboh(ObjectSpawn spawn) {
        super(spawn, "Tamabboh");
    }

    @Override protected int tileIdIndex() { return PpzObjectArt.TILE_ID_TAMABBOH; }

    @Override protected String sheetKind() {
        return damaged ? PpzObjectArt.PPZ_TAMABBOH2 : PpzObjectArt.PPZ_TAMABBOH1;
    }

    @Override protected int despawnX() { return originX; }

    @Override
    protected void run(PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); settle(); }
            case 2 -> settle();
            case 4 -> patrol(player);
            case 6 -> { routine += 2; animator.anim = 1; animate(); }     // Wait1 falls into Wait2
            case 8 -> animate();
            case 10 -> fire();
            default -> { }
        }
    }

    /** ObjTamabboh_Init (Tamabboh/Main.asm:29-58). */
    private void init() {
        routine += 2;
        priority = 4;
        colType = 0x2C;
        xRadius = 0x10;
        width = 0x10;
        yRadius = 0xF;
        originX = x;
        damaged = spawn.subtype() != 0;
        velocity = damaged ? -0x5000 : -0xA000;
        animator = PpzObjectArt.animator(artProvider(), e -> damaged ? e.tamabbohAnimations2() : e.tamabbohAnimations1(), 0);
        initialised = true;
    }

    /** ObjTamabboh_Position (Tamabboh/Main.asm:62-72). */
    private void settle() {
        addToY(0x10000);
        if (floorDistance() < 0) routine += 2;
    }

    /** ObjTamabboh_Main (Tamabboh/Main.asm:76-126). {@code debugMode} (scddisasm/src/Level/Palmtree Panic/Objects/Tamabboh/Main.asm:77) is not modelled. */
    private void patrol(PlayableEntity player) {
        for (int turns = 0; ; ) {
            if (spawn.subtype() == 0 && player != null) {
                if (cooldown != 0) {
                    cooldown = (cooldown - 1) & 0xFFFF; // subq.w #1,oVar34(a0)
                } else if (inFireRange(player)) {
                    routine += 2;                       // .NextState
                    return;
                }
            }
            addToX(velocity);
            int distance = Math.abs((short) (x - originX));
            if (distance < 0x80) {                      // cmpi.w #$80,d0; bge.s .TurnAround
                int d1 = floorDistance();
                if (d1 >= -7 && d1 <= 7) {
                    y = (y + d1) & 0xFFFF;
                    animate();
                    return;
                }
            }
            if (++turns > 2) {
                // Both directions fail the same tests, so the ROM would loop for ever here; fail loudly (see ObjAnton).
                throw new IllegalStateException(String.format("Tamabboh never finds floor: x=%04X y=%04X origin=%04X floor distance %d (slot %d)",
                        x, y, originX, floorDistance(), getSlotIndex()));
            }
            velocity = -velocity;                       // .TurnAround
            flipHorizontally();
        }
    }

    /** ObjTamabboh_CheckInRange (Tamabboh/Main.asm:130-143): inside [x-$50, x+$50) and [y-$50, y+$50). */
    private boolean inFireRange(PlayableEntity player) {
        int dy = (player.getCentreY() - y + 0x50) & 0xFFFF;
        if (dy >= 0xA0) return false;
        return ((player.getCentreX() - x + 0x50) & 0xFFFF) < 0xA0;
    }

    /** ObjTamabboh_Fire (Tamabboh/Main.asm:162-199): two missiles, the second flying the other way. */
    private void fire() {
        routine = 4;
        animator.anim = 0;
        cooldown = COOLDOWN;
        if (spawn.subtype() != 0) return;
        var objects = services().objectManager();
        if (objects == null) return;
        // FindObjSlot, then ObjTamabboh_InitMissile with oVar3F clear; a second for oVar3F set.
        for (boolean left : new boolean[] {false, true}) {
            ObjTamabbohMissile missile = new ObjTamabbohMissile(x, y - 10, tileWord, priority + 1, left);
            objects.addDynamicObject(missile);
            if (missile.getSlotIndex() < 0) return;     // bne.s .End: no free slot
        }
    }
}
