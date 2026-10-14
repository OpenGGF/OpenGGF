package com.openggf.game.mutators;

import com.openggf.level.objects.ObjectSpawn;

/** Trusted route qualification. Negative means unreviewed: preserve the native launch minimum. */
@FunctionalInterface
public interface SpringRouteMinimumSource {
    int minimumLaunchMagnitude(ObjectSpawn spring, boolean downward);
}
