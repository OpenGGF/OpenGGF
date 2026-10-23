package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.audio.NullAudioBackend;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PowerUpObject;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.level.objects.DefaultPowerUpSpawner;
import com.openggf.level.objects.InvincibilityStarsObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.ShieldObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Sonic2CompetitionObjectFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free native owner relinking through actual object capture, recreation and player refresh. */
class TestSonic2CompetitionPlayerBoundRestore {
    @BeforeEach void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        GameServices.audio().setBackend(new NullAudioBackend());
    }

    @AfterEach void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test void replacedHumansRetainBothPowerUpIdentitiesSlotsStateAndOrderAcrossReversedRepeatedRefreshes() {
        var f = new Sonic2CompetitionObjectFixture();
        var spawner = new DefaultPowerUpSpawner(f.objects);
        for (int slot = 0; slot < 2; slot++) {
            var player = f.session.player(slot);
            player.setPowerUpSpawner(spawner);
            player.giveShield();
            player.giveInvincibility(80 + slot);
            player.setXSpeed((short) (0x123 + slot));
            player.getDrowningController().update();
            tickVisuals(player, slot + 2);
        }
        var sourcePlayers = new AbstractPlayableSprite[]{f.p1, f.p2};
        var playerStates = new PerObjectRewindSnapshot[]{f.p1.captureRewindState(), f.p2.captureRewindState()};
        var adapter = f.objects.rewindSnapshottable();
        var source = adapter.capture();
        assertEquals(4, source.dynamicObjects().size());
        assertEquals(4, source.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId)
                .distinct().count(), "both native humans own distinct shield and stars identities");

        for (int cycle = 0; cycle < 4; cycle++) {
            var retiredP1 = f.p1;
            var retiredP2 = f.p2;
            f.replacePlayers();
            assertNotSame(retiredP1, f.p1);
            assertNotSame(retiredP2, f.p2);
            for (int slot = 0; slot < 2; slot++) {
                var player = f.session.player(slot);
                player.setPowerUpSpawner(new DefaultPowerUpSpawner(f.objects));
                player.restoreRewindState(playerStates[slot]);
            }
            // Generic recreation queues the original captured owners. The pending
            // entries must resolve those retired native identities to the current slots.
            adapter.restore(source);
            int[] refreshOrder = cycle % 2 == 0 ? new int[]{1, 0} : new int[]{0, 1};
            for (int slot : refreshOrder) f.session.player(slot).refreshPowerUpObjectsAfterRewindRestore();
            var restored = adapter.capture();

            assertEquals(source.dynamicObjectIdCounter(), restored.dynamicObjectIdCounter(),
                    "restored player-bound objects must not mint replacement dynamic ordinals");
            assertEquals(source.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId).toList(),
                    restored.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId).toList(),
                    "P2-first recreation must retain the captured dynamic-object order");
            assertArrayEquals(source.usedSlotsBits(), restored.usedSlotsBits());
            assertEquals(4, restored.dynamicObjects().size());
            for (int slot = 0; slot < 2; slot++) {
                var player = f.session.player(slot);
                assertSame(player, f.cameras[slot].getFocusedSprite());
                assertFalse(player.isCpuControlled());
                assertNull(player.getCpuController());
                assertInstanceOf(ShieldObjectInstance.class, player.getShieldObject());
                assertSame(player, player.getInvincibilityObject().boundPlayer());
                assertTrue(player.getShieldObject().isShieldFor(player, ShieldType.BASIC));
                assertFalse(player.getShieldObject().isShieldFor(sourcePlayers[slot], ShieldType.BASIC));
                assertFalse(player.getShieldObject().isDestroyed());
                assertFalse(player.getInvincibilityObject().isDestroyed());
                assertEquals(playerStates[slot].playerExtra().controllerState(),
                        player.captureRewindState().playerExtra().controllerState());
                assertEquals(playerStates[slot].playerExtra().xSpeed(), player.getXSpeed());
                assertEquals(playerStates[slot].playerExtra().invincibleFrames(), player.getInvincibleFrames());
                for (String type : List.of(ShieldObjectInstance.class.getName(), InvincibilityStarsObjectInstance.class.getName())) {
                    var before = entry(source, sourcePlayers[slot], type);
                    var after = entry(restored, player, type);
                    assertSame(player, after.playerOwner());
                    assertNotSame(sourcePlayers[slot], after.playerOwner());
                    assertEquals(before.objectId(), after.objectId());
                    assertEquals(before.slotIndex(), after.slotIndex());
                    assertEquals(before.ownerModId(), after.ownerModId());
                    assertEquals(before.rewindableAuxiliary(), after.rewindableAuxiliary());
                    assertOrdinaryObjectState(before.state(), after.state());
                }
                // Each next cycle restores a genuine earlier production snapshot,
                // including visibly advanced shield/stars animation and controller RAM.
                tickVisuals(player, cycle + 5);
                player.getDrowningController().update();
            }
        }
    }

    @Test void foreignSameCodeRequesterCannotConsumeTheCurrentNativeHumansPendingShieldOrStars() {
        var f = new Sonic2CompetitionObjectFixture();
        var capturedOwner = f.p1;
        var spawner = new DefaultPowerUpSpawner(f.objects);
        var originalShield = spawner.spawnShield(capturedOwner, ShieldType.BASIC);
        var originalStars = spawner.spawnInvincibilityStars(capturedOwner);
        tick(originalShield, capturedOwner, 3);
        tick(originalStars, capturedOwner, 5);
        var adapter = f.objects.rewindSnapshottable();
        var source = adapter.capture();
        var sourceShield = entry(source, capturedOwner, ShieldObjectInstance.class.getName());
        var sourceStars = entry(source, capturedOwner, InvincibilityStarsObjectInstance.class.getName());
        f.replacePlayers();
        adapter.restore(source);
        var foreign = new Sonic(f.p1.getCode(), (short) 0x200, (short) 0x180);
        assertEquals(f.p1.getCode(), foreign.getCode());
        assertEquals(-1, f.session.slotOf(foreign));

        var foreignShield = spawner.spawnShield(foreign, ShieldType.BASIC);
        var foreignStars = spawner.spawnInvincibilityStars(foreign);
        assertNotEquals(sourceShield.objectId(), identity(f, foreignShield));
        assertNotEquals(sourceStars.objectId(), identity(f, foreignStars));
        assertEquals(source.dynamicObjectIdCounter() + 2, adapter.capture().dynamicObjectIdCounter(),
                "an unregistered same-code requester must allocate new identities instead of consuming native pending entries");

        var restoredNativeShield = spawner.spawnShield(f.p1, ShieldType.BASIC);
        var restoredNativeStars = spawner.spawnInvincibilityStars(f.p1);
        assertInstanceOf(ShieldObjectInstance.class, restoredNativeShield);
        assertTrue(restoredNativeShield.isShieldFor(f.p1, ShieldType.BASIC));
        assertFalse(restoredNativeShield.isShieldFor(capturedOwner, ShieldType.BASIC));
        assertSame(f.p1, restoredNativeStars.boundPlayer());
        assertEquals(sourceShield.objectId(), identity(f, restoredNativeShield));
        assertEquals(sourceStars.objectId(), identity(f, restoredNativeStars));
        var restored = adapter.capture();
        assertEquals(sourceShield.slotIndex(), entry(restored, f.p1, sourceShield.className()).slotIndex());
        assertEquals(sourceStars.slotIndex(), entry(restored, f.p1, sourceStars.className()).slotIndex());
        assertOrdinaryObjectState(sourceShield.state(), entry(restored, f.p1, sourceShield.className()).state());
        assertOrdinaryObjectState(sourceStars.state(), entry(restored, f.p1, sourceStars.className()).state());
        assertEquals(source.dynamicObjectIdCounter() + 2, restored.dynamicObjectIdCounter());
    }

    private static ObjectManagerSnapshot.DynamicObjectEntry entry(ObjectManagerSnapshot snapshot,
                                                                  PlayableEntity owner, String type) {
        return snapshot.dynamicObjects().stream()
                .filter(entry -> entry.playerOwner() == owner && type.equals(entry.className()))
                .findFirst().orElseThrow();
    }

    private static ObjectRefId identity(Sonic2CompetitionObjectFixture f, PowerUpObject object) {
        return f.objects.objectQuery().identityOf((ObjectInstance) object).orElseThrow();
    }

    private static void tickVisuals(AbstractPlayableSprite player, int count) {
        tick(player.getShieldObject(), player, count);
        tick(player.getInvincibilityObject(), player, count + 1);
    }

    private static void tick(PowerUpObject object, PlayableEntity owner, int count) {
        for (int frame = 0; frame < count; frame++) ((ObjectInstance) object).update(frame, owner);
    }

    /** Compare ordinary captured RAM separately from the explicitly checked player-owner references. */
    private static void assertOrdinaryObjectState(PerObjectRewindSnapshot expected, PerObjectRewindSnapshot actual) {
        assertEquals(expected.destroyed(), actual.destroyed());
        assertEquals(expected.destroyedRespawnable(), actual.destroyedRespawnable());
        assertEquals(expected.hasDynamicSpawn(), actual.hasDynamicSpawn());
        assertEquals(expected.dynamicSpawnX(), actual.dynamicSpawnX());
        assertEquals(expected.dynamicSpawnY(), actual.dynamicSpawnY());
        assertEquals(expected.preUpdateX(), actual.preUpdateX());
        assertEquals(expected.preUpdateY(), actual.preUpdateY());
        assertEquals(expected.preUpdateValid(), actual.preUpdateValid());
        assertEquals(expected.preUpdateCollisionFlags(), actual.preUpdateCollisionFlags());
        assertEquals(expected.skipTouchThisFrame(), actual.skipTouchThisFrame());
        assertEquals(expected.solidContactFirstFrame(), actual.solidContactFirstFrame());
        assertEquals(expected.slotIndex(), actual.slotIndex());
        assertEquals(expected.respawnStateIndex(), actual.respawnStateIndex());
        assertEquals(expected.genericState(), actual.genericState());
        var expectedBlob = expected.compactGenericState();
        var actualBlob = actual.compactGenericState();
        if (expectedBlob == null) assertNull(actualBlob);
        else {
            assertNotNull(actualBlob);
            assertEquals(expectedBlob.schemaId(), actualBlob.schemaId());
            assertSame(expectedBlob.type(), actualBlob.type());
            assertArrayEquals(expectedBlob.scalarData(), actualBlob.scalarData());
            assertTrue(Arrays.deepEquals(expectedBlob.opaqueValues(), actualBlob.opaqueValues()),
                    "ordinary captured animation arrays must be retained");
        }
    }
}
