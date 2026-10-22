package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameServices;
import com.openggf.game.RespawnState;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelTransitionCoordinator;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.SeamlessTransitionResourceHandoff;
import com.openggf.level.SeamlessTransitionResourceHandoffId;
import com.openggf.level.resources.DeferredLevelResourceManifest;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ROM-free wiring of GameLoop's hosted-run act-completion sites: a host-returning run whose
 * host asks to retry, or whose player pressed retry on the completion frame, restarts its act
 * through the retry reload instead of ending. Covers the results-exit host return, the
 * next-act/next-zone/credits requests, the deferred cross-act seamless request and completion
 * requests left behind by an act a retry replaces. The LevelManager is mocked; ROM-backed
 * results and seamless reload paths are not exercised here.
 */
@Isolated
class TestHostedRunActCompletionWiring {
    private static final RunSpec SPEC = new RunSpec("s2", 1, 0, "sonic", GameplayRunPolicy.isolatedAct());
    private static final RunSpec S3K_SPEC = new RunSpec("s3k", 7, 0, "sonic", GameplayRunPolicy.isolatedAct());

    /** The act-completion requests GameLoop consumes before the ordinary retry boundary. */
    enum Completion {
        HOST_RETURN, NEXT_ACT, NEXT_ZONE, CREDITS;

        void request(LevelManager level) {
            switch (this) {
                case HOST_RETURN -> when(level.consumeHostReturnRequest()).thenReturn(true, false);
                case NEXT_ACT -> when(level.consumeNextActRequest()).thenReturn(true, false);
                case NEXT_ZONE -> when(level.consumeNextZoneRequest()).thenReturn(true, false);
                case CREDITS -> when(level.consumeCreditsRequest()).thenReturn(true, false);
            }
        }
    }

    private final List<RunEndReason> ended = new ArrayList<>();
    private final List<RunEndReason> returned = new ArrayList<>();
    private final AtomicInteger asked = new AtomicInteger();

    @AfterEach
    void tearDown() {
        PlaybackDebugManager playback = GameServices.playbackDebug();
        playback.setFrameObserver(null);
        playback.endSession();
        SessionManager.clear();
    }

    @ParameterizedTest
    @EnumSource(Completion.class)
    void aHostThatAsksToRetryRestartsItsCompletedAct(Completion completion) throws Exception {
        LevelManager level = installLevel(new Sonic2GameModule());
        completion.request(level);
        GameLoop loop = begin(level, true);

        loop.step();

        verify(level.getCheckpointState()).clear();
        verify(level.getTransitions()).requestZoneAndAct(SPEC.zone(), SPEC.act(), true);
        assertTrue(loop.hostedRuns.isActive(), "the retried run keeps its host");
        assertEquals(List.of(), ended);
        assertEquals(List.of(), returned);
        assertEquals(1, asked.get());
    }

    @ParameterizedTest
    @EnumSource(Completion.class)
    void aHostThatDoesNotOptInStillEndsTheRun(Completion completion) throws Exception {
        LevelManager level = installLevel(new Sonic2GameModule());
        completion.request(level);
        GameLoop loop = begin(level, false);

        loop.step();

        assertEquals(List.of(RunEndReason.ACT_COMPLETED), ended);
        assertEquals(List.of(RunEndReason.ACT_COMPLETED), returned);
        assertFalse(loop.hostedRuns.isActive());
        assertNoReloadRequested(level);
    }

    @Test
    void aRetryPressedOnTheCompletionFrameNoLongerLosesToTheHostReturn() throws Exception {
        LevelManager level = installLevel(new Sonic2GameModule());
        Completion.HOST_RETURN.request(level);
        GameLoop loop = new GameLoop(new InputHandler());
        RunHandle handle = beginOn(loop, level, false);
        handle.retry();

        loop.step();

        verify(level.getCheckpointState()).clear();
        verify(level.getTransitions()).requestZoneAndAct(SPEC.zone(), SPEC.act(), true);
        assertTrue(loop.hostedRuns.isActive());
        assertEquals(List.of(), ended);
        assertEquals(0, asked.get(), "the requested retry answers the completion; the host is not asked");
        assertFalse(loop.hostedRuns.consumeRetry(), "the retry request was consumed by the completion");
    }

