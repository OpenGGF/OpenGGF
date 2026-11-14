package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * A Hotaru firefly ({@code HotaruObject} with secondary subtype $FF, objects/r7/hotaru.asm:147-458), an
 * enemy (collision type $32) spawned by {@link ObjHotaru} in a pair. It patrols $50 pixels either side of
 * where it appeared (routine 2); when the player comes near, both fireflies fly to the middle of the screen
 * (routine 4), lock to screen coordinates for a flash (routines 6-8), and then play the electric phase:
 * the left one spawns three sparks ({@link ObjHotaruSpark}) between them (routine $E), they flash
 * (routine $12) and rise away (routine $14). Subtype 0 is the fast set (1 pixel a frame, first mapping and
 * animation sets), anything else the slow one (half a pixel, second sets).
 *
 * <p>The screen-locked phase (routines 6 to 8, sprite flags 0) keeps the object at its screen position;
 * the original also leaves the player's collision test comparing the player's world position with the
 * object's screen coordinates, which is not modelled: the firefly's world position tracks its screen
 * position instead, and its collision type $33 applies there (unverified: no native row reaches it).
 */
public final class ObjHotaruFirefly extends SharedBadnik {
    private final ObjHotaru parent;
    /** The spawner's slot (oVar2A): the firefly tests the ID byte there each turn (loc_20DDD8). */
    private final int parentSlot;
    private boolean slow;
    private int speed;                           // obj.var_2e, 16.16
    private int originX;                         // obj.var_2c
    private boolean screenLocked;
    private int screenX;
    private int screenY;
    private boolean rangeCheck;
    private boolean killed;
    /** Gone by its own range check: it cleared the spawner's pointer to it (loc_20E0AA, :417-425). */
    private boolean gone;
    private final int baseTile;

    ObjHotaruFirefly(ObjectSpawn spawn, ObjHotaru parent, int tile) {
        super(spawn, "Hotaru firefly");
        this.parent = parent;
        this.parentSlot = parent.getSlotIndex();
        this.baseTile = tile;
        this.tileWord = tile;
    }

    /** True once the player's attack destroyed it (as opposed to leaving the range). */
    boolean killed() { return killed; }

    /** True once it despawned by its range check, after which the spawner holds no pointer to it. */
    boolean gone() { return gone; }

    /**
     * loc_20E086 then loc_20E0AA (:401-425): CheckObjectDespawn deletes the firefly, which then clears the spawner's
     * {@code var_32} or {@code var_34}; the spawner reads the cleared pointer on its next turn.
     */
    @Override
    public void onUnload() {
        super.onUnload();
        gone = true;
    }

    /** Every range check follows loc_20E086's {@code jsr DrawObject} (:401-404), so it reads the bit cleared. */
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    @Override
    protected boolean ready() {
        if (!slotHoldsId(parentSlot, 0x23)) {
            setDestroyed(true);                  // loc_20DDD8: the spawner's slot no longer holds object $23 (:148-153)
            return false;
        }
        return super.ready();
    }

    @Override
    protected void run(PlayableEntity player) {
        rangeCheck = false;
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite s ? s : null;
        if (screenLocked) {
            x = screenX - 0x80 + services().camera().getX();
            y = screenY - 0x80 + services().camera().getY();
        }
        switch (routine) {
            case 0 -> init(sprite);                                          // returns without drawing (:177-213)
            case 2 -> { patrol(sprite); rangeCheck = true; }
            case 4 -> { approach(); rangeCheck = true; }
            case 6 -> { lockToScreen(); animate(); }                         // Routine6 falls into Routine8 (:360-380)
            case 8 -> animate();
            case 0xA -> { unlock(); animate(); rangeCheck = true; }          // RoutineA falls into RoutineC (:384-399)
            case 0xC, 0x10 -> { animate(); rangeCheck = true; }
            case 0xE -> {                                                    // HotaruObject_0_RoutineE (:429-437)
                routine += 2;
                animator.anim = 2;
                animator.restart();
                if (getSpawn().subtype() == 0) spawnSparks();
                animate();
                rangeCheck = true;
            }
            case 0x12 -> {                                                   // HotaruObject_0_Routine12 (:441-444)
                routine += 2;
                animator.anim = 3;
                animator.restart();
                animate();
                rangeCheck = true;
            }
            case 0x14 -> rise(sprite);
            default -> { }
        }
    }

    /** HotaruObject_0_Routine0 (:177-213). */
    private void init(AbstractPlayableSprite player) {
        var payload = cdLevel().payload();
        routine += 2;
        priority = 1;
        width = 0x14;
        yRadius = 0x0C;
        colType = 0x32;
        originX = x;
        slow = getSpawn().subtype() != 0;
        speed = slow ? -0x8000 : -0x10000;
        animator = new CdAnimator(artProvider().image(),
                SharedObjectCatalogue.offset(payload, slow ? "hotaruAnims2" : "hotaruAnims1"), 0);
        initialised = true;
        if (player != null && (player.getCentreX() & 0xFFFF) >= (x & 0xFFFF)) {   // cmp.w obj.x(a0),d0; blt (:205-207)
            speed = -speed;
            flipHorizontally();
        }
    }

