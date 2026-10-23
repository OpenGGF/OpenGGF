package com.openggf;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameServices;
import com.openggf.game.ghost.GhostRenderRegistry;
import com.openggf.game.recording.RecordingMainPlayerResolver;
import com.openggf.mods.run.DeterminismFingerprint;
import com.openggf.mods.run.GhostPose;
import com.openggf.mods.run.PlayerPose;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunInput;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.run.RunStep;
import com.openggf.ghost.GhostFrame;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.version.AppVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Engine side of a launched gameplay run: holds the {@link RunHost}, calls it at fixed points
 * of the level frame, samples the immutable {@link RunStep} after each executed step, draws its
 * ghosts and overlay, and queues its {@link RunHandle} commands for the next safe boundary.
 * {@code GameLoop} owns the transitions; this class only decides and reports.
 */
final class HostedRunController {
    private static final Logger LOGGER = Logger.getLogger(HostedRunController.class.getName());
    private static final int MAX_GHOSTS = 8;

    private final SonicConfigurationService configuration;
    private final GhostRenderer ghostRenderer = new GhostRenderer();
    private final LocalPlayerLook localLook = new LocalPlayerLook();
    private final GhostRenderRegistry.GhostLayerRenderer layerRenderer = this::renderGhostsForLayer;
    private GhostRenderRegistry registeredRegistry;

    private RunSpec spec;
    private RunHost host;
    private Handle handle;
    private long stepOrdinal;
    private int pendingHeldMask;
    private boolean pendingStartHeld;
    private boolean debugAssistThisStep;
    /** Player input is released for the admitted step because the host asked; this run set it. */
    private boolean inputHeld;
    private boolean retryRequested;
    private RunEndReason leaveReason;
    private boolean actCompleteSeen;
    private boolean spectating;
    private boolean spectateActive;
    private int spectateDx;
    private int spectateDy;

