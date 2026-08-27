package com.openggf.tests;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps the memory-bounded network ArchUnit import complete as packages grow. */
class TestNetIsolationAnalysisScopeGuard {

    private static final Set<String> NETWORK_BOUNDARY_ROOTS = Set.of(
            "com.openggf.net", "com.openggf.tools.net");
    private static final Path PRODUCTION_SOURCES = projectRoot().resolve("src/main/java");

    @Test
    void analysisScopeMatchesEveryNetworkBoundaryPackage() throws IOException {
        AnalyzeClasses analysis = TestNetIsolationRules.class.getAnnotation(AnalyzeClasses.class);
        Set<String> importedRoots = Set.copyOf(Arrays.asList(analysis.packages()));

        assertEquals(NETWORK_BOUNDARY_ROOTS, importedRoots,
                "network rules must import the complete boundary without the whole engine graph");

        Set<String> productionPackages = networkBoundaryPackages();
        assertFalse(productionPackages.isEmpty(), "network boundary source census must not be empty");
        assertTrue(productionPackages.stream().allMatch(packageName -> importedRoots.stream()
                        .anyMatch(root -> packageName.equals(root) || packageName.startsWith(root + "."))),
                () -> "network boundary packages outside the ArchUnit import: " + productionPackages);
    }

    @Test
    void analysisImportOptionSeparatesSessionProductionAndTestOutputs()
            throws ReflectiveOperationException {
        AnalyzeClasses analysis = TestNetIsolationRules.class.getAnnotation(AnalyzeClasses.class);
        assertEquals(1, analysis.importOptions().length,
                "network analysis should have one authoritative output filter");
        ImportOption importOption = analysis.importOptions()[0]
                .getDeclaredConstructor().newInstance();

        Path productionPackage = TestSessionOutputPaths.compiledClasses()
                .resolve("com/openggf/net");
        Path testPackage = TestSessionOutputPaths.compiledTestClasses()
                .resolve("com/openggf/net");
        assertTrue(importOption.includes(Location.of(productionPackage)),
                "network analysis must include session production bytecode");
        assertFalse(importOption.includes(Location.of(testPackage)),
                "network analysis must exclude session test bytecode");
    }

    private static Set<String> networkBoundaryPackages() throws IOException {
        Set<String> packages = new TreeSet<>();
        for (String root : NETWORK_BOUNDARY_ROOTS) {
            Path sourceRoot = PRODUCTION_SOURCES.resolve(root.replace('.', File.separatorChar));
            try (Stream<Path> sources = Files.walk(sourceRoot)) {
                sources.filter(path -> path.toString().endsWith(".java"))
                        .map(Path::getParent)
                        .map(PRODUCTION_SOURCES::relativize)
                        .map(path -> path.toString().replace(File.separatorChar, '.'))
                        .forEach(packages::add);
            }
        }
        return packages;
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("project.basedir",
                System.getProperty("user.dir", ".")));
    }
}
