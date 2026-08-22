package com.openggf.tests.trace.runs;

import com.openggf.trace.TraceData;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.catalog.TraceCatalog;
import com.openggf.trace.replay.runs.ActiveSegmentPayload;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunSegmentDescriptor;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRows;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Architectural ratchet for the one-active-segment payload lease. */
class TestActiveSegmentPayloadAuthorityGuard {
    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final String SELF_SOURCE =
            "com/openggf/tests/trace/runs/TestActiveSegmentPayloadAuthorityGuard.java";
    private static final String PAYLOAD = ActiveSegmentPayload.class.getName();
    private static final String WALKER = TraceRunReplayWalker.class.getName();
    private static final Set<String> PAYLOAD_ACCESSORS = Set.of(
            "trace", "specialStageRows");
    private static final Set<String> ACQUISITION_METHODS = Set.of(
            "trace", "specialStageRows", "openActiveSegment");
    private static final Set<String> EXACT_CALLER_ALLOWLIST = Set.of(
            "com.openggf.TraceSessionLauncher",
            "com.openggf.tests.trace.runs.AbstractRunChainTest",
            "com.openggf.tests.trace.runs.VisualRunReplayHarness",
            "com.openggf.trace.replay.runs.TestActiveSegmentPayload",
            "com.openggf.trace.TestTraceReaderLifecycle",
            "com.openggf.TestTraceSessionLauncherActivePayloadLifecycle",
            "com.openggf.tests.trace.runs.TestHeadlessRunActivePayloadLifecycle",
            "com.openggf.tests.trace.runs.TestVisualRunActivePayloadLifecycle",
            "com.openggf.tests.trace.runs.TestTraceRunActivePayloadOwnership",
            "com.openggf.tests.trace.runs.TestTraceRunActivePayloadPerformance",
            "com.openggf.tests.trace.runs.TestActiveSegmentPayloadAuthorityGuard");
    private static final Set<String> CONTROLLED_BYTECODE_MUTATIONS = Set.of(
            UnauthorizedDirectCall.class.getName(),
            UnauthorizedMethodReference.class.getName(),
            UnauthorizedOpenHelper.class.getName(),
            UnauthorizedEagerPlan.class.getName());
    private static final Set<String> EAGER_PLAN_CALLER_ALLOWLIST = Set.of(
            "com.openggf.tests.trace.runs.TestTraceRunDescriptorPlanningPerformance",
            "com.openggf.tests.trace.runs.TestTraceRunActivePayloadPerformance");
    private static final Set<Class<?>> PAYLOAD_GRAPH_TYPES = Set.of(
            ActiveSegmentPayload.class,
            TraceData.class,
            TraceRunSpecialStageRows.class);
    private static final Pattern REFLECTIVE_ACQUISITION = Pattern.compile(
            "\\b(?:Class\\s*\\.\\s*forName|getMethod|getDeclaredMethod|"
                    + "findVirtual|findStatic)\\s*\\(");
    private static final Pattern TARGET_TYPE_REFERENCE = Pattern.compile(
            "\\b(?:ActiveSegmentPayload|TraceRunReplayWalker)\\b");

