package com.openggf.level;

/** Internal native viewport copy for observations before the first interactive presentation. */
public final class LevelManagerObservationAccess {
    private LevelManagerObservationAccess() { }
    public static int width(LevelManager level) { return level==null?320:level.observedViewportWidth(); }
    public static int height(LevelManager level) { return level==null?224:level.observedViewportHeight(); }
}
