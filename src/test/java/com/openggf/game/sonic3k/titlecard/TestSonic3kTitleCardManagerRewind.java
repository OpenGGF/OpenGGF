package com.openggf.game.sonic3k.titlecard;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kTitleCardManagerRewind {

    @Test
    void restoreRebindsFourProductionHandlesWithoutResubmission() {
        HardwareTimingService timing = startLevel();
        Sonic3kTitleCardManager title = new Sonic3kTitleCardManager();
        RewindRegistry registry = new RewindRegistry();
        registry.register(timing);
        registry.register(title);

        title.initializeInLevel(0, 1);
        List<?> submitted = timing.pendingHandles();
        assertEquals(4, submitted.size());
        CompositeSnapshot snapshot = registry.capture();

        title.reset();
        timing.resetForMissingSnapshot();
        registry.restore(snapshot);

        assertFalse(title.isComplete());
        assertEquals(0, title.getCurrentZone());
        assertEquals(1, title.getCurrentAct());
        assertEquals(submitted, timing.pendingHandles(),
                "restore must rebind the original fully identified jobs");

        title.update();

        assertEquals(4, timing.pendingHandles().size(),
                "restored title ownership must not submit replacement work");
        assertEquals(4, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
    }

    @Test
    void gameplaySessionRegistersLiveTitleManagerBesideHardwareTiming() {
        startLevel();

        var keys = TestEnvironment.activeGameplayMode()
                .getRewindRegistry().capture().entries().keySet();

        assertTrue(keys.contains(HardwareTimingService.REWIND_KEY));
        assertTrue(keys.contains(Sonic3kTitleCardManager.REWIND_KEY));
    }

    private static HardwareTimingService startLevel() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        EngineServices.configure(
                EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
        HardwareTimingService timing = GameServices.hardwareTiming();
        timing.resetForMissingSnapshot();
        return timing;
    }
}
