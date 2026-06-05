package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayableEntity;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidRoutineKind;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
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
    void mtzLongPlatformProximityChecksNativeSidekick() throws Exception {
        MTZLongPlatformObjectInstance platform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x0AA0, 0x076C, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x13, 0, false, 0x076C));
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0x0A40, (short) 0x076E);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x0A85, (short) 0x076E);
        tails.setCpuControlled(true);
        platform.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> sonic, () -> List.of(tails))));
        setIntField(platform, "currentDist", 0x40);
        setIntField(platform, "x", 0x0A60);

        platform.update(0, sonic);

        assertEquals(0x40, intField(platform, "currentDist"),
                "Obj65 loc_26D94 checks Sidekick after MainCharacter before retracting");
        assertEquals(0x0A60, platform.getX(),
                "A native P2/Tails inside the proximity box must keep the fully extended platform stationary");
    }

    @Test
    void mtzLongPlatformLandingWidthUsesRomWidthPixels() {
        MTZLongPlatformObjectInstance platform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x0B20, 0x076C, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x13, 1, false, 0x276C));

        assertEquals(0x25, platform.getSolidParams().halfWidth(),
                "Obj65 passes width_pixels+$5 to SolidObject");
        assertEquals(0x20, platform.getTopLandingHalfWidth(null, platform.getSolidParams().halfWidth()),
                "SolidObject_Landed re-reads Obj65 width_pixels, not the common width_pixels+$B default");
    }

    @Test
    void mtzConveyorUsesPlatformObjectD3ForLandingSnap() {
        ConveyorObjectInstance conveyor = new ConveyorObjectInstance(
                new ObjectSpawn(0x1720, 0x0519, Sonic2ObjectIds.CONVEYOR, 0x01, 0, false, 0),
                "Conveyor");

        assertEquals(8, conveyor.getSolidParams().groundHalfHeight(),
                "Obj6C_Main passes d3=8 to PlatformObject for both ChkYRange and MvSonicOnPtfm");
    }

    @Test
    void mtzLongPlatformOutOfRangeUsesStoredBaseX() throws Exception {
        MTZLongPlatformObjectInstance platform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x0B20, 0x076C, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x13, 1, false, 0x276C));
        setIntField(platform, "x", 0x0AE0);

        assertEquals(0x0B20, platform.getOutOfRangeReferenceX(),
                "Obj65 loc_26C1C checks objoff_34, not moving x_pos(a0), for MarkObjGone");
    }

    @Test
    void largeRotPformOutOfRangeUsesStoredBaseX() throws Exception {
        LargeRotPformObjectInstance platform = new LargeRotPformObjectInstance(
                new ObjectSpawn(0x0BC0, 0x06C0, Sonic2ObjectIds.LARGE_ROT_PFORM, 0x20, 1, false, 0x26C0),
                "LargeRotPform");
        setIntField(platform, "x", 0x0B9A);

        assertEquals(0x0BC0, platform.getOutOfRangeReferenceX(),
                "Obj6E loc_28466 checks objoff_34, not moving x_pos(a0), for MarkObjGone");
    }

    @Test
    void mtzPlatformOutOfRangeUsesStoredBaseX() throws Exception {
        MTZPlatformObjectInstance platform = new MTZPlatformObjectInstance(
                new ObjectSpawn(0x0BC0, 0x0630, Sonic2ObjectIds.MTZ_PLATFORM, 0x02, 1, false, 0x2630),
                "MTZPlatform");
        setIntField(platform, "x", 0x0B63);

        assertEquals(0x0BC0, platform.getOutOfRangeReferenceX(),
                "Obj6B_Main checks objoff_34, not moving x_pos(a0), for MarkObjGone2");
    }

    @Test
    void s2SpikesUseLiveRollingRadiusForBottomOverlap() {
        SpikeObjectInstance spikes = new SpikeObjectInstance(
                new ObjectSpawn(0x0C40, 0x0650, Sonic2ObjectIds.SPIKES, 0x30, 2, false, 0x4650),
                "Spikes");

        assertTrue(spikes.fullSolidBottomOverlapUsesCurrentYRadiusOnly(null),
                "Obj36 SolidObject_cont doubles live y_radius(a1), so rolling underside contact must not use stand radius");
    }

    @Test
    void spikeTouchChkHurt2RewindsCurrentYVelocityBeforeHurt() {
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.getPreContactYSpeed()).thenReturn((short) 0xFE30);
        SpikeObjectInstance spikes = new SpikeObjectInstance(
                new ObjectSpawn(0x0C40, 0x0650, Sonic2ObjectIds.SPIKES, 0x30, 2, false, 0x4650),
                "Spikes");
        spikes.setServices(new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }
        });
        PlayableEntity tails = mock(PlayableEntity.class);
        when(tails.isCpuControlled()).thenReturn(true);
        when(tails.getYSpeed()).thenReturn((short) 0xFE68);

        spikes.onSolidContact(tails, new SolidContact(false, false, true, false, false), 0);

        InOrder order = inOrder(tails);
        order.verify(tails).move((short) 0, (short) 0x0198);
        order.verify(tails).applyHurt(0x0C40);
        verify(objectManager, never()).getPreContactYSpeed();
    }

    @Test
    void spikeTouchChkHurt2SkipsAfterSolidObjectCrushDeath() {
        SpikeObjectInstance spikes = new SpikeObjectInstance(
                new ObjectSpawn(0x0C40, 0x0650, Sonic2ObjectIds.SPIKES, 0x30, 2, false, 0x4650),
                "Spikes");
        spikes.setServices(new StubObjectServices());
        PlayableEntity tails = mock(PlayableEntity.class);
        when(tails.getDead()).thenReturn(true);
        when(tails.isCpuControlled()).thenReturn(true);
        when(tails.getYSpeed()).thenReturn((short) 0xFE68);

        spikes.onSolidContact(tails, new SolidContact(false, false, true, false, false), 0);

        verify(tails, never()).move((short) 0, (short) 0x0198);
        verify(tails, never()).applyHurt(0x0C40);
        verify(tails, never()).applyHurtOrDeath(0x0C40, true, false);
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

    @Test
    void mtzPlatformType5StandingContactPreservesYSubpixelWhenArmingFall() throws Exception {
        MTZPlatformObjectInstance platform = new MTZPlatformObjectInstance(
                new ObjectSpawn(0x0460, 0x04EC, Sonic2ObjectIds.MTZ_PLATFORM, 0x05, 0, false, 0),
                "MTZPlatform");
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0460, (short) 0x052C);
        setIntField(platform, "yFixed", (0x04EC << 16) | 0xF000);

        platform.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        platform.update(1, player);

        assertEquals(6, intField(platform, "moveType"),
                "Obj6B type 5 must consume the standing bit on the following Obj6B dispatch");
        assertEquals(platform.getY() << 16 | 0xF000, intField(platform, "yFixed"),
                "Obj6B type 5 uses move.w y_pos and must preserve y_sub for the following ObjectMove");
    }

    @Test
    void mtzPlatformFallingUsesRomSixteenBitSubpixelCarry() throws Exception {
        MTZPlatformObjectInstance platform = new MTZPlatformObjectInstance(
                new ObjectSpawn(0x0460, 0x04EC, Sonic2ObjectIds.MTZ_PLATFORM, 0x06, 0, false, 0),
                "MTZPlatform");
        platform.setServices(new StubObjectServices());
        setIntField(platform, "yFixed", (0x052C << 16) | 0xF000);
        setIntField(platform, "y", 0x052C);
        setIntField(platform, "yVel", 0x0010);

        platform.update(0, new TestablePlayableSprite("sonic", (short) 0x0460, (short) 0x052C));

        assertEquals(0x052D, platform.getY(),
                "ROM loc_27EE2 adds y_vel<<8 to y_pos.w:y_sub.w, so the preserved low word can carry");
        assertEquals(0x0018, intField(platform, "yVel"));
    }

    @Test
    void mtzPlatformBouncyContactArmsBounceBeforeNextDispatch() throws Exception {
        MTZPlatformObjectInstance platform = new MTZPlatformObjectInstance(
                new ObjectSpawn(0x0460, 0x052C, Sonic2ObjectIds.MTZ_PLATFORM, 0x07, 0, false, 0),
                "MTZPlatform");
        platform.setServices(new StubObjectServices());
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0460, (short) 0x050C);

        platform.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        platform.update(1, player);

        assertEquals(8, intField(platform, "bounceAccel"),
                "ROM Obj6B type 7 consumes the standing bit before the following ObjectMove dispatch");
        assertEquals(8, intField(platform, "yVel"),
                "The first post-contact bouncy dispatch must run ObjectMove with old y_vel then add objoff_38");
    }

    @Test
    void mtzTwinStompersPrimeRomMainTicksBeforeFirstContactFrame() throws Exception {
        MTZTwinStompersObjectInstance stomper = new MTZTwinStompersObjectInstance(
                new ObjectSpawn(0x0620, 0x05A0, Sonic2ObjectIds.MTZ_TWIN_STOMPERS, 0x01, 0, false, 0),
                "MTZTwinStompers");
        stomper.setServices(new StubObjectServices());

        assertEquals(0x05A8, stomper.getY(),
                "Obj64 must enter the engine contact window with the ROM's first two main ticks consumed");
        assertEquals(8, intField(stomper, "extension"));
        assertEquals(0x5A, intField(stomper, "timer"));

        stomper.update(0, new TestablePlayableSprite("sonic", (short) 0x0600, (short) 0x05F0));

        assertEquals(0x05B0, stomper.getY(),
                "The following Obj64_Main dispatch continues the ROM 8 px/tick extension cadence");
        assertEquals(0x10, intField(stomper, "extension"));
    }

    @Test
    void mtzCogRotationUsesRomVisibleLevelFrameCounter() {
        LevelManager levelManager = mock(LevelManager.class);
        CogObjectInstance cog = new CogObjectInstance(
                new ObjectSpawn(0x0800, 0x0680, Sonic2ObjectIds.COG, 0x00, 0, false, 0),
                "Cog");
        cog.setServices(new StubObjectServices() {
            @Override
            public LevelManager levelManager() {
                return levelManager;
            }
        });

        when(levelManager.getFrameCounter()).thenReturn(0x07EE);
        cog.update(0x6CC1, new TestablePlayableSprite("sonic", (short) 0x0800, (short) 0x0600));
        assertEquals(0x0800, cog.getPieceX(0),
                "Stored LevelManager frame $07EE corresponds to ROM-visible $07EF, so Obj70 must not rotate yet");

        when(levelManager.getFrameCounter()).thenReturn(0x07EF);
        cog.update(0x6CC2, new TestablePlayableSprite("sonic", (short) 0x0800, (short) 0x0600));
        assertEquals(0x080D, cog.getPieceX(0),
                "ROM-visible Level_frame_counter $07F0 advances Obj70 to the next tooth phase");
    }

    private static int intField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
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
