package com.openggf.tests;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Engine-free dependency fence for the standalone multiplayer network stack. */
@AnalyzeClasses(packages = "com.openggf", importOptions = ImportOption.DoNotIncludeTests.class)
public class TestNetIsolationRules {
    @ArchTest
    static final ArchRule NET_STACK_IS_ENGINE_FREE =
            noClasses().that().resideInAnyPackage(
                            "com.openggf.net.protocol..", "com.openggf.net.hub..",
                            "com.openggf.net.host..", "com.openggf.net.client..",
                            "com.openggf.net.identity..", "com.openggf.net.master..")
                    .should().dependOnClassesThat(
                            com.tngtech.archunit.base.DescribedPredicate.describe(
                                    "are engine classes outside net and the ghost frame codec",
                                    javaClass -> javaClass.getPackageName().startsWith("com.openggf")
                                            && !javaClass.getPackageName().startsWith("com.openggf.net")
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
