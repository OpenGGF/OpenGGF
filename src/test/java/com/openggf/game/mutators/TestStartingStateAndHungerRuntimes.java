package com.openggf.game.mutators;

import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.LevelLoadCause;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native counter writes, boundaries and rewind state of the starting-state and hunger runtimes. */
class TestStartingStateAndHungerRuntimes {
    private static final StartingStateMutatorPolicy ALL = new StartingStateMutatorPolicy(150, 0, 270, 25_000, 7);

    private static StartingStateRuntime.Start tick(StartingStateRuntime runtime, StartingStateMutatorPolicy policy,
                                                   LevelGamestate level, GameStateManager game, boolean checkpoint) {
        return runtime.beforeNativeTick(policy, level, game, checkpoint, true);
    }

    @Test
    void freshActSeedsEveryCounterOnTheFirstActiveTickWithoutAnExtraLife() {
        var runtime = new StartingStateRuntime();
        var level = new LevelGamestate();
        var game = new GameStateManager();
        runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        assertEquals(StartingStateRuntime.Start.FRESH_ACT, tick(runtime, ALL, level, game, false));
        assertEquals(150, level.getRings());
        assertEquals(0x02, level.getRingExtraLifeFlags(), "the 100 threshold the grant reached is already paid");
        assertEquals(270 * 60L, level.getTimerFrames());
        assertEquals(25_000, game.getScore());
        assertEquals(7, game.getLives());
        // The bank keeps 100 paid, so the native collection awards only 200 later.
        assertEquals(StartingStateRuntime.Start.NONE, tick(runtime, ALL, level, game, false));
        assertEquals(150, level.getRings(), "a seed is applied once per start");
    }

    @Test
    void stockPolicyLeavesNativeCountersUntouched() {
        var runtime = new StartingStateRuntime();
        var level = new LevelGamestate();
        var game = new GameStateManager();
        runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        tick(runtime, StartingStateMutatorPolicy.STOCK, level, game, false);
        assertEquals(0, level.getRings());
        assertEquals(0, level.getRingExtraLifeFlags());
        assertEquals(0, level.getTimerFrames());
        assertEquals(0, game.getScore());
        assertEquals(3, game.getLives());
    }

    @Test
    void scoreAndLivesBelongToTheSessionWhileRingsAndTimeRepeatPerStart() {
        var runtime = new StartingStateRuntime();
        var game = new GameStateManager();
        var level = new LevelGamestate();
        runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        tick(runtime, ALL, level, game, false);
        game.loseLife();
        game.addScore(100);
        var respawn = new LevelGamestate();
        runtime.beforeAssembly(LevelLoadCause.FULL_DEATH_RELOAD);
        assertEquals(StartingStateRuntime.Start.LIFE_RESTART, tick(runtime, ALL, respawn, game, false));
        assertEquals(6, game.getLives(), "a restart keeps the native life loss");
        assertEquals(25_100, game.getScore());
        assertEquals(150, respawn.getRings(), "every life is granted its rings");
        assertEquals(270 * 60L, respawn.getTimerFrames(), "a restart from the act start is seeded");
    }

    @Test
    void checkpointRestartKeepsTheNativeSavedTimeButStillGrantsRings() {
        var runtime = new StartingStateRuntime();
        var game = new GameStateManager();
        runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        tick(runtime, ALL, new LevelGamestate(), game, false);
        var respawn = new LevelGamestate();
        respawn.setTimerFrames(19_259); // Saved checkpoint time, restored by the native load.
        runtime.beforeAssembly(LevelLoadCause.FULL_DEATH_RELOAD);
        tick(runtime, ALL, respawn, game, true);
        assertEquals(19_259, respawn.getTimerFrames());
        assertEquals(150, respawn.getRings());
    }

    @Test
    void nativeRestoresBeginNoStartButANativeLevelStateResetIsAFreshAct() {
        var runtime = new StartingStateRuntime();
        var game = new GameStateManager();
        var level = new LevelGamestate();
        runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        tick(runtime, ALL, level, game, false);
        for (var cause : new LevelLoadCause[] { LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY,
                LevelLoadCause.CHECKPOINT_RESTORE, LevelLoadCause.SEAMLESS_HANDOFF, LevelLoadCause.EDITOR_SWAP }) {
            var restored = new LevelGamestate();
            restored.setRings(12);
            runtime.beforeAssembly(cause);
            assertEquals(StartingStateRuntime.Start.NONE, tick(runtime, ALL, restored, game, false), cause.name());
            assertEquals(12, restored.getRings(), cause + " keeps the native restored counter");
            level = restored;
        }
        runtime.beforeAssembly(LevelLoadCause.PREVIEW);
        runtime.beforeAssembly(LevelLoadCause.DECODE_ONLY);
        assertEquals(StartingStateRuntime.Start.NONE, tick(runtime, ALL, level, game, false), "previews are ignored");
        // The S3K results screen installs a new level state for the next act without an assembly.
        var nextAct = new LevelGamestate();
        assertEquals(StartingStateRuntime.Start.FRESH_ACT, tick(runtime, ALL, nextAct, game, false));
        assertEquals(150, nextAct.getRings());
        assertEquals(270 * 60L, nextAct.getTimerFrames());
    }

