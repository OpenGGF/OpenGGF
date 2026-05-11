package com.openggf.tests;

import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.dataselect.CrossGameDataSelectPresentations;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2LevelAnimationManager;
import com.openggf.game.sonic3k.Sonic3kLevelAnimationManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectManager;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelRenderer;
import com.openggf.level.objects.ObjectServices;
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
