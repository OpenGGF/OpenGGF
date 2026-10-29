package com.openggf.physics;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.level.LevelManager;
import com.openggf.level.DelegatingLevel;
import com.openggf.game.internal.EncodedCollisionLayout;
import com.openggf.level.WaterSystem;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Golden outputs captured from GroundSensor at pre-extraction 0ffa7f3c7.
 * Includes signed distance, angle, tile, direction and every native angle-write field;
 * empty and angle-only defaults are deliberately retained, not converted to misses.
 */
class TestPlayerTerrainScanEquivalence {
    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1() throws Exception {
        verify("s1", "sonic1.rom.path", "ca6fbf21a004fb8bec30ca7add70b3cf3fee87928ba86b2cdb7257d579a66f4d");
    }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2() throws Exception {
        verify("s2", "sonic2.rom.path", "91ad08392a292345d0c38ba5831271074a5c2ab6e61cc91a9c9ce8ae473ba82c");
    }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3k() throws Exception {
        verify("s3k", "s3k.rom.path", "5ade97e5f1d420067331c1cf7abfffcc34d0100c8a9d5b527a8e0dced0ca4e97");
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void ordinaryVerticalScanStillUsesTheEncodedTerrainOwner() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of(
                "s3k", Path.of(System.getProperty("s3k.rom.path"))))) {
            var module = library.module("s3k");
            var terrain = spy(library.level("s3k", 0, 0));
            var encoded = assertInstanceOf(EncodedCollisionLayout.class, terrain);
            assertTrue(encoded.hasEncodedCollisionLayout());
            var services = EngineServices.current();
            var level = GameplaySessionFactory.createCollisionOnlyLevel(module, terrain, 0, 0,
                    services, new Camera(services.configuration()), new SpriteManager(services.configuration()),
                    new CollisionSystem(new TerrainCollisionManager()), new WaterSystem(), new GameStateManager());
            var sprite = mock(AbstractPlayableSprite.class);
            when(sprite.getGameRules()).thenReturn(module.getRules());
            var sensor = new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true);
            var scan = GroundSensor.class.getDeclaredMethod("scanVerticalLayer", LevelManager.class,
                    short.class, short.class, int.class, Direction.class, boolean.class,
                    byte.class, boolean.class, SensorResult.class);
            scan.setAccessible(true);
            assertNotNull(scan.invoke(sensor, level, (short) 7, (short) 8, 12,
                    Direction.DOWN, false, (byte) 0, false, new SensorResult()));
            org.mockito.Mockito.verify(encoded, atLeastOnce()).collisionChunkDescAt((byte) 0, 7, 8);
        }
    }

    private void verify(String game, String property, String expected) throws Exception {
        try (var library = SceneRomLibrary.open(Map.of(game, Path.of(System.getProperty(property))))) {
            var module = library.module(game);
            var terrain = library.level(game, 0, 0);
            // The pre-extraction golden corpus used decoded maps. Keep those inputs
            // fixed while the separate ordinary-scan control retains encoded dispatch.
            var decodedEntry = new DelegatingLevel(terrain);
            assertFalse(decodedEntry instanceof EncodedCollisionLayout);
            assertSame(terrain.getMap(), decodedEntry.getMap());
            var services = EngineServices.current();
            var level = GameplaySessionFactory.createCollisionOnlyLevel(module, decodedEntry, 0, 0,
                    services, new Camera(services.configuration()), new SpriteManager(services.configuration()),
                    new CollisionSystem(new TerrainCollisionManager()), new WaterSystem(), new GameStateManager());
            var sprite = mock(AbstractPlayableSprite.class);
            when(sprite.getGameRules()).thenReturn(module.getRules());
            var sensor = new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true);
            var scan = GroundSensor.class.getDeclaredMethod("scanVerticalLayer", LevelManager.class,
                    short.class, short.class, int.class, Direction.class, boolean.class,
                    byte.class, boolean.class, SensorResult.class);
            scan.setAccessible(true);
            var digest = MessageDigest.getInstance("SHA-256");
            var target = new SensorResult();
            int count = 0, empty = 0;
            // Deterministic irregular columns/rows, all nibble offsets and negative ceiling edges.
            for (int column = 0; column < 80; column++) {
                short x = (short) ((column * 193 + 7) % Math.max(1, terrain.getMaxX()));
                for (int row = -2; row < 128; row++) {
                    short y = (short) (row * 17 - 9);
                    for (int bit = 12; bit <= 15; bit++)
                        for (var direction : new Direction[]{Direction.DOWN, Direction.UP})
                            for (byte layer = 0; layer <= 1; layer++)
                                for (boolean low : new boolean[]{false, true}) {
                                    var result = (SensorResult) scan.invoke(sensor, level, x, y, bit,
                                            direction, direction == Direction.UP && (row & 1) == 0,
                                            layer, low, target);
                                    assertNotNull(result);
                                    int[] fields = {result.distance(), result.angle(), result.tileId(), result.direction().ordinal(),
                                            result.foregroundAngleWritten() ? 1 : 0, result.foregroundAngle(),
                                            result.backgroundScanExecuted() ? 1 : 0, result.backgroundAngleWritten() ? 1 : 0,
                                            result.backgroundAngle(), result.restoreForegroundAngleState() ? 1 : 0};
                                    for (int value : fields) {
                                        digest.update((byte) (value >>> 24)); digest.update((byte) (value >>> 16));
                                        digest.update((byte) (value >>> 8)); digest.update((byte) value);
                                    }
                                    count++; if (result.tileId() == 0) empty++;
                                }
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            assertTrue(empty > 0, "Must include native empty defaults");
            assertEquals(expected, actual, game + " samples=" + count + " empty=" + empty);
        }
    }
}
