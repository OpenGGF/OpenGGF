package com.openggf.graphics;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.camera.Camera;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameServices;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertSame;

public class TestGraphicsManagerFadeRebinding {

    @BeforeEach
    public void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
    }

    @AfterEach
    public void tearDown() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        GraphicsManager graphicsManager = EngineContext.fromLegacySingletonsForBootstrap().graphics();
        graphicsManager.resetState();
        setPrivateField(graphicsManager, "uiRenderPipeline", null);
    }

    @Test
    public void testGetFadeManagerRebindsRuntimeManagedReferences() throws Exception {
        SessionManager.clear();
        GraphicsManager graphicsManager = EngineContext.fromLegacySingletonsForBootstrap().graphics();
        graphicsManager.resetState();

        FadeManager bootstrapFade = graphicsManager.getFadeManager();
        Camera bootstrapCamera = (Camera) getPrivateField(graphicsManager, "camera");
        setPrivateField(graphicsManager, "camera", bootstrapCamera);
        UiRenderPipeline pipeline = new UiRenderPipeline(graphicsManager);
        pipeline.setFadeManager(bootstrapFade);
        setPrivateField(graphicsManager, "uiRenderPipeline", pipeline);

        TestEnvironment.activeGameplayMode();
        FadeManager runtimeFade = GameServices.fade();
        Camera runtimeCamera = GameServices.camera();

        FadeManager resolvedFade = graphicsManager.getFadeManager();

        assertSame(runtimeFade, resolvedFade, "GraphicsManager should switch to the runtime FadeManager");
        assertSame(runtimeFade, pipeline.getFadeManager(), "UiRenderPipeline should also use the runtime FadeManager");
        assertSame(runtimeCamera, getPrivateField(graphicsManager, "camera"), "GraphicsManager should switch to the runtime Camera");
    }

    @Test
    public void testGetUiRenderPipelineRebindsRuntimeManagedReferences() throws Exception {
        SessionManager.clear();
        GraphicsManager graphicsManager = EngineContext.fromLegacySingletonsForBootstrap().graphics();
        graphicsManager.resetState();

        FadeManager bootstrapFade = graphicsManager.getFadeManager();
        UiRenderPipeline pipeline = new UiRenderPipeline(graphicsManager);
        pipeline.setFadeManager(bootstrapFade);
        setPrivateField(graphicsManager, "uiRenderPipeline", pipeline);

        TestEnvironment.activeGameplayMode();
        FadeManager runtimeFade = GameServices.fade();

        UiRenderPipeline resolvedPipeline = graphicsManager.getUiRenderPipeline();

        assertSame(runtimeFade, resolvedPipeline.getFadeManager(),
                "UiRenderPipeline returned directly from GraphicsManager should use the runtime FadeManager");
    }

    @Test
    public void testGetFadeManagerProvidesBootstrapDependenciesBeforeRuntime() throws Exception {
        SessionManager.clear();
        GraphicsManager graphicsManager = EngineContext.fromLegacySingletonsForBootstrap().graphics();
        graphicsManager.resetState();

        FadeManager resolvedFade = graphicsManager.getFadeManager();
        Camera resolvedCamera = (Camera) getPrivateField(graphicsManager, "camera");

        assertSame(resolvedFade, graphicsManager.getFadeManager(), "Pre-game rendering should use the bootstrap FadeManager");
        assertSame(resolvedCamera, getPrivateField(graphicsManager, "camera"), "Pre-game rendering should use the bootstrap Camera");
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
