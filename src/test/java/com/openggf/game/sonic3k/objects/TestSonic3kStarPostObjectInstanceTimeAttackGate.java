package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameStateManager;
import com.openggf.game.RespawnState;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
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
 * S3K entry-ring gate (which stops a state change that would otherwise strand
 * the player), a S3K star-post touch during a time attack must still record
 * the checkpoint (respawn + split tracking depend on it), still play its SFX,
 * and still spawn the cosmetic orbiting star that decorates the post itself
 * (no player collision, never requests any stage entry) — only the 4 bonus
 * stars, which would request a bonus-stage entry that GameLoop's chokepoint
 * silently swallows, must not spawn at all.
 * See {@link Sonic3kStarPostObjectInstance#shouldSpawnBonusStars}.
 */
class TestSonic3kStarPostObjectInstanceTimeAttackGate {

    private static final ObjectSpawn STAR_POST_SPAWN =
            new ObjectSpawn(0x0140, 0x0180, Sonic3kObjectIds.STAR_POST, 0, 0, false, 0);
    private static final int BONUS_STAR_RING_THRESHOLD = 20;

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
                new Sonic3kObjectRegistry(),
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
    void bonusStarsSuppressedButCheckpointStillRecordedDuringTimeAttack() {
        gameState.setTimeAttackActive(true);

        Sonic3kStarPostObjectInstance starPost = new Sonic3kStarPostObjectInstance(STAR_POST_SPAWN);
        starPost.setServices(newServices());
        AbstractPlayableSprite player =
                mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), BONUS_STAR_RING_THRESHOLD);

        starPost.update(1, player);

        assertTrue(checkpointState.saveCalled,
                "star post must still record the checkpoint (respawn + time-attack split tracking depend on it)");
        assertEquals(starPost.getCheckpointIndex(), checkpointState.recordedIndex,
                "recorded checkpoint index must match the touched star post");
        assertEquals(0, liveObjects(Sonic3kStarPostBonusStarChild.class).size(),
                "bonus stars must not spawn at all while a time attack is active");
        assertEquals(1, liveObjects(Sonic3kStarPostStarChild.class).size(),
                "cosmetic orbiting post star must be unaffected by the bonus-star gate");
    }

    @Test
    void bonusStarsSpawnNormallyWhenTimeAttackInactive() {
        Sonic3kStarPostObjectInstance starPost = new Sonic3kStarPostObjectInstance(STAR_POST_SPAWN);
        starPost.setServices(newServices());
        AbstractPlayableSprite player =
                mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), BONUS_STAR_RING_THRESHOLD);

        starPost.update(1, player);

        assertTrue(checkpointState.saveCalled, "precondition: checkpoint recording must still occur");
        assertEquals(4, liveObjects(Sonic3kStarPostBonusStarChild.class).size(),
                "precondition: normal touch (no time attack) still spawns the 4 bonus stars");
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
            // test has no active gameplay session. Track only what the bonus-star
            // gate under test needs to observe: that saving still happens, unaffected.
            this.saveCalled = true;
            this.recordedIndex = checkpointIndex;
        }

        @Override
        public int getLastCheckpointIndex() {
            return recordedIndex;
        }
    }
}
