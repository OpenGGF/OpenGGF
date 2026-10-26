package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameStateManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.PointsObjectInstance;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic2.objects.badniks.NebulaBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.RexonBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.RexonHeadObjectInstance;
import com.openggf.game.sonic2.objects.badniks.TurtloidBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.TurtloidRiderInstance;
import com.openggf.level.ParallaxManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real destruction callbacks and allocation owners; synthetic contact entries, not a TAS route. */
class TestS2ChainScoreConsumers {
    private ObjectManager objects;
    private TestObjectServices services;
    private TestablePlayableSprite player;
    private PatternSpriteRenderer pointsRenderer;

    @BeforeEach
    void setUp() {
        var module = new Sonic2GameModule();
        GameModuleRegistry.setCurrent(module);
        TestEnvironment.configureGameModuleFixture(module);
        AbstractObjectInstance.resetCameraBoundsForTests();
        var camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        var art = mock(ObjectRenderManager.class);
        pointsRenderer = mock(PatternSpriteRenderer.class);
        when(art.getPointsRenderer()).thenReturn(pointsRenderer);
        var sprites = mock(SpriteManager.class);
        player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 160);
        player.setCentreY((short) 112);
        when(sprites.getAllSprites()).thenReturn(List.of(player));
        services = new TestObjectServices() {
            @Override public ObjectRenderManager renderManager() { return art; }
        };
        services.withCamera(camera).withGameState(new GameStateManager())
                .withSpriteManager(sprites).withParallaxManager(mock(ParallaxManager.class))
                .withDebugOverlay(mock(DebugOverlayManager.class));
        objects = new ObjectManager(List.of(), new Sonic2ObjectRegistry(), 0, null, null,
                null, camera, services);
        services.withDirectObjectManager(objects);
        defeatFifteenRealEnemies();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void turtloidRiderSixteenthKillAwardsTenThousandAndDrawsFrameFive() {
        var base = objects.createDynamicObject(() -> new TurtloidBadnikInstance(
                spawn(Sonic2ObjectIds.TURTLOID)));
        base.update(0, player);
        var rider = only(TurtloidRiderInstance.class);
        rider.snapshotTouchResponseState();
        int before = services.gameState().getScore();

        rider.onPlayerAttack(player, null);

        assertTrue(rider.isDestroyed());
        assertFalse(base.isParentDestroyed(), "only the rider is defeated");
        assertEquals(10_000, services.gameState().getScore() - before);
        assertPopupDrawsBonusFrame();
    }

    @Test
    void rexonTipSixteenthKillAwardsTenThousandAndDrawsFrameFive() {
        var base = objects.createDynamicObject(() -> new RexonBadnikInstance(spawn(Sonic2ObjectIds.REXON)));
        base.update(0, player);
        var tips = objects.getActiveObjects().stream().filter(RexonHeadObjectInstance.class::isInstance)
                .map(RexonHeadObjectInstance.class::cast).filter(head -> (head.getCollisionFlags() & 0xC0) == 0)
                .toList();
        assertEquals(1, tips.size(), "the real parent creates one attackable tip");
        var tip = tips.getFirst();
        int before = services.gameState().getScore();

        tip.onPlayerAttack(player, null);

        assertTrue(tip.isDestroyed());
        assertFalse(base.isDestroyed());
        assertEquals(10_000, services.gameState().getScore() - before);
        assertPopupDrawsBonusFrame();
    }

    @Test
    void ordinarySixteenthKillCarriesBonusThroughDeferredAnimalIntoPopup() {
        int before = services.gameState().getScore();
        var enemy = nebula();

        enemy.onPlayerAttack(player, null);

        assertTrue(enemy.isDestroyed());
        assertEquals(10_000, services.gameState().getScore() - before);
        assertFalse(objects.getActiveObjects().stream().anyMatch(PointsObjectInstance.class::isInstance));
        only(ExplosionObjectInstance.class).update(0, player);
        var animal = only(AnimalObjectInstance.class);
        assertFalse(objects.getActiveObjects().stream().anyMatch(PointsObjectInstance.class::isInstance),
                "Obj27 passes the score to Obj28; Obj28 owns the deferred points allocation");

        animal.update(0, player);

        assertPopupDrawsBonusFrame();
    }

    private void defeatFifteenRealEnemies() {
        for (int kill = 1; kill <= 15; kill++) {
            var enemy = nebula();
            enemy.onPlayerAttack(player, null);
            assertTrue(enemy.isDestroyed(), "preroll kill " + kill);
            // Omit the later explosion/animal loop deliberately; retire every
            // aftermath object so the next real kill has free native slots.
            for (var object : List.copyOf(objects.getActiveObjects())) {
                objects.removeDynamicObject(object);
            }
        }
        assertEquals(12_800, services.gameState().getScore(), "100+200+500+12*1000 before kill 16");
    }

    private NebulaBadnikInstance nebula() {
        return objects.createDynamicObject(() -> new NebulaBadnikInstance(spawn(Sonic2ObjectIds.NEBULA)));
    }

    private void assertPopupDrawsBonusFrame() {
        var popup = only(PointsObjectInstance.class);
        popup.appendRenderCommands(new ArrayList<>());
        verify(pointsRenderer).drawFrameIndex(eq(5), anyInt(), anyInt(), eq(false), eq(false));
    }

    private <T> T only(Class<T> type) {
        var matches = objects.getActiveObjects().stream().filter(type::isInstance).map(type::cast).toList();
        assertEquals(1, matches.size(), type.getSimpleName());
        return matches.getFirst();
    }

    private static ObjectSpawn spawn(int id) {
        return new ObjectSpawn(160, 112, id, 0, 0, false, 0);
    }
}
