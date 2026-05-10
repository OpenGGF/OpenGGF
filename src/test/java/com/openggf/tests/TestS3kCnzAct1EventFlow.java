package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kCnzAct1EventFlow {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void tearDown() {
        GameServices.audio().setBackend(new NullAudioBackend());
        SessionManager.clear();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void firstEventsFg5StartsFgRefresh_notActReload() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_AFTER_BOSS);
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_POST_BOSS);
        events.setEventsFg5(true);

        events.update(0, 0);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine());
        assertFalse(events.isAct2TransitionRequested());
        assertFalse(events.isEventsFg5());
    }

    @Test
    void secondEventsFg5AtTransitionStageRequestsSeamlessActSwap() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_DO_TRANSITION);
        events.setEventsFg5(true);

        events.update(0, 1);

        assertTrue(events.isAct2TransitionRequested());
        assertEquals(0x0301, events.getPendingZoneActWord());
        assertEquals(-0x3000, events.getTransitionWorldOffsetX());
        assertEquals(0x0200, events.getTransitionWorldOffsetY());
    }

    @Test
    void productionPostBossChainAdvancesToReloadGateAndManagerSeesTransitionRequest() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);

        Sonic3kCNZEvents events = manager.getCnzEvents();
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_POST_BOSS);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_AFTER_BOSS);

        events.setEventsFg5(true);
        events.update(0, 0);
        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine());
        assertFalse(manager.isAct2TransitionRequested());

        events.update(0, 1);
        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH_2, events.getBackgroundRoutine());
        assertFalse(manager.isAct2TransitionRequested());

        events.update(0, 2);
        assertEquals(Sonic3kCNZEvents.BG_DO_TRANSITION, events.getBackgroundRoutine());

        events.setEventsFg5(true);
        events.update(0, 3);

        assertTrue(events.isAct2TransitionRequested());
        assertTrue(manager.isAct2TransitionRequested());
        assertEquals(0x0301, events.getPendingZoneActWord());
    }

    @Test
    void lowerRouteMinibossEntryRemapsPlayersAndCameraIntoBossTunnel() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x3000, (short) 0x0650);
        GameServices.sprites().addSprite(player);
        GameServices.camera().setFocusedSprite(player);
        GameServices.camera().setMinY((short) 0);
        GameServices.camera().setX((short) 0x3000);
        GameServices.camera().setY((short) 0x0600);

        events.update(0, 0);

        assertEquals(0x0650 - 0x0700, player.getCentreY(),
                "CNZ1 lower-route miniboss entry should mirror the ROM's -$700 player Y remap");
        assertEquals(0x0600 - 0x0700, GameServices.camera().getY(),
                "CNZ1 lower-route miniboss entry should mirror the ROM's -$700 camera Y remap");
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_Y, GameServices.camera().getMinY() & 0xFFFF,
                "CNZ1BGE_Normal should set the tunnel minimum Y before the $31E0 arena gate");
        assertTrue(events.isWallGrabSuppressed(),
                "CNZ1BGE_Normal should suppress wall-grab interactions inside the boss tunnel");
        assertFalse(events.isBossFlag(),
                "Early tunnel entry must not set Boss_flag before the $31E0 arena gate");
        assertEquals(Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH,
                events.getBossBackgroundMode());
    }

    @Test
    void lowerRouteTunnelModeAt3000DoesNotArmRomArenaGateUntil31E0() {
        RecordingAudioBackend audio = new RecordingAudioBackend();
        GameServices.audio().setBackend(audio);

        Sonic3kCNZEvents events = initCnzEvents(0);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x3000, (short) 0x0650);
        GameServices.sprites().addSprite(player);
        GameServices.camera().setFocusedSprite(player);
        GameServices.camera().setMinX((short) 0x0000);
        GameServices.camera().setMaxX((short) 0x4000);
        GameServices.camera().setMinY((short) 0x0000);
        GameServices.camera().setMaxY((short) 0x1000);
        GameServices.camera().setMaxYTarget((short) 0x1000);
        GameServices.camera().setX((short) 0x3000);
        GameServices.camera().setY((short) 0x0600);

        events.update(0, 0);

        assertEquals(0x0650 - 0x0700, player.getCentreY(),
                "The early lower-route remap must still use ROM centre coordinates");
        assertEquals(0x0600 - 0x0700, GameServices.camera().getY(),
                "The early lower-route remap must move the foreground camera into the tunnel");
        assertEquals(Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH,
                events.getBossBackgroundMode(),
                "Camera X $3000 may enter the miniboss tunnel scroll mode");
        assertFalse(events.isBossFlag(), "Camera X $3000 must not set Boss_flag");
        assertTrue(events.isWallGrabSuppressed(), "Camera X $3000 should suppress wall-grab during the boss tunnel setup");
        assertEquals(0x0000, GameServices.camera().getMinX() & 0xFFFF,
                "Camera X $3000 must not clamp Camera_min_X_pos to the ROM arena gate");
        assertEquals(0x4000, GameServices.camera().getMaxX() & 0xFFFF,
                "Camera X $3000 must not clamp Camera_max_X_pos to the ROM arena gate");
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_Y, GameServices.camera().getMinY() & 0xFFFF,
                "Camera X $3000 should apply the ROM tunnel minimum Y");
        assertEquals(0x1000, GameServices.camera().getMaxYTarget() & 0xFFFF,
                "Camera X $3000 must not set the arena target max Y");
        assertEquals(0, audio.fadeOutCount,
                "Camera X $3000 must not fade music for the arena gate");
        assertTrue(audio.playedMusic.isEmpty(),
                "Camera X $3000 must not start miniboss music");

        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
        events.update(0, 1);

        assertTrue(events.isBossFlag(), "Camera X $31E0 is the ROM arena gate that sets Boss_flag");
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X, GameServices.camera().getMinX() & 0xFFFF);
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_X, GameServices.camera().getMaxX() & 0xFFFF);
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_Y, GameServices.camera().getMinY() & 0xFFFF);
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_Y, GameServices.camera().getMaxYTarget() & 0xFFFF);
        assertEquals(1, audio.fadeOutCount,
                "The real arena gate fades current music before the 120-frame wait");
        assertTrue(audio.playedMusic.isEmpty(),
                "Miniboss music must wait for the outer 120-frame release timer");
    }

    @Test
    void arenaGateReleasesMinibossMusicOnceAfter120FrameWait() {
        RecordingAudioBackend audio = new RecordingAudioBackend();
        GameServices.audio().setBackend(audio);

        Sonic3kCNZEvents events = initCnzEvents(0);
        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);

        events.update(0, 0);

        assertEquals(1, audio.fadeOutCount,
                "Arena gate should issue the ROM fade-out immediately");
        assertTrue(audio.playedMusic.isEmpty(),
                "Miniboss music should not start on the gate frame");

        for (int frame = 1; frame < 120; frame++) {
            events.update(0, frame);
        }

        assertEquals(List.of(Sonic3kMusic.MINIBOSS.id), audio.playedMusic,
                "The outer gate releases mus_Miniboss after the 120-frame wait");

        for (int frame = 120; frame < 180; frame++) {
            events.update(0, frame);
        }

        assertEquals(List.of(Sonic3kMusic.MINIBOSS.id), audio.playedMusic,
                "The release timer must not replay miniboss music after it fires once");
    }

    @Test
    void bossScrollStartAdvancesWhenTunnelBackgroundReachesRomThreshold() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_BOSS_START);
        events.setBossScrollState(0x0120, 0);
        GameServices.camera().setY((short) 0x01C0);

        events.update(0, 0);

        assertEquals(Sonic3kCNZEvents.BG_BOSS, events.getBackgroundRoutine());
    }

    @Test
    void act2KnucklesEntryStartsTeleporterRoute_notModeOnly() {
        Sonic3kCNZEvents events = initCnzEvents(1);

        events.beginKnucklesTeleporterRoute();

        assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, events.getForegroundRoutine());
        assertTrue(events.isKnucklesTeleporterRouteActive());
        assertEquals(0x4750, events.getCameraMinXClamp());
        assertEquals(0x48E0, events.getCameraMaxXClamp());
    }

    private Sonic3kCNZEvents initCnzEvents(int act) {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, act);
        return manager.getCnzEvents();
    }

    private static final class RecordingAudioBackend extends NullAudioBackend {
        private int fadeOutCount;
        private final List<Integer> playedMusic = new ArrayList<>();

        @Override
        public void playMusic(int musicId) {
            playedMusic.add(musicId);
        }

        @Override
        public void fadeOutMusic(int steps, int delay) {
            fadeOutCount++;
        }
    }
}
