package com.openggf.tests;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Engine-free dependency fence for the standalone multiplayer network stack. */
@AnalyzeClasses(
        packages = {"com.openggf.net", "com.openggf.tools.net"},
        importOptions = TestNetIsolationRules.ProductionClassesOnly.class)
public class TestNetIsolationRules {

    /**
     * ArchUnit's Maven test filter only recognizes {@code target/test-classes};
     * test sessions use a session-owned build root, so fence the import by its
     * authoritative production output path instead.
     */
    public static final class ProductionClassesOnly implements ImportOption {
        private final Path productionClasses = TestSessionOutputPaths.compiledClasses()
                .toAbsolutePath().normalize();

        @Override
        public boolean includes(Location location) {
            return "file".equalsIgnoreCase(location.asURI().getScheme())
                    && Path.of(location.asURI()).toAbsolutePath().normalize()
                    .startsWith(productionClasses);
        }
    }

    @ArchTest
    static final ArchRule NET_STACK_IS_ENGINE_FREE =
            noClasses().that().resideInAPackage("com.openggf.net..")
                    .should().dependOnClassesThat(
                            com.tngtech.archunit.base.DescribedPredicate.describe(
                                    "are engine classes outside net and the ghost frame codec",
                                    javaClass -> javaClass.getPackageName().startsWith("com.openggf")
                                            && !javaClass.getPackageName().startsWith("com.openggf.net")
                                            // Type-only compatibility metadata is not a runtime engine edge.
                                            && !javaClass.getName().equals(
                                            "com.openggf.game.ModApi")
                                            && !javaClass.getName().equals(
                                            "com.openggf.ghost.GhostFrame")
                                            && !javaClass.getName().equals(
                                            "com.openggf.ghost.GhostFrameCodec")))
                    .because("the standalone master and room transports must stay engine-free");

    @ArchTest
    static final ArchRule NET_LOAD_TOOLS_ARE_HEADLESS =
            noClasses().that().resideInAnyPackage("com.openggf.tools.net..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.lwjgl..")
                    .because("network load tools must run without graphics or input natives");
}
