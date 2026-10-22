package com.openggf.game.sonic2.competition;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
@ExtendWith(SingletonResetExtension.class)
class TestSonic2CompetitionProduct {
    @Test void nativeScreenRoutesBothControllersActOneAndActTwoWithPlcGate() throws Exception {
        var session = new Sonic2CompetitionSession();
        var host = new Host(); var halfpipe = new Halfpipe();
        var special = new Sonic2CompetitiveSpecialStageAdapter(session, halfpipe);
        var screen = new Sonic2CompetitionScreen(session, host, special, TestEnvironment.currentRom());
        screen.initialize();
        assertTrue(screen.supported());
        assertEquals(GameMode.COMPETITION_MENU, screen.mode());
        screen.update(input(1, 0, true));
        assertEquals("0:0", host.loaded);
        assertEquals(GameMode.COMPETITION, screen.mode());
        session.gameState(0).addScore(100);
        finish(session);
        assertEquals(GameMode.COMPETITION_RESULTS, screen.mode());
        screen.update(input(1, 0, true));
        assertEquals(0, session.act());
        screen.resultsPlcReady();
        screen.update(input(1, 0, true));
        assertEquals("0:1", host.loaded);
        session.gameState(0).addScore(100);
        finish(session);
        screen.resultsPlcReady();
        screen.update(input(0, 0, true));
        assertEquals(Sonic2CompetitionSession.Phase.ZONE_RESULTS, session.phase());
        screen.resultsPlcReady();
        screen.update(input(1, 0, true));
        assertEquals(GameMode.COMPETITION_MENU, screen.mode());
        assertTrue(session.completed(0));
        screen.reset();
        assertFalse(session.completed(0));
        assertEquals(GameMode.COMPETITION_MENU, screen.mode());
        assertEquals(1, halfpipe.resets);
    }

    @Test void missingNativeSpecialOrUnsupportedWorldKeepsActivationUnavailable() throws Exception {
        var s = new Sonic2CompetitionSession(); var host = new Host();
        var absent = new Sonic2CompetitionScreen(s, host, null, TestEnvironment.currentRom());
        assertFalse(absent.supported());
        var halfpipe = new Halfpipe();
        var screen = new Sonic2CompetitionScreen(s, host,
                new Sonic2CompetitiveSpecialStageAdapter(s, halfpipe), TestEnvironment.currentRom());
        host.available = false; halfpipe.available = false;
        screen.initialize();
        assertFalse(screen.supported());
        screen.update(input(0, 0, true));
        assertNull(host.loaded);
        assertEquals(GameMode.COMPETITION_MENU, screen.mode());
    }

