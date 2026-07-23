package com.openggf.tools;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.InputBindingFactory;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameServices;
import com.openggf.game.InLevelTitleCardCoordinator;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Deterministic per-frame gameplay drive shared by headless trace tests and the
 * headless trace-capture tool. Delegates frame-level ordering to
 * {@link LevelFrameStep} and team updates to {@code SpriteManager.update(...)},
 * driving input through logical snapshots on an {@link InputHandler} so both
 * consumers use the exact same production update path.
 *
 * <p>This is the engine-side extraction of the BK2-recording playback +
 * {@code stepFrame} logic that lived in the test-only {@code HeadlessTestRunner};
 * the runner now delegates to this driver so capture and tests cannot drift.
 *
 * <p>All replay state (logical snapshots, BK2 cursor, frame counter) lives here
 * so the capture tool reproduces the trace-faithful trajectory the
 * trace-replay tests validate, rather than the live-playback {@code GameLoop}
 * path which omits P2/sidekick input plumbing and the explicit phase loop.
 */
public final class RecordingFrameDriver {

    private final AbstractPlayableSprite sprite;
    private final LevelManager levelManager;
    private final InputHandler inputHandler = new InputHandler(InputBindingFactory.supplier(GameServices.configuration()));

    private int frameCounter = 0;
    private LogicalInputSnapshot previousDriverSnapshot = LogicalInputSnapshot.neutral();

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
        stepFrame(driverSnapshot(up, down, left, right, jump, p2Mask, p2Start, p1Start));
    }

    private void stepFrame(LogicalInputSnapshot snapshot) {
        frameCounter++;
        try {
            updateActiveTitleCardOverlay();
            if (applyPendingSeamlessTransition()) {
                return;
            }
            startPendingInLevelTitleCardIfRequested();
            LevelFrameContext context = LevelFrameContext.from(SessionManager.getCurrentGameplayMode());
            LevelFrameStep.updateTimers(context);
            inputHandler.setLogicalOverride(snapshot);
            GameServices.sprites().publishHeldInputForLevelEvents(inputHandler);
            LevelFrameStep.executeWithPause(
                    context, levelManager, GameServices.camera(),
                    () -> GameServices.sprites().update(inputHandler),
                    snapshot.player1().startPressed(), LevelFrameStep.DIRECT_WRAPPER);
        } finally {
            inputHandler.clearLogicalOverride();
            inputHandler.update();
            previousDriverSnapshot = snapshot;
        }
    }

    public void primeInputState(Bk2FrameInput frameInput) {
        if (frameInput == null) {
            return;
        }
        previousDriverSnapshot = RecordedInputSnapshots.fromBk2(frameInput, null);
        inputHandler.setLogicalOverride(previousDriverSnapshot);
        inputHandler.update();
        inputHandler.clearLogicalOverride();
    }

    private void updateActiveTitleCardOverlay() {
        TitleCardProvider titleCardProvider = GameServices.module().getTitleCardProvider();
        if (titleCardProvider != null && titleCardProvider.isOverlayActive()) {
            titleCardProvider.update();
            if (titleCardProvider.ownsInLevelPlayerControlLock()) {
                applyInLevelTitleCardControlLock(
                        titleCardProvider.shouldLockPlayerControlForInLevelOverlay());
            }
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
        InLevelTitleCardCoordinator.startIfRequested(
                levelManager, GameServices.module().getTitleCardProvider(),
                GameServices.gameState().isEndOfLevelActive(),
                this::applyInLevelTitleCardControlLock);
    }

    private void applyInLevelTitleCardControlLock(boolean locked) {
        for (var sprite : GameServices.sprites().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable) {
                playable.setControlLocked(locked);
            }
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
        Bk2FrameInput previousInput = previousBk2Input(currentBk2Index);
        int mask = inputMask(frameInput);

        applyP1ActionPressEdge(currentBk2Index);
        stepFrame(RecordedInputSnapshots.fromBk2(frameInput, previousInput));
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

        applyP1ActionPressEdge(currentBk2Index - 1);
        stepFrame(RecordedInputSnapshots.fromBk2(driveInput, previousBk2Input(currentBk2Index - 1)));
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

        previousDriverSnapshot = RecordedInputSnapshots.fromBk2(frameInput, previousBk2Input(currentBk2Index));
        currentBk2Index++;
        // S3K's in-level title-card wait runs Process_Sprites while the level
        // gameplay counter is held at zero. Such rows are VBlank-only to the
        // physics driver, but the title-card parent/children still dispatch.
        // Keep this overlay-only work moving without ticking player physics.
        updateHeldCounterTitleCardOverlay();
        if (levelManager.hasPendingInLevelTitleCardHeldCounterDispatch()) {
            startPendingInLevelTitleCardIfRequested();
        }
        var levelEvents = GameServices.module().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.advanceVblankOnlyState();
        }
        levelManager.getObjectManager().advanceVblaCounter();
        return mask;
    }

    public void advancePlayableAnimationsOnly() {
        var sprites = GameServices.sprites();
        int animationFrame = sprites.getFrameCounter();
        for (var candidate : sprites.getAllSprites()) {
            if (candidate instanceof AbstractPlayableSprite playable) {
                playable.getAnimationManager().update(animationFrame);
            }
        }
    }

    public void suppressFirstSidekickAnimationOnce() {
        var sprites = GameServices.sprites();
        if (!sprites.getSidekicks().isEmpty()) {
            sprites.getSidekicks().getFirst().getAnimationManager().suppressNextUpdate();
        }
    }

    private void updateHeldCounterTitleCardOverlay() {
        TitleCardProvider titleCardProvider = GameServices.module().getTitleCardProvider();
        if (titleCardProvider != null && titleCardProvider.advancesOnHeldLevelCounter()) {
            titleCardProvider.update();
            if (titleCardProvider.ownsRetainedResultsHeldLevelCounter()) {
                var levelEvents = GameServices.module().getLevelEventProvider();
                if (levelEvents != null) {
                    // The retained Obj_LevelResults -> Obj_TitleCard path still
                    // runs fixed SST entries while Level_frame_counter is held.
                    levelEvents.updateFixedInLevelObjects();
                }
            }
            if (titleCardProvider.ownsInLevelPlayerControlLock()) {
                applyInLevelTitleCardControlLock(
                        titleCardProvider.shouldLockPlayerControlForInLevelOverlay());
            }
        }
    }

    private static int inputMask(Bk2FrameInput frameInput) {
        int mask = frameInput.p1InputMask();
        if (frameInput.p1ActionMask() != 0) {
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
        int mask = inputMask(frameInput);
        applyP1ActionPressEdge(currentBk2Index);
        previousDriverSnapshot =
                RecordedInputSnapshots.fromBk2(frameInput, previousBk2Input(currentBk2Index));
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

    private LogicalInputSnapshot driverSnapshot(boolean up, boolean down, boolean left, boolean right, boolean jump,
                                                int p2Mask, boolean p2Start, boolean p1Start) {
        int p1DirectionHeld = directionMask(up, down, left, right);
        int p1ActionHeld = jump ? InputActionMasks.ACTION_A : 0;
        int p2DirectionHeld = p2Mask & directionBits();
        int p2ActionHeld = (p2Mask & AbstractPlayableSprite.INPUT_JUMP) != 0
                ? InputActionMasks.ACTION_A
                : 0;
        PlayerInputState previousP1 = previousDriverSnapshot.player1();
        PlayerInputState previousP2 = previousDriverSnapshot.player2();
        PlayerInputState p1 = PlayerInputState.of(
                p1DirectionHeld,
                p1DirectionHeld & ~previousP1.heldMask(),
                p1ActionHeld,
                p1ActionHeld & ~previousP1.actionHeldMask(),
                p1Start,
                p1Start && !previousP1.startHeld());
        PlayerInputState p2 = PlayerInputState.of(
                p2DirectionHeld,
                p2DirectionHeld & ~previousP2.heldMask(),
                p2ActionHeld,
                p2ActionHeld & ~previousP2.actionHeldMask(),
                p2Start,
                p2Start && !previousP2.startHeld());
        return LogicalInputSnapshot.ofPlayers(p1, p2);
    }

    private Bk2FrameInput previousBk2Input(int index) {
        return index > 0 ? bk2Movie.getFrame(index - 1) : null;
    }

    private static int directionMask(boolean up, boolean down, boolean left, boolean right) {
        int mask = 0;
        if (up) {
            mask |= AbstractPlayableSprite.INPUT_UP;
        }
        if (down) {
            mask |= AbstractPlayableSprite.INPUT_DOWN;
        }
        if (left) {
            mask |= AbstractPlayableSprite.INPUT_LEFT;
        }
        if (right) {
            mask |= AbstractPlayableSprite.INPUT_RIGHT;
        }
        return mask;
    }

    private static int directionBits() {
        return AbstractPlayableSprite.INPUT_UP
                | AbstractPlayableSprite.INPUT_DOWN
                | AbstractPlayableSprite.INPUT_LEFT
                | AbstractPlayableSprite.INPUT_RIGHT;
    }
}
