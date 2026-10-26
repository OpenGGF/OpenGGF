package com.openggf.game.sonic2.competition;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.rewind.RewindRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.openggf.game.sonic2.competition.Sonic2CompetitionPause.Continuation;
import static com.openggf.game.sonic2.competition.Sonic2CompetitionPause.Exit;
import static com.openggf.game.sonic2.competition.Sonic2CompetitionPause.State;
import static com.openggf.game.sonic2.competition.Sonic2CompetitionPause.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** Literal two-controller and wait-boundary controls from S2 PauseGame/Level_MainLoop. */
class TestSonic2CompetitionPause {
    @Test void explicitSeedingPreservesBothNativeWordsAndAllThreeIndependentActions() {
        var pause = new Sonic2CompetitionPause();
        var unseeded = pause.capture();
        assertEquals(Continuation.UNSEEDED, pause.continuation());
        assertThrows(IllegalStateException.class, () -> pause.beginWait(3));
        assertThrows(IllegalStateException.class, () -> pause.acceptSample(sample(0, 0), false));
        assertThrows(IllegalStateException.class, pause::completeBody);
        assertEquals(unseeded, pause.capture());

        // Pressed bits intentionally need not be a subset of held bits: restore
        // retains actual native words, including stale caller-owned samples.
        pause.seedFromNativeWait(sample(0xD5E9, 0x2A16));
        assertEquals(0xD5E9, pause.player1ControllerWord());
        assertEquals(0x2A16, pause.player2ControllerWord());
        assertEquals(0xD5, pause.held(0)); assertEquals(0xE9, pause.pressed(0));
        assertEquals(0x2A, pause.held(1)); assertEquals(0x16, pause.pressed(1));
        assertEquals(Continuation.ENTRY, pause.continuation());
        assertFalse(pause.gamePaused());
        assertThrows(IllegalStateException.class, () -> pause.seedFromNativeWait(sample(0, 0)));
        assertEquals(Continuation.UNSEEDED, unseeded.continuation(), "retained state is immutable");
    }

