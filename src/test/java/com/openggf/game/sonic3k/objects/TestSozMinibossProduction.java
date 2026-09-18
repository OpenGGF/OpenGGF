package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.*;
import com.openggf.level.objects.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Positioned boss injection; arena-event admission is verified by the owning event tests. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozMinibossProduction {
    @Test void productionAwakeningArtJobsAndChildGraphReplayExactly(){
        var config=SonicConfigurationService.getInstance();var saved=new EnumMap<SonicConfiguration,Object>(SonicConfiguration.class);
        for(var key:SonicConfiguration.values())if(config.hasSessionOverride(key))saved.put(key,config.getConfigValue(key));
        try{
            config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
            CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
            var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,0).startPosition((short)0x4380,(short)0x980).startPositionIsCentre().withFreshLevelStartLifecycle().build();
            fixture.sprite().setRingCount(99);
            var manager=GameServices.level().getObjectManager();
            var placed=manager.createDynamicObject(()->new Sonic3kObjectRegistry().create(new ObjectSpawn(0x439D,0x9F7,0x97,0,0,false,0)));
            assertInstanceOf(SozMinibossInstance.class,placed,"locked-on $97 registry binding");
            assertNotNull(GameServices.level().getObjectRenderManager().getRenderer(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SOZ_MINIBOSS));
            var registry=fixture.gameplayMode().getRewindRegistry();boolean parts=false;
            for(int tick=0;tick<650;tick++){
                var before=registry.capture();fixture.stepFrame(false,false,false,false,false);
                parts|=manager.getActiveObjects().stream().filter(SozMinibossChild.class::isInstance).count()>=10;
                if(tick==30 || tick==130 || tick==250 || tick==480){
                    var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);registry.restore(before);
                    same(before,registry.capture(),"restore "+tick);fixture.stepFrame(false,false,false,false,false);same(after,registry.capture(),"replay "+tick);
                }
            }
            assertTrue(parts,"placed production scene reaches articulated eight-part body");
        }finally{CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();}
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label){
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet()){var diff=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));assertTrue(diff.isEmpty(),label+" "+key+": "+diff);}
    }
}
