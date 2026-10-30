package com.openggf.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSidekickTeamEditor {
    @Test
    void parsingMatchesTheGameplayStartupReader() {
        assertEquals(List.of(), SidekickTeamEditor.parse(""));
        assertEquals(List.of("tails", "knuckles"), SidekickTeamEditor.parse(" Tails , ,knuckles "));
    }

    @Test
    void doneJoinsTheLineInFollowOrderAndAddsStopAtTheTeamBound() {
        var saved = new ArrayList<String>();
        var editor = new SidekickTeamEditor("knuckles", "", "tails", saved::add);
        for (int i = 0; i < SidekickTeamEditor.MAX_SIDEKICKS; i++) {
            editor.activate(editor.rows().stream().filter(r -> r.kind() == SidekickTeamEditor.Kind.ADD).findFirst().orElseThrow());
        }
        assertEquals(SidekickTeamEditor.MAX_SIDEKICKS, editor.team().size());
        assertTrue(editor.rows().stream().noneMatch(r -> r.kind() == SidekickTeamEditor.Kind.ADD));
        editor.activate(new SidekickTeamEditor.Row(SidekickTeamEditor.Kind.DEFAULT, -1));
        assertEquals(List.of("tails"), editor.team());
        editor.activate(new SidekickTeamEditor.Row(SidekickTeamEditor.Kind.DONE, -1));
        assertEquals(List.of("tails"), saved);
        assertTrue(editor.closed());
    }

    @Test
    void anUnlistedModCharacterIsKeptUntilChanged() {
        var editor = new SidekickTeamEditor("sonic", "amy,tails", "tails", value -> { });
        assertEquals("amy,tails", editor.value());
        editor.cycle(0, 1);
        assertEquals("sonic,tails", editor.value());
        editor.cycle(0, -1);
        assertEquals("knuckles,tails", editor.value());
    }
}
