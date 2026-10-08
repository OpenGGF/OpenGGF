package com.openggf.control;

/** Internal presentation-to-simulation bridge. Replay overrides and host menu input remain separate. */
record RetainedGameplayInput(PlayerInputState player1, PlayerInputState player2) {
    LogicalInputSnapshot apply(LogicalInputSnapshot fresh) {
        return new LogicalInputSnapshot(merge(fresh.player1(), player1), merge(fresh.player2(), player2),
                fresh.menuUp(), fresh.menuDown(), fresh.menuLeft(), fresh.menuRight(), fresh.menuAccept(),
                fresh.menuBack(), fresh.menuStart(), fresh.anyActionPressed(), fresh.debugModeTogglePressed(),
                fresh.debugShiftDown(), fresh.debugControlDown(), fresh.debugAltDown(), fresh.debugSuperDown());
    }

    private static PlayerInputState merge(PlayerInputState fresh, PlayerInputState pending) {
        return PlayerInputState.of(fresh.heldMask(), fresh.pressedMask() | pending.pressedMask(),
                fresh.actionHeldMask(), fresh.actionPressedMask() | pending.actionPressedMask(),
                fresh.startHeld(), fresh.startPressed() || pending.startPressed());
    }
}
