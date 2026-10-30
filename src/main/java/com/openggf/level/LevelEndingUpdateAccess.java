package com.openggf.level;

import java.util.Objects;

/** Internal logical update of the separate ending scene; drawing only consumes its scroll output. */
public final class LevelEndingUpdateAccess {
    private LevelEndingUpdateAccess() { }

    /** Called after an admitted ending update selects its background and BG Y. No GL ownership. */
    public static void advanceBackground(LevelManager level, int bgVscroll) {
        Objects.requireNonNull(level, "level");
        if (level.level == null || level.level.getMap() == null) return;

        // Retain the existing ending background clock/sample, formerly advanced by drawing.
        // EndingSequence counts logical loops (docs/s2disasm/s2.asm:13134-13150),
        // and the object update dispatches native scroll at loc_A34C (s2.asm:13482-13490).
        level.frameCounter++;
        level.parallaxManager.updateForEnding(level.currentZone, level.currentAct, level.frameCounter, bgVscroll);
    }
}
