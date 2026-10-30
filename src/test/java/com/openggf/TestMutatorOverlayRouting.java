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

/** Production loop routing: configuration owns input/pause across native and controlled modules. */
@Isolated
class TestMutatorOverlayRouting {
    private final Overlay overlay = new Overlay();
    private GameLoop loop;
    private InputHandler input;
    private GameplayModeContext gameplay;
    private FadeManager fade;
    private com.openggf.audio.AudioManager audio;
    private final TitleScreenProvider title = mock(TitleScreenProvider.class);
    static final class Overlay implements LevelInputOverlay {
        boolean held = true, waiting, closeDirectly; int handled, consumed, accepted; Command command=Command.NONE;
        @Override public boolean handleInput(InputHandler input) { handled++; if (closeDirectly) held=false; return held; }
        @Override public boolean pausesGameplay() { return held; }
        @Override public Command consumeCommand() { consumed++; var next=command; command=Command.NONE; return next; }
        @Override public void commandQueued(boolean value) { waiting=value; held=value; if(!value) accepted++; }
    }
    @BeforeEach void setup() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear(); GameServices.playbackDebug().endSession();
        var module=new DelegatingGameModule(new Sonic2GameModule(),"test:mutator-overlay") {
            @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
            @Override public <T> T getGameService(Class<T> type) { return type==LevelInputOverlay.class?type.cast(overlay):super.getGameService(type); }
        };
        GameModuleRegistry.setCurrent(module); gameplay=TestEnvironment.activeGameplayMode();
        // Controlled native rows execute through the handler's scoped publication
        // callback. A pure mock silently drops that callback and the world update.
        input=spy(new InputHandler(InputBindingFactory.supplier(GameServices.configuration()),
                GamepadStateSource.noop()));
        loop=new GameLoop(input); loop.setGameMode(GameMode.LEVEL);
        audio=mock(com.openggf.audio.AudioManager.class); fade=gameplay.getFadeManager();
        field("audioManager",audio);
    }
    @AfterEach void cleanup() { GameServices.playbackDebug().endSession(); SessionManager.clear(); GameModuleRegistry.setCurrent(new Sonic2GameModule()); }
    void field(String name,Object value) throws Exception { var f=GameLoop.class.getDeclaredField(name);f.setAccessible(true);f.set(loop,value); }
    private int[] installControlledHold() throws Exception {
        return installControlled(false);
    }
    private int[] installControlled(boolean advance) throws Exception {
        int[] decisions = {0};
        if (advance) gameplay.getCamera().setFocusedSprite(
                new com.openggf.sprites.playable.Sonic("sonic", (short) 0, (short) 0));
        var world = gameplay.getWorldSession();
        var base = world.getGameModule();
        var frameController = new com.openggf.game.mode.GameplayFrameController() {
            public boolean beforeTick(com.openggf.game.mode.CourseControl c, LogicalInputSnapshot i) { decisions[0]++; return advance; }
            public void afterTick(com.openggf.game.mode.CourseControl c, boolean advanced) { assertEquals(advance, advanced); }
        };
        var module = new DelegatingGameModule(base, "test:controlled-overlay") {
            @Override public com.openggf.game.mode.GameplayFrameController gameplayFrameController() { return frameController; }
        };
        var moduleField = world.getClass().getDeclaredField("gameModule"); moduleField.setAccessible(true); moduleField.set(world, module);
        return decisions;
    }
    @Test void directOverlayCloseCannotServiceOneFadeTwiceInTheControlledRow() throws Exception {
        int[] decisions = installControlled(true);
        overlay.closeDirectly = true;
        fade.startFadeFromBlack(null);
        int nativeFrame = gameplay.getSpriteManager().getFrameCounter();
        loop.step();
        assertFalse(overlay.held);
        assertEquals(LevelInputOverlay.Command.NONE, overlay.command, "direct closure uses no host command");
        assertEquals(1, fade.getFrameCount(), "the modal presentation already serviced this row's fade");
        assertEquals(0, decisions[0], "the presented fade row cannot admit another controller/world row");
        assertEquals(nativeFrame, gameplay.getSpriteManager().getFrameCounter());
        verify(input, times(1)).update();
        clearInvocations(input);
        loop.step();
        assertEquals(2, fade.getFrameCount(), "the released next row services the fade once");
        assertEquals(1, decisions[0]);
        assertEquals(nativeFrame + 1, gameplay.getSpriteManager().getFrameCounter());
        verify(input, times(1)).update();
    }
    @Test void directOverlayCloseWithoutAnActiveFadeAdmitsTheControlledRow() throws Exception {
        int[] decisions = installControlled(true);
        overlay.closeDirectly = true;
        int nativeFrame = gameplay.getSpriteManager().getFrameCounter();
        loop.step();
        assertFalse(overlay.held);
        assertEquals(1, decisions[0], "a released overlay with no presentation work admits gameplay immediately");
        assertEquals(nativeFrame + 1, gameplay.getSpriteManager().getFrameCounter());
        verify(input, times(1)).update();
    }
    @Test void controlledConfigurationHoldRejectsFrameStepAndOwnsEscape() throws Exception {
        int[] decisions = installControlledHold();
        var config = GameServices.configuration();
        when(input.isKeyPressed(config.getInt(SonicConfiguration.FRAME_STEP_KEY))).thenReturn(true);
        when(input.isKeyPressed(GLFW_KEY_ESCAPE)).thenReturn(true);
        when(input.isKeyDown(GLFW_KEY_ESCAPE)).thenReturn(true);
        loop.step();
        assertEquals(0, decisions[0], "configuration holds cannot enter a controller row through frame-step");
        assertFalse(loop.getEscapeToMasterTitleController().visible(), "modal Escape belongs to its overlay");
        assertEquals(1, overlay.handled);
    }
    @Test void controlledConfigurationResumeUsesTheExistingCommandAdmission() throws Exception {
        installControlledHold();
        loop.toggleUserPause(); overlay.command = LevelInputOverlay.Command.RESUME;
        loop.step();
        assertEquals(1, overlay.accepted);
        assertFalse(overlay.held); assertFalse(loop.isUserPaused());
        assertEquals(LevelInputOverlay.Command.NONE, overlay.command);
    }
    @Test void controlledConfigurationRetainsCommandsAcrossNativeFadeWithoutEnteringControllerRows() throws Exception {
        int[] decisions = installControlledHold();
        loop.toggleUserPause(); overlay.command = LevelInputOverlay.Command.RESUME;
        fade.startFadeFromBlack(null);
        int nativeFrame = gameplay.getSpriteManager().getFrameCounter();
        for (int i = 0; fade.isActive() && i < 30; i++) loop.step();
        assertFalse(fade.isActive(), "configuration holds retain native fade presentation");
        assertEquals(1, overlay.accepted); assertFalse(loop.isUserPaused()); assertFalse(overlay.held);
        assertEquals(0, decisions[0]); assertEquals(nativeFrame, gameplay.getSpriteManager().getFrameCounter());
        overlay.held = true; loop.step(); assertEquals(1, overlay.accepted);
    }
    @Test void controlledFinishingFadeCannotReenterTheRetiredLevelController() throws Exception {
        int[] decisions = installControlledHold();
        fade.startFadeFromBlack(() -> {
            overlay.held = false;
            loop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
        });
        for (int i = 0; fade.isActive() && i < 30; i++) loop.step();
        assertFalse(fade.isActive()); assertEquals(GameMode.MASTER_TITLE_SCREEN, loop.getCurrentGameMode());
        assertEquals(0, decisions[0], "the finishing presentation row belongs to the destination mode");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void sceneActHoldRetainsHostPauseBeforeWorldAdmission(boolean controlled) throws Exception {
        int[] decisions = controlled ? installControlledHold() : new int[1];
        overlay.held = false;
        var held = ModSceneActBridge.class.getDeclaredField("transitioning");
        held.setAccessible(true); held.setBoolean(loop.modSceneActBridge, true);
        when(input.isKeyPressed(GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY))).thenReturn(true);
        int frame = gameplay.getSpriteManager().getFrameCounter();
        clearInvocations(input); loop.step();
        assertTrue(loop.isUserPaused(), "a native handoff retains the host's pause input");
        verify(input, times(1)).update();
        clearInvocations(input); loop.step();
        assertFalse(loop.isUserPaused(), "the same host input resumes before handoff admission");
        verify(input, times(1)).update();
        assertEquals(frame, gameplay.getSpriteManager().getFrameCounter());
        assertEquals(0, decisions[0], "host pause cannot admit a controller/world row through the bridge hold");
    }
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
    @Test void nullCommandKeepsNativePlayHeldWithoutAcknowledgmentOrRestart() {
        overlay.command=null;
        int nativeFrame=gameplay.getSpriteManager().getFrameCounter();
        loop.step();
        assertTrue(overlay.held);assertEquals(0,overlay.accepted);assertFalse(fade.isActive());
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
        assertEquals(nativeFrame,gameplay.getSpriteManager().getFrameCounter());
        verify(audio,never()).fadeOutMusic();
        overlay.command=LevelInputOverlay.Command.RESUME;loop.step();
        assertEquals(1,overlay.accepted);assertFalse(overlay.held);
    }
    @Test void modalTitleOwnsEscapeAndClearsPriorHostPromptWhileStockDefaultRetainsIt() {
        loop.setGameMode(GameMode.TITLE_SCREEN);loop.pause();
        when(input.isKeyPressed(GLFW_KEY_ESCAPE)).thenReturn(true);
        when(input.isKeyDown(GLFW_KEY_ESCAPE)).thenReturn(true);
        var controller=loop.getEscapeToMasterTitleController();
        controller.update(GameMode.TITLE_SCREEN,input);assertTrue(controller.visible());
        when(title.ownsEscapeInput()).thenReturn(true);
        for(int i=0;i<125;i++)loop.step();
        assertFalse(controller.visible());assertEquals(0,controller.progress());
        assertEquals(GameMode.TITLE_SCREEN,loop.getCurrentGameMode());assertFalse(fade.isActive());
        when(title.ownsEscapeInput()).thenReturn(false);loop.step();
        assertTrue(controller.visible(),"stock titles retain the host shortcut");
    }
    @Test void modalTitleQueuesHubCommandThroughExistingFadeOwner() {
        loop.setGameMode(GameMode.TITLE_SCREEN);loop.pause();
        when(title.ownsEscapeInput()).thenReturn(true);
        when(input.isKeyPressed(GLFW_KEY_ESCAPE)).thenReturn(true);
        loop.getEscapeToMasterTitleController().update(GameMode.TITLE_SCREEN,input);
        overlay.command=LevelInputOverlay.Command.RETURN_TO_HUB;loop.step();
        assertTrue(fade.isActive());assertEquals(1,overlay.accepted);
        assertFalse(loop.getEscapeToMasterTitleController().visible(),"accepted modal exits clear the old prompt too");
        assertEquals(LevelInputOverlay.Command.NONE,overlay.command);
        loop.step();assertEquals(1,overlay.accepted,"the title command is not replayed");
    }
}
