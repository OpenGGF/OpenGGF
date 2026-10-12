package com.openggf.tests;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Shared networking can be used by any mod without importing engine gameplay owners. */
@AnalyzeClasses(packages = "com.openggf.net",
        importOptions = TestNetIsolationRules.ProductionClassesOnly.class)
public class TestNetIsolationRules {
    public static final class ProductionClassesOnly implements ImportOption {
        private final Path productionClasses = TestSessionOutputPaths.compiledClasses()
                .toAbsolutePath().normalize();

        @Override
        public boolean includes(Location location) {
            return "file".equalsIgnoreCase(location.asURI().getScheme())
                    && Path.of(location.asURI()).toAbsolutePath().normalize().startsWith(productionClasses);
        }
    }

    @ArchTest
    static final ArchRule NET_STACK_IS_ENGINE_FREE = noClasses()
            .that().resideInAPackage("com.openggf.net..")
            .should().dependOnClassesThat(com.tngtech.archunit.base.DescribedPredicate.describe(
                    "are gameplay or creator implementation classes",
                    type -> (type.getPackageName().startsWith("com.openggf")
                            && !type.getPackageName().startsWith("com.openggf.net")
                            && !type.getName().equals("com.openggf.game.ModApi"))
                            || type.getPackageName().startsWith("openggf.timeattack")
                            || type.getPackageName().startsWith("openggf.racing.server")))
            .because("transport, identity and room services must remain reusable outside Time Attack");

    @ArchTest
    static final ArchRule CORE_NET_HAS_NO_OPERATOR_OR_GRAPHICS_LIBRARIES = noClasses()
            .that().resideInAPackage("com.openggf.net..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.netty..", "org.sqlite..", "org.bouncycastle..", "org.lwjgl..")
            .because("the shared backend must run without dedicated-server or graphics dependencies");

}
