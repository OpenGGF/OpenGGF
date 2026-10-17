package com.openggf.graphics;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestWhiteFadePresentationBinding {
    @AfterEach
    void reset() {
        TestEnvironment.resetAll();
    }

    @Test
    void compositionPinsWhiteFadeSemanticsAcrossAmbientWorldChangesAndReset() {
        var mode = SessionManager.openGameplaySession(new Sonic3kGameModule());
        GameplaySessionFactory.attachManagers(mode, EngineServices.current());
        var graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        var fade = mode.getFadeManager();
        graphics.bindRuntimeManagedReferences(mode.getCamera(), fade);

        // Another world's selection must not change an already composed owner.
        SessionManager.openGameplaySession(new Sonic2GameModule());
        for (int replay = 0; replay < 2; replay++) {
            fade.resetState();
            fade.startFadeFromWhite(null);
            assertEquals(PaletteFadePresentation.Mode.FROM_WHITE,
                    graphics.getPaletteFadePresentation().mode());
            for (int frame = 1; frame <= 4; frame++) {
                fade.update();
                assertEquals((frame + 2) / 3, graphics.getPaletteFadePresentation().steps(),
                        "injected three-V-int period survives reset and ambient world change");
            }
            var snapshot = fade.capture();
            for (int frame = 0; frame < 3; frame++) fade.update();
            assertEquals(3, graphics.getPaletteFadePresentation().steps());
            fade.restore(snapshot);
            assertEquals(2, graphics.getPaletteFadePresentation().steps(),
                    "restore republishes the bound owner's palette despite the ambient S2 world");
        }
    }

    @Test
    void detachedUiFadeKeepsOverlayEvenWhileAnS3kWorldExists() {
        SessionManager.openGameplaySession(new Sonic3kGameModule());
        var graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        var fade = new FadeManager();
        graphics.bindRuntimeManagedReferences(new com.openggf.camera.Camera(), fade);
        fade.startFadeFromWhite(null);
        fade.update();
        assertEquals(PaletteFadePresentation.Mode.NONE, graphics.getPaletteFadePresentation().mode());
    }
}
