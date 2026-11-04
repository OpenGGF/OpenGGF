package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelLoadContext;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic3kFreshTitleSelection {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void originalSavedReturnSelectionCannotBecomeFreshIntroDuringDisplayHold() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var manager = GameServices.level();
        manager.setBonusStageReturnCheckpointIndex(0);
        var context = new LevelLoadContext();
        context.setIncludePostLoadAssembly(true);
        context.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        var slots = new Sonic3kFreshTitleCardSst();
        var selected = slots.select(context);
        assertTrue(selected.installing());
        manager.clearBonusStageReturn();
        slots.prepare(selected);
        assertTrue(slots.isInstalling(), "resumption uses the selected saved-return policy");
        slots.finishInstallation();
        assertFalse(slots.isInstalling());
    }
}
