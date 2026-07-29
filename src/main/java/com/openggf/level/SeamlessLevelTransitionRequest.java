package com.openggf.level;

/**
 * In-place transition request for seamless level events.
 */
@com.openggf.game.ModApi
public final class SeamlessLevelTransitionRequest {
    @com.openggf.game.ModApi
    public enum TransitionType {
        MUTATE_ONLY,
        RELOAD_SAME_LEVEL,
        RELOAD_TARGET_LEVEL
    }

    @com.openggf.game.ModApi
    public enum ObjectSurvivalPolicy {
        PERSISTENT_ONLY,
        ALL_LIVE_SST
    }

    @com.openggf.game.ModApi
    public enum ObjectOffsetPolicy {
        CARRIED_OBJECTS,
        ROM_WORLD_OFFSET_RANGE
    }

    private final TransitionType type;
    private final int targetZone;
    private final int targetAct;
    private final boolean deactivateLevelNow;
    private final boolean preserveMusic;
    private final boolean preserveLevelGamestate;
    private final boolean preserveEndOfLevelActive;
    private final boolean preserveEndOfLevelFlag;
    private final boolean showInLevelTitleCard;
    private final boolean resetLevelGamestateAtInLevelTitleCardDisplay;
    private final int inLevelTitleCardResetAdditionalDispatches;
    private final int inLevelTitleCardResetPhaseOneDispatchOverlap;
    private final boolean lockPlayerControlForInLevelTitleCard;
    private final int inLevelTitleCardExitAdditionalDispatches;
    private final int inLevelTitleCardExitPhaseOneDispatchOverlap;
    private final boolean forceAirOnStaleObjectSupportLoss;
    private final boolean preserveOffsetCameraPosition;
    private final Integer postTransitionMinX;
    private final Integer postTransitionMaxX;
    private final Integer postTransitionMinY;
    private final Integer postTransitionMaxY;
    private final Integer postTransitionMaxYTarget;
    private final Integer postTransitionMinXTarget;
    private final Integer postTransitionMaxXTarget;
    private final Integer postTransitionMinYTarget;
    private final int playerOffsetX;
    private final int playerOffsetY;
    private final int cameraOffsetX;
    private final int cameraOffsetY;
    private final String mutationKey;
    private final int musicOverrideId;
    private final ObjectSurvivalPolicy objectSurvivalPolicy;
    private final ObjectOffsetPolicy objectOffsetPolicy;
    private final int objectOffsetStartSlot;
    private final int objectOffsetEndSlotExclusive;
    private final boolean preserveCheckpointUntilResults;
    private final boolean omitSecondaryLevelPlc;
    private final boolean suppressLevelLoadRewindBoundary;
    private final boolean deferRingInitializationToLevelUpdate;
    private final SeamlessTransitionResourceHandoffId resourceHandoffId;

    private SeamlessLevelTransitionRequest(Builder builder) {
        this.type = builder.type;
        this.targetZone = builder.targetZone;
        this.targetAct = builder.targetAct;
        this.deactivateLevelNow = builder.deactivateLevelNow;
        this.preserveMusic = builder.preserveMusic;
        this.preserveLevelGamestate = builder.preserveLevelGamestate;
        this.preserveEndOfLevelActive = builder.preserveEndOfLevelActive;
        this.preserveEndOfLevelFlag = builder.preserveEndOfLevelFlag;
        this.showInLevelTitleCard = builder.showInLevelTitleCard;
        this.resetLevelGamestateAtInLevelTitleCardDisplay =
                builder.resetLevelGamestateAtInLevelTitleCardDisplay;
        this.inLevelTitleCardResetAdditionalDispatches =
                builder.inLevelTitleCardResetAdditionalDispatches;
        this.inLevelTitleCardResetPhaseOneDispatchOverlap =
                builder.inLevelTitleCardResetPhaseOneDispatchOverlap;
        this.lockPlayerControlForInLevelTitleCard = builder.lockPlayerControlForInLevelTitleCard;
        this.inLevelTitleCardExitAdditionalDispatches =
                builder.inLevelTitleCardExitAdditionalDispatches;
        this.inLevelTitleCardExitPhaseOneDispatchOverlap =
                builder.inLevelTitleCardExitPhaseOneDispatchOverlap;
        this.forceAirOnStaleObjectSupportLoss = builder.forceAirOnStaleObjectSupportLoss;
        this.preserveOffsetCameraPosition = builder.preserveOffsetCameraPosition;
        this.postTransitionMinX = builder.postTransitionMinX;
        this.postTransitionMaxX = builder.postTransitionMaxX;
        this.postTransitionMinY = builder.postTransitionMinY;
        this.postTransitionMaxY = builder.postTransitionMaxY;
        this.postTransitionMaxYTarget = builder.postTransitionMaxYTarget;
        this.postTransitionMinXTarget = builder.postTransitionMinXTarget;
        this.postTransitionMaxXTarget = builder.postTransitionMaxXTarget;
        this.postTransitionMinYTarget = builder.postTransitionMinYTarget;
        this.playerOffsetX = builder.playerOffsetX;
        this.playerOffsetY = builder.playerOffsetY;
        this.cameraOffsetX = builder.cameraOffsetX;
        this.cameraOffsetY = builder.cameraOffsetY;
        this.mutationKey = builder.mutationKey;
        this.musicOverrideId = builder.musicOverrideId;
        this.objectSurvivalPolicy = builder.objectSurvivalPolicy;
        this.objectOffsetPolicy = builder.objectOffsetPolicy;
        this.objectOffsetStartSlot = builder.objectOffsetStartSlot;
        this.objectOffsetEndSlotExclusive = builder.objectOffsetEndSlotExclusive;
        this.preserveCheckpointUntilResults = builder.preserveCheckpointUntilResults;
        this.omitSecondaryLevelPlc = builder.omitSecondaryLevelPlc;
        this.suppressLevelLoadRewindBoundary = builder.suppressLevelLoadRewindBoundary;
        this.deferRingInitializationToLevelUpdate = builder.deferRingInitializationToLevelUpdate;
        this.resourceHandoffId = builder.resourceHandoffId;
    }

