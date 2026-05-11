package com.openggf.tests;

import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.dataselect.CrossGameDataSelectPresentations;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2LevelAnimationManager;
import com.openggf.game.sonic3k.Sonic3kLevelAnimationManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectManager;
import com.openggf.game.session.EditorModeContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelRenderer;
import com.openggf.level.objects.ObjectServices;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.CacheMode;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bytecode-level architectural invariants.
 *
 * <p>Source-text hazards that should also fail on comments, unused imports, or
 * string literals stay in {@link TestArchitecturalReviewGuard}.
 */
@AnalyzeClasses(
        packages = "com.openggf",
        cacheMode = CacheMode.FOREVER,
        importOptions = ImportOption.DoNotIncludeTests.class)
class TestArchUnitRules {
    private static final String GAME_SERVICES = GameServices.class.getName();
    private static final String SESSION_MANAGER = SessionManager.class.getName();
    private static final String ENGINE_SERVICES = EngineServices.class.getName();
    private static final String WORLD_SESSION = WorldSession.class.getName();
    private static final String GAMEPLAY_MODE_CONTEXT = GameplayModeContext.class.getName();
    private static final String EDITOR_MODE_CONTEXT = EditorModeContext.class.getName();
    private static final String ZONE_RUNTIME_REGISTRY = ZoneRuntimeRegistry.class.getName();
    private static final String PALETTE_OWNERSHIP_REGISTRY = PaletteOwnershipRegistry.class.getName();
    private static final String ANIMATED_TILE_CHANNEL_GRAPH = AnimatedTileChannelGraph.class.getName();
    private static final String ZONE_LAYOUT_MUTATION_PIPELINE = ZoneLayoutMutationPipeline.class.getName();
    private static final String SPECIAL_RENDER_EFFECT_REGISTRY = SpecialRenderEffectRegistry.class.getName();
    private static final String ADVANCED_RENDER_MODE_CONTROLLER = AdvancedRenderModeController.class.getName();
    private static final String REWIND_REGISTRY = RewindRegistry.class.getName();

