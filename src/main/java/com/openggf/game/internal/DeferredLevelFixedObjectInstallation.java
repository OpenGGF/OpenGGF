package com.openggf.game.internal;

import com.openggf.level.objects.FixedSstSlotSink;

/** Native fixed gameplay slots installed after the locked title's prefix. */
public interface DeferredLevelFixedObjectInstallation {
    boolean defersFixedLevelObjects();
    void installDeferredFixedLevelObjects(int romZoneId, int act, FixedSstSlotSink slots);
}
