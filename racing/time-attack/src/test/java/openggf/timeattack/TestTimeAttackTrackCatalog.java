package openggf.timeattack;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestTimeAttackTrackCatalog {
    @Test
    void everyGameHasTracksAndAllAreLabelled() {
        for (String game : new String[] {"s1", "s2", "s3k"}) {
            var tracks = TimeAttackTrackCatalog.tracksFor(game);
            assertFalse(tracks.isEmpty(), game);
            for (var t : tracks) {
                assertEquals(game, t.gameId());
                assertFalse(t.label().isBlank());
                assertFalse(t.characters().isEmpty());
            }
        }
    }

    @Test
    void unknownGameYieldsEmptyList() {
        assertTrue(TimeAttackTrackCatalog.tracksFor("nope").isEmpty());
        assertTrue(TimeAttackTrackCatalog.eligibleFor("nope").isEmpty());
        assertTrue(TimeAttackTrackCatalog.eligibleFor(null).isEmpty());
    }

    @Test
    void s3kOffersAllThreeCharacters() {
        assertTrue(TimeAttackTrackCatalog.tracksFor("s3k").get(0).characters()
                .containsAll(java.util.List.of("sonic", "tails", "knuckles")));
    }

    @Test
    void eligibleCatalogContainsTheCuratedListAndIsWellFormed() {
        for (String game : new String[] {"s1", "s2", "s3k"}) {
            List<TimeAttackTrackCatalog.Track> eligible = TimeAttackTrackCatalog.eligibleFor(game);
            assertTrue(eligible.containsAll(TimeAttackTrackCatalog.tracksFor(game)), game);
            assertTrue(eligible.size() <= VotePool.MAX_ACTS, game + " fits one room's pool");
            Set<String> keys = new HashSet<>();
            Set<String> labels = new HashSet<>();
            for (TimeAttackTrackCatalog.Track track : eligible) {
                assertTrue(keys.add(track.key()), "duplicate " + track.key());
                assertTrue(labels.add(track.label()), "duplicate label " + track.label());
                assertFalse(track.characters().isEmpty(), track.key());
                assertEquals(track, TimeAttackTrackCatalog.forKey(track.key()).orElseThrow());
            }
        }
        assertEquals(17, TimeAttackTrackCatalog.eligibleFor("s1").size());
        assertEquals(17, TimeAttackTrackCatalog.eligibleFor("s2").size());
        assertEquals(22, TimeAttackTrackCatalog.eligibleFor("s3k").size());
    }

    @Test
    void bossAndCapsuleActsAreEligibleButActsWithoutATallyAreNot() {
        for (String key : List.of("s1:0:2", "s1:3:2", "s1:5:1", "s2:0:1", "s2:7:2", "s3k:0:1",
                "s3k:6:1", "s3k:22:0", "s3k:10:0", "s3k:11:0")) {
            assertTrue(TimeAttackTrackCatalog.isEligibleKey(key), key);
        }
        // S1 SBZ3 (restart into FZ) and FZ (ending); S2 SCZ, WFZ, DEZ; S3K LRZ2, SSZ act 2,
        // DEZ2, DDZ, HPZ (zone 22 act 1) and the Death Egg boss act.
        for (String key : List.of("s1:5:2", "s1:6:0", "s2:8:0", "s2:9:0", "s2:10:0", "s3k:9:1",
                "s3k:10:1", "s3k:11:1", "s3k:12:0", "s3k:22:1", "s3k:23:0", "s3k:1:2", "s3k:x:0", "s3k:0")) {
            assertFalse(TimeAttackTrackCatalog.isEligibleKey(key), key);
        }
    }

    @Test
    void knucklesRacesOnlyTheActsHisRouteReaches() {
        for (String key : List.of("s3k:22:0", "s3k:10:0", "s3k:11:0")) {
            List<String> characters = TimeAttackTrackCatalog.forKey(key).orElseThrow().characters();
            assertEquals(List.of("sonic", "tails"), characters, key);
        }
        assertTrue(TimeAttackTrackCatalog.forKey("s3k:9:0").orElseThrow().characters().contains("knuckles"));
    }
}
