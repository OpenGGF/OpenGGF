package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Sasori's gun, object $21 with secondary subtype 1 (loc_20D502, objects/r5/sasori.asm:288-294): a
 * sprite with no collision that the body positions every frame. It is created by routine 2 with the body's
 * position, ID and priority minus one, frame {@code oSubtype}, size {@code $18}/{@code $10} (:80-99), and is
 * deleted when its body is no longer object $21.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSasoriGun extends QqzObject {
    private final ObjSasori body;
    /** obj.var_2a: the body's slot ({@code move.w a0,obj.var_2a(a1)}, objects/r5/sasori.asm:71). */
    private final int bodySlot;

    ObjSasoriGun(ObjSasori body) {
        super(new ObjectSpawn(body.getX(), body.getY(), 0x21, body.getSpawn().subtype(), 0, false, 0),
                "Sasori gun", 1);
        this.body = body;
        this.bodySlot = body.getSlotIndex();
        this.xRadius = 0x18;
        this.width = 0x18;
        this.yRadius = 0x10;
        this.priority = body.priority() - 1;                 // subq.b #1,obj.sprite_layer(a1)
        this.mapFrame = body.getSpawn().subtype();           // move.b obj.subtype(a0),obj.sprite_frame(a1)
        this.tileWord = QqzObjectArt.SASORI_TILE;
        // The slot is fresh (SpawnObjectAfter): oSprFlags 4 only, so no flip, whatever the body's layout flags.
        this.spriteFlip = 0;
        this.statusFlip = 0;
        this.initialised = true;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return false; }

    @Override protected String sheetKind() { return QqzObjectArt.SASORI_GUN; }

    @Override
    protected void run(PlayableEntity player) {
        if (!slotHoldsId(bodySlot, 0x21)) {                  // loc_20D502: cmpi.b #$21,obj.id(a1) (:289-291)
            deleteObject();                                  // loc_20D4FC
            drawThisFrame = false;
        }
    }

    /** The body writes the gun's X and Y words. */
    void place(int newX, int newY) {
        x = newX & 0xFFFF;
        y = newY & 0xFFFF;
    }

    /** {@code addq/subq} on the gun's X and Y words. */
    void nudge(int dx, int dy) {
        x = (x + dx) & 0xFFFF;
        y = (y + dy) & 0xFFFF;
    }

    /** {@code bchg #0,obj.sprite_flags(a1); bchg #0,obj.flags(a1)}. */
    void flip() {
        flipHorizontally();
    }
}
