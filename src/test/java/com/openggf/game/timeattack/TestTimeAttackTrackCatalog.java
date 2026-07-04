package com.openggf.game.timeattack;

import org.junit.jupiter.api.Test;

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
    }

    @Test
    void s3kOffersAllThreeCharacters() {
        assertTrue(TimeAttackTrackCatalog.tracksFor("s3k").get(0).characters()
                .containsAll(java.util.List.of("sonic", "tails", "knuckles")));
    }
}