    public TransitionType type() {
        return type;
    }

    public int targetZone() {
        return targetZone;
    }

    public int targetAct() {
        return targetAct;
    }

    public boolean deactivateLevelNow() {
        return deactivateLevelNow;
    }

    public boolean preserveMusic() {
        return preserveMusic;
    }

    public boolean preserveLevelGamestate() {
        return preserveLevelGamestate;
    }

    public boolean preserveEndOfLevelState() {
        return preserveEndOfLevelActive && preserveEndOfLevelFlag;
    }

    public boolean preserveEndOfLevelActive() {
        return preserveEndOfLevelActive;
    }

    public boolean preserveEndOfLevelFlag() {
        return preserveEndOfLevelFlag;
    }

    public boolean showInLevelTitleCard() {
        return showInLevelTitleCard;
    }

    public boolean resetLevelGamestateAtInLevelTitleCardDisplay() {
        return resetLevelGamestateAtInLevelTitleCardDisplay;
    }

    public int inLevelTitleCardResetAdditionalDispatches() {
        return inLevelTitleCardResetAdditionalDispatches;
    }

    public int inLevelTitleCardResetPhaseOneDispatchOverlap() {
        return inLevelTitleCardResetPhaseOneDispatchOverlap;
    }

    public boolean lockPlayerControlForInLevelTitleCard() {
        return lockPlayerControlForInLevelTitleCard;
    }

    public int inLevelTitleCardExitAdditionalDispatches() {
        return inLevelTitleCardExitAdditionalDispatches;
    }

    public int inLevelTitleCardExitPhaseOneDispatchOverlap() {
        return inLevelTitleCardExitPhaseOneDispatchOverlap;
    }

    public boolean forceAirOnStaleObjectSupportLoss() {
        return forceAirOnStaleObjectSupportLoss;
    }

    public boolean preserveOffsetCameraPosition() {
        return preserveOffsetCameraPosition;
    }

    public Integer postTransitionMinX() {
        return postTransitionMinX;
    }

    public Integer postTransitionMaxX() {
        return postTransitionMaxX;
    }

    public Integer postTransitionMinY() {
        return postTransitionMinY;
    }

    public Integer postTransitionMaxY() {
        return postTransitionMaxY;
    }

    public Integer postTransitionMaxYTarget() {
        return postTransitionMaxYTarget;
    }

    public Integer postTransitionMinXTarget() { return postTransitionMinXTarget; }
    public Integer postTransitionMaxXTarget() { return postTransitionMaxXTarget; }
    public Integer postTransitionMinYTarget() { return postTransitionMinYTarget; }

    public int playerOffsetX() {
        return playerOffsetX;
    }

    public int playerOffsetY() {
        return playerOffsetY;
    }

    public int cameraOffsetX() {
        return cameraOffsetX;
    }

    public int cameraOffsetY() {
        return cameraOffsetY;
    }

    public String mutationKey() {
        return mutationKey;
    }

