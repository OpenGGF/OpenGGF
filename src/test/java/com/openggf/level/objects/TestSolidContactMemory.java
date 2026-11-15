package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises production contact memory without collision geometry, game boot or a substitute state model. */
class TestSolidContactMemory {
    @Test
    void nextFrameExpiresHistoryButKeepsLiveObjectBitsAndTheSameRideHolder() {
        Harness harness = new Harness();
        SolidContactMemory state = harness.state;
        Solid object = new Solid(spawn(), false, true);
        Sonic player = player("sonic");
        state.ride(player, object, 0);
        var ride = state.rideFor(player);
        state.setObjectStandingBit(player, object, 0);
        state.setObjectPushingBit(player, object);
        state.snapshotObjectStandingBit(player, object, 0);
        state.markStandingBitEstablishedThisFrame(player, object, 0);
        state.rememberControllerRelease(player, object);
        assertTrue(state.wasObjectStandingBitSetThisFrame(player, object, 0));
        assertTrue(state.wasStandingBitEstablishedThisFrame(player, object, 0));

        state.beginFrame();

        assertEquals(1, state.frameCounter());
        assertSame(ride, state.rideFor(player), "the hot ride holder is reused, not snapshotted per query");
        assertTrue(state.hasObjectStandingBit(player, object, 0));
        assertTrue(state.hasPushingLatch(player));
        assertFalse(state.wasObjectStandingBitSetThisFrame(player, object, 0));
        assertFalse(state.wasStandingBitEstablishedThisFrame(player, object, 0));
        assertFalse(state.consumeControllerRelease(player, object));
    }

    @Test
    void objectRetirementExpiresBothPlayersLiveBitsWithoutChangingTheirRideOrPhysicalState() {
        Harness harness = new Harness();
        SolidContactMemory state = harness.state;
        Solid original = new Solid(spawn(), false, true);
        Sonic sonic = player("sonic");
        Sonic tails = player("tails");
        for (Sonic player : List.of(sonic, tails)) {
            state.ride(player, original, 0);
            state.setObjectStandingBit(player, original, 0);
            state.setObjectStandingBit(player, original, 1);
            state.setObjectPushingBit(player, original);
        }
        assertEquals(2, state.captureRewindState().standingBits().getFirst().keys().size());
        assertTrue(state.hasPushingLatch(sonic));
        assertTrue(state.hasPushingLatch(tails));

        state.releaseObjectLatchesForAllPlayers(original);

        assertTrue(state.captureRewindState().standingBits().isEmpty());
        assertTrue(state.captureRewindState().pushingBits().isEmpty());
        Solid reloaded = new Solid(original.getSpawn(), false, true);
        for (Sonic player : List.of(sonic, tails)) {
            assertSame(original, state.getRidingObject(player));
            assertTrue(player.isOnObject());
            assertTrue(player.getPushing());
            assertFalse(player.getAir());
            assertFalse(state.hasObjectStandingBit(player, reloaded, 0));
            assertFalse(state.hasObjectStandingBit(player, reloaded, 1));
        }
    }

    @Test
    void permanentInstanceRetirementExpiresPieceHistoryWithoutDroppingTheRide() {
        Harness harness = new Harness();
        SolidContactMemory state = harness.state;
        Solid owner = new Solid(spawn(), true, true);
        Sonic player = player("sonic");
        state.ride(player, owner, 0);
        state.setObjectStandingBit(player, owner, 0);
        state.setObjectPushingBit(player, owner);
        state.snapshotObjectStandingBit(player, owner, 0);
        state.markStandingBitEstablishedThisFrame(player, owner, 0);
        assertTrue(state.wasStandingBitEstablishedThisFrame(player, owner, 0));

        state.evictLatchForDestroyedInstance(owner);

        assertFalse(state.hasObjectStandingBit(player, owner, 0));
        assertFalse(state.hasPushingLatch(player));
        assertFalse(state.wasObjectStandingBitSetThisFrame(player, owner, 0));
        assertFalse(state.wasStandingBitEstablishedThisFrame(player, owner, 0),
                "a retired object's same-frame marker cannot survive its native slot");
        assertSame(owner, state.getRidingObject(player));
        assertTrue(player.isOnObject());
    }

