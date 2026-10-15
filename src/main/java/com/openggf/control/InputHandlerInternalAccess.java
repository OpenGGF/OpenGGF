package com.openggf.control;

/** Host-only player edge bridge; these operations are not the creator InputHandler API. */
public final class InputHandlerInternalAccess {
    private InputHandlerInternalAccess() { }
    public static void publishNativeStagePlayers(InputHandler handler, PlayerInputState p1, PlayerInputState p2) {
        handler.publishNativeStagePlayers(p1, p2);
    }
    public static void retainGameplayInput(InputHandler handler, PlayerInputState player1, PlayerInputState player2) {
        handler.retainGameplayInputForNextRefresh(player1, player2);
    }
    /**
     * While true, Genesis/menu mappings, debug shortcuts and rewind see a neutral pad; physical
     * pad state ({@link InputHandler#capturePhysicalInput()}) and prompt-family tracking remain.
     * The window-level editor HUD owns the pads this way.
     */
    public static void suppressGamepad(InputHandler handler, boolean suppressed) {
        if (handler != null) handler.setGamepadSuppressed(suppressed);
    }
    public static void discardRetainedGameplayInput(InputHandler handler) {
        if (handler != null) handler.discardRetainedGameplayInput();
    }
}
