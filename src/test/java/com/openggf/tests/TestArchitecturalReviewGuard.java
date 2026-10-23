package com.openggf.tests;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.TextFileBasedViolationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Source-text architectural invariants that are intentionally not bytecode
 * ArchUnit rules.
 *
 * <p>Source paths resolve against the project root supplied by the surefire
 * system property {@code project.basedir} (with a fallback to the JVM working
 * directory) so the checks work from any IDE or Maven invocation.
 *
 * <p>The checks are substring-based, which means a comment or string literal
 * containing a forbidden identifier will also fail the test. That is intentional:
 * comments referencing the forbidden type are a hazard for future maintainers
 * and should be paraphrased rather than left as a quote.
 */
class TestArchitecturalReviewGuard {

    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final Pattern DOCUMENTED_BASELINE_COUNT = Pattern.compile(
            "^- `([^`]+)`: (\\d+)\\s*$", Pattern.MULTILINE);
    private static final Pattern DOCUMENTED_CYCLE_CLUSTER = Pattern.compile("`cycle:([a-z0-9_-]+)`");
    private static final Map<String, ArchRule> DOCUMENTED_FROZEN_RULES = Map.of(
            "low_level_layers_do_not_depend_on_runtime_layers",
            TestArchUnitRules.low_level_layers_do_not_depend_on_runtime_layers,
            "shared_layers_do_not_depend_on_game_specific_packages",
            TestArchUnitRules.shared_layers_do_not_depend_on_game_specific_packages,
            "per_game_packages_do_not_cross_depend",
            TestArchUnitRules.per_game_packages_do_not_cross_depend,
            "runtime_owned_framework_packages_do_not_access_global_service_roots",
            TestArchUnitRules.runtime_owned_framework_packages_do_not_access_global_service_roots,
            "runtime_registry_controllers_are_only_constructed_by_runtime_composition_roots",
            TestArchUnitRules.runtime_registry_controllers_are_only_constructed_by_runtime_composition_roots);

