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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestInitialSolidAdapterLifetime {
    enum Hook { SLOPE_PROFILE, SLOPE_SAMPLE, PIECE_WIDTH }

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
    void currentAdapterCallbackCompletesItsInitialDispatch(Hook hook) throws Exception {
        Fixture fixture = new Fixture(hook);
        fixture.probe.arm(hook, () -> { });

        fixture.dispatch();

        assertEquals(1, fixture.probe.callbackCalls, "the selected adapter callback must be reached");
        assertEquals(1, fixture.probe.updates);
        assertEquals(1, fixture.probe.checkpointReturns);
        assertEquals(0, fixture.probe.staleCalls);
        assertEquals(0x52, fixture.probe.marker);
        assertEquals(1, fixture.objects.rewindSnapshottable().capture().dynamicObjects().size());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @ParameterizedTest
    @EnumSource(Hook.class)
    void restoredAdapterOwnerSurvivesWithoutRetiredCallbacks(Hook hook) throws Exception {
        Fixture fixture = new Fixture(hook);
        Probe original = fixture.probe;
        var snapshot = fixture.objects.rewindSnapshottable().capture();
        AtomicBoolean restoreReturned = new AtomicBoolean();
        AtomicReference<Probe> replacement = new AtomicReference<>();
        original.arm(hook, () -> {
            fixture.objects.rewindSnapshottable().restore(snapshot);
            Probe restored = fixture.currentProbe();
            assertNotSame(original, restored, "the actual manager restore must reconstruct B");
            assertEquals(original.getClass(), restored.getClass());
            assertEquals(0x52, restored.marker);
            assertEquals(0, restored.updates);
            assertEquals(1, fixture.objects.rewindSnapshottable().capture().dynamicObjects().size(),
                    "B must remain capturable before the old adapter callback returns");
            replacement.set(restored);
            original.retired = true;
            original.staleMutation = () -> restored.marker = -1;
            restoreReturned.set(true);
        });

        assertThrows(ObjectCallbackAbortException.class, fixture::dispatch);

        assertTrue(restoreReturned.get(), "restore prerequisites must complete before cancellation qualifies");
        assertEquals(1, original.callbackCalls);
        assertEquals(0, original.staleCalls,
                "no retired slope, piece, profile or update callback may run after replacement");
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

        Fixture(Hook hook) throws Exception {
            var module = new DelegatingGameModule(new Sonic3kGameModule(), "test:initial-solid-adapter") {
                @Override public TouchResponseTable createTouchResponseTable(GameDataSource source) { return null; }
                @Override public ObjectRegistry createObjectRegistry() { return OBJECTS; }
            };
            var context = SessionManager.openGameplaySession(module);
            GameplaySessionFactory.attachManagers(context, EngineServices.current());
            player = new Sonic("sonic", (short) 160, (short) 256);
            player.setAir(true);
            player.setYSpeed((short) 0);
            context.getSpriteManager().addSprite(player);
            context.getCamera().setFocusedSprite(player);
            var level = context.getLevelManager();
            level.initializeCollisionOnly(new EmptyTerrain(), 0, 0);
            level.initObjectManager();
            objects = level.getObjectManager();
            objects.reset(0);
            objects.setRewindInPlaceRestoreEnabledForTest(false);
            assertInstanceOf(DefaultSolidExecutionRegistry.class, context.getSolidExecutionRegistry());
            int objectId = hook == Hook.PIECE_WIDTH ? 2 : 1;
            ObjectSpawn spawn = new ObjectSpawn(player.getCentreX(),
                    player.getCentreY() + player.getYRadius() + 12, objectId, 0, 0, false, 0);
            probe = objects.createDynamicObject(() -> createProbe(spawn));
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

    private static Probe createProbe(ObjectSpawn spawn) {
        return spawn.objectId() == 2 ? new PieceProbe(spawn) : new SlopeProbe(spawn);
    }

    private static final ObjectRegistry OBJECTS = new ObjectRegistry() {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return createProbe(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "solid-adapter-probe"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
    };

    private abstract static class Probe extends AbstractObjectInstance
            implements SolidObjectProvider, RewindRecreatable {
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

        Probe(ObjectSpawn spawn) { super(spawn, "solid-adapter-probe"); }

        void arm(Hook hook, Runnable callback) {
            this.hook = hook;
            this.callback = callback;
            armed = true;
        }

        void visit(Hook actual) {
            staleTail();
            if (!armed || hook != actual) return;
            armed = false;
            callbackCalls++;
            callback.run();
        }

        void staleTail() {
            if (retired) {
                staleCalls++;
                staleMutation.run();
            }
        }

        @Override public SolidExecutionMode solidExecutionMode() {
            staleTail();
            return SolidExecutionMode.MANUAL_CHECKPOINT;
        }
        @Override public SolidObjectParams getSolidParams() {
            staleTail();
            return SolidObjectParams.of(32, 16, 16);
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
            return createProbe(context.spawn());
        }
    }

    private static final class SlopeProbe extends Probe implements SlopedSolidProvider {
        SlopeProbe(ObjectSpawn spawn) { super(spawn); }
        @Override public SlopedSolidRoutineProfile getSlopedSolidRoutineProfile() {
            visit(Hook.SLOPE_PROFILE);
            return new SlopedSolidRoutineProfile(true, false, false, 0);
        }
        @Override public byte[] getSlopeData() {
            staleTail();
            byte[] slope = new byte[128];
            Arrays.fill(slope, (byte) 16);
            return slope;
        }
        @Override public boolean isSlopeFlipped() { staleTail(); return false; }
        @Override public int getSlopeSampleShift() { staleTail(); return 0; }
        @Override public Integer sampleSlopeByte(int sampleIndex) {
            visit(Hook.SLOPE_SAMPLE);
            return 16;
        }
    }

    private static final class PieceProbe extends Probe implements MultiPieceSolidProvider {
        PieceProbe(ObjectSpawn spawn) { super(spawn); }
        @Override public int getPieceCount() { staleTail(); return 1; }
        @Override public int getPieceX(int index) { staleTail(); return getSpawn().x(); }
        @Override public int getPieceY(int index) { staleTail(); return getSpawn().y(); }
        @Override public boolean usesPieceSpecificLandingHalfWidths() { staleTail(); return true; }
        @Override public int getPieceLandingHalfWidth(int index) {
            visit(Hook.PIECE_WIDTH);
            return 32;
        }
    }

    private static final class EmptyTerrain extends AbstractLevel {
        EmptyTerrain() {
            super(0);
            palettes = new com.openggf.level.Palette[PALETTE_COUNT];
            Arrays.setAll(palettes, ignored -> new com.openggf.level.Palette());
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