    @Test
    void leaseAndWalkerExposeOnlyTheApprovedPublicSurface() {
        assertTrue(Modifier.isFinal(ActiveSegmentPayload.class.getModifiers()),
                "the active payload lease must remain final");

        Constructor<?>[] constructors = ActiveSegmentPayload.class
                .getDeclaredConstructors();
        assertEquals(1, constructors.length,
                "the lease must keep one construction boundary");
        assertFalse(Modifier.isPublic(constructors[0].getModifiers())
                        || Modifier.isProtected(constructors[0].getModifiers()),
                "the lease constructor must not become public or protected");

        List<String> publicMethods = Arrays.stream(
                        ActiveSegmentPayload.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(TestActiveSegmentPayloadAuthorityGuard::methodSignature)
                .sorted()
                .toList();
        assertEquals(List.of(
                "close():void",
                "descriptor():com.openggf.trace.replay.runs.TraceRunSegmentDescriptor",
                "isClosed():boolean",
                "specialStageRows():com.openggf.trace.replay.runs.TraceRunSpecialStageRows",
                "trace():com.openggf.trace.TraceData"), publicMethods,
                "the lease public API must remain the exact approved surface");

        List<Method> openFacades = Arrays.stream(
                        TraceRunReplayWalker.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getReturnType()
                        == ActiveSegmentPayload.class)
                .toList();
        assertEquals(1, openFacades.size(),
                "the walker must expose exactly one active-payload facade");
        Method facade = openFacades.getFirst();
        assertEquals("openActiveSegment", facade.getName());
        assertTrue(Modifier.isStatic(facade.getModifiers()));
        assertEquals(List.of(
                        TraceRunSegmentDescriptor.class, int.class),
                Arrays.asList(facade.getParameterTypes()));
        assertEquals(List.of(IOException.class),
                Arrays.asList(facade.getExceptionTypes()));
    }

    @Test
    void compiledPayloadAcquisitionHasOnlyTheExactCallerAllowlist() {
        List<String> violations = unauthorizedPayloadCalls(importProjectClasses(),
                CONTROLLED_BYTECODE_MUTATIONS);

        assertEquals(List.of(), violations,
                "active payload access must stay at the exact approved owners");
    }

    @Test
    void bytecodeGuardRejectsUnauthorizedDirectCallsMethodReferencesAndHelpers() {
        JavaClasses mutations = new ClassFileImporter().importClasses(
                UnauthorizedDirectCall.class,
                UnauthorizedMethodReference.class,
                UnauthorizedOpenHelper.class);

        List<String> violations = unauthorizedPayloadCalls(mutations, Set.of());

        assertEquals(3, violations.size());
        assertTrue(violations.stream().anyMatch(line -> line.contains(
                UnauthorizedDirectCall.class.getName())
                && line.contains(".trace(")), violations.toString());
        assertTrue(violations.stream().anyMatch(line -> line.contains(
                UnauthorizedMethodReference.class.getName())
                && line.contains(".trace(")), violations.toString());
        assertTrue(violations.stream().anyMatch(line -> line.contains(
                UnauthorizedOpenHelper.class.getName())
                && line.contains(".openActiveSegment(")), violations.toString());
    }

    @Test
    void transitionalEagerApisAndNonBenchmarkPlanCallersAreAbsent() {
        List<String> planViolations = eagerPlanCalls(
                importProjectClasses(), CONTROLLED_BYTECODE_MUTATIONS);
        assertEquals(List.of(), planViolations,
                "only the exact benchmark classes may call the eager reference planner");

        List<String> catalogNestedTypes = Arrays.stream(
                        TraceCatalog.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .filter(name -> Set.of(
                        "PreparedRunLaunch", "RunSegmentPlanner", "RunPlannerPair")
                        .contains(name))
                .sorted()
                .toList();
        assertEquals(List.of(), catalogNestedTypes,
                "the transitional eager catalog API must stay removed");
        assertTrue(Arrays.stream(TraceCatalog.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("prepareRunLaunch")),
                "the eager catalog launch method must stay removed");
        assertTrue(Arrays.stream(TraceRunReplayWalker.class.getDeclaredMethods())
                .noneMatch(method -> Set.of(
                        "hasHardwareTimingStream", "hardwareTimingSegments")
                        .contains(method.getName())),
                "the eager timing-summary helpers must stay removed");
        assertTrue(Arrays.stream(
                        TraceRunPlaybackCoordinator.class.getConstructors())
                .noneMatch(constructor -> Arrays.stream(
                        constructor.getGenericParameterTypes())
                        .map(Type::getTypeName)
                        .anyMatch(name -> name.contains("SegmentPlan"))),
                "the eager four-argument coordinator constructor must stay removed");
    }

    @Test
    void eagerPlanGuardRejectsAReplayHarnessCaller() {
        JavaClasses mutation = new ClassFileImporter().importClasses(
                UnauthorizedEagerPlan.class);

        List<String> violations = eagerPlanCalls(mutation, Set.of());

        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.getFirst().contains(
                UnauthorizedEagerPlan.class.getName()), violations.toString());
    }

    @Test
    void sourceGuardRejectsReflectiveMethodHandleAndConstructedAcquisition() {
        assertSingleSourceViolation("Reflective.java", """
                class Reflective {
                    Object acquire() throws Exception {
                        return ActiveSegmentPayload.class.getMethod("trace");
                    }
                }
                """);
        assertSingleSourceViolation("ClassForName.java", """
                class ClassForName {
                    Object acquire() throws Exception {
                        return Class.forName(
                                "com.openggf.trace.replay.runs.ActiveSegmentPayload")
                                .getDeclaredMethod("specialStageRows");
                    }
                }
                """);
        assertSingleSourceViolation("MethodHandle.java", """
                class MethodHandle {
                    Object acquire(MethodHandles.Lookup lookup) throws Exception {
                        return lookup.findVirtual(ActiveSegmentPayload.class,
                                "trace", MethodType.methodType(TraceData.class));
                    }
                }
                """);
        assertSingleSourceViolation("StaticMethodHandle.java", """
                class StaticMethodHandle {
                    Object acquire(MethodHandles.Lookup lookup) throws Exception {
                        return lookup.findStatic(TraceRunReplayWalker.class,
                                "openActiveSegment", MethodType.methodType(
                                        ActiveSegmentPayload.class,
                                        TraceRunSegmentDescriptor.class, int.class));
                    }
                }
                """);
        assertSingleSourceViolation("ConstructedName.java", """
                class ConstructedName {
                    Object acquire() throws Exception {
                        String name = "tr" + "ace";
                        return ActiveSegmentPayload.class.getDeclaredMethod(name);
                    }
                }
                """);
    }

    @Test
    void repositorySourcesCannotAcquireTheLeaseReflectively() throws IOException {
        List<String> violations = new ArrayList<>();
        scanSourceTree(MAIN_SOURCES, violations);
        scanSourceTree(TEST_SOURCES, violations);

        assertEquals(List.of(), violations,
                "reflection and method handles must not bypass payload authority");
    }

    @Test
    void allowlistedOwnersCannotPublishPayloadRelayApis() {
        List<String> violations = new ArrayList<>();
        for (String ownerName : EXACT_CALLER_ALLOWLIST) {
            Class<?> owner = loadClass(ownerName);
            if (owner != null) {
                inspectRelaySurface(owner, violations);
            }
        }

        assertEquals(List.of(), violations,
                "approved callers may consume a lease but cannot relay its graph");
    }

    @Test
    void relayGuardRejectsLeaseTraceAndSpecialRowsSurfaces() {
        List<String> violations = new ArrayList<>();
        inspectRelaySurface(LeaseRelayMutation.class, violations);
        inspectRelaySurface(TraceRelayMutation.class, violations);
        inspectRelaySurface(SpecialRowsRelayMutation.class, violations);

        assertEquals(3, violations.size());
        assertTrue(violations.stream().anyMatch(line -> line.contains(
                LeaseRelayMutation.class.getName())), violations.toString());
        assertTrue(violations.stream().anyMatch(line -> line.contains(
                TraceRelayMutation.class.getName())), violations.toString());
        assertTrue(violations.stream().anyMatch(line -> line.contains(
                SpecialRowsRelayMutation.class.getName())), violations.toString());
    }

    private static JavaClasses importProjectClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeArchives())
                .importPackages("com.openggf");
    }

    private static List<String> unauthorizedPayloadCalls(
            JavaClasses classes, Set<String> ignoredOrigins) {
        return classes.stream()
                .flatMap(owner -> owner.getAccessesFromSelf().stream())
                .filter(TestActiveSegmentPayloadAuthorityGuard::targetsAcquisition)
                .filter(call -> !EXACT_CALLER_ALLOWLIST.contains(
                        call.getOriginOwner().getName()))
                .filter(call -> !ignoredOrigins.contains(
                        call.getOriginOwner().getName()))
                .map(JavaAccess::getDescription)
                .sorted()
                .distinct()
                .toList();
    }

    private static List<String> eagerPlanCalls(
            JavaClasses classes, Set<String> ignoredOrigins) {
        return classes.stream()
                .flatMap(owner -> owner.getAccessesFromSelf().stream())
                .filter(access -> access instanceof JavaMethodCall
                        || access instanceof JavaMethodReference)
                .filter(access -> WALKER.equals(access.getTargetOwner().getName()))
                .filter(access -> "plan".equals(access.getTarget().getName()))
                .filter(access -> !EAGER_PLAN_CALLER_ALLOWLIST.contains(
                        access.getOriginOwner().getName()))
                .filter(access -> !ignoredOrigins.contains(
                        access.getOriginOwner().getName()))
                .map(JavaAccess::getDescription)
                .sorted()
                .distinct()
                .toList();
    }

    private static boolean targetsAcquisition(JavaAccess<?> call) {
        if (!(call instanceof JavaMethodCall)
                && !(call instanceof JavaMethodReference)) {
            return false;
        }
        String owner = call.getTargetOwner().getName();
        String method = call.getTarget().getName();
        return (PAYLOAD.equals(owner) && PAYLOAD_ACCESSORS.contains(method))
                || (WALKER.equals(owner) && "openActiveSegment".equals(method));
    }

    private static void scanSourceTree(
            Path root, List<String> violations) throws IOException {
        try (Stream<Path> sources = Files.walk(root)) {
            for (Path source : sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                String relative = root.relativize(source).toString()
                        .replace(source.getFileSystem().getSeparator(), "/");
                if (root.equals(TEST_SOURCES) && SELF_SOURCE.equals(relative)) {
                    continue;
                }
                violations.addAll(scanReflectiveAcquisition(
                        source.toString(), Files.readString(source)));
            }
        }
    }

    private static List<String> scanReflectiveAcquisition(
            String fileName, String source) {
        String withoutComments = stripCommentsPreservingLines(source);
        List<String> violations = new ArrayList<>();
        java.util.regex.Matcher acquisition =
                REFLECTIVE_ACQUISITION.matcher(withoutComments);
        while (acquisition.find()) {
            int windowStart = Math.max(0, acquisition.start() - 800);
            int windowEnd = Math.min(
                    withoutComments.length(), acquisition.end() + 800);
            String acquisitionWindow = withoutComments.substring(
                    windowStart, windowEnd);
            if (!TARGET_TYPE_REFERENCE.matcher(acquisitionWindow).find()) {
                continue;
            }
            Set<String> names = accessorNamesPresent(acquisitionWindow);
            if (names.isEmpty()) {
                continue;
            }
            int line = 1;
            for (int index = 0; index < acquisition.start(); index++) {
                if (withoutComments.charAt(index) == '\n') {
                    line++;
                }
            }
            violations.add(fileName + ":" + line
                    + " reflectively acquires active payload authority "
                    + names);
        }
        return violations;
    }

    private static Set<String> accessorNamesPresent(String source) {
        java.util.HashSet<String> names = new java.util.HashSet<>();
        List<StringLiteral> literals = stringLiterals(source);
        for (StringLiteral literal : literals) {
            if (ACQUISITION_METHODS.contains(literal.value())) {
                names.add(literal.value());
            }
        }
        for (int first = 0; first < literals.size(); first++) {
            StringBuilder joined = new StringBuilder(literals.get(first).value());
            for (int next = first + 1; next < literals.size(); next++) {
                if (!isConcatenationSeparator(source,
                        literals.get(next - 1).end(),
                        literals.get(next).start())) {
                    break;
                }
                joined.append(literals.get(next).value());
                if (ACQUISITION_METHODS.contains(joined.toString())) {
                    names.add(joined.toString());
                }
            }
        }
        return Set.copyOf(names);
    }

    private static List<StringLiteral> stringLiterals(String source) {
        List<StringLiteral> literals = new ArrayList<>();
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) != '"') {
                continue;
            }
            int start = index;
            StringBuilder value = new StringBuilder();
            for (index++; index < source.length(); index++) {
                char current = source.charAt(index);
                if (current == '"' && !escaped(source, index)) {
                    literals.add(new StringLiteral(
                            value.toString(), start, index + 1));
                    break;
                }
                value.append(current);
            }
        }
        return List.copyOf(literals);
    }

    private static boolean isConcatenationSeparator(
            String source, int start, int end) {
        boolean plus = false;
        for (int index = start; index < end; index++) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (current == '+' && !plus) {
                plus = true;
                continue;
            }
            return false;
        }
        return plus;
    }

    private static String stripCommentsPreservingLines(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;
        boolean string = false;
        boolean character = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length()
                    ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    result.append(current);
                } else {
                    result.append(' ');
                }
            } else if (blockComment) {
                if (current == '*' && next == '/') {
                    result.append("  ");
                    index++;
                    blockComment = false;
                } else {
                    result.append(current == '\n' ? '\n' : ' ');
                }
            } else if (!string && !character && current == '/' && next == '/') {
                result.append("  ");
                index++;
                lineComment = true;
            } else if (!string && !character && current == '/' && next == '*') {
                result.append("  ");
                index++;
                blockComment = true;
            } else {
                result.append(current);
                if (current == '"' && !character && !escaped(source, index)) {
                    string = !string;
                } else if (current == '\'' && !string
                        && !escaped(source, index)) {
                    character = !character;
                }
            }
        }
        return result.toString();
    }

    private static boolean escaped(String source, int index) {
        int slashes = 0;
        for (int candidate = index - 1;
                candidate >= 0 && source.charAt(candidate) == '\\';
                candidate--) {
            slashes++;
        }
        return (slashes & 1) != 0;
    }

    private static void assertSingleSourceViolation(
            String fileName, String source) {
        List<String> violations = scanReflectiveAcquisition(fileName, source);
        assertFalse(violations.isEmpty(), violations.toString());
        assertTrue(violations.stream().allMatch(
                        violation -> violation.startsWith(fileName + ":")),
                violations.toString());
    }

    private static void inspectRelaySurface(
            Class<?> owner, List<String> violations) {
        for (Field field : owner.getDeclaredFields()) {
            if (isPublicOrProtected(field.getModifiers())
                    && containsPayloadGraph(field.getGenericType())) {
                violations.add(owner.getName() + " field " + field.getName());
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (isPublicOrProtected(method.getModifiers())
                    && containsPayloadGraph(method.getGenericReturnType())) {
                violations.add(owner.getName() + " method " + method.getName());
            }
        }
    }

    private static boolean containsPayloadGraph(Type type) {
        if (type instanceof Class<?> typeClass) {
            return PAYLOAD_GRAPH_TYPES.stream()
                    .anyMatch(payloadType -> payloadType.isAssignableFrom(typeClass));
        }
        if (type instanceof ParameterizedType parameterized) {
            return containsPayloadGraph(parameterized.getRawType())
                    || Arrays.stream(parameterized.getActualTypeArguments())
                    .anyMatch(TestActiveSegmentPayloadAuthorityGuard
                            ::containsPayloadGraph);
        }
        return false;
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String methodSignature(Method method) {
        return method.getName() + "("
                + Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(java.util.stream.Collectors.joining(","))
                + "):" + method.getReturnType().getName();
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException failure) {
            return null;
        }
    }

    private record StringLiteral(String value, int start, int end) {
    }

    private static final class UnauthorizedDirectCall {
        private TraceData acquire(ActiveSegmentPayload payload) {
            return payload.trace();
        }
    }

    private static final class UnauthorizedMethodReference {
        private Function<ActiveSegmentPayload, TraceData> acquire() {
            return ActiveSegmentPayload::trace;
        }
    }

    private static final class UnauthorizedOpenHelper {
        private ActiveSegmentPayload acquire(
                TraceRunSegmentDescriptor descriptor) throws IOException {
            return TraceRunReplayWalker.openActiveSegment(descriptor, 0);
        }
    }

    private static final class UnauthorizedEagerPlan {
        private List<TraceRunReplayWalker.SegmentPlan> plan(
                TraceRunManifest manifest, Path directory) throws IOException {
            return TraceRunReplayWalker.plan(manifest, directory);
        }
    }

    private static final class LeaseRelayMutation {
        public ActiveSegmentPayload payload() {
            return null;
        }
    }

    private static final class TraceRelayMutation {
        protected TraceData trace() {
            return null;
        }
    }

    private static final class SpecialRowsRelayMutation {
        public TraceRunSpecialStageRows rows() {
            return null;
        }
    }
}
