package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLbzRollingDrumInstance {

    @Test
    void registryRoutesS3klSlot31ToLbzRollingDrum() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_LBZ);

        ObjectInstance drum = registry.create(new ObjectSpawn(
                0x1800, 0x0600, Sonic3kObjectIds.LBZ_ROLLING_DRUM, 0x40, 0, false, 0));

        assertFalse(drum instanceof PlaceholderObjectInstance,
                "S3KL slot $31 is Obj_LBZRollingDrum and must not remain a placeholder");
        assertInstanceOf(LbzRollingDrumInstance.class, drum);
        assertEquals("LBZRollingDrum", drum.getName());
    }

    @Test
    void subtypeDefinesHorizontalCaptureWidth() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1840, 0x05C0);
        player.setYSpeed((short) 0);

        drum.update(0, player);

        assertFalse(player.isOnObject(),
                "Obj_LBZRollingDrum rejects x_pos deltas >= subtype; subtype $40 accepts -$40..+$3F only");
        assertFalse(drum.isRidingForTest(player));
    }

    @Test
    void firstContactSeedsRideStateAndMinimumGroundSpeed() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);
        player.setGSpeed((short) 0);

        drum.update(0, player);

        assertTrue(player.isOnObject(), "loc_2C42C calls RideObject_SetRide for a player inside the drum window");
        assertEquals(Sonic3kObjectIds.LBZ_ROLLING_DRUM, player.getLatchedSolidObjectId(),
                "The invisible controller must latch the live object so Status_OnObj is not treated as stale");
        assertEquals(0x80, player.getFlipType(),
                "loc_2C44E writes flip_type=$80 on the capture frame");
        assertEquals(Sonic3kAnimationIds.WALK.id(), player.getAnimationId(),
                "loc_2C44E writes move.w #1,anim(a1), which means anim=0 and prev_anim=1");
        assertTrue(((UnitPlayableSprite) player).wasAnimationRestartForced(),
                "move.w #1,anim(a1) forces anim != prev_anim, so the walk/tumble script must restart");
        assertEquals((short) 1, player.getGSpeed(),
                "loc_2C44E seeds ground_vel=1 when it was zero");
        assertEquals(0x81, drum.getRideAngleForTest(player),
                "d0 < 8 stores $81 in the per-player angle byte before the ride update increments it");
    }

    @Test
    void airborneTopLandingClearsAirAndContinuesIntoRideArc() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);
        player.setAirForTest(true);
        player.setYSpeed((short) 0x0200);
        player.setXSpeed((short) 0x0300);
        player.setGSpeed((short) 0);

        drum.update(0, player);

        assertTrue(player.isOnObject(), "RideObject_SetRide should set Status_OnObj for an airborne top landing");
        assertFalse(player.getAir(), "RideObject_SetRide clears Status_InAir before the next object update");
        assertEquals((short) 0, player.getYSpeed(), "RideObject_SetRide zeroes y_vel on landing");
        assertEquals((short) 0x0300, player.getGSpeed(), "RideObject_SetRide copies x_vel to ground_vel");

        drum.update(1, player);

        assertTrue(player.isOnObject(),
                "The drum must keep the rider latched after the airborne landing instead of releasing immediately");
        assertEquals(expectedRideY(0x81, player.getYRadius(), 0x0600), player.getCentreY() & 0xFFFF,
                "The next frame should enter loc_2C4BA and rotate Sonic around the drum");
    }

    @Test
    void airborneRollingLandingRunsPlayerTouchFloorBeforeRide() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);
        player.setAirForTest(true);
        player.setRolling(true);
        player.setYSpeed((short) 0x0200);

        drum.update(0, player);

        assertFalse(player.getRolling(), "RideObject_SetRide calls Player_TouchFloor, which clears Status_Roll");
        assertEquals(player.getStandYRadius(), player.getYRadius(),
                "Player_TouchFloor restores default y_radius before the drum's sine path consumes it");
        assertEquals(Sonic3kAnimationIds.WALK.id(), player.getAnimationId(),
                "Player_TouchFloor plus loc_2C44E leave the player in the walk/tumble animation slot");
    }

    @Test
    void middleRecapturePreservesPreviousNativeAngleByte() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);

        drum.update(0, player);
        drum.update(1, player);
        int angleAfterRide = drum.getRideAngleForTest(player);

        player.setCentreXPreserveSubpixel((short) 0x1840);
        player.setAir(false);
        drum.update(2, player);
        assertFalse(player.isOnObject());

        player.setCentreXPreserveSubpixel((short) 0x1800);
        player.setCentreY((short) 0x0600);
        player.setYSpeed((short) 0);
        player.setAir(false);
        drum.update(3, player);

        assertTrue(player.isOnObject());
        assertEquals(angleAfterRide, drum.getRideAngleForTest(player),
                "loc_2C42C only writes _unkF7B0 for d0<8 or d0>=$9E; middle recapture leaves it unchanged");
    }

    @Test
    void nativeAngleByteIsSharedAcrossRollingDrumControllerObjects() {
        ZoneRuntimeRegistry registry = lbzRuntimeRegistry();
        LbzRollingDrumInstance first = drum(0x1800, 0x0600, 0x40);
        LbzRollingDrumInstance second = drum(0x1880, 0x0600, 0x40);
        TestObjectServices services = new TestObjectServices().withZoneRuntimeRegistry(registry);
        first.setServices(services);
        second.setServices(services);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);

        first.update(0, player);
        first.update(1, player);
        int angleAfterFirstController = first.getRideAngleForTest(player);
        player.setCentreXPreserveSubpixel((short) 0x1840);
        player.setAir(false);
        first.update(2, player);
        assertFalse(player.isOnObject());

        player.setCentreXPreserveSubpixel((short) 0x1880);
        player.setCentreY((short) 0x0600);
        player.setYSpeed((short) 0);
        player.setAir(false);
        second.update(3, player);

        assertTrue(player.isOnObject());
        assertEquals(angleAfterFirstController, second.getRideAngleForTest(player),
                "_unkF7B0 is LBZ runtime RAM shared by every Obj31 controller, so middle handoff must not reset angle");
    }

    @Test
    void activeRideWritesYFromRomSinePathAndAdvancesPerPlayerAngle() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);

        drum.update(0, player);
        drum.update(1, player);

        int expectedY = expectedRideY(0x81, player.getYRadius(), 0x0600);
        assertEquals(expectedY, player.getCentreY() & 0xFFFF,
                "loc_2C4BA writes y_pos from GetSineCosine(angle), y_radius, and the drum centre");
        assertEquals(0x01, player.getFlipAngle(),
                "loc_2C4BA stores angle+$80 in flip_angle");
        assertEquals(0x83, drum.getRideAngleForTest(player),
                "loc_2C4BA increments the per-player angle byte by 2 each active ride frame");
        assertTrue(player.isHighPriority(),
                "loc_2C506 keeps art_tile priority when angle+$80 remains positive");
    }

    @Test
    void activeRideRestoresObjectOwnedTumbleMappingAfterNormalAnimationOverwrite() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);

        drum.update(0, player);
        player.setAnimationId(Sonic3kAnimationIds.WAIT.id());
        player.setObjectMappingFrameControl(false);
        player.setMappingFrame(0);
        drum.update(1, player);

        assertEquals(Sonic3kAnimationIds.WALK.id(), player.getAnimationId(),
                "Obj31 keeps anim=0 active while flip_type/flip_angle select Anim_Tumble");
        assertTrue(player.isObjectMappingFrameControl(),
                "The drum must own the visible tumble mapping so idle animation cannot overwrite it between object frames");
        assertEquals(rollingDrumTumbleFrame(0x01, 0x80), player.getMappingFrame(),
                "Anim_Tumble uses flip_angle/flip_type to choose the rolling-drum pose");
    }

    @Test
    void activeRideAppliesRomNegativeTumbleRenderFlagsForRightFacingPlayer() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);
        player.setDirection(Direction.RIGHT);

        drum.update(0, player);
        drum.update(1, player);

        assertFalse(player.getRenderHFlip(),
                "Anim_Tumble right-facing flip_type=$80 clears render_flags bit 0");
        assertTrue(player.getRenderVFlip(),
                "Anim_Tumble right-facing flip_type=$80 sets render_flags bit 1");
    }

    @Test
    void activeRideAppliesRomNegativeTumbleRenderFlagsForLeftFacingPlayer() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05AD);
        player.setDirection(Direction.LEFT);

        drum.update(0, player);
        drum.update(1, player);

        assertTrue(player.getRenderHFlip(),
                "Anim_Tumble left-facing flip_type=$80 sets render_flags bit 0");
        assertTrue(player.getRenderVFlip(),
                "Anim_Tumble left-facing flip_type=$80 sets render_flags bit 1 in LBZ");
    }

    @Test
    void latchedStaleAirBitDoesNotDropRiderThroughBottom() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05C0);
        player.setGSpeed((short) 0x0200);

        drum.update(0, player);
        drum.update(1, player);
        player.setCentreXPreserveSubpixel((short) 0x1830);
        player.setCentreY((short) 0x0660);
        player.setAir(true);
        player.setOnObject(true);
        player.setLatchedSolidObject(Sonic3kObjectIds.LBZ_ROLLING_DRUM, drum);
        drum.update(2, player);

        assertTrue(player.isOnObject(),
                "A non-jumping, non-hurt rider still latched to Obj31 should remain captured inside the horizontal window");
        assertFalse(player.getAir(),
                "The engine's stale air bit must be cleared instead of taking loc_2C48A's release branch");
        assertTrue(drum.isRidingForTest(player));
    }

    @Test
    void rightMovingRiderWithLostPlayerLatchIsReattachedInsideHorizontalWindow() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05C0);
        player.setGSpeed((short) 0x0300);

        drum.update(0, player);
        drum.update(1, player);
        player.setCentreXPreserveSubpixel((short) 0x1838);
        player.setCentreY((short) 0x0660);
        player.setAir(true);
        player.setOnObject(false);
        player.setLatchedSolidObjectId(0);
        drum.update(2, player);

        assertTrue(player.isOnObject(),
                "Obj31 standing state still owns Sonic while x_pos is within -subtype..+subtype-1");
        assertFalse(player.getAir(),
                "A lost player-side Status_OnObj latch must be repaired instead of dropping the rider from the bottom");
        assertTrue(drum.isRidingForTest(player));
    }

    @Test
    void leavingHorizontalWindowReleasesWithFlipRecoveryState() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05C0);

        drum.update(0, player);
        drum.update(1, player);
        assertTrue(player.isObjectMappingFrameControl());
        player.setCentreXPreserveSubpixel((short) 0x1840);
        player.setAir(false);
        player.setFlipsRemaining(0x22);
        player.setFlipSpeed(0x08);
        drum.update(2, player);

        assertFalse(player.isOnObject(), "loc_2C48A clears Status_OnObj when x_pos exits the drum range");
        assertEquals(0, player.getLatchedSolidObjectId());
        assertTrue(player.getAir(), "loc_2C48A sets Status_InAir on release");
        assertEquals(0, player.getFlipsRemaining(), "loc_2C48A clears flips_remaining");
        assertEquals(4, player.getFlipSpeed(), "loc_2C48A writes flip_speed=4");
        assertFalse(player.isObjectMappingFrameControl(),
                "Object-owned tumble mapping must end when loc_2C48A releases the player");
    }

    @Test
    void airborneRiderBelowDrumGetsDownwardVelocityOnRelease() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite player = groundedPlayer(0x1800, 0x05C0);

        drum.update(0, player);
        player.setAir(true);
        player.setOnObject(false);
        player.setJumping(true);
        player.setCentreY((short) 0x0610);
        drum.update(1, player);

        assertFalse(player.isOnObject());
        assertEquals((short) 0x0400, player.getYSpeed(),
                "loc_2C4A8 writes y_vel=$400 when an airborne rider has crossed below the drum centre");
    }

    @Test
    void nativeP2UsesIndependentAngleStateInSameObjectUpdate() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite sonic = groundedPlayer(0x1800, 0x05AD);
        TestablePlayableSprite tails = groundedPlayer(0x17F8, 0x05AD);
        drum.setServices(new TestObjectServices() {
            @Override
            public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> sonic, () -> List.of(tails));
            }
        });

        drum.update(0, sonic);

        assertTrue(sonic.isOnObject());
        assertTrue(tails.isOnObject(), "loc_2C3CA processes Player_2 after Player_1 in the same object update");
        assertEquals(0x81, drum.getNativeRideAngleForTest(0));
        assertEquals(0x81, drum.getNativeRideAngleForTest(1),
                "The ROM stores P1/P2 drum angles in adjacent bytes, not one shared global angle");
    }

    @Test
    void rewindRoundTripsNativeRideAngleState() {
        LbzRollingDrumInstance drum = drum(0x1800, 0x0600, 0x40);
        TestablePlayableSprite sonic = groundedPlayer(0x1800, 0x05AD);
        TestablePlayableSprite tails = groundedPlayer(0x17F8, 0x05AD);
        drum.setServices(new TestObjectServices() {
            @Override
            public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> sonic, () -> List.of(tails));
            }
        });
        drum.update(0, sonic);
        drum.update(1, sonic);

        PerObjectRewindSnapshot snapshot = drum.captureRewindState();

        sonic.setCentreXPreserveSubpixel((short) 0x1840);
        tails.setCentreXPreserveSubpixel((short) 0x1840);
        drum.update(2, sonic);
        assertFalse(drum.isNativeRidingForTest(0));
        assertFalse(drum.isNativeRidingForTest(1));

        drum.restoreRewindState(snapshot);

        assertTrue(drum.isNativeRidingForTest(0));
        assertTrue(drum.isNativeRidingForTest(1));
        assertEquals(0x83, drum.getNativeRideAngleForTest(0));
        assertEquals(0x83, drum.getNativeRideAngleForTest(1));
    }

    private static LbzRollingDrumInstance drum(int x, int y, int subtype) {
        return new LbzRollingDrumInstance(new ObjectSpawn(
                x, y, Sonic3kObjectIds.LBZ_ROLLING_DRUM, subtype, 0, false, 0));
    }

    private static TestablePlayableSprite groundedPlayer(int x, int y) {
        TestablePlayableSprite player = new UnitPlayableSprite("sonic", (short) x, (short) y);
        player.setAir(false);
        return player;
    }

    private static int expectedRideY(int angle, int yRadius, int drumY) {
        int cos = TrigLookupTable.cosHex(angle);
        int radius = (yRadius << 8) + 0x4000;
        return drumY + ((cos * radius) >> 16);
    }

    private static int rollingDrumTumbleFrame(int flipAngle, int flipType) {
        int type = flipType & 0x7F;
        if (type == 1) {
            return (((flipAngle & 0xFF) - 8) & 0xFF) / 0x16 + 0x3D;
        }
        return ((0x8F - (flipAngle & 0xFF)) & 0xFF) / 0x16 + 0x31;
    }

    private static ZoneRuntimeRegistry lbzRuntimeRegistry() {
        ZoneRuntimeRegistry registry = new ZoneRuntimeRegistry();
        registry.install(new LbzZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE));
        return registry;
    }

    private static final class UnitPlayableSprite extends TestablePlayableSprite {
        private boolean animationRestartForced;

        private UnitPlayableSprite(String characterCode, short x, short y) {
            super(characterCode, x, y);
        }

        @Override
        public void setAir(boolean air) {
            setAirForTest(air);
        }

        @Override
        public void forceAnimationRestart() {
            animationRestartForced = true;
        }

        private boolean wasAnimationRestartForced() {
            return animationRestartForced;
        }
    }

    private static final class ZoneForTestRegistry extends Sonic3kObjectRegistry {
        private final int zoneId;

        private ZoneForTestRegistry(int zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        protected int currentRomZoneId() {
            return zoneId;
        }
    }
}
