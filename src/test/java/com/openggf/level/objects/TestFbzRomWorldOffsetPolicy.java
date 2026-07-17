package com.openggf.level.objects;

import com.openggf.level.SeamlessLevelTransitionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzRomWorldOffsetPolicy {
    @Test
    void s3kDynamicWindowRepresentsSlot93() {
        assertEquals(94, ObjectSlotLayout.SONIC_3K.lastDynamicSlotExclusive(),
                "FBZ Offset_ObjectsDuringTransition scans through global SST slot 93 inclusive");
    }

    @Test
    void fbzRangeIsHalfOpenAndRequiresLiveCodeAndRenderFlagsBit2() {
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .romWorldObjectOffsetRange(4, 94)
                .build();

        assertFalse(request.shouldApplyRomWorldOffset(3, true, true));
        assertTrue(request.shouldApplyRomWorldOffset(4, true, true));
        assertTrue(request.shouldApplyRomWorldOffset(93, true, true));
        assertFalse(request.shouldApplyRomWorldOffset(94, true, true));
        assertFalse(request.shouldApplyRomWorldOffset(4, false, true));
        assertFalse(request.shouldApplyRomWorldOffset(4, true, false));
    }

    @Test
    void sameLevelRetargetPreservesReloadPoliciesAndTitleCardTailState() {
        SeamlessLevelTransitionRequest original = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_SAME_LEVEL)
                .preserveMusic(false)
                .preserveLevelGamestate(true)
                .preserveEndOfLevelState(true)
                .showInLevelTitleCard(true)
                .resetLevelGamestateAtInLevelTitleCardDisplay(true)
                .inLevelTitleCardResetAdditionalDispatches(2)
                .lockPlayerControlForInLevelTitleCard(true)
                .inLevelTitleCardExitAdditionalDispatches(3)
                .playerOffset(-0x2E00, 4)
                .cameraOffset(-0x2E00, 8)
                .romWorldObjectOffsetRange(4, 94)
                .preserveCheckpointUntilResults(true)
                .omitSecondaryLevelPlc(true)
                .suppressLevelLoadRewindBoundary(true)
                .deferRingInitializationToLevelUpdate(true)
                .build();

        SeamlessLevelTransitionRequest retargeted = original.retargetedForReload(4, 1);

        assertEquals(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL,
                retargeted.type());
        assertEquals(4, retargeted.targetZone());
        assertEquals(1, retargeted.targetAct());
        assertFalse(retargeted.preserveMusic());
        assertTrue(retargeted.preserveLevelGamestate());
        assertTrue(retargeted.preserveEndOfLevelState());
        assertTrue(retargeted.showInLevelTitleCard());
        assertTrue(retargeted.resetLevelGamestateAtInLevelTitleCardDisplay());
        assertEquals(2, retargeted.inLevelTitleCardResetAdditionalDispatches());
        assertTrue(retargeted.lockPlayerControlForInLevelTitleCard());
        assertEquals(3, retargeted.inLevelTitleCardExitAdditionalDispatches());
        assertEquals(-0x2E00, retargeted.playerOffsetX());
        assertEquals(4, retargeted.playerOffsetY());
        assertEquals(-0x2E00, retargeted.cameraOffsetX());
        assertEquals(8, retargeted.cameraOffsetY());
        assertEquals(SeamlessLevelTransitionRequest.ObjectOffsetPolicy.ROM_WORLD_OFFSET_RANGE,
                retargeted.objectOffsetPolicy());
        assertEquals(4, retargeted.objectOffsetStartSlot());
        assertEquals(94, retargeted.objectOffsetEndSlotExclusive());
        assertTrue(retargeted.preserveCheckpointUntilResults());
        assertTrue(retargeted.omitSecondaryLevelPlc());
        assertTrue(retargeted.suppressLevelLoadRewindBoundary());
        assertTrue(retargeted.deferRingInitializationToLevelUpdate());
    }
}
