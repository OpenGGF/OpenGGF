package com.openggf.level;

import com.openggf.game.EngineOnly;
import java.util.Objects;

/** Internal load-assembly bridge to the existing manager-owned course writers. */
public final class NativeLevelCourseReset {
    private NativeLevelCourseReset() { }

    @EngineOnly
    public static void reset(LevelManager manager, int zone, int act) {
        Objects.requireNonNull(manager, "level manager").resetForCourse(zone, act);
    }
}
