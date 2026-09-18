package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.rewind.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Independent production arena thresholds and real Load_Level/title admission. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozAct1ArenaProduction {
    private final EnumMap<SonicConfiguration,Object> saved=new EnumMap<>(SonicConfiguration.class);
    private HeadlessTestFixture fixture;
    @BeforeEach void setup(){
        var config=SonicConfigurationService.getInstance();
        for(var key:SonicConfiguration.values())if(config.hasSessionOverride(key))saved.put(key,config.getConfigValue(key));
        config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,true);
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
        fixture=HeadlessTestFixture.builder().withZoneAndAct(8,0).startPosition((short)0x43B0,(short)0x9D4).startPositionIsCentre().withFreshLevelStartLifecycle().build();
        fixture.sprite().setRingCount(99);
    }
    @AfterEach void cleanup(){CrossGameFeatureProvider.getInstance().resetState();var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();}
    private SozZoneRuntimeState state(){return S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();}
    private void step(){fixture.stepFrame(false,false,false,false,false);}
    private static void same(CompositeSnapshot first,CompositeSnapshot second){
        assertEquals(first.entries().keySet(),second.entries().keySet());
        for(String key:first.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,first.get(key),second.get(key)).isEmpty(),key+": "+RewindSnapshotDiff.diffKey(key,first.get(key),second.get(key)));
    }
    @Test void approachCreatesDoorAndThenMinibossThroughNativeAdmission(){
        var registry=fixture.gameplayMode().getRewindRegistry();boolean controller=false,boss=false;
        for(int frame=0;frame<1600;frame++){
            var before=registry.capture();step();var manager=GameServices.level().getObjectManager();
            controller|=!manager.activeObjectsOfType(SozAct1ArenaController.class).isEmpty();
            boss|=!manager.activeObjectsOfType(SozMinibossInstance.class).isEmpty();
            if(frame==10 || frame==500 || frame==1000 || frame==1400){
                var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);registry.restore(before);same(before,registry.capture());step();same(after,registry.capture());
            }
        }
        assertTrue(controller,"native background redraw allocates controller");assertTrue(boss,"door acknowledgment allocates Egg Golem");
        assertEquals(8,state().events().backgroundRoutine());
        assertFalse(fixture.sprite().getDead(),"stationary arena setup remains playable");
    }
    @Test void postResultsDoorFadeReloadsAndCompletesSeamlessEntry(){
        var events=state().events();events.initialized(true);events.backgroundRoutine(8);events.sandPosition(0x280<<16);
        state().requestMinibossPostResultsAlignmentComplete();
        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(),0x43A0);NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(),0x9ED);
        for(var sidekick:GameServices.sprites().getSidekicks()){NativePositionOps.writeXPosPreserveSubpixel(sidekick,0x43A0);NativePositionOps.writeYPosPreserveSubpixel(sidekick,0x9ED);}
        GameServices.level().getObjectManager().createDynamicObject(()->new SozAct1ArenaController.Door(new ObjectSpawn(0x439C,0x9D4,0,0,0,false,0)));
        boolean transferred=false,dark=false;
        for(int frame=0;frame<500;frame++){
            step();
            if(GameServices.level().getCurrentAct()==1){
                transferred=true;dark|=state().lighting().darknessLevel()==5;
                if(state().events().seamlessEntry())assertEquals(1799,state().lighting().masterTimer(),"native negative fade timer holds palette clocks");
            }
        }
        assertTrue(transferred,"real executor reloads Act2");assertTrue(dark,"loc55EFC initializes darkness only for seamless entry");
        assertEquals(8,state().events().foregroundRoutine());assertEquals(0x10,state().events().backgroundRoutine());
        assertFalse(state().events().seamlessEntry());assertFalse(fixture.sprite().isControlLocked());
        assertEquals((short)-0x100,fixture.camera().getMinYTarget());
        assertEquals(0x800,fixture.camera().getMaxYTarget());
        var registry=fixture.gameplayMode().getRewindRegistry();var before=registry.capture();step();var after=registry.capture();registry.restore(before);same(before,registry.capture());step();same(after,registry.capture());
    }
    @Test void cameraThresholdAndTwoRowRedrawKeepNativeDispatchBoundaries(){
        var driver=new com.openggf.game.sonic3k.events.Sonic3kSOZEvents();var events=state().events();events.initialized(true);
        fixture.camera().setY((short)0x95F);fixture.camera().setX((short)0x4310);driver.update(0,0);
        assertEquals(0,state().sandCorkBackgroundFlag());
        assertEquals(0x960,fixture.camera().getMaxYTarget());
        fixture.camera().setY((short)0x960);fixture.camera().setX((short)0x430F);driver.update(0,1);assertEquals(0,state().sandCorkBackgroundFlag());
        fixture.camera().setX((short)0x4310);driver.update(0,2);assertEquals(0xFFFF,state().sandCorkBackgroundFlag());assertEquals(0,events.backgroundRoutine());
        driver.update(0,3);assertEquals(4,events.backgroundRoutine());assertEquals(13,events.redrawRemaining());
        for(int tick=0;tick<6;tick++)driver.update(0,4+tick);assertEquals(4,events.backgroundRoutine());
        driver.update(0,10);assertEquals(8,events.backgroundRoutine());
        var manager=GameServices.level().getObjectManager();assertEquals(1,manager.activeObjectsOfType(SozAct1ArenaController.class).size());
        assertEquals(1,manager.activeObjectsOfType(SozAct1ArenaController.Mask.class).size());assertEquals(1,manager.activeObjectsOfType(SozAct1ArenaController.Door.class).size());
    }
    @Test void postResultsGateReadsNativeP1AndP2Boundaries(){
        var driver=new com.openggf.game.sonic3k.events.Sonic3kSOZEvents();var events=state().events();events.initialized(true);events.backgroundRoutine(8);state().requestMinibossPostResultsAlignmentComplete();
        var second=GameServices.sprites().getSidekicks().getFirst();
        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(),0x4377);NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(),0x9A8);
        NativePositionOps.writeXPosPreserveSubpixel(second,0x4378);NativePositionOps.writeYPosPreserveSubpixel(second,0x9A7);
        driver.update(0,1);assertEquals(8,events.backgroundRoutine());
        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(),0x4378);driver.update(0,2);assertEquals(8,events.backgroundRoutine());
        NativePositionOps.writeXPosPreserveSubpixel(second,0x4379);NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(),0x9A7);
        driver.update(0,3);assertEquals(8,events.backgroundRoutine());
        NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(),0x9A8);driver.update(0,4);assertEquals(0xC,events.backgroundRoutine());assertEquals(15,events.fadeDelay());
    }
    @Test void arenaAllocationKeepsEverySuccessfulPrefixAcrossRecreation(){
        step();state().consumeSandCorkBackgroundFlag();
        var manager=GameServices.level().getObjectManager();state().events().initialized(true);state().events().backgroundRoutine(8);
        for(int prefix=0;prefix<=3;prefix++){
            manager.getActiveObjects().stream().filter(object->object instanceof SozAct1ArenaController || object instanceof SozAct1ArenaController.Mask || object instanceof SozAct1ArenaController.Door).toList().forEach(manager::removeDynamicObject);
            var occupied=manager.getActiveObjects().stream().map(object->((com.openggf.level.objects.AbstractObjectInstance)object).getSlotIndex()).collect(java.util.stream.Collectors.toSet());
            for(int slot=4;slot<94;slot++)if(!occupied.contains(slot))manager.releaseDynamicSlot(slot);
            manager.reserveAllButNFreeSlots(prefix);
            var controller=manager.createDynamicObject(()->new SozAct1ArenaController(new ObjectSpawn(0,0,0,0,0,false,0)));
            if(controller!=null && !controller.isDestroyed())controller.allocateDoorSiblings();
            assertEquals(prefix,arenaCount());var registry=fixture.gameplayMode().getRewindRegistry();var snapshot=registry.capture();
            manager.setRewindInPlaceRestoreEnabledForTest(false);registry.restore(snapshot);same(snapshot,registry.capture());assertEquals(prefix,arenaCount());
        }
    }
    private long arenaCount(){return GameServices.level().getObjectManager().getActiveObjects().stream().filter(object->object instanceof SozAct1ArenaController || object instanceof SozAct1ArenaController.Mask || object instanceof SozAct1ArenaController.Door).count();}

}
