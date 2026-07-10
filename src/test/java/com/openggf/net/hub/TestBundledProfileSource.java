package com.openggf.net.hub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBundledProfileSource {
    @Test
    void loadsBundledTableAndDegradesOnUnknownTracks() {
        BundledProfileSource source = new BundledProfileSource();
        TrackValidationProfile aiz1 = source.profileFor("s3k", 0, 0).orElseThrow();
        assertTrue(aiz1.levelWidthPx() > 1000);
        assertTrue(aiz1.levelHeightPx() > 200);
        assertEquals(TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME,
                aiz1.maxSpeedPxPerFrame());
        assertTrue(source.profileFor("s3k", 99, 0).isEmpty());
        assertTrue(source.profileFor("nope", 0, 0).isEmpty());
    }
}
