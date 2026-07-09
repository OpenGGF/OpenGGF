package com.openggf.net.hub;

import java.util.Optional;

/** Supplies ROM-free validation profiles to a room. */
public interface TrackValidationProfileSource {
    Optional<TrackValidationProfile> profileFor(String gameId, int zone, int act);

    static TrackValidationProfileSource none() {
        return (gameId, zone, act) -> Optional.empty();
    }
}
