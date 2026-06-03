package com.openggf.trace.replay;

import com.openggf.GameLoop;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.live.LiveTraceComparator;

import java.util.function.Supplier;

/**
 * UI-agnostic deterministic trace-replay drive. Extracted verbatim from
 * {@code TraceSessionLauncher.finishLaunchAfterGameBootstrap()} so the same
 * bootstrap can be reused by the live Trace Test Mode launcher and the headless
 * trace-capture tool.
 *
 * <p>{@link #start(int, int)} runs the reusable bootstrap steps 1–9 in order.
 * The fragile invariants (team-after-reset-before-zone-load,
 * title-card-before-first-step, startPosition-before-applyBootstrap,
 * align-counters-before-comparator, cursor sync) live in this one method now.
 * UI-specific concerns (fade/teardown, completion-hold, camera focus, HUD
 * overlay, rewind controllers, Esc handling, the {@code activeSession} static)
 * remain in the caller.
 */
public final class TraceReplayDriver {

    private final TraceData trace;
    private final Bk2Movie movie;
    private final TraceReplayFixture fixture;
    private final GameLoop loop;
    private final Supplier<AbstractPlayableSprite> spriteSupplier;

    private LiveTraceComparator comparator;
    private int initialCursor;

    public TraceReplayDriver(TraceData trace, Bk2Movie movie, TraceReplayFixture fixture,
                             GameLoop loop, Supplier<AbstractPlayableSprite> spriteSupplier) {
        this.trace = trace;
        this.movie = movie;
        this.fixture = fixture;
        this.loop = loop;
        this.spriteSupplier = spriteSupplier;
    }

    /**
     * Runs the reusable bootstrap steps 1–9 in order. Throws on failure (the
     * caller restores config and routes back to its idle/picker path).
     */
    public void start(int zone, int act) throws Exception {
        PlaybackDebugManager playback = GameServices.playbackDebug();

        // prepareConfiguration already ran inside launch() before
        // launchGameByEntry fired — the master-title exit handler
        // needs the recorded team config in place when it calls
        // GameplayTeamBootstrap.registerActiveTeam.
        //
        // Mirror TestEnvironment.resetPerTest so the replay starts
        // from the same zero state the headless trace tests do.
        // Without this, state left by initializeGame() (title
        // screen, default level, residual objects) leaks in and
        // causes subpixel drift from frame 0 that surfaces at the
        // first enemy destruction.
        //
        // The reset zaps the sprite manager, so we need to
        // re-register the active team BEFORE loadZoneAndAct runs
        // (loadZoneAndAct's spawnPlayerAtStartPosition expects the
        // main sprite to already exist — otherwise the camera's
        // focusedSprite ends up null on the next frame).
        TraceReplaySessionBootstrap.resetLevelSubsystemsForReplay();
        GameplayTeamBootstrap.registerActiveTeam(
                GameServices.module(),
                GameServices.sprites(),
                GameServices.configuration());
        GameServices.level().loadZoneAndAct(zone, act);
        loop.setGameMode(GameMode.LEVEL);

        // Swallow every title-card request the level load raised —
        // both the main `consumeTitleCardRequest` (fired by
        // requestTitleCardIfNeeded during profile load steps; if
        // left armed, GameLoop.stepInternal flips the mode to
        // TITLE_CARD on the next step, freezing the sprite and
        // desyncing the BK2 cursor from the comparator) and the
        // in-level variant. Headless trace tests bypass both via
        // the headless graphics mode; we do it explicitly.
        //
        // Relies on TitleCardProvider.reset() being a simple state
        // wipe that can't throw — true for all three game modules
        // today (S1/S2/S3K TitleCardManager.reset are field resets).
        GameServices.level().consumeTitleCardRequest();
        GameServices.level().consumeInLevelTitleCardRequest();
        TitleCardProvider titleCardProvider =
                GameServices.module() != null
                        ? GameServices.module().getTitleCardProvider()
                        : null;
        if (titleCardProvider != null && titleCardProvider.isOverlayActive()) {
            titleCardProvider.reset();
        }

        int startIndex = TraceReplayBootstrap
                .recordingStartFrameForTraceReplay(trace);
        playback.startSession(movie, startIndex);

        // Reapply metadata start centre + initial ground snap
        // BEFORE applyBootstrap so the order matches the headless
        // fixture (which does these in Builder.build() — i.e.
        // before any trace-data bootstrap runs). Running them
        // after applyBootstrap would clobber subpixel state the
        // helper's hydration steps wrote for seeded traces.
        TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
        TraceReplaySessionBootstrap.BootstrapResult boot =
                TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);

        this.initialCursor = boot.replayStart().startingTraceIndex();
        TraceFrame previousDriveFrame = boot.replayStart().hasSeededTraceState()
                ? trace.getFrame(boot.replayStart().seededTraceIndex())
                : initialCursor > 0 ? trace.getFrame(initialCursor - 1) : null;
        TraceReplaySessionBootstrap.alignFrameCountersForReplayStart(
                previousDriveFrame,
                initialCursor < trace.frameCount() ? trace.getFrame(initialCursor) : null);
        this.comparator = new LiveTraceComparator(
                trace,
                ToleranceConfig.DEFAULT,
                initialCursor,
                spriteSupplier::get,
                loop::toggleUserPause);
        playback.setFrameObserver(comparator);
    }

    /** Trace index where replay starts (recording start frame for this trace). */
    public int recordingStartFrame() {
        return TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace);
    }

    /** Trace cursor the comparator started at (used as the rewind trace base frame). */
    public int initialCursor() {
        return initialCursor;
    }

    public LiveTraceComparator comparator() {
        return comparator;
    }

    public boolean isComplete() {
        return comparator != null && comparator.isComplete();
    }

    public TraceFrame currentVisualFrame() {
        return comparator != null ? comparator.currentVisualFrame() : null;
    }
}
