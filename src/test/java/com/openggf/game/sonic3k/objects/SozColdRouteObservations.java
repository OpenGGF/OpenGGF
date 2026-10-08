package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;

/** Read-only package-level observations for rendered cold-route acceptance. */
public final class SozColdRouteObservations {
    private SozColdRouteObservations() {}

    public static boolean capsuleOpened() {
        return GameServices.level()
                .getObjectManager()
                .activeObjectsOfType(SozEndBossEggCapsule.class)
                .stream()
                .anyMatch(SozEndBossEggCapsule::isOpened);
    }
}
