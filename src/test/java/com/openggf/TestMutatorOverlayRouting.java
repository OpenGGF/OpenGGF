package com.openggf;

import com.openggf.configuration.*;
import com.openggf.control.*;
import com.openggf.game.*;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.debug.playback.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

/** Production loop routing: configuration has input/pause ownership, no custom frame controller. */
@Isolated
class TestMutatorOverlayRouting {
    private final Overlay overlay = new Overlay();
    private GameLoop loop;
    private InputHandler input;
    private GameplayModeContext gameplay;
    private FadeManager fade;
    private com.openggf.audio.AudioManager audio;
    static final class Overlay implements LevelInputOverlay {
        boolean held = true, waiting; int handled, consumed, accepted; Command command=Command.NONE;
        @Override public boolean handleInput(InputHandler input) { handled++; return held; }
        @Override public boolean pausesGameplay() { return held; }
        @Override public Command consumeCommand() { consumed++; var next=command; command=Command.NONE; return next; }
        @Override public void commandQueued(boolean value) { waiting=value; held=value; if(!value) accepted++; }
    }
    @BeforeEach void setup() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear(); GameServices.playbackDebug().endSession();
        var module=new DelegatingGameModule(new Sonic2GameModule(),"test:mutator-overlay") {
            @Override public <T> T getGameService(Class<T> type) { return type==LevelInputOverlay.class?type.cast(overlay):super.getGameService(type); }
        };
        GameModuleRegistry.setCurrent(module); gameplay=TestEnvironment.activeGameplayMode();
        input=mock(InputHandler.class); when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        loop=new GameLoop(input); loop.setGameMode(GameMode.LEVEL);
        audio=mock(com.openggf.audio.AudioManager.class); fade=gameplay.getFadeManager();
        field("audioManager",audio);
    }
    @AfterEach void cleanup() { GameServices.playbackDebug().endSession(); SessionManager.clear(); GameModuleRegistry.setCurrent(new Sonic2GameModule()); }
    void field(String name,Object value) throws Exception { var f=GameLoop.class.getDeclaredField(name);f.setAccessible(true);f.set(loop,value); }
    @Test void holdOwnsSimultaneousEscapePauseAndFrameStepWithoutAdvancingNativePlay() {
        var config=GameServices.configuration();
        when(input.isKeyPressed(config.getInt(SonicConfiguration.PAUSE_KEY))).thenReturn(true);
        when(input.isKeyPressed(config.getInt(SonicConfiguration.FRAME_STEP_KEY))).thenReturn(true);
        when(input.isKeyPressed(GLFW_KEY_ESCAPE)).thenReturn(true);
        when(input.isKeyDown(GLFW_KEY_ESCAPE)).thenReturn(true);
        int frame=gameplay.getSpriteManager().getFrameCounter();
        loop.pause(); loop.resume();
        for(int i=0;i<125;i++) loop.step();
        assertEquals(frame,gameplay.getSpriteManager().getFrameCounter());
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode()); assertTrue(loop.isPaused()); assertFalse(loop.isUserPaused());
        assertEquals(com.openggf.audio.presentation.PresentationMode.FORWARD,loop.presentationModeForOuterFrame(false,false),"configuration permits ROM music and menu feedback");
        assertEquals(125,overlay.handled);
    }
    @Test void acceptedResumeReleasesExistingUserPauseButPreservesWindowPause() {
        loop.toggleUserPause(); loop.pause(); overlay.command=LevelInputOverlay.Command.RESUME;
        loop.step(); assertFalse(loop.isUserPaused()); assertTrue(loop.isPaused()); assertFalse(overlay.held);
        loop.resume(); assertFalse(loop.isPaused());
        overlay.held=true; overlay.command=LevelInputOverlay.Command.RESUME; loop.step(); assertFalse(loop.isPaused());
    }
    @Test void commandIsRetainedAcrossFadeAndAcceptedExactlyOnce() {
        loop.toggleUserPause(); overlay.command=LevelInputOverlay.Command.RESUME;
        int nativeFrame=gameplay.getSpriteManager().getFrameCounter();
        fade.startFadeFromBlack(null);
        loop.step(); loop.step(); assertTrue(overlay.waiting); assertTrue(loop.isUserPaused());
        for(int i=0;fade.isActive() && i<30;i++) {
            loop.step();
            if(fade.isActive()) { assertTrue(overlay.waiting); assertEquals(0,overlay.accepted); }
        }
        assertFalse(fade.isActive(),"the native fade must complete while configuration holds play");
        assertFalse(overlay.waiting); assertFalse(overlay.held); assertFalse(loop.isUserPaused());
        assertEquals(1,overlay.accepted,"the finishing fade iteration accepts the command once");
        assertEquals(LevelInputOverlay.Command.NONE,overlay.command);
        assertEquals(nativeFrame,gameplay.getSpriteManager().getFrameCounter());
        overlay.held=true; loop.step(); assertEquals(1,overlay.accepted,"no stale command survives the acknowledgment");
    }
    @Test void externalMovieStartDoesNotOpenConfiguration() {
        overlay.held=false;
        GameServices.playbackDebug().startSession(new Bk2Movie(Path.of("overlay-input.bk2"),"logkey",Map.of(),
                List.of(new Bk2FrameInput(0,0,0,true,"start")),1),0);
        assertFalse(GameLoopPauseInput.handleOverlay(GameMode.LEVEL,input)); assertEquals(0,overlay.handled);
    }
}