    @Test void worldPolicyPublishesOnlyActiveNativeRosterAndRestoresIndependentBanks() {
        var session = new Sonic2CompetitionSession();
        var policy = new Sonic2CompetitionPolicy(session);
        var module = new Sonic2GameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) world -> policy) : super.getGameService(type);
            }
        };
        var world = new WorldSession(module);
        assertNull(NativeCompetitionRoster.from(world));
        session.updateMenu(input(0, 0, true).logical(), true);
        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var tails = new Tails("tails", (short) 0, (short) 0);
        session.bindPlayers(sonic, tails);
        session.bindCameras(new Camera(), new Camera());
        var roster = NativeCompetitionRoster.from(world);
        assertSame(session, roster);
        assertEquals(0, roster.slotOf(sonic)); assertEquals(1, roster.slotOf(tails));
        assertEquals(-1, roster.slotOf(new Sonic("other", (short) 0, (short) 0)));
        assertNotSame(roster.gameState(0), roster.gameState(1));
        assertNotSame(roster.levelState(0), roster.levelState(1));
        assertNotSame(roster.camera(0), roster.camera(1));
        tails.setCpuControlled(true);
        assertThrows(IllegalArgumentException.class, () -> session.bindPlayers(sonic, tails));
        tails.setCpuControlled(false);
        assertThrows(IllegalArgumentException.class, () -> session.bindPlayers(tails, sonic));
        session.playerState(1).addRings(25);
        var snapshot = session.capture();
        session.playerState(1).addRings(75);
        session.restore(snapshot);
        assertSame(tails, NativeCompetitionRoster.from(world).player(1));
        assertEquals(25, roster.levelState(1).getRings());
        assertEquals(3, roster.gameState(1).getLives());
        WorldSessionPolicyAccess.beforeAssembly(world, com.openggf.game.LevelLoadCause.DECODE_ONLY);
        assertSame(session, NativeCompetitionRoster.from(world));
        policy.retire();
        assertNull(NativeCompetitionRoster.from(world));
    }

    @Test void competitiveHalfpipeUsesNativeSectorEventsWithoutCpuOrSummedRingGates() {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(input(1, 10, true).logical(), true);
        var halfpipe = new Halfpipe();
        var adapter = new Sonic2CompetitiveSpecialStageAdapter(session, halfpipe);
        adapter.load();
        assertEquals(4, halfpipe.loaded);
        for (int i = 0; i < 105; i++) halfpipe.events.collectedRing(1);
        halfpipe.events.collectedRing(0);
        halfpipe.events.hitBomb(0);
        assertEquals(0, session.levelState(0).getRings());
        assertEquals(105, session.levelState(1).getRings());
        assertEquals(3, session.gameState(1).getLives());
        halfpipe.events.checkpoint();
        assertEquals(java.util.List.of(0, 105, 0, 0, 0, 0), session.specialRings());
        assertEquals(0, session.levelState(1).getRings());
        halfpipe.events.checkpoint(); halfpipe.events.checkpoint();
        assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_ACT_RESULTS, session.phase());
        assertThrows(IllegalStateException.class, () -> halfpipe.events.collectedRing(0));
    }

    @Test void teleportRequestsFullAtomicSwapPreservesIdentitiesAndProgressAndStartsFreezeOnlyOnSuccess() {
        var s = new Sonic2CompetitionSession(); s.updateMenu(input(0, 0, true).logical(), true);
        var host = new Host(); s.playerState(1).addRings(17);
        var sonic = new Sonic("sonic", (short) 0, (short) 0); var tails = new Tails("tails", (short) 0, (short) 0);
        s.bindPlayers(sonic, tails);
        host.swap = false;
        assertFalse(s.teleport(host)); assertEquals(0, host.frozen);
        host.swap = true;
        assertTrue(s.teleport(host)); assertEquals(0x40, host.frozen);
        assertEquals(17, s.levelState(1).getRings()); assertSame(sonic, s.player(0)); assertSame(tails, s.player(1));
        assertTrue(s.teleportWhite());
        var snapshot = s.capture();
        for (int i = 0; i < 49; i++) s.advanceActFlow();
        assertFalse(s.teleportWhite());
        s.restore(snapshot); assertTrue(s.teleportWhite());
        assertEquals(0x40, s.teleportTimer());
    }

    private static InputHandler input(int slot, int directions, boolean start) {
        var state = PlayerInputState.of(directions, directions, 0, 0, start, start);
        var handler = new InputHandler();
        handler.setLogicalOverride(LogicalInputSnapshot.ofPlayers(slot == 0 ? state : PlayerInputState.neutral(),
                slot == 1 ? state : PlayerInputState.neutral()));
        return handler;
    }
    private static void finish(Sonic2CompetitionSession s) {
        s.observeGoal(0, 100, 100); s.observeGoal(1, 100, 100); s.completeSignpostSpin();
    }
    private static final class Host implements Sonic2CompetitionHost {
        String loaded; boolean available = true, swap; int frozen;
        @Override public boolean supportsZone(int zone) { return available && (zone == 0 || zone == 11 || zone == 12); }
        @Override public void loadAct(Sonic2CompetitionSession s, int zone, int act) { loaded = zone + ":" + act; }
        @Override public void step(Sonic2CompetitionSession s, InputHandler input) { }
        @Override public void draw(Sonic2CompetitionSession s) { }
        @Override public void freezeObjects(int ticks) { frozen = ticks; }
        @Override public boolean swapNativeActorWorldState() { return swap; }
    }
    private static final class Halfpipe implements Sonic2CompetitiveSpecialStageAdapter.Halfpipe {
        Sonic2CompetitiveSpecialStageAdapter.Events events;
        int loaded = -1, resets; boolean available = true;
        @Override public boolean supportsCompetitiveStage(int index) { return available; }
        @Override public void loadCompetitiveStage(int index, Sonic2CompetitiveSpecialStageAdapter.Events events) { loaded = index; this.events = events; }
        @Override public void step(InputHandler input) { }
        @Override public void draw() { }
        @Override public void reset() { resets++; }
    }
}
