package com.openggf.mods.mutators;

import com.openggf.game.ModApi;
import java.util.Objects;

/** Owner UI content marker; it grants no direct world mutation or modal hold. */
@ModApi
public record OverlayPolicy(Mode mode) implements MutatorPolicy {
    public OverlayPolicy { Objects.requireNonNull(mode, "mode"); }
    @Override public MutatorCapability capability() { return MutatorCapability.OVERLAY_CONTENT; }
    @ModApi public enum Mode { SIMULATED_CHAT, PONG, SNAKE, BLOCK_STACK, RHYTHM, LANE_DEFENCE }
}