    /** HotaruObject_0_Routine2 (:217-255): patrol, and wake both fireflies when the player is near. */
    private void patrol(AbstractPlayableSprite player) {
        addToX(speed);
        int d = (short) (x - originX);
        if (Math.abs(d) >= 0x50) {
            addToX(-speed);
            speed = -speed;
            flipHorizontally();
        }
        if (player != null && ObjHotaru.near(player, x, y, -0x38, 0x70, -0x50, 0xA0)) {
            if (parent.plus != null && !parent.plus.isDestroyed()) parent.plus.routine = 4;
            if (parent.minus != null && !parent.minus.isDestroyed()) parent.minus.routine = 4;
        }
    }

    /** HotaruObject_0_Routine4 (:259-353): fly both fireflies to the middle of the screen. */
    private void approach() {
        ObjHotaruFirefly a3 = parent.plus;
        ObjHotaruFirefly a4 = parent.minus;
        if (a3 == null || a4 == null || a3.isDestroyed() || a4.isDestroyed()) {
            routine += 2;                                                    // loc_20DFF6: addq.b #2,obj.routine(a0)
            lockToScreen();
            animate();
            return;
        }
        int step = Math.abs(speed);
        int cameraX = services().camera().getX();
        int cameraY = services().camera().getY();
        boolean xAligned = false;
        int midX = ((short) (a3.x + a4.x) >> 1) - cameraX;
        if (midX < 0x98) {                                                   // loc_20DF7A
            a3.setFacingRight(true);
            a4.setFacingRight(true);
            a3.addToX(step);
            a4.addToX(step);
        } else if (midX > 0xA8) {                                            // loc_20DFA4
            a3.setFacingRight(false);
            a4.setFacingRight(false);
            a3.addToX(-step);
            a4.addToX(-step);
        } else {
            xAligned = true;
        }
        boolean yAligned = false;
        int midY = ((short) (a3.y + a4.y) >> 1) - cameraY;
        if (midY < 0x68) {                                                   // loc_20DFD0
            a3.addToY(step);
            a4.addToY(step);
        } else if (midY > 0x78) {                                            // loc_20DFE2
            a3.addToY(-step);
            a4.addToY(-step);
        } else {
            yAligned = true;
        }
        if (xAligned && yAligned) {
            a3.routine = 6;
            a4.routine = 6;
        }
    }

    private void setFacingRight(boolean right) {
        if (right) { spriteFlip |= 1; statusFlip |= 1; } else { spriteFlip &= ~1; statusFlip &= ~1; }
    }

    /** HotaruObject_0_Routine6 (:360-375): sprite flags 0 and screen coordinates, then Routine8. */
    private void lockToScreen() {
        routine += 2;
        width = 0x0C;
        yRadius = 0x14;
        colType = 0x33;
        spriteFlip = 0;
        screenX = x - services().camera().getX() + 0x80;
        screenY = y - services().camera().getY() + 0x80;
        screenLocked = true;
    }

    /** HotaruObject_0_RoutineA (:384-395): back to world coordinates, animation 1. */
    private void unlock() {
        routine += 2;
        animator.anim = 1;
        animator.restart();
        spriteFlip = 0;
        screenLocked = false;
        x = screenX - 0x80 + services().camera().getX();
        y = screenY - 0x80 + services().camera().getY();
    }

    /** HotaruObject_0_RoutineE's sub_20E0F8 (:463-504): three sparks on the line between the pair. */
    private void spawnSparks() {
        ObjHotaruFirefly right = parent.minus;
        if (parent.plus != this || right == null || right.isDestroyed()) return;
        if (!renderFlagOnScreen() || !right.renderFlagOnScreen()) return;     // tst.b 1(a5); bpl / tst.b 1(a6); bpl
        var objects = services().objectManager();
        if (objects == null) return;
        int stepX = (short) (right.x - x) >> 2;
        int stepY = (short) (right.y - y) >> 2;
        int sx = x;
        int sy = y + 7;
        for (int i = 0; i < 3; i++) {
            sx += stepX;
            sy += stepY;
            objects.addDynamicObject(new ObjHotaruSpark(
                    new ObjectSpawn(sx, sy, getSpawn().objectId(), getSpawn().subtype(), 0, false, 0), this, right, baseTile));
        }
    }

    /** HotaruObject_0_Routine14 (:448-459): rise 2 pixels a frame until off screen and the player is $A0 below. */
    private void rise(AbstractPlayableSprite player) {
        y -= 2;
        if (!renderFlagOnScreen() && player != null && (short) (player.getCentreY() - y) > 0xA0) {
            setDestroyed(true);                                              // loc_20E09E: DeleteObject
            return;
        }
        animate();
        rangeCheck = true;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        killed = true;
        super.onPlayerAttack(player, result);
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return rangeCheck; }

    @Override
    protected String sheetKey() {
        return slow ? SharedObjectArt.HOTARU_2 : SharedObjectArt.HOTARU_1;
    }
}
