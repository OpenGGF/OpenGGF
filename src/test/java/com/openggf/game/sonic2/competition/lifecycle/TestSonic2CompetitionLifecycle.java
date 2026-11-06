package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.game.competition.NativePlayerDeathPass;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle.Routine.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionLifecycle {
    @BeforeEach void headless() { TestEnvironment.activeGameplayMode(); }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void ordinaryDeathRespawnsOnlyThatHumanAndKeepsRunningWorldBanks(int slot) throws Exception {
        var f = new LifecycleFixture();
        var actor = f.actor(slot);
        actor.setHighPriority(true);
        actor.setTopSolidBit((byte) 0xE); actor.setLrbSolidBit((byte) 0xF); actor.setLayer((byte) 1);
        f.session.levelState(slot).addRings(140);
        f.session.levelState(slot).setTimerFrames(527);
        f.lifecycle.saveCheckpoint(slot, 0x83, 0x800, 0x500);
        var checkpoint = f.lifecycle.checkpoint(slot);
        var otherBefore = f.actor(1 - slot).captureRewindState();
        var otherProgress = f.session.playerState(1 - slot).capture();
        f.session.levelState(slot).setTimerFrames(999);
        actor.setHighPriority(false); actor.setLayer((byte) 0);
        actor.setTopSolidBit((byte) 0xC); actor.setLrbSolidBit((byte) 0xD);
        actor.setObjectControlled(true); actor.setMoveLockTimer(123);
        actor.setSubpixelRaw(0x1234, 0x5678);
        actor.setXSpeed((short) 0x400); actor.setYSpeed((short) 0x900); actor.setGSpeed((short) 0x700);
        int lives = f.session.gameState(slot).getLives();
        f.putBelowOwnDeathRow(slot);
        // MOVE.W to y_pos in the kill setup above clears only test setup's fraction; reseat it.
        actor.setSubpixelRaw(0x1234, 0x5678);
        assertTrue(f.lifecycle.runDeathPass(actor));
        assertEquals(RESPAWNING, f.lifecycle.routine(slot));
        assertFalse(actor.getDead());
        assertEquals(10, actor.getObjectRoutineOverride());
        assertEquals(0x800, actor.getCentreX()); assertEquals(0x500, actor.getCentreY());
        assertEquals(0x1234, actor.getXSubpixelRaw()); assertEquals(0x5678, actor.getYSubpixelRaw());
        assertEquals(0, actor.getXSpeed()); assertEquals(0x38, actor.getYSpeed()); assertEquals(0, actor.getGSpeed());
        assertTrue(actor.getAir()); assertFalse(actor.isObjectControlled()); assertEquals(0, actor.getMoveLockTimer());
        assertEquals(5, actor.getAnimationId()); assertTrue(actor.isHighPriority()); assertEquals(1, actor.getLayer());
        assertEquals(0xE, actor.getTopSolidBit()); assertEquals(0xF, actor.getLrbSolidBit());
        assertEquals(lives - 1, f.session.gameState(slot).getLives());
        assertEquals(0, f.session.levelState(slot).getRings());
        assertEquals(0, f.session.levelState(slot).getRingExtraLifeFlags());
        assertEquals(999, f.session.levelState(slot).getTimerFrames());
        assertFalse(f.session.levelState(slot).isTimerPaused());
        assertEquals(checkpoint, f.lifecycle.checkpoint(slot));
        assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                "other-player", otherBefore, f.actor(1 - slot).captureRewindState()));
        assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                "other-progress", otherProgress, f.session.playerState(1 - slot).capture()));
        assertTrue(f.cardRequests.isEmpty());
        assertFalse(f.session.camera(slot).getFrozen());
        verify(f.actors.level, never()).requestRespawn();
    }

    @Test void nativeMovementHookInterceptsDeadAndAliveRespawningBeforeOrdinaryDispatch() throws Exception {
        var f = new LifecycleFixture();
        assertSame(f.lifecycle, NativePlayerDeathPass.from(f.actor(1)));
        f.putBelowOwnDeathRow(1);
        var movement = (PlayableSpriteMovement) f.actor(1).getMovementManager();
        movement.handleMovement(false, false, false, true, true, false, false, false);
        assertEquals(RESPAWNING, f.lifecycle.routine(1));
        int x = f.actor(1).getCentreX(), y = f.actor(1).getCentreY();
        f.lifecycle.recordCameraDisplacement(1, -16, 0);
        movement.handleMovement(false, false, false, true, true, false, false, false);
        assertEquals(x, f.actor(1).getCentreX()); assertEquals(y, f.actor(1).getCentreY());
        assertEquals(RESPAWNING, f.lifecycle.routine(1));
        f.lifecycle.recordCameraDisplacement(1, 0, -1);
        assertTrue(f.lifecycle.runDeathPass(f.actor(1)));
        assertEquals(RESPAWNING, f.lifecycle.routine(1));
        f.lifecycle.recordCameraDisplacement(1, 0x10000, 0x10000);
        assertTrue(f.lifecycle.runDeathPass(f.actor(1)));
        assertEquals(CONTROL, f.lifecycle.routine(1));
        assertEquals(5, f.actor(1).getForcedAnimationId(), "This routine-A call still animates byte5");
        assertFalse(f.lifecycle.runDeathPass(f.actor(1)));
        assertEquals(-1, f.actor(1).getForcedAnimationId());
        verify(f.actors.level, never()).requestRespawn();
    }

    @Test void signedThresholdIsStrictAndDeadFallUsesBothOldVelocitiesAndWordGravity() throws Exception {
        var f = new LifecycleFixture();
        var actor = f.actor(0);
        actor.setDead(true); actor.setCentreX((short) 500);
        actor.setCentreY((short) (f.session.camera(0).getMaxY() + 0x100));
        actor.setSubpixelRaw(0xF000, 0x8000);
        actor.setXSpeed((short) 0x180); actor.setYSpeed((short) -0x80);
        assertTrue(f.lifecycle.runDeathPass(actor));
        assertEquals(DEAD, f.lifecycle.routine(0)); assertEquals(3, f.session.gameState(0).getLives());
        assertEquals(502, actor.getCentreX()); assertEquals(0x7000, actor.getXSubpixelRaw());
        assertEquals(0x700, actor.getCentreY()); assertEquals(0, actor.getYSubpixelRaw());
        assertEquals(-0x48, actor.getYSpeed()); assertTrue(f.session.camera(0).getFrozen());
        f.session.camera(0).setMaxY((short) 0x7F80);
        actor.setCentreY((short) 0x100); actor.setYSpeed((short) 0);
        f.lifecycle.runDeathPass(actor);
        assertEquals(RESPAWNING, f.lifecycle.routine(0), "ADDI.W wraps the signed threshold");
    }

    @Test void zeroLivesUnderflowUsesNativeByteAndNeverInventsGameOver() throws Exception {
        var f = new LifecycleFixture(); f.lives(0, 0); f.die(0);
        assertEquals(255, f.session.gameState(0).getLives());
        assertEquals(RESPAWNING, f.lifecycle.routine(0)); assertTrue(f.cardRequests.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void finalLifeClearsOnlyItsTimeOverAndFreezesBothTimerBanks(int slot) throws Exception {
        var f = new LifecycleFixture(); f.lives(slot, 1);
        f.timeout(slot); f.session.levelState(1 - slot).resumeTimer();
        f.die(slot);
        assertEquals(0, f.session.gameState(slot).getLives());
        assertFalse(f.session.levelState(slot).isTimeOver());
        assertEquals(List.of(slot + ":false"), f.cardRequests);
        assertEquals(GONE, f.lifecycle.routine(slot));
        assertEquals(0, f.actor(slot).getDeathCountdown());
        assertTrue(f.session.levelState(0).isTimerPaused()); assertTrue(f.session.levelState(1).isTimerPaused());
        assertEquals(0xB000 + slot * 0x40, f.card(2).getNativeParentWord());
        int y = f.actor(slot).getCentreY(); f.lifecycle.runDeathPass(f.actor(slot));
        assertEquals(y, f.actor(slot).getCentreY()); assertTrue(f.actor(slot).isHidden());
        verify(f.actors.level, never()).requestRespawn();
    }

    @Test void rev01BothTimeoutFlagsKeepP1CardWhileP2EntersGoneWithoutAnotherRequest() throws Exception {
        var f = new LifecycleFixture(); f.timeout(0); f.timeout(1);
        f.actor(1).setDeathCountdown(17);
        f.die(0); var word = f.card(2); f.die(1);
        assertEquals(List.of("0:true"), f.cardRequests); assertSame(word, f.card(2));
        assertEquals(GONE, f.lifecycle.routine(0)); assertEquals(GONE, f.lifecycle.routine(1));
        assertEquals(0, f.actor(1).getDeathCountdown());
        assertTrue(f.session.levelState(0).isTimeOver()); assertTrue(f.session.levelState(1).isTimeOver());
    }

    @Test void rev00BothTimeoutFlagsRespawnTailsAndRetainItsCountdownInsteadOfEnteringGone() throws Exception {
        String source = System.getProperty("sonic2.rev00.rom.path");
        org.junit.jupiter.api.Assumptions.assumeTrue(source != null && !source.isBlank(),
                "Requires an original REV00 ROM via sonic2.rev00.rom.path");
        try (var rom = new com.openggf.data.Rom()) {
            assertTrue(rom.open(source));
            assertEquals(com.openggf.data.RetailRomRevision.S2_REV00,
                    com.openggf.data.RetailRomRevision.detect(rom));
            var f = new LifecycleFixture(rom);
            f.timeout(0); f.timeout(1);
            f.actor(1).setDeathCountdown(17);
            f.die(0); var word = f.card(2); f.die(1);
            assertEquals(List.of("0:true"), f.cardRequests);
            assertSame(word, f.card(2));
            assertEquals(GONE, f.lifecycle.routine(0));
            assertEquals(RESPAWNING, f.lifecycle.routine(1));
            assertEquals(17, f.actor(1).getDeathCountdown());
            assertFalse(f.actor(1).getDead());
            assertTrue(f.session.levelState(1).isTimeOver());
            assertTrue(f.session.levelState(0).isTimerPaused());
            assertTrue(f.session.levelState(1).isTimerPaused());
        }
    }

    @Test void p1TimeoutAloneDoesNotTurnP2OrdinaryDeathIntoAnotherTimeCard() throws Exception {
        var f = new LifecycleFixture(); f.timeout(0); f.die(0); f.die(1);
        assertEquals(List.of("0:true"), f.cardRequests); assertEquals(RESPAWNING, f.lifecycle.routine(1));
        assertTrue(f.session.levelState(1).isTimerPaused(), "Independent respawn does not re-enable a paused bank");
    }

    @Test void revisionZeroRetainedLauncherRoutineWriteResumesOnlyTheNativeDispatcherBank() throws Exception {
        String source = System.getProperty("sonic2.rev00.rom.path");
        org.junit.jupiter.api.Assumptions.assumeTrue(source != null && !source.isBlank(),
                "Requires an original REV00 ROM via sonic2.rev00.rom.path");
        try (var rom = new com.openggf.data.Rom()) {
            assertTrue(rom.open(source));
            assertEquals(com.openggf.data.RetailRomRevision.S2_REV00,
                    com.openggf.data.RetailRomRevision.detect(rom));
            var f = new LifecycleFixture(rom);
            var actor = f.actor(0);
            actor.setDead(true);
            f.lifecycle.runDeathPass(actor);
            assertEquals(DEAD, f.lifecycle.routine(0));
            var progress = f.session.playerState(0).capture();
            var dead = f.lifecycle.capture();
            // Obj85 owns these actor writes alongside its primary-routine write.
            actor.setDead(false); actor.setHurt(false); actor.setObjectRoutineOverride(null);
            f.lifecycle.resumeControlFromLauncher(actor);
            assertEquals(CONTROL, f.lifecycle.routine(0));
            assertFalse(f.lifecycle.runDeathPass(actor));
            var resumed = f.session.playerState(0).capture();
            assertEquals(progress.rings(), resumed.rings());
            assertEquals(progress.totalRings(), resumed.totalRings());
            assertEquals(progress.monitors(), resumed.monitors());
            assertEquals(progress.frames(), resumed.frames());
            assertEquals(progress.game().lives(), resumed.game().lives());
            assertEquals(progress.game().score(), resumed.game().score());
            assertTrue(f.cardRequests.isEmpty());
            f.lifecycle.restore(dead);
            assertEquals(DEAD, f.lifecycle.routine(0));
            f.lifecycle.resumeControlFromLauncher(actor);
            assertFalse(f.lifecycle.runDeathPass(actor));
            assertEquals(CONTROL, f.lifecycle.routine(0));
        }
        var later = new LifecycleFixture();
        assertThrows(IllegalStateException.class,
                () -> later.lifecycle.resumeControlFromLauncher(later.actor(0)));
    }

    @Test void healthyCpuForeignAndRetiredActorsCannotAcquireNativeDeathAuthority() throws Exception {
        var f = new LifecycleFixture();
        assertFalse(f.lifecycle.runDeathPass(f.actor(0)));
        var other = new LifecycleFixture(); other.putBelowOwnDeathRow(0);
        assertFalse(f.lifecycle.runDeathPass(other.actor(0)));
        var old = f.actor(0); f.actors.replacePlayers(); old.setDead(true);
        assertFalse(f.lifecycle.runDeathPass(old));
        f.actor(1).setCpuControlled(true); f.actor(1).setDead(true);
        assertFalse(f.lifecycle.runDeathPass(f.actor(1)));
    }

    @Test void destructiveActorReplacementRestoresSlotPhaseAndForwardsWithoutTouchingRetiredActor() throws Exception {
        var f = new LifecycleFixture(); f.die(1); f.lifecycle.recordCameraDisplacement(1, -8, 12);
        var life = f.lifecycle.capture(); var session = f.session.capture();
        var player = f.actor(1).captureRewindState(); var retired = f.actor(1);
        f.lifecycle.recordCameraDisplacement(1, 0, 0); f.lifecycle.runDeathPass(retired); f.lifecycle.runDeathPass(retired);
        f.actors.replacePlayers(); f.session.restore(session); f.actor(1).restoreRewindState(player);
        f.lifecycle.restore(life);
        assertTrue(f.lifecycle.runDeathPass(f.actor(1))); assertEquals(RESPAWNING, f.lifecycle.routine(1));
        assertFalse(f.lifecycle.runDeathPass(retired));
        f.lifecycle.recordCameraDisplacement(1, 0, 0); f.lifecycle.runDeathPass(f.actor(1));
        assertEquals(CONTROL, f.lifecycle.routine(1));
        assertEquals(2, f.session.gameState(1).getLives());
        assertEquals(Sonic2CompetitionSession.Phase.ACT, f.session.phase());
    }
}
