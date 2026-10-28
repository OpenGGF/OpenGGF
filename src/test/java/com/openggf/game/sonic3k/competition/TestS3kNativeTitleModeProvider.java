package com.openggf.game.sonic3k.competition;

import com.openggf.control.InputHandler;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.nativemenu.NativeTitleModeProvider;
import com.openggf.game.session.EngineContext;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestS3kNativeTitleModeProvider {
    @Test void lockedOnRuntimePreservesTheBasesSourceAwareGameConstruction() {
        var base = mock(com.openggf.game.GameModule.class);
        when(base.getGameId()).thenReturn(com.openggf.game.GameId.S3K);
        var profile = mock(S3kCompetitionLevelInitProfile.class);
        when(profile.resources()).thenReturn(S3kCompetitionResources.LOCKED_ON);
        var source = mock(com.openggf.game.GameDataSource.class);
        var expected = mock(com.openggf.game.Game.class);
        when(base.createGame(source)).thenReturn(expected);
        var runtime = new S3kCompetitionRuntimeModule(base, profile,
                mock(com.openggf.game.ObjectArtProvider.class), mock(com.openggf.game.ScrollHandlerProvider.class));
        assertSame(expected, runtime.createGame(source));
        verify(base).createGame(source);
        verify(base, never()).createGame(any(com.openggf.data.Rom.class));
        verifyNoInteractions(source);
    }

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