    public int musicOverrideId() {
        return musicOverrideId;
    }

    public ObjectSurvivalPolicy objectSurvivalPolicy() { return objectSurvivalPolicy; }

    public ObjectOffsetPolicy objectOffsetPolicy() { return objectOffsetPolicy; }

    public int objectOffsetStartSlot() { return objectOffsetStartSlot; }

    public int objectOffsetEndSlotExclusive() { return objectOffsetEndSlotExclusive; }

    public boolean preserveCheckpointUntilResults() { return preserveCheckpointUntilResults; }

    public boolean omitSecondaryLevelPlc() { return omitSecondaryLevelPlc; }

    public boolean suppressLevelLoadRewindBoundary() { return suppressLevelLoadRewindBoundary; }

    public boolean deferRingInitializationToLevelUpdate() { return deferRingInitializationToLevelUpdate; }

    public SeamlessTransitionResourceHandoffId resourceHandoffId() { return resourceHandoffId; }

    public boolean shouldApplyRomWorldOffset(int slot, boolean objectCodeNonZero,
                                              boolean renderFlagsBit2) {
        return objectOffsetPolicy == ObjectOffsetPolicy.ROM_WORLD_OFFSET_RANGE
                && objectCodeNonZero
                && renderFlagsBit2
                && slot >= objectOffsetStartSlot
                && slot < objectOffsetEndSlotExclusive;
    }

    /** Converts a same-level request to the concrete target used by the reload executor. */
    public SeamlessLevelTransitionRequest retargetedForReload(int zone, int act) {
        Builder adjusted = builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(zone, act)
                .deactivateLevelNow(deactivateLevelNow)
                .preserveMusic(preserveMusic)
                .preserveLevelGamestate(preserveLevelGamestate)
                .preserveEndOfLevelState(preserveEndOfLevelState())
                .showInLevelTitleCard(showInLevelTitleCard)
                .resetLevelGamestateAtInLevelTitleCardDisplay(
                        resetLevelGamestateAtInLevelTitleCardDisplay)
                .inLevelTitleCardResetAdditionalDispatches(
                        inLevelTitleCardResetAdditionalDispatches)
                .lockPlayerControlForInLevelTitleCard(lockPlayerControlForInLevelTitleCard)
                .inLevelTitleCardExitAdditionalDispatches(inLevelTitleCardExitAdditionalDispatches)
                .forceAirOnStaleObjectSupportLoss(forceAirOnStaleObjectSupportLoss)
                .preserveOffsetCameraPosition(preserveOffsetCameraPosition)
                .postTransitionMinXIfPresent(postTransitionMinX)
                .postTransitionMaxXIfPresent(postTransitionMaxX)
                .postTransitionMinYIfPresent(postTransitionMinY)
                .postTransitionMaxYIfPresent(postTransitionMaxY)
                .postTransitionMaxYTargetIfPresent(postTransitionMaxYTarget)
                .postTransitionMinXTargetIfPresent(postTransitionMinXTarget)
                .postTransitionMaxXTargetIfPresent(postTransitionMaxXTarget)
                .postTransitionMinYTargetIfPresent(postTransitionMinYTarget)
                .playerOffset(playerOffsetX, playerOffsetY)
                .cameraOffset(cameraOffsetX, cameraOffsetY)
                .mutationKey(mutationKey)
                .musicOverrideId(musicOverrideId)
                .objectSurvivalPolicy(objectSurvivalPolicy)
                .preserveCheckpointUntilResults(preserveCheckpointUntilResults)
                .omitSecondaryLevelPlc(omitSecondaryLevelPlc)
                .suppressLevelLoadRewindBoundary(suppressLevelLoadRewindBoundary)
                .deferRingInitializationToLevelUpdate(deferRingInitializationToLevelUpdate)
                .resourceHandoff(resourceHandoffId);
        if (objectOffsetPolicy == ObjectOffsetPolicy.ROM_WORLD_OFFSET_RANGE) {
            adjusted.romWorldObjectOffsetRange(objectOffsetStartSlot, objectOffsetEndSlotExclusive);
        }
        return adjusted.build();
    }

    public static Builder builder(TransitionType type) {
        return new Builder(type);
    }

