package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rules.GameRules;
import com.openggf.sprites.playable.Sonic;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class TestObjectSolidContactController {

    @Test
    void resetClearsInlineSupportAndStaleSupportLossState() {
        ObjectSolidContactController controller =
                new ObjectSolidContactController(mock(ObjectManager.class));
        PlayableEntity player = mock(PlayableEntity.class);

        controller.markObjectSupportThisFrame(player);
        controller.forceAirOnStaleObjectSupportLoss(player);
        ObjectManagerSnapshot.SolidContactState before = controller.captureRewindState();
        assertEquals(1, before.inlineSupportedPlayers().size());
        assertEquals(1, before.forceAirOnStaleSupportLoss().size());

        controller.reset();

        ObjectManagerSnapshot.SolidContactState after = controller.captureRewindState();
        assertTrue(after.inlineSupportedPlayers().isEmpty(),
                "reset must clear per-frame inline support players");
        assertTrue(after.forceAirOnStaleSupportLoss().isEmpty(),
                "reset must clear stale-support force-air players");
    }

    @Test
    void globalSolidProviderGetterRunsInsideCreatorFaultBoundary() {
        HostileSolidObject hostile = new HostileSolidObject(true, false);
        ObjectManager manager = boundaryEnabledManager(hostile);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class,
                () -> manager.hasStandingContact(mock(PlayableEntity.class)));

        assertEquals("solid-owner", aborted.owner());
        assertTrue(aborted.getCause() instanceof IllegalStateException);
    }

    @Test
    void compatibilitySolidListenerRunsInsideCreatorFaultBoundary() {
        HostileSolidObject hostile = new HostileSolidObject(false, true);
        ObjectManager manager = boundaryEnabledManager(hostile);
        PlayableEntity player = mock(PlayableEntity.class);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class,
                () -> manager.processImmediateInlineSolidCheckpoint(
                        hostile, player, List.of()));

        assertEquals("solid-owner", aborted.owner());
        assertTrue(aborted.getCause() instanceof IllegalStateException);
    }

    @Test
    void realMultipartLandingsPublishIndependentChildStandingBits() {
        var owner = new PieceSolid(spawn(), false, true);
        var manager = boundaryEnabledManager(owner);
        // The ordinary object pass clears the constructor's first-frame solid-contact skip.
        owner.snapshotPreUpdatePosition();
        var lowerRider = landOnPiece(manager, owner, 0, "sonic-lower");
        var upperRider = landOnPiece(manager, owner, 1, "sonic-upper");

        var captured = manager.solidContacts().captureRewindState();
        assertEquals(List.of(new ObjectManagerSnapshot.SolidContactLatchKey(owner.getSpawn(), -1, false, 0)),
                keysFor(captured.standingBits(), lowerRider));
        assertEquals(List.of(new ObjectManagerSnapshot.SolidContactLatchKey(owner.getSpawn(), -1, false, 1)),
                keysFor(captured.standingBits(), upperRider));
        assertSame(owner.getSpawn(), keysFor(captured.standingBits(), lowerRider).getFirst().spawn());
        assertEquals(0, manager.getRidingPieceIndex(lowerRider));
        assertEquals(1, manager.getRidingPieceIndex(upperRider));
        assertFalse(manager.hasObjectStandingBit(lowerRider, owner),
                "the existing two-argument query is the unscoped object bit, not either child");
    }

    @Test
    void pieceStandingAndClearedKeysKeepSiblingScopeAndCanonicalOrder() {
        var owner = new PieceSolid(spawn(), false, true);
        var player = mock(PlayableEntity.class);
        var manager = mock(ObjectManager.class);
        when(manager.findRestoredRidingObject(owner.getSpawn(), owner.getSlotIndex())).thenReturn(owner);
        var controller = new ObjectSolidContactController(manager);
        var first = new ObjectManagerSnapshot.SolidContactLatchKey(owner.getSpawn(), -1, false, 0);
        var second = new ObjectManagerSnapshot.SolidContactLatchKey(owner.getSpawn(), -1, false, 1);

        controller.restoreRewindState(contactState(player, owner, List.of(second, first), List.of(second, first)));
        var captured = controller.captureRewindState();
        assertEquals(List.of(first, second), keysFor(captured.standingBits(), player));
        assertEquals(List.of(first, second), keysFor(captured.standingBitSnapshots(), player));
        assertFalse(controller.hasStandingLatch(player, owner), "piece keys never become an aggregate key");

        controller.restoreRewindState(contactState(player, owner, List.of(first, second), List.of(first, second)));
        assertEquals(captured, controller.captureRewindState(), "insertion order cannot change a saved world");
    }

    @Test
    void registeredPieceLatchesResolveFreshInstanceOwnersAndPlayersTwice() {
        var originalOwner = new PieceSolid(spawn(), true, true);
        var originalPlayer = mock(PlayableEntity.class);
        var originalManager = mock(ObjectManager.class);
        when(originalManager.findRestoredRidingObject(originalOwner.getSpawn(), originalOwner.getSlotIndex()))
                .thenReturn(originalOwner);
        var originalController = new ObjectSolidContactController(originalManager);
        var first = new ObjectManagerSnapshot.SolidContactLatchKey(
                originalOwner.getSpawn(), originalOwner.getSlotIndex(), true, 0);
        var second = new ObjectManagerSnapshot.SolidContactLatchKey(
                originalOwner.getSpawn(), originalOwner.getSlotIndex(), true, 1);
        originalController.restoreRewindState(contactState(
                originalPlayer, originalOwner, List.of(second, first), List.of(first)));
        var originalIds = new RewindIdentityTable();
        originalIds.registerPlayer(originalPlayer, PlayerRefId.mainPlayer());
        var captured = originalController.captureRewindState(RewindCaptureContext.withIdentityTable(originalIds));
        assertNull(captured.standingBits().getFirst().player());
        assertEquals(PlayerRefId.mainPlayer(), captured.standingBits().getFirst().playerId());

        for (int cycle = 0; cycle < 2; cycle++) {
            var freshOwner = new PieceSolid(originalOwner.getSpawn(), true, true);
            var freshPlayer = mock(PlayableEntity.class);
            var manager = mock(ObjectManager.class);
            when(manager.findRestoredRidingObject(freshOwner.getSpawn(), freshOwner.getSlotIndex()))
                    .thenReturn(freshOwner);
            var controller = new ObjectSolidContactController(manager);
            var ids = new RewindIdentityTable();
            ids.registerPlayer(freshPlayer, PlayerRefId.mainPlayer());
            var context = RewindCaptureContext.withIdentityTable(ids);

            controller.restoreRewindState(captured, context);
            assertSame(freshOwner, controller.getRidingObject(freshPlayer));
            assertNull(controller.getRidingObject(originalPlayer));
            assertEquals(0, controller.getRidingPieceIndex(freshPlayer));
            assertEquals(captured, controller.captureRewindState(context));
            controller.releaseObjectStandingLatchForAllPlayers(originalOwner);
            assertEquals(captured, controller.captureRewindState(context), "the discarded incarnation owns no bit");
            controller.releaseObjectStandingLatchForAllPlayers(freshOwner);
            var released = controller.captureRewindState(context);
            assertTrue(released.standingBits().isEmpty(), "the fresh raw owner owns both restored sibling bits");
            assertEquals(captured.standingBitSnapshots(), released.standingBitSnapshots(),
                    "object-owned release does not erase the independent pre-clear snapshot");
        }
    }

    @Test
    void retainedPieceRideDoesNotManufactureAnExplicitlyClearedStandingBit() {
        var owner = new PieceSolid(spawn(), false, true);
        var player = mock(PlayableEntity.class);
        var manager = mock(ObjectManager.class);
        when(manager.findRestoredRidingObject(owner.getSpawn(), owner.getSlotIndex())).thenReturn(owner);
        var controller = new ObjectSolidContactController(manager);
        var cleared = new ObjectManagerSnapshot.SolidContactLatchKey(owner.getSpawn(), -1, false, 0);

        controller.restoreRewindState(contactState(player, owner, List.of(), List.of(cleared)));
        var captured = controller.captureRewindState();
        assertSame(owner, controller.getRidingObject(player));
        assertEquals(0, controller.getRidingPieceIndex(player));
        assertTrue(captured.standingBits().isEmpty(), "riding membership is not authority to recreate a live bit");
        assertEquals(List.of(cleared), keysFor(captured.standingBitSnapshots(), player));
    }

    @Test
    void legacyLatchConstructorRetainsTheUnscopedObjectStandingBit() {
        var owner = new PieceSolid(spawn(), false, false);
        var player = mock(PlayableEntity.class);
        var manager = mock(ObjectManager.class);
        when(manager.findRestoredRidingObject(owner.getSpawn(), owner.getSlotIndex())).thenReturn(owner);
        var controller = new ObjectSolidContactController(manager);
        var legacy = new ObjectManagerSnapshot.SolidContactLatchKey(owner.getSpawn(), -1, false);

        controller.restoreRewindState(contactState(player, owner, List.of(legacy), List.of(legacy)));
        assertTrue(controller.hasStandingLatch(player, owner));
        var captured = controller.captureRewindState();
        assertEquals(-1, captured.standingBits().getFirst().keys().getFirst().pieceIndex());
        assertEquals(List.of(legacy), keysFor(captured.standingBits(), player));
        assertEquals(List.of(legacy), keysFor(captured.standingBitSnapshots(), player));
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(100, 100, 0, 0, 0, false, 0);
    }

    private static final class ContactSonic extends Sonic {
        private ContactSonic(String code) {
            super(code, (short) 0, (short) 0);
            setGameRulesForTest(GameRules.SONIC_3K);
        }
    }

    private static Sonic landOnPiece(ObjectManager manager, PieceSolid owner, int piece, String code) {
        var player = new ContactSonic(code);
        player.setCentreX((short) owner.getPieceX(piece));
        int maxTop = owner.getPieceParams(piece).groundHalfHeight() + player.getYRadius();
        player.setCentreY((short) (owner.getPieceY(piece) - 4 - maxTop + 8));
        player.setAir(true);
        player.setYSpeed((short) 0x100);
        manager.updateSolidContacts(player);
        assertSame(owner, manager.getRidingObject(player), "ordinary solid resolution established the ride");
        return player;
    }

    private static List<ObjectManagerSnapshot.SolidContactLatchKey> keysFor(
            List<ObjectManagerSnapshot.SolidContactLatchEntry> entries, PlayableEntity player) {
        return entries.stream().filter(entry -> entry.player() == player)
                .flatMap(entry -> entry.keys().stream()).toList();
    }

    private static ObjectManagerSnapshot.SolidContactState contactState(PlayableEntity player, PieceSolid owner,
            List<ObjectManagerSnapshot.SolidContactLatchKey> live,
            List<ObjectManagerSnapshot.SolidContactLatchKey> cleared) {
        return new ObjectManagerSnapshot.SolidContactState(17,
                List.of(new ObjectManagerSnapshot.SolidContactRidingEntry(player, owner.getSpawn(),
                        owner.getSlotIndex(), 100, 100, 0)),
                List.of(), List.of(), List.of(), List.of(),
                live.isEmpty() ? List.of() : List.of(new ObjectManagerSnapshot.SolidContactLatchEntry(player, live)),
                List.of(),
                cleared.isEmpty() ? List.of() : List.of(new ObjectManagerSnapshot.SolidContactLatchEntry(player, cleared)));
    }

    private static final class PieceSolid extends AbstractObjectInstance implements MultiPieceSolidProvider {
        private final boolean instanceKey;
        private final boolean pieceScoped;

        private PieceSolid(ObjectSpawn spawn, boolean instanceKey, boolean pieceScoped) {
            super(spawn, "piece-solid");
            this.instanceKey = instanceKey;
            this.pieceScoped = pieceScoped;
            setSlotIndex(7);
        }

        @Override public int getPieceCount() { return 2; }
        @Override public int getPieceX(int piece) { return getSpawn().x() + piece * 64; }
        @Override public int getPieceY(int piece) { return getSpawn().y(); }
        @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(16, 8, 8); }
        @Override public boolean usesPieceScopedStandingBits() { return pieceScoped; }
        @Override public boolean usesInstanceSolidStateLatchKey() { return instanceKey; }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static ObjectManager boundaryEnabledManager(ObjectInstance instance) {
        ModFaultBoundary faultBoundary = new ModFaultBoundary(
                Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        ObjectRegistry registry = new BoundaryRegistry(faultBoundary);
        ObjectServices services = mock(ObjectServices.class);
        SolidExecutionRegistry solidExecutionRegistry = mock(SolidExecutionRegistry.class);
        when(services.solidExecutionRegistry()).thenReturn(solidExecutionRegistry);
        when(solidExecutionRegistry.previousStanding(any(), any()))
                .thenReturn(PlayerStandingState.NONE);
        ObjectManager manager = new ObjectManager(List.of(), registry, 0, null, null,
                mock(GraphicsManager.class), mock(Camera.class), services);
        manager.reset(0);
        manager.addDynamicObject(instance);
        manager.objectCallbacks().register(instance, instance.getSpawn());
        return manager;
    }

    private record BoundaryRegistry(ModFaultBoundary faultBoundary)
            implements ObjectRegistry, Supplier<ModFaultBoundary> {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return null;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "HOSTILE";
        }

        @Override
        public ModFaultBoundary get() {
            return faultBoundary;
        }
    }

    private static final class HostileSolidObject
            implements ObjectInstance, SolidObjectProvider, SolidObjectListener {
        private final ObjectSpawn spawn = new ObjectSpawn(
                100, 100, 0, 0, 0, false, 0, -1,
                "solid-owner", "solid-owner:hostile");
        private final boolean failProvider;
        private final boolean failListener;

        private HostileSolidObject(boolean failProvider, boolean failListener) {
            this.failProvider = failProvider;
            this.failListener = failListener;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return spawn;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public boolean isSolidFor(PlayableEntity player) {
            if (failProvider) {
                throw new IllegalStateException("hostile solid provider");
            }
            return false;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return new SolidObjectParams(16, 16, 16);
        }

        @Override
        public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
            if (failListener) {
                throw new IllegalStateException("hostile solid listener");
            }
        }

        @Override
        public void onSolidContactCleared(PlayableEntity player, int frameCounter) {
            if (failListener) {
                throw new IllegalStateException("hostile solid listener");
            }
        }
    }
}
