package com.openggf.net;

import com.openggf.mods.validation.ModValidationFinding;
import com.openggf.mods.validation.ModValidationReport;
import com.openggf.mods.validation.ModValidator;
import com.openggf.net.protocol.ControlJsonCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mod-readiness gate for the racing library and the JDK room host (Time Attack extraction,
 * lane A, 2026-10-08): their compiled classes must pass {@link ModValidator}'s
 * static-state rule, because they will ship inside a validated bundled mod.
 *
 * <p>The validator reports every class under {@code com/openggf/} as
 * {@code RESERVED_ENGINE_PACKAGE} and skips it <em>before</em> the static-state rule runs,
 * so validating the in-tree classes as-is would pass vacuously. This test therefore
 * relocates {@code com/openggf/net/} to the same-length {@code org/openggf/net/} in the
 * class bytes (consistent across names, descriptors and signatures), packages the result,
 * and runs the real validator. A deliberately bad canary proves the rule still fires.
 * Delete this test once the library and mod are built and validated as their own artifacts.
 */
class TestRacingLibraryStaticState {
    private static final String NET = "com/openggf/net/";
    private static final String RELOCATED_NET = "org/openggf/net/";
    /** Whole packages (and subpackages) bound for the racing library or the bundled mod. */
    private static final List<String> MOD_BOUND_PACKAGES = List.of(
            NET + "protocol/", NET + "hub/", NET + "client/", NET + "identity/",
            NET + "host/jdk/");
    /** Netty-free shared host classes the client and JDK host depend on. */
    private static final Set<String> MOD_BOUND_CLASSES = Set.of(
            NET + "host/RaceRoomHost", NET + "host/HostMasterLink",
            NET + "host/ConnectionHygiene");
    /** Server-side static facade kept for the master; it must not be reachable from the mod set. */
    private static final Set<String> SERVER_ONLY_CLASSES = Set.of(NET + "protocol/ControlCodec");
    /** Classes whose presence proves the census actually covered each mod-bound area. */
    private static final Set<String> CENSUS_SENTINELS = Set.of(
            NET + "protocol/ControlJsonCodec", NET + "protocol/GhostPackets",
            NET + "hub/RoomHost", NET + "hub/GhostHub", NET + "client/RaceClient",
            NET + "client/MasterClient", NET + "identity/PlayerIdentity",
            NET + "host/jdk/JdkRaceHostServer", NET + "host/HostMasterLink");
    private static final String CANARY = TestRacingLibraryStaticState.class.getName()
            .replace('.', '/') + "$Canary";
    private static final Set<String> ALLOWED_ERROR_CODES = Set.of(
            "ENTRYPOINT_MISSING", "STATIC_STATE_UNSUPPORTED");
    private static final List<String> FORBIDDEN_DEPENDENCIES = List.of(
            "io/netty/", "org/bouncycastle/", "org/sqlite/", NET + "master/");

    /** Holds the static state the validator must reject; never initialized by this test. */
    static final class Canary {
        static final int LITERAL_CONSTANT = 7;
        static final Object SHARED_OBJECT = new Object();
        static final List<String> CONSTANT_LIST = List.of("a", "b");

        private Canary() {
        }
    }

    @Test
    void modBoundRacingClassesHoldNoValidatorRejectedStaticState() throws Exception {
        Map<String, byte[]> modBound = modBoundProductionClasses();
        assertTrue(modBound.keySet().containsAll(CENSUS_SENTINELS),
                "mod-bound census is missing expected classes: " + CENSUS_SENTINELS);

        Map<String, byte[]> jarClasses = new TreeMap<>(modBound);
        jarClasses.put(CANARY, testClassBytes(CANARY));
        ModValidationReport report = new ModValidator().validate(
                relocatedJar(jarClasses), RELOCATED_NET + "NoEntrypoint");

        List<String> unexpectedErrors = new ArrayList<>();
        List<String> staticState = new ArrayList<>();
        Set<String> canaryMembers = new TreeSet<>();
        String relocatedCanary = relocate(CANARY);
        for (ModValidationFinding finding : report.findings()) {
            if (finding.severity() != ModValidationFinding.Severity.ERROR) {
                continue;
            }
            if (!ALLOWED_ERROR_CODES.contains(finding.code())) {
                unexpectedErrors.add(describe(finding));
            } else if ("STATIC_STATE_UNSUPPORTED".equals(finding.code())) {
                if (finding.className().equals(relocatedCanary)) {
                    canaryMembers.add(finding.member());
                } else {
                    staticState.add(describe(finding));
                }
            }
        }
        assertEquals(List.of(), unexpectedErrors,
                "relocated racing classes must parse, stay outside reserved packages and be unique");
        assertEquals(Set.of("<clinit>", "SHARED_OBJECT", "CONSTANT_LIST"), canaryMembers,
                "the static-state rule must still reject the canary's non-literal statics "
                        + "(and only those); otherwise this gate proves nothing");
        assertEquals(List.of(), staticState,
                "mod-bound racing classes must hold only literal static constants; move "
                        + "loggers, mappers, patterns and collections into instance state");
    }

