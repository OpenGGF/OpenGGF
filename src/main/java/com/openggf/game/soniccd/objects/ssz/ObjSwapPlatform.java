package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

/**
 * Swap platform, object $35 ({@code SwapPlatformObject}, objects/r7/swap_platform.asm:3-33): a $40 by $22
 * solid that exists only while {@code stage_layer} is set. With the layer set it calls {@code SolidObject}
 * every frame and remembers whether that returned a contact ({@code sne obj.var_2a}, :15-20); with the layer
 * clear it makes the player step off once ({@code GetOffObject}, :24-29) and is not solid. Its mapping frame
 * is {@code debug_cheat}, which is zero outside debug mode, and frame 0 of {@code SwapPlatformSprites} is empty,
 * so it is never drawn (the platform piece is frame 1). The routine ends in {@code CheckObjectDespawn}
 * (:32-33). Acts 1 and 2 place it as $35 (r7/object_index_1_2.asm slot $34) and act 3 as well.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSwapPlatform extends SszObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** oVar2A: SolidObject reported a top or bottom contact on the last layer-set frame. */
    private boolean touching;

    public ObjSwapPlatform(ObjectSpawn spawn) {
        super(spawn, "Swap platform");
    }

    /** Never drawn: frame 0 is empty (see the class comment). */
    @Override protected String sheetKind() { return null; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                  // :4-11
            routine += 2;
            width = 0x20;                                    // move.b #$20,obj.width_2(a0)
            yRadius = 0x11;                                  // move.b #$11,obj.height(a0)
        }
        mapFrame = 0;                                        // move.b debug_cheat,obj.sprite_frame(a0): debug mode is not hosted
        if (SszStageLayer.of(cdLevel()).isSet()) {
            touching = solidObject(ReevaluatingSolidObject.Entry.SOLID);
        } else if (touching) {
            touching = false;                                // clr.b obj.var_2a(a0)
            if (player != null) ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        }
    }
}
