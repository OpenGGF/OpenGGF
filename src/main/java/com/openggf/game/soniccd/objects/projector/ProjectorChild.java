package com.openggf.game.soniccd.objects.projector;

import com.openggf.level.objects.ObjectServices;

/**
 * An object the projector creates that follows it: a hologram animal ({@code oProjectorParent}, {@code oVar3E}, is the
 * projector's slot). The animal's hologram routine tests {@link #parentAlive}: {@code movea.w oVar3E(a0),a1; cmpi.b
 * #parentId,oID(a1); bne ... Destroy; tst.b oVar3F(a1); bne ... Destroy} (Palmtree Panic/Objects/Animal/Main.asm:77-82,
 * 170-175).
 */
public interface ProjectorChild {
    /** {@code move.w a0,oVar3E(a1)}: the projector's slot and its object ID. */
    void linkProjector(int parentSlot, int parentId);

    int projectorSlot();

    int projectorId();

    /** The two tests every child makes of its parent on each turn. */
    static boolean parentAlive(ObjectServices services, int slot, int id) {
        var objects = services.objectManager();
        if (objects == null || slot < 0 || objects.objectIdInSlot(slot) != (id & 0xFF)) return false;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof CdProjector projector && !projector.isDestroyed() && projector.getSlotIndex() == slot) {
                return !projector.childrenInvalid();
            }
        }
        return true;     // an object with the same ID in the slot that is not a projector: its oVar3F is not modelled
    }
}