    HostedRunController(SonicConfigurationService configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    boolean isActive() {
        return host != null;
    }

    RunSpec spec() {
        return spec;
    }

    /** Starts hosting {@code spec}; the caller loads the level and then calls {@link #onLevelReady}. */
    RunHandle begin(RunSpec spec, RunHost host) {
        if (this.host != null) {
            throw new IllegalStateException("A run is already active");
        }
        this.spec = Objects.requireNonNull(spec, "spec");
        this.host = Objects.requireNonNull(host, "host");
        this.handle = new Handle();
        localLook.request(com.openggf.net.identity.PlayerAppearance.STOCK);
        retryRequested = false;
        leaveReason = null;
        return handle;
    }

    /** The run's level is loaded (launch or retry). */
    void onLevelReady(boolean debugAssisted) {
        onLevelReady(debugAssisted, false);
    }

    /** The run's level is loaded; {@code deathRestart} marks a fast restart after a death. */
    void onLevelReady(boolean debugAssisted, boolean deathRestart) {
        if (host == null) {
            return;
        }
        String determinismFingerprint = new DeterminismFingerprint(AppVersion.get(), romChecksumOrZero()).asString();
        stepOrdinal = 0;
        debugAssistThisStep = false;
        // The load that just finished satisfies any retry requested before it (for example a
        // second press during the reload fade); only a request made from here on reloads again.
        retryRequested = false;
        // The sidekick pattern-bank cursor resets on every level load
        // (LevelPlayableArtInitializer), so cached ghost slots hold stale bank
        // bases; clear them before the new level's first render.
        ghostRenderer.clearSlots();
        // The load built fresh player renderers; point them at the look before the first draw.
        refreshLocalLook();
        attachRenderer(GameServices.ghostRenderRegistryOrNull());
        stopSpectating();
        actCompleteSeen = false;
        int[] size = levelSize();
        RunLevelStart start = new RunLevelStart(spec, determinismFingerprint, debugAssisted, size[0], size[1],
                deathRestart);
        guarded(() -> host.onLevelReady(start));
    }

    private static int[] levelSize() {
        var levelManager = GameServices.levelOrNull();
        var level = levelManager != null ? levelManager.getCurrentLevel() : null;
        if (level == null) {
            return new int[] {0, 0};
        }
        int block = level.getBlockPixelSize();
        return new int[] {Math.max(1, level.getLayerWidthBlocks(0)) * block,
                Math.max(1, level.getLayerHeightBlocks(0)) * block};
    }

    /** Applies the host's spectator request; honoured only once the act is complete. */
    private void applySpectate() {
        var camera = GameServices.cameraOrNull();
        if (camera == null) {
            return;
        }
        if (!spectateActive || !actCompleteSeen) {
            stopSpectating();
            return;
        }
        if (!spectating) {
            spectating = true;
            camera.setFrozen(true);
        }
        int x = Math.clamp(camera.getX() + (long) spectateDx, camera.getMinX(), camera.getMaxX());
        int y = Math.clamp(camera.getY() + (long) spectateDy, camera.getMinY(), camera.getMaxY());
        camera.setX((short) x);
        camera.setY((short) y);
    }

    private void stopSpectating() {
        spectateActive = false;
        spectateDx = 0;
        spectateDy = 0;
        if (spectating) {
            spectating = false;
            var camera = GameServices.cameraOrNull();
            if (camera != null) {
                camera.setFrozen(false);
            }
        }
    }

    private static int romChecksumOrZero() {
        try {
            return GameServices.rom().getRom().calculateChecksum();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to compute ROM checksum for the run fingerprint", e);
            return 0;
        }
    }

    /** Asks the host whether this frame's step may run; false holds the run. */
    boolean admitStep(InputHandler input) {
        if (host == null || leaveReason != null) {
            return true;
        }
        try {
            boolean admitted = host.admitStep(new FrameInput(input));
            applySpectate();
            refreshLocalLook();
            holdPlayerInput(admitted && host.holdsPlayerInput());
            return admitted;
        } catch (RuntimeException failure) {
            abort(failure);
            return true;
        }
    }

    /**
     * Releases player 1's controller for the admitted step at the host's request. Only a
     * suppression this run set is cleared, so the engine's own input locks are left alone.
     */
    private void holdPlayerInput(boolean hold) {
        if (hold == inputHeld) {
            return;
        }
        var sprites = GameServices.spritesOrNull();
        if (sprites != null) {
            sprites.setInputSuppressed(hold);
        }
        inputHeld = hold;
    }

    /** Captures player 1's admitted input for the step about to execute. */
    void beforeStep(InputHandler input) {
        var p1 = input.logical().player1();
        pendingHeldMask = inputHeld ? 0 : p1.heldMask();
        pendingStartHeld = !inputHeld && p1.startHeld();
    }

    /** A debug shortcut was used this frame; reported with the next step. */
    void noteDebugAssist() {
        debugAssistThisStep = true;
    }

    /** Samples and delivers the step that just executed. */
    void afterStep() {
        if (host == null) {
            return;
        }
        var sprites = GameServices.spritesOrNull();
        var level = GameServices.levelOrNull();
        var gameState = GameServices.gameStateOrNull();
        if (sprites == null || level == null || gameState == null) {
            return;
        }
        AbstractPlayableSprite player;
        try {
            player = RecordingMainPlayerResolver.resolve(configuration, sprites);
        } catch (IllegalStateException e) {
            return;
        }
        // S3K raises the ROM Level_end_flag (endOfLevelActive); S1/S2 raise the
        // game-agnostic act-completion signal (their ROMs never set Level_end_flag,
        // and shared physics reads it for the strict right-boundary clamp).
        boolean actComplete = gameState.isEndOfLevelActive() || gameState.isActCompletionSignalActive();
        var checkpointState = level.getCheckpointState();
        int checkpointIndex = checkpointState != null ? checkpointState.getLastCheckpointIndex() : -1;
        actCompleteSeen |= actComplete;
        RunStep step = new RunStep(stepOrdinal++, pendingHeldMask, pendingStartHeld, poseOf(player),
                actComplete, checkpointIndex, debugAssistThisStep);
        debugAssistThisStep = false;
        guarded(() -> host.afterStep(step));
    }

    static PlayerPose poseOf(AbstractPlayableSprite sprite) {
        return new PlayerPose(sprite.getCentreX(), sprite.getCentreY(), sprite.getMappingFrame(),
                sprite.getRenderHFlip(), sprite.getRenderVFlip(), sprite.getPriorityBucket(),
                sprite.isHighPriority());
    }

    boolean consumeRetry() {
        boolean requested = retryRequested;
        retryRequested = false;
        return requested && host != null;
    }

    /**
     * Asks the host whether its completed act restarts as a retry instead of ending the run.
     * False with no run or a pending leave, including a leave the host raises while answering;
     * a failing host aborts the run and answers false.
     */
    boolean retryOnActCompletion() {
        if (host == null || leaveReason != null) {
            return false;
        }
        try {
            boolean retry = host.retryOnActCompletion();
            return retry && leaveReason == null;
        } catch (RuntimeException failure) {
            abort(failure);
            return false;
        }
    }

    /**
     * Resolves a host-returning run's completed act: null restarts the act as a retry (one
     * requested this frame, or one the host asks for), otherwise the reason the run ends. A
     * pending leave or host failure wins over a retry, as at the ordinary boundary; a host
     * failure (including one the retry query raises) ends the run as aborted, anything else
     * as completed.
     */
    RunEndReason resolveActCompletion() {
        RunEndReason pending = consumeLeave();
        if (pending == null && (consumeRetry() || retryOnActCompletion())) {
            return null;
        }
        if (pending == null) {
            pending = consumeLeave();
        }
        return pending == RunEndReason.ABORTED ? RunEndReason.ABORTED : RunEndReason.ACT_COMPLETED;
    }

    /** The reason the run must end at the next boundary (host leave or host failure), or null. */
    RunEndReason consumeLeave() {
        RunEndReason reason = leaveReason;
        leaveReason = null;
        return host != null ? reason : null;
    }

    /** Draws the host's screen-space overlay. */
    void drawOverlay(LevelOverlayCanvas canvas) {
        if (host != null && leaveReason == null) {
            // Also runs on paused and held frames, so a look set then shows from the next frame.
            refreshLocalLook();
            ghostRenderer.drawNameplates(canvas);
            guarded(() -> host.drawOverlay(canvas));
        }
    }

    /** Ends the run, telling the host why. Safe to call when no run is active. */
    void end(RunEndReason reason) {
        if (host == null) {
            return;
        }
        RunHost ended = host;
        holdPlayerInput(false);
        stopSpectating();
        detachRenderer();
        ghostRenderer.clearSlots();
        localLook.request(com.openggf.net.identity.PlayerAppearance.STOCK);
        localLook.clear();
        handle.active = false;
        host = null;
        spec = null;
        handle = null;
        retryRequested = false;
        leaveReason = null;
        try {
            ended.onRunEnded(reason);
        } catch (com.openggf.mods.code.ModFaultBoundary.CallbackAborted aborted) {
            // The owner is disabled: the outer engine boundary must close its scene,
            // not resume it through the ordinary run-completion path.
            throw aborted;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Run host failed while ending the run", e);
        }
    }

    void attachRenderer(GhostRenderRegistry registry) {
        detachRenderer();
        if (registry != null) {
            registry.register(layerRenderer);
            registeredRegistry = registry;
        }
    }

    private void detachRenderer() {
        if (registeredRegistry != null) {
            registeredRegistry.unregister(layerRenderer);
            registeredRegistry = null;
        }
    }

    private void renderGhostsForLayer(int bucket, boolean highPriority) {
        if (host == null) {
            return;
        }
        var sprites = GameServices.spritesOrNull();
        if (sprites == null) {
            return;
        }
        AbstractPlayableSprite player;
        try {
            player = RecordingMainPlayerResolver.resolve(configuration, sprites);
        } catch (IllegalStateException e) {
            return;
        }
        List<GhostPose> poses;
        try {
            poses = leaveReason == null ? host.ghosts() : null;
        } catch (RuntimeException failure) {
            abort(failure);
            return;
        }
        if (poses == null || poses.isEmpty()) {
            return;
        }
        List<ActiveGhost> active = new ArrayList<>(Math.min(MAX_GHOSTS, poses.size()));
        for (GhostPose pose : poses) {
            if (active.size() >= MAX_GHOSTS) {
                break;
            }
            if (pose != null) {
                active.add(toActiveGhost(pose));
            }
        }
        ghostRenderer.renderForLayer(active, bucket, highPriority, player.getCentreX(), player.getCentreY(),
                spec.character());
    }

    /** Points the run's own character at the requested look (or back at its stock rows). */
    private void refreshLocalLook() {
        if (host == null) {
            return;
        }
        var sprites = GameServices.spritesOrNull();
        if (sprites == null) {
            return;
        }
        AbstractPlayableSprite player;
        try {
            player = RecordingMainPlayerResolver.resolve(configuration, sprites);
        } catch (IllegalStateException e) {
            return;
        }
        localLook.refresh(player, spec.character());
    }

    /** The look requested for the run's own character; for tests. */
    com.openggf.net.identity.PlayerAppearance localLook() {
        return localLook.requested();
    }

    static ActiveGhost toActiveGhost(GhostPose pose) {
        PlayerPose p = pose.pose();
        GhostFrame frame = new GhostFrame(p.centreX(), p.centreY(), p.mappingFrame(), p.hFlip(), p.vFlip(),
                false, p.priorityBucket(), p.highPriority());
        return new ActiveGhost(pose.slotId(), pose.characterCode(), frame, pose.nameplate(), pose.opacity(),
                pose.appearance());
    }

    /** Runs a host callback; a failure aborts the run at the next boundary instead of the engine. */
    private void guarded(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException failure) {
            abort(failure);
        }
    }

