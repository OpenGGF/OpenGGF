package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
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
        TestEnvironment.resetAll();
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void groundedWallTailUsesSemanticCollisionActivityRatherThanLegacyFlag(boolean active) {
        GameServices.gameState().setBackgroundCollisionFlag(!active);
        TestEnvironment.activeGameplayMode().attachBackgroundPlaneCollisionProvider(
                () -> new BackgroundPlaneCollisionProvider.State(active, 0x20, 0));
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(invocation -> {
            int layer = (byte) invocation.getArgument(0);
            int x = invocation.getArgument(1);
            return layer == 1 && x >= 0x110 && x < 0x120 ? bgTile : null;
        });
        sprite.setCentreX((short) 0x128);
        sprite.setXSpeed((short) 0x123);
        sprite.setGSpeed((short) 0x234);
        var collision = new CollisionSystem(new TerrainCollisionManager());

        collision.resolvePostMovementBackgroundWallClamp(FrameCollisionPlan.terrainOnly(), sprite);

        assertEquals(active ? 0x125 : 0x128, sprite.getCentreX(),
                "CheckRightWallDist at BG $112 returns -3; the native tail changes x_pos only");
        assertEquals(0x123, sprite.getXSpeed());
        assertEquals(0x234, sprite.getGSpeed());
        if (!active) {
            org.mockito.Mockito.verify(level, org.mockito.Mockito.never())
                    .getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void groundedFatalFloorTailUsesTheSameSemanticCollisionActivity(boolean active) {
        GameServices.gameState().setBackgroundCollisionFlag(!active);
        TestEnvironment.activeGameplayMode().attachBackgroundPlaneCollisionProvider(
                () -> new BackgroundPlaneCollisionProvider.State(active, 0x20, 0));
        sprite.setCentreY((short) 0x108);
        var collision = new CollisionSystem(new TerrainCollisionManager());

        assertEquals(active, collision.hasFatalPostMovementBackgroundFloorOverlap(
                FrameCollisionPlan.terrainOnly(), sprite),
                "sub_F846 tests the active background floor before either wall correction");
        assertEquals(0x108, sprite.getCentreY(), "the overlap query performs no position write");
        if (!active) {
            org.mockito.Mockito.verify(level, org.mockito.Mockito.never())
                    .getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean());
        }
    }

    @Test
    void defaultMaskPreservesLegacyObjectProbeCoordinates() {
        var collision = new BackgroundPlaneCollisionProvider.State(true, 0, 0x200);
        GameServices.zoneRuntimeRegistry().install(new ZoneRuntimeState() {
            @Override public String gameId() { return "test"; }
            @Override public int zoneIndex() { return 0; }
            @Override public int actIndex() { return 0; }
            @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
                return collision;
            }
        });
        var layout = mock(com.openggf.level.Level.class);
        when(level.getCurrentLevel()).thenReturn(layout);
        when(layout.hasBackgroundCollisionRowAt(anyInt())).thenReturn(true);
        BackgroundPlaneCollisionProvider legacy = () -> collision;
        for (int y : new int[]{-0x100, 0, 0x100, 0x8000}) {
            var expected = ObjectTerrainUtils.checkFloorDist(level, legacy, false, 0x100, y);
            var actual = ObjectTerrainUtils.checkFloorDist(level, GameServices.backgroundPlaneCollision(), false, 0x100, y);
            assertEquals(expected.distance(), actual.distance());
            assertEquals(expected.foundSurface(), actual.foundSurface());
        }
        org.mockito.Mockito.verify(level, org.mockito.Mockito.atLeastOnce())
                .getChunkDescAt((byte) 1, 0x100, -0x100);
    }

    @Test
    void maskedBackgroundRowsReachPlayerAndObjectProbesBeforeAbsentRowRejection() {
        GameServices.zoneRuntimeRegistry().install(new ZoneRuntimeState() {
            @Override public String gameId() { return "test"; }
            @Override public int zoneIndex() { return 0; }
            @Override public int actIndex() { return 0; }
            @Override public int backgroundCollisionYMask() { return 0x7FF; }
            @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
                return new BackgroundPlaneCollisionProvider.State(true,0x20,-0x800);
            }
        });
        var layout=mock(com.openggf.level.Level.class);
        when(level.getCurrentLevel()).thenReturn(layout);
        when(layout.hasBackgroundCollisionRowAt(anyInt())).thenAnswer(call -> (int)call.getArgument(0)<0x800);
        when(level.getChunkDescAt(anyByte(),anyInt(),anyInt())).thenAnswer(call -> {
            int layer=(byte)call.getArgument(0),x=call.getArgument(1),y=call.getArgument(2);
            return layer==1&&x>=0xE0&&x<0x100&&y>=0x100&&y<0x120?bgTile:null;
        });
        for(var direction:new Direction[]{Direction.DOWN,Direction.UP,Direction.RIGHT}) {
            var result=new GroundSensor(sprite,direction,(byte)0,(byte)0,true).scanWorld(
                    direction,(short)0,(short)0,(short)0,(short)0,
                    direction==Direction.DOWN?sprite.getTopSolidBit():sprite.getLrbSolidBit());
            assertNotNull(result);assertEquals(7,result.tileId());
        }
        var provider=GameServices.backgroundPlaneCollision();
        assertTrue(ObjectTerrainUtils.checkFloorDist(level,provider,false,0x100,0x100).foundSurface());
        assertTrue(ObjectTerrainUtils.checkRightWallDist(level,provider,false,0x100,0x100).foundSurface());
        org.mockito.Mockito.verify(layout,org.mockito.Mockito.atLeastOnce()).hasBackgroundCollisionRowAt(0x100);
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
    void signedNonAlignedWallDiffsTranslateWorldPointsBeforeTheWallScan() {
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

        // Native LEFT callers mirror inside FindWall; the provider receives
        // the physical world point $110 and translates it to BG $10D.
        sprite.setCentreX((short) 0x110);
        SensorResult sensorResult = new GroundSensor(sprite, Direction.LEFT, (byte) 0, (byte) 0, true)
                .scanWorld(Direction.LEFT, (short) 0, (short) 0,
                        (short) 0, (short) 0, sprite.getLrbSolidBit());
        TerrainCheckResult objectResult = ObjectTerrainUtils.checkLeftWallDist(
                level, mode.getBackgroundPlaneCollisionProvider(), false, 0x110, 0x100);

        assertNotNull(sensorResult);
        assertEquals(7, sensorResult.tileId());
        assertEquals(-3, sensorResult.distance(), "BG $10D is three pixels inside the wall's right edge");
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
        sprite.setCentreX((short) 0x100);
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
