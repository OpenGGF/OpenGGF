package com.openggf.game.internal;

/** A title wait serviced by the ordinary PLC lifecycle, before level objects are dispatched. */
public interface NativeTitleOnlyVBlankWait {
    /** Whether the upcoming title iteration needs a clock tick outside the level-object pass. */
    boolean ownsTitleOnlyVBlankWait();
}
