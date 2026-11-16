package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.S3kScreenShake;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@code ShakeScreen_Setup} (sonic3k.asm:104234-104256) writes offset 0 and
 * leaves {@code Screen_shake_flag} untouched while {@code Player_1+routine >= 6}.
 * Routine 6 is installed before the engine's dead flag in a drowning sink, so
 * every zone call site must read the native routine, not {@code getDead()}.
 * Each case first proves its real call site consumes a timed countdown, then
 * repeats it with Player 1 at routine 6.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kShakeSetupPlayerRoutineGate {
    private static final int TIMED = 0x14;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    private static Sonic3kLevelEventManager manager() {
        return (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
    }

    private static void assertGate(HeadlessTestFixture fixture, Supplier<S3kScreenShake> shake, Runnable callSite) {
        assertGate(fixture, () -> shake.get().flag(), () -> shake.get().offset(),
                value -> shake.get().writeFlag(value), callSite);
    }

    private static void assertGate(HeadlessTestFixture fixture, IntSupplier flag, IntSupplier offset,
                                   IntConsumer writeFlag, Runnable callSite) {
        writeFlag.accept(TIMED);
        callSite.run();
        assertEquals(TIMED - 1, flag.getAsInt(), "control: the call site must run ShakeScreen_Setup");

        fixture.sprite().setObjectRoutineOverride(6);
        assertFalse(fixture.sprite().getDead(), "routine 6 precedes the engine dead flag");
        writeFlag.accept(TIMED);
        callSite.run();
        assertEquals(TIMED, flag.getAsInt(), "routine >= 6 skips the countdown");
        assertEquals(0, offset.getAsInt(), "routine >= 6 publishes no shake");
    }

    @Test
    void hiddenPalace() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .withSkippedZoneIntro().build();
        assertGate(fixture, () -> S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().screenShake(), () -> manager().update());
    }

    @Test
    void lavaReef() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .withSkippedZoneIntro().build();
        assertGate(fixture, () -> S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().screenShake(), () -> manager().update());
    }

    @Test
    void skySanctuaryAct1() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withSkippedZoneIntro().build();
        assertGate(fixture, () -> S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().screenShake(), () -> manager().update());
    }

    @Test
    void skySanctuaryAct2() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 1)
                .withSkippedZoneIntro().build();
        assertGate(fixture, () -> S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().screenShake(), () -> manager().update());
    }

    @Test
    void sandopolis() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SOZ, 0)
                .withSkippedZoneIntro().build();
        var events = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().events();
        assertGate(fixture, events::screenShakeFlag, events::screenShakeOffset, events::screenShakeFlag,
                () -> manager().getSozEvents().updateShake(events, GameServices.level().getFrameCounter()));
    }

    @Test
    void deathEggFinalArena() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0).build();
        fixture.stepIdleFrames(1);
        var state = (DezFinalBossZoneRuntimeState) GameServices.zoneRuntimeState();
        assertGate(fixture, state::screenShake, () -> DezFinalScreenEvents.update(
                GameServices.level().getObjectManager(), state, GameServices.level().getFrameCounter()));
    }
}
