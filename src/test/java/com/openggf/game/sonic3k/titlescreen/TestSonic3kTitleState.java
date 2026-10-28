package com.openggf.game.sonic3k.titlescreen;

import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic3kTitleState {
    @AfterEach void cleanup() { SessionManager.clear(); }

    @Test void coldStateIsZeroAndWarmCaptureIsAValue() {
        var state = new Sonic3kTitleState();
        assertEquals(0, state.option());
        state.setOption(0x101);
        var warm = state.capture();
        state.apply(new Sonic3kTitleState.Snapshot(0));
        assertEquals(0, state.option());
        state.apply(warm);
        assertEquals(1, state.option());
        state.setOption(0xFF);
        state.enterStandaloneMenu();
        assertEquals(1, state.option());
    }

    @Test void titleResetRetainsItsModuleOwnedNativeSelection() {
        TestEnvironment.resetAll();
        var title = new Sonic3kTitleScreenManager();
        title.nativeTitleState().setOption(1);
        var owner = title.nativeTitleState();
        title.reset();
        assertSame(owner, title.nativeTitleState());
        assertEquals(1, owner.option());
        assertEquals(0, new Sonic3kTitleScreenManager().nativeTitleState().option(),
                "a new module's title must not inherit an old selection");
    }
}
