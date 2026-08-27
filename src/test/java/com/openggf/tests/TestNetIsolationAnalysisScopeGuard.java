package com.openggf.tests;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps the memory-bounded network ArchUnit import complete as packages grow. */
class TestNetIsolationAnalysisScopeGuard {

    private static final Set<String> NETWORK_BOUNDARY_ROOTS = Set.of(
            "com.openggf.net", "com.openggf.tools.net");
    private static final String CLASS_FILE_SUFFIX = ".class";

    @Test
    void analysisScopeMatchesEveryCompiledNetworkBoundaryClass()
            throws IOException, ReflectiveOperationException {
        AnalyzeClasses analysis = TestNetIsolationRules.class.getAnnotation(AnalyzeClasses.class);
        Set<String> importedRoots = Set.copyOf(Arrays.asList(analysis.packages()));

        assertEquals(NETWORK_BOUNDARY_ROOTS, importedRoots,
                "network rules must import the complete boundary without the whole engine graph");

        ImportOption importOption = configuredImportOption(analysis);
        Path productionClasses = TestSessionOutputPaths.compiledClasses()
                .toAbsolutePath().normalize();
        Set<Path> boundaryClassFiles = networkBoundaryClassFiles(productionClasses);
        assertFalse(boundaryClassFiles.isEmpty(),
                "compiled network boundary class census must not be empty");

        List<String> rejectedClasses = boundaryClassFiles.stream()
                .filter(path -> !importOption.includes(Location.of(path)))
                .map(productionClasses::relativize)
                .map(Path::toString)
                .toList();
        assertEquals(List.of(), rejectedClasses,
                "the production-only filter must accept every compiled boundary class");

        Set<String> compiledClassNames = classNames(productionClasses, boundaryClassFiles);
        Set<String> analyzedClassNames = new ClassFileImporter()
                .withImportOption(importOption)
                .importPackages(analysis.packages()).stream()
                .map(javaClass -> javaClass.getName())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(compiledClassNames, analyzedClassNames,
                "the bounded ArchUnit import must analyze every compiled boundary class exactly once");
    }

    @Test
    void analysisImportOptionRejectsActualNetworkTestClasses()
            throws IOException, ReflectiveOperationException {
        AnalyzeClasses analysis = TestNetIsolationRules.class.getAnnotation(AnalyzeClasses.class);
        ImportOption importOption = configuredImportOption(analysis);
        Path testClasses = TestSessionOutputPaths.compiledTestClasses()
                .toAbsolutePath().normalize();
        Set<Path> relevantTestClassFiles = networkBoundaryClassFiles(testClasses);
        addIsolationGuardClassFiles(testClasses, relevantTestClassFiles);

        assertTrue(relevantTestClassFiles.stream()
                        .map(path -> className(testClasses, path))
                        .anyMatch(TestNetIsolationAnalysisScopeGuard.class.getName()::equals),
                "test census must include this meta-guard");
        assertTrue(relevantTestClassFiles.stream()
                        .map(path -> className(testClasses, path))
                        .anyMatch(TestNetIsolationRules.class.getName()::equals),
                "test census must include the network ArchUnit rule class");

        List<String> acceptedTestClasses = relevantTestClassFiles.stream()
                .filter(path -> importOption.includes(Location.of(path)))
                .map(testClasses::relativize)
                .map(Path::toString)
                .toList();
        assertEquals(List.of(), acceptedTestClasses,
                "the production-only filter must reject actual network and guard test classes");
    }

    private static ImportOption configuredImportOption(AnalyzeClasses analysis)
            throws ReflectiveOperationException {
        assertEquals(1, analysis.importOptions().length,
                "network analysis should have one authoritative output filter");
        return analysis.importOptions()[0].getDeclaredConstructor().newInstance();
    }

    private static Set<Path> networkBoundaryClassFiles(Path outputRoot) throws IOException {
        Set<Path> classFiles = new TreeSet<>();
        for (String root : NETWORK_BOUNDARY_ROOTS) {
            Path boundaryRoot = outputRoot.resolve(root.replace('.', File.separatorChar));
            try (Stream<Path> paths = Files.walk(boundaryRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(CLASS_FILE_SUFFIX))
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .forEach(classFiles::add);
            }
        }
        return classFiles;
    }

    private static void addIsolationGuardClassFiles(Path testClasses, Set<Path> classFiles)
            throws IOException {
        Path testsPackage = testClasses.resolve("com/openggf/tests");
        try (Stream<Path> paths = Files.list(testsPackage)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("TestNetIsolation"))
                    .filter(path -> path.getFileName().toString().endsWith(CLASS_FILE_SUFFIX))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .forEach(classFiles::add);
        }
    }

    private static Set<String> classNames(Path outputRoot, Set<Path> classFiles) {
        return classFiles.stream()
                .map(path -> className(outputRoot, path))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String className(Path outputRoot, Path classFile) {
        String relative = outputRoot.relativize(classFile).toString();
        return relative.substring(0, relative.length() - CLASS_FILE_SUFFIX.length())
                .replace(File.separatorChar, '.');
    }
}
