package com.openggf.physics;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.Test;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestBackgroundPlaneCollisionProvider {

    @Test
    void inactiveLegacyStateProducesForegroundOnlyProbe() {
        GameStateManager gameState = new GameStateManager();
        BackgroundPlaneCollisionProvider provider = provider(gameState, new Camera(),
                new ParallaxManager(), new ZoneRuntimeRegistry(), mock(LevelManager.class));

        assertFalse(provider.state().active());
        assertFalse(provider.state().active());
    }

    @Test
    void legacyStateUsesLiveScrollHandlerCameraDifferences() throws Exception {
        GameStateManager gameState = new GameStateManager();
        gameState.setBackgroundCollisionFlag(true);
        Camera camera = new Camera();
        camera.setX((short) 0x120);
        camera.setY((short) 0x80);
        ParallaxManager parallax = new ParallaxManager();
        LevelManager level = mock(LevelManager.class);
        when(level.getFeatureZoneId()).thenReturn(5);
        installHandler(parallax, 5, new TestHandler(0x100, (short) 0x60));

        BackgroundPlaneCollisionProvider provider = provider(gameState, camera, parallax,
                new ZoneRuntimeRegistry(), level);

        assertEquals(new BackgroundPlaneCollisionProvider.State(true, 0x20, 0x20), provider.state());
        var state = provider.state();
        assertEquals(0x1E0, provider.backgroundX(state, 0x200, Direction.DOWN));
        assertEquals(0x2E0, provider.backgroundY(state, 0x300));
    }

    @Test
    void unchangedInputsReuseStateUntilCameraSemanticsChange() throws Exception {
        GameStateManager gameState = new GameStateManager();
        gameState.setBackgroundCollisionFlag(true);
        Camera camera = new Camera();
        ParallaxManager parallax = new ParallaxManager();
        Field cachedBg = ParallaxManager.class.getDeclaredField("cachedBgCameraX");
        cachedBg.setAccessible(true);
        cachedBg.set(parallax, 0);
        BackgroundPlaneCollisionProvider provider = provider(gameState, camera, parallax,
                new ZoneRuntimeRegistry(), mock(LevelManager.class));

        var first = provider.state();
        assertSame(first, provider.state());
        camera.setX((short) 1);
        var changed = provider.state();
        assertNotSame(first, changed);
        assertEquals(1, changed.cameraDiffX());
    }

    @Test
    void explicitZoneStateIsTheSingleAuthorityWhenPresent() {
        GameStateManager gameState = new GameStateManager();
        gameState.setBackgroundCollisionFlag(false);
        ZoneRuntimeRegistry registry = new ZoneRuntimeRegistry();
        registry.install(new ExplicitState(new BackgroundPlaneCollisionProvider.State(true, -0x30, 0x18)));

        BackgroundPlaneCollisionProvider provider = provider(gameState, new Camera(),
                new ParallaxManager(), registry, mock(LevelManager.class));

        assertEquals(new BackgroundPlaneCollisionProvider.State(true, -0x30, 0x18), provider.state());
        var state = provider.state();
        assertEquals(0x230, provider.backgroundX(state, 0x200, Direction.DOWN));
        assertEquals(0x2E8, provider.backgroundY(state, 0x300));
    }

    @Test
    void leftWallUsesRomComplementedWordTranslationForNonAlignedSignedDiff() {
        BackgroundPlaneCollisionProvider provider = () ->
                new BackgroundPlaneCollisionProvider.State(true, 3, -5);

        assertEquals(0x1231,
                provider.backgroundX(provider.state(), 0x1234, Direction.RIGHT));
        assertEquals(0x1237,
                provider.backgroundX(provider.state(), 0x1234, Direction.LEFT));
        assertEquals((short) -31,
                (short) provider.backgroundX(provider.state(), -2, Direction.LEFT));
    }

    @Test
    void newGameplayContextClearsExplicitCollisionStateToForegroundOnly() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        GameServices.zoneRuntimeRegistry().install(
                new ExplicitState(new BackgroundPlaneCollisionProvider.State(true, 7, 9)));
        assertTrue(GameServices.backgroundPlaneCollision().state().active());

        TestEnvironment.resetAll();

        assertFalse(GameServices.backgroundPlaneCollision().state().active());
        assertFalse(GameServices.backgroundPlaneCollision().state().active());
        SessionManager.clear();
    }

    private static BackgroundPlaneCollisionProvider provider(GameStateManager gameState,
                                                               Camera camera,
                                                               ParallaxManager parallax,
                                                               ZoneRuntimeRegistry registry,
                                                               LevelManager level) {
        return new DefaultBackgroundPlaneCollisionProvider(gameState, camera, parallax, registry, () -> level);
    }

    private static void installHandler(ParallaxManager parallax, int zoneId, ZoneScrollHandler handler)
            throws Exception {
        ScrollHandlerProvider scrollProvider = new ScrollHandlerProvider() {
            @Override public void load(com.openggf.data.Rom rom) { }
            @Override public ZoneScrollHandler getHandler(int zoneIndex) {
                return zoneIndex == zoneId ? handler : null;
            }
            @Override public ScrollHandlerProvider.ZoneConstants getZoneConstants() {
                return mock(ScrollHandlerProvider.ZoneConstants.class);
            }
        };
        Field field = ParallaxManager.class.getDeclaredField("scrollProvider");
        field.setAccessible(true);
        field.set(parallax, scrollProvider);
    }

    private record TestHandler(int bgCameraX, short bgY) implements ZoneScrollHandler {
        @Override public void update(int[] buffer, int cameraX, int cameraY, int frameCounter, int actId) { }
        @Override public short getVscrollFactorBG() { return bgY; }
        @Override public int getMinScrollOffset() { return 0; }
        @Override public int getMaxScrollOffset() { return 0; }
        @Override public int getBgCameraX() { return bgCameraX; }
    }

    private record ExplicitState(BackgroundPlaneCollisionProvider.State collisionState)
            implements ZoneRuntimeState {
        @Override public String gameId() { return "test"; }
        @Override public int zoneIndex() { return 0; }
        @Override public int actIndex() { return 0; }
        @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
            return collisionState;
        }
    }
}
