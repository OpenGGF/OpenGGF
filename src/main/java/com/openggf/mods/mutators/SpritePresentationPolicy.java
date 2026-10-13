package com.openggf.mods.mutators;

import com.openggf.game.ModApi;
import java.util.Objects;

/** Transforms admitted artwork; native collision, targeting and animation stay owned. */
@ModApi
public record SpritePresentationPolicy(Mode mode, Target target, int strengthPercent,
                                       int periodPresentations) implements MutatorPolicy {
    public SpritePresentationPolicy {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(target, "target");
        if (periodPresentations < 30 || periodPresentations > 1800)
            throw new IllegalArgumentException("Sprite period must be 30..1800");
        boolean valid = switch (mode) {
            case SCALE -> strengthPercent >= 75 && strengthPercent <= 200;
            case SQUASH -> strengthPercent >= 100 && strengthPercent <= 120;
            default -> strengthPercent == 100;
        };
        if (!valid) throw new IllegalArgumentException("Sprite strength exceeds mode bounds");
        if (mode == Mode.TRAIL && target != Target.LEADER && target != Target.ALL_TEAM)
            throw new IllegalArgumentException("Trails require an admitted native player");
    }
    @Override public MutatorCapability capability() { return MutatorCapability.SPRITE_PRESENTATION; }
    @ModApi public enum Mode { RAINBOW, TRAIL, SCALE, ROTATE, SQUASH }
    @ModApi public enum Target { LEADER, ALL_TEAM, BADNIKS, PICKUPS, BADNIKS_AND_PICKUPS }
}
