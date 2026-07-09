package com.openggf.game.sonic2.specialstage;

import com.openggf.level.Palette;

import java.util.List;

final class Sonic2SpecialStageSnapshot {
    final boolean initialized;
    final int currentStage;
    final Sonic2SpecialStageManager.ResultState resultState;
    final boolean emeraldCollected;

    Sonic2SpecialStageSnapshot(
            boolean initialized,
            int currentStage,
            Sonic2SpecialStageManager.ResultState resultState,
            boolean emeraldCollected) {
        this.initialized = initialized;
        this.currentStage = currentStage;
        this.resultState = resultState;
        this.emeraldCollected = emeraldCollected;
    }

    record TrackAnimatorSnapshot(
            byte[] stageLayout,
            int layoutLength,
            int currentSegmentIndex,
            int currentFrameInSegment,
            int frameDelayCounter,
            int currentSegmentType,
            boolean currentSegmentFlipped,
            int speedFactor,
            boolean stageComplete,
            boolean orientationFlipped,
            int lastOrientationFrame) {
        TrackAnimatorSnapshot {
            stageLayout = Sonic2SpecialStageSnapshot.cloneByteArray(stageLayout);
        }
    }

    record PlayerSlotSnapshot(
            Sonic2SpecialStagePlayer.PlayerType type,
            boolean mainCharacter) {
    }

    record PlayerTopologySnapshot(
            List<PlayerSlotSnapshot> slots,
            int sonicSlotIndex,
            int tailsSlotIndex,
            boolean playersLinked) {
        PlayerTopologySnapshot {
            slots = List.copyOf(slots);
        }

        static PlayerTopologySnapshot capture(
                List<Sonic2SpecialStagePlayer> players,
                Sonic2SpecialStagePlayer sonicPlayer,
                Sonic2SpecialStagePlayer tailsPlayer) {
            java.util.ArrayList<PlayerSlotSnapshot> slots = new java.util.ArrayList<>();
            int sonicIndex = -1;
            int tailsIndex = -1;
            for (int i = 0; i < players.size(); i++) {
                Sonic2SpecialStagePlayer player = players.get(i);
                slots.add(new PlayerSlotSnapshot(player.getPlayerType(), player.isMainCharacter()));
                if (player == sonicPlayer) {
                    sonicIndex = i;
                }
                if (player == tailsPlayer) {
                    tailsIndex = i;
                }
            }
            boolean linked = sonicPlayer != null && tailsPlayer != null
                    && sonicPlayer.getOtherPlayerForRewind() == tailsPlayer
                    && tailsPlayer.getOtherPlayerForRewind() == sonicPlayer;
            return new PlayerTopologySnapshot(slots, sonicIndex, tailsIndex, linked);
        }
    }

    record PlayerSnapshot(
            Sonic2SpecialStagePlayer.PlayerType playerType,
            boolean mainCharacter,
            Sonic2SpecialStagePlayer.RoutineState routine,
            int routineSecondary,
            int ssXPos,
            int ssXSub,
            int ssYPos,
            int ssYSub,
            int ssZPos,
            int xPos,
            int yPos,
            int xVel,
            int yVel,
            int inertia,
            int angle,
            int ssSlideTimer,
            int ssHurtTimer,
            int ssDplcTimer,
            int ssInitFlipTimer,
            int ssFlipTimer,
            int ssLastAngleIndex,
            int anim,
            int prevAnim,
            int animFrame,
            int animFrameDuration,
            int mappingFrame,
            int yRadius,
            int xRadius,
            int priority,
            boolean statusXFlip,
            boolean statusYFlip,
            boolean statusJumping,
            boolean statusSlowing,
            boolean renderXFlip,
            boolean renderYFlip,
            int collisionProperty,
            int globalAnimFrameTimer,
            int[] ctrlRecordBuf,
            int ctrlRecordIndex,
            boolean swapPositionsFlag,
            int invulnerabilityCountdown) {
        PlayerSnapshot {
            ctrlRecordBuf = Sonic2SpecialStageSnapshot.cloneIntArray(ctrlRecordBuf);
        }
    }

    record IntroMessageLetterSnapshot(
            int x,
            int y,
            int tileOffset,
            double flyoutAngle,
            int flyoutSpeed,
            boolean visible) {
    }

    record IntroBannerLetterSnapshot(
            int x,
            int y,
            int frame,
            double flyoutAngle,
            int flyoutSpeed,
            boolean visible) {
    }

