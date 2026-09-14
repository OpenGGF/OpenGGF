package com.openggf.game.sonic2.kis2;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic2.objects.SpecialStageResultsScreenObjectInstance;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestKis2ResultsMessageHandoff {
    @Test void productionLoaderSelectsMessageLifecycleAtTheZeroTallyBoundary() {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        var rom = TestEnvironment.currentRom();
        GameServices.module().createGame(rom);
        var space = LockOnAddressSpace.tierTwo(dump.window(0,0x200000), dump.window(0x200000,0x100000),dump);
        var loader = new Kis2SpecialStageDataLoader(rom, space);
        var manager = mock(Sonic2SpecialStageManager.class);
        when(manager.getDataLoader()).thenReturn(loader);
        var services = new TestObjectServices() {
            @Override public <T> T gameService(Class<T> type) {
                return type == Sonic2SpecialStageManager.class ? type.cast(manager)
                        : GameServices.module().getGameService(type);
            }
        }.withGameModule(GameServices.module()).withGameState(new GameStateManager());
        var screen = new SpecialStageResultsScreenObjectInstance(0,0,true,6,7,services);
        int frame = 0;
        while (screen.getEmeraldBonus() > 0 && frame < 1000) screen.update(++frame,null);
        assertEquals(0, screen.getEmeraldBonus());
        screen.update(++frame,null); // zero tally pass sets routine $30 immediately
        for (int pass = 1; pass < 210; pass++) {
            screen.update(++frame,null);
            assertFalse(screen.isComplete(), "premature completion at message pass " + pass);
        }
        screen.update(++frame,null);
        assertTrue(screen.isComplete(), "9 out + init +18 in + latch +180hold + DisplayOnly");
    }
}
