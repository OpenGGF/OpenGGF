package com.openggf.mods.scene.host;

import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;

import static com.openggf.configuration.SonicConfiguration.MULTIPLAYER_CENSOR_PROFANITY;
import static com.openggf.configuration.SonicConfiguration.MULTIPLAYER_CHAT_DISABLED;
import static org.junit.jupiter.api.Assertions.*;

class TestSceneChatPreferences {
    @TempDir Path directory;

    @Test
    void existingContextImplementationsInheritSafeReadOnlyDefaults() throws Throwable {
        SceneContext context = (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(),
                new Class<?>[] { SceneContext.class }, (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    throw new UnsupportedOperationException(method.getName());
                });
        assertTrue(context.censorMultiplayerProfanity());
        assertFalse(context.multiplayerChatDisabled());
        assertTrue(SceneContext.class.getMethod("censorMultiplayerProfanity").isDefault());
        assertTrue(SceneContext.class.getMethod("multiplayerChatDisabled").isDefault());
    }

    @Test
    void differentModVisitsReadTheSameLiveGlobalPreferencesImmediatelyAfterSave() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        var first = new ModSceneHost(config);
        var second = new ModSceneHost(config);
        var firstScene = new ProbeScene();
        var secondScene = new ProbeScene();
        try {
            open(first, firstScene, "chat-one");
            open(second, secondScene, "chat-two");
            assertDefaults(firstScene.context);
            assertDefaults(secondScene.context);
            var draft = new EngineSettingsDraft(config);
            draft.set(MULTIPLAYER_CENSOR_PROFANITY, "false");
            draft.set(MULTIPLAYER_CHAT_DISABLED, "true");
            assertDefaults(firstScene.context);
            draft.apply();
            for (SceneContext context : new SceneContext[] { firstScene.context, secondScene.context }) {
                assertFalse(context.censorMultiplayerProfanity());
                assertTrue(context.multiplayerChatDisabled());
            }
            first.suspend();
            draft.reset(MULTIPLAYER_CENSOR_PROFANITY);
            draft.reset(MULTIPLAYER_CHAT_DISABLED);
            draft.apply();
            assertDefaults(firstScene.context);
            assertDefaults(secondScene.context);
        } finally {
            first.close();
            second.close();
        }
    }

    private void open(ModSceneHost host, ProbeScene scene, String owner) {
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        host.open(ModContextTestAccess.ownedScene(owner, () -> scene, boundary),
                new SceneServices(null, null, directory, null, () -> { }, () -> { }), 320, 224);
    }

    private static void assertDefaults(SceneContext context) {
        assertTrue(context.censorMultiplayerProfanity());
        assertFalse(context.multiplayerChatDisabled());
    }

    private static final class ProbeScene implements ModScene {
        SceneContext context;
        @Override public void enter(SceneContext context) { this.context = context; }
        @Override public void update(SceneContext context) { }
        @Override public void draw(SceneContext context, SceneCanvas canvas) { }
        @Override public void exit(SceneContext context) { }
    }
}
