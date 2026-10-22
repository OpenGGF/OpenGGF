package com.openggf.level.scroll;

/**
 * Immutable game-owned picture phase. Sampling performs composition only: it must not advance a
 * native handler, consult live services, mutate gameplay, or publish art/scroll state.
 */
@FunctionalInterface
public interface ScrollProjectionPhase {
    ScrollProjection sample(ScrollView view);
}
