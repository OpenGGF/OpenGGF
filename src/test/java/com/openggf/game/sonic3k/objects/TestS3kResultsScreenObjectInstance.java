package com.openggf.game.sonic3k.objects;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.events.S3kTransitionEventBridge;
import com.openggf.camera.Camera;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestS3kResultsScreenObjectInstance {

    @Test
    void actOneTransitionSignalWaitsForRomKosCreateGate() {
        TransitionRecordingServices services = new TransitionRecordingServices(0x03);
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                services,
                () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_AND_TAILS, 0));
        results.setServices(services);

        assertEquals(0, services.signalCount,
                "Obj_LevelResultsInit only queues Kosinski modules; Events_fg_5 is not set until "
                        + "Obj_LevelResultsCreate sees Kos_modules_left == 0 "
                        + "(docs/skdisasm/sonic3k.asm:62512-62584,62586-62616)");

        for (int i = 0; i < 8; i++) {
            results.update(i, null);
            assertEquals(0, services.signalCount,
                    "Obj_LevelResultsCreate must keep waiting while the ROM Kos module queue is nonzero "
                            + "(docs/skdisasm/sonic3k.asm:62586-62590)");
        }

        results.update(8, null);
        assertEquals(1, services.signalCount,
                "Obj_LevelResultsCreate sets Events_fg_5 only after the art-load gate opens "
                        + "(docs/skdisasm/sonic3k.asm:62610-62616)");
    }

    @Test
    void resultsCreateGateReadyUsesPostDecrementWait() {
        assertFalse(S3kResultsScreenObjectInstance.romResultsCreateGateReady(1));
        assertTrue(S3kResultsScreenObjectInstance.romResultsCreateGateReady(0));
    }

    @Test
    void cnzActOneExitStartsActTwoTitleCardAndMusic() throws Exception {
        ActTransitionRecordingServices services = new ActTransitionRecordingServices(0x03, Sonic3kMusic.CNZ2.id);
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                services,
                () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_AND_TAILS, 0));
        results.setServices(services);

        Method onExitReady = S3kResultsScreenObjectInstance.class.getDeclaredMethod("onExitReady");
        onExitReady.setAccessible(true);
        onExitReady.invoke(results);

        assertEquals(List.of(Sonic3kMusic.CNZ2.id), services.playedMusic,
                "Obj_LevelResults exit for CNZ Act 1 must start the next act's level music "
                        + "after the act-clear results leave (docs/skdisasm/sonic3k.asm:62708-62720)");
        assertEquals(List.of("3:1"), services.titleCard.calls,
                "Obj_LevelResults exit for CNZ Act 1 mutates into the in-level Act 2 title card "
                        + "(docs/skdisasm/sonic3k.asm:62708-62720)");
        assertEquals(1, services.apparentAct,
                "Act 1 results exit must update Apparent_act to Act 2 before title-card handoff "
                        + "(docs/skdisasm/sonic3k.asm:62708-62720)");
    }

    @Test
    void hczAndMgzSeamlessActOneExitSetsTransitionReadyFlag() throws Exception {
        for (int zone : List.of(0x01, 0x02)) {
            ActTransitionRecordingServices services = new ActTransitionRecordingServices(zone, Sonic3kMusic.HCZ2.id);
            S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                    services,
                    () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_AND_TAILS, 0));
            results.setServices(services);

            Method onExitReady = S3kResultsScreenObjectInstance.class.getDeclaredMethod("onExitReady");
            onExitReady.setAccessible(true);
            onExitReady.invoke(results);

            verify(services.gameState).setEndOfLevelFlag(true);
        }
    }

    @Test
    void iczActTwoExitKeepsBossLeftCameraLockWhenRestoringLevelBounds() throws Exception {
        IczExitRecordingServices services = new IczExitRecordingServices();
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                services,
                () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_ALONE, 1));
        results.setServices(services);

        Method onExitReady = S3kResultsScreenObjectInstance.class.getDeclaredMethod("onExitReady");
        onExitReady.setAccessible(true);
        onExitReady.invoke(results);

        assertEquals(0x4560, services.camera.getMinX() & 0xFFFF,
                "ICZ2's capsule handoff leaves Camera_min_X_pos at the post-boss lock instead of restoring level start");
        assertEquals(0x7000, services.camera.getMaxX() & 0xFFFF,
                "The right edge should still be released to the full level/end bounds for the results exit");
        assertEquals(0x0000, services.camera.getMinY() & 0xFFFF);
        assertEquals(0x0800, services.camera.getMaxY() & 0xFFFF);
    }

    private static final class TransitionRecordingServices extends TestObjectServices {
        private final int zone;
        private final RecordingBridge bridge = new RecordingBridge();
        private int signalCount;

        private TransitionRecordingServices(int zone) {
            this.zone = zone;
        }

        @Override
        public int romZoneId() {
            return zone;
        }

        @Override
        public LevelEventProvider levelEventProvider() {
            return bridge;
        }

        private final class RecordingBridge implements LevelEventProvider, S3kTransitionEventBridge {
            @Override
            public void initLevel(int zone, int act) {
            }

            @Override
            public void update() {
            }

            @Override
            public void signalActTransition() {
                signalCount++;
            }

            @Override
            public void requestHczPostTransitionCutscene() {
            }

            @Override
            public void requestMgzPostTransitionRelease() {
            }

            @Override
            public void requestCnzPostTransitionRelease(int framesUntilRelease) {
            }
        }
    }

    private static final class ActTransitionRecordingServices extends TestObjectServices {
        private final int zone;
        private final int act2MusicId;
        private final GameStateManager gameState = mock(GameStateManager.class);
        private final Camera camera = new Camera();
        private final RecordingTitleCardProvider titleCard = new RecordingTitleCardProvider();
        private final List<Integer> playedMusic = new ArrayList<>();
        private int apparentAct = -1;

        private ActTransitionRecordingServices(int zone, int act2MusicId) {
            this.zone = zone;
            this.act2MusicId = act2MusicId;
        }

        @Override
        public int romZoneId() {
            return zone;
        }

        @Override
        public GameStateManager gameState() {
            return gameState;
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public GameModule gameModule() {
            GameModule module = mock(GameModule.class);
            ZoneRegistry zoneRegistry = mock(ZoneRegistry.class);
            when(zoneRegistry.getMusicId(zone, 1)).thenReturn(act2MusicId);
            when(module.getZoneRegistry()).thenReturn(zoneRegistry);
            return module;
        }

        @Override
        public void playMusic(int musicId) {
            playedMusic.add(musicId);
        }

        @Override
        public void setApparentAct(int act) {
            apparentAct = act;
        }

        @Override
        public TitleCardProvider titleCardProvider() {
            return titleCard;
        }
    }

    private static final class IczExitRecordingServices extends TestObjectServices {
        private final Camera camera = new Camera();
        private final GameStateManager gameState = mock(GameStateManager.class);
        private final Level level = mock(Level.class);

        private IczExitRecordingServices() {
            camera.setMinX((short) 0x4560);
            camera.setMaxX((short) 0x44C0);
            camera.setMinY((short) 0x05F8);
            camera.setMaxY((short) 0x05F8);
            when(level.getMinX()).thenReturn(0);
            when(level.getMaxX()).thenReturn(0x7000);
            when(level.getMinY()).thenReturn(0);
            when(level.getMaxY()).thenReturn(0x0800);
        }

        @Override
        public int romZoneId() {
            return 0x05;
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public GameStateManager gameState() {
            return gameState;
        }

        @Override
        public Level currentLevel() {
            return level;
        }
    }

    private static final class RecordingTitleCardProvider implements TitleCardProvider {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void initialize(int zoneIndex, int actIndex) {
            calls.add(zoneIndex + ":" + actIndex);
        }

        @Override
        public void initializeInLevel(int zoneIndex, int actIndex) {
            calls.add(zoneIndex + ":" + actIndex);
        }

        @Override public void update() {}
        @Override public boolean shouldReleaseControl() { return false; }
        @Override public boolean isOverlayActive() { return false; }
        @Override public boolean isComplete() { return false; }
        @Override public void draw() {}
        @Override public void reset() {}
        @Override public int getCurrentZone() { return -1; }
        @Override public int getCurrentAct() { return -1; }
    }
}
