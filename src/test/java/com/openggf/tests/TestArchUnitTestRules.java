package com.openggf.tests;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Bytecode-level architectural invariants for test classes.
 */
class TestArchUnitTestRules {

    private static final String JUNIT4_ROOT = "org." + "junit";

    @Test
    void tests_do_not_reference_junit4_apis() {
        var testClasses = new ClassFileImporter()
                .importPath(TestSessionOutputPaths.compiledTestClasses());

        ArchRule rule = noClasses().that().resideInAPackage("com.openggf..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        JUNIT4_ROOT,
                        JUNIT4_ROOT + ".experimental..",
                        JUNIT4_ROOT + ".rules..",
                        JUNIT4_ROOT + ".runner..",
                        JUNIT4_ROOT + ".runners..");
        assertNoJUnit4Dependencies(rule, testClasses);
    }

    private static void assertNoJUnit4Dependencies(
            ArchRule rule, JavaClasses testClasses) {
        rule.check(testClasses);
    }
}
