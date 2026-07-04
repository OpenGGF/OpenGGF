package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameStateManager;
import com.openggf.game.RespawnState;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression for the time-attack star-post softlock companion bug: unlike the
 * S1/S3K giant-ring/entry-ring gates (which stop a state change that would
 * otherwise strand the player), a S2 checkpoint touch during a time attack
 * must still record the checkpoint (respawn + split tracking depend on it)
 * and still run its SFX/dongle — only the special-stage star circle, which
 * would request an entry that GameLoop's chokepoint silently swallows, must
 * not spawn at all. See {@link CheckpointObjectInstance#shouldSpawnStars}.
 */
class TestCheckpointObjectInstanceTimeAttackGate {

    private static final ObjectSpawn CHECKPOINT_SPAWN =
            new ObjectSpawn(0x0140, 0x0180, Sonic2ObjectIds.CHECKPOINT, 0, 0, false, 0);

    private GameStateManager gameState;
    private RecordingCheckpointState checkpointState;
    private ObjectManager objectManager;

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
        gameState = new GameStateManager();
        gameState.resetSession();
        checkpointState = new RecordingCheckpointState();

        Camera camera = mockCameraAtOrigin();
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GameStateManager gameState() { return gameState; }
            @Override public RespawnState checkpointState() { return checkpointState; }
        };
        objectManager = new ObjectManager(
                List.of(),
                new Sonic2ObjectRegistry(),
                0, null, null,
                GraphicsManager.getInstance(),
                camera,
                services);
        holder[0] = objectManager;
        objectManager.reset(0);
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void starCircleSuppressedButCheckpointStillRecordedDuringTimeAttack() {
        gameState.setTimeAttackActive(true);

        CheckpointObjectInstance checkpoint = new CheckpointObjectInstance(CHECKPOINT_SPAWN, "Checkpoint");
        checkpoint.setServices(newServices());
        AbstractPlayableSprite player = mockPlayerAt(CHECKPOINT_SPAWN.x(), CHECKPOINT_SPAWN.y(), 50);

        checkpoint.update(1, player);

        assertTrue(checkpointState.saveCalled,
                "checkpoint must still be recorded (respawn + time-attack split tracking depend on it)");
        assertEquals(checkpoint.getCheckpointIndex(), checkpointState.recordedIndex,
                "recorded checkpoint index must match the touched checkpoint");
        assertEquals(0, liveObjects(CheckpointStarInstance.class).size(),
                "star circle must not spawn at all while a time attack is active");
        assertEquals(1, liveObjects(CheckpointDongleInstance.class).size(),
                "dongle animation must be unaffected by the star gate");
    }

    @Test
    void starCircleSpawnsNormallyWhenTimeAttackInactive() {
        CheckpointObjectInstance checkpoint = new CheckpointObjectInstance(CHECKPOINT_SPAWN, "Checkpoint");
        checkpoint.setServices(newServices());
        AbstractPlayableSprite player = mockPlayerAt(CHECKPOINT_SPAWN.x(), CHECKPOINT_SPAWN.y(), 50);

        checkpoint.update(1, player);

        assertTrue(checkpointState.saveCalled, "precondition: checkpoint recording must still occur");
        assertEquals(4, liveObjects(CheckpointStarInstance.class).size(),
                "precondition: normal touch (no time attack) still spawns the 4-star circle");
    }

    private ObjectServices newServices() {
        return new StubObjectServices() {
            @Override public ObjectManager objectManager() { return objectManager; }
            @Override public GameStateManager gameState() { return gameState; }
            @Override public RespawnState checkpointState() { return checkpointState; }
        };
    }

    private <T extends ObjectInstance> List<T> liveObjects(Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .toList();
    }

    private AbstractPlayableSprite mockPlayerAt(int x, int y, int ringCount) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) x);
        when(player.getCentreY()).thenReturn((short) y);
        when(player.getRingCount()).thenReturn(ringCount);
        return player;
    }

    private static Camera mockCameraAtOrigin() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    private static final class RecordingCheckpointState extends CheckpointState {
        private boolean saveCalled;
        private int recordedIndex = -1;

        @Override
        public void saveCheckpoint(int checkpointIndex, int x, int y, boolean cameraLockFlag) {
            // Skip the real implementation's GameServices.camera() dependency — this
            // test has no active gameplay session. Track only what the star gate
            // under test needs to observe: that saving still happens, unaffected.
            this.saveCalled = true;
            this.recordedIndex = checkpointIndex;
        }

        @Override
        public int getLastCheckpointIndex() {
            return recordedIndex;
        }
    }
}
