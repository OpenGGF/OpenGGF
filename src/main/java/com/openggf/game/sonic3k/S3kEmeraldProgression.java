package com.openggf.game.sonic3k;

import com.openggf.game.GameStateManager;

import java.util.List;
import java.util.Objects;

/**
 * Typed view of S3K's two-bit {@code Got_Emeralds_array} entries.
 */
public final class S3kEmeraldProgression {
    public enum EmeraldState {
        ABSENT(0),
        CHAOS(1),
        GRAY_SUPER(2),
        SUPER(3);

        private final int romValue;

        EmeraldState(int romValue) {
            this.romValue = romValue;
        }

        public int romValue() {
            return romValue;
        }

        static EmeraldState fromRomValue(int value) {
            for (EmeraldState state : values()) {
                if (state.romValue == value) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Invalid S3K emerald state: " + value);
        }
    }

    private final GameStateManager gameState;

    private S3kEmeraldProgression(GameStateManager gameState) {
        this.gameState = Objects.requireNonNull(gameState, "gameState");
    }

    public static S3kEmeraldProgression from(GameStateManager gameState) {
        return new S3kEmeraldProgression(gameState);
    }

    public static S3kEmeraldProgression restore(
            GameStateManager gameState, List<Integer> states, boolean emeraldsConverted) {
        Objects.requireNonNull(gameState, "gameState")
                .restoreS3kEmeraldProgress(states, emeraldsConverted);
        return new S3kEmeraldProgression(gameState);
    }

    public List<Integer> states() {
        return gameState.getS3kEmeraldStates();
    }

    public EmeraldState state(int index) {
        return EmeraldState.fromRomValue(states().get(index));
    }

    /**
     * Latches the ROM conversion flag when the sanctuary controller discovers
     * at least one state-1 emerald. No pedestal value changes at this point.
     */
    public boolean beginSanctuaryConversion() {
        if (states().stream().noneMatch(value -> value == EmeraldState.CHAOS.romValue())) {
            return false;
        }
        gameState.setEmeraldsConverted(true);
        return true;
    }

    public int convert(int index) {
        if (state(index) == EmeraldState.CHAOS) {
            replace(index, EmeraldState.GRAY_SUPER);
        }
        return state(index).romValue();
    }

    public int awardSuper(int index) {
        if (state(index) == EmeraldState.GRAY_SUPER) {
            replace(index, EmeraldState.SUPER);
        }
        return state(index).romValue();
    }

    public boolean isConverted() {
        return gameState.isEmeraldsConverted();
    }

    private void replace(int index, EmeraldState replacement) {
        List<Integer> updated = new java.util.ArrayList<>(states());
        updated.set(index, replacement.romValue());
        gameState.restoreS3kEmeraldProgress(updated, gameState.isEmeraldsConverted());
    }
}