    @Test
    void occurrenceExpiryCannotClearAnotherInstanceSharingTheSameSpawnValue() {
        Harness harness = new Harness();
        Solid first = new Solid(spawn(), false, false);
        Solid second = new Solid(spawn(), false, false);
        harness.occurrences.add(first);
        harness.occurrences.add(second);
        Sonic player = player("sonic");
        harness.state.setObjectStandingBit(player, first);
        harness.state.setObjectPushingBit(player, first);

        harness.state.releaseObjectLatchesForAllPlayers(second);
        assertTrue(harness.state.hasObjectStandingBit(player, first));
        assertTrue(harness.state.hasPushingLatch(player));

        harness.state.releaseObjectLatchesForAllPlayers(first);
        assertFalse(harness.state.hasObjectStandingBit(player, first));
        assertFalse(harness.state.hasPushingLatch(player));
    }

    @Test
    void retainedMultipartSstKeepsLivePieceBitButDoesNotRecreateAnExplicitlyClearedBit() {
        Harness previous = new Harness();
        Solid retained = new Solid(spawn(), true, true);
        Sonic lower = player("sonic-carried");
        Sonic upper = player("tails-carried");
        previous.state.ride(lower, retained, 0);
        previous.state.ride(upper, retained, 1);
        previous.state.setObjectStandingBit(lower, retained, 0);
        previous.state.setObjectStandingBit(upper, retained, 1);
        previous.state.setObjectPushingBit(lower, retained);
        previous.state.clearObjectStandingBit(upper, retained, 1);

        Harness successor = new Harness();
        successor.state.inheritRetainedSstContacts(previous.state, List.of(retained));

        assertSame(retained, successor.state.getRidingObject(lower));
        assertSame(retained, successor.state.getRidingObject(upper));
        assertEquals(0, successor.state.getRidingPieceIndex(lower));
        assertEquals(1, successor.state.getRidingPieceIndex(upper));
        assertTrue(successor.state.hasObjectStandingBit(lower, retained, 0));
        assertTrue(successor.state.hasPushingLatch(lower));
        assertFalse(successor.state.hasObjectStandingBit(upper, retained, 1),
                "retained ride membership is not authority to recreate a cleared native bit");
        assertEquals(1, successor.state.captureRewindState().standingBits().size());
    }

    @Test
    void successfulRestoreDiscardsUncapturedFutureFrameObservationsWithoutAdvancingTheSavedClock() {
        Harness harness = new Harness();
        Solid object = new Solid(spawn(), false, false);
        harness.occupants.put(object.getSlotIndex(), object);
        Sonic player = player("sonic-restored");
        harness.state.beginFrame();
        harness.state.ride(player, object, -1);
        harness.state.setObjectStandingBit(player, object);
        harness.state.setObjectPushingBit(player, object);
        var saved = harness.state.captureRewindState();
        harness.state.beginFrame();
        harness.state.snapshotObjectStandingBit(player, object);
        harness.state.markStandingBitEstablishedThisFrame(player, object, -1);
        harness.state.rememberControllerRelease(player, object);
        harness.state.markSameFrameMonitorBreakBounce(player);

        harness.state.restoreRewindState(saved);

        assertEquals(saved.frameCounter(), harness.state.frameCounter());
        assertEquals(saved, harness.state.captureRewindState(), "captured live state is restored exactly");
        assertAll(
                () -> assertFalse(harness.state.wasObjectStandingBitSetThisFrame(player, object)),
                () -> assertFalse(harness.state.wasStandingBitEstablishedThisFrame(player, object, -1),
                        "future fresh-contact protection was never captured and must not survive restore"),
                () -> assertFalse(harness.state.consumeControllerRelease(player, object),
                        "an uncaptured future controller release must not consume a restored ride"),
                () -> assertFalse(harness.state.hadSameFrameMonitorBreakBounce(player)));
    }

