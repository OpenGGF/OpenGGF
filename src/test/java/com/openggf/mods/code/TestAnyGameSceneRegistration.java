package com.openggf.mods.code;

import com.openggf.game.patch.GamePatch;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.ModSceneFactory;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class TestAnyGameSceneRegistration {
    private static final ModSceneFactory SCENE = () -> new ModScene() {
        @Override public void enter(SceneContext context) { }
        @Override public void update(SceneContext context) { }
        @Override public void draw(SceneContext context, SceneCanvas canvas) { }
    };

    private static ModContext context(String owner, com.openggf.mods.ModType type) {
        return new ModContext(owner, type == com.openggf.mods.ModType.STANDALONE ? null : "any",
                ModAssetRoot.forTests(owner), null, type, com.openggf.game.save.SavePaths.root());
    }

    @Test
    void onlyGameManifestsCanRegisterStartupScenes() {
        for (var type : com.openggf.mods.ModType.values()) {
            ModContext context = context("arcade", type);
            if (type == com.openggf.mods.ModType.GAME) {
                context.registerStartupScene(SCENE);
                assertSame(SCENE, context.freeze().startupScene());
            } else {
                assertThrows(ModRegistrationException.class, () -> context.registerStartupScene(SCENE));
                assertThrows(ModRegistrationException.class, context::freeze);
            }
        }
    }

    @Test
    void sharedSceneExpandsToFourOrdinaryDistinctDecorators() {
        ModContext context = context("arcade", com.openggf.mods.ModType.GAME);
        context.registerStartupScene(SCENE);
        ModRegistrationPlan plan = context.freeze();
        List<ModRegistrationPlan> expanded = plan.stockScenePlans();
        assertEquals(List.of("s1", "s2", "s3k", "scd"),
                expanded.stream().map(ModRegistrationPlan::baseGameId).toList());
        ModFaultBoundary boundary = org.mockito.Mockito.mock(ModFaultBoundary.class);
        var patches = expanded.stream().map(stock -> new ModBackedGamePatch(stock, boundary,
                (owner, finding) -> { }, "content-" + stock.baseGameId())).toList();
        assertEquals(List.of("arcade:content-s1", "arcade:content-s2", "arcade:content-s3k", "arcade:content-scd"),
                patches.stream().map(ModBackedGamePatch::id).toList());
        assertTrue(expanded.stream().allMatch(stock -> stock.startupScene() == SCENE));
    }

    @Test
    void missingSceneAndPatchesOrAForeignPatchGamePoisonTheWholeTransaction() {
        ModContext missing = new ModContext("arcade", "any", ModAssetRoot.forTests("arcade"));
        assertThrows(ModRegistrationException.class, missing::freeze);

        ModContext hostile = context("arcade", com.openggf.mods.ModType.GAME);
        hostile.registerStartupScene(SCENE);
        ModRegistrationException failure = assertThrows(ModRegistrationException.class,
                () -> hostile.registerGamePatch(patch("any")));
        assertSame(failure, assertThrows(ModRegistrationException.class, hostile::freeze));
    }

    @ParameterizedTest @CsvSource({"scene,false", "scene,true", "title,false", "title,true", "patch,false", "patch,true"})
    void codeRolesShareContentWithOrWithoutMutators(String role, boolean catalogue) {
        ModContext context = context("typing", "scene".equals(role) ? com.openggf.mods.ModType.GAME : com.openggf.mods.ModType.PATCH);
        if ("scene".equals(role)) context.registerStartupScene(SCENE);
        if ("title".equals(role)) context.registerTitleEntry("Shared", SCENE);
        if (catalogue) context.registerMutator(new com.openggf.mods.mutators.MutatorDefinition(
                "gravity", "Gravity", "", com.openggf.mods.mutators.MutatorScope.LIVE,
                com.openggf.mods.mutators.MutatorScope.LIVE, List.of(),
                java.util.Set.of(com.openggf.mods.mutators.MutatorCapability.DRY_SONIC_GRAVITY),
                options -> List.of(new com.openggf.mods.mutators.MutatorPolicy.DrySonicGravity(100))));
        if ("patch".equals(role)) {
            context.registerGamePatch(patch("s1"));
            context.registerGamePatch(patch("s3k"));
        }
        context.registerObject("target", (spawn, registry) -> null);
        context.registerServiceBundle("run", () -> com.openggf.game.GameServiceBundle.builder().build());
        List<ModRegistrationPlan> expanded = context.freeze().stockScenePlans();
        assertEquals(List.of("s1", "s2", "s3k", "scd"),
                expanded.stream().map(ModRegistrationPlan::baseGameId).toList());
        assertEquals("patch".equals(role) ? List.of(List.of("s1"), List.of(), List.of("s3k"), List.of())
                : List.of(List.of(), List.of(), List.of(), List.of()), expanded.stream()
                .map(stock -> stock.explicitPatches().stream().map(GamePatch::baseGameId).toList()).toList());
        assertTrue(expanded.stream().allMatch(stock -> stock.objectFactories().keySet().stream().anyMatch(key -> key.endsWith("target"))
                && stock.serviceBundles().containsKey("run")));
        assertTrue(expanded.stream().allMatch(stock -> stock.mutators().size() == (catalogue ? 1 : 0)));
        assertEquals("title".equals(role), context.titleEntry() != null);
    }

    @Test
    void perGamePatchesAloneNeedNoStartupScene() {
        ModContext context = new ModContext("typing", "any", ModAssetRoot.forTests("typing"));
        context.registerGamePatch(patch("s2"));
        context.registerGamePatch(patch("scd"));
        var plan = context.freeze();
        assertFalse(com.openggf.game.patch.ScopedLaunch.contributesTo(plan, "s1"));
        assertTrue(com.openggf.game.patch.ScopedLaunch.contributesTo(plan, "s2"));
        assertFalse(com.openggf.game.patch.ScopedLaunch.contributesTo(plan, "s3k"));
        assertTrue(com.openggf.game.patch.ScopedLaunch.contributesTo(plan, "scd"));
        assertEquals(List.of(0, 1, 0, 1), plan.stockScenePlans().stream()
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
