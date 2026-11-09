package com.openggf.net.hub;

import com.openggf.net.protocol.TrackKey;
import java.util.Optional;

/** Kind-aware extension for sources supporting arenas, retaining lambda act sources. */
public interface KeyedTrackValidationProfileSource extends TrackValidationProfileSource {
    Optional<TrackValidationProfile> profileFor(TrackKey key);
    @Override default Optional<TrackValidationProfile> profileFor(String game, int zone, int act) {
        return profileFor(new TrackKey(game, "ACT", zone, act));
    }
}