    @Test
    void failedStructuralRoleResolutionPreservesLiveAndFrameState() {
        Harness harness = new Harness();
        Solid object = new Solid(spawn(), false, false);
        Sonic player = player("sonic-before-failed-restore");
        harness.state.ride(player, object, -1);
        harness.state.setObjectStandingBit(player, object);
        harness.state.snapshotObjectStandingBit(player, object);
        harness.state.markStandingBitEstablishedThisFrame(player, object, -1);
        harness.state.rememberControllerRelease(player, object);
        var identities = new RewindIdentityTable();
        identities.registerPlayer(player, PlayerRefId.mainPlayer());
        var structural = harness.state.captureRewindState(RewindCaptureContext.withIdentityTable(identities));
        var before = harness.state.captureRewindState();
        var missingRoles = RewindCaptureContext.withIdentityTable(new RewindIdentityTable());

        assertThrows(IllegalStateException.class, () -> harness.state.restoreRewindState(structural, missingRoles));

        assertEquals(before, harness.state.captureRewindState());
        assertTrue(harness.state.wasObjectStandingBitSetThisFrame(player, object));
        assertTrue(harness.state.wasStandingBitEstablishedThisFrame(player, object, -1));
        assertTrue(harness.state.consumeControllerRelease(player, object));
    }

    @Test
    void structuralSnapshotRestoresFreshRoleAndPieceOwnerOnTwoSuccessiveIncarnations() {
        Harness original = new Harness();
        Solid owner = new Solid(spawn(), true, true);
        Sonic player = player("sonic-original");
        original.state.ride(player, owner, 1);
        original.state.setObjectStandingBit(player, owner, 0);
        original.state.setObjectStandingBit(player, owner, 1);
        original.state.setObjectPushingBit(player, owner);
        var identities = new RewindIdentityTable();
        identities.registerPlayer(player, PlayerRefId.mainPlayer());
        var saved = original.state.captureRewindState(RewindCaptureContext.withIdentityTable(identities));
        assertNull(saved.riding().getFirst().player());
        assertEquals(PlayerRefId.mainPlayer(), saved.riding().getFirst().playerId());

        for (int incarnation = 0; incarnation < 2; incarnation++) {
            Harness restored = new Harness();
            Solid freshOwner = new Solid(owner.getSpawn(), true, true);
            restored.occupants.put(freshOwner.getSlotIndex(), freshOwner);
            Sonic freshPlayer = player("sonic-fresh-" + incarnation);
            var freshIdentities = new RewindIdentityTable();
            freshIdentities.registerPlayer(freshPlayer, PlayerRefId.mainPlayer());
            var context = RewindCaptureContext.withIdentityTable(freshIdentities);

            restored.state.restoreRewindState(saved, context);

            assertSame(freshOwner, restored.state.getRidingObject(freshPlayer));
            assertNull(restored.state.getRidingObject(player));
            assertEquals(1, restored.state.getRidingPieceIndex(freshPlayer));
            assertTrue(restored.state.hasObjectStandingBit(freshPlayer, freshOwner, 0));
            assertTrue(restored.state.hasObjectStandingBit(freshPlayer, freshOwner, 1));
            assertTrue(restored.state.hasPushingLatch(freshPlayer));
            assertEquals(saved, restored.state.captureRewindState(context));
        }
    }

    @Test
    void invalidatingKeyCallbackIsRejectedBeforeAnyLiveBitIsPublished() {
        Harness harness = new Harness();
        Solid object = new Solid(spawn(), false, false) {
            @Override
            public boolean usesInstanceSolidStateLatchKey() {
                harness.incarnation++;
                return false;
            }
        };
        Sonic player = player("sonic");

        assertThrows(IllegalStateException.class, () -> harness.state.setObjectStandingBit(player, object));

        assertTrue(harness.state.captureRewindState().standingBits().isEmpty());
    }