    private void abort(RuntimeException failure) {
        if (failure instanceof com.openggf.mods.code.ModFaultBoundary.CallbackAborted aborted) {
            // Retire host state and visual registrations immediately. The original abort
            // retains attribution even if the final owner cleanup callback also fails.
            try {
                end(RunEndReason.ABORTED);
            } catch (RuntimeException cleanup) {
                // This is another creator callback, not a failed engine teardown.
                // The outer recovery treats suppressed cleanup errors as unsafe;
                // the boundary has already attributed this one, and state is retired.
                LOGGER.log(Level.WARNING, "Run host also failed during fault cleanup", cleanup);
            }
            throw aborted;
        }
        LOGGER.log(Level.WARNING, "Run host failed; ending the run", failure);
        leaveReason = RunEndReason.ABORTED;
    }

    private final class Handle implements RunHandle {
        private boolean active = true;

        @Override
        public void retry() {
            if (active) {
                retryRequested = true;
            }
        }

        @Override
        public void leave() {
            if (active && leaveReason == null) {
                leaveReason = RunEndReason.LEFT;
            }
        }

        @Override
        public void spectate(boolean on, int dx, int dy) {
            if (active) {
                spectateActive = on;
                spectateDx = on ? dx : 0;
                spectateDy = on ? dy : 0;
            }
        }

        @Override
        public void setPlayerAppearance(com.openggf.net.identity.PlayerAppearance appearance) {
            Objects.requireNonNull(appearance, "appearance");
            if (active) {
                localLook.request(appearance);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    private record FrameInput(InputHandler handler) implements RunInput {
        @Override
        public LogicalInputSnapshot input() {
            return handler.logical();
        }

        @Override
        public boolean keyDown(int key) {
            return handler.isKeyDown(key);
        }

        @Override
        public boolean keyPressed(int key) {
            return handler.isKeyPressed(key);
        }

        @Override
        public boolean levelFadingIn() {
            var level = GameServices.levelOrNull();
            return level != null && level.getAnimatedPatternManager()
                    instanceof com.openggf.game.internal.LevelPaletteFadeOwner owner
                    && owner.presentsLevelFadeFromBlack();
        }
    }
}