    private static Path resolveProjectRoot() {
        String basedir = System.getProperty("project.basedir");
        if (basedir != null && !basedir.isEmpty()) {
            return Paths.get(basedir);
        }
        return Paths.get(System.getProperty("user.dir", "."));
    }

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }

    @Test
    void pomDoesNotKeepJUnit4OrVintageOnTestClasspath() throws IOException {
        String pom = readSource("pom.xml");

        assertFalse(pom.contains("<groupId>junit</groupId>"));
        assertFalse(pom.contains("junit-vintage-engine"));
    }

    @Test
    void archUnitFrozenRulesDoNotAutoUpdateDuringNormalRuns() throws IOException {
        String properties = readSource("src/test/resources/archunit.properties");

        assertTrue(properties.contains("freeze.store.default.allowStoreUpdate=false"),
                "Frozen ArchUnit baselines must not auto-update in normal test runs");
    }

    @Test
    void archUnitPublishedBaselineCountsMatchFrozenStore() throws IOException {
        Map<String, Integer> documentedCounts = documentedBaselineCounts();
        Map<String, Integer> frozenCounts = frozenBaselineCounts();

        assertEquals(frozenCounts, documentedCounts,
                "Published allowances must count store records, not historical description labels or physical lines");
    }

    @Test
    void frozenRecordCountsIgnoreHistoricalDescriptionLabels(@TempDir Path storeDirectory) throws IOException {
        ArchRule rule = noClasses().should().bePublic()
                .because("historical count; frozen baseline: 97 violations");
        Properties index = new Properties();
        index.setProperty(rule.getDescription(), "violations");
        try (var output = Files.newOutputStream(storeDirectory.resolve("stored.rules"))) {
            index.store(output, "Accounting fixture with a stale label");
        }
        Files.writeString(storeDirectory.resolve("violations"), "one stored record\n");

        assertEquals(Map.of("sample", 1),
                frozenBaselineCounts(storeDirectory, Map.of("sample", rule)));
        RuntimeException updateFailure = assertThrows(RuntimeException.class,
                () -> readOnlyFrozenStore(storeDirectory).save(rule, List.of("replacement")));
        assertTrue(updateFailure.getMessage().contains("Updating frozen violations is disabled"));
        assertEquals("one stored record\n", Files.readString(storeDirectory.resolve("violations")));
    }

    @Test
    void frozenRecordCountsKeepEscapedMultilineRecordsGrouped(@TempDir Path storeDirectory) throws IOException {
        ArchRule firstRule = noClasses().should().bePublic().as("sample rule")
                .because("first reason; frozen baseline: 97 violations");
        ArchRule secondRule = noClasses().should().bePublic().as("sample rule")
                .because("different reason; frozen baseline: 83 violations");
        Properties index = new Properties();
        index.setProperty(firstRule.getDescription(), "grouped");
        index.setProperty(secondRule.getDescription(), "single");
        try (var output = Files.newOutputStream(storeDirectory.resolve("stored.rules"))) {
            index.store(output, "Accounting fixture with full description keys");
        }
        Files.writeString(storeDirectory.resolve("grouped"),
                "Slice first depends on Slice second:\\\r\n"
                        + "Field <first.field> has type <second.Type>\\\r\n"
                        + "Method <first.call()> calls method <second.call()>\r\n"
                        + "\r\n"
                        + "Slice second depends on Slice third:\\\r\n"
                        + "Constructor <second.Type()> calls constructor <third.Type()>");
        Files.writeString(storeDirectory.resolve("single"), "one stored record\n");

        assertEquals(Map.of("grouped", 2, "single", 1), frozenBaselineCounts(storeDirectory,
                Map.of("grouped", firstRule, "single", secondRule)));
        List<String> groupedRecords = readOnlyFrozenStore(storeDirectory).getViolations(firstRule);
        assertEquals(List.of(
                "Slice first depends on Slice second:\n"
                        + "Field <first.field> has type <second.Type>\n"
                        + "Method <first.call()> calls method <second.call()>",
                "Slice second depends on Slice third:\n"
                        + "Constructor <second.Type()> calls constructor <third.Type()>"), groupedRecords);
        assertEquals(3, sliceMemberDependencyCount(groupedRecords),
                "Slice headers are grouping metadata, not member dependencies");
        ArchRule unknownReason = firstRule.as("sample rule").because("unregistered reason");
        assertThrows(IllegalArgumentException.class,
                () -> frozenBaselineCounts(storeDirectory, Map.of("missing", unknownReason)),
                "A shared description prefix must not select a different .because key");
    }

    @Test
    void archUnitDocumentedCrossGameMemberCountMatchesGroupedRecords() throws IOException {
        Matcher documented = Pattern.compile(
                "\\| `per_game_packages_do_not_cross_depend` \\| (\\d+) member dependencies in")
                .matcher(readSource("docs/architecture/archunit-exceptions.md"));
        assertTrue(documented.find(), "Cross-game decay accounting must name member-dependency units");
        List<String> records = readOnlyFrozenStore(frozenStoreDirectory())
                .getViolations(TestArchUnitRules.per_game_packages_do_not_cross_depend);

        assertEquals(sliceMemberDependencyCount(records), Long.parseLong(documented.group(1)),
                "Cross-game member counts must exclude slice headers and count every stored detail");
    }

    @Test
    void archUnitDocumentedPackagePairPermissionsMatchExecutableAllowances()
            throws IOException, ReflectiveOperationException {
        Matcher documented = Pattern.compile(
                "`core_runtime_cycle_cluster_does_not_gain_top_level_edges`: (\\d+) allowed\\s+"
                        + "source/target top-level package pairs")
                .matcher(readSource("docs/architecture/archunit-exceptions.md"));
        assertTrue(documented.find(), "Publish package-pair permissions separately from measured live edges");
        var field = TestArchUnitRules.class.getDeclaredField("CORE_RUNTIME_TOP_LEVEL_DEPENDENCY_EDGES");
        field.setAccessible(true);
        Set<?> allowedPairs = (Set<?>) field.get(null);

        assertEquals(allowedPairs.size(), Integer.parseInt(documented.group(1)),
                "Document the executable permission set without evaluating or pruning live dependencies");
    }

    @Test
    void archUnitCycleClusterDocumentationHasMatchingRatchets() throws IOException {
        String docs = readSource("docs/architecture/archunit-exceptions.md");
        String archUnitRules = readSource("src/test/java/com/openggf/tests/TestArchUnitRules.java");
        String storedRules = readSource("src/test/resources/archunit/frozen/stored.rules");
        Set<String> documentedClusters = documentedCycleClusters(docs);

        int cycleRecords = readOnlyFrozenStore(frozenStoreDirectory())
                .getViolations(TestArchUnitRules.package_slices_are_free_of_cycles).size();
        assertTrue(docs.contains("`package_slices_are_free_of_cycles`: " + cycleRecords + " committed cycle records"),
                "Cycle accounting must reflect stored records after explicit pair ignores");
        assertTrue(docs.contains("`cycle:core-runtime`"),
                "Cycle ratchet docs should name the current core runtime cluster");
        assertFalse(archUnitRules.contains(".ignoreDependency(inCoreRuntimeCycleCluster(), inCoreRuntimeCycleCluster())"),
                "Package cycle ratchets must not ignore every dependency inside a documented cycle cluster");
        assertFalse(archUnitRules.contains("inCoreRuntimeCycleCluster()"),
                "Package cycle ratchets should be frozen against concrete current debt, not a broad cluster predicate");
        assertFalse(archUnitRules.contains("CURRENT_TOP_LEVEL_CYCLE_SLICES"),
                "Package cycle ratchets should not use a broad current-slice allowlist");
        assertFalse(archUnitRules.contains("inCurrentTopLevelCycleSlice"),
                "Package cycle ratchets should use named frozen clusters, not a broad current-slice helper");

        for (String cluster : documentedClusters) {
            assertTrue(archUnitRules.contains(cluster) || storedRules.contains(cluster),
                    "Documented package cycle cluster needs a matching ArchUnit ratchet: " + cluster);
        }
    }

    @Test
    void traceReplayQuarantineIsExplicitAndNotGameSpecific() throws IOException {
        String pom = readSource("pom.xml");

        assertTrue(pom.contains("<id>trace-replay</id>"));
        assertTrue(pom.contains("**/tests/trace/**/*.java"),
                "Default Surefire should quarantine trace replay/regression workloads by directory");
        assertTrue(pom.contains("**/tests/trace/**/*TraceReplay.java"));
        assertTrue(pom.contains("**/tests/trace/**/*ReplayBootstrap.java"));
        assertTrue(pom.contains("**/tests/trace/s2/*Regression.java"));
        assertFalse(pom.contains("**/tests/trace/s1/TestS1"),
                "no game-specific trace exclude should be hand-written outside the standard pattern");
        assertFalse(pom.contains("**/trace/s1/TestS1*TraceReplay.java"));
    }

    private static Map<String, Integer> documentedBaselineCounts() throws IOException {
        String docs = readSource("docs/architecture/archunit-exceptions.md");
        Matcher matcher = DOCUMENTED_BASELINE_COUNT.matcher(docs);
        Map<String, Integer> counts = new LinkedHashMap<>();
        while (matcher.find()) {
            String ruleName = matcher.group(1);
            if (DOCUMENTED_FROZEN_RULES.containsKey(ruleName)) {
                counts.put(ruleName, Integer.parseInt(matcher.group(2)));
            }
        }
        return counts;
    }

    private static Map<String, Integer> frozenBaselineCounts() {
        return frozenBaselineCounts(frozenStoreDirectory(), DOCUMENTED_FROZEN_RULES);
    }

    private static Map<String, Integer> frozenBaselineCounts(Path storeDirectory, Map<String, ArchRule> rules) {
        TextFileBasedViolationStore store = readOnlyFrozenStore(storeDirectory);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var rule : rules.entrySet()) {
            counts.put(rule.getKey(), store.getViolations(rule.getValue()).size());
        }
        return counts;
    }

    private static Path frozenStoreDirectory() {
        return PROJECT_ROOT.resolve("src/test/resources/archunit/frozen");
    }

    private static TextFileBasedViolationStore readOnlyFrozenStore(Path storeDirectory) {
        Properties configuration = new Properties();
        configuration.setProperty("default.path", storeDirectory.toString());
        configuration.setProperty("default.allowStoreCreation", "false");
        configuration.setProperty("default.allowStoreUpdate", "false");
        TextFileBasedViolationStore store = new TextFileBasedViolationStore();
        store.initialize(configuration);
        return store;
    }

    private static long sliceMemberDependencyCount(List<String> records) {
        long count = 0;
        for (String record : records) {
            List<String> lines = record.lines().toList();
            assertTrue(!lines.isEmpty() && lines.getFirst().matches("Slice .+ depends on Slice .+:"),
                    "Expected an ArchUnit slice record with a dependency-group header");
            count += lines.stream().skip(1).filter(line -> !line.isBlank()).count();
        }
        return count;
    }

    private static Set<String> documentedCycleClusters(String docs) {
        Matcher matcher = DOCUMENTED_CYCLE_CLUSTER.matcher(docs);
        Set<String> clusters = new HashSet<>();
        while (matcher.find()) {
            clusters.add(matcher.group(1));
        }
        return clusters;
    }
}
