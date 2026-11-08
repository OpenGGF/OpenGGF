package com.openggf.level.objects;

/** Internal execution profile: a routine tail is independent of placement-block retirement. */
public interface ObjectRoutineLifetime {
    enum Tail { MARK_OBJ_GONE, MARK_OBJ_GONE_2, MARK_OBJ_GONE_3, MARK_OBJ_GONE_P1, CUSTOM }
    Tail tail(ObjectInstance object);
    /** Published production coarse camera word, not a recomputed camera position. */
    int coarseCameraX(int slot);
    // MarkObjGone_P2 (s2.asm:30303-30324): unsigned word distances from both published coarse banks.
    static boolean outOfRange(ObjectInstance instance, ObjectRoutineLifetime profile) {
        if (profile == null || profile.tail(instance) != ObjectRoutineLifetime.Tail.MARK_OBJ_GONE_P1)
            return false;
        int x = instance.getX() & 0xFF00;
        return ((x - profile.coarseCameraX(0)) & 0xFFFF) > 0x300
                && ((x - profile.coarseCameraX(1)) & 0xFFFF) > 0x300;
    }


}
