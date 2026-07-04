package com.openggf.game.timeattack;

import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.SonicGame;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ROM-gated guard for {@link TimeAttackTrackCatalog}: the label/character
 * checks in {@link TestTimeAttackTrackCatalog} cannot catch a wrong zone/act
 * int (the S1/S2 catalog entries were originally seeded with the wrong ints
 * &mdash; see the catalog's Javadoc). Each catalog entry is loaded headless
 * via {@link SharedLevel}, the exact harness {@code TestS2Ehz1Headless} /
 * {@code TestLevelEntryPathsHeadless} (S2) and the S1 headless zone tests use.
 * A wrong zone/act int fails this test instead of surfacing only at menu launch.
 *
 * <p>One {@code @Test} per game so a missing ROM skips only that game's
 * validation via {@link Assumptions#assumeTrue}.
 */
class TestTimeAttackTrackCatalogRomValidation {

    @Test
    void s1TracksAllLoad() throws Exception {
        Assumptions.assumeTrue(RomTestUtils.ensureSonic1RomAvailable() != null,
                "Sonic 1 ROM not available; skipping catalog validation");
        assertAllTracksLoad(SonicGame.SONIC_1, "s1");
    }

    @Test
    void s2TracksAllLoad() throws Exception {
        Assumptions.assumeTrue(RomTestUtils.ensureSonic2RomAvailable() != null,
                "Sonic 2 ROM not available; skipping catalog validation");
        assertAllTracksLoad(SonicGame.SONIC_2, "s2");
    }

    @Test
    void s3kTracksAllLoad() throws Exception {
        Assumptions.assumeTrue(RomTestUtils.ensureSonic3kRomAvailable() != null,
                "Sonic 3&K ROM not available; skipping catalog validation");
        assertAllTracksLoad(SonicGame.SONIC_3K, "s3k");
    }

    private static void assertAllTracksLoad(SonicGame game, String gameId) throws Exception {
        for (TimeAttackTrackCatalog.Track track : TimeAttackTrackCatalog.tracksFor(gameId)) {
            SharedLevel shared = SharedLevel.load(game, track.zone(), track.act());
            try {
                assertNotNull(shared.level(),
                        track.label() + " (zone " + track.zone() + " act " + track.act() + ") should load");
            } finally {
                shared.dispose();
            }
        }
    }
}
