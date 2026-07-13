package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.camera.Camera;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.boss.BossStateContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import com.openggf.tests.TestEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSonic1FzWidescreenAndTeamSafety {
    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        TestEnvironment.activeGameplayMode();
        com.openggf.game.GameServices.camera().setX((short) 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void finalFlightContainsAndLocksMainAndThreeSidekicksAtEveryWidth(int width) throws Exception {
        TestPlayableSprite main = player(0x2791);
        TestPlayableSprite p2 = player(0x27E1);
        TestPlayableSprite p3 = player(0x2800);
        TestPlayableSprite p4 = player(0x2900);
        Sonic1FZBossInstance boss = boss(main, List.of(p2, p3, p4), width);

        invokeFinalFlight(boss, main);

        for (TestPlayableSprite player : List.of(main, p2, p3, p4)) {
            assertTrue(player.isControlLocked());
            assertEquals(0, player.getGSpeed());
            assertTrue((player.getCentreX() & 0xFFFF) <= 0x27E0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void bossVisibilityUsesCurrentViewportWidth(int width) throws Exception {
        TestPlayableSprite main = player(0x2600);
        Sonic1FZBossInstance boss = boss(main, List.of(), width);
        BossStateContext state = state(boss);
        state.x = width - 1;
        state.xFixed = state.x << 16;

        Method method = Sonic1FZBossInstance.class.getDeclaredMethod("isBossOnScreen");
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(boss));
    }

    private static Sonic1FZBossInstance boss(TestPlayableSprite main,
            List<TestPlayableSprite> sidekicks, int width) throws Exception {
        Camera camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) width);
        when(camera.getX()).thenReturn((short) 0);
        Sonic1FZBossInstance boss = new Sonic1FZBossInstance(
                new ObjectSpawn(0, 0, Sonic1ObjectIds.FZ_BOSS, 0, 0, false, 0));
        boss.setServices(new TestObjectServices().withSidekicks(sidekicks)
                .withCamera(camera));
        BossStateContext state = state(boss);
        state.routineSecondary = 14;
        state.x = 0;
        state.y = 0;
        state.xFixed = 0;
        state.yFixed = 0;
        return boss;
    }

    private static BossStateContext state(Sonic1FZBossInstance boss) throws Exception {
        Field field = boss.getClass().getSuperclass().getDeclaredField("state");
        field.setAccessible(true);
        return (BossStateContext) field.get(boss);
    }

    private static void invokeFinalFlight(Sonic1FZBossInstance boss, TestPlayableSprite main)
            throws Exception {
        Method method = Sonic1FZBossInstance.class.getDeclaredMethod(
                "updateFinalFlight", com.openggf.sprites.playable.AbstractPlayableSprite.class, int.class);
        method.setAccessible(true);
        method.invoke(boss, main, 1);
    }

    private static TestPlayableSprite player(int x) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) x);
        player.setGSpeed((short) 0x400);
        return player;
    }
}
