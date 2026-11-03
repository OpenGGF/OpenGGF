package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.tests.rules.SonicGame;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.SeamlessLevelTransitionRequest.ObjectSurvivalPolicy;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Bounded shared reload checks; these do not replay each zone's boss/event route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kActTransitionShieldContinuity {
    record Reload(int zone, ObjectSurvivalPolicy policy, boolean registeredInsta) {}

    static Stream<Reload> reloads() {
        // Production act-1 -> act-2 carry policies. SSZ/DDZ have no such reload.
        return Stream.of(
                new Reload(0, ObjectSurvivalPolicy.ALL_LIVE_SST, false),
                new Reload(1, ObjectSurvivalPolicy.PERSISTENT_EXACT_SST, false),
                new Reload(2, ObjectSurvivalPolicy.PERSISTENT_EXACT_SST, false),
                new Reload(3, ObjectSurvivalPolicy.PERSISTENT_EXACT_SST, false),
                new Reload(4, ObjectSurvivalPolicy.ALL_LIVE_SST, false),
                new Reload(5, ObjectSurvivalPolicy.PERSISTENT_ONLY, false),
                new Reload(6, ObjectSurvivalPolicy.PERSISTENT_EXACT_SST, false),
                new Reload(7, ObjectSurvivalPolicy.PERSISTENT_EXACT_SST, false),
                new Reload(8, ObjectSurvivalPolicy.ALL_LIVE_SST, false),
                new Reload(9, ObjectSurvivalPolicy.ALL_LIVE_SST, false),
                new Reload(11, ObjectSurvivalPolicy.ALL_LIVE_SST, false))
                .flatMap(reload -> Stream.of(reload,
                        new Reload(reload.zone(), reload.policy(), true)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reloads")
    void fireShieldKeepsUpdatingAcrossEveryActReload(Reload reload) throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(reload.zone(), 0).build();
        var player = fixture.sprite();
        if (reload.registeredInsta()) player.tickStatus();
        player.giveShield(ShieldType.FIRE);
        var shield = player.getShieldObject();
        assertNotNull(shield);
        var field = shield.getClass().getDeclaredField("animationLifecycle");
        field.setAccessible(true);
        var lifecycle = field.get(shield);
        var capture = lifecycle.getClass().getDeclaredMethod("captureRewindStateValue");
        capture.setAccessible(true);

        GameServices.level().applySeamlessTransition(SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(reload.zone(), 1)
                .preserveMusic(true)
                .objectSurvivalPolicy(reload.policy())
                .build());

        assertEquals(1, GameServices.level().getCurrentAct());
        assertSame(shield, player.getShieldObject());
        var objects = GameServices.level().getObjectManager();
        assertEquals(1, objects.getActiveObjects().stream().filter(o -> o == shield).count());
        // Isolate object dispatch from unrelated stage entry hazards. This is the
        // production Process_Sprites pass, not a direct shield.update() call.
        var before = capture.invoke(lifecycle);
        boolean advanced = false;
        for (int i = 0; i < 16; i++) {
            player.tickStatus();
            objects.update(GameServices.camera().getX(), player, java.util.List.of(), i, false);
            advanced |= !before.equals(capture.invoke(lifecycle));
        }
        assertFalse(((AbstractObjectInstance) shield).isDestroyed());
        assertTrue(advanced, "the carried fire shield must receive object updates");
    }
}
