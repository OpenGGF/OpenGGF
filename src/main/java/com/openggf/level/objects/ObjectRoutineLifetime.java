package com.openggf.level.objects;

/** Internal execution profile: a routine tail is independent of placement-block retirement. */
public interface ObjectRoutineLifetime {
    enum Tail { MARK_OBJ_GONE, MARK_OBJ_GONE_2, MARK_OBJ_GONE_3, MARK_OBJ_GONE_P1, CUSTOM }
    Tail tail(ObjectInstance object);
    /** Published production coarse camera word, not a recomputed camera position. */
    int coarseCameraX(int slot);
}
