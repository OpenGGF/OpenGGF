package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Sasori's missile, object $21 with secondary subtype {@code $FF} (loc_20D51A, objects/r5/sasori.asm:
 * 298-305): hurt collision {@code $B2}, size {@code $10}, flying at a constant X velocity of
 * {@code -$30000} (left) or {@code $30000} (right), 16.16. Made by routine C, {@code SasoriObject_0_RoutineC}
 * (:210-242), 24 pixels above the body and 4 beside it. It animates with {@code SasoriMissileAnims} and runs
 * CheckObjectDespawn; it is deleted when its body is no longer object $21.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSasoriMissile extends QqzObject {
    private final ObjSasori body;
    /** obj.var_2a: the body's slot ({@code move.w a0,obj.var_2a(a1)}, objects/r5/sasori.asm:71). */
    private final int bodySlot;
    private final int velocity;

    ObjSasoriMissile(ObjSasori body, int x, int y, int velocity) {
        super(new ObjectSpawn(x, y, 0x21, body.getSpawn().subtype(), 0, false, 0), "Sasori missile", 0xFF);
        this.body = body;
        this.bodySlot = body.getSlotIndex();
        this.velocity = velocity;
        this.priority = body.priority();                     // loc_20D2B0: move.b obj.sprite_layer(a0)
        this.xRadius = 0x10;
        this.width = 0x10;
        this.yRadius = 0x10;
        this.colType = 0xB2;
        this.tileWord = QqzObjectArt.SASORI_TILE;
        this.animColumn = "sasoriMissileAnimations";
        this.initialised = true;
    }

    @Override protected String sheetKind() { return QqzObjectArt.SASORI_MISSILE; }

    @Override
    protected void run(PlayableEntity player) {
        if (!slotHoldsId(bodySlot, 0x21)) {                  // loc_20D502: cmpi.b #$21,obj.id(a1) (:289-291)
            deleteObject();                                  // loc_20D4FC
            drawThisFrame = false;
            return;
        }
        addToX(velocity);                                    // add.l d0,obj.x(a0)
        animate();
    }
}
