package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPachinkoItemOrbObjectInstance {

    private static final int[] EXPECTED_REWARD_TABLE = {
            1, 3, 1, 3, 8, 3, 8, 5, 1, 3, 6, 4, 1, 7, 6, 5, 8, 6, 4, 3,
            4, 3, 4, 5, 8, 4, 5, 3, 7, 3, 8, 3, 6, 5, 6, 7, 4, 3, 7, 5,
            6, 4, 6, 4, 7, 3, 3, 5, 4, 3, 4, 6, 3, 4, 3, 7, 4, 3, 4, 3,
            4, 3, 4, 3
    };

    @Test
    void rewardTableMatchesDisassemblyByte1E4484() {
        for (int yNibble = 0; yNibble < 16; yNibble++) {
            for (int framePhase = 0; framePhase < 4; framePhase++) {
                int index = (yNibble << 2) + framePhase;
                assertEquals(EXPECTED_REWARD_TABLE[index],
                        PachinkoItemOrbObjectInstance.resolveRewardSubtype(0x180 | yNibble, framePhase),
                        "Mismatch at yNibble=" + yNibble + " framePhase=" + framePhase);
            }
        }
    }

    @Test
    void firstTouchOnlyArmsOrb_andNextUpdateUsesFollowingFrameCounter() {
        PachinkoItemOrbObjectInstance orb =
                new PachinkoItemOrbObjectInstance(new ObjectSpawn(0x140, 0x183, 0xED, 0, 0, false, 0));
        orb.setServices(new TestObjectServices());

        orb.onTouchResponse(null, null, 0);

        assertEquals(0xED, orb.getSpawn().objectId());
        assertEquals(0, orb.getSpawn().subtype());

        orb.update(1, null);

        assertEquals(0xEB, orb.getSpawn().objectId());
        assertEquals(7, orb.getSpawn().subtype());
    }

    @Test
    void touchResponseProfileKeepsOrbEdgeTriggeredWithSingleRegionDefaults() {
        PachinkoItemOrbObjectInstance orb =
                new PachinkoItemOrbObjectInstance(new ObjectSpawn(0x140, 0x183, 0xED, 0, 0, false, 0));

        TouchResponseProfile profile = orb.getTouchResponseProfile();

        assertFalse(orb.requiresContinuousTouchCallbacks());
        assertEquals(TouchCategoryDecodeMode.NORMAL, profile.categoryDecodeMode());
        assertFalse(profile.continuousCallbacks());
        assertTrue(profile.requiresRenderFlagForTouch());
        assertFalse(profile.multiRegionSource());
        assertEquals(TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS,
                profile.stopAfterFirstOverlapPolicy());
    }

    @Test
    void orbDeclaresProfileInsteadOfLegacyContinuousTouchHook() throws NoSuchMethodException {
        assertThrows(NoSuchMethodException.class,
                () -> PachinkoItemOrbObjectInstance.class
                        .getDeclaredMethod("requiresContinuousTouchCallbacks"));
        assertEquals(TouchResponseProfile.class, PachinkoItemOrbObjectInstance.class
                .getDeclaredMethod("getTouchResponseProfile")
                .getReturnType());
    }
}


