package com.openggf.mods.code;

import com.openggf.game.GameDataSource;
import com.openggf.game.PlayableEntity;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.AbstractLevel;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.InitialObjectDispatchScope;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestInitialDispatchCreatorCancellation {
    private static final String OWNER = "initial-dispatch-owner";
    private static final ObjectSpawn FIRST = spawn(0x80);
    private static final ObjectSpawn SECOND = spawn(0xC0);

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
    void currentRegisteredCreatorCompletesInitialDispatchWithoutQuarantine() throws Exception {
        Fixture fixture = new Fixture();

        fixture.dispatch();

        assertEquals(1, fixture.first().updates);
        assertEquals(1, fixture.second().updates);
        fixture.assertOwnerAvailable();
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @Test
    void creatorRestoreCancelsOnlyTheSuspendedDispatchWithoutQuarantine() throws Exception {
        Fixture fixture = new Fixture();
        Probe oldFirst = fixture.first();
        Probe oldSecond = fixture.second();
        var captured = fixture.objects.rewindSnapshottable().capture();
        Probe[] restored = new Probe[2];
        oldFirst.action = () -> {
            fixture.objects.rewindSnapshottable().restore(captured);
            restored[0] = fixture.first();
            restored[1] = fixture.second();
            assertNotSame(oldFirst, restored[0], "the actual placement factory must reconstruct B");
            assertNotSame(oldSecond, restored[1]);
            assertEquals(0, restored[0].updates);
            assertEquals(0, restored[1].updates);
            assertEquals(2, fixture.objects.rewindSnapshottable().capture().slots().size(),
                    "B must remain capturable before the suspended creator returns");
        };

        var aborted = assertThrows(ObjectCallbackAbortException.class, fixture::dispatch);

        assertFalse(aborted instanceof ModFaultBoundary.CallbackAborted,
                "engine supersession must not become a creator fault");
        assertEquals(1, oldFirst.updates);
        assertEquals(0, oldSecond.updates);
        assertSame(restored[0], fixture.first());
        assertSame(restored[1], fixture.second());
        assertEquals(0, fixture.first().updates);
        assertEquals(0, fixture.second().updates,
                "the resumed A walk must not execute B's next slot");
        assertEquals(captured.frameCounter(), fixture.objects.getFrameCounter());
        fixture.assertOwnerAvailable();
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    @Test
    void ordinaryRegisteredCreatorFailureStillQuarantinesItsOwner() throws Exception {
        Fixture fixture = new Fixture();
        var failure = new IllegalStateException("creator failed during initial dispatch");
        fixture.first().action = () -> { throw failure; };

        fixture.assertQuarantined(failure);
    }

    @Test
    void creatorCannotForgeEngineCancellationWithThePublicAbortBase() throws Exception {
        Fixture fixture = new Fixture();
        var forged = new ForgedAbort();
        fixture.first().action = () -> { throw forged; };

        fixture.assertQuarantined(forged);
    }

    private static ObjectSpawn spawn(int x) {
        return new ObjectSpawn(x, 0x80, 0xFF, 0, 0, false, 0x80, 0,
                OWNER, OWNER + ":probe");
    }

    private static final class Fixture {
        final ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        final List<Set<String>> disabled = new ArrayList<>();
        final ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), disabled::add);
        final ObjectManager objects;
        final Sonic player = new Sonic("sonic", (short) 0x80, (short) 0x80);

        Fixture() throws Exception {
            var registration = new ModContext(OWNER, "s3k", ModAssetRoot.forTests(OWNER));
            registration.registerObject("probe", (spawn, registry) -> new Probe(spawn));
            var stock = new DelegatingGameModule(new Sonic3kGameModule(), "test:creator-initial-dispatch") {
                @Override public TouchResponseTable createTouchResponseTable(GameDataSource source) {
                    return null;
                }
            };
            var module = new ModBackedGamePatch(registration.freeze(), boundary).apply(stock, null);
            var context = SessionManager.openGameplaySession(module);
            GameplaySessionFactory.attachManagers(context, EngineServices.current());
            context.getSpriteManager().addSprite(player);
            context.getCamera().setFocusedSprite(player);
            var level = context.getLevelManager();
            level.initializeCollisionOnly(new EmptyTerrain(), 0, 0);
            level.initObjectManager();
            objects = level.getObjectManager();
            objects.reset(0);
            objects.preloadInitialSpawnsForHydration();
            objects.setRewindInPlaceRestoreEnabledForTest(false);
            assertNotNull(first(), "the registered placement must be present before dispatch");
            assertNotNull(second());
        }

        Probe first() { return (Probe) objects.getActiveObjectForRewind(FIRST); }
        Probe second() { return (Probe) objects.getActiveObjectForRewind(SECOND); }

        void dispatch() {
            try (InitialObjectDispatchScope scope = objects.beginInitialProcessSprites(0, player, List.of())) {
                objects.loadInitialDynamicSlots(scope);
                objects.processInitialDynamicSlots(scope);
                objects.finishInitialProcessSprites(scope);
            }
        }

        void assertOwnerAvailable() {
            assertTrue(boundary.isOwnerAvailable(OWNER));
            assertTrue(findings.findingsFor(OWNER).isEmpty());
            assertTrue(disabled.isEmpty());
        }

        void assertQuarantined(RuntimeException primary) {
            var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class, this::dispatch);
            assertSame(primary, aborted.getCause());
            assertEquals(OWNER, aborted.owner());
            assertEquals(Set.of(OWNER), aborted.disabledOwners());
            assertEquals(List.of(Set.of(OWNER)), disabled);
            assertFalse(boundary.isOwnerAvailable(OWNER));
            assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor(OWNER).getFirst().code());
            assertEquals(0, second().updates);
            assertFalse(objects.hasActiveInitialProcessSpritesDispatch());
        }
    }

    private static final class ForgedAbort extends ObjectCallbackAbortException {
        ForgedAbort() { super("creator-forged callback cancellation", null); }
    }

    private static final class Probe extends AbstractObjectInstance {
        int updates;
        @RewindTransient(reason = "test-owned callback, never restored gameplay state")
        Runnable action = () -> { };

        Probe(ObjectSpawn spawn) { super(spawn, "registered-initial-dispatch-probe"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            updates++;
            action.run();
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isPersistent() { return true; }
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
            objects = List.of(FIRST, SECOND);
            rings = List.of();
        }
    }
}
