package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.ObjectServices;

/**
 * Which Collision Chaos object index the loaded stage links. The registry resolves object IDs by zone, but the IDs
 * {@code $30}-{@code $36} differ by stage: Act 1 Present (R31A) assembles the intro cast ({@code $30} intro spikes, {@code $31}
 * Metal Sonic, {@code $32} Amy Rose, {@code $33} heart, {@code $34} exhaust; Object Index (Act 1 Present).asm:60-64), the other acts 1 and
 * 2 stages leave them {@code ObjNull} (r3/object_index_1b_1c_1d.asm, object_index_2.asm: a null object deletes itself,
 * Object Index (Act 1 Present).asm:76-78), and Act 3 links the boss and the drain puzzle ({@code $30} entry barrier, {@code $31} boss
 * machine, {@code $32} gear, {@code $33} Eggman, {@code $36} drain; r3/object_index_3.asm). An object of this lane that is
 * placed in a stage whose index does not link it stands down as the ROM's index does: deleted where the slot is
 * {@code ObjNull}, left alone in Act 3 until the boss lane registers its parts.
 */
final class CczStage {
    private CczStage() { }

    private static String name(ObjectServices services) {
        var levels = services == null ? null : services.levelManager();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel cd ? cd.payload().name() : "";
    }

    /** True in Act 1 Present, the only stage whose index links the intro objects. */
    static boolean intro(ObjectServices services) {
        return name(services).equals("R31A");
    }

    /** True in the Act 3 stages (R33C, R33D). */
    static boolean bossAct(ObjectServices services) {
        return name(services).startsWith("R33");
    }

    /**
     * True when {@code object} must not run an intro-only routine here; deletes it where the index slot is
     * {@code ObjNull} (acts 1 and 2 other than R31A), leaves it where Act 3's boss index owns the ID.
     */
    static boolean standDownFromIntro(CczObject object, ObjectServices services) {
        if (intro(services)) return false;
        if (!bossAct(services)) object.setDestroyed(true);
        return true;
    }

    /** True when {@code object} must not run the drain routines here (only Act 3 links {@code $36}); deletes it elsewhere. */
    static boolean standDownFromDrain(CczObject object, ObjectServices services) {
        if (bossAct(services)) return false;
        object.setDestroyed(true);
        return true;
    }
}
