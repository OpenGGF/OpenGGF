package com.openggf.mods.code;

import com.openggf.game.patch.GamePatch;
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
    void missingSceneAndPatchesOrAForeignPatchGamePoisonTheWholeTransaction() {
        ModContext missing = new ModContext("arcade", "any", ModAssetRoot.forTests("arcade"));
        assertThrows(ModRegistrationException.class, missing::freeze);

        ModContext hostile = new ModContext("arcade", "any", ModAssetRoot.forTests("arcade"));
        hostile.registerStartupScene(SCENE);
        ModRegistrationException failure = assertThrows(ModRegistrationException.class,
                () -> hostile.registerGamePatch(patch("any")));
        assertSame(failure, assertThrows(ModRegistrationException.class, hostile::freeze));
    }

    @Test
    void perGamePatchesExpandOnlyIntoTheirOwnGameWhileObjectsAndServicesAreShared() {
        ModContext context = new ModContext("typing", "any", ModAssetRoot.forTests("typing"));
        context.registerStartupScene(SCENE);
        GamePatch s1 = patch("s1");
        GamePatch s3k = patch("s3k");
        context.registerGamePatch(s1);
        context.registerGamePatch(s3k);
        context.registerObject("target", (spawn, registry) -> null);
        context.registerServiceBundle("run", () -> com.openggf.game.GameServiceBundle.builder().build());
        List<ModRegistrationPlan> expanded = context.freeze().stockScenePlans();
        assertEquals(List.of("s1", "s2", "s3k"),
                expanded.stream().map(ModRegistrationPlan::baseGameId).toList());
        assertEquals(List.of(List.of("s1"), List.of(), List.of("s3k")), expanded.stream()
                .map(stock -> stock.explicitPatches().stream().map(GamePatch::baseGameId).toList()).toList());
        assertTrue(expanded.stream().allMatch(stock -> stock.objectFactories().keySet().stream().anyMatch(key -> key.endsWith("target"))
                && stock.serviceBundles().containsKey("run")));
    }

    @Test
    void perGamePatchesAloneNeedNoStartupScene() {
        ModContext context = new ModContext("typing", "any", ModAssetRoot.forTests("typing"));
        context.registerGamePatch(patch("s2"));
        assertEquals(List.of(0, 1, 0), context.freeze().stockScenePlans().stream()
                .map(stock -> stock.explicitPatches().size()).toList());
    }

    private static GamePatch patch(String game) {
        return new GamePatch() {
            @Override public String id() { return "rails-" + game; }
            @Override public String displayName() { return "Rails " + game; }
            @Override public String baseGameId() { return game; }
            @Override public boolean activatesFor(com.openggf.game.patch.GameplayLaunchRequest request) { return true; }
            @Override public java.util.Set<com.openggf.game.patch.LogicalRom> romPrerequisites() { return java.util.Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public com.openggf.game.GameModule apply(com.openggf.game.GameModule base,
                    com.openggf.game.patch.PatchContext context) { return base; }
        };
    }
}
