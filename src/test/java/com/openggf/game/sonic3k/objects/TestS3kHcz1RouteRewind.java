package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Each case reaches its own live production spot; later route failures cannot hide early checks. */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("slow-suite")
class TestS3kHcz1RouteRewind {
    enum Spot {
        WATER_ENTRY, BUBBLE_SHIELD, FIRST_BRIDGE, SECOND_BRIDGE,
        FAN_LIFT, CONVEYOR_RIDE, SPRING_ASCENT, BOSS_ACTIVE, BOSS_HIT, BOSS_DEFEATED
    }

    @ParameterizedTest(name = "HCZ1 native rewind {0}")
    @EnumSource(Spot.class)
    void liveSpotRestoresAndReplaysTwice(Spot spot) throws Exception {
        Hcz1Route.withConfiguration(320, "off", route -> {
            while (!atSpot(spot, route)) {
                assertEquals(0, GameServices.level().getCurrentAct(), "missed " + spot);
                route.step();
            }
            replayTwice(route, 30, spot.name());
        });
    }

    @ParameterizedTest(name = "HCZ1 horizontal admission rewind width={0}")
    @ValueSource(ints = {320, 400, 512, 640, 800})
    void horizontalArenaAdmissionRestoresAndReplaysTwice(int width) throws Exception {
        Hcz1Route.withConfiguration(width, "off", route -> {
            var camera = route.fixture.camera();
            while (route.boss() == null || route.boss().getState().routine != 2
                    || camera.getX() + (width - 320) / 2 < 0x3680 - 48) {
                assertEquals(0, GameServices.level().getCurrentAct(), "missed arena approach");
                assertNotEquals(0x3680, camera.getMaxX() & 0xffff, "missed horizontal admission");
                route.step();
            }
            assertNotEquals(0x3680, camera.getMaxX() & 0xffff, "capture must precede the lock");
            replayTwice(route, 30, "HORIZONTAL_ADMISSION_WIDTH_" + width);
            assertEquals(0x3680, camera.getMinX() & 0xffff, "replay must cross horizontal admission");
            assertEquals(0x3680, camera.getMaxX() & 0xffff);
        });
    }

    static void replayTwice(Hcz1Route route, int count, String context) {
        var fixture = route.fixture;
        int captureFrame = route.frames;
        int previousMask = route.previousMask;
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        List<Integer> masks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            masks.add(route.step());
            assertEquals(0, GameServices.level().getCurrentAct(), "rewind window crossed reload: " + context);
        }
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            fixture.runner().primeInputState(new Bk2FrameInput(0, previousMask,
                    (previousMask & 16) == 0 ? 0 : 1, false, ""));
            TestS3kAiz1RouteRewind.assertSnapshotsMatch(before, registry.capture(),
                    context + " restore cycle=" + cycle, captureFrame, count);
            for (int mask : masks) {
                InputProgram.step(fixture, mask);
                assertFalse(fixture.sprite().getDead() || fixture.sprite().isDrowningDeath());
            }
            TestS3kAiz1RouteRewind.assertSnapshotsMatch(expected, registry.capture(),
                    context + " replay cycle=" + cycle, captureFrame, count);
        }
        System.out.printf("HCZREWIND spot=%s frame=%d replay=%d cycles=2%n", context, captureFrame, count);
    }

    private static boolean atSpot(Spot spot, Hcz1Route route) {
        var player = route.fixture.sprite();
        var boss = route.boss();
        return switch (spot) {
            case WATER_ENTRY -> player.isInWater();
            case BUBBLE_SHIELD -> player.isInWater() && player.hasShield();
            case FIRST_BRIDGE -> triggeredBridge(route.firstBridge);
            case SECOND_BRIDGE -> triggeredBridge(route.secondBridge);
            case FAN_LIFT -> route.stage == Hcz1Route.Stage.FAN_LIFT && player.getYSpeed() < 0;
            case CONVEYOR_RIDE -> route.stage == Hcz1Route.Stage.CONVEYOR_EXIT && player.isObjectControlled();
            case SPRING_ASCENT -> route.stage == Hcz1Route.Stage.SPRING_ASCENT;
            case BOSS_ACTIVE -> boss != null && boss.getState().routine >= 4 && !boss.getState().defeated;
            case BOSS_HIT -> boss != null && boss.getState().hitCount < 6 && boss.getState().hitCount > 0;
            case BOSS_DEFEATED -> boss != null && boss.getState().defeated;
        };
    }

    private static boolean triggeredBridge(com.openggf.level.objects.ObjectSpawn placement) {
        // Capture the real Blastoid trigger while the bridge owner is still live,
        // not merely the controller's earlier approach stage.
        return com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.testAny(placement.subtype() & 0x0f)
                && GameServices.level().getObjectManager().getActiveObjects().stream()
                        .anyMatch(object -> object instanceof CollapsingBridgeObjectInstance
                                && placement.equals(object.getSpawn()));
    }

}
