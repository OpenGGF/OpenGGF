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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestS3kDezFinalBossInstance {
    private static final TouchResponseResult HIT =
            new TouchResponseResult(0x0F, 0, 0, TouchCategory.ENEMY);

    @Test
    void firstDispatchBuildsTheFixedBossGraph() {
        Harness services = new Harness(PlayerCharacter.SONIC_ALONE);
        S3kDezFinalBossInstance boss = boss(services);
        boss.update(0, player());
        assertEquals(S3kDezFinalBossInstance.GRAPH_SIZE, boss.getChildComponents().size());
        assertEquals(0x3C0, services.runtime.act3BackgroundWord(0x02));
        assertEquals(0x0F8, services.runtime.act3BackgroundWord(0x04));
    }

    @Test
    void eighthHitStartsTheArenaShrinkAndStopsTheTimer() {
        Harness services = new Harness(PlayerCharacter.SONIC_ALONE);
        S3kDezFinalBossInstance boss = boss(services);
        var player = player();
        boss.setPhaseForTesting(S3kDezFinalBossInstance.FIGHT, 0);
        boss.update(0, player);
        for (int hit = 0; hit < 8; hit++) {
            boss.getState().invulnerable = false;
            boss.getState().invulnerabilityTimer = 0;
            boss.onPlayerAttack(player, HIT);
        }
        assertEquals(S3kDezFinalBossInstance.ARENA_SHRINK, boss.phase());
        verify(services.levelState).pauseTimer();
    }

    @Test
    void sevenEmeraldSonicExitRequestsDoomsday() {
        Harness services = new Harness(PlayerCharacter.SONIC_ALONE);
        for (int index = 0; index < 7; index++) services.gameState.markEmeraldCollected(index);
        S3kDezFinalBossInstance boss = boss(services);
        boss.setPhaseForTesting(S3kDezFinalBossInstance.EXIT, 1);
        boss.update(0, player());
        assertEquals(Sonic3kZoneIds.ZONE_DDZ, services.requestedZone);
        assertEquals(0, services.requestedAct);
    }

    @Test
    void nonSuperSonicExitRequestsEndingCampaign() {
        Harness services = new Harness(PlayerCharacter.SONIC_AND_TAILS);
        S3kDezFinalBossInstance boss = boss(services);
        boss.setPhaseForTesting(S3kDezFinalBossInstance.EXIT, 1);
        boss.update(0, player());
        assertEquals(Sonic3kZoneIds.ZONE_INTRO_ENDING, services.requestedZone);
        assertEquals(1, services.requestedAct);
    }

    private static S3kDezFinalBossInstance boss(Harness services) {
        var boss = new S3kDezFinalBossInstance(new ObjectSpawn(
                0x3C0, 0x0F8, 0xA7, 0, 0, false, -1));
        boss.setServices(services);
        services.objectManager().addDynamicObject(boss);
        return boss;
    }

    private static TestablePlayableSprite player() {
        return new TestablePlayableSprite("sonic", (short) 0x300, (short) 0xCD);
    }

    private static final class Harness extends StubObjectServices {
        private final Camera camera = new Camera();
        private final LevelState levelState = mock(LevelState.class);
        private final GameStateManager gameState = new GameStateManager();
        private final S3kDezZoneRuntimeState runtime;
        private int requestedZone = -1;
        private int requestedAct = -1;

        Harness(PlayerCharacter character) {
            withIsolatedObjectManager();
            runtime = new S3kDezZoneRuntimeState(
                    Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0, character);
            zoneRuntimeRegistry().install(runtime);
        }

        @Override public Camera camera() { return camera; }
        @Override public LevelState levelGamestate() { return levelState; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public Rom rom() { return null; }
        @Override public void requestZoneAndAct(int zone, int act, boolean deactivate) {
            requestedZone = zone;
            requestedAct = act;
        }
    }
}