    record IntroSnapshot(
            Sonic2SpecialStageIntro.Phase currentPhase,
            int phaseTimer,
            int frameCounter,
            int bannerX,
            int bannerY,
            boolean bannerVisible,
            int messageX,
            int messageY,
            boolean messageVisible,
            int ringRequirement,
            boolean lettersFlying,
            int letterFlyoutProgress,
            boolean messageFlyoutInitialized,
            boolean bannerFlyoutInitialized,
            List<IntroMessageLetterSnapshot> messageLetters,
            List<IntroBannerLetterSnapshot> bannerLetters) {
        IntroSnapshot {
            messageLetters = List.copyOf(messageLetters);
            bannerLetters = List.copyOf(bannerLetters);
        }
    }

    record CheckpointMessageLetterSnapshot(
            int x,
            int y,
            int tileOffset,
            int flyoutAngle,
            int flyoutSpeed,
            boolean visible) {
    }

    record CheckpointRainbowRingSnapshot(
            int baseIndex,
            int frameIndex,
            int positionOffset,
            int mappingFrame,
            int x,
            int y,
            boolean active) {
    }

    record CheckpointSnapshot(
            Sonic2SpecialStageCheckpoint.MessagePhase phase,
            int phaseTimer,
            Sonic2SpecialStageCheckpoint.Result lastResult,
            int currentCheckpoint,
            int ringRequirement,
            int ringsCollected,
            List<CheckpointMessageLetterSnapshot> messageLetters,
            boolean showCheckpointHand,
            int handX,
            int handY,
            int handTargetY,
            boolean handThumbsUp,
            boolean handMovingDown,
            List<CheckpointRainbowRingSnapshot> rainbowRings,
            int pendingRingRequirement,
            int pendingRingsCollected,
            boolean pendingFinalCheckpoint,
            boolean rainbowOnly) {
        CheckpointSnapshot {
            messageLetters = List.copyOf(messageLetters);
            rainbowRings = List.copyOf(rainbowRings);
        }
    }

    enum SpecialStageObjectType {
        RING,
        BOMB,
        EMERALD
    }

    record BaseObjectSnapshot(
            Sonic2SpecialStageObject.State state,
            int angle,
            long depthFixed,
            int screenX,
            int screenY,
            int trackFloorY,
            int animIndex,
            int animFrame,
            int animTimer,
            boolean onScreen,
            boolean highPriority) {
    }

    record ObjectSnapshot(
            SpecialStageObjectType type,
            BaseObjectSnapshot base,
            Integer ringSpinFrame,
            Sonic2SpecialStageEmerald.EmeraldPhase emeraldPhase,
            int emeraldPhaseTimer,
            int emeraldBobbingOffset,
            int emeraldBobbingCounter,
            int emeraldRingRequirement,
            boolean emeraldMusicFaded,
            boolean emeraldAwarded) {
    }

    record ObjectManagerSnapshot(
            byte[] objectLocationData,
            int[] stageOffsets,
            int currentPosition,
            int currentStage,
            int lastProcessedSegment,
            int ringsCollected,
            int perfectRingsTotal,
            int currentSpecialAct,
            boolean noCheckpointFlag,
            boolean noCheckpointMsgFlag,
            boolean ringsToGoEnabled,
            boolean emeraldSpawned,
            List<ObjectSnapshot> activeObjects) {
        ObjectManagerSnapshot {
            objectLocationData = Sonic2SpecialStageSnapshot.cloneByteArray(objectLocationData);
            stageOffsets = Sonic2SpecialStageSnapshot.cloneIntArray(stageOffsets);
            activeObjects = List.copyOf(activeObjects);
        }

        public byte[] objectLocationData() {
            return Sonic2SpecialStageSnapshot.cloneByteArray(objectLocationData);
        }

        public int[] stageOffsets() {
            return Sonic2SpecialStageSnapshot.cloneIntArray(stageOffsets);
        }
    }

    static byte[] cloneByteArray(byte[] source) {
        return source != null ? source.clone() : null;
    }

    static int[] cloneIntArray(int[] source) {
        return source != null ? source.clone() : null;
    }

    static Palette[] clonePalettes(Palette[] source) {
        if (source == null) {
            return null;
        }
        Palette[] copy = new Palette[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].deepCopy() : null;
        }
        return copy;
    }

    static <T> List<T> copyList(List<T> source) {
        return source != null ? List.copyOf(source) : List.of();
    }
}
