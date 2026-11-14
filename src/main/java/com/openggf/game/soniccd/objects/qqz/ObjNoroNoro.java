package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.audio.CdSoundRequests;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Noro Noro, object $20 ({@code NoroNoroObject}, objects/r5/noro_noro.asm:3-14), a slug badnik. It drops
 * to the floor, then patrols {@code $50} pixels either side of its start at 0.25 pixels a frame, turning
 * at the range limit or where the floor ahead is more than 7 below or 6 above (routine 4). A subtype-0
 * Noro that sees the player inside a {@code $F0} square (centre {@code -$78}) switches to animation 1 and
 * makes a spikes child ({@link ObjNoroSpikes}, subtype {@code $FF}, collision {@code $B0}) 20 pixels above
 * itself, deleting it again when the player leaves; subtype 1 uses the second sprite table and never does.
 * Collision {@code $2F} (enemy). {@code DestroyInGoodFuture} runs first (objects/r5/noro_noro.asm:5).
 *
 * <p>Routines (off_20CFB6, :17-20): 0 init, which falls into 2; 2 fall; 4 walk. The Act 3 index links the
 * boss's sparks at this ID, so the object stands down there. Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjNoroNoro extends QqzBadnik {
    /** NoroNoroObject_0_Routine0: {@code move.l #-$4000,obj.var_2c(a0)} (objects/r5/noro_noro.asm:24-34). */
    private static final int WALK_VELOCITY = -0x4000;

    private int originX;
    private int velocity;
    private ObjNoroSpikes spikes;

    public ObjNoroNoro(ObjectSpawn spawn) {
        super(spawn, "Noro Noro");
    }

    @Override protected int despawnX() { return originX; }

    @Override
    protected String sheetKind() {
        return spawn.subtype() == 0 ? QqzObjectArt.NORO1 : QqzObjectArt.NORO2;
    }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        if (destroyInGoodFuture()) return;
        switch (routine) {
            case 0 -> { init(); fall(); }                 // Routine0 falls through into Routine2
            case 2 -> fall();
            case 4 -> walk(player);
            default -> { }
        }
    }

    /** NoroNoroObject_0_Routine0 (objects/r5/noro_noro.asm:24-41). */
    private void init() {
        originX = x;                                      // move.w obj.x(a0),obj.var_2a(a0)
        priority = 3;
        tileWord = QqzObjectArt.NORO_TILE;
        xRadius = 0x10;
        width = 0x10;
        yRadius = 0xF;
        colType = 0x2F;
        velocity = WALK_VELOCITY;
        routine += 2;
        animColumn = "noroAnimations";
        initialised = true;
    }

    /** NoroNoroObject_0_Routine2 (objects/r5/noro_noro.asm:43-52): one pixel down until the floor is reached. */
    private void fall() {
        addToY(0x10000);
        int d1 = floorDistance();
        if (d1 >= 0) return;                              // tst.w d1; bpl.s
        y = (y + d1) & 0xFFFF;
        routine += 2;
    }

    /** NoroNoroObject_0_Routine4 (objects/r5/noro_noro.asm:56-122). */
    private void walk(PlayableEntity player) {
        addToX(velocity);
        int offset = Math.abs((short) (x - originX));
        if (offset >= 0x50) {                             // cmpi.w #$50,d0; bge.w loc_20D0FA
            turn();
            return;
        }
        int d1 = floorDistance();
        if (((d1 + 7) & 0xFFFF) >= 0xE) {                 // addq.w #7; subi.w #$E; bcc.w loc_20D0FA
            turn();
            return;
        }
        y = (y + d1) & 0xFFFF;
        if (spawn.subtype() != 0) {                       // tst.b obj.subtype(a0); bne.w loc_20D0F0
            animate();
            return;
        }
        if (player != null && playerInRange(player)) {
            animId = 1;                                   // move.b #1,obj.anim_id(a0)
            if (spikes == null) spawnSpikes();
        } else {
            animId = 0;                                   // loc_20D0D4
            if (spikes != null) {
                spikes.deleteFromParent();                // jsr DeleteOtherObject
                spikes = null;                            // move.w #0,obj.var_3e(a0)
            }
        }
        animate();
    }

    /** loc_20D0FA (objects/r5/noro_noro.asm:118-122): turn round. */
    private void turn() {
        velocity = -velocity;
        flipHorizontally();
    }

    /**
     * sub_20D10C (objects/r5/noro_noro.asm:126-138): carry set when the player is within {@code [-$78, $78)} of the
     * Noro on both axes (the two {@code subi.w} borrows).
     */
    private boolean playerInRange(PlayableEntity player) {
        if (!inWindow(centreY(player) - y, 0x78, 0xF0)) return false;
        return inWindow(centreX(player) - x, 0x78, 0xF0);
    }

    /** NoroNoroObject_0_Routine4 (objects/r5/noro_noro.asm:79-102): SpawnObject, the child, and the sound. */
    private void spawnSpikes() {
        var objects = services().objectManager();
        if (objects == null) return;
        ObjNoroSpikes child = new ObjNoroSpikes(this, (x) & 0xFFFF, (y - 0x14) & 0xFFFF, tileWord, sub2());
        objects.addDynamicObject(child);
        if (child.isDestroyed()) return;                  // bne.s loc_20D0F0: no free slot
        spikes = child;
        if (onScreen()) services().playSfx(0xB7);         // tst.b obj.sprite_flags(a0); bpl.s; move.w #$B7,d0
    }
}
