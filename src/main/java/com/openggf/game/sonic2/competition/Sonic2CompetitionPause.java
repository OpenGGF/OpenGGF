package com.openggf.game.sonic2.competition;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;

/**
 * Captured S2 PauseGame continuation, independent of polling, audio and world execution.
 *
 * <p>PauseGame (s2.asm:1571-1597) tests the previously polled controller bytes;
 * Pause_ChkBC/Pause_ChkStart (s2.asm:1607-1617) read the current pause wait's
 * sample. Pause_Resume and Pause_SlowMo (s2.asm:1619-1632) both return before
 * Level_MainLoop's distinct ordinary wait and body (s2.asm:5088-5095).
 * The host seeds the actual preceding native wait, then services each chosen
 * wait before supplying its sample. Reset does not invent a controller sample.
 */
final class Sonic2CompetitionPause implements RewindSnapshottable<Sonic2CompetitionPause.State> {
    private static final int START = 0x80, A = 0x40, B = 0x10, C = 0x20;

    enum Wait { ORDINARY_WAIT, PAUSE_WAIT }
    enum Exit { NONE, TITLE_SCREEN }
    enum Continuation {
        UNSEEDED,
        /** Execute PauseGame using the previously captured two-controller sample. */
        ENTRY,
        /** Continue Pause_Loop without repeating its entry/life test. */
        PAUSE_WAIT,
        /** PauseGame returned; the caller still owes the ordinary wait. */
        ORDINARY_WAIT,
        AWAITING_PAUSE_SAMPLE, AWAITING_ORDINARY_SAMPLE, BODY_READY, EXITED
    }

    /** Native words retain held in the high byte and pressed in the low byte. */
    record State(int player1ControllerWord, int player2ControllerWord, boolean gamePaused,
                 Continuation continuation, Exit pendingExit) {
        State {
            requireWord(player1ControllerWord);
            requireWord(player2ControllerWord);
            Objects.requireNonNull(continuation, "continuation");
            Objects.requireNonNull(pendingExit, "pending exit");
            if (continuation == Continuation.UNSEEDED
                    && (player1ControllerWord != 0 || player2ControllerWord != 0 || gamePaused || pendingExit != Exit.NONE))
                throw new IllegalArgumentException("Unseeded pause has no native controller or continuation state");
            if ((continuation == Continuation.PAUSE_WAIT || continuation == Continuation.AWAITING_PAUSE_SAMPLE)
                    && !gamePaused)
                throw new IllegalArgumentException("Pause_Loop requires Game_paused");
            if (pendingExit == Exit.TITLE_SCREEN
                    && (gamePaused || switch (continuation) {
                        case ORDINARY_WAIT, AWAITING_ORDINARY_SAMPLE, BODY_READY, EXITED -> false;
                        default -> true;
                    }))
                throw new IllegalArgumentException("Title exit must follow Pause_Resume's ordinary continuation");
            if (continuation == Continuation.EXITED && pendingExit == Exit.NONE)
                throw new IllegalArgumentException("Exited pause requires its completed title request");
        }
    }

    private static final State UNSEEDED_STATE = new State(0, 0, false, Continuation.UNSEEDED, Exit.NONE);
    private State state = UNSEEDED_STATE;

    void seedFromNativeWait(LogicalInputSnapshot sample) {
        if (state.continuation() != Continuation.UNSEEDED)
            throw new IllegalStateException("Native pause already has a controller sample");
        Objects.requireNonNull(sample, "native wait sample");
        int one = convertLogical(sample.player1()), two = convertLogical(sample.player2());
        state = new State(one, two, false, Continuation.ENTRY, Exit.NONE);
    }

    /**
     * Choose one wait. FixBugs=0 checks only P1's native life BYTE; the fixed
     * branch additionally rejects P2 game-over and either time-over flag.
     * An already running Pause_Loop does not repeat that entry check.
     */
    Wait beginWait(int nativeP1Lives) {
        return switch (state.continuation()) {
            case ENTRY -> {
                if ((nativeP1Lives & 0xFF) == 0) {
                    continueAt(false, Continuation.AWAITING_ORDINARY_SAMPLE, Exit.NONE);
                    yield Wait.ORDINARY_WAIT;
                }
                if (state.gamePaused() || ((pressed(0) | pressed(1)) & START) != 0) {
                    continueAt(true, Continuation.AWAITING_PAUSE_SAMPLE, Exit.NONE);
                    yield Wait.PAUSE_WAIT;
                }
                continueAt(false, Continuation.AWAITING_ORDINARY_SAMPLE, Exit.NONE);
                yield Wait.ORDINARY_WAIT;
            }
            case PAUSE_WAIT -> {
                continueAt(true, Continuation.AWAITING_PAUSE_SAMPLE, Exit.NONE);
                yield Wait.PAUSE_WAIT;
            }
            case ORDINARY_WAIT -> {
                continueAt(state.gamePaused(), Continuation.AWAITING_ORDINARY_SAMPLE, state.pendingExit());
                yield Wait.ORDINARY_WAIT;
            }
            default -> throw new IllegalStateException("No next native wait from " + state.continuation());
        };
    }

