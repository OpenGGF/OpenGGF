package com.openggf.level.objects;

import com.openggf.game.GameDataSource;
import com.openggf.game.PlayableEntity;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.snapshot.SolidExecutionSnapshot;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.AbstractLevel;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestInitialObjectDispatchSuccessorOwnership {
    @BeforeEach
    void setup() {
        TestEnvironment.resetAll();
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void currentInitialDispatchPublishesItsCompletedStanding() throws Exception {
        Fixture fixture = new Fixture();
        Probe object = fixture.create(1);
        object.publishStanding = true;

        fixture.runInitialDispatch();

        assertEquals(1, object.updates);
        assertTrue(fixture.solids.previousStanding(object, fixture.player).standing());
        assertEquals(1, fixture.solids.capture().previousStanding().size());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @Test
    void currentInitialDispatchPreservesItsPrimaryFailure() throws Exception {
        Fixture fixture = new Fixture();
        Probe object = fixture.create(1);
        RuntimeException sentinel = new RuntimeException("current initial dispatch failure");
        object.action = () -> { throw sentinel; };

        assertSame(sentinel, assertThrows(RuntimeException.class, fixture::runInitialDispatch));

        assertEquals(1, object.updates);
        assertTrue(fixture.solids.capture().previousStanding().isEmpty());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @Test
    void solidRestoreDuringFailingInitialUpdateSurvivesOldScopeCleanup() throws Exception {
        Fixture fixture = new Fixture();
        Probe outer = fixture.create(1);
        Probe successor = fixture.create(2);
        fixture.publishStanding(successor);
        SolidExecutionSnapshot completed = fixture.solids.capture();
        assertEquals(1, completed.previousStanding().size());
        RuntimeException sentinel = new RuntimeException("after genuine solid restore");
        outer.action = () -> {
            fixture.solids.restore(completed);
            assertTrue(fixture.solids.previousStanding(successor, fixture.player).standing(),
                    "the restored standing must exist before the old callback throws");
            assertEquals(completed, fixture.solids.capture());
            throw sentinel;
        };

        assertSame(sentinel, assertThrows(RuntimeException.class, fixture::runInitialDispatch));

        assertEquals(1, outer.updates);
        assertEquals(0, successor.updates);
        assertTrue(fixture.solids.previousStanding(successor, fixture.player).standing(),
                "closing the suspended initial scope must preserve restored standing");
        assertEquals(completed, fixture.solids.capture());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @Test
    void registryRestoreDuringFailingInitialUpdateSurvivesOldScopeCleanup() throws Exception {
        Fixture fixture = new Fixture();
        CompletedSuccessor completed = fixture.completeSuccessor();
        RuntimeException sentinel = new RuntimeException("after genuine registry restore");
        AtomicReference<Probe> restoredStanding = new AtomicReference<>();
        completed.outer.action = () -> {
            fixture.context.getRewindRegistry().restore(completed.snapshot);
            Probe restored = fixture.requireRestoredSuccessor(completed);
            restoredStanding.set(restored);
            throw sentinel;
        };

        assertSame(sentinel, assertThrows(RuntimeException.class, fixture::runInitialDispatch));

        assertNotNull(restoredStanding.get(), "the complete registry restore must return before the sentinel");
        fixture.requireStandingUnchanged(completed, restoredStanding.get());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @ParameterizedTest
    @EnumSource(SuccessorOperation.class)
    void currentCompletedSuccessorSupportsItsPublicOperations(SuccessorOperation operation) throws Exception {
        Fixture fixture = new Fixture();
        CompletedSuccessor completed = fixture.completeSuccessor();

        operation.perform(fixture);

        fixture.requireStandingUnchanged(completed, completed.standing);
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @ParameterizedTest
    @EnumSource(SuccessorOperation.class)
    void restoredSuccessorOperationCompletesBeforeOldDispatchAborts(SuccessorOperation operation) throws Exception {
        Fixture fixture = new Fixture();
        CompletedSuccessor completed = fixture.completeSuccessor();
        AtomicBoolean operationReturned = new AtomicBoolean();
        AtomicReference<Probe> restoredStanding = new AtomicReference<>();
        completed.outer.action = () -> {
            fixture.context.getRewindRegistry().restore(completed.snapshot);
            Probe restored = fixture.requireRestoredSuccessor(completed);
            restoredStanding.set(restored);
            operation.perform(fixture);
            fixture.requireStandingUnchanged(completed, restored);
            operationReturned.set(true);
            completed.outer.retired = true;
            completed.outer.staleTail = () -> restored.marker = -1;
        };

        assertThrows(ObjectCallbackAbortException.class, fixture::runInitialDispatch);

        assertTrue(operationReturned.get(), "the genuine successor operation must return before old A is rejected");
        assertEquals(0, completed.outer.staleTailCalls,
                "old A must receive no getter or publication tail after replacement");
        assertNotNull(restoredStanding.get());
        fixture.requireStandingUnchanged(completed, restoredStanding.get());
        assertEquals(1, fixture.requireProbe(1).updates, "restored A's saved object must not run in the old pass");
        assertEquals(1, fixture.requireProbe(2).updates, "restored B must not run in the old pass");
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @Test
    void restoredSuccessorCanCompleteAnOrdinaryUpdateBeforeOldDispatchAborts() throws Exception {
        Fixture fixture = new Fixture();
        CompletedSuccessor completed = fixture.completeSuccessor();
        AtomicBoolean updated = new AtomicBoolean();
        completed.outer.action = () -> {
            fixture.context.getRewindRegistry().restore(completed.snapshot);
            Probe standing = fixture.requireRestoredSuccessor(completed);
            fixture.objects.update(0, fixture.context.getSpriteManager().getMainPlayable(), List.of(), 1);
            assertEquals(2, fixture.requireProbe(1).updates);
            assertEquals(2, standing.updates);
            assertTrue(fixture.solids.previousStanding(standing,
                    fixture.context.getSpriteManager().getMainPlayable()).standing());
            fixture.context.getRewindRegistry().capture();
            updated.set(true);
            completed.outer.retired = true;
            completed.outer.staleTail = () -> standing.marker = -1;
        };

        assertThrows(ObjectCallbackAbortException.class, fixture::runInitialDispatch);

        assertTrue(updated.get());
        assertEquals(0, completed.outer.staleTailCalls);
        assertEquals(2, fixture.requireProbe(1).updates);
        assertEquals(2, fixture.requireProbe(2).updates);
        assertEquals(0x52, fixture.requireProbe(2).marker);
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @ParameterizedTest
    @EnumSource(CollisionBuildOperation.class)
    void collisionBuilderRetainsItsUnscopedOperation(CollisionBuildOperation operation) throws Exception {
        Fixture fixture = new Fixture();
        fixture.completeSuccessor();

        assertDoesNotThrow(() -> operation.perform(fixture.objects));

        assertEquals(operation.stage, fixture.objects.rewindSnapshottable().capture()
                .collisionResponseState().collisionBuildStage());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @ParameterizedTest
    @EnumSource(CollisionBuildOperation.class)
    void collisionBuilderRejectsASupersededOpenScopeBeforeMutation(CollisionBuildOperation operation) throws Exception {
        Fixture fixture = new Fixture();
        Probe standing = fixture.create(1);
        fixture.publishStanding(standing);
        SolidExecutionSnapshot completed = fixture.solids.capture();
        assertEquals(1, completed.previousStanding().size());
        try (var scope = fixture.objects.beginInitialProcessSprites(0, fixture.player, List.of())) {
            fixture.objects.freezeInitialCollisionResponseReadView();
            var collision = fixture.objects.rewindSnapshottable().capture().collisionResponseState();
            fixture.solids.restore(completed);

            assertThrows(ObjectCallbackAbortException.class, () -> operation.perform(fixture.objects));

            assertEquals(collision, fixture.objects.rewindSnapshottable().capture().collisionResponseState());
        }
        assertEquals(completed, fixture.solids.capture());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    private enum CollisionBuildOperation {
        FREEZE(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.CollisionBuildStage.PREVIOUS_READ_FROZEN) {
            @Override void perform(ObjectManager objects) { objects.freezeInitialCollisionResponseReadView(); }
        },
        RESET(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.CollisionBuildStage.CURRENT_BUILD_RESET) {
            @Override void perform(ObjectManager objects) { objects.resetInitialCollisionResponseBuild(); }
        },
        MARK(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.CollisionBuildStage.DYNAMIC_BUILD_COMPLETE) {
            @Override void perform(ObjectManager objects) { objects.markInitialDynamicCollisionBuildComplete(); }
        };

        final com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.CollisionBuildStage stage;
        CollisionBuildOperation(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.CollisionBuildStage stage) {
            this.stage = stage;
        }
        abstract void perform(ObjectManager objects);
    }

    private enum SuccessorOperation {
        QUERY {
            @Override void perform(Fixture fixture) {
                Probe first = fixture.requireProbe(1);
                Probe second = fixture.requireProbe(2);
                assertNotSame(first, second);
                assertEquals(List.of(first, second), fixture.objects.objectQuery().activeObjectsOfType(Probe.class));
            }
        },
        NULL_SPAWN {
            @Override void perform(Fixture fixture) {
                Probe created = fixture.objects.createDynamicObject(() -> new Probe(null));
                assertNotNull(created);
                assertNull(created.getSpawn());
                assertTrue(ObjectSlotLayout.SONIC_3K.isDynamicSlot(created.getSlotIndex()));
                assertTrue(fixture.objects.getActiveObjects().contains(created));
                var identity = fixture.objects.objectQuery().identityOf(created).orElseThrow();
                assertSame(created, fixture.objects.objectQuery().resolve(identity).orElseThrow());
            }
        };

        abstract void perform(Fixture fixture);
    }

    private record CompletedSuccessor(
            Probe outer, Probe standing, CompositeSnapshot snapshot, SolidExecutionSnapshot solidSnapshot) { }

    private static final class Fixture {
        final GameplayModeContext context;
        final ObjectManager objects;
        final DefaultSolidExecutionRegistry solids;
        final Sonic player;

        Fixture() throws Exception {
            var module = new DelegatingGameModule(new Sonic3kGameModule(), "test:initial-dispatch") {
                @Override public TouchResponseTable createTouchResponseTable(GameDataSource source) { return null; }
                @Override public ObjectRegistry createObjectRegistry() { return TEST_OBJECTS; }
            };
            context = SessionManager.openGameplaySession(module);
            GameplaySessionFactory.attachManagers(context, EngineServices.current());
            player = new Sonic("sonic", (short) 160, (short) 256);
            context.getSpriteManager().addSprite(player);
            context.getCamera().setFocusedSprite(player);
            var level = context.getLevelManager();
            level.initializeCollisionOnly(new EmptyTerrain(), 0, 0);
            level.initObjectManager();
            objects = level.getObjectManager();
            objects.reset(0);
            objects.setRewindInPlaceRestoreEnabledForTest(false);
            solids = assertInstanceOf(DefaultSolidExecutionRegistry.class, context.getSolidExecutionRegistry());
            var registry = context.getRewindRegistry();
            registry.deregister("solid-execution");
            registry.register(objects.rewindSnapshottable());
            registry.register(solids);
        }

        Probe create(int id) {
            Probe probe = objects.createDynamicObject(() -> new Probe(
                    new ObjectSpawn(160, 256, id, 0, 0, false, 256)));
            assertNotNull(probe);
            assertTrue(ObjectSlotLayout.SONIC_3K.isDynamicSlot(probe.getSlotIndex()));
            return probe;
        }

        void publishStanding(Probe object) {
            solids.beginFrame(0, List.of(player));
            solids.publishCheckpoint(standing(object, player));
            solids.finishFrame();
            assertTrue(solids.previousStanding(object, player).standing());
        }

        void runInitialDispatch() {
            try (InitialObjectDispatchScope scope = objects.beginInitialProcessSprites(0, player, List.of())) {
                objects.loadInitialDynamicSlots(scope);
                objects.processInitialDynamicSlots(scope);
                objects.finishInitialProcessSprites(scope);
            }
        }

        CompletedSuccessor completeSuccessor() {
            Probe outer = create(1);
            Probe standing = create(2);
            outer.marker = 0x41;
            standing.marker = 0x52;
            standing.publishStanding = true;
            runInitialDispatch();
            assertEquals(1, outer.updates);
            assertEquals(1, standing.updates);
            assertTrue(solids.previousStanding(standing, player).standing());
            CompositeSnapshot snapshot = context.getRewindRegistry().capture();
            var keys = List.copyOf(snapshot.entries().keySet());
            assertTrue(keys.indexOf("sprites") < keys.indexOf("object-manager"));
            assertTrue(keys.indexOf("object-manager") < keys.indexOf("solid-execution"));
            SolidExecutionSnapshot solidSnapshot = (SolidExecutionSnapshot) snapshot.get("solid-execution");
            assertEquals(1, solidSnapshot.previousStanding().size());
            assertEquals(solids.capture(), solidSnapshot);
            return new CompletedSuccessor(outer, standing, snapshot, solidSnapshot);
        }

        Probe requireProbe(int id) {
            List<Probe> matches = objects.getActiveObjects().stream()
                    .filter(Probe.class::isInstance).map(Probe.class::cast)
                    .filter(object -> object.getSpawn() != null && object.getSpawn().objectId() == id)
                    .toList();
            assertEquals(1, matches.size());
            return matches.getFirst();
        }

        Probe requireRestoredSuccessor(CompletedSuccessor completed) {
            assertSame(context, SessionManager.getCurrentGameplayMode());
            Probe restoredOuter = requireProbe(1);
            Probe restoredStanding = requireProbe(2);
            assertNotSame(completed.outer, restoredOuter, "the actual registry must recreate the old owner");
            assertNotSame(completed.standing, restoredStanding, "standing must resolve to B's recreated object");
            assertEquals(1, restoredOuter.updates);
            assertEquals(1, restoredStanding.updates);
            requireStandingUnchanged(completed, restoredStanding);
            assertEquals(completed.solidSnapshot,
                    context.getRewindRegistry().capture().get("solid-execution"),
                    "the restored successor must support a complete registered capture inside the callback");
            return restoredStanding;
        }

        void requireStandingUnchanged(CompletedSuccessor completed, Probe standing) {
            var restoredPlayer = assertInstanceOf(Sonic.class, context.getSpriteManager().getSprite("sonic"));
            assertTrue(solids.previousStanding(standing, restoredPlayer).standing());
            assertEquals(0x52, standing.marker);
            assertEquals(completed.solidSnapshot, solids.capture());
        }
    }

    private static SolidCheckpointBatch standing(ObjectInstance object, PlayableEntity player) {
        var contact = new PlayerSolidContactResult(ContactKind.TOP, true, true, false, false, null, null, 0);
        return new SolidCheckpointBatch(object, Map.of(player, contact));
    }

    private static final ObjectRegistry TEST_OBJECTS = new ObjectRegistry() {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return new Probe(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "initial-dispatch-probe"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
    };

    private static final class Probe extends AbstractObjectInstance implements RewindRecreatable {
        int updates;
        int marker;
        boolean publishStanding;
        @RewindTransient(reason = "test-owned callback, never restored gameplay state")
        Runnable action = () -> { };
        @RewindTransient(reason = "test-owned retirement observation, never restored gameplay state")
        boolean retired;
        @RewindTransient(reason = "test-owned retirement observation, never restored gameplay state")
        int staleTailCalls;
        @RewindTransient(reason = "test-owned callback, never restored gameplay state")
        Runnable staleTail = () -> { };

        Probe(ObjectSpawn spawn) { super(spawn, "initial-dispatch-probe"); }

        @Override public void update(int vIntRunCount, PlayableEntity player) {
            updates++;
            action.run();
            if (publishStanding) services().solidExecutionRegistry().publishCheckpoint(standing(this, player));
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isDestroyed() {
            observeRetiredTail();
            return super.isDestroyed();
        }
        @Override public int getSlotIndex() {
            observeRetiredTail();
            return super.getSlotIndex();
        }
        @Override public void clearAwaitingFirstTouchExecution() {
            observeRetiredTail();
            super.clearAwaitingFirstTouchExecution();
        }
        @Override public boolean isPersistent() { return true; }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new Probe(context.spawn());
        }

        private void observeRetiredTail() {
            if (retired) {
                staleTailCalls++;
                staleTail.run();
            }
        }
    }

    private static final class EmptyTerrain extends AbstractLevel {
        EmptyTerrain() {
            super(0);
            palettes = new com.openggf.level.Palette[PALETTE_COUNT];
            java.util.Arrays.setAll(palettes, ignored -> new com.openggf.level.Palette());
            patterns = new com.openggf.level.Pattern[]{new com.openggf.level.Pattern()};
            chunks = new com.openggf.level.Chunk[]{new com.openggf.level.Chunk()};
            blocks = new com.openggf.level.Block[]{new com.openggf.level.Block()};
            solidTiles = new com.openggf.level.SolidTile[]{
                    new com.openggf.level.SolidTile(0, new byte[16], new byte[16], (byte) 0)};
            patternCount = chunkCount = blockCount = solidTileCount = 1;
            map = new com.openggf.level.Map(2, 8, 8);
            maxX = maxY = 8 * getBlockPixelSize();
            objects = List.of();
            rings = List.of();
        }
    }
}