    @Test
    void aMidActRetryKeepsTheActPlayingUnderTheReloadFade() throws Exception {
        LevelManager level = installLevel(new Sonic2GameModule());
        GameLoop loop = new GameLoop(new InputHandler());
        RunHandle handle = beginOn(loop, level, true);
        handle.retry();

        loop.step();

        verify(level.getTransitions()).requestZoneAndAct(SPEC.zone(), SPEC.act());
        verify(level.getTransitions(), never()).requestZoneAndAct(anyInt(), anyInt(), anyBoolean());
        assertEquals(0, asked.get(), "no act completed, so the host is not asked");
    }

    @Test
    void aPendingLeaveWinsOverARetryAtActCompletion() throws Exception {
        LevelManager level = installLevel(new Sonic2GameModule());
        Completion.HOST_RETURN.request(level);
        GameLoop loop = new GameLoop(new InputHandler());
        RunHandle handle = beginOn(loop, level, true);
        handle.retry();
        handle.leave();

        loop.step();

        assertEquals(List.of(RunEndReason.ACT_COMPLETED), ended);
        assertFalse(loop.hostedRuns.isActive());
        assertNoReloadRequested(level);
        assertEquals(0, asked.get());
    }

    @Test
    void aLeaveRaisedWhileAnsweringTheRetryQueryEndsTheRunWithoutAReload() throws Exception {
        LevelManager level = installLevel(new Sonic2GameModule());
        Completion.HOST_RETURN.request(level);
        GameLoop loop = new GameLoop(new InputHandler());
        RunHandle[] handle = new RunHandle[1];
        handle[0] = loop.beginHostedRun(SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() {
                handle[0].leave();
                return true;
            }
            @Override public void onRunEnded(RunEndReason reason) { ended.add(reason); }
        }, returned::add);
        startRun();

        loop.step();

