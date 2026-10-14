package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** ROM-placed local approaches and real death/reload, independent of long cold routes. */
@RequiresRom(SonicGame.SONIC_3K)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class TestLrzSszCheckpointRoutes {
    // Independent skdisasm Levels/{LRZ,SSZ}/Object Pos binaries, locked-on SKL IDs.
    private static final List<Post> POSTS=List.of(
            new Post(9,0,1,0xB66,0x469),new Post(9,0,2,0xEC0,0x968),
            new Post(9,0,3,0x19BF,0x428),new Post(9,0,4,0x2180,0x8A8),
            new Post(9,0,5,0x240E,0xA48),new Post(9,0,6,0x2A3E,0x7A8),
            new Post(9,1,4,0x1380,0x988),new Post(9,1,1,0x17E0,0x5A8),
            new Post(9,1,5,0x19C0,0xE8),new Post(9,1,2,0x2140,0x8A8),
            new Post(9,1,3,0x2CF0,0x6E8),new Post(22,0,1,0x9C0,0x368),
            new Post(10,0,2,0x640,0x5E8),new Post(10,0,3,0x14C0,0xE8),
            new Post(10,0,4,0x1880,0x968));
    private record Post(int zone,int act,int index,int x,int y) {
        boolean supports(String character){return character.equals("knuckles")?zone==9:true;}
    }
    private record Team(String main,String followers,String donor) { }
    private static final Team NATIVE_SONIC=new Team("sonic","","off");
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}

    @org.junit.jupiter.api.Order(0)
    @Test void independentPlacementInventoryIncludesTheNoPostKnucklesFinalArena() {
        var actual=new ArrayList<Post>();
        for(int[] layout:new int[][]{{9,0},{9,1},{22,0},{10,0},{10,1}}) {
            SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,
                    layout[0]==10&&layout[1]==1?"knuckles":"sonic");
            HeadlessTestFixture.builder().withZoneAndAct(layout[0],layout[1]).build();
            for(var spawn:GameServices.level().getCurrentLevel().getObjects())if(spawn.objectId()==Sonic3kObjectIds.STAR_POST)
                actual.add(new Post(layout[0],layout[1],spawn.subtype()&0x7F,spawn.x(),spawn.y()));
        }
        var order=Comparator.comparingInt(Post::zone).thenComparingInt(Post::act).thenComparingInt(Post::x);
        assertEquals(POSTS.stream().sorted(order).toList(),actual.stream().sorted(order).toList());
    }
    @org.junit.jupiter.api.Order(10)
    @Test void ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost() throws java.io.IOException {
        var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,"sonic","tails","off",null,
                null,null,null,false,false,null,null,false,null,false);
        var movie=new com.openggf.debug.playback.Bk2MovieLoader().loadMovieOrInputLog(java.nio.file.Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-tails-cold-complete-320.bk2"));
        try(var session=new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),10,0,settings);
            var checkpoint=GameServices.level().getCheckpointState();
            assertFalse(checkpoint.isActive());
            int inputs=0;
            while(inputs<7912&&checkpoint.getLastCheckpointIndex()<2) {
                session.step(movie.getFrame(inputs++));
                GameServices.graphics().runPendingRenderThreadTasks();
                assertFalse(session.player().getDead(),"ordinary opening remains alive at input "+inputs);
            }
            assertTrue(inputs<7912,"ordinary route reaches the physical post before its established upper walkway; "
                    +"index="+checkpoint.getLastCheckpointIndex()+" P1="+session.player().getCentreX()+","+session.player().getCentreY());
            assertEquals(2,checkpoint.getLastCheckpointIndex());
            assertEquals(0x640,checkpoint.getSavedX()&65535);
            assertEquals(0x5E8,checkpoint.getSavedY()&65535);
        }
    }
    static Stream<Arguments> firstWitnesses(){return Stream.of(POSTS.get(0),POSTS.get(6),POSTS.get(11),POSTS.get(12))
            .map(post->Arguments.of(post,NATIVE_SONIC,java.util.Arrays.stream(WidescreenAspect.values()).filter(aspect->aspect.pixelWidth()==320).findFirst().orElseThrow()));}
    static Stream<Arguments> allProducts(){
        var rows=new ArrayList<Arguments>();
        for(var post:POSTS)for(var aspect:WidescreenAspect.values()) {
            for(String donor:new String[]{"off","s1","s2"})for(String main:new String[]{"sonic","tails","knuckles"})
                if(post.supports(main)&&SozAcceptanceConfigurations.supportsCharacter(donor,main))
                    rows.add(Arguments.of(post,new Team(main,"",donor),aspect));
            // Comma rosters exercise the configured engine API, not panel normalization.
            for(var team:List.of(new Team("sonic","tails","off"),new Team("sonic","sonic,sonic","off"),
                    new Team("sonic","tails,knuckles,sonic,tails,knuckles,sonic,tails","off"),
                    new Team("sonic","sonic,sonic","s1"),new Team("sonic","tails,sonic,tails","s2"),
                    new Team("tails","sonic,tails,sonic,tails,sonic,tails,sonic","s2")))
                rows.add(Arguments.of(post,team,aspect));
        }
        return rows.stream();
    }
    @org.junit.jupiter.api.Order(1)
    @ParameterizedTest @MethodSource("firstWitnesses")
    void firstPostOfEachLayoutHasARealContactAndDeathBoundary(Post post,Team team,WidescreenAspect aspect) throws java.io.IOException{run(post,team,aspect);}
    @org.junit.jupiter.api.Order(1)
    @Test void lrz1PostFiveHasARealContactAndDeathBoundary() throws java.io.IOException {
        run(POSTS.get(4),NATIVE_SONIC,java.util.Arrays.stream(WidescreenAspect.values())
                .filter(aspect->aspect.pixelWidth()==320).findFirst().orElseThrow());
    }
    @org.junit.jupiter.api.Order(2)
    @Test void s2TailsWithSevenFollowersReplaysAfterEachCheckpointDeath() throws java.io.IOException {
        run(POSTS.get(0),new Team("tails","sonic,tails,sonic,tails,sonic,tails,sonic","s2"),
                java.util.Arrays.stream(WidescreenAspect.values()).filter(aspect->aspect.pixelWidth()==800).findFirst().orElseThrow());
    }
    @org.junit.jupiter.api.Order(20)
    @ParameterizedTest @MethodSource("allProducts")
    void everyPostPreservesConfiguredOwnersAcrossTwoDeaths(Post post,Team team,WidescreenAspect aspect) throws java.io.IOException{run(post,team,aspect);}

    private void run(Post post,Team team,WidescreenAspect aspect) throws java.io.IOException {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,team.main());
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,team.followers());
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,true);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        SozAcceptanceConfigurations.selectDisplay(aspect);
        if(!team.donor().equals("off"))config.setSessionOverride(team.donor().equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_2_ROM,
                java.util.Objects.requireNonNull(System.getProperty(team.donor().equals("s1")?"sonic1.rom.path":"sonic2.rom.path")));
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
        // LRZ1 post5 sits beside a left-side wall that expels an initially
        // overlapping player into the post. Approach its open right ledge;
        // every setup must remain outside sub_2D028's literal8px touch box.
        boolean fromRight=post.zone()==9&&post.act()==0&&post.index()==5;
        int approachX=post.x()+(fromRight?0x18:-0x18);
        var builder=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(post.zone(),post.act())
                .startPosition((short)approachX,(short)(post.y()+4)).startPositionIsCentre().withFreshLevelStartLifecycle();
        if(!team.donor().equals("off"))builder.withCrossGameDonation(team.donor());
        var f=builder.build();
        if(post.zone()==10) {
            // Declared saved-post-1 bootstrap selects the ROM branch past SSZ's
            // arrival, independently of the physical posts2..4 under test.
            assertInstanceOf(com.openggf.game.CheckpointState.class, GameServices.level().getCheckpointState())
                    .saveCheckpoint(1,approachX,post.y()+4,false);
        }
        f.stepIdleFrames(1);
        roster(team,aspect);
        var save=GameServices.level().getCheckpointState();assertTrue(save.getLastCheckpointIndex()<post.index(),"the placed post under test is untouched: "+post+" "+team+" "+aspect
                +" saved="+save.getLastCheckpointIndex()+" P1="+f.sprite().getCentreX()+","+f.sprite().getCentreY());
        var registry=f.gameplayMode().getRewindRegistry();var before=registry.capture();int approach=0;
        while(approach<60&&save.getLastCheckpointIndex()<post.index()){f.stepFrame(false,false,fromRight,!fromRight,false);approach++;}
        assertFalse(f.sprite().getDead());assertTrue(save.isActive(),()->"placed contact "+post+" P1="+f.sprite().getCentreX()+","+f.sprite().getCentreY());
        assertEquals(post.index(),save.getLastCheckpointIndex());assertEquals(post.x(),save.getSavedX()&65535);
        assertEquals(post.y(),save.getSavedY()&65535);var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture(),"before"+cycle);roster(team,aspect);
            f.runner().primeInputState(new Bk2FrameInput(0,0,0,false,""));
            for(int n=0;n<approach;n++)f.stepFrame(false,false,fromRight,!fromRight,false);
            same(after,registry.capture(),"forward"+cycle);roster(team,aspect);
            registry.restore(before);same(before,registry.capture(),"reverse"+cycle);
            registry.restore(after);same(after,registry.capture(),"after"+cycle);
        }
        var input=new InputHandler();var neutral=new Bk2FrameInput(0,0,0,false,"");
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(neutral,neutral));
        var loop=new GameLoop(input);loop.setGameplayMode(f.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        assertTrue(loop.ownsGameplayFadeLifecycle(),"actual production loop owns each fade service");
        try {
            for(int cycle=0;cycle<2;cycle++) {
                for(int n=0;n<30;n++){loop.step();GameServices.graphics().runPendingRenderThreadTasks();}
                var objects=GameServices.level().getObjectManager();var runtime=GameServices.zoneRuntimeRegistry().current();
                var followers=List.copyOf(GameServices.sprites().getRegisteredSidekicks());
                int lives=GameServices.gameState().getLives();
                assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath(),"declared production death stimulus");
                long outgoing=0;boolean loaded=false;
                for(int n=0;n<1200;n++) {
                    loop.step();GameServices.graphics().runPendingRenderThreadTasks();
                    var rewind=f.gameplayMode().getRewindController();
                    if(GameServices.level().getObjectManager()!=objects) {
                        loaded=true;assertEquals(post.zone(),GameServices.level().getCurrentZone());assertEquals(post.act(),GameServices.level().getCurrentAct());
                        assertEquals(post.index(),GameServices.level().getCheckpointState().getLastCheckpointIndex());
                        var player=GameServices.sprites().getMainPlayable();assertEquals(post.x(),player.getCentreX()&65535);assertEquals(post.y(),player.getCentreY()&65535);
                        assertFalse(player.getDead());assertEquals(lives-1,GameServices.gameState().getLives(),"one actual life per death");
                        assertEquals(followers,GameServices.sprites().getRegisteredSidekicks());
                        assertNotSame(runtime,GameServices.zoneRuntimeRegistry().current());
                        assertTrue(outgoing>10,"outgoing history actually exists");
                        assertTrue(rewind==null||rewind.currentFrame()<outgoing,"fresh timeline retires outgoing history");
                        roster(team,aspect);break;
                    }
                    if(rewind!=null)outgoing=Math.max(outgoing,rewind.currentFrame());
                }
                assertTrue(loaded,"real death reload cycle"+cycle);boolean released=false;
                for(int n=0;n<700;n++) {
                    loop.step();GameServices.graphics().runPendingRenderThreadTasks();
                    var title=GameServices.module().getTitleCardProvider();
                    if(loop.getCurrentGameMode()==GameMode.LEVEL&&(title==null||title.isComplete())
                            &&!f.gameplayMode().getFadeManager().isActive()&&!f.gameplayMode().getFadeManager().hasPendingCompletion()
                            &&!GameServices.level().hasPendingFreshLevelTransitionBoundary()
                            &&!GameServices.sprites().getMainPlayable().isControlLocked()) {
                        released=true;break;
                    }
                }
                assertTrue(released,"actual title/fade controls release");assertFalse(GameServices.sprites().getMainPlayable().getDead());roster(team,aspect);
                assertEquals(lives-1,GameServices.gameState().getLives(),"title release did not hide a second restart");
                replayReleasedWorld(f,loop,input,post,team,aspect,cycle);
            }
        } finally {loop.closePresence();}
    }
    private static void replayReleasedWorld(HeadlessTestFixture fixture,GameLoop loop,InputHandler input,
                                            Post post,Team team,WidescreenAspect aspect,int death) {
        var registry=fixture.gameplayMode().getRewindRegistry();
        var before=registry.capture();int lives=GameServices.gameState().getLives();
        advanceReleased(loop,input,post,team,aspect,lives);
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture(),"incoming death="+death+" before="+cycle);roster(team,aspect);
            advanceReleased(loop,input,post,team,aspect,lives);
            same(after,registry.capture(),"incoming death="+death+" forward="+cycle);roster(team,aspect);
            registry.restore(before);same(before,registry.capture(),"incoming death="+death+" reverse="+cycle);roster(team,aspect);
            registry.restore(after);same(after,registry.capture(),"incoming death="+death+" after="+cycle);roster(team,aspect);
        }
    }
    private static void advanceReleased(GameLoop loop,InputHandler input,Post post,Team team,WidescreenAspect aspect,int lives) {
        var neutral=new Bk2FrameInput(0,0,0,false,"");
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(neutral,neutral));
        for(int frame=0;frame<45;frame++) {
            loop.step();GameServices.graphics().runPendingRenderThreadTasks();
            assertEquals(GameMode.LEVEL,loop.getCurrentGameMode(),"released ordinary world remains active");
            assertFalse(GameServices.sprites().getMainPlayable().getDead(),"released-world window survives input="+frame);
            assertEquals(lives,GameServices.gameState().getLives(),"no hidden restart in incoming replay");
            assertEquals(post.index(),GameServices.level().getCheckpointState().getLastCheckpointIndex());
            roster(team,aspect);
        }
    }
    private static void roster(Team team,WidescreenAspect aspect) {
        SozAcceptanceConfigurations.assertDisplay(aspect);SozAcceptanceConfigurations.assertUsableTeam(team.donor());
        var main=GameServices.sprites().getMainPlayable();assertEquals(team.main(),main.getCode());assertSame(main,GameServices.camera().getFocusedSprite());
        var followers=GameServices.sprites().getRegisteredSidekicks();
        assertEquals(team.followers().isEmpty()?List.of():List.of(team.followers().split(",")),followers.stream().map(p->p.characterKey().persisted()).toList());
        var identities=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<AbstractPlayableSprite,Boolean>());identities.add(main);
        var previous=main;
        for(var follower:followers) {
            assertTrue(identities.add(follower),"duplicate character names retain distinct live owners");
            assertTrue(follower.isCpuControlled());assertNotNull(follower.getCpuController());assertSame(previous,follower.getCpuController().getLeader());previous=follower;
        }
        assertEquals(!team.donor().equals("off"),CrossGameFeatureProvider.isActive());
        if(!team.donor().equals("off"))assertEquals(team.donor(),CrossGameFeatureProvider.getInstance().getDonorGameId());
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(var key:expected.entries().keySet()){var diff=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));assertTrue(diff.isEmpty(),()->label+" "+key+": "+diff);}
    }
}
