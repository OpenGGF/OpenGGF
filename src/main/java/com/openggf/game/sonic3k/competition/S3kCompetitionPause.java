package com.openggf.game.sonic3k.competition;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;

/**
 * Captured continuation of Pause_Game and the competition loop's following wait.
 * The host supplies only P1's physical controller bytes at each actual wait;
 * this planner neither polls input nor executes a world or VBlank.
 *
 * <p>Pause_Game (sonic3k.asm:1550-1630) tests the previous Ctrl_1_pressed
 * before entering Pause_Loop. That loop reads its newly polled bytes after
 * Wait_VSync. Resume, Time Attack B and slow-motion frame advance all return
 * to loc_6678's separate ordinary Wait_VSync before another body executes
 * (sonic3k.asm:8021-8063). There is no debounce counter or P2 input branch.
 */
public final class S3kCompetitionPause implements RewindSnapshottable<S3kCompetitionPause.State> {
    /** GetDemoPtr writes Start held, no newly pressed buttons (sonic3k.asm:9489-9500). */
    public static final int INITIAL_CONTROLLER_WORD = 0x8000;
    private static final int START = 0x80, A = 0x40, B = 0x10, C = 0x20;

    public enum Wait { ORDINARY_WAIT, PAUSE_WAIT }
    public enum Exit { NONE, TIME_ATTACK_SELECTION, TITLE_SCREEN }
    public enum Continuation {
        /** Execute Pause_Game using the previously latched controller word. */
        ENTRY,
        /** Still inside Pause_Loop; do not repeat its entry/life check. */
        PAUSE_WAIT,
        /** Pause_Game returned; the caller must still reach its ordinary wait. */
        ORDINARY_WAIT,
        AWAITING_PAUSE_SAMPLE, AWAITING_ORDINARY_SAMPLE, BODY_READY, EXITED
    }

    public record State(int controllerWord, boolean gamePaused,
            Continuation continuation, Exit pendingExit) {
        public State {
            if ((controllerWord & ~0xFFFF) != 0)
                throw new IllegalArgumentException("Ctrl_1 must fit a native word");
            Objects.requireNonNull(continuation, "continuation");
            Objects.requireNonNull(pendingExit, "pendingExit");
        }
    }

    private int controllerWord = INITIAL_CONTROLLER_WORD;
    private boolean gamePaused;
    private Continuation continuation = Continuation.ENTRY;
    private Exit pendingExit = Exit.NONE;

    public int controllerWord() { return controllerWord; }
    public int held() { return controllerWord >>> 8; }
    public int pressed() { return controllerWord & 0xFF; }
    public boolean gamePaused() { return gamePaused; }
    public Continuation continuation() { return continuation; }
    public Exit pendingExit() { return pendingExit; }
    public boolean bodyReady() { return continuation == Continuation.BODY_READY; }

    /**
     * Choose exactly one next wait. Entry observes the native BYTE Life_count;
     * an already active Pause_Loop does not repeat that test on every VBlank.
     */
    public Wait beginWait(int nativeP1Lives) {
        switch (continuation) {
            case ENTRY -> {
                if ((nativeP1Lives & 0xFF) == 0) gamePaused = false;
                else if (gamePaused || (pressed() & START) != 0) {
                    gamePaused = true;
                    continuation = Continuation.AWAITING_PAUSE_SAMPLE;
                    return Wait.PAUSE_WAIT;
                }
                continuation = Continuation.AWAITING_ORDINARY_SAMPLE;
                return Wait.ORDINARY_WAIT;
            }
            case PAUSE_WAIT -> {
                continuation = Continuation.AWAITING_PAUSE_SAMPLE;
                return Wait.PAUSE_WAIT;
            }
            case ORDINARY_WAIT -> {
                continuation = Continuation.AWAITING_ORDINARY_SAMPLE;
                return Wait.ORDINARY_WAIT;
            }
            default -> throw new IllegalStateException("No next native wait from " + continuation);
        }
    }

    /** Accept the Ctrl_1 bytes polled by the wait just selected by beginWait. */
    public void acceptSample(int p1Held, int p1Pressed, boolean slowMotion, boolean timeAttack) {
        requireByte(p1Held); requireByte(p1Pressed);
        if (continuation != Continuation.AWAITING_PAUSE_SAMPLE
                && continuation != Continuation.AWAITING_ORDINARY_SAMPLE)
            throw new IllegalStateException("No native controller wait is pending");
        boolean pauseWait = continuation == Continuation.AWAITING_PAUSE_SAMPLE;
        controllerWord = p1Held << 8 | p1Pressed;
        if (!pauseWait) {
            continuation = Continuation.BODY_READY;
            return;
        }
        // Shipped Slow_motion_flag branch order: A pressed, B held, C pressed.
        // This is not a FixBugs/Sonic2K variant; the locked-on flag is normally 0.
        if (slowMotion && (p1Pressed & A) != 0) {
            pendingExit = Exit.TITLE_SCREEN;
            gamePaused = false;
            continuation = Continuation.ORDINARY_WAIT;
        } else if (slowMotion && ((p1Held & B) != 0 || (p1Pressed & C) != 0)) {
            gamePaused = true; // Pause_FrameAdvance leaves Game_paused=1.
            continuation = Continuation.ORDINARY_WAIT;
        } else if (timeAttack && (p1Pressed & B) != 0) {
            pendingExit = Exit.TIME_ATTACK_SELECTION;
            gamePaused = false;
            continuation = Continuation.ORDINARY_WAIT;
        } else if ((p1Pressed & START) != 0) {
            gamePaused = false;
            continuation = Continuation.ORDINARY_WAIT;
        } else continuation = Continuation.PAUSE_WAIT;
    }

    /** Word form matches RAM: held in the high byte, pressed in the low byte. */
    public void acceptSample(int p1ControllerWord, boolean slowMotion, boolean timeAttack) {
        if ((p1ControllerWord & ~0xFFFF) != 0)
            throw new IllegalArgumentException("Ctrl_1 must fit a native word");
        acceptSample(p1ControllerWord >>> 8, p1ControllerWord & 0xFF, slowMotion, timeAttack);
    }

    /** Report a mode request only after the caller has run the following ordinary body. */
    public Exit completeBody() {
        if (continuation != Continuation.BODY_READY)
            throw new IllegalStateException("No native ordinary body is ready");
        continuation = pendingExit == Exit.NONE ? Continuation.ENTRY : Continuation.EXITED;
        return pendingExit;
    }

    public void reset() {
        controllerWord = INITIAL_CONTROLLER_WORD;
        gamePaused = false;
        continuation = Continuation.ENTRY;
        pendingExit = Exit.NONE;
    }
    @Override public String key() { return "sonic3k.competition.pause"; }
    @Override public State capture() { return new State(controllerWord, gamePaused, continuation, pendingExit); }
    @Override public void restore(State state) {
        Objects.requireNonNull(state, "state");
        controllerWord = state.controllerWord(); gamePaused = state.gamePaused();
        continuation = state.continuation(); pendingExit = state.pendingExit();
    }
    @Override public void resetForMissingSnapshot() { reset(); }

    private static void requireByte(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Native controller input must fit a byte");
    }
}