    @Test
    void delayedGrantWaitsForActiveTicksAndJoinsCollectedRings() {
        var runtime = new StartingStateRuntime();
        var policy = new StartingStateMutatorPolicy(50, 2, -1, -1, -1);
        var level = new LevelGamestate();
        var game = new GameStateManager();
        runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        tick(runtime, policy, level, game, false);
        assertEquals(0, level.getRings());
        level.setRings(60);
        for (int i = 0; i < 119; i++) tick(runtime, policy, level, game, false);
        assertEquals(60, level.getRings(), "two seconds are 120 active native ticks");
        tick(runtime, policy, level, game, false);
        assertEquals(110, level.getRings());
        assertEquals(0x02, level.getRingExtraLifeFlags(), "crossing 100 by the grant pays no life");
        assertEquals(-1, runtime.ringDelayRemaining());
    }

    @Test
    void disablingDuringTheDelayAbandonsTheGrantAndNoRingsDeniesIt() {
        var runtime = new StartingStateRuntime();
        var policy = new StartingStateMutatorPolicy(50, 1, -1, -1, -1);
        var level = new LevelGamestate();
        runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        tick(runtime, policy, level, null, false);
        tick(runtime, StartingStateMutatorPolicy.STOCK, level, null, false);
        for (int i = 0; i < 120; i++) tick(runtime, policy, level, null, false);
        assertEquals(0, level.getRings());
        runtime.beforeAssembly(LevelLoadCause.FULL_RESTART);
        var denied = new LevelGamestate();
        runtime.beforeNativeTick(new StartingStateMutatorPolicy(50, 0, -1, -1, -1), denied, null, false, false);
        assertEquals(0, denied.getRings(), "No Rings dominates the grant");
    }

    @Test
    void grantSaturatesAtTheNativeCeilingAndMarksBothThresholds() {
        var level = new LevelGamestate();
        level.setRings(990);
        StartingStateRuntime.grant(50, level, true);
        assertEquals(999, level.getRings());
        assertEquals(0, level.getRingExtraLifeFlags(), "thresholds already passed natively are left to their owner");
        var fresh = new LevelGamestate();
        StartingStateRuntime.grant(250, fresh, true);
        assertEquals(0x06, fresh.getRingExtraLifeFlags());
    }

    @Test
    void startingStateSnapshotRoundTripsAPendingDelayedGrant() {
        var runtime = new StartingStateRuntime();
        var policy = new StartingStateMutatorPolicy(50, 1, -1, -1, -1);
        var level = new LevelGamestate();
        runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        tick(runtime, policy, level, null, false);
        for (int i = 0; i < 30; i++) tick(runtime, policy, level, null, false);
        var snapshot = runtime.capture();
        for (int i = 0; i < 30; i++) tick(runtime, policy, level, null, false);
        assertEquals(50, level.getRings());
        runtime.restore(snapshot);
        level.setRings(0);
        level.setRingExtraLifeFlags(0);
        assertEquals(30, runtime.ringDelayRemaining());
        for (int i = 0; i < 30; i++) tick(runtime, policy, level, null, false);
        assertEquals(50, level.getRings(), "the restored countdown still grants exactly once");
    }

    private static AbstractPlayableSprite leader() {
        var leader = mock(AbstractPlayableSprite.class);
        boolean[] dead = { false };
        when(leader.getDead()).thenAnswer(call -> dead[0]);
        when(leader.applyCrushDeath()).thenAnswer(call -> dead[0] = true);
        return leader;
    }

    @Test
    void hungerGraceDrainStarvationAndNativeKill() {
        var runtime = new RingHungerRuntime();
        var policy = RingHungerMutatorPolicy.ofSeconds(2, 3, 1, 0, 5);
        var level = new LevelGamestate();
        level.setRings(7);
        var leader = leader();
        var game = new GameStateManager();
        runtime.beforeNativeTick(policy, leader, level, game);
        assertEquals(RingHungerRuntime.Phase.GRACE, runtime.phase());
        assertEquals(16, runtime.indicator().remainingSixteenths());
        for (int i = 1; i < 60; i++) runtime.beforeNativeTick(policy, leader, level, game);
        assertEquals(8, runtime.indicator().remainingSixteenths(), "half the grace is left after one second");
        for (int i = 0; i < 60; i++) runtime.beforeNativeTick(policy, leader, level, game);
        assertEquals(RingHungerRuntime.Phase.DRAIN, runtime.phase());
        assertEquals(7, level.getRings(), "no ring leaves during the grace");
        for (int i = 0; i < 60; i++) runtime.beforeNativeTick(policy, leader, level, game);
        assertEquals(4, level.getRings());
        for (int i = 0; i < 120; i++) runtime.beforeNativeTick(policy, leader, level, game);
        assertEquals(0, level.getRings(), "the drain stops at zero");
        assertEquals(RingHungerRuntime.Phase.STARVING, runtime.phase());
        verify(leader, never()).applyCrushDeath();
        // Starving began on the drain step that reached zero, two ticks ago: 298 ticks remain.
        for (int i = 0; i < 297; i++) runtime.beforeNativeTick(policy, leader, level, game);
        verify(leader, never()).applyCrushDeath();
        runtime.beforeNativeTick(policy, leader, level, game);
        verify(leader).applyCrushDeath();
    }

