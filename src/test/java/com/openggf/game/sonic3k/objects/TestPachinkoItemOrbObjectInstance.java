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

    /**
     * ROM sonic3k.asm:96777-96786 (loc_4A218) arms the orb on touch but does not convert the
     * same pass, and sonic3k.asm:96789-96791 (loc_4A238 -&gt; loc_4A274) re-checks
     * collision_property next pass and stays armed for as long as the touch persists — the orb
     * only converts once a pass resolves with the touch signal clear (contact released).
     */
    @Test
    void touchArmsOrb_conversionWaitsForReleaseBeforeUsingReleaseFrameCounter() {
        PachinkoItemOrbObjectInstance orb =
                new PachinkoItemOrbObjectInstance(new ObjectSpawn(0x140, 0x183, 0xED, 0, 0, false, 0));
        orb.setServices(new TestObjectServices());

        orb.onTouchResponse(null, null, 0);
        orb.update(1, null);

        // Armed, but not yet converted: still-touching still resolves at update(1).
        assertEquals(0xED, orb.getSpawn().objectId());
        assertEquals(0, orb.getSpawn().subtype());

        // Contact persists into the next pass (loc_4A274): stays armed, does not convert.
        orb.onTouchResponse(null, null, 1);
        orb.update(2, null);

        assertEquals(0xED, orb.getSpawn().objectId());
        assertEquals(0, orb.getSpawn().subtype());

        // Touch signal resolves clear (player released contact): converts now, using this
        // release pass's frame counter (3; TestObjectServices has no ObjectManager wired, so
        // resolveRomFrameCounter falls back to the raw update() parameter). yNibble=3
        // (0x183 & 0xF), 3&3=3 -> REWARD_TABLE[(3<<2)+3]=REWARD_TABLE[15]=5.
        orb.update(3, null);

        assertEquals(0xEB, orb.getSpawn().objectId());
        assertEquals(5, orb.getSpawn().subtype());
    }

    /**
     * A single touch that is never repeated still requires one additional touch-clear pass
     * before converting (ROM never converts on the same pass collision_property was set).
     */
    @Test
    void singleTouchArmsButDoesNotConvertOnTheImmediatelyFollowingUpdate() {
        PachinkoItemOrbObjectInstance orb =
                new PachinkoItemOrbObjectInstance(new ObjectSpawn(0x140, 0x183, 0xED, 0, 0, false, 0));
        orb.setServices(new TestObjectServices());

        orb.onTouchResponse(null, null, 0);
        orb.update(1, null);

        assertEquals(0xED, orb.getSpawn().objectId());
        assertEquals(0, orb.getSpawn().subtype());

        // yNibble=3 (0x183 & 0xF), 2&3=2 -> REWARD_TABLE[(3<<2)+2]=REWARD_TABLE[14]=6.
        orb.update(2, null);

        assertEquals(0xEB, orb.getSpawn().objectId());
        assertEquals(6, orb.getSpawn().subtype());
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


