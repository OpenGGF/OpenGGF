package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.resources.DynamicArtDecisionOwner;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle.Routine.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual SpriteManager player pass, including its canonical ROM animation/DPLC call. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionPlayerPass {
    @BeforeEach void headless() {
        TestEnvironment.activeGameplayMode();
        GameServices.graphics().initHeadless();
    }

    private static DynamicArtDecisionOwner installRomAnimation(LifecycleFixture f, int slot) throws Exception {
        var actor = f.actor(slot);
        var art = new Sonic2PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom()))
                .loadForCharacter(slot == 0 ? "sonic" : "tails");
        actor.setAnimationProfile(art.animationProfile()); actor.setAnimationSet(art.animationSet());
        actor.setAnimationFrameCount(art.mappingFrames().size());
        actor.setSpriteRenderer(new PlayerSpriteRenderer(art, GameServices.graphics()));
        var observer = mock(DynamicArtDecisionOwner.class);
        actor.getAnimationManager().setDynamicArtDecisionOwner(observer);
        return observer;
    }

    private static void tick(LifecycleFixture f, int slot, int frame) {
        SpriteManager.tickPlayablePhysics(f.actor(slot), false, false, false, false,
                false, false, false, false, f.actors.level, frame);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void deathAndCameraWaitSkipControlTailButKeepRomAnimationAndNextControlHistory(int slot) throws Exception {
        var f = new LifecycleFixture();
        when(f.actors.level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        var actor = f.actor(slot); var artObserver = installRomAnimation(f, slot);
        actor.setInWater(true);
        int wetAcceleration = actor.getRunAccel();
        actor.setInvulnerableFrames(71);
        int history = actor.historyPos();
        f.putBelowOwnDeathRow(slot);
        tick(f, slot, 1);
        assertEquals(RESPAWNING, f.lifecycle.routine(slot));
        assertEquals((history + 1) & 63, actor.historyPos(), "Routine6 records once after its fall");
        assertFalse(actor.isInWater()); assertEquals(wetAcceleration, actor.getRunAccel());
        assertEquals(5, actor.getAnimationId());
        assertNotNull(actor.getSpriteRenderer().dplcFrame(actor.getMappingFrame()));
        assertEquals(71, actor.getInvulnerableFrames());
        int afterDeathHistory = actor.historyPos();
        short[] savedXHistory = actor.copyXHistory(); short[] savedYHistory = actor.copyYHistory();
        f.lifecycle.recordCameraDisplacement(slot, -16, 4);
        tick(f, slot, 2);
        assertEquals(RESPAWNING, f.lifecycle.routine(slot));
        assertEquals(afterDeathHistory, actor.historyPos());
        assertArrayEquals(savedXHistory, actor.copyXHistory()); assertArrayEquals(savedYHistory, actor.copyYHistory());
        assertEquals(wetAcceleration, actor.getRunAccel()); assertEquals(71, actor.getInvulnerableFrames());
        f.lifecycle.recordCameraDisplacement(slot, 0, 0);
        tick(f, slot, 3);
        assertEquals(CONTROL, f.lifecycle.routine(slot));
        assertEquals(afterDeathHistory, actor.historyPos(), "Final A->2 still does not record or run control tails");
        verify(f.actors.level, never()).updatePlayableWaterStateForCurrentLevel(actor);
        verify(f.actors.level, never()).applyTouchResponses(actor);
        verify(f.actors.level, never()).applyPlaneSwitchers(actor);
        verify(artObserver, times(3)).observe(anyInt());
        verify((com.openggf.game.internal.PlayerDplcDmaSink) f.runtimeArt, times(3))
                .playerLoadPlc(eq(actor), anyInt(), any());
        // This small fixture omits terrain. An ordinary object-controlled call
        // skips movement but must still execute the normal control tail.
        actor.setObjectControlled(true); actor.setObjectControlSuppressesMovement(true);
        tick(f, slot, 4);
        assertEquals((afterDeathHistory + 1) & 63, actor.historyPos(), "Canonical cleanup permits next control write");
        verify(f.actors.level).updatePlayableWaterStateForCurrentLevel(actor);
        verify(f.actors.level).applyTouchResponses(actor);
        verify(f.actors.level).applyPlaneSwitchers(actor);
        verify(artObserver, times(4)).observe(anyInt());
        verify((com.openggf.game.internal.PlayerDplcDmaSink) f.runtimeArt, times(4))
                .playerLoadPlc(eq(actor), anyInt(), any());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void finalDeathAnimatesOnceThenGoneHasNoHistoryAnimationOrControlTail(int slot) throws Exception {
        var f = new LifecycleFixture(); var observer = installRomAnimation(f, slot);
        when(f.actors.level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        f.lives(slot, 1); f.putBelowOwnDeathRow(slot);
        tick(f, slot, 1); assertEquals(GONE, f.lifecycle.routine(slot));
        int history = f.actor(slot).historyPos();
        int frame = f.actor(slot).getMappingFrame();
        for (int i = 2; i < 8; i++) tick(f, slot, i);
        assertEquals(history, f.actor(slot).historyPos()); assertEquals(frame, f.actor(slot).getMappingFrame());
        assertTrue(f.actor(slot).isHidden()); verify(observer).observe(anyInt());
        verify((com.openggf.game.internal.PlayerDplcDmaSink) f.runtimeArt)
                .playerLoadPlc(eq(f.actor(slot)), anyInt(), any());
        verify(f.actors.level, never()).applyTouchResponses(f.actor(slot));
        verify(f.actors.level, never()).updatePlayableWaterStateForCurrentLevel(f.actor(slot));
        verify(f.actors.level, never()).applyPlaneSwitchers(f.actor(slot));
        verify(f.actors.level, never()).requestRespawn();
    }

    @Test void healthyHumanStillRunsOrdinaryControlTailAndRecordsOnce() throws Exception {
        var f = new LifecycleFixture();
        when(f.actors.level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        var actor = f.actor(0); var observer = installRomAnimation(f, 0);
        actor.setAir(true);
        actor.setObjectControlled(true); actor.setObjectControlSuppressesMovement(true);
        assertFalse(f.lifecycle.ownsDispatch(actor));
        int history = actor.historyPos();
        tick(f, 0, 1);
        assertEquals((history + 1) & 63, actor.historyPos());
        verify(observer).observe(anyInt());
        verify(f.actors.level).updatePlayableWaterStateForCurrentLevel(actor);
        verify(f.actors.level).applyTouchResponses(actor);
        verify(f.actors.level).applyPlaneSwitchers(actor);
    }
}
