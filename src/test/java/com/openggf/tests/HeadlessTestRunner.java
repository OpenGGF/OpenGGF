package com.openggf.tests;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.RecordingFrameDriver;

/**
 * Headless test runner that simulates the full game loop update cycle.
 * Delegates to the shared engine-side {@link RecordingFrameDriver}, which
 * handles frame-level ordering via {@code LevelFrameStep} and team updates via
 * {@code SpriteManager.update(...)}, ensuring the test harness, the trace-replay
 * tests, and the headless trace-capture tool cannot drift from production
 * team behavior (or from each other).
 *
 * <p>Usage example:
 * <pre>
 * HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
 * runner.stepFrame(false, false, true, false, false); // Walk left
 * </pre>
 *
 * <p>Important setup requirements for tests using this class:
 * <ul>
 *   <li>Reset test state: TestEnvironment.resetAll()</li>
 *   <li>Initialize headless graphics: GameServices.graphics().initHeadless()</li>
 *   <li>Load level: GameServices.level().loadZoneAndAct(zone, act)</li>
 *   <li>Fix GroundSensor: GroundSensor.setLevelManager(GameServices.level())</li>
 *   <li>Update camera position: GameServices.camera().updatePosition(true)</li>
 * </ul>
 */
public class HeadlessTestRunner {

    private final RecordingFrameDriver driver;

    /**
     * Creates a new HeadlessTestRunner for the given sprite.
     *
     * @param sprite The playable sprite to run physics updates on
     */
    public HeadlessTestRunner(AbstractPlayableSprite sprite) {
        this.driver = new RecordingFrameDriver(sprite);
    }

    /**
     * Steps one frame with the given input state.
     * Frame-level ordering is defined by {@code LevelFrameStep#execute};
     * playable/team ordering is defined by {@code SpriteManager.update(...)}.
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump) {
        driver.stepFrame(up, down, left, right, jump);
    }

    /**
     * Convenience overload that also carries P1 Start, used by tests exercising
     * ROM in-game pause.
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump,
                          boolean p1Start) {
        driver.stepFrame(up, down, left, right, jump, p1Start);
    }

    /**
     * Steps one frame with both P1 and P2 input.
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump,
                          int p2Mask, boolean p2Start) {
        driver.stepFrame(up, down, left, right, jump, p2Mask, p2Start);
    }

    /**
     * Full-input step overload, including P1 Start so ROM in-game pause can be
     * exercised from both unit tests and BK2 replay.
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump,
                          int p2Mask, boolean p2Start, boolean p1Start) {
        driver.stepFrame(up, down, left, right, jump, p2Mask, p2Start, p1Start);
    }

    /**
     * Primes the synthetic input handler to the state at an already-restored
     * playback frame.
     */
    public void primeInputState(Bk2FrameInput frameInput) {
        driver.primeInputState(frameInput);
    }

    /**
     * Steps multiple frames with no input (idle).
     */
    public void stepIdleFrames(int frames) {
        driver.stepIdleFrames(frames);
    }

    /** Gets the current frame counter. */
    public int getFrameCounter() {
        return driver.getFrameCounter();
    }

    /** Gets the sprite being controlled. */
    public AbstractPlayableSprite getSprite() {
        return driver.getSprite();
    }

    // ---- BK2 recording playback ----

    /**
     * Sets the BK2 movie for recording playback.
     *
     * @param movie          The parsed BK2 movie
     * @param bk2FrameOffset The 0-based emulation frame index where trace recording began
     *                       (from BizHawk's emu.framecount() in the Lua script).
     */
    public void setBk2Movie(Bk2Movie movie, int bk2FrameOffset) {
        driver.setBk2Movie(movie, bk2FrameOffset);
    }

    /**
     * Steps one frame using input from the BK2 movie recording.
     *
     * @return The raw input mask used (for trace input validation)
     */
    public int stepFrameFromRecording() {
        return driver.stepFrameFromRecording();
    }

    /**
     * Consumes the current BK2 row for validation but drives gameplay with the
     * previous BK2 row.
     */
    public int stepFrameFromRecordingUsingPreviousInput() {
        return driver.stepFrameFromRecordingUsingPreviousInput();
    }

    static boolean hasNewP1ActionPressForLogicalInput(Bk2Movie movie, int bk2Index, boolean controlLocked) {
        return RecordingFrameDriver.hasNewP1ActionPressForLogicalInput(movie, bk2Index, controlLocked);
    }

    static boolean hasNewP1ActionPress(Bk2Movie movie, int bk2Index) {
        return RecordingFrameDriver.hasNewP1ActionPress(movie, bk2Index);
    }

    /**
     * Advances the BK2 movie by one frame without processing physics.
     *
     * @return The raw input mask for that frame (for trace input validation)
     */
    public int skipFrameFromRecording() {
        return driver.skipFrameFromRecording();
    }

    /**
     * Consumes one BK2 input frame without mutating gameplay or timing counters.
     */
    public int consumeRecordingFrameInputOnly() {
        return driver.consumeRecordingFrameInputOnly();
    }

    /**
     * Returns the BK2 input mask at the given offset from the current cursor
     * without advancing the cursor or stepping gameplay.
     */
    public int peekRecordingInputAt(int offset) {
        return driver.peekRecordingInputAt(offset);
    }

    /**
     * Returns the number of BK2 recording frames remaining.
     */
    public int getRecordingFramesRemaining() {
        return driver.getRecordingFramesRemaining();
    }

    /**
     * Advances the BK2 cursor without mutating gameplay state.
     */
    public void advanceRecordingCursor(int frameCount) {
        driver.advanceRecordingCursor(frameCount);
    }
}
