package com.openggf.level.objects;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Construction and injected ownership must win even with another active gameplay session. */
class TestObjectHelperServiceOwnership {
    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        ObjectConstructionContext.clearConstructionContext();
        SessionManager.clear();
    }

    @Test
    void staticManagerHelpersPreferConstructionOwnerOverActiveSession() {
        LevelManager ownedLevel = mock(LevelManager.class);
        RingManager ownedRings = mock(RingManager.class);
        TestObjectServices owner = new TestObjectServices() {
            @Override public RingManager ringManager() { return ownedRings; }
        }.withLevelManager(ownedLevel).withIsolatedObjectManager();
        assertNotSame(ownedLevel, GameServices.levelOrNull());

        ObjectConstructionContext.with(owner, () -> assertAll(
                () -> assertSame(owner.objectManager(), AbstractObjectInstance.staticObjectManager()),
                () -> assertSame(ownedLevel, AbstractObjectInstance.staticLevelManager()),
                () -> assertSame(ownedRings, AbstractObjectInstance.staticRingManager())));
    }

    @Test
    void absentConstructionManagerDoesNotBorrowTheAmbientManager() {
        TestObjectServices owner = new TestObjectServices();
        assertNotNull(GameServices.levelOrNull());
        ObjectConstructionContext.with(owner, () -> assertAll(
                () -> assertNull(AbstractObjectInstance.staticLevelManager()),
                () -> assertNull(AbstractObjectInstance.staticObjectManager()),
                () -> assertNull(AbstractObjectInstance.staticRingManager())));
    }

    @Test
    void sparkleUsesConstructionAnimationAndInjectedRenderOwner() {
        RecordingRings constructionRings = new RecordingRings(2, 10);
        TestObjectServices construction = servicesWithRings(constructionRings);
        SignpostSparkleObjectInstance sparkle = ObjectConstructionContext.construct(construction,
                () -> new SignpostSparkleObjectInstance(100, 200));
        RecordingRings injectedRings = new RecordingRings(4, 20);
        sparkle.setServices(servicesWithRings(injectedRings));

        // A different construction scope while drawing must not override the object's injected owner.
        ObjectConstructionContext.with(construction,
                () -> sparkle.appendRenderCommands(new ArrayList<GLCommand>()));
        assertEquals(List.of(10), injectedRings.frames);
        assertTrue(constructionRings.frames.isEmpty());

        for (int i = 0; i < 14; i++) sparkle.update(i, null);
        assertTrue(sparkle.isDestroyed(),
                "two owned sparkle frames retain the native six-execution duration and final delete routine");
    }

    @Test
    void skidDustRegistersWithConstructionOwnerWithoutAmbientObjectManager() {
        TestObjectServices owner = new TestObjectServices().withIsolatedObjectManager();
        TestablePlayableSprite player = playerWithDustRenderer();
        int x = player.getCentreX(), y = player.getCentreY();

        ObjectConstructionContext.with(owner, () -> SkidDustObjectInstance.spawn(player));

        assertEquals(1, owner.objectManager().getActiveObjects().size());
        ObjectInstance dust = owner.objectManager().getActiveObjects().iterator().next();
        assertInstanceOf(SkidDustObjectInstance.class, dust);
        assertEquals(x, dust.getX());
        assertEquals(y + 16, dust.getY());
    }

    @Test
    void ordinarySkidSpawnUsesActivePlayerManagerWithoutConstructionContext() throws Exception {
        TestObjectServices owner = new TestObjectServices().withIsolatedObjectManager();
        var field = LevelManager.class.getDeclaredField("objectManager");
        field.setAccessible(true);
        field.set(GameServices.levelOrNull(), owner.objectManager());
        TestablePlayableSprite player = playerWithDustRenderer();
        int x = player.getCentreX(), y = player.getCentreY();
        assertNull(AbstractObjectInstance.currentConstructionContext());

        SkidDustObjectInstance.spawn(player);

        List<SkidDustObjectInstance> effects = owner.objectManager().activeObjectsOfType(SkidDustObjectInstance.class);
        assertEquals(1, effects.size());
        assertEquals(x, effects.getFirst().getX());
        assertEquals(y + 16, effects.getFirst().getY());
        assertSame(owner, effects.getFirst().services());
        assertNull(AbstractObjectInstance.currentConstructionContext(), "the temporary construction scope is retired");
    }

    @Test
    void splashCreationOnlyRequiresThePlayersRenderer() {
        TestablePlayableSprite player = playerWithDustRenderer();
        int x = player.getCentreX();
        SessionManager.clear();
        SplashObjectInstance splash = ObjectConstructionContext.construct(new TestObjectServices(),
                () -> SplashObjectInstance.create(player, 123));
        assertNotNull(splash, "an unrelated ambient level is not a splash creation prerequisite");
        assertEquals(x, splash.getX());
        assertEquals(123, splash.getY());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void injectedSpawnFailureDoesNotPublishIntoAmbientSession(boolean lowestFreeSlot) throws Exception {
        ObjectManager ambient = new TestObjectServices().withIsolatedObjectManager().objectManager();
        var field = LevelManager.class.getDeclaredField("objectManager");
        field.setAccessible(true);
        field.set(GameServices.levelOrNull(), ambient);
        IllegalStateException ownedFailure = new IllegalStateException("owned manager failed");
        SpawnProbe parent = new SpawnProbe();
        parent.setServices(new TestObjectServices() {
            @Override public ObjectManager objectManager() { throw ownedFailure; }
        });
        SpawnProbe child = new SpawnProbe();

        assertAll(
                () -> assertSame(ownedFailure, assertThrows(IllegalStateException.class,
                        () -> parent.spawn(child, lowestFreeSlot))),
                () -> assertTrue(ambient.getActiveObjects().isEmpty(),
                        "an owned failure cannot transfer the child to the ambient session"));
    }

    @Test
    void skidDustUsesConstructionOwnersGravity() {
        com.openggf.game.GameStateManager state = new com.openggf.game.GameStateManager();
        state.setReverseGravityActive(true);
        TestObjectServices owner = new TestObjectServices().withIsolatedObjectManager().withGameState(state);
        TestablePlayableSprite player = playerWithDustRenderer();
        int y = player.getCentreY();

        ObjectConstructionContext.with(owner, () -> SkidDustObjectInstance.spawn(player));

        ObjectInstance dust = owner.objectManager().getActiveObjects().iterator().next();
        assertEquals(y - 16, dust.getY(), "the native foot offset follows the effect owner's gravity");
    }

    private static final class SpawnProbe extends AbstractObjectInstance {
        SpawnProbe() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "spawn-probe"); }
        void spawn(SpawnProbe child, boolean lowestFreeSlot) {
            if (lowestFreeSlot) spawnDynamicObjectLowestFreeSlot(child);
            else spawnDynamicObject(child);
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static TestablePlayableSprite playerWithDustRenderer() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        player.setSpindashDustController(new SpindashDustController(player, mock(PlayerSpriteRenderer.class)));
        return player;
    }

    private static TestObjectServices servicesWithRings(RingManager rings) {
        return new TestObjectServices() {
            @Override public RingManager ringManager() { return rings; }
        };
    }

    private static final class RecordingRings extends RingManager {
        private final int count;
        private final int start;
        final List<Integer> frames = new ArrayList<>();
        RecordingRings(int count, int start) {
            super(List.of(), null, null, null, null);
            this.count = count;
            this.start = start;
        }
        @Override public int getSparkleFrameCount() { return count; }
        @Override public int getSparkleStartIndex() { return start; }
        @Override public void drawFrameIndex(int frame, int x, int y) { frames.add(frame); }
    }
}
