package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.badniks.MonkeyDudeArmObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.MonkeyDudeBadnikInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Actual AIZ1 ROM placement, five preset cameras and complete moving-world graph replay. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMonkeyDudePlacedGraph {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();
    }
    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void placedBodyAdmitsItsNativeGraphAndReplaysTheCompleteWorld(WidescreenAspect aspect) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,aspect.name());
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,true);
        config.resolveDisplayAspect();GameServices.camera().refreshViewportDimensions(config);
        assertEquals(aspect.pixelWidth(),GameServices.camera().getWidth());
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,0)
                .startPosition((short)0x1EA6,(short)0x490).startPositionIsCentre().build();
        assertEquals(aspect.pixelWidth(),fixture.camera().getWidth());
        fixture.stepIdleFrames(1); // first ordinary Load_Sprites at the positioned camera
        // The actor must come from the decoder/registry, never a manually spawned replacement.
        var body=GameServices.level().getObjectManager().activeObjectsOfType(MonkeyDudeBadnikInstance.class)
                .stream().filter(o->o.getSpawn().x()==0x1E58 && o.getSpawn().y()==0x490)
                .findFirst().orElseThrow();
        assertEquals(0x8E,body.getSpawn().objectId());assertEquals(0x10,body.getSpawn().subtype());
        assertEquals(1,body.getSpawn().renderFlags()&1,"literal ROM placement flip bit");
        fixture.stepIdleFrames(4);
        assertEquals(0x1E58,body.getX());
        assertEquals(0x1E58,body.getCollisionX(),"native Touch_Width origin is the body SST X");
        var arms=GameServices.level().getObjectManager().activeObjectsOfType(MonkeyDudeArmObjectInstance.class)
                .stream().filter(o->!o.isDestroyed() && o.getSpawn().x()==0x1E58 && o.getSpawn().y()==0x490).toList();
        assertEquals(5,arms.size());
        assertEquals(java.util.Set.of(0,2,4,6,8),arms.stream().map(o->o.getSpawn().subtype()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(arms.stream().allMatch(o->o.getSlotIndex()>body.getSlotIndex()));
        var level=GameServices.level().getCurrentLevel();
        var registry=fixture.gameplayMode().getRewindRegistry();
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        CompositeSnapshot before=registry.capture();
        fixture.stepIdleFrames(45);assertSame(level,GameServices.level().getCurrentLevel());
        CompositeSnapshot after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture());
            fixture.stepIdleFrames(45);assertSame(level,GameServices.level().getCurrentLevel());
            same(after,registry.capture());
        }
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet()) {
            var differences=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(differences.isEmpty(),()->key+": "+differences);
        }
    }
}
