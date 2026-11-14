package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.ObjectServices;

/** Facts about the loaded Palmtree Panic stage that the zone's objects branch on. */
final class PpzStage {
    private PpzStage() { }

    /**
     * Act 3 (R13C, R13D) links a different object index: IDs $2A-$32 are the boss and its parts
     * (Palmtree Panic/Object Index (Act 3).asm:44-52), not the 3D ramp, plant or scenery. The registry resolves
     * by zone, so these objects stand down in the boss act until its own lane registers it.
     */
    static boolean bossAct(ObjectServices services) {
        var levels = services == null ? null : services.levelManager();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel cd
                && cd.payload().name().startsWith("R13");
    }

    /**
     * An object whose ID is {@code ObjNull} in the boss act's index (Palmtree Panic/Object Index (Act 3).asm): its first
     * run is {@code move.b #0,(a0)}, deleting it. Returns whether the object stood down.
     */
    static boolean standDown(ObjectServices services, com.openggf.level.objects.AbstractObjectInstance object) {
        if (!bossAct(services)) return false;
        object.setDestroyed(true);
        return true;
    }
}
