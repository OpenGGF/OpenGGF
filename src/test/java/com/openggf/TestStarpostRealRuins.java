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

/** Native generated Ruins routes and scene return; each band/character runs independently. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarpostRealRuins {
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

    Object scene, shell, game, play, ruins, chamber;
    HeadlessTestRunner runner;
    void enter(String farmer,int number) throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,"WIDE_16_9"); config.resolveDisplayAspect();
        assertTrue(ModSceneLauncher.openStartupScene(loop,config,0,GraphicsManager.getInstance(),400,224)); drainFade();
        assertTrue(loop.modSceneHost.debugJump("new "+farmer));
        assertTrue(loop.modSceneHost.debugJump("close"));
        scene=SceneHostTestAccess.scene(loop.modSceneHost); shell=field(scene,"shell"); game=field(shell,"game"); play=call(shell,"screen");
        assertTrue(loop.modSceneHost.debugJump("ruins "+number));
        loop.modSceneActBridge.updateScene(input); drainFade();
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
        ruins=effective.getGameService(scene.getClass().getClassLoader().loadClass("starpost.realruins.RuinsSession"));
        chamber=call(ruins,"chamber"); runner=new HeadlessTestRunner(player()); runner.stepIdleFrames(2);
        assertEquals(farmer,player().getCode());
        assertTrue(GameServices.level().getCurrentLevel() instanceof com.openggf.game.sonic3k.Sonic3kLevel);
        assertTrue(player().getRingCount()>=10);
        assertEquals(490,field(game,"rings"));
        assertEquals(400,GameServices.camera().getWidth());
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> routes() {
        return java.util.stream.Stream.of("sonic","tails","knuckles").flatMap(c->java.util.stream.Stream.of(1,16,31)
                .map(n->org.junit.jupiter.params.provider.Arguments.of(c,n)));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("routes")
    void nativeMovementHitReturnAndRewind(String farmer,int number) throws Exception {
        enter(farmer,number);
        int startX=player().getCentreX();
        runner.stepFrame(false,false,true,false,false); runner.stepFrame(false,false,false,true,false);
        assertFalse(player().getDead());
        var registry=loop.resolveGameplayModeContext().getRewindRegistry();
        var checkpoint=registry.capture(); Object before=call(ruins,"capture");
        runner.stepIdleFrames(20); var forward=registry.capture();
        registry.restore(checkpoint); assertEquals(before,call(ruins,"capture"));
        runner.stepIdleFrames(20); var replay=registry.capture();
        for(String key:forward.entries().keySet()) assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,
                forward.entries().get(key),replay.entries().get(key)).isEmpty(),key+": "+com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,forward.entries().get(key),replay.entries().get(key)));
        player().setInvulnerableFrames(0); player().removeShield();
        var controller=GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o->o.getClass().getName().equals("starpost.realruins.RuinsController")).findFirst().orElseThrow();
        var hurt=controller.getClass().getMethod("hurt",ObjectServices.class,AbstractPlayableSprite.class,int.class,DamageCause.class,int.class);
        hurt.invoke(null,GameServices.level().getObjectManager().getObjectServices(),player(),startX+10,DamageCause.NORMAL,100);
        assertTrue(player().isHurt()); assertEquals(0,player().getRingCount());
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(o->o.getClass().getSimpleName().contains("LostRing"))
                ,"native scatter");
        call(ruins,"request",ActExit.LEFT,"leave"); runner.stepIdleFrames(1);
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode()); assertSame(game,field(shell,"game")); assertSame(play,call(shell,"screen"));
        assertEquals(490,field(game,"rings"),"zero native rings return without refunding health lost to hit");
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("routes")
    void nativeFaintRunsExistingRules(String farmer,int number) throws Exception {
        enter(farmer,number); call(field(game,"inventory"),"set",0,"marble_chip",8);
        player().setInvulnerableFrames(0); player().removeShield(); GameServices.level().getLevelGamestate().setRings(0);
        player().applyHurtOrDeath(player().getCentreX()+10,DamageCause.NORMAL,false); runner.stepIdleFrames(1);
        assertEquals(ActExit.FAINTED,call(ruins,"exit"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(4,call(field(game,"inventory"),"total","marble_chip"));
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("routes")
    void reachesGeneratedExitWithNativeInput(String farmer,int number) throws Exception {
        enter(farmer,number);
        int targetX=(int)field(chamber,"exitX"),targetY=(int)field(chamber,"exitY");
        int oldX=player().getCentreX(), stalled=0;
        for(int f=0;f<2000;f++) {
            int dx=targetX-player().getCentreX(), feet=player().getCentreY()+player().getYRadius()-128;
            if(Math.abs(dx)<17 && Math.abs(feet-targetY)<12 && !player().getAir() && !player().isHurt()) {
                call(ruins,"input",false,true,false,false); runner.stepIdleFrames(1);
                assertEquals(ActExit.COMPLETED,call(ruins,"exit"));
                assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
                loop.modSceneActBridge.updateScene(input); drainFade();
                assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
                assertEquals(number+1,field(call(ruins,"chamber"),"number"));
                assertTrue(harness.findings().isEmpty(),harness.findings().toString()); return;
            }
            int direction=Integer.signum(dx);
            double speed=player().getGSpeed()/256.0;
            if(Math.signum(speed)==direction && speed*speed>Math.abs(dx)-3) direction=-direction;
            stalled=Math.abs(player().getCentreX()-oldX)<1?stalled+1:0; oldX=player().getCentreX();
            boolean jump=!player().getAir() && (stalled>12 || feet>targetY+24);
            runner.stepFrame(false,false,direction<0,direction>0,jump);
            assertNull(call(ruins,"exit"),"premature exit at "+player().getCentreX()+","+feet);
            assertFalse(player().getDead());
        }
        fail("native route stalled; "+farmer+" room="+number+" entry="+field(chamber,"entryX")+","+field(chamber,"entryY")+
                " exit="+targetX+","+targetY+" player="+player().getCentreX()+","+player().getCentreY());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"sonic","tails","knuckles"})
    void labyrinthUsesNativeDrowningAndBubbleShield(String farmer) throws Exception {
        enter(farmer,25);
        int water=(int)field(chamber,"waterY");
        int x=-1,floor=-1;
        int bubbles=chamber.getClass().getField("BUBBLES").getInt(null);
        // Real drowning spot away from authored air vents; no gameplay countdown is injected.
        for(int candidate=32;candidate<(Integer)field(chamber,"width")-32;candidate+=16) {
            int below=(Integer)call(chamber,"floorBelow",candidate,water+24);
            boolean vent=false;
            for(Object thing:(List<?>)field(chamber,"things")) if((Integer)call(thing,"type")==bubbles
                    && Math.abs((Integer)call(thing,"x")-candidate)<80) vent=true;
            if(!vent && below>water+40 && below<(Integer)field(chamber,"height")) { x=candidate;floor=below;break; }
        }
        assertTrue(x>=0,"independent submerged floor away from bubble makers");
        NativePositionOps.writeXPosResetSubpixel(player(),x);
        NativePositionOps.writeYPosResetSubpixel(player(),128+floor-player().getYRadius());
        player().setAir(false); player().setGSpeed((short)0); player().setXSpeed((short)0); player().setYSpeed((short)0);
        player().setInvincibleFrames(5000);
        runner.stepIdleFrames(1900);
        assertTrue(player().isInWater()); assertFalse(player().getDead());
        assertEquals(30,player().getDrowningController().getRemainingAir(),"native Bubble Shield breathes");
        call(field(game,"inventory"),"select",1); player().removeShield();
        for(int f=0;f<2050 && call(ruins,"exit")==null;f++) runner.stepIdleFrames(1);
        assertTrue(player().isDrowningDeath()); assertEquals(ActExit.FAINTED,call(ruins,"exit"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode());
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }

    @Test void everyDailyChamberLoadsThroughSharedEncoderAndRegeneratesByDay() throws Exception {
        enter("sonic",1);
        Object town=effective.getGameService(scene.getClass().getClassLoader().loadClass("starpost.realtown.TownSession"));
        Object art=call(ruins,"art"),calendar=field(game,"calendar");
        var registry=effective.getZoneRegistry();
        int zone=registry.resolveZoneKey(new ZoneKey.Mod("starpost-valley","ruins")).orElseThrow();
        int index=registry.getLevelDataForZone(zone).getFirst().levelIndex();
        for(int n=1;n<=40;n++) {
            call(ruins,"prepare",game,town,art,n,false);
            assertNotNull(effective.loadLevelOverride(index),"chamber "+n);
            assertEquals(n,field(call(ruins,"chamber"),"number"));
            assertTrue((Integer)field(call(ruins,"chamber"),"width")>=512);
        }
        call(ruins,"prepare",game,town,art,1,false);
        int[] today=((int[])field(call(ruins,"chamber"),"cells")).clone();
        call(ruins,"prepare",game,town,art,1,false); assertArrayEquals(today,(int[])field(call(ruins,"chamber"),"cells"));
        call(calendar,"nextDay"); call(ruins,"prepare",game,town,art,1,false);
        assertFalse(Arrays.equals(today,(int[])field(call(ruins,"chamber"),"cells")),"morning changes generated room");
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    @Test void droppedFindsAndInventoryRoundTripRetainRoomProgress() throws Exception {
        enter("sonic",1);
        call(ruins,"drop","marble_chip",3,player().getCentreX()+80f,player().getCentreY()-128f);
        runner.stepIdleFrames(1);
        var registry=loop.resolveGameplayModeContext().getRewindRegistry(); var checkpoint=registry.capture();
        runner.stepIdleFrames(20); var forward=registry.capture(); registry.restore(checkpoint); runner.stepIdleFrames(20);
        var replay=registry.capture();
        for(String key:forward.entries().keySet()) assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,
            forward.entries().get(key),replay.entries().get(key)).isEmpty(),key);
        call(ruins,"request",ActExit.LEFT,"inventory"); runner.stepIdleFrames(1);
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertTrue(loop.modSceneHost.debugJump("close")); loop.modSceneActBridge.updateScene(input); drainFade();
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode()); assertSame(game,field(shell,"game"));
        runner=new HeadlessTestRunner(player()); runner.stepIdleFrames(2);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(o->o.getClass().getName().equals("starpost.realruins.RuinsFind")),"pending find recreated on same-room load");
        Object find=((List<?>)call(ruins,"finds")).getFirst();
        NativePositionOps.writeXPosResetSubpixel(player(),Math.round((Float)call(find,"x")));
        NativePositionOps.writeYPosResetSubpixel(player(),128+Math.round((Float)call(find,"y")));
        runner.stepIdleFrames(1);
        assertEquals(3,call(field(game,"inventory"),"total","marble_chip"));
        assertEquals(490,field(game,"rings")); assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    @Test void starPostElevatorMenuLaunchesUnlockedNativeChamber() throws Exception {
        enter("sonic",5);
        int x=(Integer)field(chamber,"elevatorX"), feet=(Integer)field(chamber,"elevatorY"); assertTrue(x>=0);
        NativePositionOps.writeXPosResetSubpixel(player(),x); NativePositionOps.writeYPosResetSubpixel(player(),128+feet-player().getYRadius());
        player().setAir(false); player().setGSpeed((short)0); player().setXSpeed((short)0); player().setYSpeed((short)0);
        runner.stepIdleFrames(2); call(ruins,"input",true,false,false,false); runner.stepIdleFrames(1);
        assertEquals(ActExit.LEFT,call(ruins,"exit"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertTrue((Boolean)call(shell,"hasOverlay"));
        // Drive the logical Genesis pad, independent of the host's configured keyboard bindings.
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
            com.openggf.control.PlayerInputState.of(2,2,0,0,false,false),com.openggf.control.PlayerInputState.neutral()));
        loop.modSceneActBridge.updateScene(input);
        Object menu=((Deque<?>)field(shell,"overlays")).peek(); assertEquals(1,field(menu,"cursor"));
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral()); loop.modSceneActBridge.updateScene(input);
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
            com.openggf.control.PlayerInputState.of(0,0,1,1,false,false),com.openggf.control.PlayerInputState.neutral()));
        loop.modSceneActBridge.updateScene(input); drainFade();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode()); assertEquals(5,field(call(ruins,"chamber"),"number"));
        assertSame(game,field(shell,"game")); assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    @Test void dayEndReturnsTimeUpAndAdvancesExistingCalendar() throws Exception {
        enter("sonic",1); Object calendar=field(game,"calendar");
        call(calendar,"set",1,0,1,1560); runner.stepIdleFrames(1);
        assertEquals(ActExit.TIME_UP,call(ruins,"exit"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode());
        for(int i=0;i<40;i++) loop.modSceneActBridge.updateScene(input);
        assertEquals("DayEndScreen",call(shell,"screen").getClass().getSimpleName()); assertEquals(2,call(calendar,"day"));
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"sonic","tails","knuckles"})
    void valleyDoorLaunchesRuinsAndLightReturnsToSameDoor(String farmer) throws Exception {
        enter(farmer,1); call(ruins,"request",ActExit.LEFT,"leave"); runner.stepIdleFrames(1);
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        loop.modSceneActBridge.updateScene(input); drainFade(); assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
        Object town=effective.getGameService(scene.getClass().getClassLoader().loadClass("starpost.realtown.TownSession"));
        Object layout=call(town,"layout"); int x=(Integer)call(layout,"anchor","ruins");
        Object ground=field(layout,"ground"); int floor=(Integer)call(ground,"floorBelow",x,0);
        NativePositionOps.writeXPosResetSubpixel(player(),x); NativePositionOps.writeYPosResetSubpixel(player(),floor-player().getYRadius());
        player().setAir(false); player().setGSpeed((short)0); player().setXSpeed((short)0); player().setYSpeed((short)0);
        runner=new HeadlessTestRunner(player()); runner.stepIdleFrames(2);
        call(town,"input",false,true,false,false,false,-1); runner.stepIdleFrames(1);
        assertNotNull(call(town,"handBack")); assertEquals("ruins",call(call(town,"handBack"),"place"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        loop.modSceneActBridge.updateScene(input); drainFade();
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode()); assertTrue((Boolean)call(ruins,"active"));
        runner=new HeadlessTestRunner(player()); runner.stepIdleFrames(2);
        call(ruins,"input",true,false,false,false); runner.stepIdleFrames(1);
        assertEquals(ActExit.LEFT,call(ruins,"exit"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade(); loop.modSceneActBridge.updateScene(input); drainFade();
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode()); assertTrue((Boolean)call(town,"active"));
        assertEquals(x,player().getCentreX()); assertSame(game,field(shell,"game")); assertSame(play,call(shell,"screen"));
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
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
