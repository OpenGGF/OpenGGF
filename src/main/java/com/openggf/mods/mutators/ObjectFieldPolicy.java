package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record ObjectFieldPolicy(Target target, Mode mode) implements MutatorPolicy {
    public ObjectFieldPolicy {
        java.util.Objects.requireNonNull(target); java.util.Objects.requireNonNull(mode);
        if (mode == Mode.ORBIT && target != Target.RING) throw new IllegalArgumentException("Orbit requires independently owned rings");
    }
    @Override public MutatorCapability capability() { return MutatorCapability.OBJECT_FIELD; }
    @com.openggf.game.ModApi public enum Target { RING, BADNIK, PROJECTILE }
    @com.openggf.game.ModApi public enum Mode { ATTRACT, REPEL, ORBIT }
}
