package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.objects.Sonic3kSpikeObjectInstance;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Short entry/reload/reset product; deliberately no traversal or boss claim.
 * Contamination is explicit test setup. Every replacement runs production load/reset.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzEntryReloadResetMatrix {
    @ParameterizedTest(name="FBZ actIndex={0} {1}px donor={2}: cold entry and two reset cycles")
    @MethodSource("cases")
    void coldEntryAndRepeatedProductionReloadsIsolateActOwners(int act, int width, String donor) throws Exception {
        try (ConfigurationScope ignored = new ConfigurationScope(width, donor)) {
            var builder = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, act);
            if (!donor.equals("off")) builder.withCrossGameDonation(donor);
            var fixture = builder.build();
            GameServices.graphics().setViewport(0,0,width,224);
            assertFresh(act,width,donor);
            for (int cycle=0; cycle<2; cycle++) {
                contaminateAndLoad(1-act,false,width,donor);
                contaminateAndLoad(act,true,width,donor);
                fixture.stepIdleFrames(2);
                assertFalse(fixture.sprite().getDead());
                assertEquals(act,GameServices.level().getCurrentAct());
            }
        }
    }

    private static void contaminateAndLoad(int destinationAct, boolean reset, int width, String donor) throws Exception {
        var manager = GameServices.level();
        var oldObjects = manager.getObjectManager();
        var oldRuntime = assertInstanceOf(FbzZoneRuntimeState.class,GameServices.zoneRuntimeRegistry().current());
        var oldEvents = ((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getFbzEvents();
        var player = GameServices.sprites().getMainPlayable();
        oldEvents.setEventsFg5(true);
        if (oldRuntime.actIndex()==1) {
            oldEvents.setScreenShakeState(true,7,3);
            oldEvents.setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED);
        }
        var contact = ObjectConstructionContext.construct(TestEnvironment.objectServices(),
                () -> new Sonic3kSpikeObjectInstance(new ObjectSpawn(player.getCentreX(),player.getCentreY(),
                        Sonic3kObjectIds.SPIKES,0,0,false,0)));
        oldObjects.addDynamicObject(contact);
        oldObjects.forceRidingObjectForBootstrap(player,contact);
        assertTrue(oldObjects.isRidingObject(player,contact));
        assertTrue(oldObjects.hasObjectStandingBit(player,contact));
        if (reset) manager.resetState();
        manager.loadZoneAndAct(Sonic3kZoneIds.ZONE_FBZ,destinationAct);
        assertNotSame(oldObjects,manager.getObjectManager());
        assertNotSame(oldRuntime,GameServices.zoneRuntimeRegistry().current());
        assertNotSame(oldEvents,((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getFbzEvents());
        assertFalse(manager.getObjectManager().getActiveObjects().contains(contact));
        assertFalse(manager.getObjectManager().isRidingObject(player));
        assertFalse(manager.getObjectManager().hasObjectStandingBit(player,contact));
        assertFalse(player.isOnObject(),"fresh load must release the source player's contact bit");
        assertFresh(destinationAct,width,donor);
    }

    private static void assertFresh(int act, int width, String donor) {
        assertEquals(Sonic3kZoneIds.ZONE_FBZ,GameServices.level().getCurrentZone());
        assertEquals(act,GameServices.level().getCurrentAct());
        int[] romStart=GameServices.module().getZoneRegistry().getStartPosition(Sonic3kZoneIds.ZONE_FBZ,act);
        var player=assertInstanceOf(Sonic.class,GameServices.sprites().getMainPlayable());
        assertEquals("sonic",player.getCode());
        assertFalse(player.isCpuControlled());
        assertEquals(romStart[0],player.getCentreX()&0xffff);
        assertEquals(romStart[1],player.getCentreY()&0xffff);
        assertEquals(1,GameServices.sprites().getSidekicks().size());
        var follower=assertInstanceOf(Tails.class,GameServices.sprites().getSidekicks().getFirst());
        assertEquals("tails_p2",follower.getCode());
        assertTrue(follower.isCpuControlled());
        assertSame(player,follower.getCpuController().getLeader());
        assertEquals(width,GameServices.camera().getWidth()&0xffff);
        assertEquals(width,GameServices.graphics().getViewportWidth());
        assertEquals(!donor.equals("off"),CrossGameFeatureProvider.isActive());
        if (!donor.equals("off")) {
            assertEquals(donor,CrossGameFeatureProvider.getInstance().getDonorGameId());
            assertNotSame(GameServices.module().getRules(),player.getGameRules());
        } else assertSame(GameServices.module().getRules(),player.getGameRules());
        assertEquals(!donor.equals("s1"),player.getGameRules().playerCapability().spindashEnabled());
        var events=((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getFbzEvents();
        var runtime=assertInstanceOf(FbzZoneRuntimeState.class,GameServices.zoneRuntimeRegistry().current());
        assertTrue(runtime.isBackedBy(events));
        assertEquals(act,runtime.actIndex());
        assertEquals(PlayerCharacter.SONIC_AND_TAILS,runtime.playerCharacter());
        assertFalse(events.isEventsFg5());
        assertFalse(events.isScreenShakeActive());
        assertEquals(Sonic3kFBZEvents.PlaneAssignmentMode.NORMAL,events.getPlaneAssignmentMode());
    }

    private static Stream<Arguments> cases() {
        return IntStream.range(0,2).boxed().flatMap(act -> IntStream.of(320,400,512,640,800).boxed()
                .flatMap(width -> Stream.of("off","s1","s2").map(donor -> Arguments.of(act,width,donor))));
    }

    private static final class ConfigurationScope implements AutoCloseable {
        private final SonicConfigurationService config=SonicConfigurationService.getInstance();
        private final Map<SonicConfiguration,Object> overrides=new EnumMap<>(SonicConfiguration.class);
        private final int x=GameServices.graphics().getViewportX(), y=GameServices.graphics().getViewportY();
        private final int width=GameServices.graphics().getViewportWidth(), height=GameServices.graphics().getViewportHeight();
        ConfigurationScope(int width,String donor) {
            File rom=switch(donor) {
                case "s1" -> RomTestUtils.ensureSonic1RomAvailable();
                case "s2" -> RomTestUtils.ensureSonic2RomAvailable();
                default -> null;
            };
            if (!donor.equals("off")) { assertNotNull(rom,"mandatory donor ROM missing");assertTrue(rom.isFile()); }
            for (var key:SonicConfiguration.values()) if(config.hasSessionOverride(key)) overrides.put(key,config.getConfigValue(key));
            config.clearSessionOverrides();
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,WidescreenAspect.NATIVE_4_3.name());
            config.resolveDisplayAspect();
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
            if(rom!=null) config.setSessionOverride(donor.equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
            CrossGameFeatureProvider.getInstance().resetState();
            SessionManager.clear();TestEnvironment.activeGameplayMode();
        }
        @Override public void close() {
            CrossGameFeatureProvider.getInstance().resetState();
            config.clearSessionOverrides();overrides.forEach(config::setSessionOverride);config.resolveDisplayAspect();
            GameServices.graphics().setViewport(x,y,width,height);
            SessionManager.clear();TestEnvironment.activeGameplayMode();
        }
    }
}
