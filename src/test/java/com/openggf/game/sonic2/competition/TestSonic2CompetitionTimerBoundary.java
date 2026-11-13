package com.openggf.game.sonic2.competition;

import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionTimerBoundary {
    @Test void crossingStopsAfterThisInterruptsIncrementAndNeverTicksAtTheBodyTail() throws Exception {
        var rom = TestEnvironment.currentRom();
        // loc_40F50 increments the frame byte; Obj0D_Main clears the enable byte later.
        assertEquals(0x4A38, rom.read16BitAddr(0x40F58));
        NativeTeleportTestWorld.run(w -> {
            var first = w.session.playerState(0);
            var second = w.session.playerState(1);
            first.setTimerFrames(2487); second.setTimerFrames(5045);
            var crossing = new AbstractObjectInstance(new ObjectSpawn(0,0,0,0,0,false,0), "crossing") {
                @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
                @Override public void update(int clock, com.openggf.game.PlayableEntity player) {
                    w.session.observeGoal(0, 100, 100);
                    w.session.observeGoal(1, 100, 100);
                }
            };
            w.host.context().getObjectManager().addDynamicObjectAtSlot(crossing, 40);
            w.tick();
            assertEquals(2488, first.getTimerFrames(), "native 0:41+28");
            assertEquals(5046, second.getTimerFrames(), "native 1:24+6");
            w.tick();
            assertEquals(2488, first.getTimerFrames());
            assertEquals(5046, second.getTimerFrames());
        });
    }
}
