package com.openggf.tests;
import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;
@RequiresRom(SonicGame.SONIC_3K)
class TestSozLightAndGhostsProduction {
    private final EnumMap<SonicConfiguration,Object> saved=new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;
    @BeforeEach void setup() {
        config=SonicConfigurationService.getInstance();
        for(var k:SonicConfiguration.values())if(config.hasSessionOverride(k))saved.put(k,config.getConfigValue(k));
        config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() {
        CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    @Test void placedLightPullReleasesAndRestoresSharedLighting() {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1).startPosition((short)0x2B0,(short)0x330).startPositionIsCentre().withFreshLevelStartLifecycle().build();
        var level=GameServices.level();
        var spawn=level.getCurrentLevel().getObjects().stream().filter(o->o.objectId()==0x41&&o.subtype()==4).findFirst().orElseThrow();
        NativePositionOps.writeXPosPreserveSubpixel(f.sprite(),spawn.x());
        NativePositionOps.writeYPosPreserveSubpixel(f.sprite(),spawn.y()+48);
        f.sprite().setAir(true);f.sprite().setXSpeed((short)0);f.sprite().setYSpeed((short)0);
        for(int i=0;i<3;i++)f.stepFrame(false,false,false,false,false);
        var light=find(SozLightSwitchObjectInstance.class);
        NativePositionOps.writeXPosPreserveSubpixel(f.sprite(),light.getX());
        NativePositionOps.writeYPosPreserveSubpixel(f.sprite(),light.getY()+48);
        f.sprite().setAir(true);f.sprite().setXSpeed((short)0);f.sprite().setYSpeed((short)0);
        f.stepFrame(false,false,false,false,false);
        assertTrue(light.isPlayerHeld(f.sprite()),"light="+light.getX()+","+light.getY()+" player="+f.sprite().getCentreX()+","+f.sprite().getCentreY());
        var state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        state.lighting().initializeSeamlessDarkness();
        for(int i=0;i<20;i++) {
            var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);
            if(i==5||i==15)replay(f,before,false,"light pull and shared fade");
        }
        assertEquals(0,state.lighting().darknessLevel());
        assertEquals(32,find(SozLightSwitchObjectInstance.class).extension());
        var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,true);replay(f,before,true,"light jump release");
        assertFalse(find(SozLightSwitchObjectInstance.class).isPlayerHeld(f.sprite()));
        assertNotNull(level.getObjectRenderManager().getRenderer(Sonic3kObjectArtKeys.SOZ_LIGHT_SWITCH));
    }
    @Test void capsuleButtonOpensCheckpointAndRecreatesEscapeGraph() {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1).startPosition((short)0xB50,(short)0x380)
                .startPositionIsCentre().withFreshLevelStartLifecycle().build();
        for(int i=0;i<3;i++)f.stepFrame(false,false,false,false,false);
        var capsule=find(SozHyudoroCapsuleObjectInstance.class);
        assertFalse(capsule.opened());
        var p=f.sprite();NativePositionOps.writeXPosPreserveSubpixel(p,0xB50);NativePositionOps.writeYPosPreserveSubpixel(p,0x3E0-0x24-4-p.getYRadius()-3);
        p.setAir(true);p.setYSpeed((short)0x100);p.setXSpeed((short)0);
        boolean opened=false;
        for(int i=0;i<30;i++){
            var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);
            if(find(SozHyudoroCapsuleObjectInstance.class).opened()){
                replay(f,before,false,"capsule checkpoint and children");opened=true;break;
            }
        }
        assertTrue(opened);
        assertEquals(1,GameServices.level().getCheckpointState().getLastCheckpointIndex());
        assertEquals(6,GameServices.level().getObjectManager().getActiveObjects().stream().filter(SozHyudoroCapsuleObjectInstance.EscapeGhost.class::isInstance).count());
        var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);replay(f,before,false,"escaped ghosts and fragment graph");
        assertNotNull(GameServices.level().getObjectRenderManager().getRenderer(Sonic3kObjectArtKeys.SOZ_GHOSTS));
    }
    @Test void bossRoomEntryBrightensAndFadesExistingGhostsAtTheNativeThreshold() {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1)
                .startPosition((short)0x4FF0,(short)0x480).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        f.camera().setY((short)0x4FF);f.camera().setFrozen(true);
        f.sprite().setInvulnerableFrames(1000);f.sprite().setRingCount(99);
        f.stepFrame(false,false,false,false,false);
        var state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        assertEquals(0x20,state.events().backgroundRoutine());
        ((CheckpointState)GameServices.level().getCheckpointState()).saveCheckpoint(1,0x4FF0,0x480,false);
        state.lighting().initializeSeamlessDarkness();
        var manager=GameServices.level().getObjectManager();
        if(manager.activeObjectsOfType(SozHyudoroControllerObjectInstance.class).isEmpty())
            manager.addDynamicObject(new SozHyudoroControllerObjectInstance(new ObjectSpawn(0x120,0xA0,0xAA,0,0,false,0)));
        for(int i=0;i<80;i++)f.stepFrame(false,false,false,false,false);
        assertFalse(manager.activeObjectsOfType(SozHyudoroBodyObjectInstance.class).isEmpty());
        NativePositionOps.writeXPosResetSubpixel(f.sprite(),0x5000);
        f.stepFrame(false,false,false,false,false);
        assertEquals(5,state.lighting().darknessLevel(),"camera below $500 must not enter the arena");
        f.camera().setY((short)0x500);
        var before=f.gameplayMode().getRewindRegistry().capture();
        f.stepFrame(false,false,false,false,false);
        replay(f,before,false,"boss room light, ghost fade and wall allocation");
        assertEquals(0,state.lighting().darknessLevel());
        assertEquals(0x24,state.events().backgroundRoutine());
        assertEquals(8,manager.activeObjectsOfType(SozBossWallObjectInstance.class).size());
        for(int i=0;i<45;i++)f.stepFrame(false,false,false,false,false);
        assertEquals(0,state.lighting().fadeStep());
        assertTrue(manager.activeObjectsOfType(SozHyudoroBodyObjectInstance.class).isEmpty());
    }
    @Test void dynamicGhostAttackAndFadeRestoreOwnerAndBody() {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1).withFreshLevelStartLifecycle().build();
        ((CheckpointState)GameServices.level().getCheckpointState()).saveCheckpoint(1,0x140,0x3AC,false);
        var state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        state.lighting().initializeSeamlessDarkness();
        if(GameServices.level().getObjectManager().getActiveObjects().stream().noneMatch(SozHyudoroControllerObjectInstance.class::isInstance))
            GameServices.level().getObjectManager().addDynamicObject(new SozHyudoroControllerObjectInstance(new ObjectSpawn(0x120,0xA0,0xAA,0,0,false,0)));
        boolean attack=false;
        for(int i=0;i<220;i++){
            var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);
            if(i==10)replay(f,before,false,"ghost apparition");
            if(GameServices.level().getObjectManager().getActiveObjects().stream().filter(SozHyudoroBodyObjectInstance.class::isInstance).map(SozHyudoroBodyObjectInstance.class::cast).anyMatch(g->g.getCollisionFlags()==0xD7)){
                replay(f,before,false,"ghost world attack");attack=true;break;
            }
        }
        assertTrue(attack);state.lighting().resetLight();
        var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);replay(f,before,false,"ghost light fade");
        for(int i=0;i<40;i++)f.stepFrame(false,false,false,false,false);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream().noneMatch(SozHyudoroBodyObjectInstance.class::isInstance));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0xB00,0x1B8,0", "0x1470,0x310,4"})
    void placedArtTriggersReplaceRomTilesAndRestore(int x,int y,int subtype) {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1).startPosition((short)(x-0x40),(short)y)
                .startPositionIsCentre().withFreshLevelStartLifecycle().build();
        for(int i=0;i<3;i++)f.stepFrame(false,false,false,false,false);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(o->o instanceof SozHyudoroArtTriggerObjectInstance&&o.getSpawn().subtype()==subtype));
        NativePositionOps.writeXPosPreserveSubpixel(f.sprite(),x);NativePositionOps.writeYPosPreserveSubpixel(f.sprite(),y);
        f.sprite().setXSpeed((short)0);f.sprite().setYSpeed((short)0);f.sprite().setAir(true);
        var before=f.gameplayMode().getRewindRegistry().capture();f.stepFrame(false,false,false,false,false);
        assertFalse(GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(o->o instanceof SozHyudoroArtTriggerObjectInstance&&o.getSpawn().subtype()==subtype));
        replay(f,before,false,"capsule/enemy ROM art trigger subtype "+subtype);
    }
    private static <T>T find(Class<T>type){return GameServices.level().getObjectManager().getActiveObjects().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();}
    private static void replay(HeadlessTestFixture fixture,CompositeSnapshot before,boolean jump,String label) {
        var registry=fixture.gameplayMode().getRewindRegistry();var after=registry.capture();
        // Exercise identity relinking, not only the in-place object fast path.
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(before);same(before,registry.capture(),label+" restore");
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,""));
        fixture.stepFrame(false,false,false,false,jump);same(after,registry.capture(),label+" forward replay");
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(String key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),
                ()->label+" "+key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
}
