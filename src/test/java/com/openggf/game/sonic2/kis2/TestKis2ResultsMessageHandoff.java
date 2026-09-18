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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestKis2ResultsMessageHandoff {
    private SpecialStageResultsScreenObjectInstance screen(boolean gotEmerald, int emeralds) {
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
        }.withGameModule(GameServices.module()).withGameState(new GameStateManager())
                .withRom(rom).withRomManager(GameServices.rom());
        return new SpecialStageResultsScreenObjectInstance(1,0,gotEmerald,6,emeralds,services);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void productionLoaderSelectsMessageLifecycleWithoutRenderingOrAfterLateArtLoad(boolean loadLateArt) throws Exception {
        var screen = screen(true, 7);
        int frame = 0;
        while (screen.getEmeraldBonus() > 0 && frame < 1000) screen.update(++frame,null);
        assertEquals(0, screen.getEmeraldBonus());
        screen.update(++frame,null); // zero tally pass sets routine $30 immediately
        for (int pass = 1; pass < 210; pass++) {
            if (loadLateArt && pass == 25) {
                // Exercise lazy CPU art loading after message movement has started;
                // this must not recreate its clock or require a graphics context.
                var loadArt = SpecialStageResultsScreenObjectInstance.class.getDeclaredMethod("loadArt");
                loadArt.setAccessible(true);
                loadArt.invoke(screen);
            }
            screen.update(++frame,null);
            assertFalse(screen.isComplete(), "premature completion at message pass " + pass);
        }
        screen.update(++frame,null);
        assertTrue(screen.isComplete(), "9 out + init +18 in + latch +180hold + DisplayOnly");
    }

    @Test void nonSuperWaitAdvancesToDisplayOnlyBeforeEndingOnTheNextPass() {
        var screen = screen(false, 0);
        int frame = 0;
        while (screen.getDisplayedRingCount() > 0 && frame < 1000) screen.update(++frame,null);
        assertEquals(0, screen.getDisplayedRingCount());
        screen.update(++frame,null); // exhausted tally pass: timer=$78
        for (int pass = 0; pass < 120; pass++) screen.update(++frame,null);
        assertFalse(screen.isComplete(), "TimedDisplay advances routine but does not set inactive");
        screen.update(++frame,null);
        assertTrue(screen.isComplete());
    }
}
