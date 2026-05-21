package com.openggf.game.sonic1.objects;

import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestSonic1ObjectTouchResponseProfiles {

    @Test
    void chainedStomperDeclaresMultiRegionHurtProfile() throws NoSuchMethodException {
        Sonic1ChainedStomperObjectInstance stomper = new Sonic1ChainedStomperObjectInstance(
                new ObjectSpawn(0x1200, 0x0400, 0x31, 0x01, 0, false, 0));

        assertNotNull(stomper.getMultiTouchRegions());
        assertEquals(multiRegionHurtProfile(), stomper.getTouchResponseProfile());
        assertEquals(multiRegionHurtProfile(), stomper.getTouchResponseProfile(true));
        assertEquals(singleRegionHurtProfile(), stomper.getTouchResponseProfile(false));
        Sonic1ChainedStomperObjectInstance.class.getDeclaredMethod("getTouchResponseProfile");
        Sonic1ChainedStomperObjectInstance.class.getDeclaredMethod("getTouchResponseProfile", boolean.class);
    }

    @Test
    void spikedBallChainDeclaresMultiRegionHurtProfile() throws NoSuchMethodException {
        Sonic1SpikedBallChainObjectInstance chain = new Sonic1SpikedBallChainObjectInstance(
                new ObjectSpawn(0x1200, 0x0400, 0x57, 0x03, 0, false, 0),
                Sonic1Constants.ZONE_SYZ);

        assertNotNull(chain.getMultiTouchRegions());
        assertEquals(multiRegionHurtProfile(), chain.getTouchResponseProfile());
        assertEquals(multiRegionHurtProfile(), chain.getTouchResponseProfile(true));
        assertEquals(singleRegionHurtProfile(), chain.getTouchResponseProfile(false));
        Sonic1SpikedBallChainObjectInstance.class.getDeclaredMethod("getTouchResponseProfile");
        Sonic1SpikedBallChainObjectInstance.class.getDeclaredMethod("getTouchResponseProfile", boolean.class);
    }

    @Test
    void spikedPoleHelixDeclaresMultiRegionHurtProfile() throws NoSuchMethodException {
        Sonic1SpikedPoleHelixObjectInstance helix = new Sonic1SpikedPoleHelixObjectInstance(
                new ObjectSpawn(0x1200, 0x0400, 0x17, 0x10, 0, false, 0));

        assertNotNull(helix.getMultiTouchRegions());
        assertEquals(multiRegionHurtProfile(), helix.getTouchResponseProfile());
        assertEquals(multiRegionHurtProfile(), helix.getTouchResponseProfile(true));
        assertEquals(singleRegionHurtProfile(), helix.getTouchResponseProfile(false));
        Sonic1SpikedPoleHelixObjectInstance.class.getDeclaredMethod("getTouchResponseProfile");
        Sonic1SpikedPoleHelixObjectInstance.class.getDeclaredMethod("getTouchResponseProfile", boolean.class);
    }

    private static TouchResponseProfile multiRegionHurtProfile() {
        return touchProfile(true, TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_MAIN_ONLY);
    }

    private static TouchResponseProfile singleRegionHurtProfile() {
        return touchProfile(false, TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    }

    private static TouchResponseProfile touchProfile(boolean multiRegionSource,
            TouchOverlapStopPolicy stopPolicy) {
        return new TouchResponseProfile(
                TouchCategoryDecodeMode.NORMAL,
                false,
                true,
                multiRegionSource,
                TouchShieldDeflectCapability.NONE,
                0,
                TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                stopPolicy);
    }
}
