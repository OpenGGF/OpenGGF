package com.openggf;

import com.openggf.game.ActExit;

import com.openggf.configuration.*;
import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.session.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelSceneActAccess;
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

/** Real bootstrap and town round trips, using packaged external creator classes and native S3K actors. */
@RequiresRom(SonicGame.SONIC_3K)
class TestModSceneActBridge {
    @TempDir Path temp;
    SharedLevel source;
    ExampleModHarness harness;
    GameLoop loop;
    GameModule effective;
    InputHandler input;
    boolean oldTest;
    final List<RewindBoundary> boundaries = new ArrayList<>();
    static final ZoneKey.Mod DEST = new ZoneKey.Mod("starpost-valley", "valley");

    @BeforeEach void setup() throws Exception {
        source = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        var config = SonicConfigurationService.getInstance();
        oldTest = config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        harness = ExampleModHarness.build(Path.of("examples/starpost-valley"), temp.resolve("build"));
        effective = harness.apply(GameServices.module());
        TestEnvironment.configureGameModuleFixture(effective);
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.TEST_MODE_ENABLED,false);
        GameServices.graphics().initHeadless();
        input = new InputHandler(); loop = new GameLoop(input);
        loop.setGameMode(GameMode.MOD_SCENE);
    }
    @AfterEach void close() throws Exception {
        if (loop != null) { loop.setGameMode(GameMode.MASTER_TITLE_SCREEN); loop.modSceneHost.cleanup(); }
        if (harness != null) harness.close();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, oldTest);
        if (source != null) source.dispose();
    }
    void drainFade() {
        for (int i = 0; i < 400 && loop.resolveFadeManager().isActive(); i++) loop.resolveFadeManager().update();
        assertFalse(loop.resolveFadeManager().isActive(), "handoff fade must complete");
    }
    AbstractPlayableSprite player() { return (AbstractPlayableSprite) GameServices.camera().getFocusedSprite(); }
    ActLaunch launch() { return new ActLaunch(DEST, 0, CharacterKey.KNUCKLES, List.of(CharacterKey.TAILS),
            OptionalInt.of(896), OptionalInt.of(173), 7, Map.of("entry", "door")); }
    static final class Probe implements ModScene {
        SceneContext context;
        int updates, resumes, exits;
        ActResult result;
        public void enter(SceneContext context) { this.context = context; }
        public void update(SceneContext context) { updates++; }
        public void draw(SceneContext context, SceneCanvas canvas) {}
        public void resume(SceneContext context, ActResult result) { assertSame(this.context, context); resumes++; this.result=result; }
        public void exit(SceneContext context) { exits++; }
    }
    ModSceneActBridge openProbe(Probe probe) {
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> {});
        loop.modSceneHost.open(ModContextTestAccess.ownedScene("starpost-valley", () -> probe, boundary),
                new SceneServices(null, null, temp, null, () -> {}, () -> {}), 400, 224);
        return new ModSceneActBridge(loop, EngineServices.current(), (root, module, zone, launch) ->
                GameplayTeamBootstrapContext.registryOnly().openAndLoad(root, module, EngineServices.current(),
                        SonicConfigurationService.getInstance(), zone, launch.act(), mode -> {
                            loop.setGameplayMode(mode); mode.setRewindBoundaryReporter(boundaries::add);
                        }, launch));
    }
    @Test void nativeStartOverrideTeamRingsAudioAndRewindBoundariesRoundTrip() throws Exception {
        Probe probe = new Probe(); var bridge = openProbe(probe);
        probe.context.startAct(launch()); bridge.admitLaunch();
        assertTrue(loop.modSceneHost.isSuspended()); bridge.updateScene(input); assertEquals(0, probe.updates);
        drainFade();
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertEquals("knuckles", player().getCode());
        assertEquals(896, player().getCentreX()); assertEquals(173, player().getCentreY());
        assertEquals(7, GameServices.level().getLevelGamestate().getRings());
        assertEquals(1, GameServices.sprites().getSidekicks().size());
        assertTrue(boundaries.contains(RewindBoundary.LEVEL_LOAD), boundaries.toString());
        var runtime = loop.resolveGameplayModeContext();
        runtime.getRewindRegistry().capture();
        runtime.getLevelManager().setFrameCounter(41);
        runtime.getLevelManager().getLevelGamestate().setRings(3);
        runtime.getLevelManager().requestActExit(ActExit.LEFT, Map.of("door", "inn"));
        var pending = runtime.getRewindRegistry().capture();
        runtime.getLevelManager().requestActExit(ActExit.COMPLETED, Map.of("door", "wrong"));
        var beforeConsume = runtime.getRewindRegistry().capture();
        assertEquals(pending.entries().get("level-transition"), beforeConsume.entries().get("level-transition"));
        GameServices.audio().resetState(); assertNull(GameServices.audio().getAudioProfile());
        assertTrue(bridge.consumeExitOrHold(input)); assertEquals(0, probe.resumes);
        assertTrue(bridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE, loop.getCurrentGameMode());
        assertEquals(1, probe.resumes); assertEquals(ActExit.LEFT, probe.result.reason());
        assertEquals(Map.of("door", "inn"), probe.result.state());
        assertEquals(3, probe.result.rings()); assertEquals(41, probe.result.frames());
        assertNotNull(GameServices.audio().getAudioProfile(), "resume re-prepares ROM audio");
        assertTrue(boundaries.contains(RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE));
        assertNull(LevelSceneActAccess.consume(runtime.getLevelManager()));
        assertFalse(bridge.consumeExitOrHold(input)); assertEquals(1, probe.resumes);
        assertTrue(loop.modSceneHost.isOpen()); assertFalse(loop.modSceneHost.isSuspended());
        bridge.reset();
    }
    @Test void signedAndUnsignedNativeCentreWordsSurviveLaunch() {
        Probe probe = new Probe(); var bridge = openProbe(probe);
        var launch = new ActLaunch(launch().destination(),0,CharacterKey.KNUCKLES,List.of(),
                OptionalInt.of(0x8010),OptionalInt.of((short)0xff02),7,Map.of());
        probe.context.startAct(launch); bridge.admitLaunch(); drainFade();
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
        assertEquals(0x8010,Short.toUnsignedInt(player().getCentreX()));
        assertEquals(0xff02,Short.toUnsignedInt(player().getCentreY()));
        assertThrows(IllegalArgumentException.class, () -> new ActLaunch(launch.destination(),0,
                launch.main(),List.of(),OptionalInt.of(0x10000),OptionalInt.empty(),0,Map.of()));
        bridge.reset();
    }
    @Test void pendingExitRestoresWithRewindBeforeConsumption() {
        Probe probe = new Probe(); var bridge = openProbe(probe);
        probe.context.startAct(launch()); bridge.admitLaunch(); drainFade();
        var runtime = loop.resolveGameplayModeContext(); var registry = runtime.getRewindRegistry();
        var empty = registry.capture();
        runtime.getLevelManager().requestActExit(ActExit.LEFT, Map.of("door", "inn"));
        var pending = registry.capture();
        registry.restore(empty);
        assertNull(LevelSceneActAccess.consume(runtime.getLevelManager()));
        registry.restore(pending);
        assertTrue(bridge.consumeExitOrHold(input)); drainFade(); assertEquals(1, probe.resumes);
        bridge.reset();
    }
    @Test void holdEscapeClosesSuspendedSceneWithoutResume() {
        Probe probe = new Probe(); var bridge = openProbe(probe);
        probe.context.startAct(launch()); bridge.admitLaunch(); drainFade();
        loop.setReturnToMasterTitleHandler(() -> {
            loop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
            loop.resolveFadeManager().startFadeFromBlack(null);
        });
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
        for (int i=0; i<EscapeToMasterTitleController.HOLD_FRAMES; i++) {
            loop.getEscapeToMasterTitleController().update(GameMode.LEVEL,input); input.update();
        }
        drainFade();
        assertEquals(GameMode.MASTER_TITLE_SCREEN, loop.getCurrentGameMode());
        assertFalse(loop.modSceneHost.isOpen()); assertEquals(1, probe.exits); assertEquals(0, probe.resumes);
        bridge.reset();
    }
    @Test void creatorObjectFaultInActSafelyClosesSuspendedScene() {
        Probe probe = new Probe(); var bridge = openProbe(probe);
        probe.context.startAct(launch()); bridge.admitLaunch(); drainFade();
        var findings = new ModRuntimeFindingStore();
        var boundary = new ModFaultBoundary(Map.of(),findings, owners -> new ModStateSaveResult.Saved(), owners -> {});
        var plan = ModContextTestAccess.freezeWithObject("failing", "s3k", "fault", (spawn,registry) -> new AbstractObjectInstance(spawn,"fault") {
            public void update(int vIntRunCount, PlayableEntity player) { throw new IllegalStateException("act fault"); }
            public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) {}
        });
        var module = new ModBackedGamePatch(plan,boundary).apply(effective,(PatchContext)null);
        var registry = module.createObjectRegistry();
        var object = registry.create(new ObjectSpawn(896,173,0,0,0,false,173,-1,"failing","failing:fault"));
        var manager = new ObjectManager(List.of(),registry,-1,null,null,GameServices.graphics(),GameServices.camera(),
                GameServices.level().getObjectManager().getObjectServices());
        manager.addDynamicObject(object);
        assertFalse(Engine.runFrameWithModAbort(() -> ObjectCallbackDispatch.run(manager,object,
                () -> object.update(1,player())),
            () -> loop.setGameMode(GameMode.MASTER_TITLE_SCREEN)));
        assertTrue(findings.snapshot().containsKey("failing"));
        assertFalse(loop.modSceneHost.isOpen()); assertEquals(1,probe.exits); assertEquals(0,probe.resumes);
        bridge.reset();
    }

    @Test void starpostFarmActDoorMenuActGateFarmUsesSameSession() throws Exception {
        assertTrue(ModSceneLauncher.openStartupScene(loop, SonicConfigurationService.getInstance(), 0,
                GraphicsManager.getInstance(),400,224)); drainFade();
        assertTrue(loop.modSceneHost.debugJump("new sonic"), "Sonic 1 must be supplied for this round trip");
        assertTrue(loop.modSceneHost.debugJump("close"));
        Object scene = SceneHostTestAccess.scene(loop.modSceneHost), shell = field(scene,"shell");
        Object game = field(shell,"game"), play = call(shell,"screen");
        // Cross the real farm gate and fold, rather than calling the town launcher directly.
        int gate = scene.getClass().getClassLoader().loadClass("starpost.farm.FarmView").getField("GATE_X").getInt(null);
        assertTrue(loop.modSceneHost.debugJump("farm " + (gate+8) + " 30"));
        input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);
        for (int i=0;i<70 && loop.getCurrentGameMode()!=GameMode.LEVEL;i++) {
            input.refreshLogicalSnapshot(); loop.modSceneActBridge.updateScene(input); input.update(); drainFade();
        }
        input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_RELEASE); input.update();
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode()); assertTrue(loop.modSceneHost.isSuspended());
        Object town = effective.getGameService(scene.getClass().getClassLoader().loadClass("starpost.realtown.TownSession"));
        assertSame(game, call(town,"game"));
        var runner = new HeadlessTestRunner(player());
        runner.stepIdleFrames(1); // Retire S3K initial Process_Sprites before measuring world frames.
        long firstTick = (long)call(town,"ticks"); runner.stepIdleFrames(1);
        assertEquals(firstTick+1,call(town,"ticks"),"nearby admission anchors must elect one director");
        NativePositionOps.writeXPosResetSubpixel(player(),896); NativePositionOps.writeYPosResetSubpixel(player(),173);
        player().setAir(false); player().setXSpeed((short)0); player().setGSpeed((short)0);
        call(town,"input",false,true,false,false,false,-1); runner.stepIdleFrames(1);
        assertEquals("inn",call(call(town,"handBack"),"place"));
        int doorY = (int)call(call(town,"handBack"),"returnY");
        int wallet = (int)field(game,"rings"); GameServices.level().getLevelGamestate().setRings(2);
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode()); assertTrue((boolean)call(shell,"hasOverlay"));
        assertSame(scene,SceneHostTestAccess.scene(loop.modSceneHost)); assertSame(game,field(shell,"game"));
        assertSame(play,call(shell,"screen")); assertEquals(wallet,field(game,"rings"));
        // Close the actual inn menu through the scene controls.
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
            com.openggf.control.PlayerInputState.of(0,0,com.openggf.control.InputActionMasks.ACTION_B,
                com.openggf.control.InputActionMasks.ACTION_B,false,false), com.openggf.control.PlayerInputState.neutral()));
        loop.modSceneActBridge.updateScene(input); input.update();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
        assertFalse((boolean)call(shell,"hasOverlay"), "pad B closes the inn menu");
        loop.modSceneActBridge.updateScene(input); drainFade();
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
        assertEquals(896,player().getCentreX()); assertEquals(doorY,player().getCentreY());
        assertEquals(2,GameServices.level().getLevelGamestate().getRings());
        runner = new HeadlessTestRunner(player()); runner.stepIdleFrames(1);
        long beforeTick = (long)call(town,"ticks"); runner.stepIdleFrames(1);
        assertEquals(beforeTick+1,call(town,"ticks"),"door spawn admits exactly one director");
        NativePositionOps.writeXPosResetSubpixel(player(),150); NativePositionOps.writeYPosResetSubpixel(player(),173);
        player().setAir(false); player().setXSpeed((short)0); player().setGSpeed((short)0);
        call(town,"input",false,true,false,false,false,-1); runner.stepIdleFrames(1);
        assertEquals("farm_gate",call(call(town,"handBack"),"place"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode()); assertSame(play,call(shell,"screen"));
        assertTrue((boolean)call(play,"onFarm")); assertSame(game,field(shell,"game"));
        assertFalse((boolean)call(town,"active")); assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    static Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    static Object call(Object object,String name,Object... args) throws Exception {
        for (var method:object.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount()==args.length)
            return method.invoke(object,args);
        throw new NoSuchMethodException(name);
    }
}
