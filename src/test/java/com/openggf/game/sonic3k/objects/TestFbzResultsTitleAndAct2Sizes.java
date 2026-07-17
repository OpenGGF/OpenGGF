package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.camera.Camera;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestFbzResultsTitleAndAct2Sizes {
    @Test
    void fbzResultsExitCarriesOwnerIntoTitleWaitWithoutEarlyMusic() throws Exception {
        RecordingServices services = new RecordingServices();
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                services,
                () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_AND_TAILS, 0));
        results.setServices(services);
        services.camera.setMinX((short) 0x20);
        services.camera.setMaxX((short) 0xA0);
        services.camera.setMinY((short) 0x540);
        services.camera.setMaxY((short) 0x540);
        services.camera.setMaxYTarget((short) 0x560);

        Method onExitReady = S3kResultsScreenObjectInstance.class.getDeclaredMethod("onExitReady");
        onExitReady.setAccessible(true);
        onExitReady.invoke(results);

        assertTrue(services.titleCard.started,
                "loc_2DD06 converts the surviving results owner into the in-level title card");
        assertTrue(services.playedMusic.isEmpty(),
                "Obj_TitleCardWait, not the earlier results exit, restores FBZ2 music");
        assertFalse(results.isDestroyed(),
                "the live results SST slot survives and owns the carried title-card wait");
        assertTrue(results.carriedTitlePhase()
                        == S3kResultsScreenObjectInstance.CarriedTitlePhase.TITLE_CARD_WAIT,
                "the surviving SST owner must change routine identity, not remain results logic");
        assertEquals(0x20, services.camera.getMinX() & 0xFFFF);
        assertEquals(0xA0, services.camera.getMaxX() & 0xFFFF);
        assertEquals(0x540, services.camera.getMinY() & 0xFFFF);
        assertEquals(0x540, services.camera.getMaxY() & 0xFFFF,
                "carried FBZ results must not restore full Act 2 camera bounds");

        results.update(0, null);
        assertTrue(results.carriedTitlePhase()
                        == S3kResultsScreenObjectInstance.CarriedTitlePhase.TITLE_CARD_WAIT2);
        verify(services.levelManager).resetLevelGamestate(services.freshLevelState);
        assertEquals(List.of(0x0A), services.playedMusic,
                "Obj_TitleCardWait restores Act 2 music at the same dispatch as Timer/Ring_count reset");

        services.titleCard.complete = true;
        for (int i = 0; i < 90; i++) {
            results.update(i + 1, null);
        }
        verify(services.gameState, never()).setEndOfLevelFlag(true);
        assertFalse(results.isDestroyed(),
                "the 90th positive timer decrement must return before polling already-gone children");

        results.update(91, null);
        verify(services.gameState).setEndOfLevelFlag(true);
        verify(services.levelManager, times(1)).resetLevelGamestate(services.freshLevelState);
        assertTrue(results.isDestroyed());
    }

    private static final class RecordingServices extends TestObjectServices {
        private final GameStateManager gameState = mock(GameStateManager.class);
        private final RecordingTitleCard titleCard = new RecordingTitleCard();
        private final Camera camera = new Camera();
        private final List<Integer> playedMusic = new ArrayList<>();
        private final Level level = mock(Level.class);
        private final LevelManager levelManager = mock(LevelManager.class);
        private final LevelState freshLevelState = mock(LevelState.class);
        private final GameModule module = mock(GameModule.class);

        private RecordingServices() {
            when(level.getMinX()).thenReturn(0);
            when(level.getMaxX()).thenReturn(0x6000);
            when(level.getMinY()).thenReturn(0);
            when(level.getMaxY()).thenReturn(0x0B00);
            ZoneRegistry registry = mock(ZoneRegistry.class);
            when(registry.getMusicId(0x04, 1)).thenReturn(0x0A);
            when(module.getZoneRegistry()).thenReturn(registry);
            when(module.createLevelState()).thenReturn(freshLevelState);
        }

        @Override public int romZoneId() { return 0x04; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public Camera camera() { return camera; }
        @Override public Level currentLevel() { return level; }
        @Override public LevelManager levelManager() { return levelManager; }
        @Override public void playMusic(int musicId) { playedMusic.add(musicId); }
        @Override public TitleCardProvider titleCardProvider() { return titleCard; }
        @Override public GameModule gameModule() { return module; }
    }

    private static final class RecordingTitleCard implements TitleCardProvider {
        private boolean started;
        private boolean complete;
        @Override public void initialize(int zoneIndex, int actIndex) { started = true; }
        @Override public void initializeInLevel(int zoneIndex, int actIndex) { started = true; }
        @Override public void update() { }
        @Override public boolean shouldReleaseControl() { return true; }
        @Override public boolean isOverlayActive() { return false; }
        @Override public boolean isComplete() { return complete; }
        @Override public void draw() { }
        @Override public void reset() { }
        @Override public int getCurrentZone() { return 0x04; }
        @Override public int getCurrentAct() { return 1; }
    }
}
