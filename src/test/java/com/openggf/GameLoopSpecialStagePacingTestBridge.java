package com.openggf;

import com.openggf.game.SpecialStageProvider;
import com.openggf.trace.replay.runs.SpecialStageRecordedPassPacing;

/** Test-only access to comparison pacing without publishing it as part of the mod API. */
public final class GameLoopSpecialStagePacingTestBridge {
    private GameLoopSpecialStagePacingTestBridge() {
    }

    public interface ObservationPacing extends SpecialStageRecordedPassPacing.ObservationPacing {
        @Override
        default void runPass(int index, SpecialStageProvider provider) {
            SpecialStageRecordedPassPacing.ObservationPacing.super.runPass(index, provider);
        }
    }

    public static void install(
            GameLoop loop, SpecialStageRecordedPassPacing.ObservationPacing pacing) {
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
            public void runPass(int index, SpecialStageProvider provider) {
                pacing.runPass(index, provider);
            }

            @Override
            public void afterPass(int index) {
                pacing.afterPass(index);
            }
        });
    }
}
