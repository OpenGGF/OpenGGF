package com.openggf.level.objects;

import com.openggf.game.GameDataSource;
import com.openggf.game.PlayableEntity;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindTransient;
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
class TestInitialSolidCallbackLifetime {
    enum Hook { MODE, PARAMS, IS_SOLID }

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
    void currentManualSolidCallbackCompletesItsInitialDispatch(Hook hook) throws Exception {
        Fixture fixture = new Fixture();
        Probe original = fixture.probe;
        original.arm(hook, () -> { });

        fixture.dispatch();

        assertEquals(1, original.callbackCalls, "the selected real solid callback must be reached");
        assertEquals(1, original.updates);
        assertEquals(1, original.checkpointReturns);
        assertEquals(0, original.staleCalls);
        assertEquals(0x52, original.marker);
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
        assertEquals(1, fixture.objects.rewindSnapshottable().capture().dynamicObjects().size());
    }

    @ParameterizedTest
    @EnumSource(Hook.class)
    void restoringInsideManualSolidCallbackRejectsItsOldTail(Hook hook) throws Exception {
        Fixture fixture = new Fixture();
        Probe original = fixture.probe;
        var snapshot = fixture.objects.rewindSnapshottable().capture();
        AtomicBoolean restoreReturned = new AtomicBoolean();
        AtomicReference<Probe> replacement = new AtomicReference<>();
        original.arm(hook, () -> {
            fixture.objects.rewindSnapshottable().restore(snapshot);
            Probe restored = fixture.currentProbe();
            assertNotSame(original, restored, "the genuine manager restore must reconstruct B");
            assertEquals(0x52, restored.marker);
            assertEquals(0, restored.updates);
            assertEquals(1, fixture.objects.rewindSnapshottable().capture().dynamicObjects().size(),
                    "B must be capturable before the old hook returns");
            replacement.set(restored);
            original.retired = true;
            original.staleMutation = () -> restored.marker = -1;
            restoreReturned.set(true);
        });

        assertThrows(ObjectCallbackAbortException.class, fixture::dispatch);

        assertTrue(restoreReturned.get(), "a failed restore prerequisite does not qualify as cancellation");
        assertEquals(1, original.callbackCalls);
        assertEquals(0, original.staleCalls, "engine code must not invoke an old hook or return to its update tail");
        assertEquals(0, original.checkpointReturns);
        Probe restored = replacement.get();
        assertNotNull(restored);
        assertSame(restored, fixture.currentProbe());
        assertEquals(0x52, restored.marker);
        assertEquals(0, restored.updates);
        assertEquals(0, restored.checkpointReturns);
        assertEquals(1, fixture.objects.rewindSnapshottable().capture().dynamicObjects().size());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    private static final class Fixture {
        final ObjectManager objects;
        final Sonic player;
        final Probe probe;

        Fixture() throws Exception {
            var module = new DelegatingGameModule(new Sonic3kGameModule(), "test:initial-solid-lifetime") {
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
            probe = objects.createDynamicObject(() -> new Probe(new ObjectSpawn(160, 300, 1, 0, 0, false, 300)));
            assertNotNull(probe);
            assertTrue(ObjectSlotLayout.SONIC_3K.isDynamicSlot(probe.getSlotIndex()));
            probe.marker = 0x52;
        }

        Probe currentProbe() {
            List<Probe> probes = objects.getActiveObjects().stream()
                    .filter(Probe.class::isInstance).map(Probe.class::cast).toList();
            assertEquals(1, probes.size());
            return probes.getFirst();
        }

        void dispatch() {
            try (InitialObjectDispatchScope scope = objects.beginInitialProcessSprites(0, player, List.of())) {
                objects.loadInitialDynamicSlots(scope);
                objects.processInitialDynamicSlots(scope);
                objects.finishInitialProcessSprites(scope);
            }
        }
    }

    private static final ObjectRegistry OBJECTS = new ObjectRegistry() {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return new Probe(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "manual-solid-probe"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
    };

    private static final class Probe extends AbstractObjectInstance implements SolidObjectProvider, RewindRecreatable {
        int marker;
        int updates;
        int checkpointReturns;
        @RewindTransient(reason = "test-owned callback selection")
        Hook hook;
        @RewindTransient(reason = "test-owned callback selection")
        boolean armed;
        @RewindTransient(reason = "test-owned callback observation")
        int callbackCalls;
        @RewindTransient(reason = "test-owned retirement observation")
        boolean retired;
        @RewindTransient(reason = "test-owned retirement observation")
        int staleCalls;
        @RewindTransient(reason = "test-owned callback, never restored gameplay state")
        Runnable callback = () -> { };
        @RewindTransient(reason = "test-owned stale-tail observation")
        Runnable staleMutation = () -> { };

        Probe(ObjectSpawn spawn) { super(spawn, "manual-solid-probe"); }

        void arm(Hook hook, Runnable callback) {
            this.hook = hook;
            this.callback = callback;
            armed = true;
        }

        private void visit(Hook actual) {
            staleTail();
            if (!armed || actual != hook) return;
            armed = false;
            callbackCalls++;
            callback.run();
        }

        private void staleTail() {
            if (retired) {
                staleCalls++;
                staleMutation.run();
            }
        }

        @Override public SolidExecutionMode solidExecutionMode() {
            visit(Hook.MODE);
            return SolidExecutionMode.MANUAL_CHECKPOINT;
        }
        @Override public SolidObjectParams getSolidParams() {
            visit(Hook.PARAMS);
            return SolidObjectParams.of(32, 16, 16);
        }
        @Override public boolean isSolidFor(PlayableEntity player) {
            visit(Hook.IS_SOLID);
            return true;
        }
        @Override public SolidRoutineProfile getSolidRoutineProfile() {
            staleTail();
            return SolidRoutineProfile.fullSolid(false, false, true);
        }
        @Override public boolean usesPreUpdatePositionForSolidContact(PlayableEntity player) {
            staleTail();
            return false;
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            staleTail();
            updates++;
            services().solidExecutionRegistry().currentObject().resolveSolidNowAll();
            staleTail();
            checkpointReturns++;
        }
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
