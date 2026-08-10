package com.openggf;

import com.openggf.game.SpecialStageProvider;

/** Test-only access to comparison pacing without publishing it as part of the mod API. */
public final class GameLoopSpecialStagePacingTestBridge {
    private GameLoopSpecialStagePacingTestBridge() {
    }

    public interface ObservationPacing {
        int passCount();

        void applyPassInput(int index, SpecialStageProvider provider);

        default void afterPass(int index) {
        }
    }

    public static void install(GameLoop loop, ObservationPacing pacing) {
        if (pacing == null) {
            loop.setSpecialStageObservationPacing(null);
            return;
        }
        loop.setSpecialStageObservationPacing(new GameLoop.SpecialStageObservationPacing() {
            @Override
            public int passCount() {
                return pacing.passCount();
            }

            @Override
            public void applyPassInput(int index, SpecialStageProvider provider) {
                pacing.applyPassInput(index, provider);
            }

            @Override
            public void afterPass(int index) {
                pacing.afterPass(index);
            }
        });
    }
}
