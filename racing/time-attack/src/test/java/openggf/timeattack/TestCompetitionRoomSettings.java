package openggf.timeattack;

import com.openggf.game.arena.MonitorRule;
import com.openggf.mods.run.TrackKind;
import openggf.timeattack.mp.VoteTrackPools;
import org.junit.jupiter.api.Test;
import java.util.List;
import static openggf.timeattack.CompetitionRoomSettings.Mode.*;
import static com.openggf.game.arena.MonitorRule.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCompetitionRoomSettings {
    @Test void defaultsAndChoices() {
        assertEquals(new CompetitionRoomSettings(TIME_ATTACK, NORMAL, 60, 10), CompetitionRoomSettings.defaults());
        for (int cutoff : CompetitionRoomSettings.cutoffChoices()) {
            for (int start : CompetitionRoomSettings.startInChoices()) {
                assertDoesNotThrow(() -> new CompetitionRoomSettings(COMPETITION, OFF, cutoff, start));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new CompetitionRoomSettings(COMPETITION, OFF, 45, 10));
        assertThrows(IllegalArgumentException.class, () -> new CompetitionRoomSettings(COMPETITION, OFF, 60, 3));
        assertThrows(IllegalArgumentException.class, () -> new CompetitionRoomSettings(TIME_ATTACK, OFF, 60, 10));
    }

    @Test void modeEligibilityAndArenaIdentity() {
        assertTrue(TimeAttackTrackCatalog.eligibleFor("s1", COMPETITION).isEmpty());
        assertEquals(TimeAttackTrackCatalog.eligibleFor("s2"), TimeAttackTrackCatalog.eligibleFor("s2", COMPETITION));
        var arenas = TimeAttackTrackCatalog.arenas();
        assertEquals(List.of("AZURE LAKE", "BALLOON PARK", "CHROME GADGET", "DESERT PALACE", "ENDLESS MINE"),
                arenas.stream().map(TimeAttackTrackCatalog.Track::label).toList());
        for (int i = 0; i < 5; i++) {
            var track = arenas.get(i);
            assertEquals("s3k:arena:" + i, track.key());
            assertEquals(track, TimeAttackTrackCatalog.forKey(track.key()).orElseThrow());
            assertEquals(TrackKind.ARENA, track.kind());
            assertEquals(TimeAttackTrackCatalog.Group.ARENAS, track.group());
            assertEquals(5, track.lapCount());
            assertEquals(List.of("sonic", "tails", "knuckles"), track.characters());
            assertFalse(TimeAttackTrackCatalog.eligibleFor("s3k").contains(track));
            assertTrue(TimeAttackTrackCatalog.eligibleFor("s3k", COMPETITION).contains(track));
        }
        assertTrue(TimeAttackTrackCatalog.forKey("s2:arena:0").isEmpty());
        assertTrue(TimeAttackTrackCatalog.forKey("s3k:arena:5").isEmpty());
        var keys = arenas.stream().map(TimeAttackTrackCatalog.Track::key).toList();
        assertTrue(VoteTrackPools.rejection("s3k", keys, null, COMPETITION).isEmpty());
        assertTrue(VoteTrackPools.rejection("s3k", keys, null).isPresent());
        assertDoesNotThrow(() -> new VotePool("Arenas", List.of("arena:0", "arena:1", "arena:2")));
    }

    @Test void legalMonitorIntersection() {
        var s2 = TimeAttackTrackCatalog.eligible("s2", 0, 0).orElseThrow();
        var boss = TimeAttackTrackCatalog.eligible("s2", 0, 1).orElseThrow();
        var s3 = TimeAttackTrackCatalog.eligible("s3k", 0, 0).orElseThrow();
        assertEquals(List.of(NORMAL, RANDOMISED, OFF), CompetitionRoomSettings.legalMonitors(COMPETITION, List.of(s2)));
        assertEquals(List.of(NORMAL, OFF), CompetitionRoomSettings.legalMonitors(COMPETITION, List.of(s2, boss)));
        assertEquals(List.of(NORMAL, OFF), CompetitionRoomSettings.legalMonitors(COMPETITION, List.of(s3)));
        assertEquals(List.of(COMPETITION_ITEMS, OFF), CompetitionRoomSettings.legalMonitors(COMPETITION, TimeAttackTrackCatalog.arenas()));
        assertEquals(List.of(OFF), CompetitionRoomSettings.legalMonitors(COMPETITION, List.of(s3, TimeAttackTrackCatalog.arenas().getFirst())));
        assertEquals(List.of(NORMAL), CompetitionRoomSettings.legalMonitors(TIME_ATTACK, List.of(s2, boss)));
        assertTrue(CompetitionRoomSettings.legalMonitors(COMPETITION, TimeAttackTrackCatalog.eligibleFor("s1")).isEmpty());
        assertTrue(CompetitionRoomSettings.legalMonitors(TIME_ATTACK, TimeAttackTrackCatalog.arenas()).isEmpty());
        assertFalse(CompetitionRoomSettings.defaults().legalFor(List.of()));
        assertEquals(COMPETITION_ITEMS, CompetitionRoomSettings.defaultsFor(COMPETITION, TimeAttackTrackCatalog.arenas()).monitors());
        assertEquals(RANDOMISED, CompetitionRoomSettings.defaultsFor(COMPETITION, List.of(s2)).monitors(),
                "S2 Competition defaults to the original two-player draw");
        assertEquals(NORMAL, CompetitionRoomSettings.defaultsFor(COMPETITION, List.of(s2, boss)).monitors());
        assertThrows(IllegalArgumentException.class, () -> CompetitionRoomSettings.defaultsFor(COMPETITION, List.of()));
    }
}
