package com.openggf.tools.fbzvisual;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@Isolated
class TestHiddenGlCaptureSessionFailureCleanup {

    private EngineContext context;
    private SonicConfigurationService configuration;

    @BeforeEach
    void setUp() {
        context = EngineContext.fromLegacySingletonsForBootstrap();
        EngineServices.configure(context);
        configuration = context.configuration();
        configuration.clearSessionOverrides();
        SessionManager.clear();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        configuration.clearSessionOverrides();
    }

    @Test
    void constructorFailureClearsConfigurationOverrides() {
        FbzVisualCaptureMode mode = FbzVisualCaptureMode.resolve("native-320", 320, 224, 0);

        assertThrows(IllegalStateException.class,
                () -> new HiddenGlCaptureSession(mode, context,
                        (width, height, services) -> {
                            throw new IllegalStateException("GL init failed");
                        }));

        assertFalse(configuration.hasSessionOverride(SonicConfiguration.DISPLAY_ASPECT));
        assertFalse(configuration.hasSessionOverride(SonicConfiguration.SCREEN_WIDTH));
        assertFalse(configuration.hasSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED));
    }

    @Test
    void bootFailureClosesGameplaySessionOpenedByHeadlessBoot() throws Exception {
        FbzVisualCaptureMode mode = FbzVisualCaptureMode.resolve("native-320", 320, 224, 0);
        HeadlessGameBoot boot = mock(HeadlessGameBoot.class);
        GameModule module = mock(GameModule.class);
        doAnswer(invocation -> {
            SessionManager.openGameplaySession(module, module, missingDataSource(), null);
            throw new IOException("level load failed");
        }).when(boot).boot(any(Path.class), eq(4), eq(0), eq(0L));

        try (HiddenGlCaptureSession session = new HiddenGlCaptureSession(
                mode, context, (width, height, services) -> boot)) {
            assertThrows(IOException.class,
                    () -> session.boot(Path.of("failure.gen"), 0, 0L));
            assertNull(SessionManager.getCurrentGameplayMode());
            assertNull(SessionManager.getCurrentWorldSession());
        }
    }

    private static GameDataSource missingDataSource() {
        return new GameDataSource() {
            @Override public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
            @Override public InputStream openAsset(String normalizedPath) {
                return InputStream.nullInputStream();
            }
            @Override public String identity() { return "test:fbz-boot-failure"; }
        };
    }
}
