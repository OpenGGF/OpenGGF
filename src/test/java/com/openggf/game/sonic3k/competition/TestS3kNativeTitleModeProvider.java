package com.openggf.game.sonic3k.competition;

import com.openggf.control.InputHandler;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.nativemenu.NativeTitleModeProvider;
import com.openggf.game.session.EngineContext;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestS3kNativeTitleModeProvider {
    @Test void contextualExtensionPreservesLegacyScreenAndThreeArgumentExit() {
        var screen = mock(NativeMenuScreen.class);
        var legacy = new NativeTitleModeProvider() {
            @Override public NativeMenuScreen optionsScreen() { return null; }
            @Override public NativeMenuScreen competitionScreen() { return screen; }
        };
        var context = new NativeMenuHostContext(mock(EngineContext.class), new Sonic3kGameModule(),
                new InputHandler(), ignored -> {});
        assertSame(screen, legacy.competitionScreen(context));
        assertEquals(new NativeMenuScreen.Exit(NativeMenuScreen.Destination.NONE, null, false),
                new S3kNativeTitleModeProvider().singlePlayerTitleExit());
        var selected = new NativeMenuScreen.Exit(NativeMenuScreen.Destination.LEVEL, null, true);
        assertSame(selected, new S3kNativeTitleModeProvider(() -> selected).singlePlayerTitleExit());
        assertNotNull(new Sonic3kGameModule().getGameService(NativeTitleModeProvider.class));
    }

    @Test void aVisitCannotBeConstructedWithIncompleteCapabilities() {
        var engine = mock(EngineContext.class);
        var module = new Sonic3kGameModule();
        var input = new InputHandler();
        assertThrows(NullPointerException.class, () -> new NativeMenuHostContext(null, module, input, ignored -> {}));
        assertThrows(NullPointerException.class, () -> new NativeMenuHostContext(engine, null, input, ignored -> {}));
        assertThrows(NullPointerException.class, () -> new NativeMenuHostContext(engine, module, null, ignored -> {}));
        assertThrows(NullPointerException.class, () -> new NativeMenuHostContext(engine, module, input, null));
    }
}
