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

    private static final String[] MOD_BOUND_RACING_PACKAGES = {
            "com.openggf.net.protocol..", "com.openggf.net.hub..", "com.openggf.net.client..",
            "com.openggf.net.identity..", "com.openggf.net.host.jdk.."};

    @ArchTest
    static final ArchRule MOD_BOUND_RACING_CLASSES_ARE_JDK_ONLY =
            noClasses().that().resideInAnyPackage(MOD_BOUND_RACING_PACKAGES)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.netty..", "org.bouncycastle..", "org.sqlite..",
                            "com.openggf.net.master..")
                    .because("the racing library and in-process JDK host ship inside a "
                            + "validated mod, which cannot carry Netty, Bouncy Castle or SQLite");

    @ArchTest
    static final ArchRule MOD_BOUND_RACING_CLASSES_AVOID_THE_NETTY_HOST =
            noClasses().that().resideInAnyPackage(MOD_BOUND_RACING_PACKAGES)
                    .should().dependOnClassesThat(
                            com.tngtech.archunit.base.DescribedPredicate.describe(
                                    "are Netty room-host adapters",
                                    javaClass -> javaClass.getPackageName()
                                            .equals("com.openggf.net.host")
                                            && !javaClass.getName().startsWith(
                                            "com.openggf.net.host.RaceRoomHost")
                                            && !javaClass.getName().startsWith(
                                            "com.openggf.net.host.HostMasterLink")
                                            && !javaClass.getName().startsWith(
                                            "com.openggf.net.host.ConnectionHygiene")))
                    .because("callers swap transports through RaceRoomHost; only the "
                            + "dedicated server constructs the Netty host");

    @ArchTest
    static final ArchRule NET_LOAD_TOOLS_ARE_HEADLESS =
            noClasses().that().resideInAnyPackage("com.openggf.tools.net..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.lwjgl..")
                    .because("network load tools must run without graphics or input natives");
}
