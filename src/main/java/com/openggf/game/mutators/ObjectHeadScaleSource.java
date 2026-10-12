package com.openggf.game.mutators;

/** Admitted render-only size, owned by one world. Rings come from its current native leader. */
@FunctionalInterface
public interface ObjectHeadScaleSource { int percent(ObjectHeadRole role, int rings); }
