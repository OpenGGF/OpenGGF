package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** ROM off_4A890 path geometry and sub_4A818 division, independent of the current level clock. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzChainedPlatforms {
    @BeforeEach void reset() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @CsvSource({"0,496", "1,752", "2,1008"})
    void eachRomPathClosesAndReversalChangesItsDirection(int path, int period) throws Exception {
        for (int reversed = 0; reversed <= 1; reversed++) {
            var platform = new LrzChainedPlatformObjectInstance(
                    new ObjectSpawn(0x1000, 0x400, 0x25, path * 16 + 1, reversed, false, 0));
            platform.setServices(new TestObjectServices());
            int direction = reversed == 0 ? -1 : 1;
            platform.update(777, null);
            assertEquals(0x1000 + direction, platform.getX());
            assertEquals(0x401, platform.getY(), "DIVS signed remainder seeds the minor fraction");
            // Native read-only BK2 observation: root $81 at movie frame 422436 and
            // root $82 at 422859 both have y_sub=$73F8 after the first diagonal step.
            var fraction = LrzChainedPlatformObjectInstance.class.getDeclaredField("yFixed");
            fraction.setAccessible(true);
            assertEquals(0x73F8, fraction.getInt(platform) & 0xFFFF);
            for (int i = 1; i < 22; i++) platform.update(777, null);
            assertEquals(0x1000 + direction * 0x16, platform.getX());
            assertEquals(0x40A, platform.getY());
            for (int i = 22; i < 44; i++) platform.update(777, null);
            assertEquals(0x1000 + direction * 0x20, platform.getX());
            assertEquals(0x420, platform.getY());
            for (int i = 44; i < period; i++) platform.update(777, null);
            assertEquals(0x1000, platform.getX());
            assertEquals(0x400, platform.getY());
        }
    }
}
