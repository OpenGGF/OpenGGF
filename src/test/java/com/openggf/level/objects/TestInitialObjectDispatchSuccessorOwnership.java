package com.openggf.level.objects;

import com.openggf.game.GameDataSource;
import com.openggf.game.PlayableEntity;
import com.openggf.game.patch.DelegatingGameModule;
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

import java.util.List;
import java.util.Map;

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
        boolean publishStanding;
        @RewindTransient(reason = "test-owned callback, never restored gameplay state")
        Runnable action = () -> { };

        Probe(ObjectSpawn spawn) { super(spawn, "initial-dispatch-probe"); }

        @Override public void update(int vIntRunCount, PlayableEntity player) {
            updates++;
            action.run();
            if (publishStanding) services().solidExecutionRegistry().publishCheckpoint(standing(this, player));
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