    @com.openggf.game.ModApi
    public static final class Builder {
        private final TransitionType type;
        private int targetZone = -1;
        private int targetAct = -1;
        private boolean deactivateLevelNow;
        private boolean preserveMusic = true;
        private boolean preserveLevelGamestate;
        private boolean preserveEndOfLevelActive;
        private boolean preserveEndOfLevelFlag;
        private boolean showInLevelTitleCard;
        private boolean resetLevelGamestateAtInLevelTitleCardDisplay;
        private int inLevelTitleCardResetAdditionalDispatches;
        private int inLevelTitleCardResetPhaseOneDispatchOverlap;
        private boolean lockPlayerControlForInLevelTitleCard;
        private int inLevelTitleCardExitAdditionalDispatches;
        private int inLevelTitleCardExitPhaseOneDispatchOverlap;
        private boolean forceAirOnStaleObjectSupportLoss;
        private boolean preserveOffsetCameraPosition;
        private Integer postTransitionMinX;
        private Integer postTransitionMaxX;
        private Integer postTransitionMinY;
        private Integer postTransitionMaxY;
        private Integer postTransitionMaxYTarget;
        private Integer postTransitionMinXTarget;
        private Integer postTransitionMaxXTarget;
        private Integer postTransitionMinYTarget;
        private int playerOffsetX;
        private int playerOffsetY;
        private int cameraOffsetX;
        private int cameraOffsetY;
        private String mutationKey;
        private int musicOverrideId = -1;
        private ObjectSurvivalPolicy objectSurvivalPolicy = ObjectSurvivalPolicy.PERSISTENT_ONLY;
        private ObjectOffsetPolicy objectOffsetPolicy = ObjectOffsetPolicy.CARRIED_OBJECTS;
        private int objectOffsetStartSlot;
        private int objectOffsetEndSlotExclusive;
        private boolean preserveCheckpointUntilResults;
        private boolean omitSecondaryLevelPlc;
        private boolean suppressLevelLoadRewindBoundary;
        private boolean deferRingInitializationToLevelUpdate;
        private SeamlessTransitionResourceHandoffId resourceHandoffId;

        private Builder(TransitionType type) {
            this.type = type;
        }

        public Builder targetZoneAct(int zone, int act) {
            this.targetZone = zone;
            this.targetAct = act;
            return this;
        }

        public Builder deactivateLevelNow(boolean deactivateLevelNow) {
            this.deactivateLevelNow = deactivateLevelNow;
            return this;
        }

        public Builder preserveMusic(boolean preserveMusic) {
            this.preserveMusic = preserveMusic;
            return this;
        }

        public Builder preserveLevelGamestate(boolean preserveLevelGamestate) {
            this.preserveLevelGamestate = preserveLevelGamestate;
            return this;
        }

        /**
         * Keeps the ROM end-of-level globals alive across an in-place
         * {@code Load_Level}. Use this when the results/end-sign objects span
         * the resource reload and still own those globals afterward.
         */
        public Builder preserveEndOfLevelState(boolean preserveEndOfLevelState) {
            this.preserveEndOfLevelActive = preserveEndOfLevelState;
            this.preserveEndOfLevelFlag = preserveEndOfLevelState;
            return this;
        }

        /** Keeps Level_end_flag while allowing End_of_level_flag to reset. */
        public Builder preserveEndOfLevelActive(boolean preserveEndOfLevelActive) {
            this.preserveEndOfLevelActive = preserveEndOfLevelActive;
            return this;
        }

        public Builder showInLevelTitleCard(boolean showInLevelTitleCard) {
            this.showInLevelTitleCard = showInLevelTitleCard;
            return this;
        }

        public Builder resetLevelGamestateAtInLevelTitleCardDisplay(boolean reset) {
            this.resetLevelGamestateAtInLevelTitleCardDisplay = reset;
            return this;
        }

        public Builder inLevelTitleCardResetAdditionalDispatches(int dispatches) {
            this.inLevelTitleCardResetAdditionalDispatches = Math.max(0, dispatches);
            return this;
        }

        public Builder inLevelTitleCardResetPhaseOneDispatchOverlap(int dispatches) {
            this.inLevelTitleCardResetPhaseOneDispatchOverlap = Math.max(0, dispatches);
            return this;
        }

        public Builder lockPlayerControlForInLevelTitleCard(boolean lock) {
            this.lockPlayerControlForInLevelTitleCard = lock;
            return this;
        }

        public Builder inLevelTitleCardExitAdditionalDispatches(int dispatches) {
            this.inLevelTitleCardExitAdditionalDispatches = Math.max(0, dispatches);
            return this;
        }

        public Builder inLevelTitleCardExitPhaseOneDispatchOverlap(int dispatches) {
            this.inLevelTitleCardExitPhaseOneDispatchOverlap = Math.max(0, dispatches);
            return this;
        }

