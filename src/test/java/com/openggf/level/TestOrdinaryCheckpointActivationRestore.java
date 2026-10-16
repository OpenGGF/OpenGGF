package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production load-boundary and stock-post component checks; not a physical death/bonus route. */
@Isolated
class TestOrdinaryCheckpointActivationRestore {
    private static final int X = 0x300, Y = 0x180, INDEX = 5;

    @BeforeEach void setup() { TestEnvironment.resetAll(); GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void cleanup() { SessionManager.clear(); GraphicsManager.getInstance().resetState(); }

    @Test void ordinaryRestoreSuppressesTheStockPostBeforeItsFirstUpdateCanSaveAgain() {
        var f = new Fixture();
        f.state.saveCheckpoint(INDEX, X, Y, false);
        var context = new LevelLoadContext();
        context.snapshotCheckpoint(f.state);
        f.coordinator.prepareForLevelStart();
        assertEquals(-1, f.state.getStarPostActivationMark());
        f.coordinator.restoreCheckpointState(context);
        int restoredMarkBeforeAnyPostUpdate = f.state.getStarPostActivationMark();
        int savesBeforeUpdate = f.state.saves;
        f.touchFreshStockPost();
        assertAll(
                () -> assertEquals(INDEX, f.state.getLastCheckpointIndex()),
                () -> assertEquals(INDEX, restoredMarkBeforeAnyPostUpdate,
                        "ordinary saved checkpoint restores its native activation high-water before post initialization"),
                () -> assertEquals(savesBeforeUpdate, f.state.saves, "restored stock post must not save again"),
                () -> assertTrue(f.sounds.isEmpty(), "restored stock post must not play activation SFX"),
                () -> assertFalse(f.state.isUsedForSpecialStage()));
    }

    @Test void actualBonusCoordinatorRestoresSeparateHighWaterWithNativeZeroIndex() {
        var f = new Fixture();
        f.state.saveCheckpoint(INDEX, X, Y, false);
        var bonusLevel = mock(LevelManager.class);
        when(bonusLevel.getCheckpointState()).thenReturn(f.state);
        var bonus = new BonusStageTransitionCoordinator();
        var captured = bonus.captureEntry(bonusLevel, new Camera(), null, null, null, 0);
        assertEquals(0, captured.savedState().savedLastStarPostHit());
        assertEquals(INDEX, captured.pendingStarPostActivationMark());
        bonus.prepareReturnLoad(bonusLevel, captured.savedState());
        bonus.restoreReturnState(bonusLevel, new Camera(), null, null, null,
                captured.savedState(), captured.pendingStarPostActivationMark(), 0, null, null, null);
        assertEquals(0, f.state.getLastCheckpointIndex());
        assertEquals(INDEX, f.state.getStarPostActivationMark());
        assertFalse(f.state.isUsedForSpecialStage());
        int savesBeforeUpdate = f.state.saves;
        f.touchFreshStockPost();
        assertEquals(savesBeforeUpdate, f.state.saves);
        assertTrue(f.sounds.isEmpty());
        assertEquals(0, f.state.getLastCheckpointIndex(), "the post must not turn bonus return into ordinary checkpoint save");
    }

    private static final class Fixture {
        final RecordingState state = new RecordingState();
        final List<Integer> sounds = new ArrayList<>();
        final LevelCheckpointCoordinator coordinator;
        Fixture() {
            LevelManager level = TestEnvironment.activeGameplayMode().getLevelManager();
            level.gameModule = new Sonic3kGameModule() {
                @Override public RespawnState createRespawnState() { return state; }
            };
            coordinator = new LevelCheckpointCoordinator(level);
            coordinator.prepareForLevelStart();
        }
        void touchFreshStockPost() {
            var post = new Sonic3kStarPostObjectInstance(new ObjectSpawn(X, Y,
                    Sonic3kObjectIds.STAR_POST, INDEX, 0, false, 0));
            post.setServices(new StubObjectServices() {
                @Override public RespawnState checkpointState() { return state; }
                @Override public GameStateManager gameState() { return new GameStateManager(); }
                @Override public void playSfx(int id) { sounds.add(id); }
            });
            var player = mock(AbstractPlayableSprite.class);
            when(player.getCentreX()).thenReturn((short) X);
            when(player.getCentreY()).thenReturn((short) Y);
            when(player.getRingCount()).thenReturn(0);
            post.update(0, player);
        }
    }

    private static final class RecordingState extends CheckpointState {
        int saves;
        @Override public void saveCheckpoint(int index, int x, int y, boolean lock) {
            saves++;
            super.saveCheckpoint(index, x, y, lock);
        }
    }
}
