package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.ParallaxManager;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidRoutineKind;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
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
    void steamSpringRendersPistonMappingFrameSeven() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_STEAM_PISTON)).thenReturn(renderer);

        SteamSpringObjectInstance spring = new SteamSpringObjectInstance(
                new ObjectSpawn(0x2000, 0x0400, Sonic2ObjectIds.STEAM_SPRING, 0x00, 0, false, 0));
        spring.setServices(new StubObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return renderManager;
            }
        });

        spring.appendRenderCommands(new ArrayList<GLCommand>());

        verify(renderer).drawFrameIndex(7, 0x2000, 0x0410, false, false);
    }

    @Test
    void steamSpringRightEdgeUsesRomInclusiveSolidObjectGate() {
        SteamSpringObjectInstance spring = new SteamSpringObjectInstance(
                new ObjectSpawn(0x04B0, 0x0140, Sonic2ObjectIds.STEAM_SPRING, 0x00, 0, false, 0));
        spring.snapshotPreUpdatePosition();
        ObjectManager manager = buildSingleObjectManager(spring);

        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        tails.setWidth(18);
        tails.setHeight(18);
        tails.setAir(false);
        tails.setXSpeed((short) -0x100);
        tails.setGSpeed((short) -0x100);
        tails.setCentreX((short) 0x04CB);
        tails.setCentreY((short) 0x0150);

        manager.updateSolidContacts(tails);

        assertTrue(tails.getPushing(),
                "Obj42 SolidObject_cont uses bhi, so relX == $1B*2 must still set Status_Push");
        assertEquals(0, tails.getXSpeed());
        assertEquals(0, tails.getGSpeed());
        assertEquals(0x04CB, tails.getCentreX(),
                "Exact right-edge contact has zero shove distance and should not move Tails");
    }

    @Test
    void steamPuffDoesNotUseMarkObjGoneUnloadWindow() {
        SteamPuffObjectInstance puff = new SteamPuffObjectInstance(0x0208, 0x0270, true);

        assertTrue(puff.usesCustomOutOfRangeCheck(),
                "Obj42 routine 4 tails to DisplaySprite, not MarkObjGone");
        assertFalse(puff.isCustomOutOfRange(0x0306),
                "Obj42 steam puffs must survive off-screen until their animation deletes them");
    }

    @Test
    void spikyBlockRendersParentBlockMappingFrameFour() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_SPIKE_BLOCK)).thenReturn(renderer);

        SpikyBlockObjectInstance block = new SpikyBlockObjectInstance(
                new ObjectSpawn(0x1800, 0x0500, Sonic2ObjectIds.SPIKY_BLOCK, 0x00, 0, false, 0),
                "SpikyBlock");
        block.setServices(new StubObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return renderManager;
            }
        });

        block.appendRenderCommands(new ArrayList<GLCommand>());

        verify(renderer).drawFrameIndex(4, 0x1800, 0x0500, false, false);
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
    void mtzLongPlatformDefersBit7ChildCogUntilFirstRoutinePass() {
        ObjectManager objectManager = mock(ObjectManager.class);
        StubObjectServices services = new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }
        };

        MTZLongPlatformObjectInstance platform = ObjectConstructionContext.construct(services,
                () -> new MTZLongPlatformObjectInstance(
                        new ObjectSpawn(0x0600, 0x01B0, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x80, 0, false, 0)));
        platform.setServices(services);

        verify(objectManager, never()).addDynamicObjectAfterCurrent(
                org.mockito.ArgumentMatchers.any(AbstractObjectInstance.class));

        platform.update(0, new TestablePlayableSprite("sonic", (short) 0x0600, (short) 0x01B0));

        verify(objectManager).addDynamicObjectAfterCurrent(
                org.mockito.ArgumentMatchers.argThat(MTZLongPlatformCogInstance.class::isInstance));
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
    void s2SpikesUseSolidObjectAirborneStaleStandingBitReturn() {
        SpikeObjectInstance spikes = new SpikeObjectInstance(
                new ObjectSpawn(0x0C40, 0x0650, Sonic2ObjectIds.SPIKES, 0x30, 2, false, 0x4650),
                "Spikes");

        assertTrue(spikes.airborneStaleStandingBitReturnsNoContact(null),
                "Obj36 calls the shared SolidObject path; an airborne stale standing bit returns before new contact");
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
    void mtzTwinStompersPrimeSingleRomMainTickBeforeFirstContactFrame() throws Exception {
        MTZTwinStompersObjectInstance stomper = new MTZTwinStompersObjectInstance(
                new ObjectSpawn(0x0620, 0x05A0, Sonic2ObjectIds.MTZ_TWIN_STOMPERS, 0x01, 0, false, 0),
                "MTZTwinStompers");
        stomper.setServices(new StubObjectServices());

        assertEquals(0x05A0, stomper.getY(),
                "Obj64_Init falls through into one Obj64_Main tick before the next dispatch");
        assertEquals(0, intField(stomper, "extension"));
        assertEquals(0x5A, intField(stomper, "timer"));

        stomper.update(0, new TestablePlayableSprite("sonic", (short) 0x0600, (short) 0x05F0));

        assertEquals(0x05A8, stomper.getY(),
                "The following Obj64_Main dispatch continues the ROM 8 px/tick extension cadence");
        assertEquals(8, intField(stomper, "extension"));
    }

    @Test
    void skyChaseCloudKeepsSixteenBitSubpixelAccumulator() throws Exception {
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        ParallaxManager parallaxManager = mock(ParallaxManager.class);
        when(parallaxManager.getTornadoVelocityX()).thenReturn(0);
        CloudObjectInstance cloud = new CloudObjectInstance(
                new ObjectSpawn(0x0300, 0x0120, Sonic2ObjectIds.CLOUD, 0x60, 0, false, 0));
        cloud.setServices(new StubObjectServices() {
            @Override
            public Camera camera() {
                return camera;
            }

            @Override
            public ParallaxManager parallaxManager() {
                return parallaxManager;
            }
        });

        cloud.update(0, new TestablePlayableSprite("sonic", (short) 0x0300, (short) 0x0120));

        SubpixelMotion.State motionState = (SubpixelMotion.State) objectField(cloud, "motionState");
        assertEquals(0x02FF, cloud.getX(),
                "ObjB3 ObjectMove should apply the negative fractional carry on the first frame");
        assertEquals(0xC000, motionState.xSub,
                "ObjB3 must preserve the ROM 16.16 low word instead of truncating it to 8 bits");
    }

    @Test
    void collapsingPlatformFragmentFallDeletesUsingFallingParentY() throws Exception {
        StubObjectServices services = new StubObjectServices();
        CollapsingPlatformObjectInstance platform = ObjectConstructionContext.construct(services,
                () -> new CollapsingPlatformObjectInstance(
                        new ObjectSpawn(0x0240, 0x05D0, Sonic2ObjectIds.COLLAPSING_PLATFORM, 0x00, 0, false, 0),
                        "CollapsPform"));
        platform.setServices(services);
        setBooleanField(platform, "collapsed", true);
        setIntField(platform, "parentY", 0x0700);
        setIntField(platform, "verticalOnlyOffscreenTicks", 2);

        AbstractObjectInstance.updateCameraBounds(0x0200, 0x052C, 0x0340, 0x060C, 0);

        platform.update(222, new TestablePlayableSprite("sonic", (short) 0x0330, (short) 0x058C));

        assertTrue(platform.isDestroyed(),
                "Obj1F_FragmentFall must delete from the falling parent y_pos, not the original spawn y_pos");
    }

    @Test
    void collapsingPlatformFragmentFallDeletesWhenRenderBoxLeavesScreenLeft() throws Exception {
        StubObjectServices services = new StubObjectServices();
        CollapsingPlatformObjectInstance platform = ObjectConstructionContext.construct(services,
                () -> new CollapsingPlatformObjectInstance(
                        new ObjectSpawn(0x0240, 0x05D0, Sonic2ObjectIds.COLLAPSING_PLATFORM, 0x00, 0, false, 0),
                        "CollapsPform"));
        platform.setServices(services);
        setBooleanField(platform, "collapsed", true);
        setIntField(platform, "parentY", 0x05ED);

        AbstractObjectInstance.updateCameraBounds(0x0285, 0x052C, 0x03C5, 0x060C, 0);

        platform.update(221, new TestablePlayableSprite("sonic", (short) 0x0330, (short) 0x058C));

        assertTrue(platform.isDestroyed(),
                "Obj1F_FragmentFall must observe DisplaySprite render_flags, not MarkObjGone's 0x80 unload margin");
    }

    @Test
    void collapsingPlatformFragmentFallUsesApproximateRenderHeight() throws Exception {
        StubObjectServices services = new StubObjectServices();
        CollapsingPlatformObjectInstance platform = ObjectConstructionContext.construct(services,
                () -> new CollapsingPlatformObjectInstance(
                        new ObjectSpawn(0x0441, 0x05B0, Sonic2ObjectIds.COLLAPSING_PLATFORM, 0x00, 0, false, 0),
                        "CollapsPform"));
        platform.setServices(services);
        AbstractObjectInstance.updateCameraBounds(0x0428, 0x0506, 0x0568, 0x05E6, 0);
        platform.update(320, new TestablePlayableSprite("sonic", (short) 0x04C0, (short) 0x0555));

        setBooleanField(platform, "collapsed", true);
        setIntField(platform, "parentY", 0x05FA);

        platform.update(321, new TestablePlayableSprite("sonic", (short) 0x04C0, (short) 0x0555));

        assertFalse(platform.isDestroyed(),
                "Obj1F lacks render_flags.explicit_height, so BuildSprites keeps it through the 32px approximate Y band");
    }

    @Test
    void collapsingPlatformFragmentFallKeepsVerticalOnlyOffscreenParentForCpuSlotRefresh() throws Exception {
        StubObjectServices services = new StubObjectServices();
        CollapsingPlatformObjectInstance platform = ObjectConstructionContext.construct(services,
                () -> new CollapsingPlatformObjectInstance(
                        new ObjectSpawn(0x0441, 0x05B0, Sonic2ObjectIds.COLLAPSING_PLATFORM, 0x00, 0, false, 0),
                        "CollapsPform"));
        platform.setServices(services);
        setBooleanField(platform, "collapsed", true);
        setIntField(platform, "parentY", 0x0606);

        AbstractObjectInstance.updateCameraBounds(0x0428, 0x0506, 0x0568, 0x05E6, 0);

        platform.update(324, new TestablePlayableSprite("sonic", (short) 0x04C0, (short) 0x0555));
        assertFalse(platform.isDestroyed(),
                "A vertically clipped but horizontally visible Obj1F parent must survive the first CPU refresh tick");

        platform.update(325, new TestablePlayableSprite("sonic", (short) 0x04C0, (short) 0x0555));
        assertFalse(platform.isDestroyed(),
                "The second CPU refresh still observes the Obj1F id before the ROM slot clears");

        platform.update(326, new TestablePlayableSprite("sonic", (short) 0x04C0, (short) 0x0555));
        assertTrue(platform.isDestroyed(),
                "Once the vertical-only grace expires, Obj1F_FragmentFall deletes the parent slot");
    }

    @Test
    void collapsingPlatformFragmentsReuseParentAsFragmentZero() throws Exception {
        ObjectManager objectManager = mock(ObjectManager.class);
        StubObjectServices services = new StubObjectServices() {
                    @Override
                    public ObjectManager objectManager() {
                        return objectManager;
                    }
                };
        CollapsingPlatformObjectInstance platform = ObjectConstructionContext.construct(
                services,
                () -> new CollapsingPlatformObjectInstance(
                        new ObjectSpawn(0x0441, 0x05B0, Sonic2ObjectIds.COLLAPSING_PLATFORM, 0x00, 0, false, 0),
                        "CollapsPform"));
        platform.setServices(services);

        Method collapse = CollapsingPlatformObjectInstance.class.getDeclaredMethod("collapse");
        collapse.setAccessible(true);
        collapse.invoke(platform);

        verify(objectManager, times(6)).addDynamicObject(
                org.mockito.ArgumentMatchers.any(CollapsingPlatformObjectInstance.CollapsingPlatformFragmentInstance.class));
        verify(objectManager).markRemembered(platform.getSpawn());
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

    private static Object objectField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static ObjectManager buildSingleObjectManager(ObjectInstance instance) {
        ObjectRegistry registry = new ObjectRegistry() {
            @Override
            public ObjectInstance create(ObjectSpawn spawn) {
                return instance;
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
                // No-op for tests.
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "TEST";
            }
        };

        ObjectManager objectManager = new ObjectManager(List.of(), registry, 0, null, null,
                null, null, new StubObjectServices());
        objectManager.reset(0);
        objectManager.addDynamicObject(instance);
        return objectManager;
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
