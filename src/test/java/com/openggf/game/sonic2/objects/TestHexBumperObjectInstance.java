package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.GameModule;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TestHexBumperObjectInstance {

    @Test
    void nativeCompetitionKeepsMovingBumperPastBothCameraEndpoints() {
        var module = mock(GameModule.class);
        var policy = mock(WorldSessionPolicyState.class);
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policy);
        when(policy.getService(NativeCompetitionRoster.class)).thenReturn(mock(NativeCompetitionRoster.class));
        var world = new WorldSession(module);
        var bumper = new HexBumperObjectInstance(
                new ObjectSpawn(0x1FF8, 0x028C, 0xD7, 0x01, 0, false, 0x828C), "HexBumper");
        bumper.setServices(new StubObjectServices() {
            @Override public WorldSession worldSession() { return world; }
        });
        assertFalse(bumper.isCustomOutOfRange(0x2500));
    }

    @Test
    void movingBumperUsesRangeBoundsForOutOfRangeDelete() {
        HexBumperObjectInstance bumper = new HexBumperObjectInstance(
                new ObjectSpawn(0x1FF8, 0x028C, 0xD7, 0x01, 0, false, 0x828C),
                "HexBumper");
        bumper.setServices(new StubObjectServices());

        assertTrue(bumper.usesCustomOutOfRangeCheck());
        assertFalse(bumper.isCustomOutOfRange(0x1F80),
                "ROM ObjD7 keeps moving bumpers alive while either objoff_30 or objoff_32 is in range");
        assertTrue(bumper.isCustomOutOfRange(0x2500),
                "ROM ObjD7 deletes only after both horizontal movement bounds leave the camera window");
    }

    @Test
    void stationaryBumperKeepsSharedOutOfRangePath() {
        HexBumperObjectInstance bumper = new HexBumperObjectInstance(
                new ObjectSpawn(0x2034, 0x04F2, 0xD7, 0x00, 0, false, 0x84F2),
                "HexBumper");

        assertFalse(bumper.usesCustomOutOfRangeCheck());
    }

    @Test
    void touchProfileNamesSpecialLatchPolicy() {
        HexBumperObjectInstance bumper = new HexBumperObjectInstance(
                new ObjectSpawn(0x2034, 0x04F2, 0xD7, 0x00, 0, false, 0x84F2),
                "HexBumper");

        TouchResponseProfile profile = bumper.getTouchResponseProfile();

        assertEquals(TouchCategoryDecodeMode.NORMAL, profile.categoryDecodeMode());
        assertTrue(profile.continuousCallbacks());
        assertFalse(profile.requiresRenderFlagForTouch());
        assertFalse(profile.multiRegionSource());
        assertEquals(TouchShieldDeflectCapability.NONE, profile.shieldDeflectCapability());
        assertEquals(0, profile.shieldReactionFlags());
        assertEquals(TouchAttackBouncePolicy.STANDARD_ENEMY_KILL, profile.attackBouncePolicy());
        assertEquals(TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY, profile.actorContextPolicy());
        assertEquals(TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS,
                profile.stopAfterFirstOverlapPolicy());

        assertTrue(bumper.requiresContinuousTouchCallbacks());
        assertFalse(bumper.requiresRenderFlagForTouch());
        assertEquals(0x4A, bumper.getCollisionFlags());
        assertEquals(0, bumper.getCollisionProperty());
        assertTrue(bumper.enablesPostSpecialTouchAirborneSideVelocityPreservation());
        assertEquals(profile, bumper.getTouchResponseProfile(false));
    }
}
