package com.openggf.game.soniccd.ending;

import com.openggf.game.EndingPhase;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.fmv.CdFmvNativeAccess;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ending provider runs the program to its return: the video and credits, then the image, then finished. */
class TestCdEndingProvider {
    @Test
    void runsTheEndingThroughItsPhasesToTheTitle() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNativeAccess.usaCue())) {
            CdSceneCoordinator coordinator = new CdSceneCoordinator(() -> disc);
            CdEndingProvider provider = new CdEndingProvider(coordinator);
            assertTrue(provider.ownsEntryPresentation(), "the program fades itself");
            provider.initialize();
            List<String> phases = new ArrayList<>();
            int frames = 0;
            for (; frames < 20000 && !provider.isComplete(); frames++) {
                String phase = provider.getCurrentPhase() + "";
                if (phases.isEmpty() || !phases.getLast().startsWith(phase)) phases.add(phase + "@" + frames);
                provider.update();
            }
            phases.add(provider.getCurrentPhase() + "@" + frames);
            System.out.println("CD ending provider: " + phases + " in " + frames + " frames");
            assertTrue(provider.isComplete());
            assertEquals(EndingPhase.FINISHED, provider.getCurrentPhase());
            assertEquals(3, phases.size(), "CUTSCENE, POST_CREDITS, FINISHED: " + phases);
            assertEquals(15668, frames, "the program's length (TestCdEndingNative)");
        }
    }
}
