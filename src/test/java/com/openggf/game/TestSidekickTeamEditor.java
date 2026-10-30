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
        var editor = SidekickTeamEditor.directLaunch("knuckles", "", "tails", saved::add);
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
        var editor = SidekickTeamEditor.directLaunch("sonic", "amy,tails", "tails", value -> { });
        assertEquals("amy,tails", editor.value());
        editor.cycle(0, 1);
        assertEquals("sonic,tails", editor.value());
        editor.cycle(0, -1);
        assertEquals("knuckles,tails", editor.value());
    }

    @Test
    void aSaveScreenTeamLeadsWithItsEditablePlayerAndHasNoStockValue() {
        var saved = new ArrayList<String>();
        var editor = SidekickTeamEditor.saveScreenTeam("Team 1", "knuckles,tails", saved::add);
        assertEquals(SidekickTeamEditor.Kind.MAIN, editor.rows().getFirst().kind());
        assertTrue(editor.rows().stream().noneMatch(r -> r.kind() == SidekickTeamEditor.Kind.DEFAULT));
        editor.cycle(-1, 1); // Knuckles wraps to Sonic.
        editor.activate(new SidekickTeamEditor.Row(SidekickTeamEditor.Kind.SLOT, 0));
        editor.perform(SidekickTeamEditor.Action.REMOVE);
        editor.activate(new SidekickTeamEditor.Row(SidekickTeamEditor.Kind.DONE, -1));
        assertEquals(List.of("sonic"), saved, "a team may play alone");
    }

    @Test
    void extraTeamsKeepTheSaveScreenFormatAndReadAsNames() {
        assertEquals(List.of("sonic,knuckles", "knuckles,tails"), ExtraTeamsEditor.parse("sonic, knuckles;;Knuckles,tails;"));
        assertEquals("Sonic + Knuckles; Tails alone", ExtraTeamsEditor.summary("sonic,knuckles;tails"));
        var saved = new ArrayList<String>();
        var editor = new ExtraTeamsEditor("sonic,knuckles", saved::add);
        editor.activate(new ExtraTeamsEditor.Row(ExtraTeamsEditor.Kind.ADD, -1));
        assertNotNull(editor.child(), "Add team opens the new team");
        editor.child().activate(new SidekickTeamEditor.Row(SidekickTeamEditor.Kind.DONE, -1));
        assertEquals(List.of("sonic,knuckles", "sonic,tails"), editor.teams());
        editor.activate(new ExtraTeamsEditor.Row(ExtraTeamsEditor.Kind.TEAM, 1));
        editor.perform(ExtraTeamsEditor.Action.MOVE_EARLIER);
        editor.activate(new ExtraTeamsEditor.Row(ExtraTeamsEditor.Kind.DONE, -1));
        assertEquals(List.of("sonic,tails;sonic,knuckles"), saved);
    }
}
