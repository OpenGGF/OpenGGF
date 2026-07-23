package com.openggf.trace.live;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager.PlaybackFrameObserver;
import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.EngineDiagnostics;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceBinder;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.VerificationGroup;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Engine-side per-frame trace comparator. Attached to
 * {@link com.openggf.debug.playback.PlaybackDebugManager} as a
 * {@link PlaybackFrameObserver}; gates ROM lag frames and accumulates
 * divergences into a ring buffer plus counters.
 */
public final class LiveTraceComparator implements PlaybackFrameObserver {
    private static final int RING_CAPACITY = 5;

    private final TraceData trace;
    private final TraceBinder binder;
    private final MismatchRingBuffer mismatches = new MismatchRingBuffer(RING_CAPACITY);
    private final Supplier<AbstractPlayableSprite> spriteProvider;
    private final Runnable firstErrorCallback;
    private final Consumer<FrameComparison> perFrameObserver;

    private int cursor;
    private int errorCount;
    private int warningCount;
    private int laggedFrames;
    private boolean firstErrorLogged;
    private boolean firstWarningLogged;
    private MismatchEntry firstNonCameraPhysicsMismatch;
    private int lastActionMask;
    private int lastInputMask;
    private boolean lastStartPressed;
    private boolean complete;
    private TraceFrame currentVisualFrame;

    public LiveTraceComparator(TraceData trace,
                               ToleranceConfig tolerances,
                               int initialCursor,
                               Supplier<AbstractPlayableSprite> spriteProvider) {
        this(trace, tolerances, initialCursor, spriteProvider, null, null);
    }

    public LiveTraceComparator(TraceData trace,
                               ToleranceConfig tolerances,
                               int initialCursor,
                               Supplier<AbstractPlayableSprite> spriteProvider,
                               Runnable firstErrorCallback) {
        this(trace, tolerances, initialCursor, spriteProvider, firstErrorCallback, null);
    }

    public LiveTraceComparator(TraceData trace,
                               ToleranceConfig tolerances,
                               int initialCursor,
                               Supplier<AbstractPlayableSprite> spriteProvider,
                               Runnable firstErrorCallback,
                               Consumer<FrameComparison> perFrameObserver) {
        this.trace = trace;
        this.binder = new TraceBinder(tolerances);
        this.cursor = initialCursor;
        this.spriteProvider = spriteProvider;
        this.firstErrorCallback = firstErrorCallback;
        this.perFrameObserver = perFrameObserver;
    }

    @Override
    public boolean shouldSkipGameplayTick(Bk2FrameInput frame) {
        if (cursor >= trace.frameCount()) {
            return false;
        }
        TraceFrame current = trace.getFrame(cursor);
        TraceFrame previous = cursor > 0 ? trace.getFrame(cursor - 1) : null;
        TraceExecutionPhase phase =
                TraceReplayBootstrap.phaseForReplay(trace, previous, current);
        if (phase == TraceExecutionPhase.FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD) {
            SpriteManager sprites = GameServices.spritesOrNull();
            if (sprites != null && !sprites.getSidekicks().isEmpty()) {
                sprites.getSidekicks().getFirst().getAnimationManager().suppressNextUpdate();
            }
        }
        return phase == TraceExecutionPhase.VBLANK_ONLY
                || phase == TraceExecutionPhase.ADVANCE_ONLY
                || phase == TraceExecutionPhase.PLAYABLE_ANIMATION_ONLY;
    }

    @Override
    public boolean shouldAdvanceVblankOnSkippedTick(Bk2FrameInput frame) {
        return vblankAdvanceCountOnSkippedTick(frame) > 0;
    }

    @Override
    public int vblankAdvanceCountOnSkippedTick(Bk2FrameInput frame) {
        if (cursor >= trace.frameCount()) {
            return 1;
        }
        TraceFrame previous = cursor > 0 ? trace.getFrame(cursor - 1) : null;
        TraceFrame current = trace.getFrame(cursor);
        if (TraceReplayBootstrap.phaseForReplay(trace, previous, current)
                == TraceExecutionPhase.ADVANCE_ONLY) {
            return 0;
        }
        if (previous == null) {
            return 1;
        }
        if (previous.vblankCounter() < 0 || current.vblankCounter() < 0) {
            return 1;
        }
        return (current.vblankCounter() - previous.vblankCounter()) & 0xFFFF;
    }

