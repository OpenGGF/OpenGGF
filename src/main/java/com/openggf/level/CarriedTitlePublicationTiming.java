package com.openggf.level;

/** Immutable title timing handed to objects carried across an in-place reload. */
public record CarriedTitlePublicationTiming(
        boolean explicitTiming,
        boolean resetLevelGamestateAtDisplay,
        int resetAdditionalDispatches,
        int resetPhaseOneDispatchOverlap,
        boolean lockPlayerControl,
        int exitAdditionalDispatches,
        int exitPhaseOneDispatchOverlap,
        int preloadedActCameraReleaseDispatches) {

    public static final CarriedTitlePublicationTiming NONE =
            new CarriedTitlePublicationTiming(false, false, 0, 0, false, 0, 0, -1);

    public CarriedTitlePublicationTiming {
        resetAdditionalDispatches = Math.max(0, resetAdditionalDispatches);
        resetPhaseOneDispatchOverlap = Math.max(0, resetPhaseOneDispatchOverlap);
        exitAdditionalDispatches = Math.max(0, exitAdditionalDispatches);
        exitPhaseOneDispatchOverlap = Math.max(0, exitPhaseOneDispatchOverlap);
        preloadedActCameraReleaseDispatches = preloadedActCameraReleaseDispatches < 0
                ? -1 : preloadedActCameraReleaseDispatches;
    }

    /**
     * Retains request timing only when a carried object, rather than an
     * executor-created overlay, owns title publication.
     */
    public static CarriedTitlePublicationTiming from(SeamlessLevelTransitionRequest request) {
        if (request == null) {
            return NONE;
        }
        int preloadedActCameraReleaseDispatches =
                request.inLevelTitleCardPreloadedActCameraReleaseDispatches();
        boolean explicitTiming = request.resetLevelGamestateAtInLevelTitleCardDisplay()
                || request.inLevelTitleCardResetAdditionalDispatches() != 0
                || request.inLevelTitleCardResetPhaseOneDispatchOverlap() != 0
                || request.lockPlayerControlForInLevelTitleCard()
                || request.inLevelTitleCardExitAdditionalDispatches() != 0
                || request.inLevelTitleCardExitPhaseOneDispatchOverlap() != 0;
        if (!explicitTiming && preloadedActCameraReleaseDispatches < 0) {
            return NONE;
        }
        return new CarriedTitlePublicationTiming(
                explicitTiming,
                request.resetLevelGamestateAtInLevelTitleCardDisplay(),
                request.inLevelTitleCardResetAdditionalDispatches(),
                request.inLevelTitleCardResetPhaseOneDispatchOverlap(),
                request.lockPlayerControlForInLevelTitleCard(),
                request.inLevelTitleCardExitAdditionalDispatches(),
                request.inLevelTitleCardExitPhaseOneDispatchOverlap(),
                preloadedActCameraReleaseDispatches);
    }
}
