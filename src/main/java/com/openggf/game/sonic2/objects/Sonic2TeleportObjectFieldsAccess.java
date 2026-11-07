package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.competition.state.Sonic2TeleportPorts;
import com.openggf.level.objects.AbstractObjectInstance;

/** Engine composition of the corrected native per-player fields, outside the creator API. */
public final class Sonic2TeleportObjectFieldsAccess implements Sonic2TeleportPorts.ObjectFields {
    @Override public boolean operational() { return true; }
    @Override public Sonic2TeleportPorts.ObjectPlayerFields fields(AbstractObjectInstance object) {
        if (object.isDestroyed()) throw new IllegalArgumentException("Deleted native object");
        // swap_loop_objects (s2.asm:26069-26096), fixBugs=0: only these live IDs.
        // Obj03 belongs to B's plane controller; prize parent pointers never move.
        return switch (object.getLiveObjectId()) {
            case 0x84 -> object instanceof ForcedSpinObjectInstance spin ? spin::swapPlayerOwnership : reject(object);
            case 0xD6 -> object instanceof PointPokeyObjectInstance cage ? cage::swapPlayerOwnership : reject(object);
            case 0x85 -> object instanceof LauncherSpringObjectInstance spring ? spring::swapPlayerOwnership : reject(object);
            default -> reject(object);
        };
    }
    private static Sonic2TeleportPorts.ObjectPlayerFields reject(AbstractObjectInstance object) {
        throw new IllegalArgumentException("No native teleport field owner for live ID " + object.getLiveObjectId());
    }
}
