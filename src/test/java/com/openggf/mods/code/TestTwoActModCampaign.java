package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.render.*;
import com.openggf.game.save.*;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.dataselect.*;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.*;
import com.openggf.physics.GroundSensor;
import com.openggf.tests.*;
import com.openggf.tools.modsdk.JarPackager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import javax.tools.ToolProvider;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Executes the maintained original campaign through compiler, SDK admission, normal resolver and native gameplay. */
@Isolated
class TestTwoActModCampaign {
    static final ZoneKey.Mod KEY = new ZoneKey.Mod("sample-tide-circuit","tide-circuit");
    static final Path PROJECT = Path.of("src/test/resources/mods/sample-two-act-campaign-src/project");
    @TempDir Path temp;
    @BeforeEach void reset() {
        TestEnvironment.resetAll();
        var configuration=SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,"NATIVE_4_3");
        configuration.resolveDisplayAspect();
        assertEquals(320,configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
    }
    @AfterEach void closeSession() { GroundSensor.setLevelManager(null); SessionManager.clear(); GameModuleRegistry.reset(); TestEnvironment.resetAll(); }
    record Fixture(ModRuntime runtime, GameModule root, GameModule resolved) implements AutoCloseable {
        public void close() throws Exception { runtime.close(); }
    }
    Path compilePackage() throws Exception {
        Path output = Files.createDirectory(temp.resolve("creator-classes"));
        try (var paths = Files.walk(PROJECT.resolve("src/main/resources"))) {
            for (Path source : paths.toList()) {
                Path target = output.resolve(PROJECT.resolve("src/main/resources").relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(target); else Files.copy(source,target);
            }
        }
        var arguments = new ArrayList<>(List.of("--release","21","-classpath",TestSessionOutputPaths.compiledClasses().toAbsolutePath().toString(),"-d",output.toString()));
        try (var paths=Files.walk(PROJECT.resolve("src/main/java"))) { paths.filter(path -> path.toString().endsWith(".java")).sorted().map(Path::toString).forEach(arguments::add); }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,arguments.toArray(String[]::new)),"External creator source must compile");
        Path repo=Files.createDirectory(temp.resolve("mods")); Path jar=repo.resolve("tide.jar");
        JarPackager.packageDirectory(output,jar); return jar;
    }
    Fixture load(Path jar) throws Exception {
        var scanned = new DefaultModRepositoryScanner().scan(jar.getParent().toAbsolutePath());
        var validated = new ModCatalogValidator(jar.getParent().toAbsolutePath(),ModInputLimits.production(),(game,id)->true).validate(scanned);
        ModDescriptor descriptor=assertInstanceOf(ModDescriptor.class,validated.entries().getFirst());
        assertFalse(descriptor.hasErrors(),descriptor.findings().toString());
        var catalog = new EffectiveCatalogBuilder().build(validated.entries(),new ModState(1,List.of(new ModState.Entry(KEY.ownerModId(),true,0,true,descriptor.sha256()))));
        assertEquals(1,catalog.effective().orderedEnabled().size());
        var runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(catalog.effective(),Set.of(KEY.ownerModId()));
        runtime.installStorageRoot(temp.resolve("saves"));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),ignored->new ModStateSaveResult.Saved(),runtime::disableOwnersForProcess));
        var plan=runtime.newRegistrationPlan(); assertTrue(runtime.registrationFailures().isEmpty(),runtime.registrationFailures().toString());
        var resolver=new ModuleResolutionService(List.of(),new EffectiveCatalogPatchEnablement(catalog.effective()),new LogicalRomResolver(()->null),
                SonicConfigurationService.getInstance(),ignored->plan);
        GameModule root=new Sonic2GameModule();
        GameModule resolved=resolver.resolveForLaunch(root,GameplayLaunchRequest.fromConfig(SonicConfigurationService.getInstance(),"s2"),ModuleResolutionService.LaunchPolicy.STANDARD);
        return new Fixture(runtime,root,resolved);
    }
    @Test void externalCampaignPublishesTwoBoundedActsWithIndependentFactoryLoads() throws Exception {
        try (Fixture fixture=load(compilePackage())) {
            var registry=fixture.resolved.getZoneRegistry(); int zone=registry.resolveZoneKey(KEY).orElseThrow();
            assertEquals(2,registry.getActCount(zone));
            assertEquals(2,fixture.runtime.registrationPlans().get(KEY.ownerModId()).preparedZones().size());
            assertEquals(new ZoneProgressionPlan.Successor(zone,1),registry.progressionPlan().next(registry.progressionTopology(),zone,0));
            assertEquals(0,fixture.runtime.runtimeDisabledOwners().size());
        }
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> shortCases() {
        var cases=new ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for(int width:new int[]{320,400,512,640,800}) {
            for(String main:List.of("sonic","tails")) {
                cases.add(org.junit.jupiter.params.provider.Arguments.of(width,main,"","off"));
                cases.add(org.junit.jupiter.params.provider.Arguments.of(width,main,main.equals("sonic")?"tails":"sonic","off"));
            }
        }
        for(int width:new int[]{320,800}) {
            cases.add(org.junit.jupiter.params.provider.Arguments.of(width,"sonic","","s1"));
            for(String main:List.of("sonic","tails","knuckles"))
                cases.add(org.junit.jupiter.params.provider.Arguments.of(width,main,"","s3k"));
            cases.add(org.junit.jupiter.params.provider.Arguments.of(width,"sonic","tails","s3k"));
            cases.add(org.junit.jupiter.params.provider.Arguments.of(width,"tails","sonic","s3k"));
        }
        return cases.stream();
    }
    @org.junit.jupiter.params.ParameterizedTest(name="both acts: {0}px {1}+{2}, donor={3}")
    @org.junit.jupiter.params.provider.MethodSource("shortCases")
    void shortNativeAndSupportedDonorRoutesRestoreAndReplay(int width,String character,String follower,String donor) throws Exception {
        var romFile=RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile!=null,"Requires configured S2 World REV01 ROM");
        java.io.File donorFile=switch(donor) {
            case "s1" -> RomTestUtils.ensureSonic1RomAvailable();
            case "s3k" -> RomTestUtils.ensureSonic3kRomAvailable();
            default -> null;
        };
        if(!donor.equals("off")) assumeTrue(donorFile!=null,"Requires configured " + donor + " donor ROM");
        var configuration=SonicConfigurationService.getInstance();
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,follower);
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
        if(donorFile!=null) configuration.setSessionOverride(donor.equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_3K_ROM,donorFile.getAbsolutePath());
        try(Rom rom=new Rom();Fixture fixture=load(compilePackage())) {
            assertTrue(rom.open(romFile.getAbsolutePath()));RomManager.getInstance().setRom(rom);
            EngineServices.current().graphics().initHeadless();
            var root=fixture.root;
            var gameplay=SessionManager.openGameplaySession(root,fixture.resolved,StockGameDataSources.pinned(rom,root),null);
            GameModuleRegistry.setCurrent(fixture.resolved);GameplaySessionFactory.attachManagers(gameplay,EngineServices.current());
            gameplay.getLevelManager().setRewindClassResolver(new ModClassResolver(fixture.runtime,getClass().getClassLoader()));
            if(!donor.equals("off")) {
                CrossGameFeatureProvider.getInstance().initialize(donor);
                assertTrue(CrossGameFeatureProvider.isActive());
                assertEquals(donor,CrossGameFeatureProvider.getInstance().getDonorGameId());
            }
            var team=GameplayTeamBootstrap.registerActiveTeam(fixture.resolved,gameplay.getSpriteManager(),configuration);
            var main=team.mainSprite();assertEquals(follower.isEmpty()?0:1,team.sidekicks().size());
            gameplay.getCamera().setFocusedSprite(main);gameplay.getCamera().setFrozen(false);
            assertEquals(width,gameplay.getCamera().getWidth());
            var manager=gameplay.getLevelManager();int zone=fixture.resolved.getZoneRegistry().resolveZoneKey(KEY).orElseThrow();
            for(int act=0;act<2;act++) {
                manager.loadZoneAndAct(zone,act);GroundSensor.setLevelManager(manager);gameplay.getCamera().updatePosition(true);
                var runner=new HeadlessTestRunner(main);runner.stepIdleFrames(60);
                assertFalse(main.getAir(),"Native participant grounds on authored collision");
                dispatchRender(gameplay);byte[] initial=gameplay.getZoneRuntimeRegistry().current().captureBytes();
                var captured=gameplay.getRewindRegistry().capture();
                int initialX=main.getCentreX();
                for(int frame=0;frame<24;frame++) runner.stepFrame(false,false,false,true,false);
                dispatchRender(gameplay);
                assertTrue(main.getCentreX()>initialX,"Actual participant traverses original floor");
                int expectedX=main.getCentreX(),expectedY=main.getCentreY();short expectedSpeed=main.getXSpeed();
                byte[] expected=gameplay.getZoneRuntimeRegistry().current().captureBytes();
                List<Integer> followerPositions=team.sidekicks().stream().flatMap(sprite->java.util.stream.Stream.of(
                        (int)sprite.getCentreX(),(int)sprite.getCentreY())).toList();
                gameplay.getRewindRegistry().restore(captured);
                assertArrayEquals(initial,gameplay.getZoneRuntimeRegistry().current().captureBytes());
                String events="mod:"+KEY.ownerModId()+":zone-events/"+KEY.localName();
                assertEquals(captured.get(events),gameplay.getRewindRegistry().capture().get(events),"Event rewind spot restored");
                for(int frame=0;frame<24;frame++) runner.stepFrame(false,false,false,true,false);
                dispatchRender(gameplay);
                assertEquals(expectedX,main.getCentreX());assertEquals(expectedY,main.getCentreY());assertEquals(expectedSpeed,main.getXSpeed());
                assertArrayEquals(expected,gameplay.getZoneRuntimeRegistry().current().captureBytes(),"Forward replay repeats owned runtime consumers");
                assertEquals(followerPositions,team.sidekicks().stream().flatMap(sprite->java.util.stream.Stream.of(
                        (int)sprite.getCentreX(),(int)sprite.getCentreY())).toList(),"Team lifecycle replays");
            }
        }
    }

    @Test void bothActsExecuteGameplayContributionsRewindSaveRespawnAndResultsHandoff() throws Exception {
        var romFile=RomTestUtils.ensureSonic2RomAvailable(); assumeTrue(romFile!=null,"Requires configured S2 World REV01 ROM");
        try (Rom rom=new Rom(); Fixture fixture=load(compilePackage())) {
            assertTrue(rom.open(romFile.getAbsolutePath())); RomManager.getInstance().setRom(rom);
            EngineServices.current().graphics().initHeadless();
            var root=fixture.root;
            var gameplay=SessionManager.openGameplaySession(root,fixture.resolved,StockGameDataSources.pinned(rom,root),null);
            GameplaySessionFactory.attachManagers(gameplay,EngineServices.current());
            gameplay.getLevelManager().setRewindClassResolver(new ModClassResolver(fixture.runtime,getClass().getClassLoader()));
            var main=GameplayTeamBootstrap.registerActiveTeam(fixture.resolved,gameplay.getSpriteManager(),SonicConfigurationService.getInstance()).mainSprite();
            gameplay.getCamera().setFocusedSprite(main); gameplay.getCamera().setFrozen(false);
            var manager=gameplay.getLevelManager(); int zone=fixture.resolved.getZoneRegistry().resolveZoneKey(KEY).orElseThrow();
            manager.loadZoneAndAct(zone,0); GroundSensor.setLevelManager(manager); gameplay.getCamera().updatePosition(true);
            var runner=new HeadlessTestRunner(main);
            for (int act=0;act<2;act++) {
                if(act==1) assertEquals(1,manager.getCurrentAct(),"Actual results path installed the second act");
                assertEquals(KEY,fixture.resolved.getZoneRegistry().zoneKey(manager.getCurrentZone()));
                assertTrue(gameplay.getWaterSystem().hasWater(manager.getCurrentLevel().getZoneIndex(),act));
                runner.stepIdleFrames(60);
                assertFalse(main.getAir(),"Original floor supports the actual native player");
                int startX=main.getCentreX();
                for(int frame=0;frame<32;frame++) runner.stepFrame(false,false,false,true,false);
                assertTrue(main.getCentreX()>startX,"Actual route traverses original collision geometry");
                dispatchRender(gameplay);
                byte[] initial=gameplay.getZoneRuntimeRegistry().current().captureBytes();
                var counts=ByteBuffer.wrap(initial); for(int metric=0;metric<5;metric++) assertTrue(counts.getInt()>0,"Each runtime consumer executed");
                byte tile=manager.getCurrentLevel().getPattern(2).getPixel(0,0);
                var snapshot=gameplay.getRewindRegistry().capture();
                runner.stepIdleFrames(8); dispatchRender(gameplay);
                gameplay.getRewindRegistry().restore(snapshot);
                assertArrayEquals(initial,gameplay.getZoneRuntimeRegistry().current().captureBytes(),"Active act state round trips through engine registry");
                assertEquals(tile,manager.getCurrentLevel().getPattern(2).getPixel(0,0),"Owned animated art is regenerated from captured state");
                runner.stepIdleFrames(8); assertFalse(Arrays.equals(initial,gameplay.getZoneRuntimeRegistry().current().captureBytes()));
                var save=SaveSessionContext.forSlot("s2",1,new SelectedTeam("sonic",List.of()),zone,act);
                var payload=new S2SaveSnapshotProvider().capture(SaveReason.PROGRESSION_SAVE,RuntimeSaveCapture.capture(gameplay,save));
                assertFalse(payload.containsKey("zone")); assertEquals(KEY,S2SavedZone.read(payload).zoneKey());
                assertEquals(new com.openggf.game.dataselect.DataSelectDestination(zone,act),fixture.resolved.getDataSelectHostProfile().resolveLoadDestination(payload));
                var absent=new S2DataSelectProfile(()->root.getZoneRegistry()).resolveLoadDestination(payload);
                assertEquals(new com.openggf.game.dataselect.DataSelectDestination(0,0),absent,"Missing/disabled owner recovers to stock");
                manager.loadZoneAndAct(zone,act); // respawn/load creates fresh contributions and a separate rewind timeline
                assertEquals(0,ByteBuffer.wrap(gameplay.getZoneRuntimeRegistry().current().captureBytes()).getInt());
                for(int frame=0;frame<400 && !manager.getCheckpointState().isActive();frame++)
                    runner.stepFrame(false,false,false,true,false);
                assertTrue(manager.getCheckpointState().isActive(),"Native checkpoint was reached through the authored route");
                var checkpoint=(com.openggf.game.CheckpointState)manager.getCheckpointState();
                int checkpointX=checkpoint.getSavedX(), checkpointY=checkpoint.getSavedY();
                main.setDead(true); manager.respawnPlayer();
                assertFalse(main.getDead()); assertEquals(checkpointX,main.getCentreX() & 0xffff);
                assertEquals(checkpointY,main.getCentreY() & 0xffff);
                assertEquals(0,ByteBuffer.wrap(gameplay.getZoneRuntimeRegistry().current().captureBytes()).getInt(),
                        "Death reload constructs a fresh owned runtime graph");
                boolean completionSeen=false;
                for(int frame=0;frame<2400 && manager.getCurrentZone()==zone && manager.getCurrentAct()==act;frame++) {
                    runner.stepFrame(false,false,false,true,false);
                    completionSeen|=gameplay.getGameStateManager().isActCompletionSignalActive();
                }
                assertTrue(completionSeen,act==0?"Native signpost activated the results sequence":"Original act-two finish gate activated its ring tally");
                if(act==0) assertEquals(1,manager.getCurrentAct(),"Native tally completion loads the second act");
                else assertNotEquals(zone,manager.getCurrentZone(),"Original final tally hands off to the stock successor");
            }
        }
    }

    @Test void independentActTwoFinishGateRestoresItsTallyAndRecreatesAfterFreshLoad() throws Exception {
        var romFile=RomTestUtils.ensureSonic2RomAvailable();assumeTrue(romFile!=null,"Requires configured S2 World REV01 ROM");
        try(Rom rom=new Rom();Fixture fixture=load(compilePackage())) {
            assertTrue(rom.open(romFile.getAbsolutePath()));RomManager.getInstance().setRom(rom);
            EngineServices.current().graphics().initHeadless();var root=fixture.root;
            var gameplay=SessionManager.openGameplaySession(root,fixture.resolved,StockGameDataSources.pinned(rom,root),null);
            GameModuleRegistry.setCurrent(fixture.resolved);GameplaySessionFactory.attachManagers(gameplay,EngineServices.current());
            gameplay.getLevelManager().setRewindClassResolver(new ModClassResolver(fixture.runtime,getClass().getClassLoader()));
            var main=GameplayTeamBootstrap.registerActiveTeam(fixture.resolved,gameplay.getSpriteManager(),SonicConfigurationService.getInstance()).mainSprite();
            gameplay.getCamera().setFocusedSprite(main);gameplay.getCamera().setFrozen(false);
            var manager=gameplay.getLevelManager();int zone=fixture.resolved.getZoneRegistry().resolveZoneKey(KEY).orElseThrow();
            var runner=new HeadlessTestRunner(main);
            for(int lifetime=0;lifetime<2;lifetime++) {
                manager.loadZoneAndAct(zone,1);GroundSensor.setLevelManager(manager);gameplay.getCamera().updatePosition(true);
                assertFalse(gameplay.getGameStateManager().isActCompletionSignalActive(),"Fresh load clears the previous gate signal");
                runner.stepIdleFrames(60);
                for(int frame=0;frame<500 && !gameplay.getGameStateManager().isActCompletionSignalActive();frame++)
                    runner.stepFrame(false,false,false,true,false);
                assertTrue(gameplay.getGameStateManager().isActCompletionSignalActive(),"Original act-two gate is reached through native movement");
                int score=gameplay.getGameStateManager().getScore();
                var snapshot=gameplay.getRewindRegistry().capture();
                runner.stepIdleFrames(16);int advancedScore=gameplay.getGameStateManager().getScore();
                assertTrue(advancedScore>score,"Original ring tally awards the collected-ring bonus");
                gameplay.getRewindRegistry().restore(snapshot);
                assertEquals(score,gameplay.getGameStateManager().getScore());
                runner.stepIdleFrames(16);assertEquals(advancedScore,gameplay.getGameStateManager().getScore(),"Restored gate repeats its exact tally");
                assertTrue(fixture.runtime.runtimeDisabledOwners().isEmpty(),"Owned factory recreation and replay remain admitted");
                if(lifetime==0) continue;
                for(int frame=0;frame<300 && manager.getCurrentZone()==zone;frame++) runner.stepIdleFrames(1);
                assertNotEquals(zone,manager.getCurrentZone(),"Independent original tally executes the authored progression edge");
            }
        }
    }
    static void dispatchRender(GameplayModeContext gameplay) {
        var camera=gameplay.getCamera(); var manager=gameplay.getLevelManager();
        gameplay.getSpecialRenderEffectRegistry().dispatch(SpecialRenderEffectStage.AFTER_SPRITES,
                new SpecialRenderEffectContext(camera,manager.getFrameCounter(),manager,EngineServices.current().graphics()));
    }
}