    @Test
    void pieceIdentityCallbacksValidateTheOriginalOuterAndInnerReservationsInOrder() {
        Harness harness = new Harness();
        Solid object = new Solid(spawn(), false, true) {
            @Override
            public boolean usesInstanceSolidStateLatchKey() {
                harness.events.add("instance-policy");
                return false;
            }

            @Override
            public boolean usesPieceScopedStandingBits() {
                harness.events.add("piece-policy");
                return true;
            }
        };
        harness.state.setObjectStandingBit(player("sonic"), object, 0);

        assertEquals(List.of("capture-0", "capture-1", "occurrence", "instance-policy", "validate-1",
                "validate-1", "piece-policy", "validate-0"), harness.events);
    }

    @Test
    void fullResetDropsRidesAllLiveAndFrameStateAndRestoresTheInitialClock() {
        Harness harness = new Harness();
        Solid object = new Solid(spawn(), false, false);
        Sonic player = player("sonic");
        harness.state.beginFrame();
        harness.state.ride(player, object, -1);
        harness.state.setObjectStandingBit(player, object);
        harness.state.setObjectPushingBit(player, object);
        harness.state.snapshotObjectStandingBit(player, object);
        harness.state.markStandingBitEstablishedThisFrame(player, object, -1);
        harness.state.markSameFrameMonitorBreakBounce(player);
        harness.state.rememberControllerRelease(player, object);

        harness.state.reset();

        assertEquals(0, harness.state.frameCounter());
        assertNull(harness.state.getRidingObject(player));
        assertFalse(harness.state.hasObjectStandingBit(player, object));
        assertFalse(harness.state.hasPushingLatch(player));
        assertFalse(harness.state.wasObjectStandingBitSetThisFrame(player, object));
        assertFalse(harness.state.wasStandingBitEstablishedThisFrame(player, object, -1));
        assertFalse(harness.state.hadSameFrameMonitorBreakBounce(player));
        assertFalse(harness.state.consumeControllerRelease(player, object));
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(100, 100, 0, 0, 0, false, 0);
    }

    private static Sonic player(String code) {
        Sonic player = new Sonic(code, (short) 100, (short) 100);
        player.setOnObject(true);
        player.setPushing(true);
        player.setAir(false);
        return player;
    }

    private static final class Harness implements SolidContactMemory.Access {
        private final Set<ObjectInstance> occurrences = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Integer, ObjectInstance> occupants = new java.util.HashMap<>();
        private final List<String> events = new ArrayList<>();
        private final SolidContactMemory state = new SolidContactMemory(this);
        private int incarnation;
        private int reservations;

        @Override
        public boolean usesOccurrenceKey(ObjectInstance object) {
            events.add("occurrence");
            return occurrences.contains(object);
        }

        @Override
        public ObjectInstance restoredOccupant(ObjectSpawn spawn, int slotIndex) {
            return occupants.get(slotIndex);
        }

        @Override
        public Runnable captureDispatchGuard() {
            int capturedIncarnation = incarnation;
            int reservation = reservations++;
            events.add("capture-" + reservation);
            return () -> {
                events.add("validate-" + reservation);
                if (capturedIncarnation != incarnation) {
                    throw new IllegalStateException("stale initial-dispatch reservation");
                }
            };
        }
    }

    private static class Solid extends AbstractObjectInstance implements MultiPieceSolidProvider {
        private final boolean instanceKey;
        private final boolean pieceScoped;

        private Solid(ObjectSpawn spawn, boolean instanceKey, boolean pieceScoped) {
            super(spawn, "contact-state-solid");
            this.instanceKey = instanceKey;
            this.pieceScoped = pieceScoped;
            setSlotIndex(7);
        }

        @Override public int getPieceCount() { return 2; }
        @Override public int getPieceX(int piece) { return getSpawn().x() + piece * 64; }
        @Override public int getPieceY(int piece) { return getSpawn().y(); }
        @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(16, 8, 8); }
        @Override public boolean usesInstanceSolidStateLatchKey() { return instanceKey; }
        @Override public boolean usesPieceScopedStandingBits() { return pieceScoped; }
        @Override public void update(int vIntRunCount, PlayableEntity player) {}
        @Override public void appendRenderCommands(List<GLCommand> commands) {}
    }
}
