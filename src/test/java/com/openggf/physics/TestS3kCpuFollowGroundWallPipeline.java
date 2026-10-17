package com.openggf.physics;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.session.SessionManager;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Independent geometry, not a trace seed: couples loc_13E34 to sub_F61C/loc_14BCA. */
class TestS3kCpuFollowGroundWallPipeline {
    private static final int WALL_X = 0x400;
    private static final int FLOOR_Y = 0x300;

    @AfterEach
    void cleanup() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
    }

    @ParameterizedTest(name = "follow delta {0}: CPU -> acceleration -> wall -> integration")
    @ValueSource(ints = {1, 0})
    void immediateFollowNudgeChangesProjectedWallResponseBeforeIntegration(int followDelta) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var mode = TestEnvironment.activeGameplayMode();
        var terrain = mode.getTerrainCollisionManager();
        var collision = new ObservedCollision(terrain);
        var level = declaredTerrain(collision);
        mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(), terrain,
                collision, mode.getSpriteManager(), level);
        GroundSensor.setLevelManager(level);

        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var tails = new Tails("tails", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        var cpu = new SidekickCpuController(tails, sonic);
        cpu.captureLevelStartLeaderAnchor(WALL_X + 0x80, FLOOR_Y - 20);
        cpu.update(0); // Real routine-0 dispatch; no forced NORMAL state or follow result.
        assertEquals(SidekickCpuController.State.NORMAL, cpu.getState());
        collision.observed = tails;

        // The declared full floor starts at FLOOR_Y. FindFloor's 15-height-y
        // formula puts a grounded centre one pixel above its radius (Tails 15).
        assertEquals(9, tails.getXRadius());
        assertEquals(15, tails.getYRadius());
        int startX = WALL_X - 13;
        int centreY = FLOOR_Y - tails.getYRadius() - 1;
        tails.setCentreX((short) startX);
        tails.setCentreY((short) centreY);
        tails.setSubpixelRaw(0xC000, 0);
        tails.setGroundMode(GroundMode.GROUND);
        tails.setAngle((byte) 0);
        tails.setDirection(Direction.RIGHT);
        tails.setAir(false);
        tails.setInWater(true);
        tails.setGSpeed((short) 0x1F0);
        tails.setXSpeed((short) 0x1F0);
        tails.setYSpeed((short) 0);
        assertEquals(6, tails.getRunAccel(), "Tails_Water halves the dry $C acceleration");
        var floor = new GroundSensor(tails, Direction.DOWN, (byte) 0, (byte) 15, true);
        assertEquals(0, floor.scan().distance(), "Declared full floor supports Tails before the coupled tick");

        // Populate ordinary Sonic_RecordPos history through its production writer.
        // loc_13DA6 subtracts $20 for a slow, unsupported leader. The delayed
        // target is therefore startX+delta; both cases replay held Right. Delta
        // zero is the loc_13E50 control, which does not perform the +1 nudge.
        sonic.setCentreX((short) (startX + 0x20 + followDelta));
        sonic.setCentreY((short) centreY);
        sonic.setDirection(Direction.RIGHT);
        sonic.setLogicalInputState(false, false, false, true, false);
        for (int i = 0; i < 64; i++) {
            sonic.clearFollowerHistoryRecordedFlag();
            sonic.recordFollowerHistoryForTick();
        }
        sonic.clearFollowerHistoryRecordedFlag();
        sonic.setCentreX((short) (WALL_X + 0x80));
        sonic.setCentreY((short) (FLOOR_Y - sonic.getYRadius() - 1));
        var sprites = mode.getSpriteManager();
        sprites.addSprite(sonic);
        sprites.addSprite(tails);
        var camera = GameServices.camera();
        camera.setMinX((short) 0);
        camera.setMaxX((short) 0x1000);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 0x1000);
        camera.setFocusedSprite(sonic);
        camera.setX((short) (WALL_X - 0x80));
        camera.setY((short) (FLOOR_Y - 0x80));

        sprites.update(new InputHandler()); // Includes CPU; updateWithoutInput deliberately does not.

        var step = cpu.getLatestNormalStepDiagnostics();
        assertNotNull(step);
        assertAll(
                () -> assertEquals(followDelta, step.dx()),
                () -> assertEquals(followDelta, step.appliedFollowNudge()),
                () -> assertEquals(startX + followDelta, step.postCpuX()),
                () -> assertEquals(0xC000, step.postCpuXSubpixel() & 0xFFFF),
                () -> assertTrue(cpu.getInputRight()),
                () -> assertEquals(1, collision.visits),
                () -> assertEquals(startX + followDelta, collision.beforeX),
                () -> assertEquals(0xC000, collision.beforeSubpixel),
                () -> assertEquals(0x1F6, collision.beforeVx),
                () -> assertEquals(0x1F6, collision.beforeGround),
                // sub_F61C predicts the full 16.16 position, then adds $A/$8.
                () -> assertEquals(WALL_X - 1 + followDelta, collision.probeX),
                () -> assertEquals(centreY + 8, collision.probeY),
                // At W-1 the empty tile extends to a flush distance 0; at W
                // FindWall's full-tile/previous-empty branch returns -1.
                () -> assertEquals(followDelta == 1 ? 0xF6 : 0x1F6, collision.afterVx),
                () -> assertEquals(followDelta == 1 ? 0 : 0x1F6, collision.afterGround),
                () -> assertEquals(followDelta == 1, collision.afterPush),
                () -> assertEquals(WALL_X - 11, tails.getCentreX()),
                () -> assertEquals(0xB600, tails.getXSubpixelRaw()),
                () -> assertEquals(collision.afterVx, tails.getXSpeed()),
                () -> assertEquals(collision.afterGround, tails.getGSpeed()),
                () -> assertEquals(collision.afterPush, tails.getPushing()),
                () -> assertFalse(tails.getAir()));
    }

    private static LevelManager declaredTerrain(ObservedCollision collision) {
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(mock(Level.class));
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        var solid = mock(ChunkDesc.class);
        when(solid.isSolidityBitSet(anyInt())).thenReturn(true);
        when(solid.getChunkIndex()).thenReturn(1);
        byte[] full = new byte[16];
        Arrays.fill(full, (byte) 16);
        var tile = new SolidTile(1, full, full, (byte) 0);
        when(level.getSolidTileForChunkDesc(eq(solid), anyInt())).thenReturn(tile);
        // Tile-aligned full floor and a 16-wide, 32-high vertical wall, with
        // empty space on either side above the floor. Sensors do all distances.
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
            int x = call.getArgument(1), y = call.getArgument(2);
            if (collision.inWallScan && collision.probeX == Integer.MIN_VALUE) {
                collision.probeX = x;
                collision.probeY = y;
            }
            return (byte) call.getArgument(0) == 0 && (y >= FLOOR_Y
                    || (x >= WALL_X && x < WALL_X + 16 && y >= FLOOR_Y - 32)) ? solid : null;
        });
        return level;
    }

    /** Passive seam: the production wall response and GroundSensor still execute. */
    private static final class ObservedCollision extends CollisionSystem {
        private AbstractPlayableSprite observed;
        private boolean inWallScan, afterPush;
        private int visits, beforeX, beforeSubpixel, beforeVx, beforeGround, afterVx, afterGround;
        private int probeX = Integer.MIN_VALUE, probeY;

        private ObservedCollision(TerrainCollisionManager terrain) {
            super(terrain);
        }

        @Override
        public void resolveGroundWallCollision(FrameCollisionPlan plan, AbstractPlayableSprite sprite) {
            if (sprite != observed) {
                super.resolveGroundWallCollision(plan, sprite);
                return;
            }
            visits++;
            beforeX = sprite.getCentreX();
            beforeSubpixel = sprite.getXSubpixelRaw();
            beforeVx = sprite.getXSpeed();
            beforeGround = sprite.getGSpeed();
            inWallScan = true;
            try {
                super.resolveGroundWallCollision(plan, sprite);
            } finally {
                inWallScan = false;
            }
            afterVx = sprite.getXSpeed();
            afterGround = sprite.getGSpeed();
            afterPush = sprite.getPushing();
        }
    }
}
