package com.openggf.game.sonic1.audio;

import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmSourceProgramTiming;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kYmServiceTimingProfile;
import com.openggf.game.sonic1.audio.smps.Sonic1SmpsLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1YmServiceTimingProfile {

    @Test
    void sourceProfileIsExactAndInstalledAfterDriverIntegration() {
        assertSame(Sonic1YmServiceTimingProfile.PROFILE,
                Sonic1SmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(YmServiceTimingProfile.none(),
                Sonic2SmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(Sonic3kYmServiceTimingProfile.PROFILE,
                Sonic3kSmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());

        assertEquals(4_096,
                Sonic1YmServiceTimingProfile.PROFILE.maximumWritesPerDriverService());
        assertEquals(YmServiceTimingProfile.TimingOwnership.EXCLUSIVE_SFX_FM5,
                Sonic1YmServiceTimingProfile.PROFILE.timingOwnership());
        assertEquals(30, Sonic1YmServiceTimingProfile.PROFILE.requireProgram(
                YmSourceProgramTiming.FirstPathShape.VOICE_NOTE, 0b1010).writes().size());
        assertEquals(31, Sonic1YmServiceTimingProfile.PROFILE.requireProgram(
                YmSourceProgramTiming.FirstPathShape.VOICE_PAN_NOTE, 0b1010).writes().size());
        assertThrows(IllegalArgumentException.class, () ->
                Sonic1YmServiceTimingProfile.PROFILE.requireProgram(
                        YmSourceProgramTiming.FirstPathShape.VOICE_NOTE, 0b1000));
    }

    @Test
    void retailSfxCensusPinsEveryEligibleSourceProgramOwner() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        Set<Integer> eligible = new LinkedHashSet<>();
        for (int id = 0xA0; id <= 0xD0; id++) {
            SmpsDriver driver = new SmpsDriver(44_100);
            SmpsSequencer sfx = new SmpsSequencer(loader.loadSfx(id),
                    loader.loadDacData(), driver, () -> {},
                    Sonic1SmpsSequencerConfig.CONFIG);
            driver.addSequencer(sfx, true);
            try {
                advanceWithoutRendering(driver,
                        sfx.getSamplesUntilNextDriverService());
            } catch (RuntimeException failure) {
                throw new AssertionError("SFX 0x" + Integer.toHexString(id),
                        failure);
            }
            var pending = driver.captureSnapshot().synthSnapshot()
                    .ymWriteTimeline().pending();
            if (pending.stream().map(entry -> entry.dueMasterCycle())
                    .distinct().count() > 1) {
                eligible.add(id);
            }
        }
        assertEquals(Set.of(0xA1, 0xB5, 0xBA, 0xBB, 0xC1, 0xC2, 0xCF),
                eligible);
    }

    private static void advanceWithoutRendering(SmpsDriver driver, int frames) {
        try {
            Method advance = SmpsDriver.class.getDeclaredMethod(
                    "advanceSequencersBatch", int.class);
            advance.setAccessible(true);
            advance.invoke(driver, frames);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
