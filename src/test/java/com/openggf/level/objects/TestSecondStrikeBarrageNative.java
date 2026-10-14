package com.openggf.level.objects;

import com.openggf.game.ShieldType;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import static com.openggf.level.objects.SecondStrikeBarrageFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated creator barrage regressions; no campaign-route or powered callback shortcuts. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeBarrageNative {
    @TempDir static Path temporary;
    private static URLClassLoader creator;
    private SecondStrikeBarrageFixture f;
    @BeforeAll static void compile() throws Exception { creator = SecondStrikeBarrageFixture.compile(temporary); }
    @AfterAll static void close() throws Exception { if (creator != null) creator.close(); }
    @BeforeEach void setup() throws Exception { f = new SecondStrikeBarrageFixture(creator); }
    @AfterEach void cleanup() { AbstractObjectInstance.resetCameraBoundsForTests(); SessionManager.clear(); }

    @Test void twoCommittedFansCoverBothSidesAndHighLowApproachesAfterTheWholeTell() throws Exception {
        var gunner = f.gunner();
        gunner.update(0, f.player);
        assertEquals(List.of(0x53), f.sounds);
        ticks(gunner, f.player, 35);
        assertTrue(f.shots().isEmpty(), "all 36 committed tell updates give a dodge opportunity");
        place(f.player, 260, 220); // Crossing and jumping during the tell cannot redirect release.
        gunner.update(36, f.player);
        var first = f.shots();
        assertEquals(10, first.size(), "five high/low seed lanes on each side");
        assertTrue(first.getFirst().getX() > 400, "scarce slots still prefer the direction committed before crossing");
        assertEquals(5, first.stream().filter(s -> s.getX() < 400).count());
        assertEquals(5, first.stream().filter(s -> s.getX() > 400).count());
        for (var shot : first) ticks(shot, f.player, 32);
        assertTrue(first.stream().anyMatch(s -> s.getX() <= 276 && s.getY() >= 300), "low left approach");
        assertTrue(first.stream().anyMatch(s -> s.getX() >= 524 && s.getY() >= 300), "low right approach");
        assertTrue(first.stream().anyMatch(s -> s.getX() < 380 && s.getY() <= 200), "high left approach");
        assertTrue(first.stream().anyMatch(s -> s.getX() > 420 && s.getY() <= 200), "high right approach");
        ticks(gunner, f.player, 11);
        assertEquals(10, f.shots().size());
        gunner.update(48, f.player);
        assertEquals(20, f.shots().size());
        assertEquals(2, f.fireCues(), "one native launch cue per admitted release");
        ticks(gunner, f.player, 108);
        assertEquals(20, f.shots().size(), "full 108-update stationary punish/reload window");
        f.firstRelease(gunner);
        assertEquals(20, f.shots().size(), "live per-owner cap survives missed child updates");
        assertEquals(2, f.fireCues());
    }

    @Test void fullNativeSstRejectsConstructedShotsWithoutLaunchCueOrRetry() throws Exception {
        var gunner = f.gunner();
        f.exhaustLeaving(0);
        f.firstRelease(gunner);
        assertTrue(f.shots().isEmpty());
        assertEquals(0, f.fireCues(), "nonnull construction is not native SST admission");
        var filler = f.manager.getActiveObjects().stream().filter(o -> o.getName().equals("slot filler")).findFirst().orElseThrow();
        f.manager.removeDynamicObject(filler);
        ticks(gunner, f.player, 11);
        assertTrue(f.shots().isEmpty(), "a lost release is never silently retried");
        gunner.update(48, f.player);
        assertEquals(1, f.shots().size(), "second release consumes only actual available capacity");
        assertEquals(1, f.fireCues());
    }

    @Test void partialNativeAdmissionUsesOneCueAndDoesNotOvercountRejectedConstruction() throws Exception {
        var gunner = f.gunner();
        f.exhaustLeaving(3);
        f.firstRelease(gunner);
        assertEquals(3, f.shots().size());
        assertEquals(1, f.fireCues());
        for (var shot : f.shots()) {
            assertTrue(shot.getSlotIndex() >= 0);
            var identity = f.manager.objectQuery().identityOf(shot).orElseThrow();
            assertSame(shot, f.manager.objectQuery().resolve(identity).orElseThrow());
        }
    }

    @Test void colocatedParentsAndFreshRespawnHaveSeparateCapturedBudgets() throws Exception {
        var first = f.gunner(); var second = f.gunner();
        int firstSlot = first.getSlotIndex();
        var firstId = f.manager.objectQuery().identityOf(first).orElseThrow();
        var secondId = f.manager.objectQuery().identityOf(second).orElseThrow();
        assertNotEquals(firstId, secondId);
        f.firstRelease(first); ticks(first, f.player, 12);
        var firstSeeds = List.copyOf(f.shots());
        f.firstRelease(second); ticks(second, f.player, 12);
        assertEquals(40, f.shots().size(), "same coordinates do not merge two exact owner budgets");
        f.manager.removeDynamicObject(first);
        assertTrue(firstSeeds.stream().allMatch(AbstractObjectInstance::isDestroyed));
        assertEquals(20, f.shots().size(), "unload preserves the colocated second owner's exact seeds");
        var respawn = f.gunner();
        assertEquals(firstSlot, respawn.getSlotIndex());
        assertNotEquals(firstId, f.manager.objectQuery().identityOf(respawn).orElseThrow());
        f.firstRelease(respawn);
        assertEquals(30, f.shots().size(), "respawn cannot inherit the old owner's 20-seed cap");
        for (var shot : List.copyOf(f.shots())) shot.update(0, f.player);
        assertEquals(30, f.shots().size(), "unloaded owner's old seeds retire despite colocated live replacements");
    }

    @Test void actualNativeShieldTouchDeflectsRadiallyAndClearsHurtAuthority() throws Exception {
        var gunner = f.gunner(); f.firstRelease(gunner);
        var shot = f.shots().stream().filter(s -> s.getX() > 400).findFirst().orElseThrow();
        // Keep one admitted seed; use the native frozen response list, not its callback directly.
        for (var other : List.copyOf(f.shots())) if (other != shot) f.manager.removeDynamicObject(other);
        f.manager.removeDynamicObject(gunner);
        // Use a separately spawned seed without a lifetime owner for the isolated shield interaction.
        f.manager.removeDynamicObject(shot);
        shot = f.enemy("CommittedShot", "shot", 500, 300);
        f.player.giveShield(ShieldType.LIGHTNING);
        place(f.player, 484, 300);
        f.manager.update(0, f.player, List.of(), 1, false);
        f.manager.runTouchResponsesForPlayer(f.player, 2);
        assertTrue(f.player.hasShield());
        assertFalse(f.player.isHurt());
        assertEquals(0, touch(shot).getCollisionFlags());
        assertEquals(0x800, f.velocity(shot, "horizontalVelocity"));
        assertEquals(0, f.velocity(shot, "verticalVelocity"));
        ticks(shot, f.player, 2);
        assertEquals(0, touch(shot).getCollisionFlags(), "reflected visuals cannot hurt again");
        replayFrom(f.manager.rewindSnapshottable().capture(), 12);
    }

    @Test void finalNativeHitKeepsCommittedSeedsButUnloadImmediatelyRemovesTheirAuthority() throws Exception {
        var gunner = f.gunner(); f.firstRelease(gunner);
        var seeds = f.shots();
        f.manager.update(0, f.player, List.of(), 1, false);
        f.attack(gunner, 2);
        assertFalse(gunner.isDestroyed()); assertEquals(0, touch(gunner).getCollisionProperty());
        place(f.player, 900, 600); ticks(gunner, f.player, 24);
        f.attack(gunner, 3);
        assertTrue(gunner.isDestroyed()); assertEquals(100, f.score.getScore());
        f.manager.removeDynamicObject(gunner);
        for (var seed : seeds) seed.update(4, f.player);
        assertEquals(10, f.shots().size(), "already committed attacks survive a rewarded final native hit");
        var unloaded = f.gunner(); f.firstRelease(unloaded);
        var oldSeeds = f.shots().stream().filter(s -> !seeds.contains(s)).toList();
        unloaded.setDestroyedByOffscreen();
        for (var seed : oldSeeds) assertEquals(0, touch(seed).getCollisionFlags(), "same-pass unload removes hurt authority");
        f.manager.removeDynamicObject(unloaded);
        for (var seed : oldSeeds) seed.update(5, f.player);
        assertTrue(oldSeeds.stream().allMatch(AbstractObjectInstance::isDestroyed));
        assertEquals(100, f.score.getScore(), "owner lifetime retirement gives no reward");
        // Isolate the surviving committed seeds from native reward effects for a released-owner replay.
        for (var object : List.copyOf(f.manager.getActiveObjects())) if (!seeds.contains(object))
            f.manager.removeDynamicObject(object);
        // Normal completed dispatch refreshes both collision-list views after the manual removals.
        f.manager.update(0, f.player, List.of(), 6, false);
        replayFrom(f.manager.rewindSnapshottable().capture(), 16);
    }

    @Test void nativePoweredScreenKeepsStockEnemyGateAndDeflectsHurtSeeds() throws Exception {
        var gunner = f.gunner(); f.firstRelease(gunner);
        f.manager.update(0, f.player, List.of(), 1, false);
        f.manager.poweredAttacks().apply(f.player);
        assertEquals(1, touch(gunner).getCollisionProperty(), "stock screen eligibility skips nonzero enemy property");
        assertEquals(10, f.shots().size(), "native screen deflection preserves projectile slots, not enemy kills");
        for (var seed : f.shots()) assertEquals(0, touch(seed).getCollisionFlags(),
                "stock HyperAttackTouchResponse deflects HURT seeds with shield_reaction bit3");
        f.attack(gunner, 2);
        place(f.player, 900, 600); ticks(gunner, f.player, 24);
        f.manager.update(0, f.player, List.of(), 3, false);
        f.manager.poweredAttacks().apply(f.player);
        assertTrue(gunner.isDestroyed()); assertEquals(100, f.score.getScore());
        for (var seed : f.shots()) assertEquals(0, touch(seed).getCollisionFlags());
    }

    @Test void completeWindupVolleyAndFractionalShotGraphRecreatesAndReplaysForward() throws Exception {
        var gunner = f.gunner();
        ticks(gunner, f.player, 20);
        var tell = f.manager.rewindSnapshottable().capture();
        replayFrom(tell, 35);
        assertEquals(20, f.shots().size());
        var flight = f.manager.rewindSnapshottable().capture();
        replayFrom(flight, 20);
    }

    private void replayFrom(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot captured, int steps) {
        f.sounds.clear();
        for (int i = 0; i < steps; i++) f.manager.update(0, f.player, List.of(), 100 + i, false);
        var expected = f.manager.rewindSnapshottable().capture();
        var expectedSounds = List.copyOf(f.sounds);
        for (var object : List.copyOf(f.manager.getActiveObjects())) f.manager.removeDynamicObject(object);
        f.manager.rewindSnapshottable().restore(captured); f.bounds();
        var restored = f.manager.rewindSnapshottable().capture();
        assertEquals(captured.dynamicObjects(), restored.dynamicObjects());
        assertArrayEquals(captured.usedSlotsBits(), restored.usedSlotsBits());
        f.sounds.clear();
        for (int i = 0; i < steps; i++) f.manager.update(0, f.player, List.of(), 100 + i, false);
        var actual = f.manager.rewindSnapshottable().capture();
        assertEquals(expected.dynamicObjects(), actual.dynamicObjects(), "identities, ownership, gravity, fan cursor and fractions");
        assertArrayEquals(expected.usedSlotsBits(), actual.usedSlotsBits());
        assertEquals(expected.dynamicObjectIdCounter(), actual.dynamicObjectIdCounter());
        assertEquals(expectedSounds, f.sounds);
    }

    @Test void ttlOffscreenAndActualRomFloorRetireSpentSeeds() throws Exception {
        var ttl = f.enemy("CommittedShot", "shot", 500, 200);
        ticks(ttl, f.player, 119); assertFalse(ttl.isDestroyed());
        ttl.update(120, f.player); assertTrue(ttl.isDestroyed());
        var outside = f.enemy("CommittedShot", "shot", 1200, 200);
        outside.update(0, f.player); assertTrue(outside.isDestroyed());
        // Native AIZ1 Bloominator platform floor: center $1DC0,$27C, feet at $28C.
        AbstractObjectInstance.updateCameraBounds(0x1d20, 0x20c, 0x1f00, 0x2ec, 0);
        var floor = f.services.floorSurface(0x1dc0, 0x28c, TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY);
        assertTrue(floor.foundSurface()); assertTrue(floor.distance() >= 0 && floor.distance() < 12);
        var seed = f.enemy("CommittedShot", "shot", 0x1dc0, floor.surfaceY() - 8);
        ticks(seed, f.player, 5); assertTrue(seed.isDestroyed());
        assertEquals(0, touch(seed).getCollisionFlags());
    }
    @Test void actuallyReleasedGravitySeedHurtsUnshieldedNativeP1AndP2() throws Exception {
        var gunner = f.gunner(); f.firstRelease(gunner);
        place(f.player, 900, 600);
        for (int frame = 0; frame < 20; frame++) f.manager.update(0, f.player, List.of(), 100 + frame, false);
        var seed = f.shots().stream().max(java.util.Comparator.comparingInt(AbstractObjectInstance::getX)).orElseThrow();
        assertTrue(seed.getX() > gunner.getX() + 50);
        assertNotEquals(-0x80, f.velocity(seed, "verticalVelocity"), "released fan uses its captured gravity");
        for (var other : List.copyOf(f.shots())) if (other != seed) f.manager.removeDynamicObject(other);
        var tails = new com.openggf.sprites.playable.Tails("tails", (short) 0, (short) 0);
        tails.setCpuControlled(true); tails.setRingCount(1);
        f.services.withSidekicks(List.of(tails)); f.player.setRingCount(1);
        place(f.player, seed.getX(), seed.getY()); place(tails, seed.getX(), seed.getY());
        f.manager.snapshotTouchResponseState();
        f.manager.runTouchResponsesForPlayer(f.player, 130);
        f.manager.runTouchResponsesForPlayer(tails, 130);
        assertTrue(f.player.isHurt()); assertTrue(tails.isHurt());
        assertFalse(gunner.isDestroyed()); assertEquals(0, f.score.getScore());
        assertEquals(0x98, touch(seed).getCollisionFlags());
    }

    @Test void nativePolicyReceivesOneFinalDefeatBeforeSlotAndIdentityTransfer() throws Exception {
        var port = org.mockito.Mockito.mock(com.openggf.game.mutators.NativeObjectPolicyPort.class);
        org.mockito.Mockito.when(port.permitsRoutine(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.when(port.permitsContact(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var state = org.mockito.Mockito.mock(com.openggf.game.session.WorldSessionPolicyState.class);
        org.mockito.Mockito.when(state.getService(com.openggf.game.mutators.NativeObjectPolicyPort.class)).thenReturn(port);
        var module = org.mockito.Mockito.mock(com.openggf.game.GameModule.class);
        org.mockito.Mockito.when(module.getGameService(com.openggf.game.session.WorldSessionPolicyProvider.class))
                .thenReturn(world -> state);
        f.services.withWorldSession(new com.openggf.game.session.WorldSession(module));
        var gunner = f.gunner(); f.firstRelease(gunner);
        var identity = f.manager.objectQuery().identityOf(gunner).orElseThrow();
        org.mockito.Mockito.doAnswer(call -> {
            assertFalse(gunner.isDestroyed()); assertTrue(gunner.getSlotIndex() >= 0);
            assertSame(gunner, f.manager.objectQuery().resolve(identity).orElseThrow());
            return null;
        }).when(port).badnikDefeated(gunner, f.player);
        f.manager.update(0, f.player, List.of(), 1, false);
        f.attack(gunner, 2);
        org.mockito.Mockito.verify(port, org.mockito.Mockito.never()).badnikDefeated(gunner, f.player);
        place(f.player, 900, 600); ticks(gunner, f.player, 24);
        f.attack(gunner, 3);
        org.mockito.Mockito.verify(port).badnikDefeated(gunner, f.player);
        assertEquals(100, f.score.getScore()); assertTrue(gunner.isDestroyed());
        assertTrue(f.shots().stream().allMatch(seed -> touch(seed).getCollisionFlags() == 0x98),
                "committed gravity seeds retain hurt authority after ordinary final defeat");
        f.manager.poweredAttacks().apply(f.player);
        org.mockito.Mockito.verify(port, org.mockito.Mockito.times(1)).badnikDefeated(gunner, f.player);
    }

}
