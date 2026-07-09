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