    @Override
    public void afterFrameAdvanced(Bk2FrameInput frame, boolean wasSkipped) {
        lastActionMask = frame.p1ActionMask();
        lastInputMask = frame.p1InputMask();
        lastStartPressed = frame.p1StartPressed();
        if (wasSkipped) {
            TraceFrame skipped = cursor < trace.frameCount() ? trace.getFrame(cursor) : null;
            TraceFrame previous = cursor > 0 ? trace.getFrame(cursor - 1) : null;
            TraceExecutionPhase skippedPhase = skipped != null
                    ? TraceReplayBootstrap.phaseForReplay(trace, previous, skipped)
                    : TraceExecutionPhase.VBLANK_ONLY;
            if (cursor == 0 && TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(trace)) {
                TraceReplaySessionBootstrap.applyS3kCompleteRunHandoffNativePostRowEffects(trace);
            }
            if (cursor < trace.frameCount()) {
                currentVisualFrame = trace.getFrame(cursor);
            }
            if (skippedPhase == TraceExecutionPhase.PLAYABLE_ANIMATION_ONLY) {
                advancePlayableAnimationsOnly();
            } else {
                if (skippedPhase != TraceExecutionPhase.ADVANCE_ONLY) {
                    laggedFrames++;
                }
                cursor++;
                checkComplete();
                return;
            }
        }
        if (cursor >= trace.frameCount()) {
            checkComplete();
            return;
        }
        TraceFrame expected = trace.getFrame(cursor);
        currentVisualFrame = expected;
        TraceFrame previous = cursor > 0 ? trace.getFrame(cursor - 1) : null;
        TraceExecutionPhase phase =
                TraceReplayBootstrap.phaseForReplay(trace, previous, expected);
        if (!TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
            cursor++;
            checkComplete();
            return;
        }
        AbstractPlayableSprite sprite = spriteProvider.get();
        if (sprite == null) {
            cursor++;
            checkComplete();
            return;
        }
        // Pass the first sidekick's state too so the binder's
        // appendCharacterComparisons finds an actual value instead of
        // flagging every recorded sidekick field as divergent (EHZ1
        // etc. record Sonic+Tails).
        TraceCharacterState actualSidekick = captureFirstSidekickState();
        var diagnosticLevelManager = GameServices.levelOrNull();
        ObjectManager diagnosticObjectManager = diagnosticLevelManager != null
                ? diagnosticLevelManager.getObjectManager()
                : null;
        String engineFrameClock = diagnosticObjectManager != null
                ? String.format("vbc=%04X sub=(%04X,%04X)",
                        diagnosticObjectManager.getVblaCounter() & 0xFFFF,
                        sprite.getXSubpixelRaw(), sprite.getYSubpixelRaw())
                : String.format("sub=(%04X,%04X)",
                        sprite.getXSubpixelRaw(), sprite.getYSubpixelRaw());
        var diagnosticCamera = GameServices.cameraOrNull();
        int diagnosticCameraX = diagnosticCamera != null ? diagnosticCamera.getX() : -1;
        int diagnosticCameraY = diagnosticCamera != null ? diagnosticCamera.getY() : -1;
        EngineDiagnostics animationDiagnostics =
                EngineDiagnostics.formattedWithCameraAnimationAndSubpixel(
                        diagnosticCameraX, diagnosticCameraY,
                        sprite.getAnimationId(), sprite.getMappingFrame(),
                        sprite.getXSubpixelRaw(), sprite.getYSubpixelRaw(), engineFrameClock);
        FrameComparison result = binder.compareFrame(expected,
                sprite.getCentreX(), sprite.getCentreY(),
                sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                sprite.getAngle(), sprite.getAir(), sprite.getRolling(),
                sprite.getGroundMode().ordinal(),
                null, animationDiagnostics,
                "sidekick", actualSidekick);
        if (perFrameObserver != null) {
            perFrameObserver.accept(result);
        }
        absorbDivergentFields(result, expected.frame());
        cursor++;
        checkComplete();
    }

    private static void advancePlayableAnimationsOnly() {
        SpriteManager sprites = GameServices.spritesOrNull();
        if (sprites == null) {
            return;
        }
        int animationFrame = sprites.getFrameCounter();
        for (var candidate : sprites.getAllSprites()) {
            if (candidate instanceof AbstractPlayableSprite playable) {
                playable.getAnimationManager().update(animationFrame);
            }
        }
    }

