package com.openggf.mods.mutators;

import com.openggf.game.ModApi;
import java.util.Objects;

/** Bounded world-picture transforms; native simulation and HUD remain separate. */
@ModApi
public record ScenePresentationPolicy(Mode mode, int magnitude, int periodPresentations,
                                      int sceneryIndex) implements MutatorPolicy {
    public ScenePresentationPolicy {
        Objects.requireNonNull(mode, "mode");
        if (periodPresentations < 30 || periodPresentations > 1800)
            throw new IllegalArgumentException("Presentation period must be 30..1800");
        int limit = switch (mode) {
            case SHAKE -> 6;
            case ZOOM, INPUT_SQUASH -> 8;
            case OUTLINE -> 30;
            default -> 0;
        };
        if (magnitude < 0 || magnitude > limit)
            throw new IllegalArgumentException("Picture magnitude exceeds mode bounds");
        if (sceneryIndex < 0 || sceneryIndex > 13 || mode != Mode.SCENERY && sceneryIndex != 0)
            throw new IllegalArgumentException("Scenery index exceeds prepared native source bounds");
    }
    public ScenePresentationPolicy(Mode mode, int magnitude, int periodPresentations) {
        this(mode, magnitude, periodPresentations, 0);
    }
    @Override public MutatorCapability capability() { return MutatorCapability.SCENE_PRESENTATION; }
    @ModApi public enum Mode { NEGATIVE, MIRROR_HORIZONTAL, MIRROR_VERTICAL, SHAKE, ZOOM, INPUT_SQUASH, OUTLINE, SCENERY }
}