    @Test
    void modBoundRacingClassesStayInsideTheModSet() throws Exception {
        Map<String, byte[]> modBound = modBoundProductionClasses();
        Set<String> violations = new TreeSet<>();
        for (Map.Entry<String, byte[]> entry : modBound.entrySet()) {
            for (String utf8 : utf8Constants(entry.getValue())) {
                for (String forbidden : FORBIDDEN_DEPENDENCIES) {
                    if (utf8.contains(forbidden)) {
                        violations.add(entry.getKey() + " -> " + forbidden);
                    }
                }
                for (String reference : netReferences(utf8)) {
                    if (!modBound.containsKey(reference)) {
                        violations.add(entry.getKey() + " -> " + reference);
                    }
                }
            }
        }
        assertEquals(Set.of(), violations,
                "mod-bound classes may reference only each other (no server facade, master, "
                        + "Netty host, Netty, Bouncy Castle or SQLite)");
    }

    private static Map<String, byte[]> modBoundProductionClasses() throws Exception {
        Path root = Path.of(ControlJsonCodec.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        assertTrue(Files.isDirectory(root), "expected compiled production classes at " + root);
        Map<String, byte[]> classes = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root.resolve(NET))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length());
                if (modBound(name)) {
                    classes.put(name, Files.readAllBytes(file));
                }
            }
        }
        return classes;
    }

    private static boolean modBound(String internalName) {
        String outer = internalName.contains("$")
                ? internalName.substring(0, internalName.indexOf('$')) : internalName;
        if (SERVER_ONLY_CLASSES.contains(outer)) {
            return false;
        }
        return MOD_BOUND_CLASSES.contains(outer)
                || MOD_BOUND_PACKAGES.stream().anyMatch(internalName::startsWith);
    }

    private static byte[] testClassBytes(String internalName) throws IOException {
        try (InputStream input = TestRacingLibraryStaticState.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            assertTrue(input != null, "missing test class " + internalName);
            return input.readAllBytes();
        }
    }

    private static byte[] relocatedJar(Map<String, byte[]> classes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                jar.putNextEntry(new JarEntry(relocate(entry.getKey()) + ".class"));
                jar.write(relocateBytes(entry.getValue()));
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String relocate(String internalName) {
        return internalName.startsWith(NET)
                ? RELOCATED_NET + internalName.substring(NET.length()) : internalName;
    }

    /**
     * Same-length substitution keeps every constant-pool length and offset valid. Both the
     * slash form (names, descriptors, signatures) and javac's dollar form (enum switch-map
     * fields such as {@code $SwitchMap$com$openggf$net$...}, which the validator matches
     * against the enum's internal name) are rewritten, as a real recompilation would.
     */
    private static byte[] relocateBytes(byte[] classFile) {
        byte[] copy = classFile.clone();
        replaceAll(copy, NET, RELOCATED_NET);
        replaceAll(copy, NET.replace('/', '$'), RELOCATED_NET.replace('/', '$'));
        return copy;
    }

    private static void replaceAll(byte[] bytes, String fromText, String toText) {
        byte[] from = fromText.getBytes(StandardCharsets.US_ASCII);
        byte[] to = toText.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i + from.length <= bytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < from.length && match; j++) {
                match = bytes[i + j] == from[j];
            }
            if (match) {
                System.arraycopy(to, 0, bytes, i, to.length);
                i += from.length - 1;
            }
        }
    }

    /** Class-name tokens under {@code com/openggf/net/} inside one UTF-8 constant. */
    private static Set<String> netReferences(String utf8) {
        Set<String> references = new LinkedHashSet<>();
        int index = utf8.indexOf(NET);
        while (index >= 0) {
            int end = index + NET.length();
            while (end < utf8.length() && isNameChar(utf8.charAt(end))) {
                end++;
            }
            String name = utf8.substring(index, end);
            if (!name.endsWith("/")) {
                references.add(name);
            }
            index = utf8.indexOf(NET, end);
        }
        return references;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '/';
    }

    /** Every CONSTANT_Utf8 entry (JVMS §4.4); names, descriptors and signatures all live there. */
    private static List<String> utf8Constants(byte[] classFile) {
        List<String> strings = new ArrayList<>();
        int count = u2(classFile, 8);
        int offset = 10;
        for (int index = 1; index < count; index++) {
            int tag = classFile[offset] & 0xFF;
            switch (tag) {
                case 1 -> {
                    int length = u2(classFile, offset + 1);
                    // Class-file "modified UTF-8" equals UTF-8 for every name this test inspects.
                    strings.add(new String(classFile, offset + 3, length, StandardCharsets.UTF_8));
                    offset += 3 + length;
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> offset += 5;
                case 5, 6 -> {
                    offset += 9;
                    index++;
                }
                case 7, 8, 16, 19, 20 -> offset += 3;
                case 15 -> offset += 4;
                default -> throw new IllegalStateException("unknown constant-pool tag " + tag);
            }
        }
        return strings;
    }

    private static int u2(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static String describe(ModValidationFinding finding) {
        return finding.code() + " " + finding.className()
                + (finding.member().isEmpty() ? "" : "#" + finding.member())
                + ": " + finding.message();
    }
}
