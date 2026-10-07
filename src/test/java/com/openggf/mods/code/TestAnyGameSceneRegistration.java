package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.ModSceneFactory;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestAnyGameSceneRegistration {
    private static final ModSceneFactory SCENE = () -> new ModScene() {
        @Override public void enter(SceneContext context) { }
        @Override public void update(SceneContext context) { }
        @Override public void draw(SceneContext context, SceneCanvas canvas) { }
    };

    @Test
    void sharedSceneExpandsToThreeOrdinaryDistinctDecorators() {
        ModContext context = new ModContext("arcade", "any", ModAssetRoot.forTests("arcade"));
        context.registerStartupScene(SCENE);
        ModRegistrationPlan plan = context.freeze();
        List<ModRegistrationPlan> expanded = plan.stockScenePlans();
        assertEquals(List.of("s1", "s2", "s3k"),
                expanded.stream().map(ModRegistrationPlan::baseGameId).toList());
        ModFaultBoundary boundary = org.mockito.Mockito.mock(ModFaultBoundary.class);
        var patches = expanded.stream().map(stock -> new ModBackedGamePatch(stock, boundary,
                (owner, finding) -> { }, "content-" + stock.baseGameId())).toList();
        assertEquals(List.of("arcade:content-s1", "arcade:content-s2", "arcade:content-s3k"),
                patches.stream().map(ModBackedGamePatch::id).toList());
        assertTrue(expanded.stream().allMatch(stock -> stock.startupScene() == SCENE));
    }

    @Test
    void missingSceneAndGameSpecificContentPoisonTheWholeTransaction() {
        ModContext missing = new ModContext("arcade", "any", ModAssetRoot.forTests("arcade"));
        assertThrows(ModRegistrationException.class, missing::freeze);

        ModContext hostile = new ModContext("arcade", "any", ModAssetRoot.forTests("arcade"));
        hostile.registerStartupScene(SCENE);
        hostile.registerObject("foreign", (spawn, registry) -> null);
        ModRegistrationException failure = assertThrows(ModRegistrationException.class, hostile::freeze);
        assertSame(failure, assertThrows(ModRegistrationException.class, hostile::freeze));
    }
}
