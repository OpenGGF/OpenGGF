package openggf.racing.server.master;

import com.openggf.net.hub.TrackValidationProfile;
import openggf.timeattack.TimeAttackTrackCatalog;

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

    @Test
    void everyEligibleTimeAttackActHasBounds() {
        // The master admits vote pools and track updates only for eligible acts with bounds, so the
        // regenerated table (TrackProfileExportTool) must cover the whole eligible catalog.
        BundledProfileSource source = new BundledProfileSource();
        for (String game : new String[] {"s1", "s2", "s3k"}) {
            for (TimeAttackTrackCatalog.Track track : TimeAttackTrackCatalog.eligibleFor(game)) {
                assertTrue(source.profileFor(game, track.zone(), track.act()).isPresent(),
                        "no bundled bounds for " + track.key() + " " + track.label());
            }
        }
    }
}
