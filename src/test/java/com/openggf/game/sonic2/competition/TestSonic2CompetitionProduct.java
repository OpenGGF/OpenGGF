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
    @Test void pauseTitleExitFollowsTwoPausedTokensAndOneOrdinaryBodyAndIsConsumedOnce() throws Exception {
        var session = new Sonic2CompetitionSession();
        var host = new Host();
        var screen = new Sonic2CompetitionScreen(session, host, null, TestEnvironment.currentRom());
        var menus = new com.openggf.game.nativemenu.NativeMenuController();
        menus.open(screen);
        session.updateMenu(input(0, 0, true).logical(), true);
        var phases = new java.util.ArrayList<com.openggf.game.resources.PlcLifecyclePhase>();
        var lifecycle = new com.openggf.game.resources.PlcFrameLifecycleCoordinator(
                new com.openggf.game.resources.PlcLifecycleService() {
                    @Override public void serviceVBlank(com.openggf.game.resources.PlcLifecyclePhase phase) {
                        phases.add(phase);
                    }
                    @Override public boolean hasPreparationBoundary(com.openggf.game.resources.PlcLifecyclePhase phase) {
                        return false;
                    }
                    @Override public void prepareAfterLoop(com.openggf.game.resources.PlcLifecyclePhase phase) { }
                });
        int[] calls = {0}, bodies = {0}, samples = {0};
        var input = new InputHandler() {
            @Override public void update() { samples[0]++; super.update(); }
        };
        host.onStep = () -> {
            int ordinal = ++calls[0];
            var frame = com.openggf.game.competition.NativeCompetitionIterationAccess.currentFrame();
            com.openggf.game.competition.NativeCompetitionIterationAccess.runLoop(frame, () -> {
                frame.claim(ordinal < 3
                        ? com.openggf.game.resources.PlcLifecyclePhase.NORMAL_PAUSE
                        : com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL);
                assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Exit.NONE, screen.consumeExit(),
                        "the screen cannot exit while the host is still executing its token");
                if (ordinal == 3) {
                    bodies[0]++;
                    host.pendingTitleExit = true;
                }
                return null;
            });
        };
        for (int row = 0; row < 3; row++) {
            var result = lifecycle.runLogicalIteration(() -> { }, frame -> {
                var exit = new com.openggf.game.nativemenu.NativeMenuScreen.Exit[1];
                com.openggf.game.competition.NativeCompetitionIterationAccess.runAdmitted(frame,
                        () -> exit[0] = menus.update(input, false));
                return exit[0];
            });
            if (row < 2) {
                assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Exit.NONE, result);
                assertEquals(0, bodies[0]);
            } else assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Destination.TITLE_SCREEN,
                    result.destination());
            menus.completeFrame();
        }
        assertEquals(java.util.List.of(
                com.openggf.game.resources.PlcLifecyclePhase.NORMAL_PAUSE,
                com.openggf.game.resources.PlcLifecyclePhase.NORMAL_PAUSE,
                com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL), phases);
        assertEquals(1, bodies[0]);
        assertEquals(3, samples[0], "only the actual outer controller advances each input row");
        assertFalse(host.consumePendingTitleExit());
        assertNull(host.loaded, "a title exit must not stage an act load in afterFrame");
        menus.close();
        assertFalse(menus.completeFrame());
        assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Exit.NONE, screen.consumeExit());
    }

    @Test void resetAndInitializeClearPublishedPauseTitleExits() throws Exception {
        var session = new Sonic2CompetitionSession();
        var host = new Host();
        var screen = new Sonic2CompetitionScreen(session, host, null, TestEnvironment.currentRom());
        screen.initialize();
        for (boolean initialize : new boolean[]{false, true}) {
            session.updateMenu(input(0, 0, true).logical(), true);
            host.pendingTitleExit = true;
            screen.update(input(0, 0, false));
            assertFalse(host.pendingTitleExit);
            if (initialize) screen.initialize(); else screen.reset();
            assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Exit.NONE, screen.consumeExit());
            screen.afterFrame();
            assertNull(host.loaded);
            if (!initialize) screen.initialize();
        }
    }

    @Test void nativeScreenRoutesBothControllersActOneAndActTwoWithPlcGate() throws Exception {
        var session = new Sonic2CompetitionSession();
        var host = new Host(); var halfpipe = new Halfpipe();
        var special = new Sonic2CompetitiveSpecialStageAdapter(session, halfpipe);
        var screen = new Sonic2CompetitionScreen(session, host, special, TestEnvironment.currentRom());
        screen.initialize();
        assertTrue(screen.supported());
        assertEquals(GameMode.COMPETITION_MENU, screen.mode());
        screen.update(input(1, 0, true));
        assertNull(host.loaded, "world setup waits until the outgoing frame has finished");
        screen.afterFrame();
        assertEquals("0:0", host.loaded);
        assertEquals(GameMode.COMPETITION, screen.mode());
        session.gameState(0).addScore(100);
        finish(session);
        assertEquals(GameMode.COMPETITION_RESULTS, screen.mode());
        screen.update(input(1, 0, true));
        assertEquals(0, session.act());
        screen.resultsPlcReady();
        screen.update(input(1, 0, true));
        assertEquals("0:0", host.loaded);
        screen.afterFrame();
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

    @Test void actTwoEntryRunsAfterResultsPreparationAndPreservesTheActOneLedger() throws Exception {
        var calls = new java.util.ArrayList<String>();
        var lifecycle = new com.openggf.game.resources.PlcFrameLifecycleCoordinator(
                new com.openggf.game.resources.PlcLifecycleService() {
                    @Override public void serviceVBlank(com.openggf.game.resources.PlcLifecyclePhase phase) {
                        calls.add("service:" + phase);
                    }
                    @Override public boolean hasPreparationBoundary(com.openggf.game.resources.PlcLifecyclePhase phase) {
                        return true;
                    }
                    @Override public void prepareAfterLoop(com.openggf.game.resources.PlcLifecyclePhase phase) {
                        calls.add("prepare:" + phase);
                    }
                });
        var session = new Sonic2CompetitionSession();
        var host = new Host();
        var screen = new Sonic2CompetitionScreen(session, host,
                new Sonic2CompetitiveSpecialStageAdapter(session, new Halfpipe()), TestEnvironment.currentRom());
        var menus = new com.openggf.game.nativemenu.NativeMenuController();
        menus.open(screen);
        menus.update(input(1, 0, true), false);
        menus.completeFrame();
        session.gameState(0).addScore(100);
        finish(session);
        var result = session.actResult(0, 0);
        assertNotNull(result);
        screen.resultsPlcReady();
        host.onLoad = () -> {
            assertEquals(java.util.List.of("service:TWO_PLAYER_RESULTS", "prepare:TWO_PLAYER_RESULTS"), calls);
            for (int entry = 0; entry < 26; entry++) {
                lifecycle.runLogicalIteration(() -> { }, frame -> {
                    frame.claim(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD);
                    frame.prepareAfterLoop(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD);
                    return null;
                });
            }
        };
        menus.beginFrame();
        lifecycle.runLogicalIteration(() -> { }, frame -> {
            frame.claim(com.openggf.game.resources.PlcLifecyclePhase.TWO_PLAYER_RESULTS);
            menus.update(input(1, 0, true), false);
            assertEquals("0:0", host.loaded);
            frame.prepareAfterLoop(com.openggf.game.resources.PlcLifecyclePhase.TWO_PLAYER_RESULTS);
            return null;
        });
        menus.completeFrame();

        assertEquals("0:1", host.loaded);
        assertEquals(54, calls.size());
        assertEquals(result, session.actResult(0, 0));
        assertEquals(1, session.act());
    }

    @Test void resetAndInitializeDiscardStagedActAndSpecialStageLoads() throws Exception {
        var session = new Sonic2CompetitionSession();
        var host = new Host();
        var halfpipe = new Halfpipe();
        var screen = new Sonic2CompetitionScreen(session, host,
                new Sonic2CompetitiveSpecialStageAdapter(session, halfpipe), TestEnvironment.currentRom());
        screen.initialize();
        screen.update(input(0, 0, true));
        screen.initialize();
        screen.afterFrame();
        assertNull(host.loaded);

        screen.update(input(1, 10, true));
        assertEquals(-1, halfpipe.loaded);
        screen.reset();
        screen.afterFrame();
        assertEquals(-1, halfpipe.loaded);

        screen.initialize();
        screen.update(input(1, 10, true));
        screen.afterFrame();
        assertEquals(4, halfpipe.loaded);
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
        Runnable onLoad = () -> { }, onStep = () -> { };
        boolean pendingTitleExit;
        @Override public boolean supportsZone(int zone) { return available && (zone == 0 || zone == 11 || zone == 12); }
        @Override public void loadAct(Sonic2CompetitionSession s, int zone, int act) { onLoad.run(); loaded = zone + ":" + act; }
        @Override public void step(Sonic2CompetitionSession s, InputHandler input) { onStep.run(); }
        @Override public boolean consumePendingTitleExit() {
            boolean result = pendingTitleExit; pendingTitleExit = false; return result;
        }
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
