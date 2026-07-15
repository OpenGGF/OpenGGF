package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CollapsingBridgeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCollapsingBridgeParity {
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private Sonic rider;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_MGZ, 0);
    }

    @AfterAll
    static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        rider = (Sonic) fixture.sprite();
    }

    @Test
    void mgzCollapseWave_isNotSolidForUntrackedPlayers() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x00);
        Sonic bystander = new Sonic("sonic", (short) 0, (short) 0);
        rider.setCentreX((short) 8);
        rider.setOnObject(true);
        bystander.setCentreX((short) 8);

        invokePerformCollapse(bridge, rider);

        assertFalse(bridge.isSolidFor(bystander),
                "ROM collapse-wave logic stops accepting new SolidObjectTop contacts once the bridge shatters");
    }

    @Test
    void mgzCollapseWave_staysSolidForTheRiderWhoTriggeredIt() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x00);
        Sonic rider = new Sonic("sonic", (short) 0, (short) 0);
        rider.setCentreX((short) 8);
        rider.setOnObject(true);

        invokePerformCollapse(bridge, rider);

        assertTrue(bridge.isSolidFor(rider),
                "The rider that triggered the collapse should remain supported until the release wave reaches them");
    }

    @Test
    void collapseWaveRelease_publishesNativePreviousAnimationSentinel() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x00);
        rider.getAnimationManager().publishPreviousAnimationId(2);

        invokeReleaseCollapseRider(bridge, rider);

        assertEquals(1, rider.getAnimationManager().captureRewindState().lastAnimationId(),
                "Check_CollapsePlayerRelease writes prev_anim=1");
    }

    @Test
    void freshTopContact_rejectsExactSurfaceBoundary() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x00);

        assertTrue(bridge.rejectsZeroDistanceTopSolidLanding(),
                "SolidObjectTop's unsigned -$10 comparison excludes d0=0");
    }

    @Test
    void collapsedParent_offscreenDeleteAllowsPlacementRespawn() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x00);
        setIntField(bridge, "state", 3);
        setIntField(bridge, "y", 0x7000);

        bridge.update(0, rider);

        assertTrue(bridge.isDestroyed());
        assertTrue(bridge.isDestroyedRespawnable(),
                "ObjPlatformCollapse_SmashObject clears the placement respawn latch");
    }

    @Test
    void standingContact_defersFirstCountdownTickToFollowingObjectPass() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x02);
        bridge.onSolidContact(rider, new SolidContact(true, false, false, true, false), 0);

        bridge.update(0, rider);
        assertEquals(0, getIntField(bridge, "state"));
        assertEquals(40, getIntField(bridge, "collapseTimer"));

        bridge.update(1, rider);
        assertEquals(1, getIntField(bridge, "state"));
        assertEquals(40, getIntField(bridge, "collapseTimer"));
    }

    @Test
    void mgzStomp_clearsOnObjectButPreservesGroundedStatus() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x20);
        rider.setAir(false);
        rider.setOnObject(true);
        rider.setWallCling(true);

        Method stomp = CollapsingBridgeObjectInstance.class.getDeclaredMethod(
                "performMgzStomp", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        stomp.setAccessible(true);
        stomp.invoke(bridge, rider);

        assertFalse(rider.getAir(),
                "loc_209FC clears OnObj but does not set Status_InAir");
        assertFalse(rider.isOnObject());
        assertEquals(3, getIntField(bridge, "state"));
    }

    private static CollapsingBridgeObjectInstance newMgzBridge(int subtype) throws Exception {
        CollapsingBridgeObjectInstance bridge = new CollapsingBridgeObjectInstance(
                new ObjectSpawn(0, 0, 0x0F, subtype, 0x00, false, 0));
        Method initMgz = CollapsingBridgeObjectInstance.class.getDeclaredMethod("initMGZ", int.class);
        initMgz.setAccessible(true);
        initMgz.invoke(bridge, subtype);
        return bridge;
    }

    private static void invokePerformCollapse(CollapsingBridgeObjectInstance bridge, Sonic rider) throws Exception {
        Method performCollapse = CollapsingBridgeObjectInstance.class.getDeclaredMethod(
                "performCollapse",
                com.openggf.sprites.playable.AbstractPlayableSprite.class);
        performCollapse.setAccessible(true);
        performCollapse.invoke(bridge, rider);
    }

    private static void invokeReleaseCollapseRider(CollapsingBridgeObjectInstance bridge, Sonic rider)
            throws Exception {
        Method release = CollapsingBridgeObjectInstance.class.getDeclaredMethod(
                "releaseCollapseRider",
                com.openggf.sprites.playable.AbstractPlayableSprite.class);
        release.setAccessible(true);
        release.invoke(bridge, rider);
    }

    private static void setIntField(CollapsingBridgeObjectInstance bridge, String name, int value)
            throws Exception {
        Field field = CollapsingBridgeObjectInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(bridge, value);
    }

    private static int getIntField(CollapsingBridgeObjectInstance bridge, String name) throws Exception {
        Field field = CollapsingBridgeObjectInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(bridge);
    }
}
