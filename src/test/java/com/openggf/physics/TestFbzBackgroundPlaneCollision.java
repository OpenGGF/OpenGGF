package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestFbzBackgroundPlaneCollision {
    protected LevelManager level;
    protected AbstractPlayableSprite sprite;
    private ChunkDesc bgTile;
    private SolidTile solidTile;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        level = mock(LevelManager.class);
        bgTile = mock(ChunkDesc.class);
        when(bgTile.isSolidityBitSet(anyInt())).thenReturn(true);
        when(bgTile.getChunkIndex()).thenReturn(7);
        byte[] full = new byte[16];
        java.util.Arrays.fill(full, (byte) 16);
        solidTile = new SolidTile(7, full, full, (byte) 0);
        when(level.getSolidTileForChunkDesc(bgTile, 0x0C)).thenReturn(solidTile);
        when(level.getSolidTileForChunkDesc(bgTile, 0x0D)).thenReturn(solidTile);
        when(level.getSolidTileForChunkDesc(bgTile, 0x0C, false)).thenReturn(solidTile);
        when(level.getSolidTileForChunkDesc(bgTile, 0x0D, false)).thenReturn(solidTile);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(invocation -> {
            byte layer = invocation.getArgument(0);
            int x = invocation.getArgument(1);
            int y = invocation.getArgument(2);
            return layer == 1 && x >= 0xE0 && x < 0x100 && y >= 0x100 && y < 0x120 ? bgTile : null;
        });
        GroundSensor.setLevelManager(level);
        sprite = new TestSprite();
        sprite.setGroundMode(GroundMode.GROUND);
        sprite.setWidth(0);
        sprite.setHeight(0);
        sprite.setCentreX((short) 0x100);
        sprite.setCentreY((short) 0x100);
        GameServices.zoneRuntimeRegistry().install(new ExplicitState());
    }

    @AfterEach
    void tearDown() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
    }

    @Test
    void scanWorldUsesExplicitBackgroundPlaneForFloorCeilingAndWall() {
        GroundSensor sensor = new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true);

        SensorResult floor = sensor.scanWorld(Direction.DOWN, (short) 0, (short) 0,
                (short) 0, (short) 0, sprite.getTopSolidBit());
        SensorResult ceiling = sensor.scanWorld(Direction.UP, (short) 0, (short) 0,
                (short) 0, (short) 0, sprite.getLrbSolidBit());
        SensorResult wall = sensor.scanWorld(Direction.RIGHT, (short) 0, (short) 0,
                (short) 0, (short) 0, sprite.getLrbSolidBit());

        assertNotNull(floor);
        assertNotNull(ceiling);
        assertNotNull(wall);
        assertEquals(7, floor.tileId());
        assertEquals(7, ceiling.tileId());
        assertEquals(7, wall.tileId());
    }

    @Test
    void ordinaryGroundSensorsUseBackgroundPlaneForFloorCeilingAndWall() {
        SensorResult floor = new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true).scan();
        SensorResult ceiling = new GroundSensor(sprite, Direction.UP, (byte) 0, (byte) 0, true).scan();
        SensorResult wall = new GroundSensor(sprite, Direction.RIGHT, (byte) 0, (byte) 0, true).scan();

        assertNotNull(floor);
        assertNotNull(ceiling);
        assertNotNull(wall);
        assertEquals(7, floor.tileId());
        assertEquals(7, ceiling.tileId());
        assertEquals(7, wall.tileId());
    }

    @Test
    void ordinarySensorResolvesCollisionStateOnce() {
        class CountingProvider implements BackgroundPlaneCollisionProvider {
            int calls;
            private final State state = new State(true, 0x20, 0);
            @Override public State state() { calls++; return state; }
            @Override public State state(LevelManager ignored) { calls++; return state; }
        }
        CountingProvider provider = new CountingProvider();
        TestEnvironment.activeGameplayMode().attachBackgroundPlaneCollisionProvider(provider);

        new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true).scan();

        assertEquals(1, provider.calls);
    }

    @Test
    void signedNonAlignedWallDiffsUseOrientationAwareProductionTranslation() {
        GameServices.zoneRuntimeRegistry().install(new ZoneRuntimeState() {
            @Override public String gameId() { return "s3k"; }
            @Override public int zoneIndex() { return 4; }
            @Override public int actIndex() { return 1; }
            @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
                return new BackgroundPlaneCollisionProvider.State(true, 3, 0);
            }
        });
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(invocation -> {
            byte layer = invocation.getArgument(0);
            int x = invocation.getArgument(1);
            return layer == 1 && x >= 0x100 && x < 0x110 ? bgTile : null;
        });
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt())).thenAnswer(invocation -> {
            byte layer = invocation.getArgument(0);
            int x = invocation.getArgument(1);
            return layer == 1 && x >= 0x100 && x < 0x110 ? bgTile : null;
        });
        var mode = TestEnvironment.activeGameplayMode();
        mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(),
                mode.getTerrainCollisionManager(), mode.getCollisionSystem(), mode.getSpriteManager(), level);
        mode.attachBackgroundPlaneCollisionProvider(mode.createDefaultBackgroundPlaneCollisionProvider());

        SensorResult sensorResult = new GroundSensor(sprite, Direction.LEFT, (byte) 0, (byte) 0, true)
                .scanWorld(Direction.LEFT, (short) 0, (short) 0,
                        (short) 0, (short) 0, sprite.getLrbSolidBit());
        TerrainCheckResult objectResult = ObjectTerrainUtils.checkLeftWallDist(
                level, mode.getBackgroundPlaneCollisionProvider(), false, 0x100, 0x100);

        assertNotNull(sensorResult);
        assertEquals(7, sensorResult.tileId());
        assertTrue(objectResult.foundSurface());
        assertEquals(7, objectResult.tileIndex());

        GameServices.zoneRuntimeRegistry().install(new ZoneRuntimeState() {
            @Override public String gameId() { return "s3k"; }
            @Override public int zoneIndex() { return 4; }
            @Override public int actIndex() { return 1; }
            @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
                return new BackgroundPlaneCollisionProvider.State(true, -3, 0);
            }
        });
        SensorResult rightSensor = new GroundSensor(sprite, Direction.RIGHT, (byte) 0, (byte) 0, true)
                .scanWorld(Direction.RIGHT, (short) 0, (short) 0,
                        (short) 0, (short) 0, sprite.getLrbSolidBit());
        TerrainCheckResult rightObject = ObjectTerrainUtils.checkRightWallDist(
                level, mode.getBackgroundPlaneCollisionProvider(), false, 0x100, 0x100);

        assertNotNull(rightSensor);
        assertEquals(7, rightSensor.tileId());
        assertTrue(rightObject.foundSurface());
        assertEquals(7, rightObject.tileIndex());
    }

    private static final class TestSprite extends AbstractPlayableSprite {
        private TestSprite() { super("sonic", (short) 0, (short) 0); }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() { }
        @Override public void draw() { }
    }

    private static final class ExplicitState implements ZoneRuntimeState {
        @Override public String gameId() { return "s3k"; }
        @Override public int zoneIndex() { return 4; }
        @Override public int actIndex() { return 1; }
        @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
            return new BackgroundPlaneCollisionProvider.State(true, 0x20, 0);
        }
    }
}
