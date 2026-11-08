package openggf.timeattack.mp;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHudTextLayout {
    @Test
    void badgesOnlyAppearForOpenCharacterRooms() {
        assertEquals("[S] ", HudTextLayout.characterBadge("OPEN", "sonic"));
        assertEquals("[K] ", HudTextLayout.characterBadge("OPEN", "knuckles"));
        assertEquals("", HudTextLayout.characterBadge("LOCKED", "sonic"));
        assertEquals("[?] ", HudTextLayout.characterBadge("OPEN", null));
    }

    @Test
    void podiumIncludesTopRowsAndLocalResult() {
        List<ControlMessage.StandingsRow> podium = List.of(
                new ControlMessage.StandingsRow(0, "ana", "sonic", 1885, 1, "VERIFIED"),
                new ControlMessage.StandingsRow(1, "bob", "tails", 1990, 2, "PENDING"));
        List<String> lines = HudTextLayout.podiumLines(podium, 4, podium, 9, "OPEN");
        assertEquals("ROUND OVER", lines.getFirst());
        assertTrue(lines.get(1).endsWith(" *"));
        assertTrue(lines.get(2).endsWith(" .."));
        assertTrue(lines.get(1).contains("[S]"));
        assertTrue(lines.getLast().startsWith("YOU: #4"));
        assertEquals("YOU: no time",
                HudTextLayout.podiumLines(podium, -1, podium, 9, "OPEN").getLast());
    }

    @Test
    void voteTextNumbersOptionsCountsAndResult() {
        List<String> lines = HudTextLayout.voteLines(List.of("s3k:0:1", "s3k:1:0"),
                List.of(new ControlMessage.VoteCount("s3k:0:1", 2)), 12_400,
                key -> "LBL " + key);
        assertTrue(lines.getFirst().contains("VOTE 1-3 (12s)"));
        assertTrue(lines.get(1).startsWith("1 LBL s3k:0:1"));
        assertTrue(lines.get(1).contains("2 votes"));
        assertEquals("NEXT: LBL s3k:1:0",
                HudTextLayout.voteResultLine("s3k:1:0", key -> "LBL " + key));
    }

    @Test
    void plainRowsCarryARightAlignedSuffixInItsOwnColour() {
        HudTextLayout.Line line = HudTextLayout.Line.split("P4/37", HudTextLayout.MUTED, "UNVERIFIED", HudTextLayout.AMBER);
        assertEquals("P4/37 UNVERIFIED", line.plain());
        assertEquals(com.openggf.mods.ui.CompactFont.width("P4/37", 1) + 6
                + com.openggf.mods.ui.CompactFont.width("UNVERIFIED", 1), line.width());
        HudTextLayout.Panel panel = HudTextLayout.panel(320, 4, 140, List.of(line), HudTextLayout.ACCENT);
        assertEquals("UNVERIFIED", panel.lines().getFirst().suffix(), "kept while it fits");
        HudTextLayout.Panel narrow = HudTextLayout.panelAt(0, 4, 60, List.of(line), HudTextLayout.ACCENT);
        assertEquals("", narrow.lines().getFirst().suffix(), "a suffix that cannot fit is left out, not overlapped");
    }

    @Test
    void romClockMarksDrawInOneCellEach() {
        List<int[]> fills = new java.util.ArrayList<>();
        com.openggf.mods.ui.PixelCanvas canvas = new com.openggf.mods.ui.PixelCanvas() {
            public int width() { return 320; }
            public int height() { return 224; }
            public void fill(int x, int y, int w, int h, int argb) { fills.add(new int[] {x, y, w, h}); }
        };
        HudTextLayout.compact(canvas, "0'21\"50", 10, 20, HudTextLayout.WHITE);
        // The seconds mark sits in the fifth cell (x 34..38) and draws no '?' fallback.
        assertTrue(fills.stream().anyMatch(fill -> fill[0] >= 34 && fill[0] + fill[2] <= 39 && fill[1] == 20));
        assertTrue(fills.stream().noneMatch(fill -> fill[0] >= 34 && fill[0] < 39 && fill[1] > 22),
                "two short strokes at the top of the cell only");
        fills.clear();
        HudTextLayout.compact(canvas, "" + HudTextLayout.SWAP_MARK, 0, 0, HudTextLayout.WHITE);
        assertTrue(fills.stream().allMatch(fill -> fill[0] >= 0 && fill[0] + fill[2] <= 5 && fill[1] < 7));
        assertEquals(6, fills.size(), "two arrows: heads and shafts");
    }
}
