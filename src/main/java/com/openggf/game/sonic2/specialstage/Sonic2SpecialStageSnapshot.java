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
