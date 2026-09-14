package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindSnapshottable;

/**
 * Captures the ROM Events_fg_4 counter shared by Obj_AIZ1TreeRevealControl
 * and AIZ1_ScreenEvent. Recreating the controller alone leaves the counter
 * at the future value and can delete it on the first replayed frame.
 */
public final class AizTreeRevealStaticAdapter implements RewindSnapshottable<Integer> {
    @Override public String key() { return "aiz-tree-reveal-counter"; }
    @Override public Integer capture() { return AizHollowTreeObjectInstance.getTreeRevealCounter(); }
    @Override public void restore(Integer counter) { AizHollowTreeObjectInstance.setTreeRevealCounter(counter); }
}
