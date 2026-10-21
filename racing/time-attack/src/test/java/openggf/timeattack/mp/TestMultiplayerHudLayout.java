package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.net.client.RemoteGhostRegistry;
import com.openggf.net.protocol.ControlMessage;
import openggf.timeattack.TimeAttackHud;
import openggf.timeattack.TimeAttackHudState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TestMultiplayerHudLayout {
    @Test
    void nativeAndWidePanelsKeepStockHudClearAndContainAllText() {
        for (int width : new int[]{320, 424, 640, 800}) {
            var clock = TimeAttackHud.layout(width, 224,
                    new TimeAttackHudState(true, 3210, 3500, -120, false, false));
            var room = MultiplayerHudRenderer.layout(width, 224, state("RUNNING", 129), 8192, false);
            assertContained(clock, width);
            assertContained(room, width);
            assertTrue(clock.x() >= 120, "stock score/time/rings must remain visible");
            assertTrue(room.x() >= 120, "stock score/time/rings must remain visible");
            assertTrue(clock.y() + clock.height() <= room.y(), "clock and room must not overlap");
            assertTrue(room.lines().size() <= 5, "header, position, and at most three nearby rows");
            assertTrue(room.lines().stream().anyMatch(line -> line.text().startsWith("129. ")));
            assertFalse(room.lines().stream().anyMatch(line -> line.text().startsWith("1. ")),
                    "normal play must not show the top-ten wall");
        }
    }

    @Test
    void finishRoundAndVoteStayBelowTheActClearCardAndAboveTheRunStrip() {
        for (int width : new int[]{320, 800}) {
            var clock = TimeAttackHud.layout(width, 224,
                    new TimeAttackHudState(true, 3210, 3500, -120, true, true));
            assertEquals(1, clock.lines().size());
            for (String phase : List.of("RUNNING", "ROUND_END", "VOTE")) {
                var room = MultiplayerHudRenderer.layout(width, 224, state(phase, 129), 8192, true);
                assertContained(clock, width);
                assertContained(room, width);
                assertTrue(room.y() >= 150, "stock act-clear title and bonus rows need the centre");
                assertTrue(room.y() + room.height() <= clock.y(), "room and finish strips must not collide");
            }
        }
    }

    @Test
    void positionUsesLiveObservationsAndDoesNotClaimOfficialRank() {
        var state = state("RUNNING", -1);
        assertEquals("AHEAD 1 / 2 SEEN", MultiplayerHudRenderer.progressPosition(state, 8192));
        var panel = MultiplayerHudRenderer.layout(320, 224, state, 8192, false);
        assertTrue(panel.lines().stream().anyMatch(line -> line.text().endsWith("AHEAD")));
        assertTrue(panel.lines().stream().anyMatch(line -> line.text().endsWith("BEHIND")));
        assertFalse(panel.lines().stream().anyMatch(line -> line.text().matches("[0-9]+[.] .*")));
        assertTrue(MultiplayerHudRenderer.layout(320, 224, state("RUNNING", 129), 8192, true)
                .lines().getFirst().text().contains("YOU 129/256"));
    }

    @Test
    void idlePlayerBetweenAttemptsIsSeenButNeverAhead() {
        var source = state("RUNNING", -1);
        // Slot 2 reset (or finished) and is reloading: IDLE at the cell where its attempt ended.
        var players = List.of(
                new RemoteGhostRegistry.FarPlayer(1, "RUNNER", "sonic", 50, 0, 1),
                new RemoteGhostRegistry.FarPlayer(2, "RELOADING", "sonic", 400, 0, 0),
                new RemoteGhostRegistry.FarPlayer(3, "FINISHER", "sonic", 400, 0, 2));
        var state = new MultiplayerHudState(true, "RUNNING", 60_000, 0, source.standings(),
                List.of(), false, null, 4, players, "OPEN", List.of(), List.of(), 0, null,
                List.of(), -1);
        assertEquals("PROGRESS EST 2/4", MultiplayerHudRenderer.progressPosition(state, 8192));
    }

    @Test
    void denseRosterGivesEstimatedProgressAndLongNamesPreserveRankAndTime() {
        var source = state("RUNNING", -1);
        List<RemoteGhostRegistry.FarPlayer> players = IntStream.range(1, 256)
                .mapToObj(slot -> new RemoteGhostRegistry.FarPlayer(slot, "RACER " + slot,
                        "sonic", slot, 0, 1)).toList();
        var dense = new MultiplayerHudState(true, "RUNNING", 60_000, 0,
                source.standings(), List.of(), false, null, 256, players, "OPEN", List.of(),
                List.of(), 0, null, List.of(), -1);
        assertEquals("PROGRESS EST 128/256", MultiplayerHudRenderer.progressPosition(dense, 8192));
        String row = HudTextLayout.compactStanding(source.standings().get(128), 164);
        assertTrue(row.startsWith("129. "));
        assertTrue(row.endsWith("0:53.50"));
        assertTrue(CompactFont.width(row, 1) <= 164);
    }

    @Test
    void inactiveHudHasNoPanelsAndSaveNoticeSurvivesFinishedCompaction() {
        assertNull(TimeAttackHud.layout(320, 224, TimeAttackHudState.inactive()));
        assertNull(MultiplayerHudRenderer.layout(320, 224, null, 0, false));
        var panel = TimeAttackHud.layout(320, 224,
                new TimeAttackHudState(true, 3210, -1, Integer.MIN_VALUE, true, false, "ASSISTED - NOT SAVED"));
        assertTrue(panel.lines().getFirst().text().contains("NOT SAVED"));
        assertContained(panel, 320);
    }

    private static void assertContained(HudTextLayout.Panel panel, int width) {
        assertTrue(panel.x() >= 0 && panel.y() >= 0);
        assertTrue(panel.x() + panel.width() <= width);
        assertTrue(panel.y() + panel.height() <= 224);
        for (var line : panel.lines()) {
            assertTrue(CompactFont.width(line.text(), 1) <= panel.width() - 12,
                    () -> "text escapes panel: " + line.text());
        }
    }

    private static MultiplayerHudState state(String phase, int localRank) {
        List<ControlMessage.StandingsRow> rows = new ArrayList<>();
        for (int rank = 1; rank <= 256; rank++) {
            rows.add(new ControlMessage.StandingsRow(rank - 1,
                    "AN EXTREMELY LONG RACER DISPLAY NAME " + rank, "sonic", 3210, rank, "PENDING"));
        }
        return new MultiplayerHudState(true, phase, 762_000, 0, rows, List.of(), false, null,
                256, List.of(new RemoteGhostRegistry.FarPlayer(3, "A LONG PLAYER NAME AHEAD", "sonic", 200, 0, 1),
                new RemoteGhostRegistry.FarPlayer(4, "A LONG PLAYER NAME BEHIND", "tails", 50, 0, -1)),
                "OPEN", List.of("s1:0:0", "s1:0:1", "s3k:0:0"),
                List.of(new ControlMessage.VoteCount("s1:0:0", 255)), 12_000, null, rows.subList(0, 3), localRank);
    }
}
