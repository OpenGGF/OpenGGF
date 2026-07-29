package com.openggf.game;

import java.util.Objects;

/**
 * Typed request to enter a special stage.
 *
 * @param forcedStageIndex exact zero-based stage index, or {@code null} to use
 *                         the provider's normal cursor
 * @param rewardKind progression awarded on success
 */
@ModApi
public record SpecialStageEntryRequest(
        Integer forcedStageIndex,
        EmeraldRewardKind rewardKind) {

    public SpecialStageEntryRequest {
        if (forcedStageIndex != null && forcedStageIndex < 0) {
            throw new IllegalArgumentException("forcedStageIndex must be non-negative");
        }
        Objects.requireNonNull(rewardKind, "rewardKind");
    }

    public static SpecialStageEntryRequest ordinary() {
        return new SpecialStageEntryRequest(null, EmeraldRewardKind.CHAOS_EMERALD);
    }
}
