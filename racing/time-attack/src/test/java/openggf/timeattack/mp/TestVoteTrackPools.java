package openggf.timeattack.mp;

import openggf.timeattack.TimeAttackTrackCatalog;
import openggf.timeattack.VotePool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hosted vote pools: the unchanged defaults and the validation every pool passes before a room exists. */
class TestVoteTrackPools {
    @Test
    void defaultPoolsAreTheUnchangedCuratedLists() {
        assertEquals(List.of("s1:0:0", "s1:0:1", "s1:1:0", "s1:1:1", "s1:2:0", "s1:2:1", "s1:3:0",
                "s1:3:1", "s1:4:0", "s1:4:1", "s1:5:0"), VoteTrackPools.forGame("s1"));
        assertEquals(List.of("s2:0:0", "s2:1:0", "s2:2:0", "s2:3:0", "s2:4:0", "s2:5:0", "s2:6:0",
                "s2:7:0", "s2:7:1"), VoteTrackPools.forGame("s2"));
        assertEquals(List.of("s3k:0:0", "s3k:1:0", "s3k:2:0", "s3k:3:0", "s3k:5:0", "s3k:7:0"),
                VoteTrackPools.forGame("s3k"));
        for (String game : new String[] {"s1", "s2", "s3k"}) {
            assertTrue(VoteTrackPools.rejection(game, VoteTrackPools.forGame(game), null).isEmpty(), game);
        }
    }

    @Test
    void everyEligibleActOfAGameMakesAValidPool() {
        for (String game : new String[] {"s1", "s2", "s3k"}) {
            List<String> all = TimeAttackTrackCatalog.eligibleFor(game).stream()
                    .map(TimeAttackTrackCatalog.Track::key).toList();
            assertTrue(all.size() <= VoteTrackPools.MAX_KEYS, game + " must fit one room");
            assertEquals(Optional.empty(), VoteTrackPools.rejection(game, all, null), game);
        }
    }

    @Test
    void malformedForeignIneligibleAndOversizedPoolsAreRejectedWithAReason() {
        List<String> base = List.of("s1:0:0", "s1:0:1", "s1:0:2");
        assertEquals(Optional.of("The vote pool is empty"), VoteTrackPools.rejection("s1", List.of(), null));
        assertEquals(Optional.of("The vote pool needs at least 3 acts"),
                VoteTrackPools.rejection("s1", List.of("s1:0:0", "s1:0:1"), null));
        assertEquals(Optional.of("Malformed vote track \"s1:zero:0\" (expected game:zone:act)"),
                VoteTrackPools.rejection("s1", List.of("s1:0:0", "s1:0:1", "s1:zero:0"), null));
        assertEquals(Optional.of("Vote track s2:0:0 is not a S1 act"),
                VoteTrackPools.rejection("s1", List.of("s1:0:0", "s1:0:1", "s2:0:0"), null));
        assertEquals(Optional.of("Vote track s1:5:2 is not an act Time Attack can finish"),
                VoteTrackPools.rejection("s1", List.of("s1:0:0", "s1:0:1", "s1:5:2"), null),
                "SBZ3 ends in a restart into FZ with no tally");
        assertEquals(Optional.of("GREEN HILL 1 is in the vote pool twice"),
                VoteTrackPools.rejection("s1", List.of("s1:0:0", "s1:0:1", "s1:0:0"), null));
        List<String> big = new ArrayList<>();
        for (int i = 0; i < 33; i++) big.add("s1:0:" + (i % 3));
        assertEquals(Optional.of("The vote pool has 33 acts; rooms carry at most 32"),
                VoteTrackPools.rejection("s1", big, null));
        assertTrue(VoteTrackPools.rejection("s1", base, "sonic").isEmpty());
        assertEquals(Optional.of("GREEN HILL 1 cannot be raced as TAILS"),
                VoteTrackPools.rejection("s1", base, "tails"));
    }

    @Test
    void savedPoolsBecomeTheirGamesProtocolKeys() {
        assertEquals(List.of("s2:0:1", "s2:7:2"),
                VoteTrackPools.keysOf("s2", new VotePool("Bosses", List.of("0:1", "7:2"))));
    }
}