    private void absorbDivergentFields(FrameComparison result, int frameNumber) {
        List<FieldComparison> divergent = result.divergentFields();
        for (FieldComparison fc : divergent) {
            Severity sev = fc.severity();
            if (sev == Severity.ERROR) {
                errorCount++;
            } else if (sev == Severity.WARNING) {
                warningCount++;
            } else {
                continue;
            }
            if (sev == Severity.ERROR && !firstErrorLogged) {
                firstErrorLogged = true;
                if (firstErrorCallback != null) {
                    firstErrorCallback.run();
                }
                System.err.printf(
                        "[LiveTraceComparator] FIRST ERROR at trace frame %d:%n"
                                + "  field=%s expected=%s actual=%s delta=%d%n"
                                + "  full frame comparison: %s%n"
                                + "  active objects near player:%n%s",
                        frameNumber,
                        fc.fieldName(),
                        fc.expected(),
                        fc.actual(),
                        fc.delta(),
                        result,
                        summariseNearbyObjects());
            }
            if (sev == Severity.WARNING && !firstWarningLogged) {
                firstWarningLogged = true;
                System.err.printf(
                        "[LiveTraceComparator] FIRST WARNING at trace frame %d:%n"
                                + "  field=%s expected=%s actual=%s delta=%d%n"
                                + "  full frame comparison: %s%n"
                                + "  active objects near player:%n%s",
                        frameNumber,
                        fc.fieldName(),
                        fc.expected(),
                        fc.actual(),
                        fc.delta(),
                        result,
                        summariseNearbyObjects());
            }
            MismatchEntry mismatch = new MismatchEntry(
                    frameNumber,
                    fc.fieldName(),
                    fc.expected(),
                    fc.actual(),
                    Integer.toString(fc.delta()),
                    sev,
                    1);
            if (firstNonCameraPhysicsMismatch == null
                    && sev == Severity.ERROR
                    && fc.verificationGroup() == VerificationGroup.PHYSICS
                    && !fc.fieldName().startsWith("camera_")) {
                firstNonCameraPhysicsMismatch = mismatch;
            }
            mismatches.push(mismatch);
        }
    }

    private String summariseNearbyObjects() {
        var level = GameServices.levelOrNull();
        ObjectManager om = level != null ? level.getObjectManager() : null;
        if (om == null) {
            return "    (no ObjectManager)";
        }
        AbstractPlayableSprite sprite = spriteProvider.get();
        int px = sprite != null ? sprite.getCentreX() & 0xFFFF : 0;
        int py = sprite != null ? sprite.getCentreY() & 0xFFFF : 0;
        StringBuilder sb = new StringBuilder();
        for (ObjectInstance inst : om.getActiveObjects()) {
            if (!(inst instanceof AbstractObjectInstance aoi)) {
                continue;
            }
            // Dynamic effects/projectiles can legitimately have a null
            // spawn (see AbstractObjectInstance.snapshotPreUpdatePosition);
            // the interface getX()/getY() default would NPE on them. They
            // also have no meaningful placement coordinate to show in a
            // "nearby objects" summary, so skip them.
            if (aoi.getSpawn() == null) {
                continue;
            }
            int ox = aoi.getX() & 0xFFFF;
            int oy = aoi.getY() & 0xFFFF;
            int dx = Math.abs(ox - px);
            int dy = Math.abs(oy - py);
            // Include anything within a ~screen-width horizontal box —
            // the divergence is typically tied to badniks a few blocks
            // ahead of the player, not off-screen spawns.
            if (dx > 0x180 || dy > 0x100) {
                continue;
            }
            int id = aoi.getSpawn().objectId();
            sb.append(String.format(
                    "    slot=%3d id=0x%02X %s @%04X,%04X (dx=%d dy=%d)%n",
                    aoi.getSlotIndex(),
                    id & 0xFF,
                    aoi.getClass().getSimpleName(),
                    ox, oy,
                    ox - px, oy - py));
        }
        if (sb.length() == 0) {
            return "    (no active objects within a screen-width of the player)";
        }
        return sb.toString();
    }

    private static TraceCharacterState captureFirstSidekickState() {
        SpriteManager sprites = GameServices.spritesOrNull();
        if (sprites == null || sprites.getRegisteredSidekicks().isEmpty()) {
            return null;
        }
        return TraceCharacterState.fromSprite(sprites.getRegisteredSidekicks().getFirst());
    }

    private void checkComplete() {
        if (cursor >= trace.frameCount()) {
            complete = true;
        }
    }

    /**
     * Repositions the visual/comparison cursor after an engine-state rewind.
     * Existing mismatch counters are retained; no comparison is performed for
     * the seek itself.
     */
    public void seekForRewind(int targetCursor) {
        if (trace.frameCount() == 0) {
            cursor = 0;
            currentVisualFrame = null;
            complete = true;
            return;
        }
        cursor = Math.max(0, Math.min(targetCursor, trace.frameCount() - 1));
        int visualCursor = targetCursor <= 0
                ? 0
                : Math.min(targetCursor - 1, trace.frameCount() - 1);
        currentVisualFrame = trace.getFrame(visualCursor);
        complete = false;
    }

    public int errorCount() { return errorCount; }
    public int warningCount() { return warningCount; }
    public int laggedFrames() { return laggedFrames; }
    public int cursor() { return cursor; }
    public MismatchEntry firstNonCameraPhysicsMismatch() { return firstNonCameraPhysicsMismatch; }
    public boolean hasRecordingDesync() { return firstErrorLogged; }
    public boolean isComplete() { return complete; }
    public List<MismatchEntry> recentMismatches() { return mismatches.recent(); }
    public int recentActionMask() { return lastActionMask; }
    public int recentInputMask() { return lastInputMask; }
    public boolean recentStartPressed() { return lastStartPressed; }
    public TraceMetadata metadata() { return trace.metadata(); }
    public TraceFrame currentVisualFrame() { return currentVisualFrame; }
}
