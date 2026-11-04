package com.openggf.level;

import com.openggf.game.RuntimeArtAdmissionPolicy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestSeamlessLevelTransitionRequest {
    @Test
    void retargetingPreservesEveryNonIdentityField() throws Exception {
        var source = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_SAME_LEVEL)
                .targetZoneAct(90, 91)
                .deactivateLevelNow(true)
                .preserveMusic(false)
                .preserveLevelGamestate(true)
                .preserveEndOfLevelState(true)
                .showInLevelTitleCard(true)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.RESOURCE_HANDOFF_OWNER)
                .resetLevelGamestateAtInLevelTitleCardDisplay(true)
                .inLevelTitleCardResetAdditionalDispatches(12)
                .inLevelTitleCardResetPhaseOneDispatchOverlap(6)
                .lockPlayerControlForInLevelTitleCard(true)
                .inLevelTitleCardExitAdditionalDispatches(10)
                .inLevelTitleCardExitPhaseOneDispatchOverlap(5)
                .inLevelTitleCardPreloadedActCameraReleaseDispatches(8)
                .preserveOffsetCameraPosition(true)
                .postTransitionMinX(101).postTransitionMaxX(102)
                .postTransitionMinY(103).postTransitionMaxY(104)
                .postTransitionMaxYTarget(105)
                .postTransitionMinXTarget(106).postTransitionMaxXTarget(107)
                .postTransitionMinYTarget(108)
                .playerOffset(109, 110).cameraOffset(111, 112)
                .mutationKey("retained-mutation").musicOverrideId(113)
                .objectSurvivalPolicy(SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.ALL_LIVE_SST)
                .romWorldObjectOffsetRange(4, 94)
                .preserveCheckpointUntilResults(true)
                .omitSecondaryLevelPlc(true)
                .suppressLevelLoadRewindBoundary(true)
                .deferRingInitializationToLevelUpdate(true)
                .resourceHandoff(new SeamlessTransitionResourceHandoffId(73))
                .build();
        var defaults = SeamlessLevelTransitionRequest.builder(source.type()).build();
        var retargeted = source.retargetedForReload(7, 1);

        assertEquals(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL, retargeted.type());
        assertEquals(7, retargeted.targetZone());
        assertEquals(1, retargeted.targetAct());
        assertEquals(90, source.targetZone(), "retargeting must leave the queued source immutable");
        assertEquals(91, source.targetAct());
        // Enumerate the actual value fields so a future option cannot silently
        // escape this contract. A new field must first get a non-default witness.
        for (var field : SeamlessLevelTransitionRequest.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()
                    || Set.of("type", "targetZone", "targetAct").contains(field.getName())) continue;
            field.setAccessible(true);
            assertNotEquals(field.get(defaults), field.get(source),
                    "add a non-default witness for " + field.getName());
            assertEquals(field.get(source), field.get(retargeted),
                    "retargeting lost " + field.getName());
        }
    }

    @Test
    void retargetingKeepsEndOfLevelFlagsIndependent() {
        for (boolean flag : new boolean[] {false, true}) {
            for (boolean active : new boolean[] {false, true}) {
                var source = SeamlessLevelTransitionRequest.builder(
                                SeamlessLevelTransitionRequest.TransitionType.RELOAD_SAME_LEVEL)
                        .preserveEndOfLevelState(flag)
                        .preserveEndOfLevelActive(active)
                        .build();
                var target = source.retargetedForReload(1, 1);
                assertEquals(flag, target.preserveEndOfLevelFlag());
                assertEquals(active, target.preserveEndOfLevelActive());
            }
        }
    }
}
