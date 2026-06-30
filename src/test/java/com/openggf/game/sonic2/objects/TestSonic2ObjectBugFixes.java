package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PhysicsFeatureSet;
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
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
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
    void oozLauncherBallCaptureUsesObjectControlWithoutGlobalControlLockedLatch() {
        LauncherBallObjectInstance.clearActiveCaptures();
        ObjectSpawn spawn = new ObjectSpawn(0x1240, 0x02E0, Sonic2ObjectIds.LAUNCHER_BALL, 0x00, 0, false, 0);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) spawn.x(), (short) spawn.y());
        player.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        player.setLogicalInputState(false, false, true, false, false);
        player.endOfTick();
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, player.getInputHistory(0));

        LauncherBallObjectInstance launcherBall = new LauncherBallObjectInstance(spawn, "LauncherBall");
        launcherBall.setServices(new StubObjectServices()
                .withPlayerQuery(new ObjectPlayerQuery(() -> player, () -> List.of())));

        launcherBall.update(0, player);

        assertTrue(player.isObjectControlled(),
                "Obj48 writes obj_control=$81, so launcher-ball capture must suppress normal movement.");
        assertFalse(player.isControlLocked(),
                "Obj48 loc_2535E writes obj_control(a1), not global Control_Locked; Obj01_Control must keep "
                        + "refreshing Ctrl_1_Logical before Sonic_RecordPos stores the follower-history word "
                        + "(docs/s2disasm/s2.asm:51341-51367,36233-36252,36342-36353).");

        player.setLogicalInputState(false, false, false, false, false);
        player.endOfTick();
        assertEquals(0, player.getInputHistory(0),
                "With Control_Locked untouched, raw neutral input refreshes Ctrl_1_Logical instead of preserving "
                        + "stale pre-capture LEFT for TailsCPU_Normal's delayed read.");
    }

    @Test
    void oozInvisibleLauncherCaptureUsesObjectControlWithoutGlobalControlLockedLatch() throws Exception {
        ObjectSpawn spawn = new ObjectSpawn(0x1110, 0x0298, Sonic2ObjectIds.OOZ_LAUNCHER, 0x00, 0, false, 0);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) spawn.x(), (short) spawn.y());
        player.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        player.setLogicalInputState(false, false, true, false, false);
        player.endOfTick();
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, player.getInputHistory(0));

        OOZLauncherObjectInstance launcher = new OOZLauncherObjectInstance(spawn, "OOZLauncher");
        launcher.setServices(new StubObjectServices());
        Method proximity = OOZLauncherObjectInstance.class.getDeclaredMethod(
                "processProximityDetection", AbstractPlayableSprite.class);
        proximity.setAccessible(true);

        int nextState = (int) proximity.invoke(launcher, player);

        assertEquals(2, nextState, "Obj3D loc_24FC2 advances the per-player launcher state to tracking.");
        assertTrue(player.isObjectControlled(),
                "Obj3D writes obj_control=$81, so invisible-launcher tracking must suppress normal movement.");
        assertFalse(player.isControlLocked(),
                "Obj3D loc_24FC2 writes obj_control(a1), not global Control_Locked; Obj01_Control must keep "
                        + "refreshing Ctrl_1_Logical before Sonic_RecordPos stores the follower-history word "
                        + "(docs/s2disasm/s2.asm:51123-51158,36233-36252,36342-36353).");

        player.setLogicalInputState(false, false, false, false, false);
        player.endOfTick();
        assertEquals(0, player.getInputHistory(0),
                "With Control_Locked untouched, raw neutral input refreshes Ctrl_1_Logical while Obj3D owns "
                        + "movement through obj_control.");
    }

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
        platform.setServices(new ZoneActServices(null, Sonic2ZoneConstants.ROM_ZONE_MTZ_3, 0, null));

        platform.update(0, new TestablePlayableSprite("sonic", (short) 0x1CBE, (short) 0x02E0));

        assertEquals(0, intField(platform, "moveSubtype"),
                "MTZ Act 3 subtype-5 conveyor must stop at the first MTZ3 stop point");
        assertEquals(0x1CC0, platform.getX(),
                "Regression setup should land exactly on the first MTZ3 stop point");
    }

    @Test
    void mtzAct3LongPlatformKeepsMovingRightThroughMtz12StopPoint() throws Exception {
        MTZLongPlatformObjectInstance platform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x1BBE, 0x04C8, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x05, 0, false, 0));
        platform.setServices(new ZoneActServices(null, Sonic2ZoneConstants.ROM_ZONE_MTZ_3, 0, null));

        platform.update(0, new TestablePlayableSprite("sonic", (short) 0x1BBE, (short) 0x04AC));
        platform.update(1, new TestablePlayableSprite("sonic", (short) 0x1BC0, (short) 0x04AC));

        assertEquals(0x1BC2, platform.getX(),
                "ROM Obj65 loc_26E4A only treats $1BC0 as a reverse point outside metropolis_zone_2");
        assertEquals(5, intField(platform, "moveSubtype"),
                "MTZ3 must continue subtype-5 conveyor motion until $1CC0 or $2940");
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
    void mtzLongPlatformMappingFrameOneSuppressesObjectEdgeBalance() {
        MTZLongPlatformObjectInstance normalPlatform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x0B20, 0x076C, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x00, 0, false, 0));
        MTZLongPlatformObjectInstance noBalancePlatform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x0B20, 0x076C, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x10, 0, false, 0));

        assertFalse(normalPlatform.suppressesObjectEdgeBalance(),
                "Obj65 mapping_frame 0 leaves status.npc.no_balancing clear");
        assertTrue(noBalancePlatform.suppressesObjectEdgeBalance(),
                "Obj65_Init sets status.npc.no_balancing when mapping_frame == 1 (s2.asm:52865-52870)");
    }

    @Test
    void mtzLongPlatformOptsIntoZeroXSpeedLeftSideStopCharacter() {
        MTZLongPlatformObjectInstance platform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x1090, 0x01EC, Sonic2ObjectIds.MTZ_LONG_PLATFORM, 0x00, 0, false, 0));

        assertTrue(platform.zeroXSpeedStopsOnLeftSideContact(),
                "Obj65 reaches S2 SolidObject_InsideLeft with x_vel == 0; that falls through to "
                        + "SolidObject_StopCharacter and clears inertia (docs/s2disasm/s2.asm:35424-35439)");
    }

    @Test
    void mczRotPformsUseSolidObjectContStatusTiming() {
        MCZRotPformsObjectInstance platform = new MCZRotPformsObjectInstance(
                new ObjectSpawn(0x0E80, 0x05A0, Sonic2ObjectIds.MCZ_ROT_PFORMS, 0x00, 0, false, 0),
                "MCZRotPforms");
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0x0EAB, (short) 0x05F0);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x0EAB, (short) 0x05F0);
        tails.setCpuControlled(true);

        assertTrue(platform.usesInclusiveRightEdge(),
                "Obj6A reaches JmpTo13_SolidObject, and SolidObject_cont rejects the right edge with bhi "
                        + "(docs/s2disasm/s2.asm:54276,54301,35344-35354)");
        assertTrue(platform.usesInstanceSolidStateLatchKey(),
                "Obj6A rewrites dynamic spawn coordinates while ROM keeps standing/pushing bits in the live SST slot");
        assertFalse(platform.preservesSidekickCpuPushGraceWhileRiding(sonic));
        assertTrue(platform.preservesSidekickCpuPushGraceWhileRiding(tails),
                "TailsCPU_Normal reads Status_Push before the next Obj6A SolidObject pass clears it");
        assertEquals(8, platform.sidekickCpuPushGraceMinimumFramesWhileRiding(tails),
                "MCZ2 f4485 keeps the post-Obj6A push bit visible to the Tails CPU slot with eight grace frames");
    }

    @Test
    void htzRisingLavaSubtypeSixUsesCpuSidekickObjectOrderInputDelay() {
        RisingLavaObjectInstance lowerRoutePlatform = new RisingLavaObjectInstance(
                new ObjectSpawn(0x1760, 0x07D4, Sonic2ObjectIds.RISING_LAVA, 0x06, 0, false, 0),
                "RisingLava");
        RisingLavaObjectInstance slopedPlatform = new RisingLavaObjectInstance(
                new ObjectSpawn(0x1760, 0x07D4, Sonic2ObjectIds.RISING_LAVA, 0x08, 0, false, 0),
                "RisingLavaSlope");
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0x170A, (short) 0x074D);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x170A, (short) 0x074D);
        tails.setCpuControlled(true);

        assertTrue(lowerRoutePlatform.usesSidekickCpuCurrentPushObjectOrderInputDelay(tails),
                "HTZ2 f3322 reaches Obj30 subtype 6's SolidObject_Always/DropOnFloor ordering with "
                        + "Tails' current Status_Push still visible but the adjacent delayed input already flipped");
        assertFalse(lowerRoutePlatform.usesSidekickCpuCurrentPushObjectOrderInputDelay(sonic),
                "The bridge is only for CPU sidekick Ctrl_2 sampling");
        assertFalse(slopedPlatform.usesSidekickCpuCurrentPushObjectOrderInputDelay(tails),
                "Subtype 8 uses SlopedSolid and is not part of the HTZ2 lower-route Obj30 ordering window");
        tails.setCentreX((short) 0x19BA);
        assertFalse(new RisingLavaObjectInstance(
                        new ObjectSpawn(0x1920, 0x06B9, Sonic2ObjectIds.RISING_LAVA, 0x06, 0, false, 0),
                        "RisingLavaRightSide")
                        .usesSidekickCpuCurrentPushObjectOrderInputDelay(tails),
                "HTZ2 f4442 rides subtype 6 on the right side; ROM keeps the normal d1 history word "
                        + "already loaded at s2.asm:39291-39300 instead of the adjacent older input");
    }

    @Test
    void cpzStaircasePreservesRidingPushOnlyAtLowerStepSideOverlap() {
        CPZStaircaseObjectInstance staircase = new CPZStaircaseObjectInstance(
                new ObjectSpawn(0x2090, 0x0350, Sonic2ObjectIds.CPZ_STAIRCASE, 0x01, 1, false, 0),
                "CPZStaircase");
        for (int frame = 0; frame < 0x20; frame++) {
            staircase.update(frame, null);
        }

        TestablePlayableSprite tails = new TestablePlayableSprite(
                "tails", (short) staircase.getPieceX(2), (short) staircase.getPieceY(2));
        tails.setCpuControlled(true);
        tails.setDirection(Direction.RIGHT);

        assertFalse(staircase.preservesRidingPushStatus(tails),
                "CPZ1 f4351 has Tails near the centre of Obj78 slot 0x1F; ROM has no Status_Push, "
                        + "so TailsCPU_Normal must still consume the +1 FollowRight nudge");

        tails.setDirection(Direction.LEFT);
        assertFalse(staircase.preservesSidekickCpuPushGraceWhileRiding(tails),
                "Obj78 CPU-only grace models child-slot side-push visibility; it must not apply when "
                        + "Tails is centered on a stair piece with no adjacent side overlap");
        assertTrue(staircase.preservesSidekickDelayedLeaderPushWhileRiding(tails),
                "Obj78 child SolidObject slots can keep the delayed Sonic_Stat_Record_Buf push visible "
                        + "while CPU Tails rides the folded staircase (docs/s2disasm/s2.asm:55967-56021)");

        tails.setDirection(Direction.RIGHT);
        tails.setCentreX((short) (staircase.getPieceX(3) - staircase.getPieceParams(3).halfWidth()));
        assertTrue(staircase.preservesRidingPushStatus(tails),
                "Obj78's folded multi-piece latch is still needed when the rider is actually pressed "
                        + "into the lower neighbouring child slot's side");

        TestablePlayableSprite sonic = new TestablePlayableSprite(
                "sonic", (short) staircase.getPieceX(2), (short) staircase.getPieceY(2));
        assertFalse(staircase.preservesSidekickDelayedLeaderPushWhileRiding(sonic),
                "The delayed leader push bridge is only for CPU sidekick follow control");
    }

    @Test
    void cpzStaircaseKeepsCpuTailsCurrentPushWhenFacingHigherAdjacentStep() {
        CPZStaircaseObjectInstance staircase = new CPZStaircaseObjectInstance(
                new ObjectSpawn(0x1510, 0x0702, Sonic2ObjectIds.CPZ_STAIRCASE, 0x01, 1, false, 0),
                "CPZStaircase");
        for (int frame = 0; frame < 0x20; frame++) {
            staircase.update(frame, null);
        }

        TestablePlayableSprite tails = new TestablePlayableSprite(
                "tails", (short) (staircase.getPieceX(1) - 5), (short) staircase.getPieceY(1));
        tails.setCpuControlled(true);
        tails.setDirection(Direction.LEFT);

        assertTrue(staircase.preservesRidingPushStatus(tails),
                "CPZ2 f5285 has Tails on the lower Obj78 child facing the higher child slot; "
                        + "the ROM child SolidObject pass leaves Tails' current Status_Push visible "
                        + "for TailsCPU_Normal's push bypass");
        assertFalse(staircase.preservesSidekickDelayedLeaderPushWhileRiding(tails),
                "When Obj78 already preserves Tails' current Status_Push, the delayed leader sample "
                        + "must not also be forced to pushing or TailsCPU_Normal misses the auto-jump path");
        assertTrue(staircase.preservesSidekickCpuPushGraceWhileRiding(tails),
                "The same child-slot side contact supplies the ROM-visible current push grace when "
                        + "the folded engine status was already cleared before TailsCPU_Normal");
        assertTrue(staircase.usesSidekickCpuPushBypassObjectOrderStatusDelay(tails),
                "TailsCPU_Normal must compare that current push against Obj78's object-order leader "
                        + "status sample, not the final-frame status column");

        TestablePlayableSprite sonic = new TestablePlayableSprite(
                "sonic", (short) (staircase.getPieceX(1) - 5), (short) staircase.getPieceY(1));
        sonic.setDirection(Direction.LEFT);
        assertTrue(staircase.preservesRidingPushStatus(sonic),
                "The same folded child-slot push is visible to Sonic_Stat_Record_Buf when Sonic is "
                        + "also pressed into the higher Obj78 child slot, as at CPZ2 f5221");

        tails.setCentreX((short) (staircase.getPieceX(3) - 5));
        assertTrue(staircase.preservesSidekickDelayedLeaderPushWhileRiding(tails),
                "Later Obj78 child-slot contact still preserves the delayed leader push window that "
                        + "keeps CPZ2 f5221 on the normal follow-steering path");
        assertFalse(staircase.usesSidekickCpuPushBypassObjectOrderStatusDelay(tails),
                "The f5221 later-child window must keep the final delayed leader push sample; only "
                        + "the first-child f5285 handoff uses the object-order status byte");
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
    void genericPlatformOutOfRangeUsesStoredOriginX() throws Exception {
        ARZPlatformObjectInstance platform = new ARZPlatformObjectInstance(
                new ObjectSpawn(0x1940, 0x06C8, Sonic2ObjectIds.GENERIC_PLATFORM_A, 0x01, 0, false, 0x06C8),
                "GenericPlatform");
        setIntField(platform, "x", 0x190D);

        assertEquals(0x1940, platform.getOutOfRangeReferenceX(),
                "Obj18_Despawn checks obj18_x_origin, not moving x_pos(a0), before deleting");
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
    void s2SpikesUsePostObj33SidekickPushGraceThreshold() {
        SpikeObjectInstance spikes = new SpikeObjectInstance(
                new ObjectSpawn(0x0CF0, 0x0594, Sonic2ObjectIds.SPIKES, 0x30, 2, false, 0x4650),
                "Spikes");
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0x0CE3, (short) 0x0574);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x0CE3, (short) 0x0574);
        tails.setCpuControlled(true);

        tails.setGSpeed((short) -0x000C);
        assertFalse(spikes.preservesSidekickCpuPushGraceWhileRiding(sonic));
        assertEquals(Integer.MAX_VALUE, spikes.sidekickCpuPushGraceMinimumFramesWhileRiding(sonic));
        assertTrue(spikes.preservesSidekickCpuPushGraceWhileRiding(tails));
        assertEquals(8, spikes.sidekickCpuPushGraceMinimumFramesWhileRiding(tails),
                "OOZ1 f1782 reaches Obj36 riding push grace with eight frames remaining");

        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) -0x0018);
        tails.setXSpeed((short) -0x0018);
        assertEquals(0, spikes.sidekickCpuPushGraceMinimumFramesWhileRiding(tails),
                "OOZ1 f1794 reaches Obj36's inner-left edge with fresh negative inertia before Tails_TurnRight");

        tails.setGSpeed((short) 0x0080);
        assertEquals(14, spikes.sidekickCpuPushGraceMinimumFramesWhileRiding(tails),
                "The faster positive-inertia spike ride keeps the conservative existing bridge window");

        tails.setCentreX((short) 0x0CE3);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0018);
        tails.setXSpeed((short) 0x0018);
        assertEquals(Integer.MAX_VALUE, spikes.sidekickCpuPushGraceMaximumFramesWhileRiding(tails),
                "OOZ1 f1775 is still one pixel inside Obj36's left edge and keeps the long bridge");

        tails.setCentreX((short) 0x0CE4);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0018);
        tails.setXSpeed((short) 0x0018);
        assertEquals(2, spikes.sidekickCpuPushGraceMinimumFramesWhileRiding(tails),
                "OOZ1 f1803 is a late low-speed positive-inertia sample; only the immediate Obj36 bridge applies");
        assertEquals(3, spikes.sidekickCpuPushGraceMaximumFramesWhileRiding(tails),
                "At f1803 the later SolidObject pass sets Status_Push after TailsCPU_Normal, so grace=15 must fall through follow steering");

        tails.setGSpeed((short) 0x0080);
        tails.setXSpeed((short) 0x0080);
        assertEquals(3, spikes.sidekickCpuPushGraceMaximumFramesWhileRiding(tails),
                "OOZ1 f1805 is the late positive rebound at the same edge; grace=13 must not keep preserving delayed RIGHT");
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

    @Test
    void mtzCogFirstMainExecutionRotatesOnCurrentRomLowByteZero() {
        LevelManager levelManager = mock(LevelManager.class);
        CogObjectInstance cog = new CogObjectInstance(
                new ObjectSpawn(0x0380, 0x0400, Sonic2ObjectIds.COG, 0x00, 0, false, 0),
                "Cog");
        cog.setServices(new StubObjectServices() {
            @Override
            public LevelManager levelManager() {
                return levelManager;
            }
        });

        when(levelManager.getFrameCounter()).thenReturn(0x0520);
        cog.update(0x3751, new TestablePlayableSprite("sonic", (short) 0x0380, (short) 0x03A0));

        assertEquals(0x038D, cog.getPieceX(0),
                "A just-streamed Obj70 whose first Main pass lands on Level_frame_counter low byte zero "
                        + "must take the same rotation tick as the copied ROM tooth slots");
        assertEquals(0x03B8, cog.getPieceY(0));
    }

    @Test
    void mtzCogLandingUsesFullRomWidthPixelsWindow() {
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getFrameCounter()).thenReturn(0x04DB);
        CogObjectInstance cog = new CogObjectInstance(
                new ObjectSpawn(0x0480, 0x0480, Sonic2ObjectIds.COG, 0x00, 0, false, 0),
                "Cog");
        cog.setServices(new StubObjectServices() {
            @Override
            public LevelManager levelManager() {
                return levelManager;
            }
        });
        cog.update(0, new TestablePlayableSprite("sonic", (short) 0x0480, (short) 0x0400));
        cog.snapshotPreUpdatePosition();
        ObjectManager manager = buildSingleObjectManager(cog);

        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setWidth(18);
        sonic.setHeight(38);
        sonic.setAir(false);
        sonic.setAngle((byte) 0x34);
        sonic.setGroundMode(com.openggf.game.GroundMode.LEFTWALL);
        sonic.setXSpeed((short) 0x014B);
        sonic.setYSpeed((short) 0x0444);
        sonic.setGSpeed((short) 0x047A);
        sonic.setCentreX((short) 0x04D0);
        sonic.setCentreY((short) 0x0464);

        manager.updateSolidContacts(sonic);

        assertTrue(sonic.isOnObject(),
                "Obj70 SolidObject_Landed re-checks width_pixels=$10, so x_pos +8 from tooth centre must land");
        assertFalse(sonic.getAir());
        assertEquals(0, sonic.getAngle() & 0xFF);
        assertEquals(0, sonic.getYSpeed());
        assertEquals(0x014B, sonic.getGSpeed());
    }

    @Test
    void mtzCogGroundedCpuSideContactWithoutStandingBitReachesRomStopCharacterPath() {
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getFrameCounter()).thenReturn(0x04E7);
        CogObjectInstance cog = new CogObjectInstance(
                new ObjectSpawn(0x0480, 0x0480, Sonic2ObjectIds.COG, 0x00, 0, false, 0),
                "Cog");
        cog.setServices(new StubObjectServices() {
            @Override
            public LevelManager levelManager() {
                return levelManager;
            }
        });
        cog.update(0, new TestablePlayableSprite("sonic", (short) 0x0480, (short) 0x0400));
        cog.snapshotPreUpdatePosition();
        ObjectManager manager = buildSingleObjectManager(cog);

        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x047D, (short) 0x0431);
        tails.setWidth(18);
        tails.setHeight(18);
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setXSpeed((short) 0x01E7);
        tails.setGSpeed((short) 0x01EB);

        manager.updateSolidContacts(tails);

        assertTrue(tails.getPushing(),
                "Grounded Obj70 side contact without a standing bit must set Status_Push");
    }

    @Test
    void mtzCogLeftwardGroundedCpuSideContactWithoutStandingBitStillPushes() {
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getFrameCounter()).thenReturn(0x04E7);
        CogObjectInstance cog = new CogObjectInstance(
                new ObjectSpawn(0x0480, 0x0480, Sonic2ObjectIds.COG, 0x00, 0, false, 0),
                "Cog");
        cog.setServices(new StubObjectServices() {
            @Override
            public LevelManager levelManager() {
                return levelManager;
            }
        });
        cog.update(0, new TestablePlayableSprite("sonic", (short) 0x0480, (short) 0x0400));
        cog.snapshotPreUpdatePosition();
        ObjectManager manager = buildSingleObjectManager(cog);

        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x04B2, (short) 0x042E);
        tails.setWidth(18);
        tails.setHeight(18);
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setXSpeed((short) -0x0080);
        tails.setGSpeed((short) -0x0080);

        manager.updateSolidContacts(tails);

        assertTrue(tails.getPushing(),
                "Obj70 reaches SolidObject_cont with no standing bit; moving left must not be mistaken "
                        + "for the stale-standing d4=0 branch (s2.asm:35021-35044, 55080-55141)");
    }

    @Test
    void mtzCogHighSpeedLeftwardReleaseStillSkipsFoldedSiblingSideStop() {
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getFrameCounter()).thenReturn(0x04E7);
        CogObjectInstance cog = new CogObjectInstance(
                new ObjectSpawn(0x0480, 0x0480, Sonic2ObjectIds.COG, 0x00, 0, false, 0),
                "Cog");
        cog.setServices(new StubObjectServices() {
            @Override
            public LevelManager levelManager() {
                return levelManager;
            }
        });
        cog.update(0, new TestablePlayableSprite("sonic", (short) 0x0480, (short) 0x0400));
        cog.snapshotPreUpdatePosition();
        ObjectManager manager = buildSingleObjectManager(cog);

        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x04B2, (short) 0x042E);
        tails.setWidth(18);
        tails.setHeight(18);
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setXSpeed((short) -0x0700);
        tails.setGSpeed((short) -0x0700);

        manager.updateSolidContacts(tails);

        assertFalse(tails.getPushing(),
                "A high-speed leftward Obj70 release is stale folded-slot geometry, not a fresh grounded push");
        assertEquals(-0x0700, tails.getXSpeed(),
                "The folded sibling side path must not run SolidObject_StopCharacter for the stale release");
    }

    @Test
    void mtzCogAirborneHurtCpuSideContactWithoutStandingBitReachesRomStopCharacterPath() {
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getFrameCounter()).thenReturn(0x04E7);
        CogObjectInstance cog = new CogObjectInstance(
                new ObjectSpawn(0x0480, 0x0480, Sonic2ObjectIds.COG, 0x00, 0, false, 0),
                "Cog");
        cog.setServices(new StubObjectServices() {
            @Override
            public LevelManager levelManager() {
                return levelManager;
            }
        });
        cog.update(0, new TestablePlayableSprite("sonic", (short) 0x0480, (short) 0x0400));
        cog.snapshotPreUpdatePosition();
        ObjectManager manager = buildSingleObjectManager(cog);

        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x04B2, (short) 0x042E);
        tails.setWidth(18);
        tails.setHeight(18);
        tails.setCpuControlled(true);
        tails.setAir(true);
        tails.setHurt(true);
        tails.setXSpeed((short) -0x0200);
        tails.setYSpeed((short) 0x0170);
        tails.setGSpeed((short) -0x0200);

        manager.updateSolidContacts(tails);

        assertEquals(0, tails.getXSpeed(),
                "Obj02_Hurt does not self-clear x_vel until landing; an airborne clear-bit Obj70 side hit "
                        + "must still reach SolidObject_StopCharacter (s2.asm:41063-41110,35413-35436)");
        assertEquals(0, tails.getGSpeed(),
                "SolidObject_StopCharacter clears inertia/g_speed together with x_vel");
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
