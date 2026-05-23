package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidRoutineKind;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSonic2ObjectBugFixes {

    @Test
    void steamSpringLaunchClearsObjectRideState() throws Exception {
        ObjectManager objectManager = mock(ObjectManager.class);
        SteamSpringObjectInstance spring = new SteamSpringObjectInstance(
                new ObjectSpawn(0x2000, 0x0400, Sonic2ObjectIds.STEAM_SPRING, 0x00, 0, false, 0));
        spring.setServices(new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }
        });

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2000, (short) 0x0400);
        player.setOnObject(true);
        player.setAir(false);

        Method applySpring = SteamSpringObjectInstance.class.getDeclaredMethod(
                "applySpring", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        applySpring.setAccessible(true);
        applySpring.invoke(spring, player);

        assertFalse(player.isOnObject(),
                "Obj42 spring launch must clear status.player.on_object like ROM loc_26798");
        verify(objectManager).clearRidingObject(player);
    }

    @Test
    void signpostSurvivesMetropolisAct2WhenServicesUseRomZoneId() {
        ObjectManager objectManager = mock(ObjectManager.class);
        SonicConfigurationService config = mock(SonicConfigurationService.class);
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn("sonic");
        SignpostObjectInstance signpost = new SignpostObjectInstance(
                new ObjectSpawn(0x2800, 0x0300, 0x0D, 0x00, 0, true, 0),
                "Signpost");
        signpost.setServices(new ZoneActServices(objectManager, Sonic2ZoneConstants.ROM_ZONE_MTZ, 1, config));

        signpost.update(0, new TestablePlayableSprite("sonic", (short) 0x2700, (short) 0x0300));

        assertFalse(signpost.isDestroyed(),
                "Obj0D must keep the MTZ Act 2 signpost when currentZone is the ROM zone id");
        verify(objectManager, never()).markRemembered(signpost.getSpawn());
    }

    @Test
    void mtzAct3LongPlatformUsesRomZoneIdForTwoStopConveyor() throws Exception {
        MTZLongPlatformObjectInstance platform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x1CBE, 0x0300, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x05, 0, false, 0));
        platform.setServices(new ZoneActServices(null, Sonic2ZoneConstants.ROM_ZONE_MTZ, 2, null));

        platform.update(0, new TestablePlayableSprite("sonic", (short) 0x1CBE, (short) 0x02E0));

        assertEquals(0, intField(platform, "moveSubtype"),
                "MTZ Act 3 subtype-5 conveyor must stop at the first MTZ3 stop point");
        assertEquals(0x1CC0, platform.getX(),
                "Regression setup should land exactly on the first MTZ3 stop point");
    }

    @Test
    void mtzPlatformsExposeFullSolidRoutineProfiles() {
        MTZPlatformObjectInstance platform = new MTZPlatformObjectInstance(
                new ObjectSpawn(0x1000, 0x0300, Sonic2ObjectIds.MTZ_PLATFORM, 0x00, 0, false, 0),
                "MTZPlatform");
        MTZLongPlatformObjectInstance longPlatform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x1400, 0x0300, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x00, 0, false, 0));

        SolidRoutineProfile profile = platform.getSolidRoutineProfile();
        SolidRoutineProfile longProfile = longPlatform.getSolidRoutineProfile();

        assertEquals(SolidRoutineKind.FULL_SOLID, profile.kind());
        assertEquals(platform.isTopSolidOnly(), profile.topSolidOnly());
        assertEquals(platform.usesStickyContactBuffer(), profile.stickyContactBuffer());
        assertEquals(SolidRoutineKind.FULL_SOLID, longProfile.kind());
        assertEquals(longPlatform.isTopSolidOnly(), longProfile.topSolidOnly());
        assertEquals(longPlatform.usesStickyContactBuffer(), longProfile.stickyContactBuffer());
        assertEquals(longPlatform.carriesRiderOnHorizontalMove(null),
                longProfile.carriesAirborneRiderAfterExitPlatform());
    }

    private static int intField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static final class ZoneActServices extends StubObjectServices {
        private final ObjectManager objectManager;
        private final int romZoneId;
        private final int currentAct;
        private final SonicConfigurationService configuration;

        private ZoneActServices(ObjectManager objectManager, int romZoneId, int currentAct,
                                SonicConfigurationService configuration) {
            this.objectManager = objectManager;
            this.romZoneId = romZoneId;
            this.currentAct = currentAct;
            this.configuration = configuration;
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }

        @Override
        public int romZoneId() {
            return romZoneId;
        }

        @Override
        public int currentAct() {
            return currentAct;
        }

        @Override
        public SonicConfigurationService configuration() {
            return configuration;
        }
    }
}
