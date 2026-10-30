package com.openggf.control;

/** Host-only player edge bridge; these operations are not the creator InputHandler API. */
public final class InputHandlerInternalAccess {
    private InputHandlerInternalAccess() { }
    /**
     * A run-to-completion input consumer on the window thread. Event collection must
     * finish before entry. Logical re-reads reuse its pad sample, while keyboard/text
     * claims and recorded publications still go through their existing owners.
     * Closing retires the sample; only the consumer's existing update() acknowledges
     * input. In particular setup deferrals and aborts do not invent an acknowledgment.
     */
    public static final class InputPass implements AutoCloseable {
        private final InputHandler input;
        LogicalInputSnapshot gamepad;
        // Recorded gameplay may strip live player presses, while physical text
        // menus retain the original sample until their actual owner acknowledges.
        LogicalInputSnapshot physicalGamepad;
        GamepadInputManager.LogicalHistory priorGamepad;
        RetainedGameplayInput carry;
        OrderedGameplayInput.Sample admission;
        final boolean hostControls;
        final boolean observationOnly;
        boolean previewGameplay;
        boolean acknowledged;
        private boolean closed;

        InputPass(InputHandler input, boolean hostControls, boolean observationOnly) {
            this.input = input;
            this.hostControls = hostControls;
            this.observationOnly = observationOnly;
        }

        /**
         * Ends the host scope before invoking the next owner. Only its first input
         * pass inherits this sample; deliberate pumps keep independent passes.
         * A deferral or exception retains the existing unacknowledged-pass policy.
         */
        public void handoff(Runnable nextOwner) {
            java.util.Objects.requireNonNull(nextOwner, "nextOwner");
            if (closed || acknowledged || !hostControls) {
                throw new IllegalStateException("Only an unacknowledged host pass can hand off input");
            }
            closed = true;
            input.handoffInputPass(this, nextOwner);
        }

        @Override public void close() {
            if (!closed) {
                closed = true;
                input.closeInputPass(this);
            }
        }
    }

    /** Host-internal scope, separate from creator API and from simulation admission. */
    public static InputPass openInputPass(InputHandler handler) {
        return handler.openInputPass(false);
    }

    /** Samples host controls without taking the pending gameplay-input bridge. */
    public static InputPass openHostInputPass(InputHandler handler) {
        return handler.openInputPass(true);
    }
    /** Zero-step host service acknowledges its own controls, leaving physical gameplay order pending. */
    public static InputPass openObservationInputPass(InputHandler handler) {
        return handler.openInputPass(false, true);
    }
    /** Retain the same ordered prefix without draining it on a zero-step presentation. */
    public static LogicalInputSnapshot previewGameplayInput(InputHandler handler) {
        return handler.previewGameplayInput();
    }
    /** Host discontinuity/whole-frame claim. Never changes replay or rewind state. */
    public static void discardOrderedGameplayInput(InputHandler handler) {
        if (handler != null) handler.discardOrderedGameplayInput();
    }
    /** Native menu compatibility binding, sharing the consuming logical sample. */
    public static boolean nativeMenuKeyPressed(InputHandler handler, int key) {
        return handler.nativeMenuKeyPressed(key);
    }
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
    /** Temporarily publishes one controlled native row, restoring live/replay ownership even on failure. */
    public static <T> T withNativeInput(InputHandler handler, LogicalInputSnapshot snapshot,
            java.util.function.Supplier<T> row) {
        return handler.withNativeInput(snapshot, row);
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
    public static void discardRetainedGameplayInput(InputHandler handler) {
        if (handler != null) handler.discardRetainedGameplayInput();
    }
}
