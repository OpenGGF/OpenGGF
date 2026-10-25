package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.competition.Sonic2CompetitionMonitorContents;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.Sonic2CompetitionObjectFixture;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestSonic2CompetitionMonitorConsumer {
    private Sonic2CompetitionObjectFixture f;
    @BeforeEach void setUp() { GameServices.graphics().initHeadless(); f = new Sonic2CompetitionObjectFixture(); }

    private MonitorObjectInstance monitor() {
        var monitor = new MonitorObjectInstance(new ObjectSpawn(0x100, 0x100, 0x26, 4, 0, false, 0), "Monitor");
        monitor.setServices(f.services);
        monitor.update(999, f.p1);
        return monitor;
    }
    private void breakBy(MonitorObjectInstance monitor, int slot) {
        var player = f.session.player(slot);
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        player.setYSpeed((short) 0x100);
        monitor.onTouchResponse(player, new TouchResponseResult(0, 14, 14, TouchCategory.SPECIAL), 777);
    }
    private Sonic2CompetitionMonitorContents contents() {
        return f.objects.activeObjectsOfType(Sonic2CompetitionMonitorContents.class).getFirst();
    }
    private void finishIcon(Sonic2CompetitionMonitorContents icon) {
        for (int i = 0; i < 33; i++) icon.update(9000 + i, f.p1);
    }

    @Test void p2UsesNativeRollSolidityAndItsOwnContactBits() {
        var monitor = monitor();
        f.p2.setAnimationId(Sonic2AnimationIds.ROLL);
        assertFalse(monitor.isSolidFor(f.p2));
        monitor.onSolidContact(f.p2, new SolidContact(true, false, false, true, false), 1);
        breakBy(monitor, 1);
        assertEquals(0, monitor.getCollisionFlags());
        assertTrue(f.p2.getAir());
        assertFalse(f.p1.getAir());
        assertEquals(1, f.objects.activeObjectsOfType(Sonic2CompetitionMonitorContents.class).size());
    }

    @Test void contentsSampleLevelClockAtFirstExecutionAndAwardOnlyAtApex() {
        f.levelFrame = 0;
        breakBy(monitor(), 1);
        var icon = contents();
        f.levelFrame = 3; // Actual first Obj2E execution selects Super Ring, regardless of V-int.
        icon.update(7, f.p1);
        f.levelFrame = 0;
        for (int i = 0; i < 31; i++) icon.update(i, f.p1);
        assertEquals(0, f.session.playerState(1).getRings());
        icon.update(32, f.p1);
        assertEquals(10, f.session.playerState(1).getRings());
        assertEquals(10, f.session.playerState(1).totalRings());
        assertEquals(1, f.session.playerState(1).monitorsBroken());
        assertEquals(0, f.session.playerState(0).getRings());
        assertEquals(0, f.ordinary.getRings());
        finishIcon(icon);
        assertEquals(10, f.session.playerState(1).getRings());
    }

    @Test void childAllocatedBehindMonitorSamplesClockOnlyAfterThePassedSlotDelay() {
        var monitor = f.objects.createDynamicObjectAtSlot(() -> new MonitorObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x26, 4, 0, false, 0), "Monitor"), 26);
        assertNotNull(monitor);
        monitor.update(0, f.p1);
        breakBy(monitor, 1);
        var icon = contents();
        assertTrue(icon.getSlotIndex() < monitor.getSlotIndex());
        f.levelFrame = 0;
        icon.update(0, f.p1); // Passed cursor: no Obj2E initialization yet.
        f.levelFrame = 3;
        finishIcon(icon);
        assertEquals(10, f.session.playerState(1).getRings());
        assertEquals(3, f.session.gameState(0).getLives());
    }

    @Test void namedLifeAndRingThresholdAwardsUseNativeCountersWithoutDoubleCredit() {
        for (int content = 1; content <= 2; content++) {
            f.levelFrame = content - 1;
            breakBy(monitor(), 1 - (content - 1));
            finishIcon(contents());
            assertEquals(4, f.session.gameState(content - 1).getLives());
            assertEquals(1, f.session.playerState(content - 1).monitorsBroken());
            f.objects.reset(0);
        }
        f.levelFrame = 3;
        f.session.playerState(1).setRings(95);
        breakBy(monitor(), 1); finishIcon(contents());
        assertEquals(105, f.session.playerState(1).getRings());
        assertEquals(5, f.session.gameState(1).getLives());
        assertEquals(3, f.session.playerState(1).monitorsBroken());
        assertEquals("music:EXTRA_LIFE", f.events.getLast());
        assertEquals(3, f.ordinaryGame.getLives());
    }

    @Test void nativeActorEffectsApplyToBreakerAndTeleportIsOnlyAHostRequest() {
        f.session.playerState(1).setRings(1);
        for (int content = 3; content <= 8; content++) {
            f.levelFrame = content - 1;
            breakBy(monitor(), 1); finishIcon(contents());
            if (content == 3) assertTrue(f.p2.isHurt());
            if (content == 5) assertTrue(f.p2.hasSpeedShoes());
            if (content == 6) assertTrue(f.p2.hasShield());
            if (content == 7) assertTrue(f.p2.getInvincibleFrames() > 0);
            f.objects.reset(0);
        }
        assertEquals(1, f.teleports);
        assertEquals(6, f.session.playerState(1).monitorsBroken());
        assertFalse(f.p1.hasSpeedShoes());
        assertFalse(f.p1.hasShield());
        assertEquals(0x100, f.p1.getX());
        assertEquals(0x100, f.p2.getX());
    }

    @Test void robotnikMonitorRunsDamageGatesRingLossShieldLossAndDeath() {
        f.levelFrame = 2;
        f.session.playerState(0).setRings(19);
        f.session.playerState(1).setRings(3);
        breakBy(monitor(), 1); finishIcon(contents());
        assertEquals(0, f.session.playerState(1).getRings());
        assertEquals(19, f.session.playerState(0).getRings());
        assertTrue(f.p2.isHurt()); assertEquals(0x78, f.p2.getInvulnerableFrames());
        assertEquals(-0x400, f.p2.getYSpeed());
        assertEquals(3, f.objects.activeObjectsOfType(com.openggf.level.rings.LostRingObjectInstance.class).size());

        f = new Sonic2CompetitionObjectFixture(); f.levelFrame = 2;
        f.p2.giveShield(); breakBy(monitor(), 1); finishIcon(contents());
        assertFalse(f.p2.hasShield()); assertTrue(f.p2.isHurt()); assertFalse(f.p2.getDead());

        f = new Sonic2CompetitionObjectFixture(); f.levelFrame = 2;
        breakBy(monitor(), 1); finishIcon(contents());
        assertTrue(f.p2.getDead()); assertEquals(-0x700, f.p2.getYSpeed());

        f = new Sonic2CompetitionObjectFixture(); f.levelFrame = 2;
        f.session.playerState(1).setRings(3);
        f.p2.giveInvincibility(); breakBy(monitor(), 1); finishIcon(contents());
        assertFalse(f.p2.isHurt()); assertFalse(f.p2.getDead());
        assertEquals(3, f.session.playerState(1).getRings());
        assertEquals(1, f.session.playerState(1).monitorsBroken());
    }

    @Test void robotnikMonitorWithFullRamPreservesTheUnallocatedNativeRingBank() {
        f.levelFrame = 2; f.session.playerState(1).setRings(3);
        breakBy(monitor(), 1);
        var icon = contents();
        f.objects.reserveAllButNFreeSlots(0);
        finishIcon(icon);
        assertTrue(f.p2.isHurt());
        assertEquals(3, f.session.playerState(1).getRings());
        assertTrue(f.objects.activeObjectsOfType(com.openggf.level.rings.LostRingObjectInstance.class).isEmpty());
    }

    @Test void robotnikMonitorUndoesCurrentNativeYStepIncludingSubpixelBeforeKnockback() {
        f.levelFrame = 2; f.session.playerState(1).setRings(3);
        breakBy(monitor(), 1);
        var icon = contents();
        for (int i = 0; i < 32; i++) icon.update(i, f.p1);
        f.p2.setRolling(false);
        f.p2.setYSpeed((short) 0x181);
        f.p2.setSubpixelRaw(f.p2.getXSubpixelRaw(), 0x8000);
        int expectedY = ((f.p2.getCentreY() << 16) | f.p2.getYSubpixelRaw()) - (0x181 << 8);
        icon.update(32, f.p1);
        assertEquals(expectedY, (f.p2.getCentreY() << 16) | f.p2.getYSubpixelRaw());
        assertTrue(f.p2.isHurt());
    }

    @Test void invincibilityAwardPreservesBossMusicWhileGrantingNativeProtection() {
        f.levelFrame = 6;
        f.session.gameState(0).setCurrentBossId(1);
        breakBy(monitor(), 1); finishIcon(contents());
        assertEquals(1200, f.p2.getInvincibleFrames());
        assertFalse(f.events.contains("music:INVINCIBILITY"));
    }

    @Test void displacedNativeMonitorCreatesItsContentsAtTheCurrentY() throws Exception {
        var monitor = monitor();
        var currentY = MonitorObjectInstance.class.getDeclaredField("currentY");
        currentY.setAccessible(true); currentY.setInt(monitor, 0x140);
        breakBy(monitor, 1);
        assertEquals(0x140, contents().getSpawn().y());
    }

    @Test void teleportReplacementIsLatchedAtInitAndARealHostIsRequired() {
        f.teleportOnly = true;
        breakBy(monitor(), 1);
        var selectedTeleport = contents();
        selectedTeleport.update(0, f.p1);
        f.session.observeGoal(0, 100, 100);
        finishIcon(selectedTeleport);
        assertEquals(1, f.teleports, "A later finish does not rewrite already-selected content8");
        f.objects.reset(0);
        breakBy(monitor(), 1); finishIcon(contents());
        assertEquals(1, f.teleports, "A stopped timer at initialization substitutes invincibility");
        assertTrue(f.p2.getInvincibleFrames() > 0);
        f.objects.reset(0); f.hostPresent = false;
        breakBy(monitor(), 1);
        assertThrows(IllegalStateException.class, () -> contents().update(0, f.p1));
    }

    @Test void failedChildAllocationNeverAwardsAndOrdinaryCpuStillCannotBreak() {
        f.objects.reserveAllButNFreeSlots(0);
        breakBy(monitor(), 1);
        assertTrue(f.objects.activeObjectsOfType(Sonic2CompetitionMonitorContents.class).isEmpty());
        assertEquals(0, f.session.playerState(1).monitorsBroken());
        var ordinary = new MonitorObjectInstance(new ObjectSpawn(100, 100, 0x26, 4, 0, false, 0), "Monitor");
        ordinary.setServices(new StubObjectServices());
        f.p2.setCpuControlled(true);
        breakBy(ordinary, 1);
        assertEquals(0x46, ordinary.getCollisionFlags());
        assertTrue(ordinary.isSolidFor(f.p2));
    }

    @Test void pendingIconSurvivesActorRemovalAndGenericObjectRecreationThenReplays() {
        f.levelFrame = 5;
        breakBy(monitor(), 1);
        var original = contents();
        for (int i = 0; i < 14; i++) original.update(i, f.p1);
        var state = f.session.capture();
        var objects = f.objects.rewindSnapshottable().capture();
        finishIcon(original); assertTrue(f.p2.hasShield());
        f.objects.reset(0); f.replacePlayers(); f.session.restore(state);
        f.objects.setRewindInPlaceRestoreEnabledForTest(false);
        f.objects.rewindSnapshottable().restore(objects);
        var restored = contents();
        assertNotSame(original, restored);
        f.levelFrame = 0;
        finishIcon(restored);
        assertTrue(f.p2.hasShield()); assertFalse(f.p1.hasShield());
        assertEquals(1, f.session.playerState(1).monitorsBroken());
    }
}