    /** Accept both actual controller samples after the selected wait. */
    void acceptSample(LogicalInputSnapshot sample, boolean slowMotion) {
        boolean pauseWait = state.continuation() == Continuation.AWAITING_PAUSE_SAMPLE;
        if (!pauseWait && state.continuation() != Continuation.AWAITING_ORDINARY_SAMPLE)
            throw new IllegalStateException("No native controller wait is pending");
        Objects.requireNonNull(sample, "native wait sample");
        int one = convertLogical(sample.player1()), two = convertLogical(sample.player2());
        if (!pauseWait) {
            state = new State(one, two, state.gamePaused(), Continuation.BODY_READY, state.pendingExit());
            return;
        }
        // Shipped source priority: P1 A pressed, P1 B held/C pressed, then
        // either player's Start pressed. S2 has no Time Attack B exit branch.
        if (slowMotion && (one & A) != 0) {
            state = new State(one, two, false, Continuation.ORDINARY_WAIT, Exit.TITLE_SCREEN);
        } else if (slowMotion && (((one >>> 8) & B) != 0 || (one & C) != 0)) {
            state = new State(one, two, true, Continuation.ORDINARY_WAIT, Exit.NONE);
        } else if (((one | two) & START) != 0) {
            state = new State(one, two, false, Continuation.ORDINARY_WAIT, Exit.NONE);
        } else {
            state = new State(one, two, true, Continuation.PAUSE_WAIT, Exit.NONE);
        }
    }

    /** Expose a title request only after the following ordinary body completes. */
    Exit completeBody() {
        if (!bodyReady()) throw new IllegalStateException("No native ordinary body is ready");
        Exit exit = state.pendingExit();
        continueAt(state.gamePaused(), exit == Exit.NONE ? Continuation.ENTRY : Continuation.EXITED, exit);
        return exit;
    }

    int player1ControllerWord() { return state.player1ControllerWord(); }
    int player2ControllerWord() { return state.player2ControllerWord(); }
    int controllerWord(int slot) {
        return switch (slot) {
            case 0 -> state.player1ControllerWord();
            case 1 -> state.player2ControllerWord();
            default -> throw new IndexOutOfBoundsException(slot);
        };
    }
    int held(int slot) { return controllerWord(slot) >>> 8; }
    int pressed(int slot) { return controllerWord(slot) & 0xFF; }
    boolean gamePaused() { return state.gamePaused(); }
    Continuation continuation() { return state.continuation(); }
    Exit pendingExit() { return state.pendingExit(); }
    boolean bodyReady() { return state.continuation() == Continuation.BODY_READY; }

    static int convertLogical(PlayerInputState input) {
        Objects.requireNonNull(input, "native controller");
        int held = (input.heldMask() & 15) | InputActionMasks.toMegaDriveButtonBits(input.actionHeldMask())
                | (input.startHeld() ? START : 0);
        int pressed = (input.pressedMask() & 15) | InputActionMasks.toMegaDriveButtonBits(input.actionPressedMask())
                | (input.startPressed() ? START : 0);
        return held << 8 | pressed;
    }

    void reset() { state = UNSEEDED_STATE; }
    @Override public String key() { return "sonic2.competition.pause"; }
    @Override public State capture() { return state; }
    @Override public void restore(State snapshot) { state = Objects.requireNonNull(snapshot, "native pause snapshot"); }
    @Override public void resetForMissingSnapshot() { reset(); }

    private void continueAt(boolean paused, Continuation continuation, Exit exit) {
        state = new State(state.player1ControllerWord(), state.player2ControllerWord(), paused, continuation, exit);
    }

    private static void requireWord(int word) {
        if ((word & ~0xFFFF) != 0) throw new IllegalArgumentException("Native controller must fit an unsigned word");
    }
}