        assertEquals(List.of(RunEndReason.ACT_COMPLETED), ended);
        assertFalse(loop.hostedRuns.isActive());
        assertNoReloadRequested(level);
    }

    @Test
    void aFailingRetryQueryEndsTheRunAsAborted() throws Exception {
        LevelManager level = installLevel(new Sonic2GameModule());
        Completion.HOST_RETURN.request(level);
        GameLoop loop = new GameLoop(new InputHandler());
        loop.beginHostedRun(SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() { throw new IllegalStateException("creator bug"); }
            @Override public void onRunEnded(RunEndReason reason) { ended.add(reason); }
        }, returned::add);
        startRun();

        loop.step();

        assertEquals(List.of(RunEndReason.ABORTED), ended);
        assertEquals(List.of(RunEndReason.ABORTED), returned);
        assertNoReloadRequested(level);
    }

    @Test
    void aCrossActSeamlessCompletionRetriesForARetryingHost() throws Exception {
        // MHZ1_BackgroundEvent queues its act-2 reload while the act-1 tally is still running.
        LevelManager level = installLevel(new Sonic3kGameModule());
        var handoffs = SessionManager.getCurrentGameplayMode().seamlessTransitionResourceHandoffs();
        var handoff = handoffs.register(unappliedHandoff());
        SeamlessLevelTransitionRequest request = queueCrossActRequest(level, handoff);
        GameLoop loop = beginS3k(true);

        loop.step();

        assertEquals(List.of(), ended);
        assertTrue(loop.hostedRuns.isActive(), "the retried run keeps its host");
        assertEquals(1, asked.get());
        verify(level, never()).applySeamlessTransition(request);
        verify(level.getCheckpointState()).clear();
        verify(level.getTransitions()).requestZoneAndAct(S3K_SPEC.zone(), S3K_SPEC.act(), true);
        assertThrows(IllegalStateException.class, () -> handoffs.peek(handoff),
                "the dropped request's single-use handoff is released");
    }

    @Test
    void aCrossActSeamlessCompletionEndsTheRunForAHostThatDoesNotOptIn() throws Exception {
        LevelManager level = installLevel(new Sonic3kGameModule());
        var handoffs = SessionManager.getCurrentGameplayMode().seamlessTransitionResourceHandoffs();
        var handoff = handoffs.register(unappliedHandoff());
        SeamlessLevelTransitionRequest request = queueCrossActRequest(level, handoff);
        GameLoop loop = beginS3k(false);

        loop.step();

        assertEquals(List.of(RunEndReason.ACT_COMPLETED), ended);
        assertEquals(List.of(RunEndReason.ACT_COMPLETED), returned);
        verify(level, never()).applySeamlessTransition(request);
        assertNoReloadRequested(level);
        assertThrows(IllegalStateException.class, () -> handoffs.peek(handoff));
    }

    @Test
    void aRetryPressedBeforeACrossActSeamlessCompletionRestartsTheAct() throws Exception {
        LevelManager level = installLevel(new Sonic3kGameModule());
        SeamlessLevelTransitionRequest request = queueCrossActRequest(level, null);
        GameLoop loop = new GameLoop(new InputHandler());
        RunHandle handle = beginS3kOn(loop, false);
        handle.retry();

        loop.step();

        assertEquals(List.of(), ended);
        assertEquals(0, asked.get());
        verify(level, never()).applySeamlessTransition(request);
        verify(level.getTransitions()).requestZoneAndAct(S3K_SPEC.zone(), S3K_SPEC.act(), true);
    }

    @Test
    void anActCompletionRaisedDuringARetryFadeIsSatisfiedByTheRetryLoad() throws Exception {
        // A mid-act retry keeps the act playing under its fade; a results object that finishes
        // there raises a host return the fresh attempt must not inherit.
        LevelManager level = installLevel(new Sonic2GameModule());
        LevelTransitionCoordinator transitions = delegateTransitions(level);
        List<Integer> loads = new ArrayList<>();
        doAnswer(invocation -> loads.add(invocation.getArgument(0))).when(level).loadZoneAndAct(anyInt(), anyInt());
        doAnswer(invocation -> loads.add(invocation.getArgument(0)))
                .when(level).loadZoneAndActAtFreshTitleCardBoundary(anyInt(), anyInt());
        GameLoop loop = new GameLoop(new InputHandler());
        RunHandle handle = beginOn(loop, level, true);
        handle.retry();

        loop.step(); // consumes the retry: the act's reload is requested
        loop.step(); // starts the reload fade
        transitions.requestHostReturn(); // the replaced act's results exit, under the fade
        for (int frame = 0; frame < 200 && loads.isEmpty(); frame++) {
            loop.step();
        }
        assertEquals(List.of(SPEC.zone()), loads, "the retry reload ran");
        for (int frame = 0; frame < 200; frame++) {
            loop.step();
        }

        assertEquals(List.of(SPEC.zone()), loads, "the stale completion must not reload the new attempt");
        assertEquals(0, asked.get(), "the new attempt has not completed its act");
        assertTrue(loop.hostedRuns.isActive());
        assertEquals(List.of(), ended);
    }

    private GameLoop begin(LevelManager level, boolean retry) {
        GameLoop loop = new GameLoop(new InputHandler());
        beginOn(loop, level, retry);
        return loop;
    }

    private RunHandle beginOn(GameLoop loop, LevelManager level, boolean retry) {
        RunHandle handle = loop.beginHostedRun(SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() {
                asked.incrementAndGet();
                return retry;
            }
            @Override public void onRunEnded(RunEndReason reason) { ended.add(reason); }
        }, returned::add);
        startRun();
        return handle;
    }

    private GameLoop beginS3k(boolean retry) {
        GameLoop loop = new GameLoop(new InputHandler());
        beginS3kOn(loop, retry);
        return loop;
    }

    private RunHandle beginS3kOn(GameLoop loop, boolean retry) {
        SessionManager.getCurrentGameplayMode().beginGameplayRun(
                S3K_SPEC.policy(), S3K_SPEC.zone(), S3K_SPEC.act());
        return loop.beginHostedRun(S3K_SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() {
                asked.incrementAndGet();
                return retry;
            }
            @Override public void onRunEnded(RunEndReason reason) { ended.add(reason); }
        }, returned::add);
    }

    /** Queues the run act's cross-act seamless reload, as MHZ1_BackgroundEvent does. */
    private static SeamlessLevelTransitionRequest queueCrossActRequest(
            LevelManager level, SeamlessTransitionResourceHandoffId handoff) {
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(S3K_SPEC.zone(), S3K_SPEC.act() + 1)
                .resourceHandoff(handoff)
                .build();
        when(level.consumeSeamlessTransitionRequest()).thenReturn(request, (SeamlessLevelTransitionRequest) null);
        return request;
    }

    private static SeamlessTransitionResourceHandoff unappliedHandoff() {
        return new SeamlessTransitionResourceHandoff() {
            @Override public DeferredLevelResourceManifest deferredResources() {
                return DeferredLevelResourceManifest.EMPTY;
            }
            @Override public void transferAfterTargetInit() {
                throw new AssertionError("a dropped request's handoff is never transferred");
            }
        };
    }

    /** Backs the mocked level's transition requests with a real coordinator. */
    private static LevelTransitionCoordinator delegateTransitions(LevelManager level) {
        LevelTransitionCoordinator transitions = new LevelTransitionCoordinator();
        when(level.getTransitions()).thenReturn(transitions);
        when(level.consumeHostReturnRequest()).thenAnswer(i -> transitions.consumeHostReturnRequest());
        when(level.consumeNextActRequest()).thenAnswer(i -> transitions.consumeNextActRequest());
        when(level.consumeNextZoneRequest()).thenAnswer(i -> transitions.consumeNextZoneRequest());
        when(level.consumeCreditsRequest()).thenAnswer(i -> transitions.consumeCreditsRequest());
        when(level.consumeZoneActRequest()).thenAnswer(i -> transitions.consumeZoneActRequest());
        when(level.getRequestedZone()).thenAnswer(i -> transitions.getRequestedZone());
        when(level.getRequestedAct()).thenAnswer(i -> transitions.getRequestedAct());
        when(level.getRequestedMusicId()).thenAnswer(i -> transitions.getRequestedMusicId());
        // Hold gameplay so the mocked level is never stepped; request handling still runs.
        when(level.isLevelInactiveForTransition()).thenReturn(true);
        return transitions;
    }

    private static void assertNoReloadRequested(LevelManager level) {
        verify(level.getTransitions(), never()).requestZoneAndAct(anyInt(), anyInt());
        verify(level.getTransitions(), never()).requestZoneAndAct(anyInt(), anyInt(), anyBoolean());
    }

    private static void startRun() {
        SessionManager.getCurrentGameplayMode().beginGameplayRun(SPEC.policy(), SPEC.zone(), SPEC.act());
    }

    private static LevelManager installLevel(com.openggf.game.GameModule module) throws Exception {
        TestEnvironment.configureGameModuleFixture(module);
        GameplayModeContext gameplay = SessionManager.getCurrentGameplayMode();
        LevelManager level = mock(LevelManager.class);
        when(level.getCheckpointState()).thenReturn(mock(RespawnState.class));
        when(level.getTransitions()).thenReturn(mock(LevelTransitionCoordinator.class));
        setField(gameplay, "levelManager", level);
        return level;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> cursor = target.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Continue through ordinary inheritance.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
