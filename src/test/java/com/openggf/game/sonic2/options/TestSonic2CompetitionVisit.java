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
    private static void tick(NativeMenuController menus,InputHandler input,LogicalInputSnapshot sample) {
        input.setLogicalOverride(sample);
        var mode=SessionManager.getCurrentGameplayMode();
        LevelFrameStep.updateTimers(LevelFrameContext.from(mode));
        var phase = NativeMenuController.phase(menus.screen().mode());
        boolean menu = menus.screen().mode() != com.openggf.game.GameMode.COMPETITION;
        mode.plcFrameLifecycle().runLogicalIteration(mode::updateFade,frame->{
            if (menu) assertTrue(frame.claim(phase));
            NativeCompetitionIterationAccess.runAdmitted(frame,()->menus.update(input,false));
            if (menu) frame.prepareAfterLoop(phase);
            return null;
        });
        // A fresh actual wait sample during the synchronous entry observes Start released.
        input.setLogicalOverride(NEUTRAL);
        assertTrue(menus.completeFrame());
    }
    private static void position(AbstractPlayableSprite player,int x,int y) {
        assertFalse(player.getDead());
        NativePositionOps.writeXPosResetSubpixel(player,x); NativePositionOps.writeYPosResetSubpixel(player,y);
        player.setXSpeed((short)0);player.setYSpeed((short)0);player.setGSpeed((short)0);
        player.setAir(true);player.setOnObject(false);
    }
}
