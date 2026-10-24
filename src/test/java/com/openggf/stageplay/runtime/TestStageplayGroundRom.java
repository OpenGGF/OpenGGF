package com.openggf.stageplay.runtime;

import com.openggf.audio.NullAudioBackend;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.mutation.*;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.StageplayGround.*;
import com.openggf.stageplay.route.StageplayObjectRoutePlanner;
import com.openggf.stageplay.route.StageplayVisualSupport;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayGroundRom {
    @BeforeEach void headless() {
        EngineServices.current().graphics().initHeadless();
        EngineServices.current().audio().setBackend(new NullAudioBackend());
    }
    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1GroundAndNativeLanding() throws Exception { verify("s1","sonic1.rom.path",List.of("sonic")); }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2GroundAndNativeLanding() throws Exception { verify("s2","sonic2.rom.path",List.of("sonic","tails")); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3kGroundAndNativeLanding() throws Exception { verify("s3k","s3k.rom.path",List.of("sonic","tails","knuckles")); }

    private void verify(String game, String property, List<String> characters) throws Exception {
        try (var library = SceneRomLibrary.open(Map.of(game,Path.of(System.getProperty(property))));
             var resources = new StageplayResources(library,false)) {
            var source = new SetSource(game,0,0,"","");
            int[] start = library.module(game).getZoneRegistry().getStartPosition(0,0);
            int top = Math.max(0,start[1]-400), bottom = top+1023;
            var visual = new Visual(2,28,14,ObjectPath.PRIMARY);
            var queries = new ArrayList<Query>();
            for (int i=0;i<40;i++) queries.add(new Query("column-"+i,start[0]+i*8,top,bottom,visual));
            var children = ProcessHandle.current().children().map(ProcessHandle::pid).toList();
            var before = SessionManager.getCurrentWorldSession();
            var objects = resources.ground(source,new Batch(queries,20000),() -> false);
            assertSame(before,SessionManager.getCurrentWorldSession(),"Entry queries must never open/replace a session");
            assertEquals(children,ProcessHandle.current().children().map(ProcessHandle::pid).toList(),"Forty columns must not launch another JVM");
            assertEquals(40,objects.answers().size());
            assertEquals(StopReason.NONE,objects.work().stopped());
            var level = com.openggf.game.session.GameplaySessionFactory.createCollisionOnlyLevel(library.module(game),resources.routeTerrain(source),0,0,EngineServices.current());
            var terrain = new com.openggf.physics.NativeRouteTerrainProbe(level,com.openggf.physics.BackgroundPlaneCollisionProvider.FOREGROUND_ONLY);
            int placements = 0;
            for (var answer : objects.answers()) for (var placement : answer.placements()) {
                placements++;
                var rest = assertInstanceOf(StageplayVisualSupport.Resting.class,StageplayVisualSupport.rest(terrain,visual.footprint(),placement.x(),placement.surfaceY()));
                assertEquals(placement.y(),rest.support().anchorY());
                assertEquals("",StageplayObjectRoutePlanner.validateSample(terrain,visual.footprint(),placement.x(),placement.y()));
                var support = assertInstanceOf(VisualSupport.class,placement.evidence());
                assertEquals(Math.min(support.left(),Math.min(support.centre(),support.right())),placement.surfaceY());
            }
            assertTrue(placements > 0,"Native set must expose real clear floors");
            var tiny = resources.ground(source,new Batch(queries.subList(0,2),20000,1,0),() -> false);
            assertEquals(StopReason.SCAN_LIMIT,tiny.work().stopped());
            assertTrue(tiny.answers().stream().allMatch(a -> a.outcome() == Outcome.INCOMPLETE));
            for (String unsupported : List.of("sonic","tails","knuckles")) if (!characters.contains(unsupported)) {
                var rejected = resources.ground(source,new Batch(List.of(new Query("unsupported",start[0],top,bottom,
                        new Player(game,unsupported,0))),20000),() -> false).answer("unsupported");
                assertEquals(Outcome.UNSUPPORTED,rejected.outcome(),game+" must not borrow an unavailable native body");
                assertEquals(Reason.PROFILE,rejected.refusals().getFirst().reason());
            }
            if (game.equals("s1")) {
                var rejected = resources.ground(source,new Batch(List.of(new Query("path",start[0],top,bottom,
                        new Player(game,"sonic",1))),20000),() -> false).answer("path");
                assertEquals(Outcome.UNSUPPORTED,rejected.outcome(),"S1 unified collision cannot honour player path 1");
            }
            for (String character : characters) {
                var playerQueries = new ArrayList<Query>();
                for (int i=0;i<16;i++) playerQueries.add(new Query("player-"+i,start[0]+i*8,top,bottom,new Player(game,character,0)));
                var proposals = resources.ground(source,new Batch(playerQueries,20000),() -> false);
                assertEquals(StopReason.NONE,proposals.work().stopped());
                var proposal = proposals.answers().stream().flatMap(a -> a.placements().stream()).filter(p -> p.angle()==0 || (p.angle() & 1)!=0).findFirst().orElseThrow(() -> new AssertionError("Need a native cardinal standing proposal for "+game+"/"+character+": "+proposals));
                var evidence = assertInstanceOf(PlayerTerrainProposal.class,proposal.evidence());
                assertEquals(0,evidence.distance());
                assertEquals(proposal.y()+evidence.yRadius(),proposal.surfaceY());
                var actor = new Actor("hero","Hero",game,character,List.of(new ActorKey(0,proposal.x(),proposal.y(),0,-1,false,true,1,Interpolation.HOLD)));
                var scene = StageplayDocument.empty(game,0,0,proposal.x()-100,proposal.y()-100,320,224).withDurationFrames(12)
                        .withActors(List.of(actor)).withPuppets(List.of(new PuppetTrack("hero",false,0,List.of())))
                        .withWorldCommands(List.of(new WorldCommand("clear",0,WorldCommandKind.CLEAR_OBJECTS,"",ObjectTarget.none())));
                var active = SessionManager.getCurrentGameplayMode();
                var world = active == null ? StageplayLiveWorld.openOffline(library) : StageplayLiveWorld.adoptOffline(library,active);
                resources.setLiveWorld(world);
                boolean landed = false;
                double landedX = 0, landedY = 0;
                for (int frame=0;frame<6;frame++) {
                    var live = resources.resolveFrame(scene,StageplayTimeline.sample(scene,frame));
                    var nativePlayer = SessionManager.getCurrentGameplayMode().getCamera().getFocusedSprite();
                    // FindFloor and authored placement use ROM centre words, not presentation fractions.
                    // Native landing preserves the subpixel word (CollisionSystem.moveForSensorResult).
                    assertEquals(proposal.x(),nativePlayer.getCentreX(),game+"/"+character+" native X word");
                    assertEquals(proposal.y(),nativePlayer.getCentreY(),game+"/"+character+" native Y word");
                    var pose = live.actors().getFirst();
                    if (landed) {
                        assertFalse(live.puppets().getFirst().airborne());
                        assertEquals(landedX,pose.x(),0,game+"/"+character+" post-landing X drift");
                        assertEquals(landedY,pose.y(),0,game+"/"+character+" post-landing Y drift");
                    } else if (!live.puppets().getFirst().airborne()) {
                        landed = true; landedX = pose.x(); landedY = pose.y();
                        assertEquals(proposal.surfaceY(),nativePlayer.getCentreY()+nativePlayer.getYRadius());
                    }
                }
                assertTrue(landed,"Native playback must clear air within six ticks");
                var castQuery = new Batch(List.of(new Query("live",proposal.x(),top,bottom,new Player(game,character,0),"hero")),20000);
                assertEquals(Outcome.FOUND,resources.ground(scene,5,castQuery,() -> false).answer("live").outcome());
                var nativePlayer = SessionManager.getCurrentGameplayMode().getCamera().getFocusedSprite();
                assertFalse(nativePlayer.getRolling());
                nativePlayer.applyCustomRadii(nativePlayer.getXRadius()-1,nativePlayer.getYRadius()-1);
                var customSize = resources.ground(scene,5,castQuery,() -> false).answer("live");
                assertEquals(Outcome.UNSUPPORTED,customSize.outcome(),"Custom native sizes cannot borrow a stock standing profile");
                assertEquals(Reason.NON_STOCK_STATE,customSize.refusals().getFirst().reason());
                nativePlayer.restoreDefaultRadii();
                assertEquals(Outcome.FOUND,resources.ground(scene,5,castQuery,() -> false).answer("live").outcome());
                world.close();
            }
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void sceneFrameReadsLiveMutationAndEntryLayoutRetainsDecodedTerrain() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s1",Path.of(System.getProperty("sonic1.rom.path"))));
             var resources = new StageplayResources(library,false)) {
            var source = new SetSource("s1",0,0,"","");
            var visual = new Visual(2,28,14,ObjectPath.PRIMARY);
            var query = new Query("floor",128,0,1023,visual);
            var batch = new Batch(List.of(query),20000);
            var entry = resources.ground(source,batch,() -> false);
            assertEquals(Outcome.FOUND,entry.answer("floor").outcome());
            var scene = StageplayDocument.empty("s1",0,0,0,0,320,224).withDurationFrames(12);
            var active = SessionManager.getCurrentGameplayMode();
            var world = active == null ? StageplayLiveWorld.openOffline(library) : StageplayLiveWorld.adoptOffline(library,active);
            resources.setLiveWorld(world);
            var initial = resources.ground(scene,0,batch,() -> false);
            assertEquals(Outcome.FOUND,initial.answer("floor").outcome());
            var owned = SessionManager.getCurrentGameplayMode();
            var terrain = owned.getLevelManager().getCurrentLevel();
            var pipeline = new ZoneLayoutMutationPipeline();
            var context = new LayoutMutationContext(LevelMutationSurface.forLevel(terrain),effects -> {});
            int centreBlock = query.x()/terrain.getBlockPixelSize();
            for (int bx=Math.max(0,centreBlock-1);bx<=centreBlock+1;bx++) for (int by=0;by<terrain.getMap().getHeight();by++) {
                int column=bx,row=by;
                pipeline.applyImmediately(c -> c.surface().setBlockInMap(0,column,row,0),context);
            }
            int clock = owned.getLevelManager().getFrameCounter();
            long seed = owned.getRng().getSeed();
            var mutated = resources.ground(scene,0,batch,() -> false);
            assertEquals(0,mutated.work().preparationTicks(),"Already prepared frame must not replay");
            assertEquals(Outcome.NONE_IN_BAND,mutated.answer("floor").outcome());
            assertEquals(clock,owned.getLevelManager().getFrameCounter()); assertEquals(seed,owned.getRng().getSeed());
            assertEquals(entry.answer("floor"),resources.ground(source,batch,() -> false).answer("floor"));
            owned.getGameStateManager().setReverseGravityActive(true);
            var player = new Batch(List.of(new Query("hero",128,0,1023,new Player("s1","sonic",0))),20000);
            var reversed = resources.ground(scene,0,player,() -> false);
            assertEquals(Outcome.UNSUPPORTED,reversed.answer("hero").outcome());
            assertEquals(Reason.REVERSE_GRAVITY,reversed.answer("hero").refusals().getFirst().reason());
            owned.getGameStateManager().setReverseGravityActive(false);
            var cold = resources.ground(scene,11,new Batch(List.of(query),20000,1000,0),() -> false);
            assertEquals(Outcome.INCOMPLETE,cold.answer("floor").outcome());
            assertEquals(StopReason.PREPARATION_LIMIT,cold.work().stopped());
        }
    }
}
