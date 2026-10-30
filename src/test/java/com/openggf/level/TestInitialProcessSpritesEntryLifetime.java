package com.openggf.level;

import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.InitStep;
import com.openggf.game.InitialProcessSpritesLifecycle;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.PlayableEntity;
import com.openggf.game.StaticFixup;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.internal.InitialLevelScrollPreparation;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.InitialObjectDispatchScope;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestInitialProcessSpritesEntryLifetime {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0x80, 0x80, 0xFF, 0, 0, false, 0x80);

    enum CallbackPoint { PROFILE_LOOKUP, SCROLL_PREPARATION, FIXED_DISPATCHER_FACTORY }

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
    @EnumSource(CallbackPoint.class)
    void currentEntryCallbackStillAdmitsItsInitialDispatch(CallbackPoint point) throws Exception {
        Fixture fixture = new Fixture(point, false);
        fixture.armed = true;

        assertTrue(fixture.level.consumePendingInitialProcessSpritesPass());

        assertEquals(1, fixture.callbackCount);
        assertSame(fixture.original, fixture.level.getObjectManager());
        assertEquals(1, fixture.originalProbe.updates);
        assertEquals(1, fixture.original.getFrameCounter());
        assertFalse(fixture.original.hasActiveInitialProcessSpritesDispatch());
        assertFalse(fixture.level.hasPendingInitialProcessSpritesPass());
    }

    @ParameterizedTest
    @EnumSource(CallbackPoint.class)
    void replacementBeforeInitialScopeRejectsTheOriginalDispatch(CallbackPoint point) throws Exception {
        Fixture fixture = new Fixture(point, true);
        fixture.armed = true;

        assertThrows(ObjectCallbackAbortException.class,
                fixture.level::consumePendingInitialProcessSpritesPass);

        assertEquals(1, fixture.callbackCount);
        assertNotNull(fixture.successor, "the callback must genuinely install B before A is rejected");
        assertSame(fixture.successor, fixture.level.getObjectManager());
        assertEquals(0, fixture.originalProbe.updates);
        assertEquals(0, fixture.original.getFrameCounter(),
                "A must not begin an initial scope after its entry callback replaces it");
        assertEquals(1, fixture.successorProbe.updates,
                "the callback's completed B pass is the only dispatch allowed to update B");
        assertEquals(1, fixture.successor.getFrameCounter());
        assertSame(fixture.successorProbe, fixture.successor.getActiveObjectForRewind(SPAWN));
        assertEquals(1, fixture.successor.rewindSnapshottable().capture().slots().size());
        assertFalse(fixture.original.hasActiveInitialProcessSpritesDispatch());
        assertFalse(fixture.successor.hasActiveInitialProcessSpritesDispatch());
        assertFalse(fixture.level.hasPendingInitialProcessSpritesPass());
    }

    private static final class Fixture {
        final CallbackPoint point;
        final boolean replace;
        final LevelManager level;
        final ObjectManager original;
        final Probe originalProbe;
        boolean armed;
        int callbackCount;
        ObjectManager successor;
        Probe successorProbe;

        Fixture(CallbackPoint point, boolean replace) throws Exception {
            this.point = point;
            this.replace = replace;
            var profile = new RequestingProfile(() -> callback(CallbackPoint.SCROLL_PREPARATION));
            GameModule module = new DelegatingGameModule(new Sonic3kGameModule(), "test:initial-entry-lifetime") {
                @Override public LevelInitProfile getLevelInitProfile() {
                    callback(CallbackPoint.PROFILE_LOOKUP);
                    return profile;
                }
                @Override public TouchResponseTable createTouchResponseTable(GameDataSource source) {
                    return null;
                }
                @Override public ObjectRegistry createObjectRegistry() { return OBJECTS; }
                @Override public InitialFixedSstDispatcher createInitialFixedSstDispatcher(
                        SpriteManager sprites, ObjectManager objects, ZoneFeatureProvider features) {
                    callback(CallbackPoint.FIXED_DISPATCHER_FACTORY);
                    return epoch -> { };
                }
            };
            TestEnvironment.configureGameModuleFixture(module);
            level = TestEnvironment.activeGameplayMode().getLevelManager();
            level.initializeCollisionOnly(new EmptyTerrain(), 0, 0);
            level.initObjectManager();
            original = level.getObjectManager();
            original.reset(0);
            original.preloadInitialSpawnsForHydration();
            originalProbe = (Probe) original.getActiveObjectForRewind(SPAWN);
            assertNotNull(originalProbe);
            var fresh = new LevelLoadContext();
            fresh.setIncludePostLoadAssembly(true);
            fresh.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
            level.loadLevel(0, LevelLoadMode.FULL, fresh);
            assertTrue(level.hasPendingInitialProcessSpritesPass(),
                    "only the successful public fresh load may arm the tested initial pass");
            assertEquals(1, level.getCompletedProductionLoadGeneration());
        }

        void callback(CallbackPoint actual) {
            if (!armed || actual != point) return;
            armed = false;
            callbackCount++;
            if (!replace) return;
            try {
                level.initObjectManager();
            } catch (java.io.IOException failure) {
                throw new AssertionError("replacement manager construction must succeed", failure);
            }
            successor = level.getObjectManager();
            assertNotSame(original, successor);
            successor.reset(0);
            successor.preloadInitialSpawnsForHydration();
            successorProbe = (Probe) successor.getActiveObjectForRewind(SPAWN);
            assertNotNull(successorProbe);
            try (InitialObjectDispatchScope scope = successor.beginInitialProcessSprites(0, null, List.of())) {
                successor.loadInitialDynamicSlots(scope);
                successor.processInitialDynamicSlots(scope);
                successor.finishInitialProcessSprites(scope);
            }
            assertEquals(1, successorProbe.updates,
                    "B's own initial dispatch must complete while A's entry callback is suspended");
            assertFalse(successor.hasActiveInitialProcessSpritesDispatch());
            assertEquals(1, successor.rewindSnapshottable().capture().slots().size());
        }
    }

    private record RequestingProfile(Runnable prepare) implements LevelInitProfile, InitialLevelScrollPreparation {
        @Override public void prepareInitialLevelScroll() { prepare.run(); }
        @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
            return List.of(new InitStep("RequestInitialProcessSprites", "test",
                    () -> context.requestInitialProcessSpritesFromProfile(
                            InitialProcessSpritesLifecycle.LOAD_THEN_PROCESS_ONCE)));
        }
        @Override public List<InitStep> levelTeardownSteps() { return List.of(); }
        @Override public List<InitStep> perTestResetSteps() { return List.of(); }
        @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }
    }

    private static final ObjectRegistry OBJECTS = new ObjectRegistry() {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return new Probe(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int objectId) { return "initial-entry-probe"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
    };

    private static final class Probe extends AbstractObjectInstance {
        int updates;
        Probe(ObjectSpawn spawn) { super(spawn, "initial-entry-probe"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { updates++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isPersistent() { return true; }
    }

    private static final class EmptyTerrain extends AbstractLevel {
        EmptyTerrain() {
            super(0);
            palettes = new Palette[PALETTE_COUNT];
            java.util.Arrays.setAll(palettes, ignored -> new Palette());
            patterns = new Pattern[]{new Pattern()};
            chunks = new Chunk[]{new Chunk()};
            blocks = new Block[]{new Block()};
            solidTiles = new SolidTile[]{new SolidTile(0, new byte[16], new byte[16], (byte) 0)};
            patternCount = chunkCount = blockCount = solidTileCount = 1;
            map = new com.openggf.level.Map(2, 8, 8);
            maxX = maxY = 8 * getBlockPixelSize();
            objects = List.of(SPAWN);
            rings = List.of();
        }
    }
}
