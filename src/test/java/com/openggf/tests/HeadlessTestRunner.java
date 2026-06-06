package com.openggf.tests;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * Headless test runner that simulates the full game loop update cycle.
 * Delegates to {@link LevelFrameStep} for frame-level ordering and
 * {@code SpriteManager.update(...)} for playable updates, ensuring the
 * test harness cannot drift from production team behavior.
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
    private final AbstractPlayableSprite sprite;
    private final LevelManager levelManager;
    private final InputHandler inputHandler = new InputHandler();
    private final int upKey = GameServices.configuration().getInt(SonicConfiguration.UP);
    private final int downKey = GameServices.configuration().getInt(SonicConfiguration.DOWN);
    private final int leftKey = GameServices.configuration().getInt(SonicConfiguration.LEFT);
    private final int rightKey = GameServices.configuration().getInt(SonicConfiguration.RIGHT);
    private final int jumpKey = GameServices.configuration().getInt(SonicConfiguration.JUMP);
    private final int p2UpKey = GameServices.configuration().getInt(SonicConfiguration.P2_UP);
    private final int p2DownKey = GameServices.configuration().getInt(SonicConfiguration.P2_DOWN);
    private final int p2LeftKey = GameServices.configuration().getInt(SonicConfiguration.P2_LEFT);
    private final int p2RightKey = GameServices.configuration().getInt(SonicConfiguration.P2_RIGHT);
    private final int p2JumpKey = GameServices.configuration().getInt(SonicConfiguration.P2_JUMP);
    private final int p2StartKey = GameServices.configuration().getInt(SonicConfiguration.P2_START);
    private int frameCounter = 0;

    /**
     * Previous-frame P1 Start state, used to derive the leading-edge Start press
     * that drives ROM in-game pause ({@code Ctrl_1_pressed} & Start). Tracked
     * purely from the input stream (live keys or BK2 movie) — never from trace
     * data — so replay stays comparison-only.
     */
    private boolean p1StartHeldPrev = false;

    // BK2 recording playback fields
    private Bk2Movie bk2Movie;
    private int bk2StartIndex;
    private int currentBk2Index;

    /**
     * Creates a new HeadlessTestRunner for the given sprite.
     *
     * @param sprite The playable sprite to run physics updates on
     */
    public HeadlessTestRunner(AbstractPlayableSprite sprite) {
        this.sprite = sprite;
        this.levelManager = GameServices.level();
    }

    /**
     * Steps one frame with the given input state.
     * Frame-level ordering is defined by {@link LevelFrameStep#execute};
     * playable/team ordering is defined by {@code SpriteManager.update(...)}.
     *
     * @param up    Up input pressed
     * @param down  Down input pressed
     * @param left  Left input pressed
     * @param right Right input pressed
     * @param jump  Jump input pressed
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump) {
        stepFrame(up, down, left, right, jump, /* p2Mask */ 0, /* p2Start */ false);
    }

    /**
     * Convenience overload that also carries P1 Start, used by tests exercising
     * ROM in-game pause. Equivalent to the 7-arg overload with no P2 input and
     * the given P1 Start state.
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump,
                          boolean p1Start) {
        stepFrame(up, down, left, right, jump, /* p2Mask */ 0, /* p2Start */ false, p1Start);
    }

    /**
     * Steps one frame with both P1 and P2 input. The P2 input is delivered by
     * pressing/releasing the configured P2 keybindings in the same shared
     * {@link InputHandler} instance, so {@code SpriteManager.update} reads it
     * via the same path it uses in interactive play.
     *
     * @param up      P1 Up
     * @param down    P1 Down
     * @param left    P1 Left
     * @param right   P1 Right
     * @param jump    P1 Jump (A/B/C)
     * @param p2Mask  P2 input mask combining {@code INPUT_UP/DOWN/LEFT/RIGHT/JUMP}
     * @param p2Start whether P2 Start is pressed
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump,
                          int p2Mask, boolean p2Start) {
        stepFrame(up, down, left, right, jump, p2Mask, p2Start, /* p1Start */ false);
    }

    /**
     * Full-input step overload, including P1 Start so ROM in-game pause can be
     * exercised from both unit tests and BK2 replay. The Start <em>edge</em>
     * (released→pressed) is derived here from {@code p1Start} versus the previous
     * frame's Start state and fed to {@link LevelFrameStep#executeWithPause}; when
     * the resulting state is paused the level update is skipped while the frame
     * counter and input still advance — exactly as ROM {@code Pause_Loop} runs
     * only the V-int.
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump,
                          int p2Mask, boolean p2Start, boolean p1Start) {
        frameCounter++;
        boolean startEdge = p1Start && !p1StartHeldPrev;
        p1StartHeldPrev = p1Start;
        updateActiveTitleCardOverlay();
        if (applyPendingSeamlessTransition()) {
            return;
        }
        startPendingInLevelTitleCardIfRequested();
        GameServices.timers().update();
        setKeyState(upKey, up);
        setKeyState(downKey, down);
        setKeyState(leftKey, left);
        setKeyState(rightKey, right);
        setKeyState(jumpKey, jump);
        setKeyState(p2UpKey, (p2Mask & AbstractPlayableSprite.INPUT_UP) != 0);
        setKeyState(p2DownKey, (p2Mask & AbstractPlayableSprite.INPUT_DOWN) != 0);
        setKeyState(p2LeftKey, (p2Mask & AbstractPlayableSprite.INPUT_LEFT) != 0);
        setKeyState(p2RightKey, (p2Mask & AbstractPlayableSprite.INPUT_RIGHT) != 0);
        setKeyState(p2JumpKey, (p2Mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
        setKeyState(p2StartKey, p2Start);

        GameServices.sprites().publishHeldInputForLevelEvents(inputHandler);
        LevelFrameStep.executeWithPause(
                LevelFrameContext.from(SessionManager.getCurrentGameplayMode()),
                levelManager, GameServices.camera(),
                () -> GameServices.sprites().update(inputHandler),
                startEdge, LevelFrameStep.DIRECT_WRAPPER);
        inputHandler.update();
    }

    /**
     * Primes the synthetic input handler to the state at an already-restored
     * playback frame. This preserves just-pressed edge detection when rewind
     * restores an exact keyframe and then replays the following frame.
     */
    public void primeInputState(Bk2FrameInput frameInput) {
        if (frameInput == null) {
            return;
        }
        int p1Mask = frameInput.p1InputMask();
        setKeyState(upKey, (p1Mask & AbstractPlayableSprite.INPUT_UP) != 0);
        setKeyState(downKey, (p1Mask & AbstractPlayableSprite.INPUT_DOWN) != 0);
        setKeyState(leftKey, (p1Mask & AbstractPlayableSprite.INPUT_LEFT) != 0);
        setKeyState(rightKey, (p1Mask & AbstractPlayableSprite.INPUT_RIGHT) != 0);
        setKeyState(jumpKey, (p1Mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
        int p2Mask = frameInput.p2InputMask();
        setKeyState(p2UpKey, (p2Mask & AbstractPlayableSprite.INPUT_UP) != 0);
        setKeyState(p2DownKey, (p2Mask & AbstractPlayableSprite.INPUT_DOWN) != 0);
        setKeyState(p2LeftKey, (p2Mask & AbstractPlayableSprite.INPUT_LEFT) != 0);
        setKeyState(p2RightKey, (p2Mask & AbstractPlayableSprite.INPUT_RIGHT) != 0);
        setKeyState(p2JumpKey, (p2Mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
        setKeyState(p2StartKey, frameInput.p2StartPressed());
        inputHandler.update();
    }

    private void setKeyState(int keyCode, boolean pressed) {
        inputHandler.handleKeyEvent(keyCode, pressed ? GLFW_PRESS : GLFW_RELEASE);
    }

    private void updateActiveTitleCardOverlay() {
        TitleCardProvider titleCardProvider = GameServices.module().getTitleCardProvider();
        if (titleCardProvider != null && titleCardProvider.isOverlayActive()) {
            titleCardProvider.update();
        }
    }

    private boolean applyPendingSeamlessTransition() {
        SeamlessLevelTransitionRequest seamlessRequest = levelManager.consumeSeamlessTransitionRequest();
        if (seamlessRequest == null) {
            return false;
        }
        levelManager.applySeamlessTransition(seamlessRequest);
        startPendingInLevelTitleCardIfRequested();
        return true;
    }

    private void startPendingInLevelTitleCardIfRequested() {
        if (!levelManager.consumeInLevelTitleCardRequest()) {
            return;
        }
        TitleCardProvider titleCardProvider = GameServices.module().getTitleCardProvider();
        if (titleCardProvider != null) {
            titleCardProvider.initializeInLevel(
                    levelManager.getInLevelTitleCardZone(),
                    levelManager.getInLevelTitleCardAct());
        }
    }

    /**
     * Steps multiple frames with no input (idle).
     *
     * @param frames Number of frames to step
     */
    public void stepIdleFrames(int frames) {
        for (int i = 0; i < frames; i++) {
            stepFrame(false, false, false, false, false);
        }
    }

    /**
     * Gets the current frame counter.
     *
     * @return The number of frames stepped since creation
     */
    public int getFrameCounter() {
        return frameCounter;
    }

    /**
     * Gets the sprite being controlled.
     *
     * @return The playable sprite
     */
    public AbstractPlayableSprite getSprite() {
        return sprite;
    }

    // ---- BK2 recording playback ----

    /**
     * Sets the BK2 movie for recording playback.
     *
     * @param movie          The parsed BK2 movie
     * @param bk2FrameOffset The 0-based emulation frame index where trace recording began
     *                       (from BizHawk's emu.framecount() in the Lua script).
     *                       This is a direct index into the movie's frame list, NOT a
     *                       1-based BK2 line number.
     */
    public void setBk2Movie(Bk2Movie movie, int bk2FrameOffset) {
        this.bk2Movie = movie;
        this.bk2StartIndex = bk2FrameOffset;
        this.currentBk2Index = bk2StartIndex;

        // ROM parity note (vbla counter alignment):
        //
        // v_vbla_byte counts ALL VBlanks since power-on and never resets.
        // Objects with timing gates like (v_vbla_byte + d7) & 7 depend on
        // absolute mod-8 alignment. ObjectManager.initVblaCounter(bk2FrameOffset - 1)
        // would seed that alignment correctly here, but the cascading-slot
        // issue (engine slot indices differing from ROM slot indices)
        // makes it unsafe to enable globally — slot-dependent gates would
        // fire at wrong frames in tests that rely on the current accidental
        // mod-8 alignment of an uninitialised counter.
        //
        // Trace replay tests that need ROM-aligned vbla seed it themselves
        // via objectManager.initVblaCounter(...) after fixture build (see
        // TestS3kAizTraceReplay.buildReplayFixture). When the slot-allocation
        // parity work lands, this can be re-enabled here as the default.
    }

    /**
     * Steps one frame using input from the BK2 movie recording.
     *
     * @return The raw input mask used (for trace input validation)
     * @throws IllegalStateException if no BK2 movie is loaded or the movie is exhausted
     */
    public int stepFrameFromRecording() {
        if (bk2Movie == null) {
            throw new IllegalStateException("No BK2 movie loaded. Call setBk2Movie() first.");
        }
        if (currentBk2Index >= bk2Movie.getFrameCount()) {
            throw new IllegalStateException(
                    "BK2 movie exhausted at index " + currentBk2Index
                    + " (movie has " + bk2Movie.getFrameCount() + " frames)");
        }

        Bk2FrameInput frameInput = bk2Movie.getFrame(currentBk2Index);
        int mask = frameInput.p1InputMask();

        // BK2 separates directional inputs (p1InputMask) from action buttons (p1ActionMask).
        // The jump/A/B/C buttons are in the action mask, so OR the jump bit into the
        // returned mask to ensure trace input validation sees the complete input state.
        if (frameInput.p1ActionMask() != 0) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }

        // P2 input: directional + jump (any of A/B/C). Start handled separately.
        // ROM Ctrl_2_held is "currently pressed"; Ctrl_2_logical (just-pressed)
        // is computed downstream by InputHandler.update() comparing this frame's
        // keys against the previous frame's, so we only need to deliver "held"
        // here. SpriteManager forwards both to SidekickCpuController.
        int p2Mask = frameInput.p2InputMask();
        if (frameInput.p2ActionMask() != 0) {
            p2Mask |= AbstractPlayableSprite.INPUT_JUMP;
        }
        boolean p2Start = frameInput.p2StartPressed();

        boolean up = (mask & AbstractPlayableSprite.INPUT_UP) != 0;
        boolean down = (mask & AbstractPlayableSprite.INPUT_DOWN) != 0;
        boolean left = (mask & AbstractPlayableSprite.INPUT_LEFT) != 0;
        boolean right = (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        boolean jump = (mask & AbstractPlayableSprite.INPUT_JUMP) != 0;

        // P1 Start drives ROM in-game pause (Game_paused). Read it straight from
        // the BK2 movie row — never from the trace CSV/aux — so a paused movie
        // segment freezes the engine for exactly the paused frames and stays
        // frame-aligned. The Start edge is computed inside stepFrame().
        boolean p1Start = frameInput.p1StartPressed();

        stepFrame(up, down, left, right, jump, p2Mask, p2Start, p1Start);
        currentBk2Index++;

        return mask;
    }

    /**
     * Advances the BK2 movie by one frame without processing physics.
     * Used for lag frames where the ROM didn't execute the main game loop,
     * so the engine should not process physics either.
     *
     * @return The raw input mask for that frame (for trace input validation)
     * @throws IllegalStateException if no BK2 movie is loaded or the movie is exhausted
     */
    public int skipFrameFromRecording() {
        if (bk2Movie == null) {
            throw new IllegalStateException("No BK2 movie loaded. Call setBk2Movie() first.");
        }
        if (currentBk2Index >= bk2Movie.getFrameCount()) {
            throw new IllegalStateException(
                    "BK2 movie exhausted at index " + currentBk2Index
                    + " (movie has " + bk2Movie.getFrameCount() + " frames)");
        }

        Bk2FrameInput frameInput = bk2Movie.getFrame(currentBk2Index);
        int mask = frameInput.p1InputMask();
        if (frameInput.p1ActionMask() != 0) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }

        // Keep the Start-edge tracker in sync across lag frames so a Start press
        // straddling a lag frame is still detected as a single edge on the next
        // real gameplay frame. ROM Pause_Game lives inside LevelLoop, which does
        // not run on lag frames, so no pause toggle happens here.
        p1StartHeldPrev = frameInput.p1StartPressed();

        // Advance BK2 index without calling stepFrame() - no physics processed.
        currentBk2Index++;

        // ROM parity: v_vbla_byte increments in the VBlank handler even on lag
        // frames. Objects that use timing gates like (v_vbla_byte + d7) & 7 are
        // sensitive to this. Advance the ObjectManager's frame counter to keep
        // it aligned with v_vbla_byte.
        levelManager.getObjectManager().advanceVblaCounter();
        return mask;
    }

    /**
     * Consumes one BK2 input frame without mutating gameplay or timing counters.
     * Used when native bootstrap code has already reproduced the corresponding
     * game state, but the replay cursor still needs to move past the movie row.
     */
    public int consumeRecordingFrameInputOnly() {
        if (bk2Movie == null) {
            throw new IllegalStateException("No BK2 movie loaded. Call setBk2Movie() first.");
        }
        if (currentBk2Index >= bk2Movie.getFrameCount()) {
            throw new IllegalStateException(
                    "BK2 movie exhausted at index " + currentBk2Index
                    + " (movie has " + bk2Movie.getFrameCount() + " frames)");
        }

        Bk2FrameInput frameInput = bk2Movie.getFrame(currentBk2Index);
        int mask = frameInput.p1InputMask();
        if (frameInput.p1ActionMask() != 0) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }
        currentBk2Index++;
        return mask;
    }

    /**
     * Returns the BK2 input mask at the given offset from the current cursor
     * without advancing the cursor or stepping gameplay. Offset 0 is the next
     * frame {@link #stepFrameFromRecording} would consume; negative offsets
     * read prior BK2 frames. Returns -1 when no BK2 movie is loaded or the
     * requested frame is out of range.
     */
    public int peekRecordingInputAt(int offset) {
        if (bk2Movie == null) {
            return -1;
        }
        int targetIndex = currentBk2Index + offset;
        if (targetIndex < 0 || targetIndex >= bk2Movie.getFrameCount()) {
            return -1;
        }
        Bk2FrameInput frameInput = bk2Movie.getFrame(targetIndex);
        int mask = frameInput.p1InputMask();
        if (frameInput.p1ActionMask() != 0) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }
        return mask;
    }

    /**
     * Returns the number of BK2 recording frames remaining.
     *
     * @return Remaining frames, or 0 if no movie is loaded
     */
    public int getRecordingFramesRemaining() {
        if (bk2Movie == null) return 0;
        return bk2Movie.getFrameCount() - currentBk2Index;
    }

    /**
     * Advances the BK2 cursor without mutating gameplay state.
     * Used when replay logic elastically skips trace windows after the
     * engine reaches the matching checkpoint earlier than the recorded run.
     *
     * @param frameCount number of BK2 frames to skip
     */
    public void advanceRecordingCursor(int frameCount) {
        if (frameCount <= 0) {
            return;
        }
        if (bk2Movie == null) {
            throw new IllegalStateException("No BK2 movie loaded. Call setBk2Movie() first.");
        }
        int targetIndex = currentBk2Index + frameCount;
        if (targetIndex > bk2Movie.getFrameCount()) {
            throw new IllegalStateException(
                    "BK2 movie exhausted while advancing cursor to index " + targetIndex
                            + " (movie has " + bk2Movie.getFrameCount() + " frames)");
        }
        currentBk2Index = targetIndex;
    }
}
