package com.openggf.game.sonic2.options;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.nativemenu.NativeMenuController;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.competition.Sonic2CompetitionVisit;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionVisit {
    private static final LogicalInputSnapshot NEUTRAL = LogicalInputSnapshot.neutral();
    private static final LogicalInputSnapshot START = LogicalInputSnapshot.ofPlayers(
            PlayerInputState.of(0,0,0,0,true,true),PlayerInputState.neutral());
    @Test void productionQualificationIsEmpty() throws Exception {
        try {
            HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
            var input = new InputHandler(); input.setLogicalOverride(NEUTRAL);
            var context = new NativeMenuHostContext(EngineServices.current(),
                    SessionManager.getCurrentWorldSession().rootGameModule(), input, ignored -> { });
            assertNull(new Sonic2NativeModeProvider().competitionScreen(context));
        } finally { TestEnvironment.resetAll(); }
    }
    @Test void optionsTypedExitRetainsEmeraldsAndTitleEntryClearsThem() throws Exception {
        try {
            HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
            var progress = com.openggf.game.GameServices.gameState(); progress.markEmeraldCollected(0);
            var input = new InputHandler(); input.setLogicalOverride(NEUTRAL);
            var context = new NativeMenuHostContext(EngineServices.current(),
                    SessionManager.getCurrentWorldSession().rootGameModule(),input,ignored -> { });
            var provider = new Sonic2NativeModeProvider(true,zone -> zone==0);
            var options=provider.optionsScreen(); options.initialize();
            for(int i=0;i<com.openggf.game.titlescreen.SegaPaletteFade.ROM_FADE_FRAMES;i++) options.update(input);
            input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(PlayerInputState.of(
                    AbstractPlayableSprite.INPUT_DOWN,AbstractPlayableSprite.INPUT_DOWN,0,0,false,false),PlayerInputState.neutral()));
            options.update(input);
            input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(PlayerInputState.of(
                    AbstractPlayableSprite.INPUT_RIGHT,AbstractPlayableSprite.INPUT_RIGHT,0,0,false,false),PlayerInputState.neutral()));
            options.update(input); input.setLogicalOverride(START); options.update(input);
            assertEquals(com.openggf.game.nativemenu.NativeMenuScreen.Destination.COMPETITION,options.consumeExit().destination());
            options.reset();
            var visit=assertInstanceOf(Sonic2CompetitionVisit.class,provider.competitionScreen(context));
            visit.initialize();
            assertAll(
                () -> assertTrue(visit.session().gameState(0).hasEmerald(0),"fixBugs=0 options VS preserves emeralds"),
                () -> assertTrue(visit.host().teleportOnly(),"host receives the retained Two_player_items option"));
            visit.session().gameState(0).markEmeraldCollected(1);
            visit.reset();
            assertTrue(progress.hasEmerald(1),"menu close returns newly acquired native progress before reset");
            var title=assertInstanceOf(Sonic2CompetitionVisit.class,provider.competitionScreen(context));
            title.initialize(); assertFalse(title.session().gameState(0).hasEmerald(0),"later title2P does not inherit stale options provenance");
            title.reset();
        } finally { TestEnvironment.resetAll(); }
    }
    @Test void menuOnlyVisitRegistersAndRetiresItsActualOwners() throws Exception {
        var menus=new NativeMenuController();
        try {
            HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
            var mode=SessionManager.getCurrentGameplayMode();var registry=mode.getRewindRegistry();
            var input=new InputHandler();input.setLogicalOverride(NEUTRAL);
            var context=new NativeMenuHostContext(EngineServices.current(),
                    SessionManager.getCurrentWorldSession().rootGameModule(),input,ignored -> { });
            var visit=assertInstanceOf(Sonic2CompetitionVisit.class,
                    new Sonic2NativeModeProvider(true,zone -> zone==0).competitionScreen(context));
            assertTrue(menus.open(visit));
            var active=registry.capture();menus.close();var retired=registry.capture();
            assertAll(
                () -> assertTrue(active.containsKey("sonic2.competition.halfpipe")),
                () -> assertTrue(active.containsKey(visit.session().key())),
                () -> assertTrue(active.containsKey(visit.controls().key())),
                () -> assertFalse(retired.containsKey("sonic2.competition.halfpipe")),
                () -> assertFalse(retired.containsKey(visit.session().key())),
                () -> assertFalse(retired.containsKey(visit.controls().key())),
                () -> assertSame(mode,SessionManager.getCurrentGameplayMode()));
        } finally { menus.close();TestEnvironment.resetAll(); }
    }
    @Test void standaloneHalfpipeWaitsOwnTheirClockWithoutRunningTheSourceLevel() throws Exception {
        var menus=new NativeMenuController();
        try {
            HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
            var mode=SessionManager.getCurrentGameplayMode();var objects=mode.getObjectManager();
            int clock=objects.getVblaCounter(),levelFrame=mode.getLevelManager().getFrameCounter();
            var input=new InputHandler();input.setLogicalOverride(NEUTRAL);
            var context=new NativeMenuHostContext(EngineServices.current(),
                    SessionManager.getCurrentWorldSession().rootGameModule(),input,ignored -> { });
            var visit=assertInstanceOf(Sonic2CompetitionVisit.class,
                    new Sonic2NativeModeProvider(true,zone -> zone==0).competitionScreen(context));
            assertTrue(menus.open(visit));tick(menus,input,NEUTRAL);
            var select=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(10,10,0,0,true,true),PlayerInputState.neutral());
            tick(menus,input,select);
            var phases=new java.util.ArrayList<com.openggf.game.resources.PlcLifecyclePhase>();
            for(int i=0;i<3;i++) phases.add(tick(menus,input,NEUTRAL));
            assertAll(
                () -> assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_STAGE,visit.session().phase()),
                () -> assertEquals(4,visit.session().specialStageIndex()),
                () -> assertNull(visit.host().profile(),"special-only entry must not fabricate an act load"),
                () -> assertSame(mode,SessionManager.getCurrentGameplayMode()),
                () -> assertEquals(clock+5,objects.getVblaCounter()),
                () -> assertEquals(levelFrame,mode.getLevelManager().getFrameCounter()),
                () -> assertEquals(java.util.Collections.nCopies(3,com.openggf.game.resources.PlcLifecyclePhase.SPECIAL_STAGE),phases));
        } finally { menus.close();TestEnvironment.resetAll(); }
    }
    @Test void qualifiedProviderBindsRealTeleportAndRewind() throws Exception {
        var menus = new NativeMenuController();
        try {
            HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
            var input=new InputHandler();input.setLogicalOverride(NEUTRAL);
            var context=new NativeMenuHostContext(EngineServices.current(),
                    SessionManager.getCurrentWorldSession().rootGameModule(),input,ignored -> { });
            var provider=new Sonic2NativeModeProvider(true,zone -> zone==0);
            var visit=assertInstanceOf(Sonic2CompetitionVisit.class,provider.competitionScreen(context));
            assertTrue(menus.open(visit));tick(menus,input,START);
            assertNotNull(visit.host().profile(),"afterFrame must load the actual native world");
            var mode=visit.host().context();
            var video=mode.getWorldSession().getGameModule().getGameService(
                    com.openggf.game.sonic2.competition.view.Sonic2CompetitionVideoFrame.class);
            var planes=video.planeScroll();
            var snapshots = mode.getRewindRegistry();
            var beforeTeleport = snapshots.capture();
            var scrollHandler = mode.getWorldSession().getGameModule().getScrollHandlerProvider().getHandler(0);
            var scrollBefore = (com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.State) scrollHandler.captureRewindState();
            var planeBefore = planes.capture();
            assertTrue(visit.host().swapNativeActorWorldState(),"actual native transaction is operational");
            var scrollAfter = (com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.State) scrollHandler.captureRewindState();
            var background=scrollAfter.p1();
            var environment=visit.host().nativeState().primaryEnvironment(mode,visit.session());
            assertAll(
                () -> assertEquals(planeBefore.live().get(1),planes.capture().live().get(0)),
                () -> assertEquals(planeBefore.copied().get(0),planes.capture().copied().get(1)),
                () -> assertEquals(scrollBefore.p2(),scrollAfter.p1()),
                () -> assertArrayEquals(new int[]{background.x()>>>16,background.y()>>>16,background.x2()>>>16,
                    background.y2()>>>16,background.x3()>>>16,background.y3()>>>16},
                    new int[]{environment.bgX(),environment.bgY(),environment.bg2X(),environment.bg2Y(),environment.bg3X(),environment.bg3Y()}));
            snapshots.restore(beforeTeleport);
            assertTrue(mode.getRewindRegistry().capture().containsKey("sonic2.competition.halfpipe"),
                    "the real halfpipe must be registered with the live world");
        } finally { menus.close();TestEnvironment.resetAll(); }
    }
    @Test void qualifiedProviderRunsRealGoalResultsAndSecondActWithoutPrivateStateMutation() throws Exception {
        var menus = new NativeMenuController();
        try {
            HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
            var input = new InputHandler(); input.setLogicalOverride(NEUTRAL);
            var context = new NativeMenuHostContext(EngineServices.current(),
                    SessionManager.getCurrentWorldSession().rootGameModule(), input, ignored -> { });
            var provider = new Sonic2NativeModeProvider(true, zone -> zone == 0);
            var visit = assertInstanceOf(Sonic2CompetitionVisit.class,provider.competitionScreen(context));
            assertTrue(menus.open(visit));
            assertTrue(visit.host().nativeState().operational(), "real corrected C object owners must be installed");
            tick(menus,input,START);
            assertEquals(Sonic2CompetitionSession.Phase.ACT,visit.session().phase());
            var mode = visit.host().context();
            var video = mode.getWorldSession().getGameModule().getGameService(
                    com.openggf.game.sonic2.competition.view.Sonic2CompetitionVideoFrame.class);
            var planes = video.planeScroll();
            var goal = visit.host().profile().signposts().getFirst();
            int target = Math.min(visit.host().profile().bounds().maxX(),goal.x()-0xA0);
            int x = 0;
            while (x != target) {
                x += Math.clamp(target-x,-0x100,0x100);
                for(int slot=0;slot<2;slot++) {
                    position(visit.session().player(slot),Math.min(x+0xA0,goal.x()-0x20),0x100);
                    visit.session().camera(slot).setX((short)x);
                }
                tick(menus,input,NEUTRAL);
            }
            for(int i=0;i<320 && visit.session().phase()==Sonic2CompetitionSession.Phase.ACT;i++) {
                for(int slot=0;slot<2;slot++) position(visit.session().player(slot),
                        i==0 && slot==1 ? goal.x()-0x20 : goal.x()+8,goal.y()-0x40);
                tick(menus,input,NEUTRAL);
            }
            assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS,visit.session().phase());
            assertNotNull(visit.session().actResult(0,0));
            var retained = planes.capture();
            for(int i=0;i<180 && visit.session().act()==0;i++) tick(menus,input,START);
            assertEquals(1,visit.session().act());
            assertEquals(1,visit.host().profile().act(), "afterFrame must load the actual second-act terrain");
            assertEquals(Sonic2CompetitionSession.Phase.ACT,visit.session().phase());
            assertSame(mode,visit.host().context());
            var second = mode.getWorldSession().getGameModule().getGameService(
                    com.openggf.game.sonic2.competition.view.Sonic2CompetitionVideoFrame.class);
            assertSame(planes,second.planeScroll(),"act reload retains the actual plane-scroll owner");
            assertEquals(retained.copied().get(1).x(), second.planeScroll().copy(1).x(), "title retains P2 copied X across acts");
            assertEquals(retained.copied().get(1).y(), second.planeScroll().copy(1).y(), "title retains P2 copied Y across acts");
            assertEquals(0,second.displayState().ringsAnimationFrame(),"Level entry clears ring phase");
        } finally { menus.close(); TestEnvironment.resetAll(); }
    }
    private static com.openggf.game.resources.PlcLifecyclePhase tick(NativeMenuController menus,InputHandler input,LogicalInputSnapshot sample) {
        input.setLogicalOverride(sample);
        var mode=SessionManager.getCurrentGameplayMode();
        LevelFrameStep.updateTimers(LevelFrameContext.from(mode));
        var phase = NativeMenuController.phase(menus.screen().mode());
        boolean menu = menus.screen().mode() != com.openggf.game.GameMode.COMPETITION;
        var owner = mode.plcFrameLifecycle().runLogicalIteration(mode::updateFade,frame->{
            if (menu) assertTrue(frame.claim(phase));
            NativeCompetitionIterationAccess.runAdmitted(frame,()->menus.update(input,false));
            if (menu) frame.prepareAfterLoop(phase);
            return frame.ownerPhase();
        });
        // A fresh actual wait sample during the synchronous entry observes Start released.
        input.setLogicalOverride(NEUTRAL);
        assertTrue(menus.completeFrame());
        return owner;
    }
    private static void position(AbstractPlayableSprite player,int x,int y) {
        assertFalse(player.getDead());
        NativePositionOps.writeXPosResetSubpixel(player,x); NativePositionOps.writeYPosResetSubpixel(player,y);
        player.setXSpeed((short)0);player.setYSpeed((short)0);player.setGSpeed((short)0);
        player.setAir(true);player.setOnObject(false);
    }
}
