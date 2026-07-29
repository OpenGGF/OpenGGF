package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.openggf.game.sonic3k.objects.HCZWaterRushObjectInstance.HCZBreakableBarState;
import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
class TestHczLargeFanCoordinateParity {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
    }

    @Test
    void activationWindowUsesPlayerRomCentrePositionAfterKosLoadWait() {
        HCZBreakableBarState.reset();
        int fanX = 0x0B80;
        int fanY = 0x0580;
        HCZLargeFanObjectInstance fan = new HCZLargeFanObjectInstance(
                new ObjectSpawn(fanX, fanY, Sonic3kObjectIds.HCZ_LARGE_FAN, 0, 0, false, 0));
        HardwareTimingService timing = GameServices.hardwareTiming();
        timing.resetForMissingSnapshot();
        Rom rom = TestEnvironment.currentRom();
        fan.setServices(new TestObjectServices() {
            @Override
            public HardwareTimingService hardwareTiming() {
                return timing;
            }

            @Override
            public Rom rom() {
                return rom;
            }

            @Override
            public RuntimeArtCoordinator runtimeArtCoordinator() {
                return GameServices.runtimeArtCoordinator();
            }
        });

        TestablePlayableSprite player = standingPlayer();
        player.setCentreX((short) (fanX + 0x20));
        player.setCentreY((short) (fanY + 0x20));

        fan.update(1, player);
        assertEquals(fanY, fan.getY(),
                "Obj_HCZLargeFan queues Kosinski art on trigger before the first drop tick");
        int frame = 2;
        while (timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE) > 0
                && frame < 10_000) {
            serviceBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP);
            fan.update(frame++, player);
            serviceBoundary(HardwareServiceBoundary.POST_OBJECTS);
        }
        assertEquals(0, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
        assertEquals(fanY, fan.getY(),
                "Obj_HCZLargeFan waits for queued art before initializing the falling fan");
        serviceBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP);
        fan.update(frame, player);
        serviceBoundary(HardwareServiceBoundary.POST_OBJECTS);

        assertEquals(fanY + 8, fan.getY(),
                "Obj_HCZLargeFan compares ROM x_pos/y_pos, which map to player centre coordinates");
    }

    private static void serviceBoundary(HardwareServiceBoundary boundary) {
        GameServices.hardwareTiming().service(boundary);
        GameServices.runtimeArtCoordinator().afterTimingService(boundary);
    }

    private static TestablePlayableSprite standingPlayer() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setWidth(18);
        player.setHeight(38);
        return player;
    }
}
