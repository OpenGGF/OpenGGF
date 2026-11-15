package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Noro Noro's spikes, object $20 with subtype {@code $FF} (loc_20D130, objects/r5/noro_noro.asm:142-157):
 * a hurt hitbox ({@code $B0}, size {@code $30}) that follows its parent 20 pixels up and is deleted when the
 * parent is gone or its secondary subtype differs.
 *
 * <p>Its {@code oSprFlags} are zero (the parent's are not copied, objects/r5/noro_noro.asm:78-85), so DrawObject takes
 * the screen-position path ({@code obj.x}, {@code oYScr}, src/common/object.asm:DrawObjectsNoScrollPosition)
 * and the sprite lands at screen {@code (oX - $80, oXSub - $80)}: never over the Noro. Nothing is drawn here;
 * the touch pass still sees it because that path marks the slot drawn unconditionally.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjNoroSpikes extends QqzObject {
    private final ObjNoroNoro parent;
    /** oVar3E: the parent's slot; loc_20D130 tests the ID byte and the secondary subtype there each turn. */
    private final int parentSlot;
    private final int parentSubtype2;

    ObjNoroSpikes(ObjNoroNoro parent, int x, int y, int parentTileWord, int subtype2) {
        super(new ObjectSpawn(x, y, 0x20, 0xFF, 0, false, 0), "Noro Noro spikes", subtype2);
        this.parent = parent;
        this.parentSlot = parent.getSlotIndex();
        this.parentSubtype2 = subtype2;
        this.tileWord = parentTileWord;                   // move.w obj.sprite_tile(a0),obj.sprite_tile(a1)
        this.colType = 0xB0;                              // move.b #$B0,obj.collide_type(a1)
        this.initialised = true;
        this.priority = 0;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return false; }

    @Override protected String sheetKind() { return null; }

    @Override
    protected void run(PlayableEntity player) {
        drawThisFrame = false;
        // loc_20D130 (objects/r5/noro_noro.asm:142-152): cmpi.b #$20,obj.id(a1), then the secondary subtype, then the position.
        var occupant = slotHoldsId(parentSlot, 0x20) ? slotOccupant(parentSlot) : null;
        if (!(occupant instanceof QqzObject live) || live.sub2() != parentSubtype2) {
            deleteObject();                               // loc_20D15E: jmp DeleteObject
            return;
        }
        x = live.getX();                                  // move.w obj.x(a1),obj.x(a0)
        y = (live.getY() - 0x14) & 0xFFFF;                // addi.w #-$14,obj.y(a0)
    }

    /** {@code jsr DeleteOtherObject} from the parent. */
    void deleteFromParent() {
        deleteObject();
    }
}
