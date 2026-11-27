package com.openggf.level.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Measurement probe for the NATIVE widescreen dormant bake: wall-clock cost of
 * {@code DormantObjectLayer.prepareAtFrameBoundary} on the three equivalence routes at 32:9,
 * with pose, looped-pose and baked-update counts. Excluded from default runs ({@code *Probe}).
 *
 * <p>Inputs: the S1, S2 and S3K ROMs (found by the queue wrapper). Run with
 * {@code python3 tools/testing/maven_queue.py -Dmse=off -Dtest=DormantBakeTimingProbe test};
 * output is printed only. Origin: 2026-11-26 animated widescreen phantoms (bake placement decision).
 */
class DormantBakeTimingProbe {
    private static final int WARMUP = 2;
    private static final int REPETITIONS = 7;

    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void sonic1Ghz1() throws Exception { measure(SonicGame.SONIC_1); }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void sonic2Ehz1() throws Exception { measure(SonicGame.SONIC_2); }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kAiz1() throws Exception { measure(SonicGame.SONIC_3K); }

    private static void measure(SonicGame game) throws Exception {
        var config = SonicConfigurationService.getInstance();
        Object oldAspect = config.getConfigValue(SonicConfiguration.DISPLAY_ASPECT);
        Object oldMode = config.getConfigValue(SonicConfiguration.WIDESCREEN_DRAW_MODE);
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        SharedLevel level = null;
        try {
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "SUPER_32_9");
            config.setConfigValue(SonicConfiguration.WIDESCREEN_DRAW_MODE, "NATIVE");
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.resolveDisplayAspect();
            level = SharedLevel.load(game, 0, 0);
            HeadlessTestFixture.builder().withSharedLevel(level).build();
            var objects = GameServices.level().getObjectManager();
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            double[] total = new double[REPETITIONS];
            double[] slice = new double[REPETITIONS];
            for (int i = -WARMUP; i < REPETITIONS; i++) {
                objects.dormantLayer.reset();
                double sum = 0, worst = 0;
                do {
                    long start = System.nanoTime();
                    objects.prepareDormantPresentation(registry);
                    double elapsed = (System.nanoTime() - start) / 1e6;
                    sum += elapsed;
                    worst = Math.max(worst, elapsed);
                } while (!objects.dormantLayer.bakeComplete());
                if (i >= 0) {
                    total[i] = sum;
                    slice[i] = worst;
                }
            }
            Arrays.sort(total);
            Arrays.sort(slice);
            System.out.printf("DORMANT-BAKE %s: %s total median=%.2fms max=%.2fms; worst slice median=%.2fms"
                            + " max=%.2fms (n=%d)%n", game, objects.dormantLayer.bakeSummary(),
                    total[REPETITIONS / 2], total[REPETITIONS - 1], slice[REPETITIONS / 2],
                    slice[REPETITIONS - 1], REPETITIONS);
        } finally {
            if (level != null) level.dispose();
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, oldAspect);
            config.setConfigValue(SonicConfiguration.WIDESCREEN_DRAW_MODE, oldMode);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkip);
            config.resolveDisplayAspect();
        }
    }
}
