package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.LevelState;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.BossStateContext;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestS3kDezMinibossInstance {
    private static final TouchResponseResult HIT =
            new TouchResponseResult(0x15, 0, 0, TouchCategory.ENEMY);

    @Test
    void registryResolvesSklA6ToTheDezBoss() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 0x0B; }
        };
        assertInstanceOf(S3kDezMinibossInstance.class, registry.create(spawn()));
    }

    @Test
    void firstDispatchBuildsTheCompleteFixedCombatGraph() {
        HarnessServices services = new HarnessServices();
        S3kDezMinibossInstance boss = boss(services);

        boss.update(0, player());

        assertEquals(S3kDezMinibossInstance.CHILD_COUNT, boss.getChildComponents().size());
        assertEquals(0x3680, services.camera.getMinX() & 0xFFFF);
        assertEquals(0x36C0, services.camera.getMaxX() & 0xFFFF);
    }

    @Test
    void eighthHitStopsTimerAndRaisesTheForegroundEventBeforeResults() {
        HarnessServices services = new HarnessServices();
        S3kDezMinibossInstance boss = boss(services);
        TestablePlayableSprite player = player();
        boss.update(0, player);

        BossStateContext state = boss.getState();
        for (int hit = 0; hit < 8; hit++) {
            state.invulnerable = false;
            state.invulnerabilityTimer = 0;
            boss.onPlayerAttack(player, HIT);
        }
        assertEquals(S3kDezMinibossInstance.PHASE_DEFEAT_WAIT, boss.phase());
        boss.update(1, player);

        assertEquals(1, services.runtime.eventsFg4());
        verify(services.levelState).pauseTimer();
    }

    @Test
    void transitionChainRaisesActTwoStageZeroAndOpensVerticalBounds() throws Exception {
        HarnessServices services = new HarnessServices();
        S3kDezMinibossInstance boss = boss(services);
        boss.getState().routine = S3kDezMinibossInstance.PHASE_TRANSITION_DROP;

        boss.update(0, player());

        assertEquals(1, services.runtime.eventsFg4());
        assertEquals(0, services.camera.getMinYTarget());
        assertEquals(0x2000, services.camera.getMaxYTarget() & 0xFFFF);
        assertTrue(boss.isPersistent(), "the ROM carrier survives the act change");
    }

    private static S3kDezMinibossInstance boss(HarnessServices services) {
        S3kDezMinibossInstance boss = new S3kDezMinibossInstance(spawn());
        boss.setServices(services);
        services.objectManager().addDynamicObject(boss);
        return boss;
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x36A0, 0x028C, 0xA6, 0, 0, false, 0);
    }

    private static TestablePlayableSprite player() {
        return new TestablePlayableSprite("sonic", (short) 0x3690, (short) 0x0300);
    }

    private static final class HarnessServices extends StubObjectServices {
        private final Camera camera = new Camera();
        private final LevelState levelState = mock(LevelState.class);
        private final GameStateManager gameState = new GameStateManager();
        private final S3kDezZoneRuntimeState runtime =
                new S3kDezZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);

        private HarnessServices() {
            withIsolatedObjectManager();
            zoneRuntimeRegistry().install(runtime);
        }

        @Override public Camera camera() { return camera; }
        @Override public LevelState levelGamestate() { return levelState; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public Rom rom() { return null; }
    }
}
