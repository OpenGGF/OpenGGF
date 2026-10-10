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
    RewindClassResolver oldResolver;
    final List<RewindBoundary> boundaries = new ArrayList<>();
    static final ZoneKey.Mod DEST = new ZoneKey.Mod("starpost-valley", "valley");

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
        runStarpostRoute("sonic",400);
    }

    @org.junit.jupiter.params.ParameterizedTest(name="{0} at {1}px real town round trip")
    @org.junit.jupiter.params.provider.CsvSource({"sonic,320","tails,320","knuckles,320","tails,400","knuckles,400"})
    void realTerrainRoundTripWalksAndRewinds(String farmer,int width) throws Exception {
        runStarpostRoute(farmer,width);
    }

    void runStarpostRoute(String farmer,int width) throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,
            width==320 ? "NATIVE_4_3" : "WIDE_16_9");
        config.resolveDisplayAspect();
        GameServices.audio().setBackend(new com.openggf.audio.HeadlessSmpsAudioBackend(config,GameServices.profiler()));
        assertTrue(ModSceneLauncher.openStartupScene(loop, SonicConfigurationService.getInstance(), 0,
                GraphicsManager.getInstance(),width,224)); drainFade();
        assertTrue(loop.modSceneHost.debugJump("new "+farmer), "Sonic 1 must be supplied for this round trip");
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
        assertEquals(width,GameServices.camera().getWidth());
        assertEquals(farmer,player().getCode());
        int initialWallet=(int)field(game,"rings");
        Object inventory=field(game,"inventory");
        call(inventory,"set",0,"ring_radish",2);
        assertTrue(effective.getGameplayPolicyProvider().hudProfile(DEST).orElseThrow().rows().isEmpty(),
            "stock S3K rows give way to the mod's S1 TIME and RINGS art");
        walkTo(runner,400);
        assertTownMusic(0x81);
        var registry=loop.resolveGameplayModeContext().getRewindRegistry();
        var checkpoint=registry.capture();
        Object townBefore=call(town,"capture");
        for(int i=0;i<30;i++) runner.stepFrame(false,false,false,true,false);
        var expected=registry.capture();
        registry.restore(checkpoint);
        assertEquals(townBefore,call(town,"capture"),"real terrain rewind preserves mod clock/items");
        for(int i=0;i<30;i++) runner.stepFrame(false,false,false,true,false);
        var replay=registry.capture();
        assertEquals(expected.entries().keySet(),replay.entries().keySet());
        for(String key:expected.entries().keySet()) {
            var diff=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,expected.entries().get(key),replay.entries().get(key));
            assertTrue(diff.isEmpty(),key+": "+diff.stream().map(d -> d.substring(0,Math.min(400,d.length()))).toList());
        }
        walkTo(runner,896);
        assertTownMusic(0x85);
        call(town,"input",false,true,false,false,false,-1); runner.stepIdleFrames(1);
        assertEquals("inn",call(call(town,"handBack"),"place"));
        int doorY = (int)call(call(town,"handBack"),"returnY");
        int wallet = (int)field(game,"rings");
        int health=GameServices.level().getLevelGamestate().getRings();
        assertTrue(wallet>initialWallet,"walking the real floor collects wallet rings");
        assertEquals(wallet-initialWallet,health,"native rings and wallet receive the same pickups");
        Object calendar=field(game,"calendar");
        Object time=call(calendar,"capture");
        assertEquals(2,call(inventory,"total","ring_radish"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode()); assertTrue((boolean)call(shell,"hasOverlay"));
        assertSame(scene,SceneHostTestAccess.scene(loop.modSceneHost)); assertSame(game,field(shell,"game"));
        assertSame(play,call(shell,"screen")); assertEquals(wallet,field(game,"rings"));
        // Buy the inn's first food through the real scene menu; native health stays separate.
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
            com.openggf.control.PlayerInputState.of(0,0,com.openggf.control.InputActionMasks.ACTION_A,
                com.openggf.control.InputActionMasks.ACTION_A,false,false), com.openggf.control.PlayerInputState.neutral()));
        loop.modSceneActBridge.updateScene(input); input.update();
        assertEquals(1,call(inventory,"total","radish_soup"));
        int spent=2*(int)call(call(game,"item","radish_soup"),"price");
        assertEquals(wallet-spent,field(game,"rings"));
        // Close the actual inn menu through the scene controls.
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
            com.openggf.control.PlayerInputState.of(0,0,com.openggf.control.InputActionMasks.ACTION_B,
                com.openggf.control.InputActionMasks.ACTION_B,false,false), com.openggf.control.PlayerInputState.neutral()));
        loop.modSceneActBridge.updateScene(input); input.update();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
        assertFalse((boolean)call(shell,"hasOverlay"), "pad B closes the inn menu");
        loop.modSceneActBridge.updateScene(input); drainFade();
        assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
        assertTrue(Math.abs(896-player().getCentreX())<=16); assertEquals(doorY,player().getCentreY());
        assertEquals(time,call(calendar,"capture"),"menu/relaunch preserves day clock fraction");
        assertEquals(wallet-spent,field(game,"rings")); assertEquals(2,call(inventory,"total","ring_radish"));
        assertEquals(1,call(inventory,"total","radish_soup"));
        assertEquals(health,GameServices.level().getLevelGamestate().getRings());
        runner = new HeadlessTestRunner(player()); runner.stepIdleFrames(1);
        long beforeTick = (long)call(town,"ticks"); runner.stepIdleFrames(1);
        assertEquals(beforeTick+1,call(town,"ticks"),"door spawn admits exactly one director");
        walkTo(runner,150);
        call(town,"input",false,true,false,false,false,-1); runner.stepIdleFrames(1);
        assertEquals("farm_gate",call(call(town,"handBack"),"place"));
        assertTrue(loop.modSceneActBridge.consumeExitOrHold(input)); drainFade();
        assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode()); assertSame(play,call(shell,"screen"));
        assertTrue((boolean)call(play,"onFarm")); assertSame(game,field(shell,"game"));
        assertFalse((boolean)call(town,"active"));
        assertTrue((int)field(game,"rings")>=wallet-spent); assertEquals(2,call(inventory,"total","ring_radish"));
        assertEquals(1,call(inventory,"total","radish_soup")); assertTownMusic(0x81);
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    @Test void realTownSchedulesWalkSlopesAndAllDoorsUseDecodedFloor() throws Exception {
        assertTrue(ModSceneLauncher.openStartupScene(loop,SonicConfigurationService.getInstance(),0,
            GraphicsManager.getInstance(),400,224)); drainFade();
        assertTrue(loop.modSceneHost.debugJump("new tails"));
        assertTrue(loop.modSceneHost.debugJump("close"));
        assertTrue(loop.modSceneHost.debugJump("day 2"));
        assertTrue(loop.modSceneHost.debugJump("time 759"));
        Object scene=SceneHostTestAccess.scene(loop.modSceneHost),shell=field(scene,"shell");
        Object game=field(shell,"game");
        call(field(game,"calendar"),"setDayMinutes",120); // Hold the schedule hour during this short check.
        assertTrue(loop.modSceneHost.debugJump("town enter"));
        loop.modSceneActBridge.admitLaunch(); drainFade();
        var runner=new HeadlessTestRunner(player()); runner.stepIdleFrames(2);
        Object town=effective.getGameService(scene.getClass().getClassLoader().loadClass("starpost.realtown.TownSession"));
        Object sonic=townVillager("sonic");
        int before=Math.round((float)call(sonic,"x"));
        call(field(game,"calendar"),"set",1,0,2,8*60);
        Set<Integer> floorHeights=new HashSet<>();
        for(int i=0;i<180;i++) {
            runner.stepIdleFrames(1);
            for(var object:GameServices.level().getObjectManager().getActiveObjects()) {
                if(object.getClass().getName().equals("starpost.realtown.TownVillager")&&(boolean)call(object,"visible"))
                    assertEquals(call(town,"walkFloor",Math.round((float)call(object,"x"))),call(object,"feet"),
                        call(object,"id")+" follows the walkable log route");
            }
            floorHeights.add((int)call(sonic,"feet"));
        }
        assertTrue(Math.round((float)call(sonic,"x"))<before-60,"Sonic follows the meadow to slope schedule");
        assertTrue(floorHeights.size()>1,"walk crosses real slope heights: "+floorHeights);
        int doors=0;
        for(var object:GameServices.level().getObjectManager().getActiveObjects()) {
            if(!object.getClass().getName().equals("starpost.realtown.TownDoor"))continue;
            Object place=call(object,"place"); int x=(int)call(place,"x");
            assertEquals(townFloor(town,x),object.getY(),"building/door base uses the real floor");
            doors++;
        }
        assertTrue(doors>=9);
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    @Test void realValleySeasonClaimsFollowCalendarAndRewindWithoutRecolouringHud() throws Exception {
        assertTrue(ModSceneLauncher.openStartupScene(loop,SonicConfigurationService.getInstance(),0,
            GraphicsManager.getInstance(),400,224)); drainFade();
        assertTrue(loop.modSceneHost.debugJump("new sonic"));
        assertTrue(loop.modSceneHost.debugJump("close"));
        Object scene=SceneHostTestAccess.scene(loop.modSceneHost),shell=field(scene,"shell");
        Object game=field(shell,"game"),calendar=field(game,"calendar");
        assertTrue(loop.modSceneHost.debugJump("town enter"));
        loop.modSceneActBridge.admitLaunch(); drainFade();
        GameServices.level().skipPendingInitialTitleCardPresentation();
        var runner=new HeadlessTestRunner(player()); runner.stepIdleFrames(2);
        var level=GameServices.level().getCurrentLevel();
        var registry=GameServices.paletteOwnershipRegistry();
        com.openggf.game.palette.PaletteWriteSupport.resolvePendingFrameWrites(registry,level,null,null);
        assertNotNull(GameServices.level().getAnimatedPaletteManager(),"contributed palette animation survives load");
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
        List<int[]> originals=new ArrayList<>();
        for(int line=0;line<4;line++) for(int color=0;color<16;color++)
            if("starpost-valley:seasons".equals(registry.ownerAt(com.openggf.game.palette.PaletteSurface.NORMAL,line,color))) {
                assertTrue(line>0 && !(line==1 && List.of(1,5,12,14,15).contains(color)) && !(line==3 && color==15));
                originals.add(new int[]{line,color,sega(level.getPalette(line).getColor(color))});
            }
        assertFalse(originals.isEmpty(),"runtime must receive the re-encoded ROM claims, not the placeholder");
        var loader=scene.getClass().getClassLoader();
        var tone=loader.loadClass("starpost.art.Tone");
        var colour=loader.loadClass("starpost.realvalley.ActSeasons").getMethod("colour",int.class,tone,int.class,boolean.class);
        var hud=level.getPalette(1).getColor(5); int host=sega(hud);
        var snapshot=loop.resolveGameplayModeContext().getRewindRegistry().capture();
        for(int season=0;season<4;season++) for(int light=0;light<3;light++) {
            call(calendar,"set",1,season,10,light==0?720:light==1?1080:1200);
            runner.stepIdleFrames(1);
            com.openggf.game.palette.PaletteWriteSupport.resolvePendingFrameWrites(registry,level,null,null);
            Object map=tone.getConstructor(int.class).newInstance(season);
            for(int[] c:originals) assertEquals((int)colour.invoke(null,c[2],map,light,false),
                sega(level.getPalette(c[0]).getColor(c[1])),"season="+season+" light="+light+" cell="+c[0]+":"+c[1]);
            assertEquals(host,sega(hud),"HUD cells remain host-owned");
        }
        loop.resolveGameplayModeContext().getRewindRegistry().restore(snapshot);
        runner.stepIdleFrames(1);
        com.openggf.game.palette.PaletteWriteSupport.resolvePendingFrameWrites(registry,level,null,null);
        for(int[] c:originals) assertEquals(c[2],sega(level.getPalette(c[0]).getColor(c[1])));
        assertTrue(harness.findings().isEmpty(),harness.findings().toString());
    }
    private static int sega(com.openggf.level.Palette.Color c) {
        return Math.round((c.r&255)*7/255f)<<1 | Math.round((c.g&255)*7/255f)<<5 | Math.round((c.b&255)*7/255f)<<9;
    }

    Object townVillager(String id) throws Exception {
        for(var object:GameServices.level().getObjectManager().getActiveObjects())
            if(object.getClass().getName().equals("starpost.realtown.TownVillager")&&id.equals(call(object,"id")))return object;
        throw new AssertionError("missing villager "+id);
    }

    void assertTownMusic(int id) {
        GameServices.audio().presentFrame(com.openggf.audio.presentation.PresentationMode.FORWARD);
        var music=GameServices.audio().captureLogicalSnapshot().presentation().activeMusic();
        assertNotNull(music,"town soundtrack reaches the production audio route");
        assertEquals(id,music.musicId());
        assertEquals(com.openggf.audio.rewind.AudioSourceDescriptor.Route.DONOR_MUSIC_ID,music.sourceDescriptor().route());
        assertEquals("s1",music.sourceDescriptor().donorGameId());
    }
    void walkTo(HeadlessTestRunner runner,int target) {
        for(int i=0;i<1600;i++) {
            int distance=target-player().getCentreX();
            double speed=player().getGSpeed()/256.0;
            if(Math.abs(distance)<=5 && Math.abs(speed)<0.6 && !player().getAir()) return;
            double stopping=speed*speed;
            int direction=Integer.signum(distance);
            if(Math.signum(speed)==direction && stopping>Math.abs(distance)-3) direction=-direction;
            runner.stepFrame(false,false,direction<0,direction>0,false);
            assertFalse(player().getDead(),"route died at "+player().getCentreX()+","+player().getCentreY());
        }
        fail("could not walk to "+target+"; centre="+player().getCentreX()+","+player().getCentreY()+" air="+player().getAir()+" speed="+player().getGSpeed()+" held="+player().isObjectControlled());
    }
    static int townFloor(Object town, int x) throws Exception {
        Object layout = call(town,"layout");
        return (int) call(field(layout,"ground"),"floorBelow",x,0);
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