    @Test void currentP2StartRunsItsOrdinaryBodyBeforeTheNextEntryCanPause() {
        var pause = seeded(0, 0);
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(3));
        pause.acceptSample(sample(0, 0x8080), false);
        assertFalse(pause.gamePaused());
        assertTrue(pause.bodyReady());
        assertEquals(Exit.NONE, pause.completeBody());
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(3));
        assertTrue(pause.gamePaused());
        assertEquals(Continuation.AWAITING_PAUSE_SAMPLE, pause.continuation());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void eitherHumansStartCanEnterAndTheOtherHumansStartCanResume(int entrySlot) {
        var pause = seeded(entrySlot == 0 ? 0x8080 : 0, entrySlot == 1 ? 0x8080 : 0);
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(3));
        pause.acceptSample(sample(0, 0), false);
        assertEquals(Continuation.PAUSE_WAIT, pause.continuation());
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(3));
        pause.acceptSample(sample(entrySlot == 1 ? 0x8080 : 0, entrySlot == 0 ? 0x8080 : 0), false);
        assertFalse(pause.gamePaused());
        assertFalse(pause.bodyReady());
        assertEquals(Continuation.ORDINARY_WAIT, pause.continuation());
        assertThrows(IllegalStateException.class, pause::completeBody);
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(3));
        pause.acceptSample(sample(0x8000, 0x8000), false);
        assertEquals(Exit.NONE, pause.completeBody());
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(3), "the consumed resume edge cannot repause");
    }

    @Test void heldStartWithoutAPressedEdgeNeitherEntersNorResumes() {
        var running = seeded(0x8000, 0x8000);
        assertEquals(Wait.ORDINARY_WAIT, running.beginWait(3));
        running.acceptSample(sample(0x8000, 0x8000), false);
        running.completeBody();
        assertEquals(Wait.ORDINARY_WAIT, running.beginWait(3));
        assertFalse(running.gamePaused());

        var paused = enteredPause();
        paused.acceptSample(sample(0x8000, 0x8000), false);
        assertEquals(Continuation.PAUSE_WAIT, paused.continuation());
        assertTrue(paused.gamePaused());
    }

    @ParameterizedTest @ValueSource(ints = {0, 0x100, -256})
    void zeroP1LifeByteBypassesPreviousStartAndClearsAnEntryPause(int lives) {
        var pause = new Sonic2CompetitionPause();
        pause.restore(new State(0x8080, 0xFFFF, true, Continuation.ENTRY, Exit.NONE));
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(lives));
        assertFalse(pause.gamePaused());
        assertEquals(Continuation.AWAITING_ORDINARY_SAMPLE, pause.continuation());
        pause.acceptSample(sample(0, 0), false);
        assertEquals(Exit.NONE, pause.completeBody());
    }

    @ParameterizedTest @ValueSource(ints = {0xFF, -1, 0x103})
    void nonzeroUnsignedP1LifeByteAllowsEitherPlayersPreviousStart(int lives) {
        var pause = seeded(0, 0x0080);
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(lives));
        assertTrue(pause.gamePaused());
    }

    @Test void anExistingPauseLoopHasNoDebounceOrRepeatedLifeCheck() {
        var pause = enteredPause();
        for (int poll = 0; poll < 17; poll++) {
            pause.acceptSample(sample(0, 0), false);
            assertTrue(pause.gamePaused());
            assertFalse(pause.bodyReady());
            assertEquals(Wait.PAUSE_WAIT, pause.beginWait(0));
        }
        pause.acceptSample(sample(0, 0x0080), false);
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(0));
        assertFalse(pause.gamePaused());
    }

    @ParameterizedTest @MethodSource("pauseBranches")
    void currentPauseSampleUsesShippedP1CheatThenEitherStartPriority(int one, int two, boolean slowMotion,
            Continuation continuation, boolean paused, Exit exit) {
        var pause = enteredPause();
        pause.acceptSample(sample(one, two), slowMotion);
        assertEquals(one, pause.player1ControllerWord());
        assertEquals(two, pause.player2ControllerWord());
        assertEquals(continuation, pause.continuation());
        assertEquals(paused, pause.gamePaused());
        assertEquals(exit, pause.pendingExit());
        assertFalse(pause.bodyReady());
    }

    private static Stream<Arguments> pauseBranches() {
        return Stream.of(
                Arguments.of(0xFFFF, 0x8080, true, Continuation.ORDINARY_WAIT, false, Exit.TITLE_SCREEN),
                Arguments.of(0x1090, 0x0080, true, Continuation.ORDINARY_WAIT, true, Exit.NONE),
                Arguments.of(0x0020, 0x0080, true, Continuation.ORDINARY_WAIT, true, Exit.NONE),
                Arguments.of(0x6000, 0, true, Continuation.PAUSE_WAIT, true, Exit.NONE),
                Arguments.of(0x0010, 0, true, Continuation.PAUSE_WAIT, true, Exit.NONE),
                Arguments.of(0, 0x7070, true, Continuation.PAUSE_WAIT, true, Exit.NONE),
                Arguments.of(0x7070, 0, false, Continuation.PAUSE_WAIT, true, Exit.NONE),
                Arguments.of(0x7070, 0x0080, false, Continuation.ORDINARY_WAIT, false, Exit.NONE));
    }

    @Test void slowMotionRunsOneBodyWhilePausedAndThenReentersPauseWithoutAnotherStart() {
        var pause = enteredPause();
        pause.acceptSample(sample(0x1000, 0), true);
        assertTrue(pause.gamePaused());
        assertEquals(Continuation.ORDINARY_WAIT, pause.continuation());
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(3));
        pause.acceptSample(sample(0, 0), true);
        assertTrue(pause.gamePaused());
        assertTrue(pause.bodyReady());
        assertEquals(Exit.NONE, pause.completeBody());
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(3));
        assertTrue(pause.gamePaused());
    }

    @Test void titleIntentIsCapturedBeforeTheFollowingOrdinaryBodyAndReturnedOnlyAfterIt() {
        var pause = enteredPause();
        pause.acceptSample(sample(0x4040, 0), true);
        var pending = pause.capture();
        assertEquals(Exit.TITLE_SCREEN, pause.pendingExit());
        assertFalse(pause.gamePaused());
        assertThrows(IllegalStateException.class, pause::completeBody);
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(3));
        pause.acceptSample(sample(0, 0x8000), true);
        var bodyReady = pause.capture();
        assertEquals(Exit.TITLE_SCREEN, pause.completeBody());
        assertEquals(Continuation.EXITED, pause.continuation());
        assertThrows(IllegalStateException.class, () -> pause.beginWait(3));

        pause.restore(pending);
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(0));
        pause.acceptSample(sample(0, 0x8000), true);
        assertEquals(bodyReady, pause.capture());
        assertEquals(Exit.TITLE_SCREEN, pause.completeBody());
    }

    @Test void capturedUnseededPauseAndCurrentResumeContinuationsRestoreAndReplay() {
        var pause = new Sonic2CompetitionPause();
        var registry = new RewindRegistry();
        registry.register(pause);
        var unseeded = registry.capture();
        pause.seedFromNativeWait(sample(0, 0x8080));
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(3));
        var awaiting = registry.capture();
        var expected = resumeSequence(pause);
        assertEquals(Continuation.ENTRY, pause.continuation());

        registry.restore(awaiting);
        assertEquals(Continuation.AWAITING_PAUSE_SAMPLE, pause.continuation());
        assertEquals(0x8080, pause.player2ControllerWord());
        assertEquals(expected, resumeSequence(pause));
        registry.restore(unseeded);
        assertEquals(Continuation.UNSEEDED, pause.continuation());
        assertThrows(IllegalStateException.class, () -> pause.beginWait(3));
        pause.seedFromNativeWait(sample(0x8000, 0));
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(3));
        pause.resetForMissingSnapshot();
        assertEquals(new Sonic2CompetitionPause().capture(), pause.capture());
        assertEquals("sonic2.competition.pause", pause.key());
    }

    @Test void invalidWordsAndLifecycleCallsFailWithoutChangingTheRetainedState() {
        assertThrows(IllegalArgumentException.class, () -> new State(0x10000, 0, false, Continuation.ENTRY, Exit.NONE));
        assertThrows(IllegalArgumentException.class, () -> new State(0, -1, false, Continuation.ENTRY, Exit.NONE));
        assertThrows(IllegalArgumentException.class, () -> new State(1, 0, false, Continuation.UNSEEDED, Exit.NONE));
        assertThrows(IllegalArgumentException.class, () -> new State(0, 0, false, Continuation.PAUSE_WAIT, Exit.NONE));
        assertThrows(IllegalArgumentException.class, () -> new State(0, 0, false, Continuation.EXITED, Exit.NONE));
        assertThrows(IllegalArgumentException.class, () -> new State(0, 0, true, Continuation.ORDINARY_WAIT, Exit.TITLE_SCREEN));
        assertThrows(IllegalArgumentException.class, () -> new State(0, 0, false, Continuation.ENTRY, Exit.TITLE_SCREEN));
        assertThrows(NullPointerException.class, () -> new State(0, 0, false, null, Exit.NONE));
        assertThrows(NullPointerException.class, () -> new State(0, 0, false, Continuation.ENTRY, null));
        // Stale native held/pressed combinations are legal and never normalized on restore.
        var stale = new State(0x00FF, 0xFF00, true, Continuation.ENTRY, Exit.NONE);
        var pause = new Sonic2CompetitionPause();
        pause.restore(stale);
        assertThrows(NullPointerException.class, () -> pause.restore(null));
        assertThrows(IndexOutOfBoundsException.class, () -> pause.controllerWord(2));
        assertEquals(stale, pause.capture());
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(3));
        var awaiting = pause.capture();
        assertThrows(IllegalStateException.class, () -> pause.beginWait(3));
        assertThrows(NullPointerException.class, () -> pause.acceptSample(null, false));
        assertThrows(NullPointerException.class, () -> pause.acceptSample(sampleWithNullP2(), false));
        assertEquals(awaiting, pause.capture());
        pause.acceptSample(sample(0, 0x0080), false);
        var resumed = pause.capture();
        assertThrows(IllegalStateException.class, () -> pause.acceptSample(sample(0, 0), false));
        assertThrows(IllegalStateException.class, pause::completeBody);
        assertEquals(resumed, pause.capture());
        pause.reset();
        var unseeded = pause.capture();
        assertThrows(NullPointerException.class, () -> pause.seedFromNativeWait(sampleWithNullP2()));
        assertEquals(unseeded, pause.capture());
    }

    private static List<State> resumeSequence(Sonic2CompetitionPause pause) {
        var states = new ArrayList<State>();
        pause.acceptSample(sample(0x8000, 0x8000), false);
        states.add(pause.capture());
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(0));
        states.add(pause.capture());
        pause.acceptSample(sample(0, 0x0080), false);
        states.add(pause.capture());
        assertEquals(Wait.ORDINARY_WAIT, pause.beginWait(0));
        states.add(pause.capture());
        pause.acceptSample(sample(0x0400, 0x0800), false);
        states.add(pause.capture());
        assertEquals(Exit.NONE, pause.completeBody());
        states.add(pause.capture());
        return List.copyOf(states);
    }

    private static Sonic2CompetitionPause enteredPause() {
        var pause = seeded(0x8080, 0);
        assertEquals(Wait.PAUSE_WAIT, pause.beginWait(3));
        return pause;
    }

    private static Sonic2CompetitionPause seeded(int one, int two) {
        var pause = new Sonic2CompetitionPause();
        pause.seedFromNativeWait(sample(one, two));
        return pause;
    }

    private static LogicalInputSnapshot sample(int one, int two) {
        return LogicalInputSnapshot.ofPlayers(pad(one), pad(two));
    }

    private static PlayerInputState pad(int word) {
        int held = word >>> 8, pressed = word & 0xFF;
        return PlayerInputState.of(held & 15, pressed & 15, actions(held), actions(pressed),
                (held & 0x80) != 0, (pressed & 0x80) != 0);
    }

    private static int actions(int mdByte) {
        return ((mdByte & 0x40) != 0 ? InputActionMasks.ACTION_A : 0)
                | ((mdByte & 0x10) != 0 ? InputActionMasks.ACTION_B : 0)
                | ((mdByte & 0x20) != 0 ? InputActionMasks.ACTION_C : 0);
    }

    private static LogicalInputSnapshot sampleWithNullP2() {
        // The public record's canonical constructor permits nulls; the planner
        // must reject the whole sample before publishing P1 alone.
        return new LogicalInputSnapshot(pad(0xFFFF), null, false, false, false, false,
                false, false, false, false, false, false, false, false, false);
    }
}