        public Builder forceAirOnStaleObjectSupportLoss(boolean forceAirOnStaleObjectSupportLoss) {
            this.forceAirOnStaleObjectSupportLoss = forceAirOnStaleObjectSupportLoss;
            return this;
        }

        public Builder preserveOffsetCameraPosition(boolean preserveOffsetCameraPosition) {
            this.preserveOffsetCameraPosition = preserveOffsetCameraPosition;
            return this;
        }

        public Builder postTransitionMinX(int minX) {
            this.postTransitionMinX = minX;
            return this;
        }

        public Builder postTransitionMinXIfPresent(Integer minX) {
            this.postTransitionMinX = minX;
            return this;
        }

        public Builder postTransitionMaxX(int maxX) {
            this.postTransitionMaxX = maxX;
            return this;
        }

        public Builder postTransitionMaxXIfPresent(Integer maxX) {
            this.postTransitionMaxX = maxX;
            return this;
        }

        public Builder postTransitionMinY(int minY) {
            this.postTransitionMinY = minY;
            return this;
        }

        public Builder postTransitionMinYIfPresent(Integer minY) {
            this.postTransitionMinY = minY;
            return this;
        }

        public Builder postTransitionMaxY(int maxY) {
            this.postTransitionMaxY = maxY;
            return this;
        }

        public Builder postTransitionMaxYIfPresent(Integer maxY) {
            this.postTransitionMaxY = maxY;
            return this;
        }

        public Builder postTransitionMaxYTarget(int maxYTarget) {
            this.postTransitionMaxYTarget = maxYTarget;
            return this;
        }

        public Builder postTransitionMaxYTargetIfPresent(Integer maxYTarget) {
            this.postTransitionMaxYTarget = maxYTarget;
            return this;
        }

        public Builder postTransitionMinXTarget(int value) {
            this.postTransitionMinXTarget = value;
            return this;
        }

        public Builder postTransitionMinXTargetIfPresent(Integer value) {
            this.postTransitionMinXTarget = value;
            return this;
        }

        public Builder postTransitionMaxXTarget(int value) {
            this.postTransitionMaxXTarget = value;
            return this;
        }

        public Builder postTransitionMaxXTargetIfPresent(Integer value) {
            this.postTransitionMaxXTarget = value;
            return this;
        }

        public Builder postTransitionMinYTarget(int value) {
            this.postTransitionMinYTarget = value;
            return this;
        }

        public Builder postTransitionMinYTargetIfPresent(Integer value) {
            this.postTransitionMinYTarget = value;
            return this;
        }

        public Builder playerOffset(int x, int y) {
            this.playerOffsetX = x;
            this.playerOffsetY = y;
            return this;
        }

        public Builder cameraOffset(int x, int y) {
            this.cameraOffsetX = x;
            this.cameraOffsetY = y;
            return this;
        }

        public Builder mutationKey(String mutationKey) {
            this.mutationKey = mutationKey;
            return this;
        }

        public Builder musicOverrideId(int musicOverrideId) {
            this.musicOverrideId = musicOverrideId;
            return this;
        }

        public Builder objectSurvivalPolicy(ObjectSurvivalPolicy policy) {
            this.objectSurvivalPolicy = policy;
            return this;
        }

        public Builder romWorldObjectOffsetRange(int startSlot, int endSlotExclusive) {
            if (startSlot < 0 || endSlotExclusive <= startSlot) {
                throw new IllegalArgumentException("Invalid ROM object-offset range");
            }
            this.objectOffsetPolicy = ObjectOffsetPolicy.ROM_WORLD_OFFSET_RANGE;
            this.objectOffsetStartSlot = startSlot;
            this.objectOffsetEndSlotExclusive = endSlotExclusive;
            return this;
        }

        public Builder preserveCheckpointUntilResults(boolean preserve) {
            this.preserveCheckpointUntilResults = preserve;
            return this;
        }

        public Builder omitSecondaryLevelPlc(boolean omit) {
            this.omitSecondaryLevelPlc = omit;
            return this;
        }

        public Builder suppressLevelLoadRewindBoundary(boolean suppress) {
            this.suppressLevelLoadRewindBoundary = suppress;
            return this;
        }

        public Builder deferRingInitializationToLevelUpdate(boolean defer) {
            this.deferRingInitializationToLevelUpdate = defer;
            return this;
        }

        public Builder resourceHandoff(SeamlessTransitionResourceHandoffId resourceHandoffId) {
            this.resourceHandoffId = resourceHandoffId;
            return this;
        }

        public SeamlessLevelTransitionRequest build() {
            return new SeamlessLevelTransitionRequest(this);
        }
    }
}
