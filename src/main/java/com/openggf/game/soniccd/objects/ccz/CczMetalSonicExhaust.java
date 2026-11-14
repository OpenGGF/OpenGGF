package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Metal Sonic's exhaust, object $34 ({@code MetalSonicExhaustObject}, objects/r3/intro.asm:827-872): follows the
 * parent $10 left of it ($10 right when the parent faces right) at its Y, animating
 * {@code MetalSonicExhaustAnims}, and deletes itself as soon as the parent's slot no longer holds object $31.
 * Width/height are never set (it has no touch or solidity); priority 3, tile $3D0, the parent's sprite sheet.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczMetalSonicExhaust extends CczObject implements ObjectVisualFacing.NonDirectional {
    private final CczMetalSonic parent;
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$31,obj.id(a1)}). */
    private int parentSlot = -1;

    CczMetalSonicExhaust(ObjectSpawn spawn, CczMetalSonic parent) {
        super(spawn, "Metal Sonic exhaust");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            priority = 3;
            tileWord = CczObjectArt.METAL_SONIC_TILE;
        }
        if (!slotHoldsId(parentSlot, 0x31)) {                      // cmpi.b #$31,obj.id(a1); bne .Delete
            setDestroyed(true);
            return;
        }
        int d0 = parent.xPosition() - 0x10;
        if ((parent.spriteFlip() & 1) != 0) d0 += 0x20;                    // btst #0,obj.flags(a1)
        x = d0 & 0xFFFF;
        y = parent.yPosition();
        if (animator == null) animator = CczObjectArt.animator(provider(), "metalSonicExhaustAnimations", 0);
        animate();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.METAL_SONIC, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
