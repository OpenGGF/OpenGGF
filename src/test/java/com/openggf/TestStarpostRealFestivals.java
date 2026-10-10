package com.openggf;

import com.openggf.configuration.*;
import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.session.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.mods.*;
import com.openggf.mods.code.*;
import com.openggf.mods.scene.*;
import com.openggf.mods.scene.host.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native festival/lake routes and rewind across character × viewport. Origin: phase 4, 2026-10-10. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarpostRealFestivals {
    @TempDir Path temp;
    SharedLevel source;
    ExampleModHarness harness;
    GameLoop loop;
    GameModule effective;
    InputHandler input;
    boolean oldTest;
    RewindClassResolver oldResolver;
    @BeforeEach void setup() throws Exception {
        source = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        var config = SonicConfigurationService.getInstance();
        oldTest = config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        harness = ExampleModHarness.build(Path.of("examples/starpost-valley"), temp.resolve("build"));
        effective = harness.apply(GameServices.module());
        oldResolver = ModSubsystem.current().rewindClassResolver();
        ModSubsystem.current().installRewindClassResolver(harness.rewindClassResolver());
        TestEnvironment.configureGameModuleFixture(effective);
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.TEST_MODE_ENABLED,false);
        GameServices.graphics().initHeadless();
        input = new InputHandler(); loop = new GameLoop(input);
        loop.setGameMode(GameMode.MOD_SCENE);
    }
    @AfterEach void close() throws Exception {
        if (loop != null) { loop.setGameMode(GameMode.MASTER_TITLE_SCREEN); loop.modSceneHost.cleanup(); }
        if (harness != null) harness.close();
        if (oldResolver != null) ModSubsystem.current().installRewindClassResolver(oldResolver);
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, oldTest);
        if (source != null) source.dispose();
    }
    void drainFade() {
        for (int i = 0; i < 400 && loop.resolveFadeManager().isActive(); i++) loop.resolveFadeManager().update();
        assertFalse(loop.resolveFadeManager().isActive(), "handoff fade must complete");
    }
    AbstractPlayableSprite player() { return (AbstractPlayableSprite) GameServices.camera().getFocusedSprite(); }

    Object scene,shell,game,play,activity;
    HeadlessTestRunner runner;
    void enter(String farmer,int width,String kind) throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,width==400?"WIDE_16_9":"NATIVE_4_3"); config.resolveDisplayAspect();
        assertTrue(ModSceneLauncher.openStartupScene(loop,config,0,GraphicsManager.getInstance(),width,224)); drainFade();
        assertTrue(loop.modSceneHost.debugJump("new "+farmer)); assertTrue(loop.modSceneHost.debugJump("close"));
        scene=SceneHostTestAccess.scene(loop.modSceneHost); shell=field(scene,"shell"); game=field(shell,"game"); play=call(shell,"screen");
        if(kind.equals("lake")) assertTrue(loop.modSceneHost.debugJump("fish lake"));
        else {
            String festival=kind.equals("hunt")?"ring_hunt":kind.equals("race")?"valley_race":"ice_cap";
            assertTrue(loop.modSceneHost.debugJump("festival day "+festival));
            play=call(shell,"screen");
            // Accepted native invitation hands back to exactly one title/results screen.
            Object town=call(scene,"prepareTownAct"); call(town,"request","festival",null,700,300); call(scene,"resumeTownAct");
            for(int i=0;i<160;i++) loop.modSceneActBridge.updateScene(input);
            if(kind.equals("snowboard") || kind.equals("contest")) {
                var pad=com.openggf.control.PlayerInputState.of(kind.equals("snowboard")?2:0,kind.equals("snowboard")?2:0,0,0,false,false);
                input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(pad,com.openggf.control.PlayerInputState.neutral()));
                loop.modSceneActBridge.updateScene(input);
                input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                    com.openggf.control.PlayerInputState.of(0,0,1,1,false,false),com.openggf.control.PlayerInputState.neutral()));
                loop.modSceneActBridge.updateScene(input); input.clearLogicalOverride();
            }
        }
        loop.modSceneActBridge.updateScene(input); drainFade();
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
        activity=effective.getGameService(scene.getClass().getClassLoader().loadClass("starpost.realfest.ActivitySession"));
        runner=new HeadlessTestRunner(player()); runner.stepIdleFrames(2);
        assertEquals(farmer,player().getCode()); assertTrue(GameServices.level().getCurrentLevel() instanceof com.openggf.game.sonic3k.Sonic3kLevel);
        assertEquals(width,GameServices.camera().getWidth());
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> routes() {
        return java.util.stream.Stream.of("sonic","tails","knuckles").flatMap(c->java.util.stream.Stream.of(320,400)
            .flatMap(w->java.util.stream.Stream.of("race","hunt","snowboard","lake","contest")
                .map(k->org.junit.jupiter.params.provider.Arguments.of(c,w,k))));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("routes")
    void nativeRouteResultAndReturn(String farmer,int width,String kind) throws Exception {
        enter(farmer,width,kind);
        var registry=loop.resolveGameplayModeContext().getRewindRegistry();
        var checkpoint=registry.capture(); Object before=call(activity,"capture");
        runner.stepIdleFrames(12); var forward=registry.capture(); registry.restore(checkpoint);
        assertEquals(before,call(activity,"capture")); runner.stepIdleFrames(12); var replay=registry.capture();
        for(String key:forward.entries().keySet()) assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,
            forward.entries().get(key),replay.entries().get(key)).isEmpty(),key);
        if(kind.equals("lake") || kind.equals("contest")) fish(kind);
        else {
            int oldX=player().getCentreX(),stalled=0;
            for(int f=0;f<8000 && call(activity,"exit")==null;f++) {
                stalled=Math.abs(player().getCentreX()-oldX)<1?stalled+1:0; oldX=player().getCentreX();
                boolean jump=!player().getAir() && (stalled>12 || f%120==0);
                runner.stepFrame(false,false,false,true,jump);
                assertFalse(player().getDead(),kind+" fell at "+player().getCentreX()+","+player().getCentreY());
            }
            assertNotNull(call(activity,"exit"),kind+" did not finish");
            assertEquals(ActExit.COMPLETED,call(activity,"exit"),kind+" native route "+player().getCentreX()+","+player().getCentreY());
            if(kind.equals("hunt")) assertTrue((Integer)call(activity,"farmer")>0,"real ring pickup");
        }
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode()); assertSame(game,field(shell,"game"));
        assertFalse((Boolean)call(activity,"active"));
        int wallet=(Integer)field(game,"rings");
        for(int i=0;i<5;i++) loop.modSceneActBridge.consumeExitOrHold(input);
        assertEquals(wallet,field(game,"rings"),"result applies once");
        if(!kind.equals("lake")) {
            for(int i=0;i<45;i++) loop.modSceneActBridge.updateScene(input);
            input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0,0,1,1,false,false),com.openggf.control.PlayerInputState.neutral()));
            loop.modSceneActBridge.updateScene(input); input.clearLogicalOverride();
            for(int i=0;i<40;i++) loop.modSceneActBridge.updateScene(input);
            drainFade();
        }
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    void fish(String kind) throws Exception {
        for(int f=0;f<140 && player().getCentreX()<310;f++) runner.stepFrame(false,false,false,true,false);
        assertTrue(player().getCentreX()>256,"walk onto native jetty");
        runner.stepIdleFrames(10);
        call(field(game,"inventory"),"set",0,"fishing_rod",1); call(field(game,"inventory"),"select",0);
        call(activity,"input",true,true,false,false,-1); runner.stepIdleFrames(1);
        for(int f=0;f<35;f++) { call(activity,"input",false,true,false,false,-1); runner.stepIdleFrames(1); }
        runner.stepIdleFrames(1); Object lake=call(activity,"lake"),line=field(lake,"line");
        assertTrue((Boolean)call(line,"out"),"casting in the act");
        for(int f=0;f<400 && (Integer)field(line,"state")!=3;f++) runner.stepIdleFrames(1);
        assertEquals(3,field(line,"state")); call(activity,"input",true,false,false,false,-1); runner.stepIdleFrames(1);
        for(int f=0;f<1500 && call(lake,"bar")!=null;f++) {
            Object bar=call(lake,"bar");
            boolean hold=(Float)field(bar,"bubble")>(Float)field(bar,"fish")+2*(Float)field(bar,"bubbleSpeed");
            call(activity,"input",false,hold,false,false,-1); runner.stepIdleFrames(1);
        }
        assertNull(call(lake,"bar"));
        assertTrue((Integer)call(lake,"score")>=0);
        if(kind.equals("contest")) for(int f=0;f<7300 && call(activity,"exit")==null;f++) runner.stepIdleFrames(1);
        else { call(activity,"input",false,false,true,false,-1); runner.stepIdleFrames(1); }
        assertNotNull(call(activity,"exit"));
    }
    static Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    static Object call(Object object,String name,Object... args) throws Exception {
        for(var method:object.getClass().getMethods()) if(method.getName().equals(name)&&method.getParameterCount()==args.length)
            { method.setAccessible(true); return method.invoke(object,args); }
        throw new NoSuchMethodException(name);
    }
}
