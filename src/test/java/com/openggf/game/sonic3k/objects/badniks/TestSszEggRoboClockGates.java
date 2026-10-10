package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The EggRobo routines read {@code (V_int_run_count+3).w}: the low byte of the big-endian
 * longword counter, i.e. {@code V_int_run_count & $FF}. The {@code +3} is an address, so none
 * of these gates is shifted by three ticks.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboClockGates {

    /** {@code loc_915F6}: {@code andi.b #$F,d0 / bne.s loc_91648} before each release. */
    @Test
    void releaserFreesAnAnimalOnlyWhenTheVintLowNibbleIsZero() {
        EggRoboBadnikInstance releaser = onScreenReleaser();
        int animalsLeft = releaser.animalsLeftForTest();
        List<Integer> releaseTicks = new ArrayList<>();
        // Start mid-cycle with a non-zero high byte: only the low nibble of the low byte gates.
        for (int count = 0x1201; count <= 0x1250; count++) {
            releaser.update(count, null);
            if (releaser.animalsLeftForTest() != animalsLeft) {
                releaseTicks.add(count);
                animalsLeft = releaser.animalsLeftForTest();
            }
        }

        assertEquals(List.of(0x1210, 0x1220, 0x1230, 0x1240, 0x1250), releaseTicks,
                "each release waits for (V_int_run_count & $F) == 0");
        assertEquals("RISING", releaser.stateForTest(),
                "the release that takes $39 negative hands the object to loc_9164E");
    }

    /** {@code loc_9164E} and {@code loc_9167E} both open with {@code bsr.w sub_91988}. */
    @Test
    void launchedReleaserAlternatesFramesOneAndThreeOnVintBitZero() {
        EggRoboBadnikInstance robo = onScreenReleaser();
        int count = 0x1301;
        while (robo.stateForTest().equals("RELEASING")) {
            robo.update(count++, null);
        }
        int checked = 0;
        for (; robo.stateForTest().equals("RISING") || robo.stateForTest().equals("FALLING"); count++) {
            robo.update(count, null);
            assertEquals((count & 1) != 0 ? 3 : 1, robo.mappingFrame,
                    "sub_91988: frame 3 when bit 0 of (V_int_run_count+3) is set, else 1, at tick " + count);
            checked++;
        }
        assertTrue(checked > 8, "the rise and fall last more than a few frames");
    }

    /** {@code loc_9176C}: {@code moveq #0,d0 / btst #0,(V_int_run_count+3).w / beq / moveq #7,d0}. */
    @Test
    void shotChargeFlashShowsFrameSevenOnOddVintTicks() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var shot = new EggRoboShotInstance(new ObjectSpawn(0x200, 0xC00, 0, 0, 0, false, 0), true);
        shot.setServices(TestEnvironment.objectServices());
        for (int count = 0x2201; count <= 0x2210; count++) {
            shot.update(count, null);
            assertEquals((count & 1) != 0 ? 7 : 0, shot.mappingFrameForTest(),
                    "charge flash frame at tick " + count);
        }
    }

    private static EggRoboBadnikInstance onScreenReleaser() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        var services = TestEnvironment.objectServices();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        // Object $A0, subtype 4: the low nibble selects the animal releaser (loc_918FC).
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 4, 0, false, 0));
        robo.setServices(services);
        services.objectManager().addDynamicObject(robo);
        return robo;
    }
}
