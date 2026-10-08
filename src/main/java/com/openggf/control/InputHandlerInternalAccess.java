package com.openggf.control;

/** Host-only player edge bridge; these operations are not the creator InputHandler API. */
public final class InputHandlerInternalAccess {
    private InputHandlerInternalAccess() { }
    public static void retainGameplayInput(InputHandler handler, PlayerInputState player1, PlayerInputState player2) {
        handler.retainGameplayInputForNextRefresh(player1, player2);
    }
    public static void discardRetainedGameplayInput(InputHandler handler) {
        if (handler != null) handler.discardRetainedGameplayInput();
    }
}
