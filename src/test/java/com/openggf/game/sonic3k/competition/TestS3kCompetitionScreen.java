package com.openggf.game.sonic3k.competition;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Literal controller paths through the native menu state machine; no production terrain surrogate. */
class TestS3kCompetitionScreen {
    @Test void modeMenuWrapsFourRowsAndControllerTwoCanChooseGrandPrix() {
        Fixture f = new Fixture();
        f.press(0, 1, 0); assertEquals(3, f.screen.view().modeRow());
        f.press(1, 2, 0); assertEquals(0, f.screen.view().modeRow());
        f.press(1, 8, 0); assertFalse(f.screen.view().itemsEnabled());
        f.press(1, 0, InputActionMasks.ACTION_A);
        assertEquals(S3kCompetitionScreen.Phase.PLAYER_SELECT, f.screen.view().phase());
        assertEquals(2, f.screen.view().participants());
        assertEquals(GameMode.COMPETITION_MENU, f.screen.mode());
        f.press(0, 0, InputActionMasks.ACTION_A);
        assertTrue(f.screen.view().confirmed().getFirst()); assertTrue(f.launches.isEmpty());
        f.press(1, 0, InputActionMasks.ACTION_C);
        assertEquals(GameMode.COMPETITION, f.screen.mode());
        assertEquals(S3kCompetitionSession.Arena.AZURE_LAKE, f.launches.getFirst().config().arena());
        assertFalse(f.launches.getFirst().config().itemsEnabled());
    }

    @Test void characterWheelUsesNativeSeventeenTickRotationAndConfirmIsBlockedDuringIt() {
        Fixture f = new Fixture();
        f.press(1, 0, InputActionMasks.ACTION_A);
        f.press(0, 8, 0);
        assertEquals(1, f.screen.view().characters().getFirst());
        assertEquals(0x45, f.screen.view().wheelAngles().getFirst());
        for (int i = 0; i < 15; i++) f.press(0, 0, InputActionMasks.ACTION_A);
        assertFalse(f.screen.view().confirmed().getFirst());
        f.idle(1); assertEquals(0x95, f.screen.view().wheelAngles().getFirst());
        f.press(0, 0, InputActionMasks.ACTION_A);
        f.press(1, 0, InputActionMasks.ACTION_A);
        assertEquals(List.of(S3kCompetitionPlayer.Character.TAILS, S3kCompetitionPlayer.Character.TAILS),
                f.launches.getFirst().characters());
    }

    @Test void matchRaceUsesTheNativeNonNumericArenaOrderAndClampedCursor() {
        Fixture f = new Fixture();
        f.press(0, 2, 0); f.press(1, 0, InputActionMasks.ACTION_A);
        f.press(0, 0, InputActionMasks.ACTION_A); f.press(1, 0, InputActionMasks.ACTION_C);
        f.idle(10); assertEquals(-0x58, f.screen.view().horizontalScroll());
        f.press(0, 1, 0); assertEquals(0, f.screen.view().arenaRow());
        f.press(1, 2, 0); f.press(1, 2, 0);
        assertEquals(2, f.screen.view().arenaRow()); f.idle(9);
        f.press(0, 0, InputActionMasks.ACTION_A);
        assertEquals(S3kCompetitionSession.Arena.CHROME_GADGET, f.launches.getFirst().config().arena());
        assertEquals(S3kCompetitionSession.Mode.MATCH_RACE, f.launches.getFirst().config().mode());
    }

