package com.openggf.control;

/** Host-only player edge bridge; these operations are not the creator InputHandler API. */
public final class InputHandlerInternalAccess {
    private InputHandlerInternalAccess() { }
    public static TextInputSession textInput(InputHandler handler) { return handler.textInput; }
    public static TextInputSession.Lease captureKeyboardText(InputHandler handler, Object owner) {
        return handler.captureKeyboardText(owner);
    }
    public static void prepareKeyboardTextConsumer(InputHandler handler, Object owner) {
        if (handler != null) handler.prepareKeyboardTextConsumer(owner);
    }
    public static void releaseKeyboardText(InputHandler handler, Object owner) {
        if (handler != null) handler.releaseKeyboardText(owner);
    }
    public static void publishNativeStagePlayers(InputHandler handler, PlayerInputState p1, PlayerInputState p2) {
        handler.publishNativeStagePlayers(p1, p2);
    }
    /** Releases only the raw override this host owner actually published. */
    public static boolean clearLogicalOverrideIfSame(InputHandler handler, LogicalInputSnapshot expected) {
        return handler != null && handler.clearLogicalOverrideIfSame(expected);
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
    /**
     * A player's prompt label for one device: the bound key ({@code controller} false) or the physical
     * button on their assigned pad ({@code controller} true). Scenes name the device they track.
     */
    public static java.util.Optional<String> buttonLabel(InputHandler handler, int player,
                                                          ButtonPrompts.Button button, boolean controller) {
        if (handler == null) return java.util.Optional.empty();
        if (player != 0 && player != 1) throw new IllegalArgumentException("Player must be 0 or 1");
        java.util.Objects.requireNonNull(button, "button");
        return controller ? ButtonPrompts.padLabel(handler, player, button)
                : ButtonPrompts.keyLabel(handler, player, button);
    }
    public static void discardRetainedGameplayInput(InputHandler handler) {
        if (handler != null) handler.discardRetainedGameplayInput();
    }
}