    private static DescribedPredicate<JavaConstructorCall> targetOwnerNamed(String... fullyQualifiedNames) {
        return new DescribedPredicate<>("target owner named " + String.join(", ", fullyQualifiedNames)) {
            @Override
            public boolean test(JavaConstructorCall input) {
                String targetOwnerName = input.getTargetOwner().getName();
                for (String fullyQualifiedName : fullyQualifiedNames) {
                    if (targetOwnerName.equals(fullyQualifiedName)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private static DescribedPredicate<JavaConstructorCall> targetIsConcreteSonicProvider() {
        return new DescribedPredicate<>(
                "target is a concrete Sonic module, detector, profile, art, or object provider class") {
            @Override
            public boolean test(JavaConstructorCall input) {
                String packageName = input.getTargetOwner().getPackageName();
                if (!packageName.startsWith("com.openggf.game.sonic1")
                        && !packageName.startsWith("com.openggf.game.sonic2")
                        && !packageName.startsWith("com.openggf.game.sonic3k")) {
                    return false;
                }
                String simpleName = input.getTargetOwner().getSimpleName();
                return (simpleName.startsWith("Sonic1")
                        || simpleName.startsWith("Sonic2")
                        || simpleName.startsWith("Sonic3k")
                        || simpleName.startsWith("S3k"))
                        && (simpleName.contains("GameModule")
                        || simpleName.contains("RomDetector")
                        || simpleName.contains("Profile")
                        || simpleName.contains("ObjectArt")
                        || simpleName.contains("PlayerArt")
                        || simpleName.contains("DustArt")
                        || simpleName.contains("ObjectRegistry")
                        || simpleName.contains("ObjectPlacement")
                        || simpleName.contains("ObjectInstance"));
            }
        };
    }

    @ArchTest
    static final ArchRule sonic2_level_animation_manager_exposes_rewind_snapshots =
            classes().that().haveFullyQualifiedName(Sonic2LevelAnimationManager.class.getName())
                    .should().beAssignableTo(RewindSnapshottable.class);

    @ArchTest
    static final ArchRule sonic3k_level_animation_manager_exposes_rewind_snapshots =
            classes().that().haveFullyQualifiedName(Sonic3kLevelAnimationManager.class.getName())
                    .should().beAssignableTo(RewindSnapshottable.class);

    @ArchTest
    static final ArchRule object_services_interface_does_not_depend_on_game_services =
            noClasses().that().haveFullyQualifiedName(ObjectServices.class.getName())
                    .should().dependOnClassesThat().haveFullyQualifiedName(GameServices.class.getName());

    @ArchTest
    static final ArchRule checkpoint_state_does_not_depend_on_sonic3k_level_events =
            noClasses().that().haveFullyQualifiedName(CheckpointState.class.getName())
                    .should().dependOnClassesThat().haveFullyQualifiedName(Sonic3kLevelEventManager.class.getName());

    @ArchTest
    static final ArchRule shared_level_layer_does_not_depend_on_sonic3k_zone_constants =
            noClasses().that().haveFullyQualifiedName(LevelManager.class.getName())
                    .or().haveFullyQualifiedName(LevelRenderer.class.getName())
                    .should().dependOnClassesThat().haveFullyQualifiedName(Sonic3kZoneIds.class.getName());

    @ArchTest
    static final ArchRule sonic_one_and_two_modules_do_not_depend_on_s3k_data_select_manager =
            noClasses().that().haveFullyQualifiedName(Sonic1GameModule.class.getName())
                    .or().haveFullyQualifiedName(Sonic2GameModule.class.getName())
                    .should().dependOnClassesThat().haveFullyQualifiedName(S3kDataSelectManager.class.getName());

    @ArchTest
    static final ArchRule shared_data_select_layer_does_not_depend_on_sonic3k_delegates =
            noClasses().that().haveFullyQualifiedName(CrossGameDataSelectPresentations.class.getName())
                    .should().dependOnClassesThat().resideInAPackage("com.openggf.game.sonic3k..");

    @ArchTest
    static final ArchRule session_contexts_are_only_constructed_by_session_composition_roots =
            noClasses().that()
                    .resideOutsideOfPackage("com.openggf.game.session..")
                    .should().callConstructorWhere(targetOwnerNamed(
                            WORLD_SESSION,
                            GAMEPLAY_MODE_CONTEXT,
                            EDITOR_MODE_CONTEXT))
                    .as("WorldSession and mode contexts should only be created by session composition roots")
                    .because("session ownership must stay centralized in SessionManager/session factories");

    @ArchTest
    static final ArchRule world_session_does_not_depend_on_runtime_managers_or_registries =
            noClasses().that().haveFullyQualifiedName(WORLD_SESSION)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.openggf.camera..",
                            "com.openggf.timer..",
                            "com.openggf.graphics..",
                            "com.openggf.physics..",
                            "com.openggf.sprites..",
                            "com.openggf.level.objects..",
                            "com.openggf.level.rings..",
                            "com.openggf.game.zone..",
                            "com.openggf.game.palette..",
                            "com.openggf.game.animation..",
                            "com.openggf.game.mutation..",
                            "com.openggf.game.render..",
                            "com.openggf.game.rewind..")
                    .as("WorldSession should not depend on gameplay runtime managers or runtime-owned registries")
                    .because("WorldSession owns durable world metadata, not disposable gameplay runtime state");

    @ArchTest
    static final ArchRule runtime_owned_framework_packages_do_not_access_global_service_roots =
            FreezingArchRule.freeze(noClasses().that()
                            .resideInAnyPackage(
                                    "com.openggf.game.zone..",
                                    "com.openggf.game.palette..",
                                    "com.openggf.game.animation..",
                                    "com.openggf.game.mutation..",
                                    "com.openggf.game.render..",
                                    "com.openggf.game.rewind..")
                            .should().accessClassesThat().haveFullyQualifiedName(GAME_SERVICES)
                            .orShould().accessClassesThat().haveFullyQualifiedName(SESSION_MANAGER)
                            .orShould().accessClassesThat().haveFullyQualifiedName(ENGINE_SERVICES)
                            .as("runtime-owned framework packages should not access GameServices, SessionManager, or EngineServices directly"))
                    .because("runtime-owned framework code should receive dependencies from GameplayModeContext or explicit collaborators");

    @ArchTest
    static final ArchRule game_services_does_not_depend_on_concrete_game_packages_or_object_implementations =
            noClasses().that().haveFullyQualifiedName(GAME_SERVICES)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.openggf.game.sonic1..",
                            "com.openggf.game.sonic2..",
                            "com.openggf.game.sonic3k..")
                    .as("GameServices should not depend on concrete game packages or object implementations")
                    .because("GameServices is a shared facade and should expose provider contracts only");

    @ArchTest
    static final ArchRule runtime_registry_controllers_are_only_constructed_by_runtime_composition_roots =
            FreezingArchRule.freeze(noClasses().that()
                            .resideOutsideOfPackages(
                                    "com.openggf.game.session..",
                                    "com.openggf.tests..")
                            .should().callConstructorWhere(targetOwnerNamed(
                                    ZONE_RUNTIME_REGISTRY,
                                    PALETTE_OWNERSHIP_REGISTRY,
                                    ANIMATED_TILE_CHANNEL_GRAPH,
                                    ZONE_LAYOUT_MUTATION_PIPELINE,
                                    SPECIAL_RENDER_EFFECT_REGISTRY,
                                    ADVANCED_RENDER_MODE_CONTROLLER,
                                    REWIND_REGISTRY))
                            .as("runtime-owned registries and controllers should only be constructed by runtime composition roots"))
                    .because("GameplayModeContext/session factories own these gameplay-scoped registries; frozen violations are transitional fallbacks");

    @ArchTest
    static final ArchRule runtime_shared_stateful_controllers_expose_rewind_snapshots =
            classes().that().haveFullyQualifiedName(ZONE_RUNTIME_REGISTRY)
                    .or().haveFullyQualifiedName(PALETTE_OWNERSHIP_REGISTRY)
                    .or().haveFullyQualifiedName(ANIMATED_TILE_CHANNEL_GRAPH)
                    .or().haveFullyQualifiedName(ZONE_LAYOUT_MUTATION_PIPELINE)
                    .or().haveFullyQualifiedName(SPECIAL_RENDER_EFFECT_REGISTRY)
                    .or().haveFullyQualifiedName(ADVANCED_RENDER_MODE_CONTROLLER)
                    .should().beAssignableTo(RewindSnapshottable.class)
                    .as("stateful runtime-shared registries and controllers should expose rewind snapshots")
                    .because("gameplay-scoped mutable runtime framework state must participate in rewind capture");

    @ArchTest
    static final ArchRule shared_code_does_not_construct_concrete_sonic_provider_classes =
            FreezingArchRule.freeze(noClasses().that()
                            .resideOutsideOfPackages(
                                    "com.openggf.game.sonic1..",
                                    "com.openggf.game.sonic2..",
                                    "com.openggf.game.sonic3k..",
                                    "com.openggf.game.session..",
                                    "com.openggf.tools..",
                                    "com.openggf.audio.debug..",
                                    "com.openggf.debug..")
                            .and().doNotHaveFullyQualifiedName("com.openggf.Engine")
                            .and().doNotHaveFullyQualifiedName("com.openggf.game.GameModuleRegistry")
                            .and().doNotHaveFullyQualifiedName("com.openggf.game.RomDetectionService")
                            .and().doNotHaveFullyQualifiedName("com.openggf.game.CrossGameFeatureProvider")
                            .should().callConstructorWhere(targetIsConcreteSonicProvider())
                            .as("shared code should not construct concrete Sonic provider/art/object classes"))
                    .because("shared layers should obtain game-specific providers through GameModule or approved composition roots");

    @ArchTest
    static final ArchRule object_packages_do_not_access_global_game_services =
            FreezingArchRule.freeze(noClasses().that()
                            .resideInAnyPackage(
                                    "com.openggf.level.objects..",
                                    "com.openggf.game.sonic1.objects..",
                                    "com.openggf.game.sonic2.objects..",
                                    "com.openggf.game.sonic3k.objects..")
                            .and().doNotHaveFullyQualifiedName("com.openggf.level.objects.AbstractObjectInstance")
                            .and().doNotHaveFullyQualifiedName("com.openggf.level.objects.DefaultObjectServices")
                            .and().doNotHaveFullyQualifiedName("com.openggf.level.objects.BootstrapObjectServices")
                            .and().doNotHaveFullyQualifiedName("com.openggf.level.objects.AbstractObjectRegistry")
                            .and().doNotHaveFullyQualifiedName("com.openggf.game.sonic1.objects.Sonic1ObjectRegistry")
                            .and().doNotHaveFullyQualifiedName("com.openggf.game.sonic2.objects.Sonic2ObjectRegistry")
                            .and().doNotHaveFullyQualifiedName("com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry")
                            .should().accessClassesThat().haveFullyQualifiedName(GameServices.class.getName())
                            .as("object packages should not access global GameServices except approved bridges"))
                    .because("object instances should use injected ObjectServices; frozen violations are migration debt");

    @ArchTest
    static final ArchRule shared_layers_do_not_depend_on_game_specific_packages =
            FreezingArchRule.freeze(noClasses().that()
                            .resideInAnyPackage("com.openggf.level..", "com.openggf.game..")
                            .and().resideOutsideOfPackages(
                                    "com.openggf.game.sonic1..",
                                    "com.openggf.game.sonic2..",
                                    "com.openggf.game.sonic3k..")
                            .should().dependOnClassesThat().resideInAnyPackage(
                                    "com.openggf.game.sonic1..",
                                    "com.openggf.game.sonic2..",
                                    "com.openggf.game.sonic3k..")
                            .as("shared level and game layers should not depend on game-specific packages"))
                    .because("shared layers should depend on provider contracts or shared abstractions, not concrete game packages");

    @ArchTest
    static final ArchRule per_game_packages_do_not_cross_depend =
            FreezingArchRule.freeze(slices().matching("com.openggf.game.(sonic*)..")
                            .should().notDependOnEachOther()
                            .as("per-game packages should not cross-depend"))
                    .because("cross-game donation and asset reuse must be explicit and must not grow accidentally");

    @ArchTest
    static void imported_classes_include_application_code(JavaClasses classes) {
        assertTrue(classes.contain(GameServices.class), "ArchUnit must import application classes");
        classes().that().haveFullyQualifiedName(GameServices.class.getName())
                .should().haveSimpleName("GameServices")
                .allowEmptyShould(false)
                .check(classes);
    }
}
