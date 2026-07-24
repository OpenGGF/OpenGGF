package com.openggf;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.control.InputHandler;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class TestGameLoopAudioPresentationModes {
    private GameLoop loop;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        loop = new GameLoop(mock(InputHandler.class));
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void normalOuterFrameIsForward() {
        assertEquals(PresentationMode.FORWARD,
                loop.presentationModeForOuterFrame(false, false));
    }

    @Test
    void modalAndFrameStepOuterFramesAreSilent() {
        assertEquals(PresentationMode.SILENT,
                loop.presentationModeForOuterFrame(true, false));
        assertEquals(PresentationMode.SILENT,
                loop.presentationModeForOuterFrame(false, true));
    }

    @Test
    void ordinaryPauseOuterFrameIsSilent() {
        loop.toggleUserPause();
        assertEquals(PresentationMode.SILENT,
                loop.presentationModeForOuterFrame(false, false));
    }
}
