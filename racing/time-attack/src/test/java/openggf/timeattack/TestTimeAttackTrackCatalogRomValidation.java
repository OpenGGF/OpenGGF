package openggf.timeattack;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.SonicGame;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-gated guard for the eligible {@link TimeAttackTrackCatalog} (which contains the curated
 * default list): the label/character checks in {@link TestTimeAttackTrackCatalog} cannot catch a
 * wrong zone/act int (the S1/S2 catalog entries were originally seeded with the wrong ints
 * &mdash; see the catalog's Javadoc). Each eligible entry is loaded headless via
 * {@link SharedLevel}, the harness {@code TestS2Ehz1Headless} / {@code TestLevelEntryPathsHeadless}
 * (S2) and the S1 headless zone tests use, and must land on the ROM zone/act its label names.
 *
 * <p>For S1 and S2 the act's tally object is placed in the ROM object layout, so the loaded layout
 * must contain it: the signpost ({@code $0D}) for a signpost act, the prison capsule
 * ({@code $3E}) for a boss act. S3K spawns its signposts and capsules from minibosses and bosses
 * at run time (see {@code s3kEligible()}), so S3K entries are checked for zone/act only.
 *
 * <p>One {@code @Test} per game so a missing ROM skips only that game's validation via
 * {@link Assumptions#assumeTrue}.
 */
class TestTimeAttackTrackCatalogRomValidation {
    private static final int SIGNPOST_ID = 0x0D;
    private static final int PRISON_ID = 0x3E;

    /** Engine zone -> ROM zone byte. S1: id_GHZ=0, id_LZ=1, id_MZ=2, id_SLZ=3, id_SYZ=4, id_SBZ=5. */
    private static final Map<Integer, Integer> S1_ROM_ZONES = Map.of(0, 0, 1, 2, 2, 4, 3, 1, 4, 3, 5, 5);
    /** Sonic2ZoneConstants ROM_ZONE_*; MTZ act 3 is its own ROM zone (metropolis_zone_2). */
    private static final Map<Integer, Integer> S2_ROM_ZONES = Map.of(
            0, 0x00, 1, 0x0D, 2, 0x0F, 3, 0x0C, 4, 0x07, 5, 0x0B, 6, 0x0A, 7, 0x04);

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
        assertTrue(TimeAttackTrackCatalog.eligibleFor(gameId).containsAll(TimeAttackTrackCatalog.tracksFor(gameId)));
        for (TimeAttackTrackCatalog.Track track : TimeAttackTrackCatalog.eligibleFor(gameId)) {
            SharedLevel shared = SharedLevel.load(game, track.zone(), track.act());
            String what = track.label() + " (zone " + track.zone() + " act " + track.act() + ")";
            try {
                assertNotNull(shared.level(), what + " should load");
                var levels = GameServices.level();
                switch (gameId) {
                    case "s1" -> {
                        assertEquals(S1_ROM_ZONES.get(track.zone()), levels.getRomZoneId(), what);
                        assertEquals(track.act(), levels.getRomActId(), what);
                        assertTallyObjectPlaced(shared, track, what);
                    }
                    case "s2" -> {
                        boolean mtz3 = track.zone() == 7 && track.act() == 2;
                        assertEquals(mtz3 ? 0x05 : S2_ROM_ZONES.get(track.zone()), levels.getRomZoneId(), what);
                        if (!mtz3) assertEquals(track.act(), levels.getRomActId(), what);
                        assertTallyObjectPlaced(shared, track, what);
                    }
                    default -> {
                        assertEquals(track.zone(), levels.getRomZoneId(), what);
                        assertEquals(track.act(), levels.getRomActId(), what);
                    }
                }
            } finally {
                shared.dispose();
            }
        }
    }

    private static void assertTallyObjectPlaced(SharedLevel shared, TimeAttackTrackCatalog.Track track, String what) {
        int id = track.finish() == TimeAttackTrackCatalog.Finish.SIGNPOST ? SIGNPOST_ID : PRISON_ID;
        boolean placed = shared.level().getObjects().stream().mapToInt(ObjectSpawn::objectId).anyMatch(o -> o == id);
        assertTrue(placed, what + " should place its tally object $" + Integer.toHexString(id).toUpperCase());
    }
}
