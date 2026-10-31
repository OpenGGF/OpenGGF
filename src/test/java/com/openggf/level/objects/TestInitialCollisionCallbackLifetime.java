package com.openggf.level.objects;

import com.openggf.game.GameDataSource;
import com.openggf.game.PlayableEntity;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.AbstractLevel;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestInitialCollisionCallbackLifetime {
    enum Hook { DESTROYED, PUBLICATION, SLOT }

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

    @ParameterizedTest
    @EnumSource(Hook.class)
    void currentCollisionPublicationPreservesItsCompletedState(Hook hook) throws Exception {
        Fixture fixture = new Fixture();
        ObjectManagerSnapshot completed = fixture.completeAndCapture();
        Probe original = fixture.probe;

        fixture.publishInInitialScope(original, hook, () -> { });

        assertEquals(1, original.callbackCalls, "the selected publication callback must be reached");
        assertEquals(0, original.staleCalls);
        assertEquals(0x52, original.marker);
        assertSame(original, fixture.currentProbe());
        assertEquals(completed.collisionResponseState(), fixture.collisionState());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @ParameterizedTest
    @EnumSource(Hook.class)
    void restoringInsideCollisionPublicationPreservesTheCompletedSuccessor(Hook hook) throws Exception {
        Fixture fixture = new Fixture();
        ObjectManagerSnapshot completed = fixture.completeAndCapture();
        Probe original = fixture.probe;
        AtomicBoolean restoreReturned = new AtomicBoolean();
        AtomicReference<Probe> replacement = new AtomicReference<>();
        AtomicReference<ObjectManagerSnapshot.CollisionResponseState> restoredState = new AtomicReference<>();

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> fixture.publishInInitialScope(original, hook, () -> {
                    fixture.objects.rewindSnapshottable().restore(completed);
                    Probe restored = fixture.currentProbe();
                    assertNotSame(original, restored, "the actual manager restore must reconstruct B");
                    assertEquals(0x52, restored.marker);
                    ObjectManagerSnapshot.CollisionResponseState collision = fixture.collisionState();
                    assertEquals(completed.collisionResponseState(), collision,
                            "the complete captured collision state must be installed before the old callback returns");
                    replacement.set(restored);
                    restoredState.set(collision);
                    original.retired = true;
                    original.staleMutation = () -> restored.marker = -1;
                    restoreReturned.set(true);
                }));

        assertTrue(restoreReturned.get(), "a failed restore prerequisite does not qualify as cancellation");
        assertEquals(1, original.callbackCalls);
        assertEquals(restoredState.get(), assertDoesNotThrow(fixture::collisionState,
                "B collision state must remain serializable after A unwinds"),
                "old publication must preserve B's collision entries, cursor and build stage");
        assertEquals(0, original.staleCalls, "engine code must not invoke any remaining old publication hook");
        assertSame(replacement.get(), fixture.currentProbe());
        assertEquals(0x52, fixture.currentProbe().marker);
        assertInstanceOf(ObjectCallbackAbortException.class, failure);
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    private static final class Fixture {
        final ObjectManager objects;
        final Sonic player;
        final Probe probe;

        Fixture() throws Exception {
            var module = new DelegatingGameModule(new Sonic3kGameModule(), "test:initial-collision-lifetime") {
                @Override public TouchResponseTable createTouchResponseTable(GameDataSource source) { return null; }
                @Override public ObjectRegistry createObjectRegistry() { return OBJECTS; }
            };
            var context = SessionManager.openGameplaySession(module);
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
            assertInstanceOf(DefaultSolidExecutionRegistry.class, context.getSolidExecutionRegistry());
            probe = objects.createDynamicObject(() -> new Probe(new ObjectSpawn(160, 256, 1, 0, 0, false, 256)));
            assertNotNull(probe);
            assertTrue(ObjectSlotLayout.SONIC_3K.isDynamicSlot(probe.getSlotIndex()));
            probe.marker = 0x52;
        }

        ObjectManagerSnapshot completeAndCapture() {
            try (InitialObjectDispatchScope scope = objects.beginInitialProcessSprites(0, player, List.of())) {
                objects.freezeInitialCollisionResponseReadView();
                objects.loadInitialDynamicSlots(scope);
                objects.resetInitialCollisionResponseBuild();
                objects.processInitialDynamicSlots(scope);
                objects.markInitialDynamicCollisionBuildComplete();
                objects.finishInitialProcessSprites(scope);
            }
            ObjectManagerSnapshot captured = objects.rewindSnapshottable().capture();
            assertEquals(1, captured.dynamicObjects().size());
            assertEquals(1, captured.collisionResponseState().previousCollisionObjectIds().size());
            assertEquals(1, captured.collisionResponseState().currentCollisionBuildObjectIds().size());
            assertEquals(1, captured.collisionResponseState().currentCollisionBuildCursor());
            assertEquals(ObjectManagerSnapshot.CollisionBuildStage.COMPLETED,
                    captured.collisionResponseState().collisionBuildStage());
            return captured;
        }

        void publishInInitialScope(Probe object, Hook hook, Runnable callback) {
            try (InitialObjectDispatchScope scope = objects.beginInitialProcessSprites(0, player, List.of())) {
                objects.freezeInitialCollisionResponseReadView();
                objects.resetInitialCollisionResponseBuild();
                object.arm(hook, callback);
                objects.publishInitialCollisionResponse(object);
                objects.finishInitialProcessSprites(scope);
            }
        }

        Probe currentProbe() {
            List<Probe> probes = objects.getActiveObjects().stream()
                    .filter(Probe.class::isInstance).map(Probe.class::cast).toList();
            assertEquals(1, probes.size());
            return probes.getFirst();
        }

        ObjectManagerSnapshot.CollisionResponseState collisionState() {
            return objects.rewindSnapshottable().capture().collisionResponseState();
        }
    }

    private static final ObjectRegistry OBJECTS = new ObjectRegistry() {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return new Probe(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "initial-collision-probe"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
    };

    private static final class Probe extends AbstractObjectInstance implements TouchResponseProvider, RewindRecreatable {
        int marker;
        @RewindTransient(reason = "test-owned publication callback selection")
        Hook hook;
        @RewindTransient(reason = "test-owned publication callback selection")
        boolean armed;
        @RewindTransient(reason = "test-owned publication callback observation")
        int callbackCalls;
        @RewindTransient(reason = "test-owned publication retirement observation")
        boolean retired;
        @RewindTransient(reason = "test-owned publication retirement observation")
        int staleCalls;
        @RewindTransient(reason = "test-owned callback, never restored gameplay state")
        Runnable callback = () -> { };
        @RewindTransient(reason = "test-owned stale publication observation")
        Runnable staleMutation = () -> { };

        Probe(ObjectSpawn spawn) { super(spawn, "initial-collision-probe"); }

        void arm(Hook hook, Runnable callback) {
            this.hook = hook;
            this.callback = callback;
            armed = true;
        }

        private void visit(Hook actual) {
            if (retired) {
                staleCalls++;
                staleMutation.run();
            }
            if (!armed || actual != hook) return;
            armed = false;
            callbackCalls++;
            callback.run();
        }

        @Override public boolean isDestroyed() {
            visit(Hook.DESTROYED);
            return super.isDestroyed();
        }
        @Override public boolean publishesTouchResponseListEntryThisFrame() {
            visit(Hook.PUBLICATION);
            return true;
        }
        @Override public int getSlotIndex() {
            visit(Hook.SLOT);
            return super.getSlotIndex();
        }
        @Override public int getCollisionFlags() { return 0x11; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isPersistent() { return true; }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new Probe(context.spawn());
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
