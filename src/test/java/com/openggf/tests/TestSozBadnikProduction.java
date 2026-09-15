package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.objects.badniks.*;
import com.openggf.level.objects.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Short independent placed-family runs through the production object and rewind owners. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozBadnikProduction {
    @ParameterizedTest
    @CsvSource({"0,0x330,0x5D4,SkorpBadnikInstance,320,sonic,tails,off",
        "0,0xA30,0xBDC,SandwormBadnikInstance,320,sonic,tails,off",
        "0,0xE50,0xF0,RocknBadnikInstance,320,sonic,tails,off",
        "1,0x180,0x674,SkorpBadnikInstance,320,sonic,tails,off",
        "1,0x550,0x3DC,SandwormBadnikInstance,320,sonic,tails,off",
        "0,0x330,0x5D4,SkorpBadnikInstance,352,tails,none,off",
        "0,0xE50,0xF0,RocknBadnikInstance,400,knuckles,none,off",
        "1,0x180,0x674,SkorpBadnikInstance,528,sonic,'tails,knuckles',off",
        "0,0xA30,0xBDC,SandwormBadnikInstance,800,sonic,tails,s1",
        "1,0x550,0x3DC,SandwormBadnikInstance,320,sonic,tails,s2",
        "0,0x330,0x5D4,SkorpBadnikInstance,512,knuckles,none,off",
        "1,0x550,0x3DC,SandwormBadnikInstance,640,tails,sonic,off"})
    void placedFamilyBindsAndReplaysThroughChildLifecycle(int act,int x,int y,String family,int width,String character,String followers,String donor) {
        var config=SonicConfigurationService.getInstance();
        var saved=new EnumMap<SonicConfiguration,Object>(SonicConfiguration.class);
        for(var key:SonicConfiguration.values()) if(config.hasSessionOverride(key)) saved.put(key,config.getConfigValue(key));
        try {
            config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,followers.equals("none")?"":followers);
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
            CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
            if (!donor.equals("off")) {
                var rom = donor.equals("s1") ? RomTestUtils.ensureSonic1RomAvailable() : RomTestUtils.ensureSonic2RomAvailable();
                assertNotNull(rom);
                config.setSessionOverride(donor.equals("s1") ? SonicConfiguration.SONIC_1_ROM : SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
                config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,true);
                config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
            }
            var builder=HeadlessTestFixture.builder().withZoneAndAct(8,act)
                    .startPosition((short)x,(short)y).startPositionIsCentre().withFreshLevelStartLifecycle();
            if(!donor.equals("off")) builder.withCrossGameDonation(donor);
            var fixture=builder.build();
            assertEquals(width,fixture.camera().getWidth() & 65535);
            var manager=GameServices.level().getObjectManager();
            boolean bound=false,child=false;
            var registry=fixture.gameplayMode().getRewindRegistry();
            for(int frame=0;frame<180;frame++) {
                var before=registry.capture();fixture.stepFrame(false,false,false,false,false);
                bound |= manager.getActiveObjects().stream().anyMatch(o -> o.getClass().getSimpleName().equals(family));
                child |= manager.getActiveObjects().stream().anyMatch(o -> o.getClass().getEnclosingClass()!=null && o.getClass().getEnclosingClass().getSimpleName().equals(family));
                if(frame==8 || frame==24 || frame==140) {
                    var after=registry.capture(); manager.setRewindInPlaceRestoreEnabledForTest(false); registry.restore(before); same(before,registry.capture(),family+" restore "+frame);
                    fixture.stepFrame(false,false,false,false,false);same(after,registry.capture(),family+" replay "+frame);
                }
            }
            assertTrue(bound,"production registry binds "+family);assertTrue(child,"production allocates children "+family);
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);
            config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();
        }
    }
    @ParameterizedTest
    @CsvSource({"SkorpBadnikInstance,0x350,0x5D4", "SandwormBadnikInstance,0xA50,0xBDC",
            "RocknBadnikInstance,0xE60,0xF0"})
    void managerRetirementDetachesChildrenBeforeTheirNextDispatch(String family,int x,int y) {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(8,0)
                .startPosition((short)x,(short)y).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        var manager=GameServices.level().getObjectManager();
        var spawn=new ObjectSpawn(x,y,0x94,0xFE,0,false,0);
        AbstractObjectInstance owner=switch(family) {
            case "SkorpBadnikInstance" -> new SkorpBadnikInstance(spawn);
            case "SandwormBadnikInstance" -> new SandwormBadnikInstance(spawn);
            default -> new RocknBadnikInstance(spawn);
        };
        manager.addDynamicObject(owner);
        for(int i=0;i<30;i++) fixture.stepFrame(false,false,false,false,false);
        final int slot=owner.getSlotIndex();
        var registry=fixture.gameplayMode().getRewindRegistry();
        var before=registry.capture();
        // Retire between object passes, as post-object touch conversion can do.
        manager.removeDynamicObject(owner);
        manager.validateRewindReferenceClosure();
        fixture.stepFrame(false,false,false,false,false);
        var after=registry.capture();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(before);
        owner=manager.getActiveObjects().stream().filter(AbstractObjectInstance.class::isInstance)
                .map(AbstractObjectInstance.class::cast).filter(o -> o.getSlotIndex()==slot)
                .findFirst().orElseThrow();
        manager.removeDynamicObject(owner);
        manager.validateRewindReferenceClosure();
        fixture.stepFrame(false,false,false,false,false);
        same(after,registry.capture(),family+" retirement forward replay");
    }

    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(String key:expected.entries().keySet()) {
            var diff=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),label+" "+key+": "+diff);
        }
    }
}
