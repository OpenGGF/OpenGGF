package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * Mecha-Bu's saw ({@code MechaBuObject} with {@code oSubtype2 = $FF}, objects/r8/mecha_bu.asm:104-176): a hurting child that
 * follows its parent at an offset, swinging between two positions every 60 frames (a nonzero parent subtype holds one).
 * It is deleted once the parent is gone. It has no despawn check of its own.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
final class MmzMechaBuSaw extends MmzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private final MmzMechaBu parent;
    /** oVar3E: the badnik's slot, which the saw tests each turn (objects/r8/mecha_bu.asm:130-131). */
    private final int parentSlot;
    private int routine;
    private int timer;                           // oVar2A
    private int dx;                              // oVar2C
    private int dy;                              // oVar2E
    private int anim;
    private int statusFlip;
    private int mapFrame;
    private com.openggf.game.soniccd.objects.common.CdAnimator animator;
    private int spriteFlip;

    MmzMechaBuSaw(ObjectSpawn spawn, MmzMechaBu parent) {
        super(spawn, "MechaBuSaw");
        this.parent = parent;
        this.parentSlot = parent.getSlotIndex();
    }

    void flipHorizontally() {
        statusFlip ^= 1;
        spriteFlip ^= 1;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            // MechaBuObject_1_Routine0 (objects/r8/mecha_bu.asm:119-128).
            routine = 2;
            priority = 3;
            tileWord = MmzObjectArt.MECHA_BU_TILE;
            width = 0xC;
            yRadius = 0x10;
        }
        if (!slotHoldsId(parentSlot, 0x24)) {                       // cmpi.b #$24,oID(a1); bne DeleteObject
            setDestroyed(true);
            return;
        }
        if (spawn.subtype() == 0) {
            timer = (timer - 1) & 0xFFFF;
            if ((timer & 0x8000) != 0) {                            // addi.w #-1,oVar2A; bpl.s
                timer = 60;
                anim ^= 1;
                place();
            }
        } else {
            place();
        }
        x = (parent.getX() + dx) & 0xFFFF;
        y = (parent.getY() + dy) & 0xFFFF;
        if (animator == null) animator = mmzAnimator("mechaBuSawAnimations", anim);
        if (animator != null) {
            animator.anim = anim;
            animator.step();
            mapFrame = animator.mapFrame();
            spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
        }
    }

    /** loc_20C5C8 (objects/r8/mecha_bu.asm:145-157): the saw's offset from the Mecha-Bu for its animation. */
    private void place() {
        dx = -0x19;
        dy = 0;
        if (anim != 0) {
            dx = -0x13;
            dy = -0xF;
        }
        if ((spriteFlip & 1) != 0) dx = -dx;
    }

    /** move.b #$AB,oColType(a0) (MechaBuObject_1_Routine0, objects/r8/mecha_bu.asm:119-128). */
    @Override public int getCollisionFlags() { return 0xAB; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(MmzObjectArt.MECHA_BU_SAW, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
