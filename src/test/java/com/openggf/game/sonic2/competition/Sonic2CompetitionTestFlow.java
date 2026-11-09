package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import static org.junit.jupiter.api.Assertions.*;

/** Drive production continuations before positioned gameplay probes; never skip their waits. */
public final class Sonic2CompetitionTestFlow {
    private Sonic2CompetitionTestFlow() { }

    public static int finishTitle(Sonic2NativeCompetitionHost host, Runnable wait) {
        // Level_TtlCard (s2.asm:4914-4949), Level_FromCheckpoint (s2.asm:4980-5086):
        // load return is not InitPlayers/control release. LFC and HUD time stay frozen.
        int count = 0;
        while (host.titleCardActive()) {
            assertTrue(count++ < 512, "production title continuation must release control");
            assertEquals(0, host.context().getLevelManager().getFrameCounter());
            wait.run();
        }
        assertEquals(0, host.context().getLevelManager().getFrameCounter());
        return count;
    }

    public static void finishMenuFade(Sonic2CompetitionScreen screen) {
        // MenuScreen (s2.asm:11817-11824): one initial wait, then 22 fade passes.
        var neutral = new InputHandler(); neutral.setLogicalOverride(LogicalInputSnapshot.neutral());
        int count = 0;
        while (screen.capture().fadeIn()) {
            assertTrue(count++ < 23, "bounded native menu arrival");
            screen.update(neutral);
        }
        assertEquals(23, count);
    }

    public static void finishDeparture(Sonic2CompetitionScreen screen) {
        // Level (s2.asm:4757-4765) owns Pal_FadeToBlack before act assembly.
        var neutral = new InputHandler(); neutral.setLogicalOverride(LogicalInputSnapshot.neutral());
        int count = 0;
        while (screen.actTransitionPending()) {
            assertTrue(count++ < 22, "bounded native departure fade");
            screen.update(neutral);
        }
        assertEquals(22, count);
    }
}