    @Test
    void collectingAboveTheThresholdCancelsTheStarvationCountdown() {
        var runtime = new RingHungerRuntime();
        var policy = RingHungerMutatorPolicy.ofSeconds(0, 1, 60, 5, 2);
        var level = new LevelGamestate();
        level.setRings(3);
        var leader = leader();
        for (int i = 0; i < 100; i++) runtime.beforeNativeTick(policy, leader, level, null);
        assertEquals(RingHungerRuntime.Phase.STARVING, runtime.phase());
        level.setRings(6);
        runtime.beforeNativeTick(policy, leader, level, null);
        assertEquals(RingHungerRuntime.Phase.DRAIN, runtime.phase());
        level.setRings(5);
        for (int i = 0; i < 119; i++) runtime.beforeNativeTick(policy, leader, level, null);
        verify(leader, never()).applyCrushDeath();
        runtime.beforeNativeTick(policy, leader, level, null);
        verify(leader).applyCrushDeath();
    }

    @Test
    void hungerClocksHoldWhileTheNativeGameHoldsPlay() {
        var runtime = new RingHungerRuntime();
        var policy = RingHungerMutatorPolicy.ofSeconds(1, 1, 1, 0, 1);
        var level = new LevelGamestate();
        var leader = leader();
        var game = new GameStateManager();
        runtime.beforeNativeTick(policy, leader, level, game);
        level.pauseTimer();
        for (int i = 0; i < 200; i++) runtime.beforeNativeTick(policy, leader, level, game);
        assertEquals(RingHungerRuntime.Phase.GRACE, runtime.phase(), "a signpost or results hold stops the clock");
        level.resumeTimer();
        game.setCurrentBossId(1);
        for (int i = 0; i < 200; i++) runtime.beforeNativeTick(policy, leader, level, game);
        assertEquals(RingHungerRuntime.Phase.GRACE, runtime.phase(), "boss fights hold the clock");
        game.setCurrentBossId(0);
        when(leader.isObjectControlled()).thenReturn(true);
        for (int i = 0; i < 200; i++) runtime.beforeNativeTick(policy, leader, level, game);
        assertEquals(RingHungerRuntime.Phase.GRACE, runtime.phase(), "scripted control holds the clock");
        when(leader.isObjectControlled()).thenReturn(false);
        when(leader.isDebugMode()).thenReturn(true);
        for (int i = 0; i < 300; i++) runtime.beforeNativeTick(policy, leader, level, game);
        verify(leader, never()).applyCrushDeath();
    }

    @Test
    void disabledHungerIsInertAndANewLifeRestartsTheGrace() {
        var runtime = new RingHungerRuntime();
        var level = new LevelGamestate();
        level.setRings(10);
        var leader = leader();
        for (int i = 0; i < 600; i++) runtime.beforeNativeTick(RingHungerMutatorPolicy.STOCK, leader, level, null);
        assertEquals(RingHungerRuntime.Phase.OFF, runtime.phase());
        assertEquals(RingHungerRuntime.Indicator.NONE, runtime.indicator());
        assertEquals(10, level.getRings());
        var policy = RingHungerMutatorPolicy.ofSeconds(0, 1, 1, 0, 5);
        runtime.beforeNativeTick(policy, leader, level, null);
        assertEquals(RingHungerRuntime.Phase.DRAIN, runtime.phase(), "a zero grace drains at once");
        runtime.beginLife();
        assertEquals(RingHungerRuntime.Phase.OFF, runtime.phase());
    }

    @Test
    void hungerSnapshotRoundTripsTheWholeCountdown() {
        var runtime = new RingHungerRuntime();
        var policy = RingHungerMutatorPolicy.ofSeconds(1, 1, 1, 0, 3);
        var level = new LevelGamestate();
        level.setRings(2);
        var leader = leader();
        for (int i = 0; i < 60 + 120 + 30; i++) runtime.beforeNativeTick(policy, leader, level, null);
        assertEquals(RingHungerRuntime.Phase.STARVING, runtime.phase());
        var snapshot = runtime.capture();
        var indicator = runtime.indicator();
        int rings = level.getRings();
        for (int i = 0; i < 400; i++) runtime.beforeNativeTick(policy, leader, level, null);
        verify(leader).applyCrushDeath();
        runtime.restore(snapshot);
        level.setRings(rings);
        assertEquals(indicator, runtime.indicator());
        var second = leader();
        for (int i = 0; i < 147; i++) runtime.beforeNativeTick(policy, second, level, null);
        verify(second, never()).applyCrushDeath();
        runtime.beforeNativeTick(policy, second, level, null);
        verify(second).applyCrushDeath();
    }
}
