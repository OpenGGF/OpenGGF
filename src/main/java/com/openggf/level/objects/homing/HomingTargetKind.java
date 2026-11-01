package com.openggf.level.objects.homing;

/** Mutator target classes in descending default preference. Native contact still decides the result. */
public enum HomingTargetKind {
    BOSS, BADNIK, BREAKABLE_WALL, ATTACHABLE, BREAKABLE;

    /** Lower ranks win ties after hidden priority and facing. */
    public int rank() { return ordinal(); }

    /** Attack kinds are struck by a rolling player; attachable targets are reached for a native grab. */
    public boolean attack() { return this == BOSS || this == BADNIK; }
}
