package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestS3kDezEndBossInstance {
    private static final TouchResponseResult HIT =
            new TouchResponseResult(0x16, 0, 0, TouchCategory.ENEMY);

    @Test
    void registryResolvesSklA7ToTheDezBoss() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return Sonic3kZoneIds.ZONE_DEZ; }
        };
        assertInstanceOf(S3kDezEndBossInstance.class, registry.create(spawn()));
    }

    @Test
    void initialDispatchBuildsRobotnikArenaAndPlayerBallGraph() {
        HarnessServices services = new HarnessServices();
        S3kDezEndBossInstance boss = boss(services);
        boss.update(0, player(0x3480));
        assertEquals(S3kDezEndBossInstance.INITIAL_GRAPH_SIZE, boss.getChildComponents().size());
    }

    @Test
    void eighthStrikeStopsTimerAndClearsReverseGravity() {
        HarnessServices services = new HarnessServices();
        services.gameState.setReverseGravityActive(true);
        S3kDezEndBossInstance boss = boss(services);
        var player = player(0x3480);
        boss.update(0, player);

        for (int hit = 0; hit < 8; hit++) {
            boss.getState().invulnerable = false;
            boss.getState().invulnerabilityTimer = 0;
            boss.onPlayerAttack(player, HIT);
        }

        assertEquals(S3kDezEndBossInstance.PHASE_DEFEAT_FALL, boss.phase());
        assertFalse(services.gameState.isReverseGravityActive());
        verify(services.levelState).pauseTimer();
    }

    @Test
    void postDefeatRaisesTerrainEventThenRequestsThe1700Arena() {
        HarnessServices services = new HarnessServices();
        S3kDezEndBossInstance boss = boss(services);
        boss.setPhaseTimerForTesting(S3kDezEndBossInstance.PHASE_DEFEAT_WAIT, 0x30);
        boss.update(0, player(0x3480));
        assertEquals(1, services.runtime.eventsFg4());

        services.camera.setX((short) 0x3620);
        boss.setPhaseTimerForTesting(S3kDezEndBossInstance.PHASE_CAMERA_EXIT, 0);
        boss.update(1, player(0x3480));
        boss.update(2, player(0x3780));

        assertEquals(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, services.requestedZone);
        assertEquals(0, services.requestedAct);
    }

    private static S3kDezEndBossInstance boss(HarnessServices services) {
        S3kDezEndBossInstance boss = new S3kDezEndBossInstance(spawn());
        boss.setServices(services);
        services.objectManager().addDynamicObject(boss);
        return boss;
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x3480, 0x0218, 0xA7, 0, 0, false, 0);
    }

    private static TestablePlayableSprite player(int x) {
        return new TestablePlayableSprite("sonic", (short) x, (short) 0x0280);
    }

    private static final class HarnessServices extends StubObjectServices {
        private final Camera camera = new Camera();
        private final LevelState levelState = mock(LevelState.class);
        private final GameStateManager gameState = new GameStateManager();
        private final S3kDezZoneRuntimeState runtime =
                new S3kDezZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        private int requestedZone = -1;
        private int requestedAct = -1;

        private HarnessServices() {
            withIsolatedObjectManager();
            zoneRuntimeRegistry().install(runtime);
        }

        @Override public Camera camera() { return camera; }
        @Override public LevelState levelGamestate() { return levelState; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public Rom rom() { return null; }
        @Override public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
            requestedZone = zone;
            requestedAct = act;
        }
    }
}
