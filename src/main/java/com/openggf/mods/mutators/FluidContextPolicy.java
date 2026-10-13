package com.openggf.mods.mutators;

/** A breathable, player-local fluid region. The native act's water/event owner is untouched. */
@com.openggf.game.ModApi
public record FluidContextPolicy(int radius) implements MutatorPolicy {
    public FluidContextPolicy {
        if (radius < 48 || radius > 128) throw new IllegalArgumentException("Fluid radius must be 48..128 pixels");
    }
    public FluidContextPolicy() { this(96); }
    @Override public MutatorCapability capability() { return MutatorCapability.FLUID_CONTEXT; }
}