    @Test void soloGrandPrixCreatesOnlyP1AndSecondStartCanJoinBeforeLock() {
        Fixture f = new Fixture(); f.press(0, 0, InputActionMasks.ACTION_A);
        assertEquals(1, f.screen.view().participants());
        f.screen.update(LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),
                PlayerInputState.of(0, 0, 0, 0, true, true)));
        assertEquals(2, f.screen.view().participants());
        f.press(0, 0, InputActionMasks.ACTION_A); f.press(1, 0, InputActionMasks.ACTION_A);
        assertEquals(2, f.launches.getFirst().config().participantCount());
    }

    @Test void timeAttackIgnoresP2AcceptHasOneNativePlayerAndReturnsToArenaSelection() {
        Fixture f = new Fixture(); f.press(0, 2, 0); f.press(0, 2, 0);
        f.press(1, 0, InputActionMasks.ACTION_A); assertEquals(S3kCompetitionScreen.Phase.MODE, f.screen.view().phase());
        f.press(0, 0, InputActionMasks.ACTION_C); f.press(0, 0, InputActionMasks.ACTION_A); f.idle(10);
        f.press(0, 0, InputActionMasks.ACTION_A);
        assertEquals(1, f.launches.getFirst().config().participantCount());
        assertFalse(f.launches.getFirst().config().itemsEnabled());
        f.screen.requestResults(f.screen.session().result()); f.idle(1);
        assertEquals(GameMode.COMPETITION_RESULTS, f.screen.mode());
        f.idle(30); f.press(1, 0, InputActionMasks.ACTION_A);
        assertEquals(S3kCompetitionScreen.Phase.TIME_ATTACK_RESULTS, f.screen.view().phase());
        f.press(0, 0, InputActionMasks.ACTION_A);
        assertEquals(S3kCompetitionScreen.Phase.ARENA_SELECT, f.screen.view().phase());
        assertTrue(f.savedRecords.isEmpty(), "unfinished result must not write a record");
    }

    @Test void matchRaceFinishReturnsToArenaSelectAndGrandPrixResultHasNativeTwoPages() {
        Fixture match = new Fixture(); match.press(0,2,0); match.press(1,0,1);
        match.press(0,0,1); match.press(1,0,1); match.idle(10); match.press(0,0,1);
        match.screen.requestResults(match.screen.session().result()); match.idle(1);
        assertEquals(S3kCompetitionScreen.Phase.ARENA_SELECT, match.screen.view().phase());
        Fixture gp = new Fixture(); gp.press(1,0,1); gp.press(0,0,1); gp.press(1,0,1);
        gp.screen.requestResults(gp.screen.session().result()); gp.idle(1);
        gp.press(0,0,1); assertEquals(0,gp.screen.view().horizontalScroll(), "native30framehold");
        gp.idle(29); gp.press(0,0,1); gp.idle(16);
        assertEquals(-0x88,gp.screen.view().horizontalScroll());
        gp.press(1,0,InputActionMasks.ACTION_C);
        assertEquals(S3kCompetitionScreen.Phase.MODE,gp.screen.view().phase());
    }

    @Test void exitUsesNativeBAndDoesNotLaunchOrdinaryGameplay() {
        Fixture f = new Fixture(); f.press(1,0,InputActionMasks.ACTION_B);
        assertEquals(NativeMenuScreen.Destination.TITLE_SCREEN, f.screen.consumeExit().destination());
        assertEquals(NativeMenuScreen.Exit.NONE, f.screen.consumeExit()); assertTrue(f.launches.isEmpty());
    }

    private static final class Fixture implements S3kCompetitionScreen.Host {
        final S3kCompetitionScreen screen = new S3kCompetitionScreen(this);
        final List<S3kCompetitionScreen.Launch> launches = new ArrayList<>();
        final List<S3kCompetitionSession.PlayerResult> savedRecords = new ArrayList<>();
        final List<Sonic3kSfx> sounds = new ArrayList<>();
        Fixture() { screen.initialize(); }
        void press(int slot,int direction,int action) {
            var p = PlayerInputState.of(direction,direction,action,action,false,false);
            screen.update(LogicalInputSnapshot.ofPlayers(slot==0?p:PlayerInputState.neutral(),slot==1?p:PlayerInputState.neutral()));
        }
        void idle(int frames) { for(int i=0;i<frames;i++) screen.update(LogicalInputSnapshot.neutral()); }
        @Override public S3kCompetitionSession openArena(S3kCompetitionScreen.Launch launch) {
            launches.add(launch);
            var world = new CompetitionTestWorld(launch.config().participantCount()==1?
                    S3kCompetitionSession.Mode.TIME_ATTACK:launch.config().mode());
            return world.session = new S3kCompetitionSession(launch.config(),world,
                    CompetitionTestWorld.SEQUENCE, CompetitionTestWorld.ITEM_ANIMATION, CompetitionTestWorld.GOAL_ANIMATION);
        }
        @Override public void stepArena(LogicalInputSnapshot input,S3kCompetitionSession session) { }
        @Override public void drawArena() { }
        @Override public void drawMenu(S3kCompetitionScreen.MenuView view) { }
        @Override public void setClearColor() { }
        @Override public void closeArena() { }
        @Override public void playSfx(Sonic3kSfx sound) { sounds.add(sound); }
    }
}
