package com.openggf.tools;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
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
 * Deterministic per-frame gameplay drive shared by headless trace tests and the
 * headless trace-capture tool. Delegates frame-level ordering to
 * {@link LevelFrameStep} and team updates to {@code SpriteManager.update(...)},
 * driving input through a synthetic {@link InputHandler} so both consumers use
 * the exact same production update path.
 *
 * <p>This is the engine-side extraction of the BK2-recording playback +
 * {@code stepFrame} logic that lived in the test-only {@code HeadlessTestRunner};
 * the runner now delegates to this driver so capture and tests cannot drift.
 *
 * <p>All replay state (input keys, P1 Start edge, BK2 cursor, frame counter)
 * lives here so the capture tool reproduces the trace-faithful trajectory the
 * trace-replay tests validate, rather than the live-playback {@code GameLoop}
 * path which omits P2/sidekick input plumbing and the explicit phase loop.
 */
public final class RecordingFrameDriver {

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
    private boolean p1StartHeldPrev = false;

    // BK2 recording playback fields
    private Bk2Movie bk2Movie;
    private int bk2StartIndex;
    private int currentBk2Index;

    public RecordingFrameDriver(AbstractPlayableSprite sprite) {
        this.sprite = sprite;
        this.levelManager = GameServices.level();
    }

    public AbstractPlayableSprite getSprite() {
        return sprite;
    }

    public int getFrameCounter() {
        return frameCounter;
    }

    // ---- core frame step ----

    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump) {
        stepFrame(up, down, left, right, jump, /* p2Mask */ 0, /* p2Start */ false, /* p1Start */ false);
    }

    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump,
                          boolean p1Start) {
        stepFrame(up, down, left, right, jump, /* p2Mask */ 0, /* p2Start */ false, p1Start);
    }

    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump,
                          int p2Mask, boolean p2Start) {
        stepFrame(up, down, left, right, jump, p2Mask, p2Start, /* p1Start */ false);
    }

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
        LevelFrameContext context = LevelFrameContext.from(SessionManager.getCurrentGameplayMode());
        LevelFrameStep.updateTimers(context);
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
                context, levelManager, GameServices.camera(),
                () -> GameServices.sprites().update(inputHandler),
                startEdge, LevelFrameStep.DIRECT_WRAPPER);
        inputHandler.update();
    }

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

    public void stepIdleFrames(int frames) {
        for (int i = 0; i < frames; i++) {
            stepFrame(false, false, false, false, false);
        }
    }

    // ---- BK2 recording playback ----

    public void setBk2Movie(Bk2Movie movie, int bk2FrameOffset) {
        this.bk2Movie = movie;
        this.bk2StartIndex = bk2FrameOffset;
        this.currentBk2Index = bk2StartIndex;
    }

    public boolean hasBk2Movie() {
        return bk2Movie != null;
    }

    public int stepFrameFromRecording() {
        requireMovie();
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

        boolean p1Start = frameInput.p1StartPressed();

        applyP1ActionPressEdge(currentBk2Index);
        stepFrame(up, down, left, right, jump, p2Mask, p2Start, p1Start);
        currentBk2Index++;

        return mask;
    }

    public int stepFrameFromRecordingUsingPreviousInput() {
        requireMovie();
        if (currentBk2Index >= bk2Movie.getFrameCount()) {
            throw new IllegalStateException(
                    "BK2 movie exhausted at index " + currentBk2Index
                    + " (movie has " + bk2Movie.getFrameCount() + " frames)");
        }
        if (currentBk2Index <= 0) {
            return stepFrameFromRecording();
        }

        Bk2FrameInput validationInput = bk2Movie.getFrame(currentBk2Index);
        Bk2FrameInput driveInput = bk2Movie.getFrame(currentBk2Index - 1);
        int validationMask = inputMask(validationInput);
        int driveMask = inputMask(driveInput);
        int p2Mask = p2InputMask(driveInput);

        applyP1ActionPressEdge(currentBk2Index - 1);
        stepFrame(
                (driveMask & AbstractPlayableSprite.INPUT_UP) != 0,
                (driveMask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                (driveMask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                (driveMask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                (driveMask & AbstractPlayableSprite.INPUT_JUMP) != 0,
                p2Mask,
                driveInput.p2StartPressed(),
                driveInput.p1StartPressed());
        currentBk2Index++;

        return validationMask;
    }

    private void applyP1ActionPressEdge(int bk2Index) {
        if (hasNewP1ActionPressForLogicalInput(bk2Movie, bk2Index, sprite.isControlLocked())) {
            sprite.setForcedJumpPress(true);
        }
    }

    public static boolean hasNewP1ActionPressForLogicalInput(Bk2Movie movie, int bk2Index, boolean controlLocked) {
        return !controlLocked && hasNewP1ActionPress(movie, bk2Index);
    }

    public static boolean hasNewP1ActionPress(Bk2Movie movie, int bk2Index) {
        if (movie == null || bk2Index < 0 || bk2Index >= movie.getFrameCount()) {
            return false;
        }
        int currentAction = movie.getFrame(bk2Index).p1ActionMask();
        int previousAction = bk2Index > 0
                ? movie.getFrame(bk2Index - 1).p1ActionMask()
                : 0;
        return (currentAction & ~previousAction) != 0;
    }

    public int skipFrameFromRecording() {
        requireMovie();
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

        p1StartHeldPrev = frameInput.p1StartPressed();
        currentBk2Index++;
        levelManager.getObjectManager().advanceVblaCounter();
        return mask;
    }

    private static int inputMask(Bk2FrameInput frameInput) {
        int mask = frameInput.p1InputMask();
        if (frameInput.p1ActionMask() != 0) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }
        return mask;
    }

    private static int p2InputMask(Bk2FrameInput frameInput) {
        int mask = frameInput.p2InputMask();
        if (frameInput.p2ActionMask() != 0) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }
        return mask;
    }

    public int consumeRecordingFrameInputOnly() {
        requireMovie();
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

    public int getRecordingFramesRemaining() {
        if (bk2Movie == null) {
            return 0;
        }
        return bk2Movie.getFrameCount() - currentBk2Index;
    }

    public void advanceRecordingCursor(int frameCount) {
        if (frameCount <= 0) {
            return;
        }
        requireMovie();
        int targetIndex = currentBk2Index + frameCount;
        if (targetIndex > bk2Movie.getFrameCount()) {
            throw new IllegalStateException(
                    "BK2 movie exhausted while advancing cursor to index " + targetIndex
                            + " (movie has " + bk2Movie.getFrameCount() + " frames)");
        }
        currentBk2Index = targetIndex;
    }

    private void requireMovie() {
        if (bk2Movie == null) {
            throw new IllegalStateException("No BK2 movie loaded. Call setBk2Movie() first.");
        }
    }
}
