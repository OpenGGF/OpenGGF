package com.openggf.game.timeattack.mp;

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
}
