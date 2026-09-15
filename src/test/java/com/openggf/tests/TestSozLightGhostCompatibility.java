package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Short real interactions for SOZ2 character, viewport, donor and follower breadth. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozLightGhostCompatibility {
    record Scenario(String main,int width,String donor,String followers) {}
    static Stream<Scenario> scenarios(){
        List<Scenario> rows=new ArrayList<>();
        for(String main:List.of("sonic","tails","knuckles"))for(int width:new int[]{320,400,512,640,800})rows.add(new Scenario(main,width,"off",""));
        rows.add(new Scenario("sonic",320,"off","tails"));
        rows.add(new Scenario("sonic",320,"s1","tails"));
        rows.add(new Scenario("sonic",400,"s2","tails"));
        rows.add(new Scenario("sonic",800,"off","tails,knuckles"));
        return rows.stream();
    }
    private final EnumMap<SonicConfiguration,Object> saved=new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;
    @BeforeEach void setup(){
        config=SonicConfigurationService.getInstance();
        for(var k:SonicConfiguration.values())if(config.hasSessionOverride(k))saved.put(k,config.getConfigValue(k));
    }
    @AfterEach void cleanup(){
        CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    private HeadlessTestFixture boot(Scenario row,int x,int y){
        config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,row.main());
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,row.followers());
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,WidescreenAspect.NATIVE_4_3.name());config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,row.width());
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!row.donor().equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,row.donor());
        if(!row.donor().equals("off")){
            var rom=row.donor().equals("s1")?RomTestUtils.ensureSonic1RomAvailable():RomTestUtils.ensureSonic2RomAvailable();
            assertNotNull(rom,"required donor ROM "+row.donor());
            config.setSessionOverride(row.donor().equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
        }
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
        var builder=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1).startPosition((short)x,(short)y).startPositionIsCentre().withFreshLevelStartLifecycle();
        if(!row.donor().equals("off"))builder.withCrossGameDonation(row.donor());
        var f=builder.build();assertEquals(row.width(),f.camera().getWidth()&0xFFFF);
        assertEquals(row.followers().isEmpty()?0:row.followers().split(",").length,GameServices.sprites().getRegisteredSidekicks().size());
        assertEquals(!row.donor().equals("off"),CrossGameFeatureProvider.isActive());
        if(!row.donor().equals("off"))assertEquals(row.donor(),CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!row.donor().equals("s1"),f.sprite().getGameRules().playerCapability().spindashEnabled());
        return f;
    }
    @ParameterizedTest @MethodSource("scenarios")
    void lightPullJumpAndParticipantStateReplay(Scenario row){
        var f=boot(row,0x2B0,0x330);var p=f.sprite();
        for(int i=0;i<3;i++)f.stepFrame(false,false,false,false,false);
        var light=find(SozLightSwitchObjectInstance.class);
        assertTrue(light.isPlayerHeld(p),"placed switch must capture the main character");
        var state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();state.lighting().initializeSeamlessDarkness();
        // Additional followers physically enter the same native P2-style handle
        // contract; registration alone is not interaction coverage.
        if(row.followers().contains(","))for(var follower:GameServices.sprites().getRegisteredSidekicks()){
            NativePositionOps.writeXPosResetSubpixel(follower,light.getX());
            NativePositionOps.writeYPosResetSubpixel(follower,light.getY()+light.extension()+48);
            follower.setAir(true);follower.setXSpeed((short)0);follower.setYSpeed((short)0);
        }
        for(int i=0;i<20;i++){
            var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);
            if(i==1||i==15)replay(f,before,false,"switch participant/pull state");
        }
        assertEquals(0,state.lighting().darknessLevel());assertEquals(32,find(SozLightSwitchObjectInstance.class).extension());
        if(row.followers().contains(","))for(var follower:GameServices.sprites().getRegisteredSidekicks())assertTrue(find(SozLightSwitchObjectInstance.class).isPlayerHeld(follower));
        var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,true);replay(f,before,true,"switch jump release");
        assertFalse(find(SozLightSwitchObjectInstance.class).isPlayerHeld(p));assertTrue(p.getAir());assertFalse(p.getDead());
    }
    @ParameterizedTest @MethodSource("scenarios")
    void ghostAttackAndLightFadeReplay(Scenario row){
        var f=boot(row,0x140,0x3AC);var p=f.sprite();p.setRingCount(10);
        for(int i=0;i<200;i++)f.stepFrame(false,false,false,false,false);
        assertNotNull(find(SozHyudoroControllerObjectInstance.class),"native title must create controller");
        if(!row.main().equals("knuckles"))((CheckpointState)GameServices.level().getCheckpointState()).saveCheckpoint(1,0x140,0x3AC,false);
        var state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();state.lighting().initializeSeamlessDarkness();
        boolean attack=false;
        for(int i=0;i<320;i++){
            var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);
            if(i==3)replay(f,before,false,"ghost appearance");
            if(GameServices.level().getObjectManager().getActiveObjects().stream().filter(SozHyudoroBodyObjectInstance.class::isInstance).map(SozHyudoroBodyObjectInstance.class::cast).anyMatch(o->o.getCollisionFlags()==0xD7)){
                replay(f,before,false,"ghost world attack");attack=true;break;
            }
        }
        assertTrue(attack,"ghost must enter world attack");
        state.lighting().resetLight();var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);replay(f,before,false,"ghost light fade");
        for(int i=0;i<40;i++)f.stepFrame(false,false,false,false,false);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream().noneMatch(SozHyudoroBodyObjectInstance.class::isInstance));
        assertFalse(p.getDead());
    }
    @ParameterizedTest @MethodSource("scenarios")
    void capsuleButtonLandingCheckpointAndChildrenReplay(Scenario row){
        var f=boot(row,0xB50,0x380);var p=f.sprite();
        for(int i=0;i<3;i++)f.stepFrame(false,false,false,false,false);
        boolean knuckles=row.main().equals("knuckles");assertEquals(knuckles,find(SozHyudoroCapsuleObjectInstance.class).opened());
        NativePositionOps.writeXPosResetSubpixel(p,0xB50);NativePositionOps.writeYPosResetSubpixel(p,0x3E0-0x24-4-p.getYRadius()-3);
        p.setAir(true);p.setXSpeed((short)0);p.setYSpeed((short)0x100);
        boolean landed=false;
        for(int i=0;i<30;i++){
            var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);
            if(GameServices.level().getObjectManager().getRidingObject(p) instanceof SozHyudoroCapsuleObjectInstance.Button){replay(f,before,false,"capsule button landing");landed=true;break;}
        }
        assertTrue(landed,"must land on the capsule button");
        var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);replay(f,before,false,"capsule open/checkpoint graph");
        assertTrue(find(SozHyudoroCapsuleObjectInstance.class).opened());
        if(knuckles)assertFalse(GameServices.level().getCheckpointState().isActive(),"already-open Knuckles capsule must not create a checkpoint");
        else assertEquals(1,GameServices.level().getCheckpointState().getLastCheckpointIndex());
        assertEquals(knuckles?0:6,GameServices.level().getObjectManager().getActiveObjects().stream().filter(SozHyudoroCapsuleObjectInstance.EscapeGhost.class::isInstance).count());
        before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);replay(f,before,false,"capsule escaped-ghost graph");assertFalse(p.getDead());
    }
    private static <T>T find(Class<T>type){return GameServices.level().getObjectManager().getActiveObjects().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();}
    private static void replay(HeadlessTestFixture f,CompositeSnapshot before,boolean jump,String label){
        var r=f.gameplayMode().getRewindRegistry();var after=r.capture();r.restore(before);same(before,r.capture(),label+" restore");
        f.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,""));f.stepFrame(false,false,false,false,jump);same(after,r.capture(),label+" forward");
    }
    private static void same(CompositeSnapshot a,CompositeSnapshot b,String label){
        assertEquals(a.entries().keySet(),b.entries().keySet(),label);
        for(String key:a.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),()->label+" "+key+": "+RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)));
    }
}
