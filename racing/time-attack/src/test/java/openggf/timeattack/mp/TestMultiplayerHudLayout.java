package openggf.timeattack.mp;

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
            assertEquals((width - 320) / 2 + HudTextLayout.NATIVE_LABEL_X - HudTextLayout.TEXT_INSET, clock.x(),
                    "the best/split panel's text lines up with the native HUD labels");
            assertTrue(clock.y() >= 40, "below the native TIME and RINGS rows");
            assertTrue(room.x() >= 120, "stock time/rings must remain visible");
            assertEquals(4, room.y(), "the room panel takes the top-right corner");
            assertTrue(clock.x() + clock.width() <= room.x() || clock.y() >= room.y() + room.height(),
                    "clock and room must not overlap");
            assertTrue(room.lines().size() <= 5, "header, position, and at most three nearby rows");
            assertTrue(room.lines().stream().anyMatch(line -> line.text().startsWith("129. ")));
            assertFalse(room.lines().stream().anyMatch(line -> line.text().startsWith("1. ")),
                    "normal play must not show the top-ten wall");
        }
    }

    @Test
    void runningColumnLeavesMostOfTheNativeViewClear() {
        var clock = TimeAttackHud.layout(320, 224, new TimeAttackHudState(true, 3210, 3500, -120, false, false));
        var ranked = MultiplayerHudRenderer.layout(320, 224, state("RUNNING", 129), 8192, false);
        var unranked = MultiplayerHudRenderer.layout(320, 224, state("RUNNING", -1), 8192, false);
        for (var panel : List.of(clock, ranked, unranked)) {
            assertTrue(panel.width() <= HudTextLayout.PANEL_WIDTH, "right column stays under half the view");
        }
        assertTrue(clock.width() < ranked.width(), "short clock text shrinks its panel");
        // Name rows use the name font's 13-pixel line, so the column ends a few pixels lower.
        assertTrue(ranked.y() + ranked.height() <= 100, "both panels end in the top ~45% of the view");
        assertEquals(4, unranked.lines().size(), "header, position, one rival ahead and one behind");
        // Standings keep the time as a right-aligned suffix, whatever the name length.
        var standings = ranked.lines().stream().filter(HudTextLayout.Line::hasName).toList();
        assertEquals(3, standings.size());
        standings.forEach(line -> {
            assertEquals("0:53.50", line.suffix(), line.plain());
            assertTrue(line.name().endsWith("…"), "long names are cut with an ellipsis: " + line.name());
        });
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
        assertTrue(panel.lines().stream().anyMatch(line -> line.plain().endsWith("AHEAD")));
        assertTrue(panel.lines().stream().anyMatch(line -> line.plain().endsWith("BEHIND")));
        assertFalse(panel.lines().stream().anyMatch(line -> line.plain().matches("[0-9]+[.] .*")));
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
        var row = HudTextLayout.panel(320, 0, 176, List.of(HudTextLayout.Line.named("129. ",
                source.standings().get(128).displayName(), HudTextLayout.WHITE, "0:53.50", HudTextLayout.WHITE)),
                HudTextLayout.ACCENT).lines().getFirst();
        assertEquals("129. ", row.text());
        assertEquals("0:53.50", row.suffix());
        assertTrue(row.width() <= 164, row.plain());
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

    @Test
    void namesTakeTheirPlayersColoursAndAnyScriptFits() {
        var source = state("RUNNING", 2);
        List<ControlMessage.StandingsRow> rows = List.of(
                new ControlMessage.StandingsRow(7, "ブラック・ノヴァ", "sonic", 3000, 1, "VERIFIED"),
                new ControlMessage.StandingsRow(8, "Nguyễn Thị Hoa", "tails", 3100, 2, "VERIFIED"),
                new ControlMessage.StandingsRow(9, "Ωμέγα", "knuckles", 3200, 3, "VERIFIED"));
        var state = new MultiplayerHudState(true, "RUNNING", 60_000, 0, rows, List.of(), false, null, 3,
                source.farPlayers(), "OPEN", List.of(), List.of(), 0, null, rows, 2,
                java.util.Map.of(7, 0xFFFF7070, 9, 0xFF70FF70));
        for (int width : new int[] {320, 800}) {
            var panel = MultiplayerHudRenderer.layout(width, 224, state, 8192, false);
            assertContained(panel, width);
            assertTrue(panel.width() <= HudTextLayout.PANEL_WIDTH);
            var named = panel.lines().stream().filter(HudTextLayout.Line::hasName).toList();
            // A 140-pixel column keeps rank and time; long names are cut between graphemes.
            List<String> full = List.of("ブラック・ノヴァ", "Nguyễn Thị Hoa", "Ωμέγα");
            for (int i = 0; i < 3; i++) {
                String shown = named.get(i).name();
                assertTrue(shown.equals(full.get(i))
                        || (shown.endsWith("…") && full.get(i).startsWith(shown.substring(0, shown.length() - 1))
                        && shown.length() > 3), shown);
            }
            assertEquals("Ωμέγα", named.get(2).name());
            assertEquals(List.of(0xFFFF7070, HudTextLayout.WHITE, 0xFF70FF70),
                    named.stream().map(HudTextLayout.Line::nameColor).toList(),
                    "stock players use the row colour; the local row is white");
        }
        var podium = MultiplayerHudRenderer.layout(320, 224,
                new MultiplayerHudState(true, "ROUND_END", 0, 0, rows, List.of(), false, null, 3,
                        List.of(), "OPEN", List.of(), List.of(), 0, null, rows, 2, java.util.Map.of(9, 0xFF70FF70)),
                8192, true);
        assertContained(podium, 320);
        assertEquals(0xFF70FF70, podium.lines().getLast().nameColor());
    }

    private static void assertContained(HudTextLayout.Panel panel, int width) {
        assertTrue(panel.x() >= 0 && panel.y() >= 0);
        assertTrue(panel.x() + panel.width() <= width);
        assertTrue(panel.y() + panel.height() <= 224);
        for (var line : panel.lines()) {
            assertTrue(line.width() <= panel.width() - 12, () -> "text escapes panel: " + line.plain());
        }
        assertEquals(4 + panel.lines().stream().mapToInt(HudTextLayout.Line::height).sum(), panel.height());
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

    @Test
    void theProgressBarSpansTheScreenAndAHurtPlayersMarkerFlashesOnTop() {
        var players = List.of(new com.openggf.net.client.RemoteGhostRegistry.FarPlayer(3, "PEER", "tails", 50, 10, -1),
                new com.openggf.net.client.RemoteGhostRegistry.FarPlayer(4, "HURT", "sonic", 50, 10, -1));
        for (int width : new int[]{320, 424}) {
            for (int tick : new int[]{0, 3}) {
                var state = new MultiplayerHudState(true, "RUNNING", 60_000, 0, List.of(), List.of(), false, null, 3,
                        players, "OPEN", List.of(), List.of(), 0, null, List.of(), 2,
                        java.util.Map.of(3, 0xFF00FF00, 4, 0xFF0000FF), 0, tick, java.util.Map.of(4, 20));
                List<int[]> fills = new ArrayList<>();
                MultiplayerHudRenderer.drawProgress(new com.openggf.mods.ui.PixelCanvas() {
                    public int width() { return width; }
                    public int height() { return 224; }
                    public void fill(int x, int y, int w, int h, int argb) { fills.add(new int[]{x, y, w, h, argb}); }
                }, state, 6400, 1600);
                int[] bar = fills.getFirst();
                assertEquals(4, bar[0], "four pixels from the left edge, where the lives counter was");
                assertEquals(width - 8, bar[2], "the whole screen width");
                assertEquals(13, bar[3], "taller than before");
                int peer = indexOfColour(fills, 0xFF00FF00);
                int hurt = tick == 0 ? indexOfColour(fills, 0xFFFF3B3B) : indexOfColour(fills, 0xFF0000FF);
                assertTrue(peer >= 0 && hurt > peer, "the hurt marker draws after its neighbour");
                assertEquals(tick == 0, indexOfColour(fills, 0xFF0000FF) < 0, "the marker strobes red");
            }
        }
    }

    @Test
    void racersAtTheSamePlaceStackSoTheColumnCountsThemAndNobodyMoves() {
        // {x on the track, slot, local}
        List<int[]> pack = List.of(new int[]{101, 4, 0}, new int[]{100, 2, 0}, new int[]{102, 3, 1},
                new int[]{103, 1, 0}, new int[]{150, 5, 0});
        var placed = MultiplayerHudRenderer.stack(pack);
        java.util.Map<Integer, int[]> bySlot = new java.util.HashMap<>();
        for (int[] p : placed) bySlot.put(pack.get(p[0])[1], p);
        int column = 100 / MultiplayerHudRenderer.STACK_BIN * MultiplayerHudRenderer.STACK_BIN;
        for (int slot : new int[]{1, 2, 3, 4}) {
            assertEquals(column, bySlot.get(slot)[1], "slot " + slot + " stays at its own place, within one column");
        }
        assertEquals(0, bySlot.get(3)[2], "the local player is at the bottom of their column");
        assertEquals(List.of(1, 2, 3), List.of(bySlot.get(1)[2], bySlot.get(2)[2], bySlot.get(4)[2]),
                "the others stack above in slot order: the column is four high for four racers");
        assertEquals(150 / MultiplayerHudRenderer.STACK_BIN * MultiplayerHudRenderer.STACK_BIN, bySlot.get(5)[1]);
        assertEquals(0, bySlot.get(5)[2], "a racer alone is a single segment where they are");
    }

    @Test
    void aBigRoomsPackIsAShortColumnUnderItsCount() {
        List<com.openggf.net.client.RemoteGhostRegistry.FarPlayer> crowd = new ArrayList<>();
        for (int slot = 1; slot <= 40; slot++) {
            crowd.add(new com.openggf.net.client.RemoteGhostRegistry.FarPlayer(slot, "B" + slot, "sonic", 10, 10, -1));
        }
        var state = new MultiplayerHudState(true, "RUNNING", 60_000, 0, List.of(), List.of(), false, null, 41,
                crowd, "OPEN", List.of(), List.of(), 0, null, List.of(), 2, java.util.Map.of(), 0, 0,
                java.util.Map.of(17, 30));
        List<int[]> fills = new ArrayList<>();
        MultiplayerHudRenderer.drawProgress(new com.openggf.mods.ui.PixelCanvas() {
            public int width() { return 320; }
            public int height() { return 224; }
            public void fill(int x, int y, int w, int h, int argb) { fills.add(new int[]{x, y, w, h, argb}); }
        }, state, 6400, 640);
        int barTop = 224 - 13 - 3;
        int top = fills.stream().mapToInt(f -> f[1]).min().orElseThrow();
        assertTrue(top >= barTop - MultiplayerHudRenderer.STACK_MAX * 4 - 12,
                "the 41-racer column stays short: top " + top);
        // 41 racers share the column (40 rivals and you): you, the hurt racer and three others are drawn.
        var placed = MultiplayerHudRenderer.stack(List.of(new int[]{100, 9, 0, 0}, new int[]{100, 3, 0, 1},
                new int[]{100, 2, 1, 0}, new int[]{100, 1, 0, 0}));
        var byLevel = new java.util.TreeMap<Integer, Integer>();
        for (int[] p : placed) byLevel.put(p[2], new int[][]{{100, 9}, {100, 3}, {100, 2}, {100, 1}}[p[0]][1]);
        assertEquals(List.of(2, 3, 1, 9), List.copyOf(byLevel.values()), "you, then anyone hurt, then by slot");
    }

    private static int indexOfColour(List<int[]> fills, int argb) {
        for (int i = 0; i < fills.size(); i++) if (fills.get(i)[4] == argb) return i;
        return -1;
    }
}
