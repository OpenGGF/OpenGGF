package com.openggf.control;

/** Replaces only players so the outer recorder captures the same native sample while host commands survive. */
final class NativeStageLogicalSample {
    private NativeStageLogicalSample() { }
    static LogicalInputSnapshot withPlayers(LogicalInputSnapshot fresh, PlayerInputState p1, PlayerInputState p2) {
        return new LogicalInputSnapshot(p1, p2, fresh.menuUp(), fresh.menuDown(), fresh.menuLeft(), fresh.menuRight(),
                fresh.menuAccept(), fresh.menuBack(), fresh.menuStart(), fresh.anyActionPressed(),
                fresh.debugModeTogglePressed(), fresh.debugShiftDown(), fresh.debugControlDown(),
                fresh.debugAltDown(), fresh.debugSuperDown());
    }
}
