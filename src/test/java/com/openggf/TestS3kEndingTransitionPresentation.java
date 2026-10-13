package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingTransitionPresentation {
    @AfterEach void reset(){SessionManager.clear();}
    @ParameterizedTest @ValueSource(ints={1,2})
    void startAtUninitializedEndingEntryCannotBecomeAHostPause(int pad) throws Exception {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(13,1)
                .withFreshLevelStartLifecycle().build();
        var state=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();
        assertFalse(state.ending().initialized());
        assertTrue(GameServices.level().hasPendingInitialProcessSpritesPass());
        var input=new InputHandler();
        var start=com.openggf.control.PlayerInputState.of(0,0,0,0,true,true);
        var neutral=com.openggf.control.PlayerInputState.neutral();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                pad==1?start:neutral,pad==2?start:neutral));
        var overlay=GameServices.module().getGameService(com.openggf.game.LevelInputOverlay.class);
        assertTrue(overlay.handleInput(input),"native $0D01 owns Start before its first object pass");
        assertTrue(state.ending().startHeld());assertFalse(state.ending().initialized());
        var loop=new GameLoop(input);loop.setGameplayMode(fixture.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        try {
            loop.step();
            assertFalse(loop.isPaused());assertFalse(GameServices.gameState().isGamePaused());
            assertTrue(state.ending().initialized());
            assertEquals(2,GameServices.level().getObjectManager()
                    .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst().routine());
            assertFalse(GameServices.level().hasPendingInitialProcessSpritesPass());
            assertEquals(0,state.screen().scrollYFixed);
            input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(neutral,neutral));
            loop.step();assertEquals(0x8000,state.screen().scrollYFixed);
            GameServices.level().loadZoneAndAct(10,1);
            assertFalse(com.openggf.game.sonic3k.ending.S3kEndingCoordinator
                    .state(TestEnvironment.objectServices()).initialized());
            assertFalse(overlay.handleInput(input),"ordinary SSZ retains normal pause ownership");
        } finally {loop.closePresence();}
    }
    @Test void nativeEndingUsesWhiteEntryAndNoVisibleOrOmittedTitleArtOwner() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,1).build();
        var context=fixture.gameplayMode();var loop=new GameLoop(new InputHandler());
        loop.setGameplayMode(context);loop.setGameMode(GameMode.LEVEL);
        try {
            var start=GameLoop.class.getDeclaredMethod("startZoneActFade",int.class,int.class,int.class);
            start.setAccessible(true);start.invoke(loop,0x0D,1,-1);
            assertEquals(FadeManager.FadeState.FADING_TO_WHITE,context.getFadeManager().getState());
            for(int i=0;i<64&&GameServices.level().getCurrentZone()!=0x0D;i++)context.getFadeManager().update();
            assertEquals(0x0D,GameServices.level().getCurrentZone());assertEquals(1,GameServices.level().getCurrentAct());
            assertEquals(FadeManager.FadeState.FADING_FROM_WHITE,context.getFadeManager().getState(),
                    "the generic immediate title-palette policy must not erase the white ending boundary");
            assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary(),
                    "$0D01 has no title owner to hold destination assembly");
            loop.step();
            var rescue=GameServices.level().getObjectManager()
                    .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst();
            assertEquals(2,rescue.routine());
            var ram=((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).screen();
            assertEquals(0,ram.scrollYFixed,"initial Process_Sprites initializes without a second ordinary pass");
            assertFalse(GameServices.level().hasPendingInitialProcessSpritesPass());
            assertEquals(2,GameServices.level().getObjectManager()
                    .activeObjectsOfType(EndingRescueDirectorObjectInstance.Actor.class).size());
            for(int i=0;i<64&&context.getFadeManager().isActive();i++)context.getFadeManager().update();
            assertFalse(context.getFadeManager().isActive());
            loop.step();
            assertEquals(0x8000,ram.scrollYFixed,"the following LevelLoop alone advances native scroll");
            for(int i=0;i<78;i++){context.getFadeManager().update();loop.step();}
            assertFalse(GameServices.level().isTitleCardRequested());
            var title=(Sonic3kTitleCardManager)GameServices.module().getTitleCardProvider();
            assertFalse(title.ownsOmittedFreshLevelPresentation());
            assertFalse(title.ownsInLevelPlayerControlLock());
            assertTrue(title.capture().artHandles().isEmpty(),"Obj_TitleCardInitD01 deletes before all four archives");
        } finally {loop.closePresence();}
    }
}
